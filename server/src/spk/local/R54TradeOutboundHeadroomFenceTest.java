package spk.local;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * R54: capacity pressure is not a terminal write failure, but it is a
 * known reason that a multi-packet trade commit may fail after item removal.
 * Both players' inventories must remain unchanged when a counterparty queue
 * is nearly full *before* final acceptance.
 */
public final class R54TradeOutboundHeadroomFenceTest {
    private static final int CAPACITY = 1 << 20;
    public static void main(String[] args) throws Exception {
        World world = World.isolatedForTest(600L);
        WorldPlayer first = new WorldPlayer();
        WorldPlayer second = new WorldPlayer();
        world.registerPlayer(first, "opensrc");
        world.registerPlayer(second, "src");
        OutboundPacketQueue tight = new OutboundPacketQueue(CAPACITY);
        OutboundPacketQueue healthy = new OutboundPacketQueue(CAPACITY);
        ServerPacketWriter writerA = new ServerPacketWriter(
            tight, new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter writerB = new ServerPacketWriter(
            healthy, new IsaacCipher(new int[]{5,6,7,8}));
        AtomicInteger saves = new AtomicInteger();
        try {
            first.bank().spawnItem(995, 1000, writerA);
            second.bank().spawnItem(385, 2, writerB);
            TradeService.register(
                world, first, first.bank(), writerA, saves::incrementAndGet);
            TradeService.register(
                world, second, second.bank(), writerB, saves::incrementAndGet);
            contains(TradeService.start(world, first, second), "TRADE_UI_OPEN");
            contains(TradeService.handleItemAction(first,
                new ItemContainerAction(
                    145, 3322, 0, 995, 0, "ITEM_ACTION_1")), "TRADE_OFFER_OK");
            int sharkSlot = EngineR4TradeFlowTest.find(second.bank(), 385);
            check(sharkSlot >= 0, "missing shark slot");
            contains(TradeService.handleItemAction(second,
                new ItemContainerAction(
                    145, 3322, sharkSlot, 385, 0, "ITEM_ACTION_1")),
                "TRADE_OFFER_OK");
            contains(TradeService.handleWidget(first, 3420),
                "WAITING_OTHER");
            contains(TradeService.handleWidget(second, 3420),
                "CONFIRM_OPEN");
            contains(TradeService.handleWidget(first, 3546),
                "WAITING_OTHER");

            // Saturate a healthy, not-yet-failed outbound queue. Pre-R54
            // this passed R53's health check, then endBatch could reject
            // the queued trade packet AFTER both inventories were modified.
            int before = tight.queuedBytes();
            int reserve = 1024;
            check(before < CAPACITY - reserve,
                "fixture queue unexpectedly full before saturation");
            tight.offer(new byte[CAPACITY - reserve - before]);
            check(tight.queuedBytes() == CAPACITY - reserve,
                "did not establish exactly 1KiB headroom");
            check(!tight.transportFailed() && !tight.overflowed(),
                "test needs a non-terminal, non-overflowed queue");
            check(!healthy.transportFailed() && !healthy.overflowed(),
                "accepting player's queue unexpectedly unavailable");

            String outcome = TradeService.handleWidget(second, 3546);
            contains(outcome, "TRADE_COMMIT_REJECTED_OUTBOUND_HEADROOM");
            check(first.bank().inventoryCount(995) == 1000,
                "tight-queue partner's coins changed");
            check(first.bank().inventoryCount(385) == 0,
                "tight-queue partner gained a shark");
            check(second.bank().inventoryCount(995) == 0,
                "accepting player gained coins");
            check(second.bank().inventoryCount(385) == 2,
                "accepting player lost sharks");
            check(saves.get() == 0,
                "rejected trade invoked save callback");
            check(!TradeService.active(first) && !TradeService.active(second),
                "rejected trade remained active");
            check(!tight.transportFailed() && !tight.overflowed(),
                "capacity preflight should reject before queue poisoning");
            check(!healthy.transportFailed(),
                "accepting player's outbound stream was poisoned");

            System.out.println(
                "R54_TRADE_OUTBOUND_HEADROOM_PREFLIGHT_PASS " +
                "counterpartyExactly1024BytesFree=true " +
                "noTerminalFailureBeforeFinalAccept=true " +
                "bothInventoriesUnchanged=true " +
                "noSaveCallbacks=true tradeCancelled=true " +
                "lateSocketOrPersistenceFailureNotSolved=true");
        } finally {
            TradeService.unregister(first);
            TradeService.unregister(second);
            world.unregisterPlayer(first);
            world.unregisterPlayer(second);
            world.close();
        }
    }

    private static void check(boolean good, String why) {
        if (!good) throw new AssertionError(why);
    }
    private static void contains(String text, String expected) {
        check(text != null && text.contains(expected),
            "missing " + expected + " in " + text);
    }

    private R54TradeOutboundHeadroomFenceTest() {}
}

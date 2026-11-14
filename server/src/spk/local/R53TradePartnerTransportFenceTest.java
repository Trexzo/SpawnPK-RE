package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R53: a healthy final-accepting player cannot commit a trade after the
 * counterparty's outbound queue is known terminal, even if the other player's
 * World context is still live. Uses only disposable World and queue fixtures.
 */
public final class R53TradePartnerTransportFenceTest {
    public static void main(String[] args) throws Exception {
        World world = World.isolatedForTest(600L);
        WorldPlayer first = new WorldPlayer();
        WorldPlayer second = new WorldPlayer();
        world.registerPlayer(first, "opensrc");
        world.registerPlayer(second, "src");
        OutboundPacketQueue failedQueue = new OutboundPacketQueue();
        OutboundPacketQueue healthyQueue = new OutboundPacketQueue();
        ServerPacketWriter failedWriter =
            new ServerPacketWriter(failedQueue, new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter healthyWriter =
            new ServerPacketWriter(healthyQueue, new IsaacCipher(new int[]{5,6,7,8}));
        AtomicInteger saves = new AtomicInteger();
        try {
            first.bank().spawnItem(995, 1000, failedWriter);
            second.bank().spawnItem(385, 2, healthyWriter);
            TradeService.register(
                world, first, first.bank(), failedWriter, saves::incrementAndGet);
            TradeService.register(
                world, second, second.bank(), healthyWriter, saves::incrementAndGet);
            requireContains(
                TradeService.start(world, first, second), "TRADE_UI_OPEN");
            requireContains(TradeService.handleItemAction(
                first, new ItemContainerAction(145, 3322, 0, 995, 0,
                    "ITEM_ACTION_1")), "TRADE_OFFER_OK");
            int sharkSlot = EngineR4TradeFlowTest.find(second.bank(), 385);
            require(sharkSlot >= 0, "second inventory shark slot missing");
            requireContains(TradeService.handleItemAction(
                second, new ItemContainerAction(145, 3322, sharkSlot, 385, 0,
                    "ITEM_ACTION_1")), "TRADE_OFFER_OK");
            requireContains(TradeService.handleWidget(first, 3420),
                "WAITING_OTHER");
            requireContains(TradeService.handleWidget(second, 3420),
                "CONFIRM_OPEN");
            requireContains(TradeService.handleWidget(first, 3546),
                "WAITING_OTHER");

            require(failedQueue.queuedPackets() > 0,
                "no queued traffic for partial-failure simulation");
            ByteArrayOutputStream partial = new ByteArrayOutputStream();
            OutputStream broken = new OutputStream() {
                @Override public void write(int b) throws IOException {
                    partial.write(b);
                    throw new IOException("R53 forced partial transport failure");
                }
                @Override public void write(byte[] b,int off,int len)
                        throws IOException {
                    if(len > 0) partial.write(b, off, 1);
                    throw new IOException("R53 forced partial transport failure");
                }
            };
            try {
                failedQueue.drainTo(broken, 1 << 20);
                throw new AssertionError("simulated partial write accepted");
            } catch (IOException expected) {
                require(expected.getMessage().contains(
                    "outbound transport permanently failed"),
                    "did not establish permanent outbound failure");
            }
            require(partial.size() == 1, "must simulate exactly one byte written");
            require(failedQueue.transportFailed(),
                "first participant stream not terminal");
            require(!healthyQueue.transportFailed(),
                "second participant stream unexpectedly terminal");

            // The second participant's own transport remains healthy, so its
            // final click reaches TradeService. Preflight must stop transfer
            // because the FIRST participant's stream was already poisoned.
            String result = TradeService.handleWidget(second, 3546);
            requireContains(result, "TRADE_COMMIT_REJECTED_OUTBOUND_UNHEALTHY");
            require(first.bank().inventoryCount(995) == 1000,
                "failed peer lost coins");
            require(first.bank().inventoryCount(385) == 0,
                "failed peer gained shark");
            require(second.bank().inventoryCount(995) == 0,
                "healthy peer gained coins");
            require(second.bank().inventoryCount(385) == 2,
                "healthy peer lost shark");
            require(!TradeService.active(first) && !TradeService.active(second),
                "failed trade still active");
            require(saves.get() == 0,
                "rejected trade invoked account-save callbacks");
            require(!healthyQueue.transportFailed(),
                "counterparty rejection poisoned healthy stream");
            System.out.println(
                "R53_TRADE_COUNTERPARTY_TERMINAL_OUTBOUND_FENCE_PASS " +
                "partialWriteBeforeCommit=true healthyPartnerFinalAccept=true " +
                "bothInventoriesUnchanged=true noSaveCallbacks=true " +
                "tradeCancelled=true healthyQueueUnpoisoned=true " +
                "postCommitCrashAtomicityNotClaimed=true");
        } finally {
            TradeService.unregister(first);
            TradeService.unregister(second);
            world.unregisterPlayer(first);
            world.unregisterPlayer(second);
            world.close();
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void requireContains(String actual, String expected) {
        require(actual != null && actual.contains(expected),
            "expected '" + expected + "' in: " + actual);
    }

    private R53TradePartnerTransportFenceTest() {}
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R55: deterministic fault injection into either participant's final
 * publication after reciprocal inventory mutations have occurred.
 * No game socket, real account, or client is involved.
 */
public final class R55TradePublicationRollbackTest {
    public static void main(String[] args) throws Exception {
        scenario(true);
        scenario(false);
        pendingQueueFailClosed();
        System.out.println(
            "R55_TRADE_PUBLICATION_ROLLBACK_PASS " +
            "firstWriterFailureRestored=true " +
            "secondWriterFailureRestored=true " +
            "exactInventorySlotsRestored=true " +
            "tradeCancelled=true noSaveCallbacks=true " +
            "queuedCipherFailClosed=true " +
            "alreadySentClientBytesNotRetracted=true " +
            "dualAccountCrashAtomicityNotProven=true");
    }

    private static void scenario(boolean failFirst) throws Exception {
        World world = World.isolatedForTest(60_000L);
        WorldPlayer a = new WorldPlayer();
        WorldPlayer b = new WorldPlayer();
        world.registerPlayer(a, "opensrc");
        world.registerPlayer(b, "src");
        ToggleOutputStream outputA = new ToggleOutputStream();
        ToggleOutputStream outputB = new ToggleOutputStream();
        ServerPacketWriter writerA = new ServerPacketWriter(
            outputA, new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter writerB = new ServerPacketWriter(
            outputB, new IsaacCipher(new int[]{5,6,7,8}));
        AtomicInteger saves = new AtomicInteger();
        try {
            a.bank().spawnItem(995, 1000, writerA);
            b.bank().spawnItem(385, 2, writerB);
            TradeService.register(
                world, a, a.bank(), writerA, saves::incrementAndGet);
            TradeService.register(
                world, b, b.bank(), writerB, saves::incrementAndGet);
            String opened = TradeService.start(world, a, b);
            expect(opened, "TRADE_UI_OPEN");

            expect(TradeService.handleItemAction(a,
                new ItemContainerAction(
                    145, 3322, 0, 995, 0, "ITEM_ACTION_1")),
                "TRADE_OFFER_OK");
            int slot = EngineR4TradeFlowTest.find(b.bank(), 385);
            check(slot >= 0, "shark slot not found");
            expect(TradeService.handleItemAction(b,
                new ItemContainerAction(
                    145, 3322, slot, 385, 0, "ITEM_ACTION_1")),
                "TRADE_OFFER_OK");
            expect(TradeService.handleWidget(a, 3420), "WAITING_OTHER");
            expect(TradeService.handleWidget(b, 3420), "CONFIRM_OPEN");
            expect(TradeService.handleWidget(a, 3546), "WAITING_OTHER");

            BankState.Stack[] beforeA = a.bank().captureTradeInventory();
            BankState.Stack[] beforeB = b.bank().captureTradeInventory();

            if (failFirst) outputA.fail = true;
            else outputB.fail = true;
            try {
                TradeService.handleWidget(b, 3546);
                throw new AssertionError("expected final writer IOException");
            } catch (IOException expected) {
                check(expected.getMessage().contains("R55 forced output error"),
                    "unexpected publication exception: " + expected);
            }

            compare(beforeA, a.bank().captureTradeInventory(), "first");
            compare(beforeB, b.bank().captureTradeInventory(), "second");
            check(saves.get() == 0,
                "rejected transfer scheduled an account save");
            check(!TradeService.active(a) && !TradeService.active(b),
                "rejected trade remained active");
            // This assertion only covers server state. If the first writer
            // succeeded before the second failed, some bytes might already
            // be visible to that graphical client until reconnection.
        } finally {
            TradeService.unregister(a);
            TradeService.unregister(b);
            world.unregisterPlayer(a);
            world.unregisterPlayer(b);
            world.close();
        }
    }

    private static void pendingQueueFailClosed() throws Exception {
        OutboundPacketQueue queue = new OutboundPacketQueue();
        ServerPacketWriter writer = new ServerPacketWriter(
            queue, new IsaacCipher(new int[]{4,3,2,1}));
        writer.fixed(219, new byte[0]);
        check(queue.queuedPackets() > 0,
            "queued stream fixture must contain prior output");
        writer.beginBatch();
        writer.fixed(219, new byte[0]);
        writer.abortTradeBatchAfterFailure(
            new IOException("R55 synthetic publication failure"));
        check(queue.transportFailed(), "aborted stream not terminal");
        check(queue.queuedPackets() == 0 && queue.queuedBytes() == 0,
            "aborted stream retained pending encrypted frames");
        try {
            writer.fixed(219, new byte[0]);
            throw new AssertionError("resumed an aborted encrypted stream");
        } catch (IOException expected) {
            check(expected.getMessage().contains(
                "outbound transport permanently failed"),
                "unexpected new publication result");
        }
    }

    private static void compare(
        BankState.Stack[] expected,
        BankState.Stack[] actual,
        String who
    ) {
        check(expected.length == actual.length,
            who + " inventory length changed");
        for (int i = 0; i < expected.length; i++) {
            BankState.Stack e = expected[i], a = actual[i];
            if (e == null || a == null) {
                check(e == a, who + " slot " + i + " nullness changed");
            } else {
                check(e.itemId == a.itemId && e.qty == a.qty &&
                    e.tab == a.tab,
                    who + " slot " + i + " item/quantity/tab changed");
            }
        }
    }

    private static void expect(String actual, String marker) {
        check(actual != null && actual.contains(marker),
            "expected " + marker + " got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ToggleOutputStream extends OutputStream {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        boolean fail;

        @Override public void write(int value) throws IOException {
            if (fail) throw new IOException("R55 forced output error");
            sink.write(value);
        }

        @Override public void write(byte[] data,int off,int len)
                throws IOException {
            if (fail) throw new IOException("R55 forced output error");
            sink.write(data, off, len);
        }
    }

    private R55TradePublicationRollbackTest() {}
}

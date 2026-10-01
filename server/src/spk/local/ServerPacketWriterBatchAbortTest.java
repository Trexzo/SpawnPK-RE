package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class ServerPacketWriterBatchAbortTest {
    public static void main(String[] args)throws Exception{
        testExplicitAbortEmitsNothingAndRewindsIsaac();
        testSuccessfulBatchParity();
        testQueueAdmissionFailureRemainsAbortable();
        testStagedPrefixDoesNotLeak();
        System.out.println(
            "SERVER_PACKET_WRITER_BATCH_ABORT_PASS "+
            "zeroBytesOnAbort=true "+
            "isaacRewind=true "+
            "queueFailureAbortable=true "+
            "successfulBatchParity=true "+
            "stagedPrefixLeak=false"
        );
    }

    private static void testExplicitAbortEmitsNothingAndRewindsIsaac()
        throws Exception
    {
        int[] seed={1,2,3,4};
        ByteArrayOutputStream actualOut=
            new ByteArrayOutputStream();
        ServerPacketWriter actual=
            new ServerPacketWriter(
                actualOut,
                new IsaacCipher(seed)
            );

        actual.beginBatch();
        actual.fixed(
            97,
            BootstrapPackets.interface97(41000)
        );
        actual.varShort(
            126,
            BootstrapPackets.widgetText126(
                41019,
                "aborted"
            )
        );
        actual.abortBatch();

        require(
            actualOut.size()==0,
            "explicit abort emitted staged bytes"
        );

        actual.fixed(
            36,
            BootstrapPackets.config36(173,1)
        );

        ByteArrayOutputStream expectedOut=
            new ByteArrayOutputStream();
        ServerPacketWriter expected=
            new ServerPacketWriter(
                expectedOut,
                new IsaacCipher(seed)
            );
        expected.fixed(
            36,
            BootstrapPackets.config36(173,1)
        );

        require(
            Arrays.equals(
                actualOut.toByteArray(),
                expectedOut.toByteArray()
            ),
            "next packet after abort lost ISAAC parity"
        );
    }

    private static void testSuccessfulBatchParity()
        throws Exception
    {
        int[] seed={5,6,7,8};

        ByteArrayOutputStream directOut=
            new ByteArrayOutputStream();
        ServerPacketWriter direct=
            new ServerPacketWriter(
                directOut,
                new IsaacCipher(seed)
            );

        direct.fixed(
            36,
            BootstrapPackets.config36(173,1)
        );
        direct.varShort(
            126,
            BootstrapPackets.widgetText126(
                41019,
                "committed"
            )
        );

        ByteArrayOutputStream batchedOut=
            new ByteArrayOutputStream();
        ServerPacketWriter batched=
            new ServerPacketWriter(
                batchedOut,
                new IsaacCipher(seed)
            );

        batched.beginBatch();
        batched.fixed(
            36,
            BootstrapPackets.config36(173,1)
        );
        batched.varShort(
            126,
            BootstrapPackets.widgetText126(
                41019,
                "committed"
            )
        );
        batched.endBatch();

        require(
            Arrays.equals(
                directOut.toByteArray(),
                batchedOut.toByteArray()
            ),
            "successful batch changed wire bytes"
        );
    }

    private static void testQueueAdmissionFailureRemainsAbortable()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(new byte[1020]);

        int[] seed={9,10,11,12};
        IsaacCipher cipher=
            new IsaacCipher(seed);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                cipher
            );

        writer.beginBatch();
        writer.fixed(
            97,
            BootstrapPackets.interface97(41000)
        );

        boolean failed=false;
        try{
            writer.endBatch();
        }catch(IOException expected){
            failed=true;
        }

        require(
            failed,
            "queue-capacity commit unexpectedly succeeded"
        );
        require(
            queue.queuedBytes()==1020&&
            queue.queuedPackets()==1,
            "failed queue admission leaked staged bytes"
        );

        writer.abortBatch();

        IsaacCipher fresh=
            new IsaacCipher(seed);
        require(
            cipher.nextInt()==fresh.nextInt(),
            "queue-failure abort did not rewind ISAAC"
        );
    }

    private static void testStagedPrefixDoesNotLeak()
        throws Exception
    {
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{13,14,15,16}
                )
            );

        writer.beginBatch();
        writer.fixed(
            97,
            BootstrapPackets.interface97(41000)
        );

        boolean failed=false;
        try{
            writer.varByte(
                1,
                new byte[256]
            );
        }catch(IllegalArgumentException expected){
            failed=true;
            writer.abortBatch();
        }

        require(
            failed,
            "representative staging failure did not throw"
        );
        require(
            out.size()==0,
            "staged root prefix leaked after failure"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }
}

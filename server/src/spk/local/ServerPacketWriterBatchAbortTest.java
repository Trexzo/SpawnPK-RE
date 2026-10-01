package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class ServerPacketWriterBatchAbortTest {
    public static void main(String[] args)throws Exception{
        explicitAbortDiscardsAndRewindsCipher();
        failedQueueCommitRemainsAbortable();
        nestedAbortDiscardsWholeOuterBatch();
        successfulBatchPreservesWireBytes();

        System.out.println(
            "SERVER_PACKET_WRITER_BATCH_ABORT_PASS "+
            "zeroLeak=true isaacRewind=true "+
            "queueFailureAbortable=true nestedWholeAbort=true "+
            "successWireParity=true"
        );
    }

    private static void explicitAbortDiscardsAndRewindsCipher()
        throws Exception
    {
        int[] seed={11,22,33,44};
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        IsaacCipher cipher=
            new IsaacCipher(seed);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                cipher
            );

        writer.beginBatch();
        writer.fixed(
            10,
            new byte[]{1,2,3}
        );
        writer.varByte(
            11,
            new byte[]{4,5}
        );
        writer.abortBatch();

        if(out.size()!=0)
            throw new AssertionError(
                "aborted batch leaked bytes="+
                out.size()
            );

        writer.fixed(
            20,
            new byte[]{9,8}
        );

        ByteArrayOutputStream expectedOut=
            new ByteArrayOutputStream();
        ServerPacketWriter expected=
            new ServerPacketWriter(
                expectedOut,
                new IsaacCipher(seed)
            );
        expected.fixed(
            20,
            new byte[]{9,8}
        );

        if(!Arrays.equals(
                out.toByteArray(),
                expectedOut.toByteArray()))
            throw new AssertionError(
                "ISAAC state did not rewind after abort"
            );
    }

    private static void failedQueueCommitRemainsAbortable()
        throws Exception
    {
        int[] seed={5,6,7,8};
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);

        byte[] existing=
            new byte[1020];
        Arrays.fill(
            existing,
            (byte)0x5a
        );
        queue.offer(existing);

        IsaacCipher cipher=
            new IsaacCipher(seed);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                cipher
            );

        writer.beginBatch();
        writer.fixed(
            42,
            new byte[]{1,2,3,4}
        );

        boolean failed=false;
        try{
            writer.endBatch();
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected bounded queue admission failure"
            );
        if(queue.queuedBytes()!=existing.length||
           queue.queuedPackets()!=1)
            throw new AssertionError(
                "failed batch leaked queue bytes bytes="+
                queue.queuedBytes()+
                " packets="+
                queue.queuedPackets()
            );

        writer.abortBatch();

        IsaacCipher fresh=
            new IsaacCipher(seed);
        int actualNext=
            cipher.nextInt();
        int expectedNext=
            fresh.nextInt();

        if(actualNext!=expectedNext)
            throw new AssertionError(
                "queue-failed batch did not rewind ISAAC expected="+
                expectedNext+
                " actual="+
                actualNext
            );
    }

    private static void nestedAbortDiscardsWholeOuterBatch()
        throws Exception
    {
        int[] seed={9,10,11,12};
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(seed)
            );

        writer.beginBatch();
        writer.fixed(
            30,
            new byte[]{1}
        );
        writer.beginBatch();
        writer.fixed(
            31,
            new byte[]{2}
        );
        writer.abortBatch();

        if(out.size()!=0)
            throw new AssertionError(
                "nested abort leaked bytes"
            );

        writer.fixed(
            32,
            new byte[]{3}
        );

        ByteArrayOutputStream expectedOut=
            new ByteArrayOutputStream();
        ServerPacketWriter expected=
            new ServerPacketWriter(
                expectedOut,
                new IsaacCipher(seed)
            );
        expected.fixed(
            32,
            new byte[]{3}
        );

        if(!Arrays.equals(
                out.toByteArray(),
                expectedOut.toByteArray()))
            throw new AssertionError(
                "nested abort did not rewind whole outer batch"
            );
    }

    private static void successfulBatchPreservesWireBytes()
        throws Exception
    {
        int[] seed={101,202,303,404};

        ByteArrayOutputStream batchedOut=
            new ByteArrayOutputStream();
        ServerPacketWriter batched=
            new ServerPacketWriter(
                batchedOut,
                new IsaacCipher(seed)
            );

        batched.beginBatch();
        batched.fixed(
            50,
            new byte[]{1,2}
        );
        batched.varByte(
            51,
            new byte[]{3,4,5}
        );
        batched.varShort(
            52,
            new byte[]{6,7,8,9}
        );
        batched.endBatch();

        ByteArrayOutputStream directOut=
            new ByteArrayOutputStream();
        ServerPacketWriter direct=
            new ServerPacketWriter(
                directOut,
                new IsaacCipher(seed)
            );

        direct.fixed(
            50,
            new byte[]{1,2}
        );
        direct.varByte(
            51,
            new byte[]{3,4,5}
        );
        direct.varShort(
            52,
            new byte[]{6,7,8,9}
        );

        if(!Arrays.equals(
                batchedOut.toByteArray(),
                directOut.toByteArray()))
            throw new AssertionError(
                "successful batching changed wire bytes"
            );
    }
}

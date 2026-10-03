package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class ServerPacketWriterBatchAbortTest {
    public static void main(String[] args)throws Exception{
        ordinaryDirectFailureLatchesTerminal();
        explicitAbortDiscardsAndRewindsCipher();
        failedQueueCommitRemainsAbortable();
        nestedAbortDiscardsWholeOuterBatch();
        atomicPairAbortReleasesReservationAndRewindsCipher();
        successfulBatchPreservesWireBytes();

        System.out.println(
            "SERVER_PACKET_WRITER_BATCH_ABORT_PASS "+
            "ordinaryDirectTerminal=true directNoRetouch=true "+
            "zeroLeak=true isaacRewind=true "+
            "queueFailureAbortable=true batchFailureNonTerminal=true "+
            "nestedWholeAbort=true pairAbort=true successWireParity=true"
        );
    }

    private static void ordinaryDirectFailureLatchesTerminal()
        throws Exception
    {
        PartialFailOutputStream out=
            new PartialFailOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        boolean failed=false;
        try{
            writer.fixed(
                97,
                BootstrapPackets.interface97(
                    15106
                )
            );
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "ordinary direct partial publication did not fail"
            );
        if(!writer.terminal())
            throw new AssertionError(
                "ordinary direct partial failure left writer live"
            );

        int attemptsBeforeProbe=
            out.attempts;
        out.fail=false;

        boolean probeRejected=false;
        try{
            writer.fixed(
                219,
                new byte[0]
            );
        }catch(IOException terminal){
            probeRejected=true;
        }

        if(!probeRejected)
            throw new AssertionError(
                "terminal direct writer accepted later publication"
            );
        if(out.attempts!=attemptsBeforeProbe)
            throw new AssertionError(
                "terminal direct writer retouched output before="+
                attemptsBeforeProbe+
                " after="+
                out.attempts
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
        if(writer.terminal())
            throw new AssertionError(
                "abortable queue batch failure terminalized writer"
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

    private static void atomicPairAbortReleasesReservationAndRewindsCipher()
        throws Exception
    {
        int[] seedA={41,42,43,44};
        int[] seedB={51,52,53,54};
        OutboundPacketQueue queueA=
            new OutboundPacketQueue(1024);
        OutboundPacketQueue queueB=
            new OutboundPacketQueue(1024);
        ServerPacketWriter writerA=
            new ServerPacketWriter(
                queueA,
                new IsaacCipher(seedA)
            );
        ServerPacketWriter writerB=
            new ServerPacketWriter(
                queueB,
                new IsaacCipher(seedB)
            );

        ServerPacketWriter.AtomicPairBatch pair=
            ServerPacketWriter.beginAtomicQueuePair(
                writerA,
                2,
                writerB,
                2
            );

        if(pair==null)
            throw new AssertionError(
                "queue-backed pair was not reserved"
            );

        writerA.fixed(
            60,
            new byte[]{1}
        );
        writerB.fixed(
            61,
            new byte[]{2}
        );
        pair.abort();

        if(queueA.queuedBytes()!=0||
           queueB.queuedBytes()!=0)
            throw new AssertionError(
                "pair abort leaked queued bytes"
            );

        writerA.fixed(
            62,
            new byte[]{3}
        );
        writerB.fixed(
            63,
            new byte[]{4}
        );

        ByteArrayOutputStream actualA=
            new ByteArrayOutputStream();
        ByteArrayOutputStream actualB=
            new ByteArrayOutputStream();
        queueA.drainTo(
            actualA,
            1024
        );
        queueB.drainTo(
            actualB,
            1024
        );

        ByteArrayOutputStream expectedA=
            new ByteArrayOutputStream();
        ByteArrayOutputStream expectedB=
            new ByteArrayOutputStream();
        new ServerPacketWriter(
            expectedA,
            new IsaacCipher(seedA)
        ).fixed(
            62,
            new byte[]{3}
        );
        new ServerPacketWriter(
            expectedB,
            new IsaacCipher(seedB)
        ).fixed(
            63,
            new byte[]{4}
        );

        if(!Arrays.equals(
                actualA.toByteArray(),
                expectedA.toByteArray())||
           !Arrays.equals(
                actualB.toByteArray(),
                expectedB.toByteArray()))
            throw new AssertionError(
                "pair abort did not rewind cipher/reservation state"
            );
    }

    private static final class PartialFailOutputStream
        extends java.io.OutputStream {

        int attempts;
        boolean fail=true;

        @Override public void write(
            int value
        )throws IOException{
            attempts++;
            if(fail)
                throw new IOException(
                    "EXPECTED_ORDINARY_DIRECT_PARTIAL_FAILURE"
                );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            attempts++;

            if(fail)
                throw new IOException(
                    "EXPECTED_ORDINARY_DIRECT_PARTIAL_FAILURE"
                );
        }
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

package spk.local;

import java.io.*;
import java.util.*;

public final class OutboundPacketQueueTest {
    public static void main(String[] args)throws Exception{
        OutboundPacketQueue q=new OutboundPacketQueue(1024);
        q.offer(new byte[]{1,2,3});q.offer(new byte[]{4,5});
        if(q.queuedBytes()!=5||q.queuedPackets()!=2)throw new AssertionError("queue counters");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        int n=q.drainTo(out,1024);
        if(n!=5||!Arrays.equals(out.toByteArray(),new byte[]{1,2,3,4,5})||q.queuedBytes()!=0)throw new AssertionError("drain");
        IsaacCipher cipher=new IsaacCipher(new int[]{0,0,0,0});
        ServerPacketWriter writer=new ServerPacketWriter(q,cipher);
        writer.fixed(81,new byte[]{9,8,7});
        if(q.queuedBytes()!=4)throw new AssertionError("queued writer expected opcode+payload got="+q.queuedBytes());
        boolean overflow=false;try{q.offer(new byte[1024]);}catch(IOException ok){overflow=true;}
        if(!overflow||!q.overflowed())throw new AssertionError("overflow policy");

        testOrdinaryOverflowTerminalLatch();
        testSocketDrainFailureTerminalLatch();
        testExplicitBatchAbort();
        testFailedBatchAdmissionAbort();
        testSuccessfulBatchParity();

        System.out.println("V512_OUTBOUND_QUEUE_PASS worldThreadSocketWrite=false bounded=true overflowFailClosed=true ordinaryOverflowWriterTerminal=true ordinaryOverflowNoRetouch=true socketDrainWriterTerminal=true socketDrainNoFurtherPublication=true replacementWriterHealthy=true successfulSocketDrain=true batchAbortZeroLeak=true batchAbortIsaacRewind=true failedBatchAdmissionAbortable=true batchAdmissionWriterHealthy=true successfulBatchParity=true");
    }

    private static void testOrdinaryOverflowTerminalLatch()
        throws Exception
    {
        int[] seed={7,8,9,10};
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );

        IsaacCipher cipher=
            new IsaacCipher(seed);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                cipher
            );

        boolean failed=false;
        try{
            writer.fixed(
                219,
                new byte[0]
            );
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "ordinary full-queue publication did not fail"
            );
        if(!queue.overflowed())
            throw new AssertionError(
                "ordinary full-queue publication did not poison queue"
            );
        if(!writer.terminal())
            throw new AssertionError(
                "ordinary full-queue publication left writer live"
            );
        if(queue.queuedBytes()!=1024)
            throw new AssertionError(
                "ordinary full-queue publication leaked packet bytes="+
                queue.queuedBytes()
            );

        IsaacCipher expected=
            new IsaacCipher(seed);
        expected.nextInt();

        boolean probeRejected=false;
        try{
            writer.fixed(
                97,
                BootstrapPackets.interface97(
                    15106
                )
            );
        }catch(IOException terminal){
            probeRejected=true;
        }

        if(!probeRejected)
            throw new AssertionError(
                "terminal overflow writer accepted later publication"
            );

        int actualNext=
            cipher.nextInt();
        int expectedNext=
            expected.nextInt();

        if(actualNext!=expectedNext)
            throw new AssertionError(
                "terminal overflow writer consumed another ISAAC value expected="+
                expectedNext+
                " actual="+
                actualNext
            );
    }

    private static void testSocketDrainFailureTerminalLatch()
        throws Exception
    {
        int[] seed={41,42,43,44};
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(seed)
            );

        writer.fixed(
            97,
            BootstrapPackets.interface97(
                15106
            )
        );

        PartialSocketFailOutputStream failingOut=
            new PartialSocketFailOutputStream();

        boolean failed=false;
        try{
            LocalSession.drainSessionOutbound(
                queue,
                writer,
                failingOut,
                1024
            );
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "socket drain failure was not propagated"
            );
        if(!writer.terminal())
            throw new AssertionError(
                "socket drain failure left session writer live"
            );
        if(failingOut.attempts!=1)
            throw new AssertionError(
                "socket drain failure attempt count="+
                failingOut.attempts
            );
        if(queue.queuedBytes()!=0||
           queue.queuedPackets()!=0)
            throw new AssertionError(
                "failed socket drain retained removed queue packet"
            );

        int queuedBeforeProbe=
            queue.queuedBytes();
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
                "terminal socket-drain writer accepted later publication"
            );
        if(queue.queuedBytes()!=
                queuedBeforeProbe)
            throw new AssertionError(
                "terminal socket-drain writer queued later bytes"
            );

        OutboundPacketQueue replacementQueue=
            new OutboundPacketQueue(1024);
        ServerPacketWriter replacement=
            new ServerPacketWriter(
                replacementQueue,
                new IsaacCipher(
                    new int[]{45,46,47,48}
                )
            );
        replacement.fixed(
            219,
            new byte[0]
        );

        if(replacement.terminal()||
           replacementQueue.queuedBytes()!=1)
            throw new AssertionError(
                "socket drain terminal latch affected replacement writer"
            );

        OutboundPacketQueue successQueue=
            new OutboundPacketQueue(1024);
        ServerPacketWriter successWriter=
            new ServerPacketWriter(
                successQueue,
                new IsaacCipher(
                    new int[]{49,50,51,52}
                )
            );
        successWriter.fixed(
            219,
            new byte[0]
        );

        ByteArrayOutputStream successOut=
            new ByteArrayOutputStream();
        int drained=
            LocalSession.drainSessionOutbound(
                successQueue,
                successWriter,
                successOut,
                1024
            );

        if(drained!=1||
           successOut.size()!=1||
           successQueue.queuedBytes()!=0||
           successWriter.terminal())
            throw new AssertionError(
                "successful session socket drain changed behavior"
            );
    }

    private static void testExplicitBatchAbort()
        throws Exception
    {
        int[] seed={11,12,13,14};
        OutboundPacketQueue actualQueue=new OutboundPacketQueue(1024);
        ServerPacketWriter actual=new ServerPacketWriter(actualQueue,new IsaacCipher(seed));

        actual.beginBatch();
        actual.fixed(219,new byte[0]);
        actual.varShort(126,BootstrapPackets.widgetText126(100,"ABORT_ME"));
        actual.abortBatch();

        if(actualQueue.queuedBytes()!=0||actualQueue.queuedPackets()!=0)
            throw new AssertionError("explicit abort leaked staged bytes");

        byte[] expected=singleFixedPacket(seed,97,BootstrapPackets.interface97(15106));
        actual.fixed(97,BootstrapPackets.interface97(15106));
        byte[] observed=drain(actualQueue);

        if(!Arrays.equals(expected,observed))
            throw new AssertionError("explicit abort did not rewind ISAAC");
    }

    private static void testFailedBatchAdmissionAbort()
        throws Exception
    {
        int[] seed={21,22,23,24};
        OutboundPacketQueue queue=new OutboundPacketQueue(1024);
        queue.offer(new byte[1023]);
        ServerPacketWriter writer=new ServerPacketWriter(queue,new IsaacCipher(seed));

        writer.beginBatch();
        writer.fixed(219,new byte[0]);
        writer.fixed(97,BootstrapPackets.interface97(17100));

        boolean failed=false;
        try{
            writer.endBatch();
        }catch(IOException expected){
            failed=expected.getMessage()!=null&&expected.getMessage().contains("batch admission unavailable");
        }

        if(!failed)throw new AssertionError("full queue did not reject outer batch");
        if(queue.overflowed())throw new AssertionError("failed batch admission poisoned queue");
        if(queue.queuedBytes()!=1023)throw new AssertionError("failed batch admission leaked staged bytes");
        if(writer.terminal())throw new AssertionError("abortable batch admission failure terminalized writer");

        writer.abortBatch();
        drain(queue);

        byte[] expected=singleFixedPacket(seed,97,BootstrapPackets.interface97(15106));
        writer.fixed(97,BootstrapPackets.interface97(15106));
        byte[] observed=drain(queue);

        if(!Arrays.equals(expected,observed))
            throw new AssertionError("failed batch admission abort did not rewind ISAAC");
    }

    private static void testSuccessfulBatchParity()
        throws Exception
    {
        int[] seed={31,32,33,34};
        OutboundPacketQueue batchedQueue=new OutboundPacketQueue(1024);
        ServerPacketWriter batched=new ServerPacketWriter(batchedQueue,new IsaacCipher(seed));
        batched.beginBatch();
        batched.fixed(219,new byte[0]);
        batched.fixed(97,BootstrapPackets.interface97(15106));
        batched.endBatch();

        OutboundPacketQueue directQueue=new OutboundPacketQueue(1024);
        ServerPacketWriter direct=new ServerPacketWriter(directQueue,new IsaacCipher(seed));
        direct.fixed(219,new byte[0]);
        direct.fixed(97,BootstrapPackets.interface97(15106));

        if(!Arrays.equals(drain(batchedQueue),drain(directQueue)))
            throw new AssertionError("successful batching changed wire bytes");
    }

    private static final class PartialSocketFailOutputStream
        extends OutputStream {

        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        int attempts;

        @Override public void write(
            int value
        )throws IOException{
            bytes.write(value);
            attempts++;
            throw new IOException(
                "EXPECTED_SOCKET_DRAIN_FAILURE"
            );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            attempts++;
            if(length>0)
                bytes.write(
                    data[offset]
                );
            throw new IOException(
                "EXPECTED_SOCKET_DRAIN_FAILURE"
            );
        }
    }

    private static byte[] singleFixedPacket(
        int[] seed,
        int opcode,
        byte[] body
    )throws Exception{
        OutboundPacketQueue queue=new OutboundPacketQueue(1024);
        ServerPacketWriter writer=new ServerPacketWriter(queue,new IsaacCipher(seed));
        writer.fixed(opcode,body);
        return drain(queue);
    }

    private static byte[] drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        queue.drainTo(out,1<<20);
        return out.toByteArray();
    }

}

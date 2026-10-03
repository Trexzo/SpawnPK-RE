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
        testExplicitBatchAbort();
        testFailedBatchAdmissionAbort();
        testSuccessfulBatchParity();

        System.out.println("V512_OUTBOUND_QUEUE_PASS worldThreadSocketWrite=false bounded=true overflowFailClosed=true ordinaryOverflowWriterTerminal=true ordinaryOverflowNoRetouch=true batchAbortZeroLeak=true batchAbortIsaacRewind=true failedBatchAdmissionAbortable=true batchAdmissionWriterHealthy=true successfulBatchParity=true");
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

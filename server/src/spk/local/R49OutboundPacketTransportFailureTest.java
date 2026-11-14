package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * R49 fault-injected socket transport tests. No network or live game state.
 * A partial encrypted packet has no safe retry boundary, so the entire
 * session-owned outbound queue must fail closed permanently.
 */
public final class R49OutboundPacketTransportFailureTest {
    private static final int[] SEED={7,9,11,13};

    private static final class PrefixThenFail extends OutputStream {
        final ByteArrayOutputStream delivered=new ByteArrayOutputStream();
        final int prefixLimit;
        int writeAttempts;
        int flushAttempts;

        PrefixThenFail(int prefixLimit){this.prefixLimit=prefixLimit;}

        @Override public void write(int value)throws IOException{
            writeAttempts++;
            if(delivered.size()>=prefixLimit)
                throw new IOException("R49 injected socket closed after prefix");
            delivered.write(value);
        }

        @Override public void flush()throws IOException{
            flushAttempts++;
        }
    }

    private static final class FlushThenFail extends OutputStream {
        final ByteArrayOutputStream delivered=new ByteArrayOutputStream();
        int flushAttempts;

        @Override public void write(int value){
            delivered.write(value);
        }

        @Override public void flush()throws IOException{
            flushAttempts++;
            throw new IOException("R49 injected socket flush failure");
        }
    }

    public static void main(String[] args)throws Exception{
        normalFifoAndWriterParity();
        partialWriteMustNeverReplay();
        flushFailureMustNeverResume();
        existingOverflowPolicyRemains();
        System.out.println(
            "R49_OUTBOUND_PARTIAL_IO_FAIL_CLOSED_PASS "+
            "normalFifoParity=true prefixFailureNoRetry=true "+
            "failedOfferDenied=true pendingTailDiscarded=true "+
            "flushFailureNoRetry=true existingOverflowUnchanged=true"
        );
    }

    private static void normalFifoAndWriterParity()throws Exception{
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        ByteArrayOutputStream expected=new ByteArrayOutputStream();
        ServerPacketWriter direct=new ServerPacketWriter(
            expected,new IsaacCipher(SEED.clone()));

        writer.fixed(73,new byte[]{1,2,3,4});
        direct.fixed(73,new byte[]{1,2,3,4});
        writer.varByte(103,new byte[]{9,8,7});
        direct.varByte(103,new byte[]{9,8,7});

        if(queue.queuedPackets()!=2)
            throw new AssertionError("normal pending handoff should have 2 packets");
        ByteArrayOutputStream socket=new ByteArrayOutputStream();
        int written=queue.drainTo(socket,4096);
        if(written!=expected.size()||
           !Arrays.equals(socket.toByteArray(),expected.toByteArray())||
           queue.queuedPackets()!=0||queue.queuedBytes()!=0||
           queue.transportFailed())
            throw new AssertionError("normal FIFO/ISAAC writer parity drift");
    }

    private static void partialWriteMustNeverReplay()throws Exception{
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        ByteArrayOutputStream expected=new ByteArrayOutputStream();
        ServerPacketWriter direct=new ServerPacketWriter(
            expected,new IsaacCipher(SEED.clone()));
        writer.fixed(53,new byte[]{1,2,3,4,5});
        direct.fixed(53,new byte[]{1,2,3,4,5});
        writer.fixed(65,new byte[]{6,7,8,9});
        direct.fixed(65,new byte[]{6,7,8,9});
        if(queue.queuedPackets()!=2)
            throw new AssertionError("partial test needs two exact queued frames");

        PrefixThenFail partial=new PrefixThenFail(3);
        IOException original=null;
        try{
            queue.drainTo(partial,8192);
        }catch(IOException failure){
            original=failure;
        }
        if(original==null||original.getCause()==null||
           !original.getMessage().contains("no retry")||
           !queue.transportFailed()||
           queue.queuedBytes()!=0||queue.queuedPackets()!=0||
           !Arrays.equals(partial.delivered.toByteArray(),
                          Arrays.copyOf(expected.toByteArray(),3)))
            throw new AssertionError(
                "partial write must poison session, discard tail and keep prefix"
            );

        int attemptsBefore=partial.writeAttempts;
        IOException failedAgain=null;
        try{
            queue.drainTo(partial,8192);
        }catch(IOException expectedFailure){failedAgain=expectedFailure;}
        if(failedAgain==null||
           partial.writeAttempts!=attemptsBefore)
            throw new AssertionError("partial socket was retried after failure");

        try{
            queue.offer(new byte[]{42});
            throw new AssertionError("queue accepted new bytes after partial failure");
        }catch(IOException correct){/* No replay possible. */}

        try{
            writer.fixed(104,new byte[]{42});
            throw new AssertionError("encrypted packet writer resumed after partial failure");
        }catch(IOException correct){/* Original session should terminate. */}
    }

    private static void flushFailureMustNeverResume()throws Exception{
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        writer.fixed(53,new byte[]{1,2,3,4});
        writer.fixed(65,new byte[]{5,6,7,8});
        FlushThenFail socket=new FlushThenFail();
        IOException failure=null;
        try{
            // First packet fits, second is intentionally held by budget.
            queue.drainTo(socket,5);
        }catch(IOException expected){failure=expected;}
        if(failure==null||!queue.transportFailed()||
           socket.flushAttempts!=1||socket.delivered.size()!=5||
           queue.queuedPackets()!=0||queue.queuedBytes()!=0)
            throw new AssertionError("flush failure did not discard undelivered tail");
        try{
            queue.drainTo(socket,4096);
            throw new AssertionError("flush failure retried encrypted stream");
        }catch(IOException correct){/* Sticky failure. */}
        if(socket.flushAttempts!=1||socket.delivered.size()!=5)
            throw new AssertionError("flush failure attempted a second transport call");
    }

    private static void existingOverflowPolicyRemains()throws Exception{
        OutboundPacketQueue queue=new OutboundPacketQueue(1024);
        queue.offer(new byte[1024]);
        try{
            queue.offer(new byte[]{1});
            throw new AssertionError("queue overflow accepted");
        }catch(IOException expected){/* Existing bounded overflow path. */}
        if(!queue.overflowed()||queue.transportFailed()||
           queue.queuedBytes()!=1024)
            throw new AssertionError("capacity overflow confused with socket failure");
    }
}

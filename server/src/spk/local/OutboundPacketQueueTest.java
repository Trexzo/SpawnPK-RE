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
        System.out.println("V512_OUTBOUND_QUEUE_PASS worldThreadSocketWrite=false bounded=true overflowFailClosed=true");
    }
}

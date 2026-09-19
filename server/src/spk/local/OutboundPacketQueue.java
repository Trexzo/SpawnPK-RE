package spk.local;

import java.io.*;
import java.util.*;

/** Bounded non-blocking handoff from World publication to connection I/O. */
final class OutboundPacketQueue {
    private static final int DEFAULT_MAX_BYTES=1<<20;
    private final ArrayDeque<byte[]> q=new ArrayDeque<>();
    private final int maxBytes;
    private int bytes;
    private boolean overflowed;
    OutboundPacketQueue(){this(DEFAULT_MAX_BYTES);}
    OutboundPacketQueue(int maxBytes){if(maxBytes<1024)throw new IllegalArgumentException();this.maxBytes=maxBytes;}
    synchronized void offer(byte[] data)throws IOException{
        if(data==null||data.length==0)return;
        if(overflowed)throw new IOException("outbound queue already overflowed");
        if(bytes+data.length>maxBytes){overflowed=true;throw new IOException("outbound queue overflow bytes="+bytes+" add="+data.length+" max="+maxBytes);}
        q.addLast(data.clone());bytes+=data.length;
    }
    int drainTo(OutputStream out,int maxBytesPerDrain)throws IOException{
        int written=0;
        for(;;){byte[] b;synchronized(this){b=q.peekFirst();if(b==null||written>0&&written+b.length>maxBytesPerDrain)break;q.removeFirst();bytes-=b.length;}out.write(b);written+=b.length;}
        if(written>0)out.flush();
        return written;
    }
    synchronized int queuedBytes(){return bytes;}
    synchronized int queuedPackets(){return q.size();}
    synchronized boolean overflowed(){return overflowed;}
}

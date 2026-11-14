package spk.local;

import java.io.*;
import java.util.*;

/**
 * Bounded non-blocking handoff from World publication to connection I/O.
 *
 * R49: an OutputStream may write an arbitrary packet prefix and then throw.
 * Such a stream can NEVER safely resume at the next queued packet: the client
 * would interpret subsequent encrypted opcodes as the missing packet tail.
 * Failed write/flush permanently poisons this session-owned outbound queue,
 * drops remaining unsent bytes, and refuses all further offers and drains.
 * No retry or gameplay rollback is claimed.
 */
final class OutboundPacketQueue {
    private static final int DEFAULT_MAX_BYTES=1<<20;
    private final ArrayDeque<byte[]> q=new ArrayDeque<>();
    private final int maxBytes;
    private final Object drainLock=new Object();
    private int bytes;
    private boolean overflowed;
    private IOException terminalTransportFailure;

    OutboundPacketQueue(){this(DEFAULT_MAX_BYTES);}
    OutboundPacketQueue(int maxBytes){
        if(maxBytes<1024)throw new IllegalArgumentException();
        this.maxBytes=maxBytes;
    }

    synchronized void offer(byte[] data)throws IOException{
        requireTransportHealthy();
        if(data==null||data.length==0)return;
        if(overflowed)throw new IOException("outbound queue already overflowed");
        if(bytes+data.length>maxBytes){
            overflowed=true;
            throw new IOException(
                "outbound queue overflow bytes="+bytes+
                " add="+data.length+" max="+maxBytes
            );
        }
        q.addLast(data.clone());
        bytes+=data.length;
    }

    /**
     * Serializes socket-drain owners without blocking World publishers on the
     * same monitor for the duration of network I/O. The socket owner normally
     * calls this on one session thread, but a second drain must not reorder.
     */
    int drainTo(OutputStream out,int maxBytesPerDrain)throws IOException{
        Objects.requireNonNull(out,"out");
        synchronized(drainLock){
            synchronized(this){requireTransportHealthy();}
            int written=0;
            try{
                for(;;){
                    byte[] b;
                    synchronized(this){
                        requireTransportHealthy();
                        b=q.peekFirst();
                        if(b==null||
                           written>0&&written+b.length>maxBytesPerDrain)
                            break;
                        // Removing before write is deliberate: after even a
                        // one-byte write failure, replay is not safe.
                        q.removeFirst();
                        bytes-=b.length;
                    }
                    out.write(b);
                    written+=b.length;
                }
                if(written>0)out.flush();
                return written;
            }catch(IOException failure){
                synchronized(this){
                    if(terminalTransportFailure==null){
                        terminalTransportFailure=failure;
                        q.clear();
                        bytes=0;
                    }
                }
                throw terminalFailure();
            }
        }
    }

    private synchronized void requireTransportHealthy()throws IOException{
        if(terminalTransportFailure!=null)throw terminalFailure();
    }

    private synchronized IOException terminalFailure(){
        return new IOException(
            "outbound transport permanently failed; partial packet may "+
            "have been sent, no retry or further publication allowed",
            terminalTransportFailure
        );
    }

    /**
     * R50: session/World admission fence. A socket stream that is known
     * truncated, or a queue that has already overflowed, cannot safely
     * accept *new* gameplay mutations. This does not roll back earlier
     * accepted commands or make the final account save transactional.
     *
     * This check is intentionally separate from offer(): callers invoke
     * it before running each typed World request, not after mutating state.
     */
    synchronized void requireGameActionAdmission()throws IOException{
        requireTransportHealthy();
        if(overflowed)
            throw new IOException(
                "outbound queue overflowed; new World actions denied"
            );
    }

    /**
     * R54: conservatively reject a known-near-capacity queue before a
     * multi-packet, two-player transaction mutates inventory. This is only
     * a point-in-time headroom check: later concurrent publication or socket
     * failure can still interrupt the transaction.
     */
    synchronized void requireCommitHeadroom(int minimumFreeBytes)
            throws IOException{
        requireGameActionAdmission();
        if(minimumFreeBytes<1||minimumFreeBytes>maxBytes||
           maxBytes-bytes<minimumFreeBytes)
            throw new IOException(
                "outbound queue lacks required pre-commit headroom "+
                "available="+(maxBytes-bytes)+
                " reserve="+minimumFreeBytes
            );
    }

    synchronized boolean transportFailed(){
        return terminalTransportFailure!=null;
    }

    synchronized int queuedBytes(){return bytes;}
    synchronized int queuedPackets(){return q.size();}
    synchronized boolean overflowed(){return overflowed;}
}

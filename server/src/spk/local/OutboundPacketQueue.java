package spk.local;

import java.io.*;
import java.util.*;

/** Bounded non-blocking handoff from World publication to connection I/O. */
final class OutboundPacketQueue {
    private static final int DEFAULT_MAX_BYTES=1<<20;
    private static final Object PAIR_TIE_LOCK=new Object();
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

    static void offerPair(
        OutboundPacketQueue a,
        byte[] aData,
        OutboundPacketQueue b,
        byte[] bData
    )throws IOException{
        if(a==null||b==null)
            throw new NullPointerException("queue");
        byte[] left=
            aData==null
                ?new byte[0]
                :aData;
        byte[] right=
            bData==null
                ?new byte[0]
                :bData;

        if(a==b){
            synchronized(a){
                a.offerPairSameLocked(
                    left,
                    right
                );
            }
            return;
        }

        int ah=System.identityHashCode(a);
        int bh=System.identityHashCode(b);

        if(ah<bh){
            synchronized(a){
                synchronized(b){
                    offerPairDistinctLocked(
                        a,
                        left,
                        b,
                        right
                    );
                }
            }
            return;
        }

        if(ah>bh){
            synchronized(b){
                synchronized(a){
                    offerPairDistinctLocked(
                        a,
                        left,
                        b,
                        right
                    );
                }
            }
            return;
        }

        synchronized(PAIR_TIE_LOCK){
            synchronized(a){
                synchronized(b){
                    offerPairDistinctLocked(
                        a,
                        left,
                        b,
                        right
                    );
                }
            }
        }
    }
    int drainTo(OutputStream out,int maxBytesPerDrain)throws IOException{
        int written=0;
        for(;;){byte[] b;synchronized(this){b=q.peekFirst();if(b==null||written>0&&written+b.length>maxBytesPerDrain)break;q.removeFirst();bytes-=b.length;}out.write(b);written+=b.length;}
        if(written>0)out.flush();
        return written;
    }
    private static void offerPairDistinctLocked(
        OutboundPacketQueue a,
        byte[] aData,
        OutboundPacketQueue b,
        byte[] bData
    )throws IOException{
        String failure=
            pairFailureLocked(
                a,
                aData.length,
                "a"
            );
        if(failure==null)
            failure=
                pairFailureLocked(
                    b,
                    bData.length,
                    "b"
                );

        if(failure!=null)
            throw new IOException(failure);

        a.enqueueLocked(aData);
        b.enqueueLocked(bData);
    }

    private void offerPairSameLocked(
        byte[] aData,
        byte[] bData
    )throws IOException{
        if(overflowed)
            throw new IOException(
                "outbound queue already overflowed"
            );

        long add=
            (long)aData.length+
            bData.length;

        if((long)bytes+add>maxBytes){
            overflowed=true;
            throw new IOException(
                "outbound queue pair overflow bytes="+
                bytes+
                " add="+add+
                " max="+maxBytes
            );
        }

        enqueueLocked(aData);
        enqueueLocked(bData);
    }

    private static String pairFailureLocked(
        OutboundPacketQueue queue,
        int add,
        String label
    ){
        if(queue.overflowed)
            return "outbound queue "+
                label+
                " already overflowed";

        if((long)queue.bytes+add>
                queue.maxBytes){
            queue.overflowed=true;
            return "outbound queue "+
                label+
                " pair overflow bytes="+
                queue.bytes+
                " add="+add+
                " max="+queue.maxBytes;
        }

        return null;
    }

    private void enqueueLocked(
        byte[] data
    ){
        if(data.length==0)
            return;
        q.addLast(
            data.clone()
        );
        bytes+=data.length;
    }

    synchronized int queuedBytes(){return bytes;}
    synchronized int queuedPackets(){return q.size();}
    synchronized boolean overflowed(){return overflowed;}
}

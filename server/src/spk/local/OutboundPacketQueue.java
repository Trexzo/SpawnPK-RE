package spk.local;

import java.io.*;
import java.util.*;

/** Bounded non-blocking handoff from World publication to connection I/O. */
final class OutboundPacketQueue {
    private static final int DEFAULT_MAX_BYTES=1<<20;
    private static final Object PAIR_RESERVATION_LOCK=
        new Object();

    static final class PairReservation {
        private final OutboundPacketQueue first;
        private final OutboundPacketQueue second;
        private final int firstBytes;
        private final int secondBytes;
        private boolean active=true;

        private PairReservation(
            OutboundPacketQueue first,
            int firstBytes,
            OutboundPacketQueue second,
            int secondBytes
        ){
            this.first=first;
            this.firstBytes=firstBytes;
            this.second=second;
            this.secondBytes=secondBytes;
        }

        void commit(
            byte[] firstData,
            byte[] secondData
        )throws IOException{
            byte[] a=
                firstData==null
                    ?new byte[0]
                    :firstData;
            byte[] b=
                secondData==null
                    ?new byte[0]
                    :secondData;

            synchronized(PAIR_RESERVATION_LOCK){
                lockPair(
                    first,
                    second,
                    ()->{
                        if(!active)
                            throw new IllegalStateException(
                                "outbound pair reservation already completed"
                            );

                        if(a.length!=firstBytes||
                           b.length!=secondBytes)
                            throw new IllegalStateException(
                                "outbound pair reservation size mismatch expected="+
                                firstBytes+"/"+secondBytes+
                                " actual="+a.length+"/"+b.length
                            );

                        first.reservedBytes-=firstBytes;
                        second.reservedBytes-=secondBytes;

                        first.enqueueReservedLocked(a);
                        second.enqueueReservedLocked(b);
                        active=false;
                    }
                );
            }
        }

        void release(){
            synchronized(PAIR_RESERVATION_LOCK){
                lockPairUnchecked(
                    first,
                    second,
                    ()->{
                        if(!active)
                            return;

                        first.reservedBytes-=firstBytes;
                        second.reservedBytes-=secondBytes;
                        active=false;
                    }
                );
            }
        }
    }
    private final ArrayDeque<byte[]> q=new ArrayDeque<>();
    private final int maxBytes;
    private int bytes;
    private int reservedBytes;
    private boolean overflowed;
    OutboundPacketQueue(){this(DEFAULT_MAX_BYTES);}
    OutboundPacketQueue(int maxBytes){if(maxBytes<1024)throw new IllegalArgumentException();this.maxBytes=maxBytes;}
    synchronized void offer(byte[] data)throws IOException{
        if(data==null||data.length==0)return;
        if(overflowed)throw new IOException("outbound queue already overflowed");
        if(bytes+reservedBytes+data.length>maxBytes){
            overflowed=true;
            throw new IOException(
                "outbound queue overflow bytes="+bytes+
                " reserved="+reservedBytes+
                " add="+data.length+
                " max="+maxBytes
            );
        }
        q.addLast(data.clone());
        bytes+=data.length;
    }

    static PairReservation reservePair(
        OutboundPacketQueue first,
        int firstBytes,
        OutboundPacketQueue second,
        int secondBytes
    )throws IOException{
        if(first==null||second==null)
            throw new NullPointerException("pair queue");
        if(first==second)
            throw new IllegalArgumentException(
                "pair reservation requires distinct outbound queues"
            );
        if(firstBytes<0||secondBytes<0)
            throw new IllegalArgumentException(
                "pair reservation bytes"
            );

        synchronized(PAIR_RESERVATION_LOCK){
            final PairReservation[] reservation={null};

            lockPair(
                first,
                second,
                ()->{
                    first.requireReservableLocked(
                        firstBytes
                    );
                    second.requireReservableLocked(
                        secondBytes
                    );

                    first.reservedBytes+=
                        firstBytes;
                    second.reservedBytes+=
                        secondBytes;

                    reservation[0]=
                        new PairReservation(
                            first,
                            firstBytes,
                            second,
                            secondBytes
                        );
                }
            );

            return reservation[0];
        }
    }

    private void requireReservableLocked(
        int requested
    )throws IOException{
        if(overflowed)
            throw new IOException(
                "outbound queue already overflowed"
            );

        if(bytes+reservedBytes+requested>
                maxBytes)
            throw new IOException(
                "outbound queue pair reservation unavailable bytes="+
                bytes+
                " reserved="+reservedBytes+
                " add="+requested+
                " max="+maxBytes
            );
    }

    private void enqueueReservedLocked(
        byte[] data
    ){
        if(data.length==0)
            return;

        q.addLast(
            data.clone()
        );
        bytes+=data.length;
    }

    @FunctionalInterface
    private interface PairAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface PairUncheckedAction {
        void run();
    }

    private static void lockPair(
        OutboundPacketQueue first,
        OutboundPacketQueue second,
        PairAction action
    )throws IOException{
        OutboundPacketQueue lockFirst=
            System.identityHashCode(first)<
                System.identityHashCode(second)
                ?first
                :second;
        OutboundPacketQueue lockSecond=
            lockFirst==first
                ?second
                :first;

        synchronized(lockFirst){
            synchronized(lockSecond){
                action.run();
            }
        }
    }

    private static void lockPairUnchecked(
        OutboundPacketQueue first,
        OutboundPacketQueue second,
        PairUncheckedAction action
    ){
        OutboundPacketQueue lockFirst=
            System.identityHashCode(first)<
                System.identityHashCode(second)
                ?first
                :second;
        OutboundPacketQueue lockSecond=
            lockFirst==first
                ?second
                :first;

        synchronized(lockFirst){
            synchronized(lockSecond){
                action.run();
            }
        }
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

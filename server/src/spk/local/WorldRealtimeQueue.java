package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Narrow sub-tick queue for presentation timings already proven below 600 ms
 * (for example the pet egress/follow reaction). Gameplay timers should use WorldEventQueue.
 */
final class WorldRealtimeQueue implements AutoCloseable {
    interface Ownership {
        boolean owns(
            WorldPlayer player,
            long generation
        );
    }
    private static final class E implements Comparable<E>{
        final long at,seq; final WorldPlayer owner; final long generation; final Runnable task;
        E(long at,long seq,WorldPlayer owner,long generation,Runnable task){this.at=at;this.seq=seq;this.owner=owner;this.generation=generation;this.task=task;}
        public int compareTo(E o){int c=Long.compare(at,o.at);return c!=0?c:Long.compare(seq,o.seq);}
    }
    private final PriorityQueue<E> q=new PriorityQueue<>();
    private final AtomicLong seq=new AtomicLong();
    private final Ownership ownership;
    private boolean closed;

    WorldRealtimeQueue(){
        this(
            (player,generation)->
                player.accepts(generation)
        );
    }

    WorldRealtimeQueue(Ownership ownership){
        this.ownership=
            Objects.requireNonNull(
                ownership,
                "ownership"
            );
    }

    void schedule(
        long atMillis,
        WorldPlayer owner,
        Runnable task
    ){
        if(owner==null||task==null)
            throw new NullPointerException();

        synchronized(owner.mutationLock()){
            scheduleOwned(
                atMillis,
                owner,
                owner.generation(),
                task
            );
        }
    }

    void schedule(
        long atMillis,
        WorldPlayer owner,
        long expectedGeneration,
        Runnable task
    ){
        if(owner==null||task==null)
            throw new NullPointerException();

        synchronized(owner.mutationLock()){
            scheduleOwned(
                atMillis,
                owner,
                expectedGeneration,
                task
            );
        }
    }

    private void scheduleOwned(
        long atMillis,
        WorldPlayer owner,
        long expectedGeneration,
        Runnable task
    ){
        if(!owner.accepts(expectedGeneration))
            throw new IllegalStateException(
                "realtime owner generation changed: "+
                owner.id()+
                " expected="+expectedGeneration+
                " actual="+owner.generation()
            );

        if(!ownership.owns(
                owner,
                expectedGeneration
            ))
            throw new IllegalStateException(
                "realtime owner not owned by world: "+
                owner.id()
            );

        synchronized(this){
            if(closed)
                throw new IllegalStateException(
                    "world realtime queue closed"
                );

            q.add(
                new E(
                    atMillis,
                    seq.incrementAndGet(),
                    owner,
                    expectedGeneration,
                    task
                )
            );
            notifyAll();
        }
    }
    int runDue(long nowMillis){
        int n=0;

        for(;;){
            E e;

            synchronized(this){
                if(closed)return n;
                e=q.peek();
                if(e==null||e.at>nowMillis)
                    return n;
                q.remove();
            }

            if(e.owner.accepts(e.generation)&&
               ownership.owns(
                   e.owner,
                   e.generation
               )){
                try{
                    synchronized(e.owner.mutationLock()){
                        if(e.owner.accepts(
                                e.generation
                            )&&
                           ownership.owns(
                                e.owner,
                                e.generation
                            ))
                            e.task.run();
                    }
                }catch(Throwable t){
                    System.err.println(
                        "[world-realtime] task failed owner="+
                        e.owner.id()+
                        " error="+t
                    );
                }
            }

            n++;
        }
    }
    synchronized int cancelPlayer(WorldPlayer player){int n=0;for(Iterator<E>it=q.iterator();it.hasNext();){if(it.next().owner.id().equals(player.id())){it.remove();n++;}}return n;}
    synchronized long nextDueMillis(){E e=q.peek();return e==null?Long.MAX_VALUE:e.at;}
    synchronized int size(){return q.size();}

    @Override public synchronized void close(){
        if(closed)
            return;

        closed=true;
        q.clear();
        notifyAll();
    }
}
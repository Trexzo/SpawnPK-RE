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

    interface ExecutionOwnership {
        boolean run(
            WorldPlayer player,
            long generation,
            Runnable task
        )throws Exception;
    }

    /**
     * R75 OFFLINE TEST ONLY. Revalidate scheduled realtime actions at
     * producer admission and under the actual World mutation lock before
     * callback dispatch. Live/default queue runs its original direct path.
     */
    @FunctionalInterface
    interface OfflineRealtimeAdmission {
        boolean permits(WorldPlayer player,long generation,Runnable task);
    }

    private volatile OfflineRealtimeAdmission offlineRealtimeAdmission;

    synchronized void installOfflineRealtimeAdmissionForTest(
        OfflineRealtimeAdmission policy
    ){
        if(offlineRealtimeAdmission!=null)
            throw new IllegalStateException(
                "R75_REALTIME_GATE_DENIED already installed"
            );
        offlineRealtimeAdmission=Objects.requireNonNull(
            policy,"policy"
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
    private final ExecutionOwnership executionOwnership;
    private boolean closed;

    WorldRealtimeQueue(){
        this(
            (player,generation)->
                player.accepts(generation)
        );
    }

    WorldRealtimeQueue(Ownership ownership){
        this(
            ownership,
            null
        );
    }

    WorldRealtimeQueue(
        Ownership ownership,
        ExecutionOwnership executionOwnership
    ){
        this.ownership=
            Objects.requireNonNull(
                ownership,
                "ownership"
            );
        this.executionOwnership=
            executionOwnership==null
                ?this::runStandaloneOwned
                :executionOwnership;
    }

    private boolean runStandaloneOwned(
        WorldPlayer player,
        long generation,
        Runnable task
    ){
        synchronized(player.mutationLock()){
            if(!player.accepts(generation)||
               !ownership.owns(
                   player,
                   generation
               ))
                return false;

            task.run();
            return true;
        }
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
            OfflineRealtimeAdmission gate=offlineRealtimeAdmission;
            if(gate!=null&&!gate.permits(
                    owner,expectedGeneration,task))
                throw new IllegalStateException(
                    "R75_REALTIME_QUIESCED before schedule"
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
                    OfflineRealtimeAdmission gate=
                        offlineRealtimeAdmission;
                    if(gate==null){
                        // Retain exact original live execution route.
                        executionOwnership.run(
                            e.owner,e.generation,e.task
                        );
                    }else{
                        executionOwnership.run(
                            e.owner,e.generation,()->{
                                // The World ownership callback holds
                                // the real player mutation lock. A task
                                // queued BEFORE final capture must not
                                // execute AFTER the R74 quiescence flip.
                                if(gate.permits(
                                        e.owner,e.generation,e.task))
                                    e.task.run();
                            }
                        );
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
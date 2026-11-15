package spk.local;

import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic tick scheduler. Equal-tick tasks run in insertion order. */
final class WorldEventQueue implements AutoCloseable {
    static final class Handle {
        private final long id;
        private volatile boolean cancelled;
        private WorldEventQueue owner;

        Handle(
            long id,
            WorldEventQueue owner
        ){
            this.id=id;
            this.owner=owner;
        }

        long id(){
            return id;
        }

        boolean cancelled(){
            return cancelled;
        }

        void cancel(){
            WorldEventQueue queue;

            synchronized(this){
                if(cancelled)
                    return;

                cancelled=true;
                queue=owner;
                owner=null;
            }

            if(queue!=null)
                queue.remove(this);
        }

        void detach(){
            synchronized(this){
                owner=null;
            }
        }

        void discard(){
            synchronized(this){
                cancelled=true;
                owner=null;
            }
        }
    }

    private static final class E
        implements Comparable<E> {

        final long tick;
        final long seq;
        final Runnable task;
        final Handle handle;

        E(
            long tick,
            long seq,
            Runnable task,
            Handle handle
        ){
            this.tick=tick;
            this.seq=seq;
            this.task=task;
            this.handle=handle;
        }

        @Override public int compareTo(E other){
            int compare=
                Long.compare(
                    tick,
                    other.tick
                );

            return compare!=0
                ?compare
                :Long.compare(
                    seq,
                    other.seq
                );
        }
    }

    private final PriorityQueue<E>
        q=new PriorityQueue<>();
    private final AtomicLong
        seq=new AtomicLong();
    private boolean closed;

    /**
     * R76 OFFLINE ONLY: explicitly owner-tagged event callbacks. Legacy
     * untagged WorldEventQueue.schedule remains unchanged; ownership
     * cannot be inferred safely from an arbitrary Runnable closure.
     */
    interface OfflineOwnedEventAdmission {
        boolean permits(EntityId ownerId,long generation);
        void dispatch(
            EntityId ownerId,long generation,Runnable task
        );
    }

    private volatile OfflineOwnedEventAdmission offlineOwnedEventAdmission;

    synchronized void installOfflineOwnedEventAdmissionForTest(
        OfflineOwnedEventAdmission gate
    ){
        if(gate==null||offlineOwnedEventAdmission!=null||
           closed||seq.get()!=0L)
            throw new IllegalStateException(
                "R76_EVENT_GATE_DENIED invalid or already used"
            );
        offlineOwnedEventAdmission=gate;
    }

    /**
     * Explicitly tagged test-only event. The World owner/generation
     * is verified again on dispatch by the installed offline gate.
     * NEVER silently label untagged legacy events as owner-authorized.
     */
    synchronized Handle scheduleOwnedForTest(
        long tick,EntityId ownerId,long generation,Runnable task
    ){
        if(ownerId==null||task==null||generation<=0L)
            throw new IllegalArgumentException(
                "R76_EVENT_DENIED missing owner metadata"
            );
        OfflineOwnedEventAdmission gate=offlineOwnedEventAdmission;
        if(gate==null||!gate.permits(ownerId,generation))
            throw new IllegalStateException(
                "R76_EVENT_QUIESCED before schedule"
            );
        return schedule(tick,()->{
            // WorldEventQueue.runDue calls this from WorldPulse.
            // The policy revalidates the exact World generation
            // under that player's mutation lock before mutation.
            gate.dispatch(ownerId,generation,task);
        });
    }

    synchronized Handle schedule(
        long tick,
        Runnable task
    ){
        if(closed)
            throw new IllegalStateException(
                "world event queue closed"
            );

        if(task==null)
            throw new NullPointerException(
                "task"
            );

        long sequence=
            seq.incrementAndGet();

        Handle handle=
            new Handle(
                sequence,
                this
            );

        q.add(
            new E(
                tick,
                sequence,
                task,
                handle
            )
        );

        return handle;
    }

    synchronized int size(){
        return q.size();
    }

    int runDue(long tick){
        int count=0;

        for(;;){
            E event;

            synchronized(this){
                if(closed)
                    return count;

                event=q.peek();

                if(event==null||
                   event.tick>tick)
                    return count;

                q.remove();
                event.handle.detach();
            }

            if(event.handle.cancelled())
                continue;

            try{
                event.task.run();
            }catch(Throwable error){
                System.err.println(
                    "[world-events] task failed id="+
                    event.handle.id()+
                    " tick="+tick+
                    " error="+error
                );
            }

            count++;
        }
    }

    @Override public synchronized void close(){
        if(closed)
            return;

        closed=true;

        while(!q.isEmpty())
            q.remove().handle.discard();
    }

    private synchronized void remove(
        Handle handle
    ){
        for(Iterator<E> iterator=
                q.iterator();
                iterator.hasNext();){
            E event=iterator.next();

            if(event.handle==handle){
                iterator.remove();
                return;
            }
        }
    }
}
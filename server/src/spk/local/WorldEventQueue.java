package spk.local;

import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic tick scheduler. Equal-tick tasks run in insertion order. */
final class WorldEventQueue {
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

    synchronized Handle schedule(
        long tick,
        Runnable task
    ){
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
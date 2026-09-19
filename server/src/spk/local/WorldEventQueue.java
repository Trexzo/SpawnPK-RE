package spk.local;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic tick scheduler. Equal-tick tasks run in insertion order. */
final class WorldEventQueue {
    static final class Handle {
        private final long id;
        private volatile boolean cancelled;
        Handle(long id){this.id=id;}
        long id(){return id;}
        boolean cancelled(){return cancelled;}
        void cancel(){cancelled=true;}
    }
    private static final class E implements Comparable<E>{
        final long tick,seq; final Runnable task; final Handle handle;
        E(long t,long s,Runnable r,Handle h){tick=t;seq=s;task=r;handle=h;}
        public int compareTo(E o){int c=Long.compare(tick,o.tick);return c!=0?c:Long.compare(seq,o.seq);}
    }
    private final PriorityQueue<E> q=new PriorityQueue<>();
    private final AtomicLong seq=new AtomicLong();
    synchronized Handle schedule(long tick,Runnable task){
        if(task==null)throw new NullPointerException("task");
        long s=seq.incrementAndGet();Handle h=new Handle(s);q.add(new E(tick,s,task,h));return h;
    }
    synchronized int size(){return q.size();}
    int runDue(long tick){
        int n=0;
        for(;;){
            E e;
            synchronized(this){e=q.peek();if(e==null||e.tick>tick)return n;q.remove();}
            if(e.handle.cancelled())continue;
            try{e.task.run();}catch(Throwable t){System.err.println("[world-events] task failed id="+e.handle.id()+" tick="+tick+" error="+t);}
            n++;
        }
    }
}

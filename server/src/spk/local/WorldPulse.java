package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One execution context for shared logical ticks and queued gameplay commands. */
final class WorldPulse implements AutoCloseable,Runnable {
    static final int MAX_COMMANDS_PER_TICK=512;
    static final int MAX_COMMANDS_PER_PLAYER_PER_TICK=32;
    /**
     * Input/UI intents are accepted on the same single World thread, but they do
     * not have to wait for the next 600 ms simulation step. This preserves world
     * thread confinement without adding up to one full tick of artificial input latency.
     */
    static final int MAX_COMMANDS_PER_FAST_LOOP=128;
    static final int MAX_COMMANDS_PER_PLAYER_PER_FAST_LOOP=32;
    private final World world;
    private final long tickMillis;
    private final AtomicBoolean running=new AtomicBoolean();
    private Thread thread;
    private long nextTickAt;
    private long overdueTicks;
    private long lastDurationNanos;
    private long maxDurationNanos;
    private long commandsProcessed;
    private long fastCommandsProcessed;
    private long tasksProcessed;

    WorldPulse(World world,long tickMillis){this.world=world;this.tickMillis=tickMillis;}

    synchronized void start(){
        if(running.get())return;
        running.set(true);
        nextTickAt=System.currentTimeMillis()+tickMillis;
        thread=new Thread(this,"spk-world-pulse");
        thread.setDaemon(true);
        thread.start();
    }

    boolean running(){return running.get();}
    Thread thread(){return thread;}

    @Override public void run(){
        while(running.get()){
            long now=System.currentTimeMillis();
            try{world.realtime().runDue(now);}catch(Throwable t){System.err.println("[world] realtime queue error: "+t);}
            // Low-latency command phase. Commands still execute exclusively on this
            // World thread; only the logical simulation step remains 600 ms.
            try{
                int n=world.commands().drain(MAX_COMMANDS_PER_FAST_LOOP,MAX_COMMANDS_PER_PLAYER_PER_FAST_LOOP);
                fastCommandsProcessed+=n; commandsProcessed+=n;
            }catch(Throwable t){System.err.println("[world] fast command drain error: "+t);}
            if(now>=nextTickAt){
                long start=System.nanoTime();
                if(now-nextTickAt>=tickMillis)overdueTicks++;
                pulseOnce(now);
                lastDurationNanos=System.nanoTime()-start;
                if(lastDurationNanos>maxDurationNanos)maxDurationNanos=lastDurationNanos;
                // Never skip logical ticks. If badly overdue, process at most one tick per loop
                // and let the next loop continue catch-up without an unbounded inner loop.
                nextTickAt+=tickMillis;
                continue;
            }
            long realtimeDue=world.realtime().nextDueMillis();
            long wake=Math.min(nextTickAt,realtimeDue);
            long sleep=Math.max(1L,Math.min(20L,wake-now));
            try{Thread.sleep(sleep);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        }
    }

    void pulseOnce(long nowMillis){
        long tick=world.clock().advance();
        commandsProcessed+=world.commands().drain(MAX_COMMANDS_PER_TICK,MAX_COMMANDS_PER_PLAYER_PER_TICK);
        try{tasksProcessed+=world.events().runDue(tick);}catch(Throwable t){System.err.println("[world] scheduled task error tick="+tick+" error="+t);}
        for(WorldTickTarget target:world.tickTargetsSnapshot()){
            WorldPlayer p=world.players().byId(target.ownerId());
            if(p==null||!p.accepts(target.ownerGeneration()))continue;
            try{synchronized(p.mutationLock()){if(p.accepts(target.ownerGeneration()))target.onWorldTick(tick,nowMillis);}}catch(Throwable t){System.err.println("[world] tick target failed tick="+tick+" owner="+target.ownerId()+" error="+t);}
        }
    }

    String metrics(){return "WorldPulse{tick="+world.clock().tick()+",running="+running()+",players="+world.players().size()+",commandsQueued="+world.commands().size()+",commandsProcessed="+commandsProcessed+",fastCommandsProcessed="+fastCommandsProcessed+",scheduled="+world.events().size()+",tasksProcessed="+tasksProcessed+",realtime="+world.realtime().size()+",lastTickMs="+(lastDurationNanos/1_000_000.0)+",maxTickMs="+(maxDurationNanos/1_000_000.0)+",overdue="+overdueTicks+"}";}

    @Override public synchronized void close(){running.set(false);if(thread!=null)thread.interrupt();}
}

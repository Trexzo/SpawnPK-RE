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
    private volatile Thread compatibilityExecutionThread;
    private Runnable terminalAction;
    private boolean terminalComplete;
    private Throwable terminalFailure;
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
    boolean inExecutionContext(){
        Thread current=Thread.currentThread();
        return current==thread||
            current==compatibilityExecutionThread;
    }

    @Override public void run(){
        try{
            while(running.get()){
                if(world.closed())
                    break;

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
        }finally{
            runTerminalOnce();
        }
    }

    void pulseOnce(long nowMillis){
        Thread prior=
            compatibilityExecutionThread;
        compatibilityExecutionThread=
            Thread.currentThread();

        try{
            if(world.closed())
                return;

            long tick=world.clock().advance();

            if(world.closed())
                return;

            commandsProcessed+=world.commands().drain(MAX_COMMANDS_PER_TICK,MAX_COMMANDS_PER_PLAYER_PER_TICK);

            if(world.closed())
                return;

            try{tasksProcessed+=world.events().runDue(tick);}catch(Throwable t){System.err.println("[world] scheduled task error tick="+tick+" error="+t);}

            if(world.closed())
                return;

            for(WorldPlayer player:world.players().snapshot()){
                if(world.closed())
                    return;

                long generation=player.generation();

                if(!world.players().owns(
                        player,
                        generation
                    ))
                    continue;

                try{
                    world.withOpenPlayerMutationOwnershipIfCurrent(
                        player,
                        generation,
                        ()->player.timedEffects()
                            .tick(tick)
                    );
                }catch(Throwable t){
                    System.err.println(
                        "[world] timed-effect tick failed tick="+
                        tick+
                        " owner="+player.id()+
                        " error="+t
                    );
                }
            }

            if(world.closed())
                return;

            for(WorldTickTarget target:world.tickTargetsSnapshot()){
                if(world.closed())
                    return;

                WorldPlayer p=
                    world.players().byId(
                        target.ownerId()
                    );
                if(p==null)
                    continue;

                try{
                    world.withOpenPlayerMutationOwnershipIfCurrent(
                        p,
                        target.ownerGeneration(),
                        ()->target.onWorldTick(
                            tick,
                            nowMillis
                        )
                    );
                }catch(Throwable t){
                    System.err.println(
                        "[world] tick target failed tick="+
                        tick+
                        " owner="+
                        target.ownerId()+
                        " error="+t
                    );
                }

                if(world.closed())
                    return;
            }

            if(world.closed())
                return;

            try{
                world.persistence().checkpointDue(tick);
            }catch(Throwable t){
                System.err.println(
                    "[world] persistence checkpoint failed tick="+
                    tick+" error="+t
                );
            }
        }finally{
            compatibilityExecutionThread=prior;
        }
    }

    String metrics(){return "WorldPulse{tick="+world.clock().tick()+",running="+running()+",players="+world.players().size()+",commandsQueued="+world.commands().size()+",commandsProcessed="+commandsProcessed+",fastCommandsProcessed="+fastCommandsProcessed+",scheduled="+world.events().size()+",tasksProcessed="+tasksProcessed+",realtime="+world.realtime().size()+",lastTickMs="+(lastDurationNanos/1_000_000.0)+",maxTickMs="+(maxDurationNanos/1_000_000.0)+",overdue="+overdueTicks+"}";}

    void closeWithTerminal(
        Runnable terminal
    ){
        Objects.requireNonNull(
            terminal,
            "terminal"
        );

        Thread active;
        boolean inline;

        synchronized(this){
            if(terminalAction!=null||
               terminalComplete)
                throw new IllegalStateException(
                    "pulse terminal callback already assigned"
                );

            terminalAction=terminal;
            running.set(false);
            active=thread;
            inline=
                active==null||
                Thread.currentThread()==active;

            if(active!=null&&!inline)
                active.interrupt();
        }

        if(inline)
            runTerminalOnce();
        else{
            boolean interrupted=false;
            long deadline=
                System.nanoTime()+
                2_000_000_000L;

            synchronized(this){
                while(!terminalComplete){
                    long remaining=
                        deadline-
                        System.nanoTime();

                    if(remaining<=0L)
                        break;

                    long millis=
                        Math.max(
                            1L,
                            Math.min(
                                100L,
                                remaining/
                                    1_000_000L
                            )
                        );

                    try{
                        wait(millis);
                    }catch(InterruptedException ignored){
                        interrupted=true;
                    }
                }
            }

            if(interrupted)
                Thread.currentThread()
                    .interrupt();

            if(!terminalComplete)
                throw new IllegalStateException(
                    "pulse terminal callback timeout"
                );
        }

        rethrowTerminalFailure();
    }

    private void runTerminalOnce(){
        Runnable action;

        synchronized(this){
            if(terminalComplete||
               terminalAction==null)
                return;

            action=terminalAction;
            terminalAction=null;
        }

        Thread prior=
            compatibilityExecutionThread;
        compatibilityExecutionThread=
            Thread.currentThread();

        Throwable failure=null;

        try{
            action.run();
        }catch(Throwable error){
            failure=error;
        }finally{
            compatibilityExecutionThread=prior;

            synchronized(this){
                terminalFailure=failure;
                terminalComplete=true;
                notifyAll();
            }
        }
    }

    private void rethrowTerminalFailure(){
        Throwable failure;

        synchronized(this){
            failure=terminalFailure;
        }

        if(failure==null)
            return;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(failure);
    }

    @Override public synchronized void close(){
        running.set(false);

        Thread active=thread;
        if(active==null)return;

        active.interrupt();

        if(Thread.currentThread()==active)
            return;

        try{
            active.join(2_000L);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
}

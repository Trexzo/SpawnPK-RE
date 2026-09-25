package spk.local;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginScheduler;
import spk.plugin.api.PluginTask;

/** Per-plugin ownership boundary over the existing deterministic WorldEventQueue. */
final class PluginTaskTracker
    implements PluginScheduler,AutoCloseable {

    private final GameClock clock;
    private final WorldEventQueue queue;
    private final BooleanSupplier worldOpen;
    private volatile ClassLoader callbackLoader;
    private final LinkedHashSet<Task>
        tasks=new LinkedHashSet<>();

    private boolean activated;
    private boolean closed;

    PluginTaskTracker(
        GameClock clock,
        WorldEventQueue queue,
        BooleanSupplier worldOpen,
        ClassLoader callbackLoader
    ){
        this.clock=Objects.requireNonNull(
            clock,
            "clock"
        );
        this.queue=Objects.requireNonNull(
            queue,
            "queue"
        );
        this.worldOpen=Objects.requireNonNull(
            worldOpen,
            "worldOpen"
        );
        this.callbackLoader=
            callbackLoader;
    }

    @Override public synchronized PluginTask schedule(
        long delayTicks,
        Runnable action
    ){
        return create(
            delayTicks,
            0L,
            action
        );
    }

    @Override public synchronized PluginTask scheduleRepeating(
        long initialDelayTicks,
        long periodTicks,
        Runnable action
    ){
        if(periodTicks<=0L)
            throw new IllegalArgumentException(
                "periodTicks"
            );

        return create(
            initialDelayTicks,
            periodTicks,
            action
        );
    }

    private Task create(
        long delayTicks,
        long periodTicks,
        Runnable action
    ){
        if(delayTicks<=0L)
            throw new IllegalArgumentException(
                "delayTicks"
            );

        Objects.requireNonNull(
            action,
            "action"
        );

        requireOpen();

        // Validate the initial target while still in the caller's admission
        // operation. Pending enable-phase tasks are queued only after commit.
        Math.addExact(
            clock.tick(),
            delayTicks
        );

        Task task=
            new Task(
                this,
                delayTicks,
                periodTicks,
                action
            );

        tasks.add(task);

        if(activated)
            try{
                scheduleAt(
                    task,
                    Math.addExact(
                        clock.tick(),
                        delayTicks
                    )
                );
            }catch(RuntimeException|Error failure){
                tasks.remove(task);
                task.active=false;
                task.queued=null;
                throw failure;
            }

        return task;
    }

    private void requireOpen(){
        if(closed||
           !worldOpen.getAsBoolean())
            throw new IllegalStateException(
                "plugin scheduler closed"
            );
    }

    private void scheduleAt(
        Task task,
        long tick
    ){
        task.queued=
            queue.schedule(
                tick,
                task
            );
    }

    synchronized void activate(){
        requireOpen();

        if(activated)
            return;

        activated=true;

        long baseTick=
            clock.tick();

        try{
            for(Task task:
                    new ArrayList<>(tasks))
                if(task.active&&
                   task.queued==null)
                    scheduleAt(
                        task,
                        Math.addExact(
                            baseTick,
                            task.initialDelayTicks
                        )
                    );
        }catch(RuntimeException|Error failure){
            close();
            throw failure;
        }
    }

    synchronized boolean active(
        Task task
    ){
        return !closed&&
            task!=null&&
            task.active&&
            tasks.contains(task);
    }

    synchronized boolean cancel(
        Task task
    ){
        if(task==null||
           !task.active||
           !tasks.remove(task))
            return false;

        task.active=false;

        WorldEventQueue.Handle queued=
            task.queued;
        task.queued=null;

        if(queued!=null)
            queued.cancel();

        return true;
    }

    synchronized void execute(
        Task task
    ){
        if(closed||
           task==null||
           !task.active||
           !tasks.contains(task)||
           !worldOpen.getAsBoolean()){
            retire(task);
            return;
        }

        task.queued=null;

        try{
            PluginThreadContext.runUnchecked(
                callbackLoader,
                task.action
            );
        }catch(RuntimeException|Error failure){
            retire(task);
            throw failure;
        }

        if(closed||
           !task.active||
           !tasks.contains(task)||
           !worldOpen.getAsBoolean()){
            retire(task);
            return;
        }

        if(task.periodTicks<=0L){
            retire(task);
            return;
        }

        try{
            scheduleAt(
                task,
                Math.addExact(
                    clock.tick(),
                    task.periodTicks
                )
            );
        }catch(RuntimeException|Error failure){
            retire(task);
            throw failure;
        }
    }

    private void retire(
        Task task
    ){
        if(task==null)
            return;

        tasks.remove(task);
        task.active=false;

        WorldEventQueue.Handle queued=
            task.queued;
        task.queued=null;

        if(queued!=null)
            queued.cancel();
    }

    synchronized int size(){
        return tasks.size();
    }

    @Override public synchronized void close(){
        if(closed)
            return;

        closed=true;

        ArrayList<Task> snapshot=
            new ArrayList<>(tasks);
        tasks.clear();

        for(Task task:snapshot){
            task.active=false;

            WorldEventQueue.Handle queued=
                task.queued;
            task.queued=null;

            if(queued!=null)
                try{
                    queued.cancel();
                }catch(Throwable failure){
                    System.err.println(
                        "[plugins] task cancel failed error="+
                        failure
                    );
                }
        }

        callbackLoader=null;
    }

    private static final class Task
        implements PluginTask,Runnable {

        final PluginTaskTracker owner;
        final long initialDelayTicks;
        final long periodTicks;
        final Runnable action;

        boolean active=true;
        WorldEventQueue.Handle queued;

        Task(
            PluginTaskTracker owner,
            long initialDelayTicks,
            long periodTicks,
            Runnable action
        ){
            this.owner=owner;
            this.initialDelayTicks=
                initialDelayTicks;
            this.periodTicks=periodTicks;
            this.action=action;
        }

        @Override public boolean active(){
            return owner.active(this);
        }

        @Override public boolean cancel(){
            return owner.cancel(this);
        }

        @Override public void run(){
            owner.execute(this);
        }
    }
}

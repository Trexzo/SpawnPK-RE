package spk.local;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginScheduler;
import spk.plugin.api.PluginTask;

/** Per-plugin ownership boundary over the existing deterministic WorldEventQueue. */
final class PluginTaskTracker
    implements PluginScheduler,AutoCloseable {

    private volatile GameClock clock;
    private volatile WorldEventQueue queue;
    private volatile BooleanSupplier worldOpen;
    private volatile ClassLoader callbackLoader;
    private final PluginCallbackScope callbackScope;
    private volatile BooleanSupplier runtimeEnabled;
    private volatile BiConsumer<String,Throwable>
        failureHandler;
    private final LinkedHashSet<Task>
        tasks=new LinkedHashSet<>();

    private boolean activated;
    private volatile boolean closing;
    private boolean closed;
    private int inFlightExecutions;
    private Runnable quiescenceListener;

    PluginTaskTracker(
        GameClock clock,
        WorldEventQueue queue,
        BooleanSupplier worldOpen,
        ClassLoader callbackLoader,
        PluginCallbackScope callbackScope,
        BooleanSupplier runtimeEnabled,
        BiConsumer<String,Throwable> failureHandler
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
        this.callbackScope=
            Objects.requireNonNull(
                callbackScope,
                "callbackScope"
            );
        this.runtimeEnabled=
            Objects.requireNonNull(
                runtimeEnabled,
                "runtimeEnabled"
            );
        this.failureHandler=
            Objects.requireNonNull(
                failureHandler,
                "failureHandler"
            );
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
        if(closing||
           closed||
           worldOpen==null||
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

    synchronized void validateActivation(){
        requireOpen();

        long baseTick=
            clock.tick();

        for(Task task:
                new ArrayList<>(tasks))
            if(task.active&&
               task.queued==null)
                Math.addExact(
                    baseTick,
                    task.initialDelayTicks
                );
    }

    synchronized void activate(){
        validateActivation();

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
        task.action=null;

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
        if(closing||
           closed||
           task==null||
           !task.active||
           !tasks.contains(task)||
           !worldOpen.getAsBoolean()){
            retire(task);
            return;
        }

        task.queued=null;

        BooleanSupplier runtime=
            runtimeEnabled;

        if(runtime==null||
           !runtime.getAsBoolean()){
            retire(task);
            return;
        }

        Runnable action=
            task.action;

        if(action==null){
            retire(task);
            return;
        }

        inFlightExecutions++;

        try{
            callbackScope.callUnchecked(
                callbackLoader,
                lease->{
                    action.run();
                    return null;
                }
            );
        }catch(PluginCallbackScope.AdmissionException admission){
            retire(task);
            return;
        }catch(RuntimeException|Error failure){
            retire(task);
            throw failure;
        }finally{
            inFlightExecutions--;
        }

        if(closing||
           closed||
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
        task.action=null;

        WorldEventQueue.Handle queued=
            task.queued;
        task.queued=null;

        if(queued!=null)
            queued.cancel();
    }

    void reportFailure(
        Throwable failure
    ){
        reportFailure(
            "TASK",
            failure
        );
    }

    private void reportFailure(
        String kind,
        Throwable failure
    ){
        BiConsumer<String,Throwable> handler=
            failureHandler;

        if(handler!=null&&
           failure!=null)
            handler.accept(
                kind,
                failure
            );
    }

    synchronized int size(){
        return tasks.size();
    }

    void beginClose(){
        closing=true;
    }

    synchronized boolean quiescent(){
        return inFlightExecutions==0;
    }

    void onQuiescent(
        Runnable listener
    ){
        Objects.requireNonNull(
            listener,
            "listener"
        );

        boolean runNow=false;

        synchronized(this){
            if(closed)
                runNow=true;
            else if(quiescenceListener!=null&&
                    quiescenceListener!=listener)
                throw new IllegalStateException(
                    "plugin task quiescence listener already assigned"
                );
            else if(closing&&
                    inFlightExecutions==0)
                runNow=true;
            else
                quiescenceListener=listener;
        }

        if(runNow)
            listener.run();
    }

    void signalQuiescent(){
        Runnable listener=null;

        synchronized(this){
            if(closing&&
               inFlightExecutions==0&&
               quiescenceListener!=null){
                listener=quiescenceListener;
                quiescenceListener=null;
            }
        }

        if(listener!=null)
            listener.run();
    }

    void awaitQuiescent(){
        synchronized(this){
            // Lock barrier: admission is already closed by volatile closing.
        }
    }

    @Override public synchronized void close(){
        if(closed)
            return;

        closing=true;
        closed=true;

        ArrayList<Task> snapshot=
            new ArrayList<>(tasks);
        tasks.clear();

        for(Task task:snapshot){
            task.active=false;
            task.action=null;

            WorldEventQueue.Handle queued=
                task.queued;
            task.queued=null;

            if(queued!=null)
                try{
                    queued.cancel();
                }catch(Throwable failure){
                    reportFailure(
                        "CLEANUP:TASK_CANCEL",
                        failure
                    );
                    System.err.println(
                        "[plugins] task cancel failed error="+
                        failure
                    );
                }
        }

        failureHandler=null;
        quiescenceListener=null;
        runtimeEnabled=null;
        callbackLoader=null;
        worldOpen=null;
        queue=null;
        clock=null;
    }

    private static final class Task
        implements PluginTask,Runnable {

        final PluginTaskTracker owner;
        final long initialDelayTicks;
        final long periodTicks;
        volatile Runnable action;

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
            try{
                owner.execute(this);
            }catch(RuntimeException|Error failure){
                owner.reportFailure(
                    failure
                );
                throw failure;
            }finally{
                owner.signalQuiescent();
            }
        }
    }
}

package spk.local;

import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import spk.event.DomainEventBus;
import spk.plugin.api.*;

public final class PluginWorldTerminalLifecycleTest {
    public static void main(String[] args)throws Exception{
        externalClose();
        inlineNeverStartedClose();
        pulseContextClose();
        blockingTerminalCompletion();
        immediateWorldCloseCallbackFence();
        callbackQuiescence();

        System.out.println(
            "PLUGIN_WORLD_TERMINAL_LIFECYCLE_PASS "+
            "externalDisableWorldContext=true "+
            "startedDisableActualPulseThread=true "+
            "neverStartedCompatibilityContext=true "+
            "pulseCloseNoSelfJoin=true "+
            "terminalWaitNoTimeout=true "+
            "concurrentCloseWaitsTerminal=true "+
            "admissionFenceBeforeDisable=true "+
            "callbackQuiescence=true "+
            "nestedAdmissionRejected=true "+
            "tasksDestroyedAfterDisable=true "+
            "disableExactlyOnce=true "+
            "terminalWakeInterruptConsumed=true "+
            "worldCloseImmediateCallbackFence=true "+
            "postCloseTaskSuppressed=true"
        );
    }

    private static void externalClose()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        TerminalPlugin plugin=
            new TerminalPlugin(
                world
            );
        PluginHandle handle=
            world.plugins()
                .enable(plugin);

        plugin.queued=
            plugin.scheduler.schedule(
                10_000L,
                plugin.taskRuns::incrementAndGet
            );

        world.start();

        Thread startedPulse=
            world.pulse().thread();

        require(
            startedPulse!=null&&
            startedPulse.isAlive(),
            "started pulse thread missing"
        );

        world.close();

        require(
            plugin.disableCalls.get()==1,
            "external disable count"
        );
        require(
            plugin.disableOnWorld.get(),
            "external disable not on World execution context"
        );
        require(
            plugin.disableThread==
                startedPulse,
            "started World disable did not execute on actual pulse thread"
        );
        require(
            !plugin.disableInterrupted.get(),
            "lifecycle wake interrupt leaked into plugin disable"
        );
        require(
            plugin.scheduleRejectedDuringDisable.get(),
            "scheduler accepted new work during disable"
        );
        require(
            plugin.queuedActiveDuringDisable.get(),
            "queued task was destructively closed before disable callback"
        );
        require(
            !plugin.queued.active(),
            "queued task remained active after terminal cleanup"
        );
        require(
            plugin.taskRuns.get()==0,
            "queued task executed after terminal fence"
        );
        require(
            !handle.enabled(),
            "plugin handle remained enabled"
        );
    }

    private static void inlineNeverStartedClose()
        throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        TerminalPlugin plugin=
            new TerminalPlugin(
                world
            );

        world.plugins().enable(plugin);
        world.close();

        require(
            plugin.disableCalls.get()==1,
            "never-started disable count"
        );
        require(
            plugin.disableOnWorld.get(),
            "never-started terminal callback lacked compatible World context"
        );
        require(
            world.pulse().thread()==null,
            "never-started World unexpectedly created pulse thread"
        );
    }

    private static void pulseContextClose()
        throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        TerminalPlugin plugin=
            new TerminalPlugin(
                world
            );
        AtomicBoolean closeEnteredOnWorld=
            new AtomicBoolean();

        world.plugins().enable(plugin);

        world.events().schedule(
            1L,
            ()->{
                closeEnteredOnWorld.set(
                    world.pulse()
                        .inExecutionContext()
                );
                world.close();
            }
        );

        world.observePulse(
            System.currentTimeMillis()
        );

        require(
            closeEnteredOnWorld.get(),
            "close fixture did not enter from World execution context"
        );
        require(
            world.closed(),
            "pulse-context close did not fence World"
        );
        require(
            plugin.disableCalls.get()==1,
            "pulse-context disable count"
        );
        require(
            plugin.disableOnWorld.get(),
            "pulse-context disable lost World execution context"
        );

        world.close();

        require(
            plugin.disableCalls.get()==1,
            "idempotent close disabled plugin twice"
        );
    }

    private static void blockingTerminalCompletion()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        BlockingTerminalPlugin plugin=
            new BlockingTerminalPlugin(
                world
            );
        AtomicReference<Throwable> ownerFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> secondFailure=
            new AtomicReference<>();
        CountDownLatch ownerReturned=
            new CountDownLatch(1);
        CountDownLatch secondReturned=
            new CountDownLatch(1);

        world.plugins().enable(plugin);
        world.start();

        Thread owner=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        ownerFailure.set(
                            failure
                        );
                    }finally{
                        ownerReturned.countDown();
                    }
                },
                "plugin-terminal-owner-close"
            );
        owner.start();

        require(
            plugin.disableEntered.await(
                5L,
                TimeUnit.SECONDS
            ),
            "blocking disable did not enter"
        );

        Thread second=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        secondFailure.set(
                            failure
                        );
                    }finally{
                        secondReturned.countDown();
                    }
                },
                "plugin-terminal-second-close"
            );
        second.start();

        if(ownerReturned.await(
                2_300L,
                TimeUnit.MILLISECONDS))
            throw new AssertionError(
                "owner close escaped terminal cleanup after legacy timeout window"
            );

        require(
            secondReturned.getCount()==1L,
            "second close returned before terminal cleanup"
        );

        plugin.disableRelease.countDown();

        require(
            ownerReturned.await(
                5L,
                TimeUnit.SECONDS
            ),
            "owner close did not finish after terminal release"
        );
        require(
            secondReturned.await(
                5L,
                TimeUnit.SECONDS
            ),
            "second close did not finish after terminal release"
        );

        owner.join(1_000L);
        second.join(1_000L);

        require(
            ownerFailure.get()==null&&
            secondFailure.get()==null,
            "blocking terminal close failed"
        );
        require(
            plugin.disableCalls.get()==1,
            "blocking terminal disable count"
        );
    }

    private static void immediateWorldCloseCallbackFence()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        AtomicInteger callbackRuns=
            new AtomicInteger();
        AtomicInteger disableCalls=
            new AtomicInteger();
        AtomicReference<Throwable> pulseFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> closeFailure=
            new AtomicReference<>();
        AtomicReference<Thread> closeThread=
            new AtomicReference<>();

        Plugin plugin=
            new Plugin(){
                private final PluginManifest manifest=
                    new PluginManifest(
                        "terminal.close-fence",
                        "1.0.0",
                        PluginApiVersion.CURRENT,
                        Collections.<String>emptyList()
                    );

                @Override public PluginManifest manifest(){
                    return manifest;
                }

                @Override public void enable(
                    PluginContext context
                ){
                    context.events().subscribe(
                        CloseFenceEvent.class,
                        DomainEventBus.Priority.NORMAL,
                        event->
                            callbackRuns.incrementAndGet()
                    );
                }

                @Override public void disable(){
                    disableCalls.incrementAndGet();
                }
            };

        PluginHandle handle=
            world.plugins()
                .enable(plugin);

        try{
            world.events().schedule(
                1L,
                ()->{
                    try{
                        Thread closer=
                            new Thread(
                                ()->{
                                    try{
                                        world.close();
                                    }catch(Throwable failure){
                                        closeFailure.set(
                                            failure
                                        );
                                    }
                                },
                                "plugin-world-close-admission-fence"
                            );

                        closeThread.set(
                            closer
                        );
                        closer.start();

                        long deadline=
                            System.nanoTime()+
                            TimeUnit.SECONDS
                                .toNanos(5L);

                        while(!world.closed()&&
                              System.nanoTime()<deadline)
                            Thread.yield();

                        if(!world.closed())
                            throw new AssertionError(
                                "World close fence was not published"
                            );

                        world.domainEvents()
                            .publish(
                                new CloseFenceEvent()
                            );
                    }catch(Throwable failure){
                        pulseFailure.set(
                            failure
                        );
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            Thread closer=
                closeThread.get();

            require(
                closer!=null,
                "World close fence thread missing"
            );

            closer.join(
                5_000L
            );

            require(
                !closer.isAlive(),
                "World close fence thread did not finish"
            );
            require(
                pulseFailure.get()==null&&
                closeFailure.get()==null,
                "World close callback-fence path failed pulse="+
                pulseFailure.get()+
                " close="+
                closeFailure.get()
            );
            require(
                callbackRuns.get()==0,
                "plugin callback entered after immediate World close fence"
            );
            require(
                disableCalls.get()==1,
                "World close callback-fence disable count"
            );
            require(
                !handle.enabled(),
                "World close callback-fence handle remained enabled"
            );
        }finally{
            Thread closer=
                closeThread.get();

            if(closer!=null&&
               closer.isAlive())
                closer.join(
                    1_000L
                );

            if(!world.closed())
                world.close();
        }
    }

    private static void callbackQuiescence()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        PluginManager manager=
            world.plugins();
        BlockingEventPlugin plugin=
            new BlockingEventPlugin(
                world
            );
        PluginHandle handle=
            manager.enable(plugin);
        AtomicReference<Throwable> pulseFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> disableFailure=
            new AtomicReference<>();

        world.events().schedule(
            1L,
            ()->{
                try{
                    world.domainEvents()
                        .publish(
                            new BlockingEvent()
                        );
                }catch(Throwable failure){
                    throw new RuntimeException(
                        failure
                    );
                }
            }
        );

        Thread pulse=
            new Thread(
                ()->{
                    try{
                        world.observePulse(
                            System.currentTimeMillis()
                        );
                    }catch(Throwable failure){
                        pulseFailure.set(
                            failure
                        );
                    }
                },
                "plugin-callback-quiescence-pulse"
            );
        pulse.start();

        require(
            plugin.entered.await(
                5L,
                TimeUnit.SECONDS
            ),
            "blocking event callback did not enter"
        );

        Thread disable=
            new Thread(
                ()->{
                    try{
                        manager.disable(
                            "terminal.blocking-event"
                        );
                    }catch(Throwable failure){
                        disableFailure.set(
                            failure
                        );
                    }
                },
                "plugin-callback-quiescence-disable"
            );
        disable.start();

        waitForScopeClosing(
            handle,
            5_000L
        );

        require(
            plugin.disableCalls.get()==0,
            "plugin.disable entered before callback quiesced"
        );
        require(
            plugin.outer.active()&&
            plugin.nested.active(),
            "event subscriptions destroyed before callback quiesced"
        );

        plugin.attemptNested.countDown();

        require(
            plugin.nestedAttemptComplete.await(
                5L,
                TimeUnit.SECONDS
            ),
            "nested admission attempt did not complete"
        );
        require(
            plugin.nestedCalls.get()==0,
            "new callback entered after terminal admission fence"
        );
        require(
            plugin.disableCalls.get()==0,
            "plugin.disable entered while outer callback still blocked"
        );
        require(
            disable.isAlive(),
            "disable returned before callback release"
        );

        plugin.release.countDown();

        pulse.join(5_000L);
        disable.join(5_000L);

        require(
            !pulse.isAlive()&&
            !disable.isAlive(),
            "callback quiescence threads did not terminate"
        );
        require(
            pulseFailure.get()==null&&
            disableFailure.get()==null,
            "callback quiescence path failed"
        );
        require(
            plugin.disableCalls.get()==1,
            "callback quiescence disable count"
        );
        require(
            !plugin.outer.active()&&
            !plugin.nested.active(),
            "event subscriptions remained active after cleanup"
        );
        require(
            !handle.enabled(),
            "blocking event handle remained enabled"
        );

        world.close();
    }

    private static void waitForScopeClosing(
        PluginHandle handle,
        long timeoutMillis
    )throws Exception{
        java.lang.reflect.Field callbacksField=
            handle.getClass()
                .getDeclaredField(
                    "callbacks"
                );
        callbacksField.setAccessible(true);
        Object callbacks=
            callbacksField.get(handle);

        java.lang.reflect.Field closingField=
            callbacks.getClass()
                .getDeclaredField(
                    "closing"
                );
        closingField.setAccessible(true);

        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(System.nanoTime()<deadline){
            if(closingField.getBoolean(
                    callbacks))
                return;

            Thread.yield();
        }

        throw new AssertionError(
            "callback admission fence was not published"
        );
    }

    private static class TerminalPlugin
        implements Plugin {

        private final World world;
        private final PluginManifest manifest=
            new PluginManifest(
                "terminal.lifecycle",
                "1.0.0",
                PluginApiVersion.CURRENT,
                Collections.<String>emptyList()
            );

        final AtomicInteger disableCalls=
            new AtomicInteger();
        final AtomicInteger taskRuns=
            new AtomicInteger();
        final AtomicBoolean disableOnWorld=
            new AtomicBoolean();
        final AtomicBoolean disableInterrupted=
            new AtomicBoolean();
        final AtomicBoolean scheduleRejectedDuringDisable=
            new AtomicBoolean();
        final AtomicBoolean queuedActiveDuringDisable=
            new AtomicBoolean();

        volatile PluginScheduler scheduler;
        volatile PluginTask queued;
        volatile Thread disableThread;

        TerminalPlugin(
            World world
        ){
            this.world=world;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            scheduler=
                context.scheduler();
        }

        @Override public void disable(){
            disableCalls.incrementAndGet();
            disableThread=
                Thread.currentThread();
            disableOnWorld.set(
                world.pulse()
                    .inExecutionContext()
            );
            disableInterrupted.set(
                Thread.currentThread()
                    .isInterrupted()
            );

            PluginTask current=
                queued;

            if(current!=null)
                queuedActiveDuringDisable.set(
                    current.active()
                );

            try{
                scheduler.schedule(
                    1L,
                    ()->{}
                );
            }catch(IllegalStateException expected){
                scheduleRejectedDuringDisable.set(
                    true
                );
            }
        }
    }

    private static final class BlockingTerminalPlugin
        extends TerminalPlugin {

        final CountDownLatch disableEntered=
            new CountDownLatch(1);
        final CountDownLatch disableRelease=
            new CountDownLatch(1);

        BlockingTerminalPlugin(
            World world
        ){
            super(world);
        }

        @Override public void disable(){
            super.disable();
            disableEntered.countDown();

            boolean interrupted=false;

            for(;;){
                try{
                    disableRelease.await();
                    break;
                }catch(InterruptedException ignored){
                    interrupted=true;
                }
            }

            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }
    }

    private static final class BlockingEventPlugin
        implements Plugin {

        private final World world;
        private final PluginManifest manifest=
            new PluginManifest(
                "terminal.blocking-event",
                "1.0.0",
                PluginApiVersion.CURRENT,
                Collections.<String>emptyList()
            );

        final CountDownLatch entered=
            new CountDownLatch(1);
        final CountDownLatch attemptNested=
            new CountDownLatch(1);
        final CountDownLatch nestedAttemptComplete=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);
        final AtomicInteger nestedCalls=
            new AtomicInteger();
        final AtomicInteger disableCalls=
            new AtomicInteger();

        volatile DomainEventBus.Subscription outer;
        volatile DomainEventBus.Subscription nested;

        BlockingEventPlugin(
            World world
        ){
            this.world=world;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            outer=
                context.events().subscribe(
                    BlockingEvent.class,
                    DomainEventBus.Priority.HIGH,
                    event->{
                        entered.countDown();

                        boolean interrupted=false;

                        for(;;){
                            try{
                                attemptNested.await();
                                break;
                            }catch(InterruptedException ignored){
                                interrupted=true;
                            }
                        }

                        world.domainEvents()
                            .publish(
                                new NestedEvent()
                            );
                        nestedAttemptComplete.countDown();

                        for(;;){
                            try{
                                release.await();
                                break;
                            }catch(InterruptedException ignored){
                                interrupted=true;
                            }
                        }

                        if(interrupted)
                            Thread.currentThread()
                                .interrupt();
                    }
                );

            nested=
                context.events().subscribe(
                    NestedEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->
                        nestedCalls.incrementAndGet()
                );
        }

        @Override public void disable(){
            disableCalls.incrementAndGet();
        }
    }

    private static final class CloseFenceEvent
        implements DomainEventBus.Event {}

    private static final class BlockingEvent
        implements DomainEventBus.Event {}

    private static final class NestedEvent
        implements DomainEventBus.Event {}

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PluginWorldTerminalLifecycleTest(){}
}

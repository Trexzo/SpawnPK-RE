package spk.local;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import spk.content.api.*;
import spk.event.DomainEventBus;
import spk.plugin.api.*;

public final class PluginRuntimeFailureCascadeTest {
    private static final String COMMAND=
        "cascadefailure";

    public static void main(String[] args)throws Exception{
        eventCascade();
        nestedCascadeDeferred();
        taskFailure();
        contentFailureNoFallback();
        enablePhaseEventSkip();
        batchCommitPublicationBarrier();
        explicitDisableInFlightFailure();
        cleanupFailureDetached();

        System.out.println(
            "PLUGIN_RUNTIME_FAILURE_CASCADE_PASS "+
            "transitiveCascade=true "+
            "reverseDependencyCleanup=true "+
            "unrelatedEventContinues=true "+
            "taskFailureTerminal=true "+
            "contentFailureTerminal=true "+
            "sameDispatchFallback=false "+
            "disableExactlyOnce=true "+
            "terminalDiagnosticsDetached=true "+
            "terminalHandlesReleaseFailureSink=true "+
            "nestedTaskCascadeDeferred=true "+
            "batchEnableAdmissionAtomic=true "+
            "batchTaskDeferredUntilCommit=true "+
            "failedBatchTaskHandleTerminal=true "+
            "batchCommitPublicationBarrier=true "+
            "admissionRejectionNotFailure=true "+
            "claimedFailureRecorded=true "+
            "cleanupFailureDetached=true "+
            "stackDetached=true"
        );
    }

    private static void eventCascade()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        List<String> disableOrder=
            Collections.synchronizedList(
                new ArrayList<>()
            );
        AtomicInteger bCalls=
            new AtomicInteger();
        AtomicInteger cCalls=
            new AtomicInteger();
        AtomicInteger uCalls=
            new AtomicInteger();

        CascadePlugin a=
            new CascadePlugin(
                "cascade.a",
                Collections.<String>emptyList(),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.HIGH,
                            event->{
                                throw new IllegalStateException(
                                    "cascade-a-event"
                                );
                            }
                        )
            );
        CascadePlugin b=
            new CascadePlugin(
                "cascade.b",
                Collections.singletonList(
                    "cascade.a"
                ),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.NORMAL,
                            event->
                                bCalls.incrementAndGet()
                        )
            );
        CascadePlugin c=
            new CascadePlugin(
                "cascade.c",
                Collections.singletonList(
                    "cascade.b"
                ),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.LOW,
                            event->
                                cCalls.incrementAndGet()
                        )
            );
        CascadePlugin u=
            new CascadePlugin(
                "cascade.u",
                Collections.<String>emptyList(),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.NORMAL,
                            event->
                                uCalls.incrementAndGet()
                        )
            );

        PluginHandle aHandle=null;

        try{
            List<PluginHandle> handles=
                manager.enableAll(
                    Arrays.<Plugin>asList(
                        c,
                        u,
                        b,
                        a
                    )
                );

            for(PluginHandle handle:handles)
                if("cascade.a".equals(
                        handle.manifest().id()))
                    aHandle=handle;

            world.events().schedule(
                1L,
                ()->{
                    try{
                        world.domainEvents()
                            .publish(
                                new CascadeEvent()
                            );
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                manager.plugin("cascade.a")==null&&
                manager.plugin("cascade.b")==null&&
                manager.plugin("cascade.c")==null,
                "failed dependency cascade remained enabled"
            );
            require(
                manager.plugin("cascade.u")!=null,
                "unrelated plugin was terminalized"
            );
            require(
                bCalls.get()==0&&
                cCalls.get()==0,
                "dependant listener executed after provider failure"
            );
            require(
                uCalls.get()==1,
                "unrelated listener did not continue same publication"
            );
            require(
                disableOrder.equals(
                    Arrays.asList(
                        "cascade.c",
                        "cascade.b",
                        "cascade.a"
                    )
                ),
                "reverse dependency cleanup order "+
                disableOrder
            );
            require(
                a.disableCalls.get()==1&&
                b.disableCalls.get()==1&&
                c.disableCalls.get()==1,
                "cascade disable count"
            );
            require(
                aHandle!=null&&
                !aHandle.enabled(),
                "failed handle stayed enabled"
            );
            assertFailureSinkReleased(
                aHandle
            );

            List<String> diagnostics=
                manager.terminalDiagnostics();

            require(
                diagnostics.stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=cascade.a"
                            )&&
                            line.contains(
                                "phase=CALLBACK:EVENT:"
                            )&&
                            line.contains(
                                "errorClass=java.lang.IllegalStateException"
                            )&&
                            line.contains(
                                " stack="
                            )&&
                            line.contains(
                                "PluginRuntimeFailureCascadeTest"
                            )
                    ),
                "detached callback diagnostic missing "+
                diagnostics
            );

            for(Object diagnostic:diagnostics)
                require(
                    diagnostic instanceof String,
                    "terminal diagnostic retained non-string root"
                );

            require(
                !manager.disable(
                    "cascade.a"
                )&&
                a.disableCalls.get()==1,
                "post-failure explicit disable repeated cleanup"
            );

            require(
                manager.disable(
                    "cascade.u"
                ),
                "unrelated plugin did not remain normally disableable"
            );
        }finally{
            world.close();
        }
    }

    private static void nestedCascadeDeferred()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        List<String> disableOrder=
            Collections.synchronizedList(
                new ArrayList<>()
            );
        AtomicBoolean deferredInsideTask=
            new AtomicBoolean();
        AtomicInteger bCalls=
            new AtomicInteger();
        AtomicInteger unrelatedCalls=
            new AtomicInteger();

        CascadePlugin a=
            new CascadePlugin(
                "nested.a",
                Collections.<String>emptyList(),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            NestedInnerEvent.class,
                            DomainEventBus.Priority.HIGH,
                            event->{
                                throw new IllegalStateException(
                                    "nested-inner-failure"
                                );
                            }
                        )
            );

        CascadePlugin b=
            new CascadePlugin(
                "nested.b",
                Collections.singletonList(
                    "nested.a"
                ),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            NestedInnerEvent.class,
                            DomainEventBus.Priority.NORMAL,
                            event->
                                bCalls.incrementAndGet()
                        )
            );

        final CascadePlugin[] cRef=
            new CascadePlugin[1];

        CascadePlugin c=
            new CascadePlugin(
                "nested.c",
                Collections.singletonList(
                    "nested.b"
                ),
                disableOrder,
                context->
                    context.scheduler()
                        .schedule(
                            1L,
                            ()->{
                                try{
                                    world.domainEvents()
                                        .publish(
                                            new NestedInnerEvent()
                                        );
                                }catch(Exception failure){
                                    throw new RuntimeException(
                                        failure
                                    );
                                }

                                require(
                                    a.disableCalls.get()==0&&
                                    b.disableCalls.get()==0&&
                                    cRef[0].disableCalls.get()==0,
                                    "cascade cleanup entered before outer task unwound"
                                );
                                deferredInsideTask.set(
                                    true
                                );
                            }
                        )
            );
        cRef[0]=c;

        CascadePlugin unrelated=
            new CascadePlugin(
                "nested.u",
                Collections.<String>emptyList(),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            NestedInnerEvent.class,
                            DomainEventBus.Priority.LOW,
                            event->
                                unrelatedCalls
                                    .incrementAndGet()
                        )
            );

        try{
            manager.enableAll(
                Arrays.<Plugin>asList(
                    c,
                    unrelated,
                    b,
                    a
                )
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                deferredInsideTask.get(),
                "nested failure never returned to outer task"
            );
            require(
                bCalls.get()==0,
                "terminal dependant callback executed from snapshotted binding"
            );
            require(
                unrelatedCalls.get()==1,
                "unrelated nested listener did not continue"
            );
            require(
                disableOrder.equals(
                    Arrays.asList(
                        "nested.c",
                        "nested.b",
                        "nested.a"
                    )
                ),
                "nested deferred cleanup order "+
                disableOrder
            );
            require(
                manager.plugin("nested.a")==null&&
                manager.plugin("nested.b")==null&&
                manager.plugin("nested.c")==null&&
                manager.plugin("nested.u")!=null,
                "nested cascade terminal ownership mismatch"
            );

            long realNestedFailures=
                manager.terminalDiagnostics()
                    .stream()
                    .filter(
                        line->
                            line.contains(
                                "phase=CALLBACK:EVENT:"
                            )&&
                            line.contains(
                                "nested."
                            )
                    )
                    .count();

            require(
                realNestedFailures==1L,
                "admission rejection created synthetic callback failure diagnostics count="+
                realNestedFailures+
                " diagnostics="+
                manager.terminalDiagnostics()
            );

            manager.disable(
                "nested.u"
            );
        }finally{
            world.close();
        }
    }

    private static void taskFailure()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        AtomicInteger unrelated=
            new AtomicInteger();

        CascadePlugin failing=
            new CascadePlugin(
                "task.failure",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.scheduler().schedule(
                        1L,
                        ()->{
                            throw new IllegalStateException(
                                "task-runtime-failure"
                            );
                        }
                    )
            );

        CascadePlugin healthy=
            new CascadePlugin(
                "task.healthy",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.scheduler().schedule(
                        1L,
                        unrelated::incrementAndGet
                    )
            );

        try{
            manager.enable(failing);
            manager.enable(healthy);

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                manager.plugin(
                    "task.failure"
                )==null,
                "task failure plugin stayed enabled"
            );
            require(
                manager.plugin(
                    "task.healthy"
                )!=null&&
                unrelated.get()==1,
                "unrelated scheduled plugin did not continue"
            );
            require(
                failing.disableCalls.get()==1,
                "task failure disable count"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=task.failure"
                            )&&
                            line.contains(
                                "phase=CALLBACK:TASK"
                            )
                    ),
                "task failure diagnostic missing"
            );
        }finally{
            world.close();
        }
    }

    private static void contentFailureNoFallback()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        AtomicInteger fallbackCalls=
            new AtomicInteger();

        world.content().installCustom(
            new ContentModule(){
                @Override public String id(){
                    return "cascade-base";
                }

                @Override public void register(
                    ContentRegistrar registrar
                ){
                    registrar.command(
                        COMMAND,
                        1,
                        context->{
                            fallbackCalls.incrementAndGet();
                            return ContentResult.handled(
                                "FALLBACK",
                                null
                            );
                        }
                    );
                }
            }
        );

        CascadePlugin failing=
            new CascadePlugin(
                "content.failure",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.content()
                        .command(
                            COMMAND,
                            100,
                            command->{
                                throw new IllegalStateException(
                                    "content-runtime-failure"
                                );
                            }
                        )
            );

        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "content-failure-player"
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        try{
            manager.enable(failing);

            boolean failed=false;

            try{
                world.submitAndWait(
                    player,
                    generation,
                    ()->world.content()
                        .dispatchCommand(
                            player,
                            "::"+COMMAND,
                            writer
                        ),
                    5_000L
                );
            }catch(Exception expected){
                failed=true;
            }

            require(
                failed,
                "failing winner did not propagate failure"
            );
            require(
                fallbackCalls.get()==0,
                "same dispatch fell through to shadowed handler"
            );
            require(
                manager.plugin(
                    "content.failure"
                )==null&&
                failing.disableCalls.get()==1,
                "content failure did not terminalize owner"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=content.failure"
                            )&&
                            line.contains(
                                "phase=CALLBACK:CONTENT_COMMAND"
                            )
                    ),
                "content failure diagnostic missing"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void enablePhaseEventSkip()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        AtomicInteger callbackRuns=
            new AtomicInteger();
        AtomicInteger failedBatchTaskRuns=
            new AtomicInteger();
        AtomicReference<PluginTask>
            failedBatchTask=
                new AtomicReference<>();
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        Plugin first=
            new Plugin(){
                private final PluginManifest manifest=
                    new PluginManifest(
                        "enable.phase.a",
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
                        EnablePhaseEvent.class,
                        DomainEventBus.Priority.NORMAL,
                        event->
                            callbackRuns
                                .incrementAndGet()
                    );
                    failedBatchTask.set(
                        context.scheduler().schedule(
                            1L,
                            failedBatchTaskRuns::incrementAndGet
                        )
                    );
                }
            };

        Plugin failingSecond=
            new Plugin(){
                private final PluginManifest manifest=
                    new PluginManifest(
                        "enable.phase.b",
                        "1.0.0",
                        PluginApiVersion.CURRENT,
                        Collections.singletonList(
                            "enable.phase.a"
                        )
                    );

                @Override public PluginManifest manifest(){
                    return manifest;
                }

                @Override public void enable(
                    PluginContext context
                )throws Exception{
                    world.domainEvents()
                        .publish(
                            new EnablePhaseEvent()
                        );
                    throw new IllegalStateException(
                        "intentional-batch-enable-failure"
                    );
                }
            };

        try{
            world.events().schedule(
                1L,
                ()->{
                    try{
                        manager.enableAll(
                            Arrays.<Plugin>asList(
                                failingSecond,
                                first
                            )
                        );
                    }catch(Throwable error){
                        failure.set(error);
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                failure.get()!=null,
                "failing batch unexpectedly committed"
            );
            require(
                callbackRuns.get()==0,
                "earlier batch candidate became runtime-admissible before whole batch commit"
            );
            require(
                failedBatchTaskRuns.get()==0,
                "failed batch executed pending task before commit"
            );
            require(
                failedBatchTask.get()!=null&&
                !failedBatchTask.get().active(),
                "failed batch retained active pending task handle"
            );
            require(
                manager.plugin(
                    "enable.phase.a"
                )==null&&
                manager.plugin(
                    "enable.phase.b"
                )==null,
                "failed batch retained candidate"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .noneMatch(
                        line->
                            line.contains(
                                "plugin=enable.phase"
                            )&&
                            line.contains(
                                "CALLBACK:"
                            )
                    ),
                "ENABLING admission rejection created plugin failure diagnostic"
            );

            AtomicInteger committedRuns=
                new AtomicInteger();
            AtomicInteger committedTaskRuns=
                new AtomicInteger();

            CascadePlugin committedA=
                new CascadePlugin(
                    "enable.commit.a",
                    Collections.<String>emptyList(),
                    new ArrayList<>(),
                    context->
                        {
                            context.events()
                                .subscribe(
                                    EnablePhaseEvent.class,
                                    DomainEventBus.Priority.NORMAL,
                                    event->
                                        committedRuns
                                            .incrementAndGet()
                                );
                            context.scheduler().schedule(
                                1L,
                                committedTaskRuns::incrementAndGet
                            );
                        }
                );
            CascadePlugin committedB=
                new CascadePlugin(
                    "enable.commit.b",
                    Collections.singletonList(
                        "enable.commit.a"
                    ),
                    new ArrayList<>(),
                    context->{}
                );

            manager.enableAll(
                Arrays.<Plugin>asList(
                    committedB,
                    committedA
                )
            );

            world.events().schedule(
                world.clock().tick()+1L,
                ()->{
                    try{
                        world.domainEvents()
                            .publish(
                                new EnablePhaseEvent()
                            );
                    }catch(Exception error){
                        throw new RuntimeException(
                            error
                        );
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                committedRuns.get()==1,
                "successful batch did not publish runtime admission atomically"
            );
            require(
                committedTaskRuns.get()==1,
                "successful batch pending task did not execute exactly once after commit"
            );

            manager.disable(
                "enable.commit.b"
            );
            manager.disable(
                "enable.commit.a"
            );
        }finally{
            world.close();
        }
    }

    private static void batchCommitPublicationBarrier()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        AtomicInteger eventRuns=
            new AtomicInteger();
        AtomicInteger taskRuns=
            new AtomicInteger();
        CountDownLatch registrationsComplete=
            new CountDownLatch(1);
        AtomicReference<Thread> enableThread=
            new AtomicReference<>();
        AtomicReference<Throwable> enableFailure=
            new AtomicReference<>();
        List<String> disableOrder=
            Collections.synchronizedList(
                new ArrayList<>()
            );

        CascadePlugin first=
            new CascadePlugin(
                "enable.barrier.a",
                Collections.<String>emptyList(),
                disableOrder,
                context->{
                    context.events().subscribe(
                        EnablePhaseEvent.class,
                        DomainEventBus.Priority.NORMAL,
                        event->
                            eventRuns.incrementAndGet()
                    );
                    context.scheduler().schedule(
                        1L,
                        taskRuns::incrementAndGet
                    );
                }
            );
        CascadePlugin second=
            new CascadePlugin(
                "enable.barrier.b",
                Collections.singletonList(
                    "enable.barrier.a"
                ),
                disableOrder,
                context->
                    registrationsComplete.countDown()
            );

        try{
            world.events().schedule(
                1L,
                ()->{
                    synchronized(world.events()){
                        Thread worker=
                            new Thread(
                                ()->{
                                    try{
                                        manager.enableAll(
                                            Arrays.<Plugin>asList(
                                                second,
                                                first
                                            )
                                        );
                                    }catch(Throwable failure){
                                        enableFailure.set(
                                            failure
                                        );
                                    }
                                },
                                "plugin-batch-commit-barrier"
                            );

                        enableThread.set(worker);
                        worker.start();

                        try{
                            if(!registrationsComplete.await(
                                    5L,
                                    TimeUnit.SECONDS))
                                throw new AssertionError(
                                    "batch registration did not reach commit phase"
                                );

                            long deadline=
                                System.nanoTime()+
                                TimeUnit.SECONDS
                                    .toNanos(5L);

                            while(worker.isAlive()&&
                                  worker.getState()!=
                                    Thread.State.BLOCKED&&
                                  System.nanoTime()<deadline)
                                Thread.yield();

                            if(worker.getState()!=
                                    Thread.State.BLOCKED)
                                throw new AssertionError(
                                    "batch enable did not block at queue commit barrier state="+
                                    worker.getState()
                                );

                            world.domainEvents()
                                .publish(
                                    new EnablePhaseEvent()
                                );
                        }catch(Exception failure){
                            throw new RuntimeException(
                                failure
                            );
                        }
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            Thread worker=enableThread.get();

            require(
                worker!=null,
                "batch commit worker was not created"
            );

            worker.join(5_000L);

            require(
                !worker.isAlive(),
                "batch commit worker did not complete"
            );
            require(
                enableFailure.get()==null,
                "batch commit worker failed: "+
                enableFailure.get()
            );
            require(
                eventRuns.get()==0,
                "batch event callback became visible before shared commit publication"
            );
            require(
                taskRuns.get()==0,
                "batch task ran before shared commit publication"
            );

            world.events().schedule(
                world.clock().tick()+1L,
                ()->{
                    try{
                        world.domainEvents()
                            .publish(
                                new EnablePhaseEvent()
                            );
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()+
                1_000L
            );

            require(
                eventRuns.get()==1,
                "committed batch event callback did not run exactly once"
            );
            require(
                taskRuns.get()==1,
                "committed batch task did not run exactly once"
            );

            manager.disable(
                "enable.barrier.b"
            );
            manager.disable(
                "enable.barrier.a"
            );
        }finally{
            Thread worker=enableThread.get();

            if(worker!=null&&worker.isAlive()){
                worker.interrupt();
                worker.join(1_000L);
            }

            world.close();
        }
    }

    private static void explicitDisableInFlightFailure()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        CountDownLatch entered=
            new CountDownLatch(1);
        CountDownLatch release=
            new CountDownLatch(1);
        AtomicReference<Throwable> pulseFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> disableFailure=
            new AtomicReference<>();

        CascadePlugin plugin=
            new CascadePlugin(
                "claimed.failure",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.events()
                        .subscribe(
                            ClaimedFailureEvent.class,
                            DomainEventBus.Priority.NORMAL,
                            event->{
                                entered.countDown();

                                boolean interrupted=false;
                                for(;;)
                                    try{
                                        release.await();
                                        break;
                                    }catch(InterruptedException ignored){
                                        interrupted=true;
                                    }

                                if(interrupted)
                                    Thread.currentThread()
                                        .interrupt();

                                throw new IllegalStateException(
                                    "claimed-inflight-failure"
                                );
                            }
                        )
            );

        try{
            manager.enable(
                plugin
            );

            world.events().schedule(
                1L,
                ()->{
                    try{
                        world.domainEvents()
                            .publish(
                                new ClaimedFailureEvent()
                            );
                    }catch(Throwable error){
                        pulseFailure.set(error);
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
                        }catch(Throwable error){
                            pulseFailure.set(error);
                        }
                    },
                    "plugin-claimed-failure-pulse"
                );
            pulse.start();

            require(
                entered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "claimed-failure callback did not enter"
            );

            Thread disable=
                new Thread(
                    ()->{
                        try{
                            manager.disable(
                                "claimed.failure"
                            );
                        }catch(Throwable error){
                            disableFailure.set(
                                error
                            );
                        }
                    },
                    "plugin-claimed-failure-disable"
                );
            disable.start();

            long deadline=
                System.nanoTime()+
                TimeUnit.SECONDS
                    .toNanos(5L);

            while(manager.plugin(
                    "claimed.failure")!=null&&
                  System.nanoTime()<deadline)
                Thread.yield();

            require(
                manager.plugin(
                    "claimed.failure"
                )==null,
                "explicit disable did not claim terminal ownership"
            );
            require(
                disable.isAlive(),
                "explicit disable completed before in-flight callback released"
            );

            release.countDown();

            pulse.join(5_000L);
            disable.join(5_000L);

            require(
                !pulse.isAlive()&&
                !disable.isAlive(),
                "claimed-failure threads did not terminate"
            );
            require(
                pulseFailure.get()==null&&
                disableFailure.get()==null,
                "claimed-failure path escaped error pulse="+
                pulseFailure.get()+
                " disable="+
                disableFailure.get()
            );
            require(
                plugin.disableCalls.get()==1,
                "claimed-failure cleanup owner duplicated disable"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=claimed.failure"
                            )&&
                            line.contains(
                                "phase=CALLBACK:EVENT:"
                            )&&
                            line.contains(
                                "claimed-inflight-failure"
                            )&&
                            line.contains(
                                " stack="
                            )
                    ),
                "in-flight failure diagnostic was lost after cleanup claim"
            );
        }finally{
            release.countDown();
            world.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void cleanupFailureDetached()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();

        CascadePlugin plugin=
            new CascadePlugin(
                "cleanup.failure",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->{}
            );

        try{
            PluginHandle handle=
                manager.enable(
                    plugin
                );

            java.lang.reflect.Field eventsField=
                handle.getClass()
                    .getDeclaredField(
                        "events"
                    );
            eventsField.setAccessible(true);
            Object tracker=
                eventsField.get(
                    handle
                );

            java.lang.reflect.Field subscriptionsField=
                tracker.getClass()
                    .getDeclaredField(
                        "subscriptions"
                    );
            subscriptionsField.setAccessible(true);

            List<DomainEventBus.Subscription>
                subscriptions=
                    (List<DomainEventBus.Subscription>)
                        subscriptionsField.get(
                            tracker
                        );

            subscriptions.add(
                new DomainEventBus.Subscription(){
                    @Override public boolean active(){
                        return true;
                    }

                    @Override public boolean unsubscribe(){
                        throw new IllegalStateException(
                            "injected-unsubscribe-failure"
                        );
                    }
                }
            );

            require(
                manager.disable(
                    "cleanup.failure"
                ),
                "cleanup-failure plugin did not disable"
            );
            require(
                plugin.disableCalls.get()==1,
                "cleanup-failure disable count"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=cleanup.failure"
                            )&&
                            line.contains(
                                "phase=CLEANUP:EVENT_UNSUBSCRIBE"
                            )&&
                            line.contains(
                                "injected-unsubscribe-failure"
                            )&&
                            line.contains(
                                " stack="
                            )
                    ),
                "event cleanup failure was not detached"
            );
        }finally{
            world.close();
        }
    }

    private static void assertFailureSinkReleased(
        PluginHandle handle
    )throws Exception{
        java.lang.reflect.Field sink=
            handle.getClass()
                .getDeclaredField(
                    "failureSink"
                );
        sink.setAccessible(true);

        if(sink.get(handle)!=null)
            throw new AssertionError(
                "terminal PluginHandle retained runtime failure sink"
            );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    @FunctionalInterface
    private interface Installer {
        void install(
            PluginContext context
        )throws Exception;
    }

    private static final class CascadePlugin
        implements Plugin {

        private final PluginManifest manifest;
        private final List<String> disableOrder;
        private final Installer installer;
        final AtomicInteger disableCalls=
            new AtomicInteger();

        CascadePlugin(
            String id,
            List<String> dependencies,
            List<String> disableOrder,
            Installer installer
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.disableOrder=disableOrder;
            this.installer=installer;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            installer.install(
                context
            );
        }

        @Override public void disable(){
            disableCalls.incrementAndGet();
            disableOrder.add(
                manifest.id()
            );
        }
    }

    private static final class CascadeEvent
        implements DomainEventBus.Event {}

    private static final class NestedOuterEvent
        implements DomainEventBus.Event {}

    private static final class NestedInnerEvent
        implements DomainEventBus.Event {}

    private static final class EnablePhaseEvent
        implements DomainEventBus.Event {}

    private static final class ClaimedFailureEvent
        implements DomainEventBus.Event {}

    private PluginRuntimeFailureCascadeTest(){}
}

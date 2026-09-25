package spk.local;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginManager;
import spk.plugin.api.PluginManifest;
import spk.plugin.api.PluginScheduler;
import spk.plugin.api.PluginTask;

public final class PluginSchedulerLifecycleTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);
        PluginManager manager=
            world.plugins();

        SchedulerPlugin plugin=
            new SchedulerPlugin(
                "scheduler.main"
            );

        try{
            manager.enable(plugin);

            AtomicInteger oneShotRuns=
                new AtomicInteger();

            PluginTask oneShot=
                plugin.scheduler.schedule(
                    1L,
                    oneShotRuns::incrementAndGet
                );

            if(!oneShot.active())
                throw new AssertionError(
                    "one-shot inactive before execution"
                );

            pulse(world);

            if(oneShotRuns.get()!=1||
               oneShot.active())
                throw new AssertionError(
                    "one-shot lifecycle mismatch runs="+
                    oneShotRuns.get()+
                    " active="+
                    oneShot.active()
                );

            AtomicInteger repeatRuns=
                new AtomicInteger();

            PluginTask repeating=
                plugin.scheduler
                    .scheduleRepeating(
                        1L,
                        1L,
                        repeatRuns::incrementAndGet
                    );

            pulse(world);
            pulse(world);

            if(repeatRuns.get()!=2||
               !repeating.active())
                throw new AssertionError(
                    "repeating cadence mismatch runs="+
                    repeatRuns.get()
                );

            if(!repeating.cancel()||
               repeating.active())
                throw new AssertionError(
                    "repeating cancel failed"
                );

            pulse(world);

            if(repeatRuns.get()!=2)
                throw new AssertionError(
                    "cancelled repeating task ran again"
                );

            AtomicInteger failureRuns=
                new AtomicInteger();
            SchedulerPlugin runtimeFailure=
                new SchedulerPlugin(
                    "scheduler.runtime-failure"
                );

            manager.enable(
                runtimeFailure
            );

            PluginTask failing=
                runtimeFailure.scheduler
                    .scheduleRepeating(
                        1L,
                        1L,
                        ()->{
                            failureRuns
                                .incrementAndGet();
                            throw new IllegalStateException(
                                "scheduler-test-failure"
                            );
                        }
                    );

            pulse(world);
            pulse(world);

            if(failureRuns.get()!=1||
               failing.active()||
               manager.plugin(
                   "scheduler.runtime-failure"
               )!=null||
               runtimeFailure.disableCalls.get()!=1)
                throw new AssertionError(
                    "failed task did not terminalize owner runs="+
                    failureRuns.get()+
                    " active="+
                    failing.active()+
                    " disableCalls="+
                    runtimeFailure.disableCalls.get()
                );

            assertSchedulerClosed(
                runtimeFailure.scheduler,
                "runtime-failure"
            );

            PluginTask queuedForDisable=
                plugin.scheduler.schedule(
                    10L,
                    ()->{
                        throw new AssertionError(
                            "disabled queued task executed"
                        );
                    }
                );

            if(!manager.disable(
                    "scheduler.main"
                ))
                throw new AssertionError(
                    "main scheduler plugin disable failed"
                );

            if(queuedForDisable.active())
                throw new AssertionError(
                    "plugin disable retained queued task"
                );

            assertSchedulerRootsReleased(
                plugin.scheduler,
                queuedForDisable,
                "post-disable"
            );

            assertSchedulerClosed(
                plugin.scheduler,
                "post-disable"
            );

            AtomicInteger rollbackRuns=
                new AtomicInteger();

            FailingEnablePlugin failingEnable=
                new FailingEnablePlugin(
                    rollbackRuns
                );

            boolean enableFailed=false;

            try{
                manager.enable(
                    failingEnable
                );
            }catch(Exception expected){
                enableFailed=true;
            }

            if(!enableFailed)
                throw new AssertionError(
                    "failing plugin enable unexpectedly succeeded"
                );

            pulse(world);

            if(rollbackRuns.get()!=0)
                throw new AssertionError(
                    "enable-failure task survived rollback"
                );

            if(failingEnable.disableCalls.get()!=1)
                throw new AssertionError(
                    "enable-failure compensation disable count="+
                    failingEnable.disableCalls.get()
                );

            assertSchedulerClosed(
                failingEnable.scheduler,
                "enable-rollback"
            );

            BlockingPlugin blocking=
                new BlockingPlugin(
                    manager
                );

            manager.enable(blocking);

            blocking.scheduler.schedule(
                1L,
                blocking::runBlockingTask
            );

            Thread pulseThread=
                new Thread(
                    ()->pulseUnchecked(world),
                    "plugin-scheduler-pulse-test"
                );
            pulseThread.start();

            if(!blocking.entered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking task did not enter"
                );

            CountDownLatch disableAttempted=
                new CountDownLatch(1);

            Thread disableThread=
                new Thread(
                    ()->{
                        disableAttempted.countDown();
                        manager.disable(
                            "scheduler.blocking"
                        );
                    },
                    "plugin-scheduler-disable-test"
                );
            disableThread.start();

            if(!disableAttempted.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "disable thread did not start"
                );

            long blockDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(2L);

            while(disableThread.getState()!=
                        Thread.State.BLOCKED&&
                  disableThread.isAlive()&&
                  System.nanoTime()<blockDeadline)
                Thread.yield();

            if(blocking.disableCalled.get())
                throw new AssertionError(
                    "plugin.disable ran concurrently with scheduler callback"
                );

            if(!disableThread.isAlive())
                throw new AssertionError(
                    "disable completed before running callback released"
                );

            blocking.allowManagerProbe
                .countDown();

            if(!blocking.managerProbeComplete.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "scheduler callback deadlocked querying PluginManager while disable waited"
                );

            if(blocking.disableCalled.get())
                throw new AssertionError(
                    "plugin.disable ran before callback ownership section completed"
                );

            if(!blocking.scheduleRejectedAfterFence.get())
                throw new AssertionError(
                    "running task admitted new schedule after disable admission fence"
                );

            blocking.release.countDown();

            pulseThread.join(5_000L);
            disableThread.join(5_000L);

            if(pulseThread.isAlive()||
               disableThread.isAlive())
                throw new AssertionError(
                    "scheduler concurrency threads did not terminate"
                );

            if(!blocking.disableCalled.get())
                throw new AssertionError(
                    "plugin.disable did not run after callback completed"
                );

            SchedulerPlugin closePlugin=
                new SchedulerPlugin(
                    "scheduler.close"
                );

            manager.enable(closePlugin);

            PluginTask closeTask=
                closePlugin.scheduler.schedule(
                    10L,
                    ()->{
                        throw new AssertionError(
                            "World-close task executed"
                        );
                    }
                );

            PluginScheduler retained=
                closePlugin.scheduler;

            world.close();

            if(closeTask.active())
                throw new AssertionError(
                    "World close retained plugin task"
                );

            if(closePlugin.disableCalls.get()!=1)
                throw new AssertionError(
                    "World close did not disable plugin exactly once"
                );

            assertSchedulerRootsReleased(
                retained,
                closeTask,
                "world-close"
            );

            assertSchedulerClosed(
                retained,
                "world-close"
            );

            System.out.println(
                "PLUGIN_SCHEDULER_LIFECYCLE_PASS "+
                "oneShot=true "+
                "repeating=true "+
                "cancel=true "+
                "failureTerminalizedOwner=true "+
                "disableCancelled=true "+
                "enableRollback=true "+
                "disableSerializedWithCallback=true "+
                "taskAdmissionFenceImmediate=true "+
                "managerTaskLockOrderSafe=true "+
                "worldCloseCancelled=true "+
                "terminalSchedulerRootsReleased=true "+
                "terminalTaskCallbackReleased=true"
            );
        }finally{
            if(!world.closed())
                world.close();
        }
    }

    private static void pulse(
        World world
    ){
        world.observePulse(
            System.currentTimeMillis()
        );
    }

    private static void pulseUnchecked(
        World world
    ){
        try{
            pulse(world);
        }catch(Throwable failure){
            throw new RuntimeException(
                failure
            );
        }
    }

    private static void assertSchedulerRootsReleased(
        PluginScheduler scheduler,
        PluginTask task,
        String phase
    )throws Exception{
        Class<?> trackerType=
            scheduler.getClass();

        for(String fieldName:
                new String[]{
                    "clock",
                    "queue",
                    "worldOpen",
                    "callbackLoader"
                }){
            java.lang.reflect.Field field=
                trackerType.getDeclaredField(
                    fieldName
                );
            field.setAccessible(true);

            if(field.get(scheduler)!=null)
                throw new AssertionError(
                    phase+
                    " scheduler retained "+
                    fieldName
                );
        }

        java.lang.reflect.Field actionField=
            task.getClass()
                .getDeclaredField(
                    "action"
                );
        actionField.setAccessible(true);

        if(actionField.get(task)!=null)
            throw new AssertionError(
                phase+
                " task retained callback action"
            );
    }

    private static void assertSchedulerClosed(
        PluginScheduler scheduler,
        String phase
    ){
        if(scheduler==null)
            throw new AssertionError(
                phase+" scheduler missing"
            );

        boolean rejected=false;

        try{
            scheduler.schedule(
                1L,
                ()->{}
            );
        }catch(IllegalStateException expected){
            rejected=
                "plugin scheduler closed".equals(
                    expected.getMessage()
                );
        }

        if(!rejected)
            throw new AssertionError(
                phase+
                " retained scheduler accepted new work"
            );
    }

    private static class SchedulerPlugin
        implements Plugin {

        final PluginManifest manifest;
        final AtomicInteger disableCalls=
            new AtomicInteger();
        PluginScheduler scheduler;

        SchedulerPlugin(
            String id
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.<String>emptyList()
                );
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
        }
    }

    private static final class FailingEnablePlugin
        extends SchedulerPlugin {

        final AtomicInteger taskRuns;

        FailingEnablePlugin(
            AtomicInteger taskRuns
        ){
            super(
                "scheduler.enable-failure"
            );
            this.taskRuns=taskRuns;
        }

        @Override public void enable(
            PluginContext context
        ){
            super.enable(context);

            scheduler.schedule(
                1L,
                taskRuns::incrementAndGet
            );

            throw new IllegalStateException(
                "intentional enable failure"
            );
        }
    }

    private static final class BlockingPlugin
        extends SchedulerPlugin {

        final PluginManager manager;
        final CountDownLatch entered=
            new CountDownLatch(1);
        final CountDownLatch allowManagerProbe=
            new CountDownLatch(1);
        final CountDownLatch managerProbeComplete=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);
        final AtomicBoolean disableCalled=
            new AtomicBoolean();
        final AtomicBoolean scheduleRejectedAfterFence=
            new AtomicBoolean();

        BlockingPlugin(
            PluginManager manager
        ){
            super(
                "scheduler.blocking"
            );
            this.manager=manager;
        }

        void runBlockingTask(){
            entered.countDown();

            boolean interrupted=false;

            for(;;){
                try{
                    allowManagerProbe.await();
                    break;
                }catch(InterruptedException ignored){
                    interrupted=true;
                }
            }

            try{
                scheduler.schedule(
                    1L,
                    ()->{}
                );
            }catch(IllegalStateException expected){
                scheduleRejectedAfterFence.set(
                    true
                );
            }

            manager.enabled();
            managerProbeComplete.countDown();

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

        @Override public void disable(){
            disableCalled.set(true);
            super.disable();
        }
    }

    private PluginSchedulerLifecycleTest(){}
}

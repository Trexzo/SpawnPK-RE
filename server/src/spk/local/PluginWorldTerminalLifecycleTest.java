package spk.local;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import spk.plugin.api.*;

public final class PluginWorldTerminalLifecycleTest {
    public static void main(String[] args)throws Exception{
        externalClose();
        inlineNeverStartedClose();
        pulseContextClose();

        System.out.println(
            "PLUGIN_WORLD_TERMINAL_LIFECYCLE_PASS "+
            "externalDisableWorldContext=true "+
            "neverStartedCompatibilityContext=true "+
            "pulseCloseNoSelfJoin=true "+
            "admissionFenceBeforeDisable=true "+
            "tasksDestroyedAfterDisable=true "+
            "disableExactlyOnce=true "+
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

    private static final class TerminalPlugin
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
        final AtomicBoolean scheduleRejectedDuringDisable=
            new AtomicBoolean();
        final AtomicBoolean queuedActiveDuringDisable=
            new AtomicBoolean();

        volatile PluginScheduler scheduler;
        volatile PluginTask queued;

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
            disableOnWorld.set(
                world.pulse()
                    .inExecutionContext()
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

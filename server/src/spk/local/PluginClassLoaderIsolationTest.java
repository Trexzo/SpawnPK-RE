package spk.local;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManager;

public final class PluginClassLoaderIsolationTest {
    private static final String ENTRYPOINT=
        "fixture.plugin.IsolationPlugin";

    public static void main(
        String[] args
    )throws Exception{
        if(args.length!=2)
            throw new IllegalArgumentException(
                "expected isolation A/B JAR paths"
            );

        Path jarA=
            Paths.get(args[0])
                .toAbsolutePath()
                .normalize();
        Path jarB=
            Paths.get(args[1])
                .toAbsolutePath()
                .normalize();

        ClassLoader baseline=
            Thread.currentThread()
                .getContextClassLoader();

        assertReservedNamespaceRejected(
            "spk/plugin/api/Fake.class"
        );
        assertReservedNamespaceRejected(
            "spk/content/api/Fake.class"
        );
        assertReservedNamespaceRejected(
            "spk/event/Fake.class"
        );
        assertReservedNamespaceRejected(
            "spk/local/Fake.class"
        );
        assertReservedNamespaceRejected(
            "spk/plugin/fixture/Fake.class"
        );

        PluginJarLoader.LoadedPlugin loadedA=
            PluginJarLoader.load(
                jarA,
                ENTRYPOINT
            );

        URLClassLoader collisionParent=
            new URLClassLoader(
                new URL[]{
                    jarA.toUri().toURL()
                },
                Plugin.class.getClassLoader()
            );

        PluginJarLoader.LoadedPlugin loadedB=
            PluginJarLoader.load(
                jarB,
                ENTRYPOINT,
                collisionParent
            );

        assertTcclRestored(
            baseline,
            "plugin load"
        );

        World world=
            World.isolatedForTest(
                25L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "plugin-isolation-player"
            );

        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        PluginManager manager=
            world.plugins();

        try{
            PluginHandle handleA=
                manager.enable(
                    loadedA
                );
            assertTcclRestored(
                baseline,
                "enable A"
            );

            PluginHandle handleB=
                manager.enable(
                    loadedB
                );
            assertTcclRestored(
                baseline,
                "enable B"
            );

            boolean ownedDuplicateRejected=
                false;

            try{
                manager.enable(
                    loadedA
                );
            }catch(IllegalStateException expected){
                ownedDuplicateRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "already enabled"
                        );
            }

            if(!ownedDuplicateRejected||
               loadedA.closed())
                throw new AssertionError(
                    "owned duplicate attempt closed live loader"
                );

            PluginJarLoader.LoadedPlugin
                duplicateA=
                    PluginJarLoader.load(
                        jarA,
                        ENTRYPOINT
                    );
            boolean duplicateRejected=false;

            try{
                manager.enable(
                    duplicateA
                );
            }catch(IllegalStateException expected){
                duplicateRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "already enabled"
                        );
            }

            if(!duplicateRejected||
               !duplicateA.closed())
                throw new AssertionError(
                    "pre-enable duplicate rejection did not close loader"
                );

            assertTcclRestored(
                baseline,
                "duplicate reject"
            );

            final String[] commandResults=
                new String[2];
            final boolean[] throwObserved=
                new boolean[1];
            final boolean[] restoredAfterThrow=
                new boolean[1];

            DomainEventBus.Event eventA=
                event(
                    loadedA
                );
            DomainEventBus.Event eventB=
                event(
                    loadedB
                );

            world.submitAndWait(
                player,
                generation,
                ()->{
                    ContentResult a=
                        world.content()
                            .dispatchCommand(
                                player,
                                "isolationa",
                                writer
                            );
                    ContentResult b=
                        world.content()
                            .dispatchCommand(
                                player,
                                "isolationb",
                                writer
                            );

                    commandResults[0]=
                        a==null
                            ?null
                            :a.logText();
                    commandResults[1]=
                        b==null
                            ?null
                            :b.logText();

                    world.domainEvents()
                        .publish(
                            eventA
                        );
                    world.domainEvents()
                        .publish(
                            eventB
                        );
                },
                5_000L
            );

            assertTcclRestored(
                baseline,
                "content/event callbacks"
            );

            equals(
                "A",
                commandResults[0],
                "plugin A private dependency"
            );
            equals(
                "B",
                commandResults[1],
                "plugin B private dependency"
            );

            long schedulerDeadline=
                System.nanoTime()+
                java.util.concurrent.TimeUnit
                    .SECONDS.toNanos(5L);
            String healthyReportA;
            String healthyReportB;

            for(;;){
                healthyReportA=
                    report(
                        loadedA
                    );
                healthyReportB=
                    report(
                        loadedB
                    );

                if(healthyReportA.contains(
                        "task=true")&&
                   healthyReportB.contains(
                        "task=true"))
                    break;

                if(System.nanoTime()>=
                        schedulerDeadline)
                    throw new AssertionError(
                        "scheduler callbacks did not complete A="+
                        healthyReportA+
                        " B="+
                        healthyReportB
                    );

                Thread.sleep(1L);
            }

            assertTcclRestored(
                baseline,
                "scheduler callback"
            );

            assertReport(
                healthyReportA,
                "A",
                false
            );
            assertReport(
                healthyReportB,
                "B",
                false
            );

            world.submitAndWait(
                player,
                generation,
                ()->{
                    try{
                        world.content()
                            .dispatchCommand(
                                player,
                                "isolationthrowa",
                                writer
                            );
                    }catch(IllegalStateException expected){
                        throwObserved[0]=
                            "fixture-throw-A"
                                .equals(
                                    expected
                                        .getMessage()
                                );
                    }

                    restoredAfterThrow[0]=
                        Thread.currentThread()
                            .getContextClassLoader()==
                                baseline;
                },
                5_000L
            );

            if(!throwObserved[0]||
               !restoredAfterThrow[0])
                throw new AssertionError(
                    "throw-path TCCL restoration failed"
                );

            if(handleA.enabled()||
               !loadedA.closed())
                throw new AssertionError(
                    "throwing callback did not terminalize plugin A"
                );

            String failedReportA=
                report(
                    loadedA
                );

            assertReport(
                failedReportA,
                "A",
                true
            );

            if(manager.disable(
                    "isolation.a"))
                throw new AssertionError(
                    "terminal plugin A repeated explicit disable"
                );

            if(!handleB.enabled()||
               loadedB.closed())
                throw new AssertionError(
                    "plugin A failure terminalized unrelated plugin B"
                );

            if(!manager.disable(
                    "isolation.b"))
                throw new AssertionError(
                    "disable B returned false"
                );

            if(handleA.enabled()||
               handleB.enabled())
                throw new AssertionError(
                    "disabled handle remained enabled"
                );

            if(!loadedA.closed()||
               !loadedB.closed())
                throw new AssertionError(
                    "manager did not close plugin classloader"
                );

            String disabledA=
                report(
                    loadedA
                );
            String disabledB=
                report(
                    loadedB
                );

            if(!disabledA.contains(
                    "disable=true")||
               !disabledB.contains(
                    "disable=true"))
                throw new AssertionError(
                    "disable callback did not observe plugin TCCL A="+
                    disabledA+
                    " B="+
                    disabledB
                );

            assertHandleReleasedLoader(
                handleA
            );
            assertHandleReleasedLoader(
                handleB
            );

            assertTcclRestored(
                baseline,
                "disable"
            );
        }finally{
            if(!world.closed()){
                if(world.players().owns(
                        player,
                        generation))
                    world.unregisterPlayer(
                        player,
                        generation
                    );
                world.close();
            }

            loadedA.close();
            loadedB.close();
            collisionParent.close();

            Thread.currentThread()
                .setContextClassLoader(
                    baseline
                );
        }

        System.out.println(
            "PLUGIN_CLASSLOADER_ISOLATION_PASS "+
            "childFirstPrivate=true "+
            "parentCollisionIsolated=true "+
            "childFirstResource=true "+
            "childFirstResourceEnumeration=true "+
            "apiParentIdentity=true "+
            "serverInternalDenied=true "+
            "reservedNamespaceRejected=true "+
            "constructorTccl=true "+
            "manifestTccl=true "+
            "enableTccl=true "+
            "contentTccl=true "+
            "eventTccl=true "+
            "schedulerTccl=true "+
            "throwTcclRestored=true "+
            "throwFailureTerminalizedOwner=true "+
            "unrelatedPluginSurvivesFailure=true "+
            "successTcclRestored=true "+
            "preEnableRejectClosesLoader=true "+
            "ownedDuplicateKeepsLoader=true "+
            "disableTccl=true "+
            "disableClosesLoader=true "+
            "terminalHandleReleasesLoader=true "+
            "publicApiExpanded=false"
        );
    }

    private static DomainEventBus.Event event(
        PluginJarLoader.LoadedPlugin loaded
    )throws Exception{
        Class<?> type=
            Class.forName(
                ENTRYPOINT+
                "$ProbeEvent",
                true,
                loaded.classLoader()
            );

        return (DomainEventBus.Event)
            type.getDeclaredConstructor()
                .newInstance();
    }

    private static String report(
        PluginJarLoader.LoadedPlugin loaded
    )throws Exception{
        Object delegate=
            loaded.delegate();
        Method method=
            delegate.getClass()
                .getMethod(
                    "report"
                );

        return (String)
            method.invoke(
                delegate
            );
    }

    private static void assertReport(
        String report,
        String expectedVersion,
        boolean expectedThrow
    ){
        if(report==null||
           !report.startsWith(
                expectedVersion+
                "|")||
           !report.contains(
                "constructor=true")||
           !report.contains(
                "manifest=true")||
           !report.contains(
                "enable=true")||
           !report.contains(
                "apiIdentity=true")||
           !report.contains(
                "serverInternalDenied=true")||
           !report.contains(
                "resource="+
                expectedVersion+
                "_RESOURCE")||
           !report.contains(
                "resourceEnumeration="+
                expectedVersion+
                "_RESOURCE#1")||
           !report.contains(
                "command=true")||
           !report.contains(
                "throw="+
                expectedThrow)||
           !report.contains(
                "event=true")||
           !report.contains(
                "task=true"))
            throw new AssertionError(
                "plugin isolation report mismatch expected="+
                expectedVersion+
                " throw="+
                expectedThrow+
                " actual="+
                report
            );
    }

    private static void assertReservedNamespaceRejected(
        String entry
    )throws Exception{
        Path jar=
            Files.createTempFile(
                "plugin-reserved-",
                ".jar"
            );

        try{
            try(JarOutputStream out=
                    new JarOutputStream(
                        Files.newOutputStream(
                            jar
                        )
                    )){
                out.putNextEntry(
                    new JarEntry(
                        entry
                    )
                );
                out.write(
                    new byte[]{0}
                );
                out.closeEntry();
            }

            boolean rejected=false;

            try{
                PluginJarLoader.load(
                    jar,
                    "fixture.plugin.Missing"
                );
            }catch(IllegalArgumentException expected){
                rejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "reserved server namespace"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "reserved namespace accepted: "+
                    entry
                );
        }finally{
            Files.deleteIfExists(
                jar
            );
        }
    }

    private static void assertHandleReleasedLoader(
        PluginHandle handle
    )throws Exception{
        java.lang.reflect.Field pluginField=
            handle.getClass()
                .getDeclaredField(
                    "plugin"
                );
        pluginField.setAccessible(
            true
        );

        if(pluginField.get(handle)!=null)
            throw new AssertionError(
                "disabled PluginHandle retained plugin instance"
            );

        java.lang.reflect.Field tasksField=
            handle.getClass()
                .getDeclaredField(
                    "tasks"
                );
        tasksField.setAccessible(
            true
        );
        Object tasks=
            tasksField.get(handle);

        java.lang.reflect.Field loaderField=
            tasks.getClass()
                .getDeclaredField(
                    "callbackLoader"
                );
        loaderField.setAccessible(
            true
        );

        if(loaderField.get(tasks)!=null)
            throw new AssertionError(
                "disabled PluginHandle retained callback classloader"
            );
    }

    private static void assertTcclRestored(
        ClassLoader expected,
        String phase
    ){
        if(Thread.currentThread()
                .getContextClassLoader()!=
                    expected)
            throw new AssertionError(
                phase+
                " leaked plugin TCCL"
            );
    }

    private static void equals(
        Object expected,
        Object actual,
        String phase
    ){
        if(expected==null
                ?actual!=null
                :!expected.equals(actual))
            throw new AssertionError(
                phase+
                " expected="+
                expected+
                " actual="+
                actual
            );
    }

    private PluginClassLoaderIsolationTest(){}
}

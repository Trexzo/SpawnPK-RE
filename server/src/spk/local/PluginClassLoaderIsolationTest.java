package spk.local;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManifest;
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

        assertArchiveSnapshotPinned(
            jarA,
            jarB
        );
        assertManifestClasspathRejected(
            jarA,
            jarB
        );
        assertManifestParsingBounded(
            jarA
        );
        assertJarIndexRejected(
            jarA,
            jarB
        );
        assertPostLoaderFailureRetiresSnapshot(
            jarA
        );
        assertExceptionalCloseReleasesRoots(
            jarA
        );
        assertCaptureFailureCleanupDebt(
            jarA
        );
        assertValidationRollbackCleanupDebt(
            jarA
        );
        assertLiveCloseRace(
            jarA
        );

        PluginJarLoader.LoadedPlugin loadedA=
            PluginJarLoader.load(
                jarA,
                ENTRYPOINT
            );

        assertEventNamespaceNarrow(
            loadedA.classLoader()
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

        System.clearProperty(
            "spawnpk.fixture.isolation.a.throwTccl"
        );
        System.clearProperty(
            "spawnpk.fixture.isolation.a.disableTccl"
        );
        System.clearProperty(
            "spawnpk.fixture.isolation.b.disableTccl"
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

            assertRuntimeReferencesReleased(
                duplicateA,
                "pre-enable duplicate"
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

            if(!"true".equals(
                    System.getProperty(
                        "spawnpk.fixture.isolation.a.throwTccl"
                    )))
                throw new AssertionError(
                    "throwing callback lost plugin TCCL evidence"
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

            if(!"true".equals(
                    System.getProperty(
                        "spawnpk.fixture.isolation.a.disableTccl"
                    ))||
               !"true".equals(
                    System.getProperty(
                        "spawnpk.fixture.isolation.b.disableTccl"
                    )))
                throw new AssertionError(
                    "disable callback did not observe plugin TCCL"
                );

            assertHandleReleasedLoader(
                handleA
            );
            assertHandleReleasedLoader(
                handleB
            );
            assertRuntimeReferencesReleased(
                loadedA,
                "terminal callback failure"
            );
            assertRuntimeReferencesReleased(
                loadedB,
                "explicit disable"
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
            System.clearProperty(
                "spawnpk.fixture.isolation.a.throwTccl"
            );
            System.clearProperty(
                "spawnpk.fixture.isolation.a.disableTccl"
            );
            System.clearProperty(
                "spawnpk.fixture.isolation.b.disableTccl"
            );
        }

        System.out.println(
            "PLUGIN_CLASSLOADER_ISOLATION_PASS "+
            "childFirstPrivate=true "+
            "parentCollisionIsolated=true "+
            "childFirstResource=true "+
            "childFirstResourceEnumeration=true "+
            "apiParentIdentity=true "+
            "eventNamespaceNarrow=true "+
            "serverInternalDenied=true "+
            "reservedNamespaceRejected=true "+
            "javaPluginArchiveIdentityPinned=true "+
            "javaPluginManifestClasspathFenced=true "+
            "javaPluginManifestSizeBounded=true "+
            "javaPluginJarIndexFenced=true "+
            "javaPluginSnapshotFailureRetired=true "+
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
            "javaPluginRuntimeReferencesReleased=true "+
            "javaPluginExceptionalCloseReleased=true "+
            "javaPluginArchiveCleanupDebtRetried=true "+
            "javaPluginCaptureFailureDebtRetried=true "+
            "javaPluginValidationRollbackDebtRetried=true "+
            "javaPluginCloseRaceSafe=true "+
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

    private static void assertEventNamespaceNarrow(
        ClassLoader loader
    )throws Exception{
        Class<?> exported=
            Class.forName(
                "spk.event.DomainEventBus",
                false,
                loader
            );

        if(exported!=DomainEventBus.class)
            throw new AssertionError(
                "DomainEventBus lost parent API identity"
            );

        boolean denied=false;

        try{
            Class.forName(
                "spk.event.DomainEventBusTest",
                false,
                loader
            );
        }catch(ClassNotFoundException expected){
            denied=true;
        }

        if(!denied)
            throw new AssertionError(
                "non-API spk.event test class leaked through plugin parent boundary"
            );

        if(loader.getResource(
                "spk/event/DomainEventBusTest.class"
            )!=null)
            throw new AssertionError(
                "non-API spk.event test resource leaked through plugin parent boundary"
            );

        boolean privateNestedDenied=false;

        try{
            Class.forName(
                "spk.event.DomainEventBus$Binding",
                false,
                loader
            );
        }catch(ClassNotFoundException expected){
            privateNestedDenied=true;
        }

        if(!privateNestedDenied)
            throw new AssertionError(
                "private DomainEventBus nested class leaked through plugin parent boundary"
            );

        if(loader.getResource(
                "spk/event/DomainEventBus$Binding.class"
            )!=null)
            throw new AssertionError(
                "private DomainEventBus nested resource leaked through plugin parent boundary"
            );

        if(loader.getResource(
                "spk/event/DomainEventBus.class"
            )==null)
            throw new AssertionError(
                "DomainEventBus API resource missing from plugin parent boundary"
            );
    }

    private static String report(
        PluginJarLoader.LoadedPlugin loaded
    )throws Exception{
        return report(
            loaded.delegate()
        );
    }

    private static String report(
        Object delegate
    )throws Exception{
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

    private static void assertManifestClasspathRejected(
        Path jarA,
        Path jarB
    )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "plugin-manifest-classpath-"
            );
        Path sibling=
            directory.resolve(
                "b.jar"
            );

        try{
            Files.copy(
                jarB,
                sibling,
                StandardCopyOption.REPLACE_EXISTING
            );

            assertManifestClasspathRejectedCase(
                jarA,
                directory.resolve(
                    "absolute.jar"
                ),
                jarB.toUri().toString(),
                "absolute"
            );
            assertManifestClasspathRejectedCase(
                jarA,
                directory.resolve(
                    "relative.jar"
                ),
                sibling.getFileName()
                    .toString(),
                "relative"
            );
        }finally{
            Files.deleteIfExists(
                directory.resolve(
                    "absolute.jar"
                )
            );
            Files.deleteIfExists(
                directory.resolve(
                    "relative.jar"
                )
            );
            Files.deleteIfExists(
                sibling
            );
            Files.deleteIfExists(
                directory
            );
        }
    }

    private static void assertManifestClasspathRejectedCase(
        Path jarA,
        Path poisoned,
        String classPath,
        String label
    )throws Exception{
        writeJarWithManifestClasspath(
            jarA,
            poisoned,
            classPath
        );

        final Path[] snapshot=
            new Path[1];
        boolean rejected=false;

        try{
            PluginJarLoader.load(
                poisoned,
                ENTRYPOINT,
                Plugin.class.getClassLoader(),
                (source,admitted)->{},
                (source,admitted)->
                    snapshot[0]=admitted
            );
        }catch(IllegalArgumentException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "manifest Class-Path is forbidden"
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "Java plugin "+
                label+
                " manifest Class-Path was accepted"
            );

        assertSnapshotRetired(
            snapshot[0],
            label+
                " manifest Class-Path rejection"
        );
    }

    private static void assertManifestParsingBounded(
        Path jarA
    )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "plugin-manifest-bounded-"
            );
        Path oversizedMain=
            directory.resolve(
                "oversized-main.jar"
            );
        Path continued=
            directory.resolve(
                "continued.jar"
            );
        Path alias=
            directory.resolve(
                "alias.jar"
            );
        Path ambiguous=
            directory.resolve(
                "ambiguous.jar"
            );
        Path nearSpec=
            directory.resolve(
                "near-spec.jar"
            );
        Path namedHealthy=
            directory.resolve(
                "named-healthy.jar"
            );
        Path malformedNamed=
            directory.resolve(
                "malformed-named.jar"
            );
        Path oversizedTotal=
            directory.resolve(
                "oversized-total.jar"
            );
        Path signedLooking=
            directory.resolve(
                "signed-looking.jar"
            );

        try{
            writeJarWithRawManifest(
                jarA,
                oversizedMain,
                oversizedManifestMain()
            );
            assertJavaManifestRejected(
                oversizedMain,
                "manifest main section exceeds",
                "oversized main section"
            );

            writeJarWithRawManifest(
                jarA,
                continued,
                (
                    "Manifest-Version: 1.0\r\n"+
                    "Class-Path: sibling-\r\n"+
                    " continued.jar\r\n"+
                    "\r\n"
                ).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            assertJavaManifestRejected(
                continued,
                "manifest Class-Path is forbidden",
                "continued Class-Path"
            );

            writeJarWithRawManifest(
                jarA,
                alias,
                "meta-inf/manifest.mf",
                (
                    "Manifest-Version: 1.0\r\n"+
                    "Class-Path: hidden.jar\r\n"+
                    "\r\n"
                ).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            assertJavaManifestRejected(
                alias,
                "manifest Class-Path is forbidden",
                "case-insensitive manifest Class-Path"
            );

            writeJarWithAmbiguousManifest(
                jarA,
                ambiguous
            );
            assertJavaManifestRejected(
                ambiguous,
                "ambiguous manifest authority",
                "ambiguous manifest candidates"
            );

            writeJarWithRawManifest(
                jarA,
                nearSpec,
                nearSpecManifestMain()
            );
            loadAndCloseJavaPlugin(
                nearSpec,
                "near-spec manifest"
            );

            writeJarWithRawManifest(
                jarA,
                namedHealthy,
                namedSectionManifest(
                    BoundedManifestMain
                        .MAX_MANIFEST_BYTES/2
                )
            );
            loadAndCloseJavaPlugin(
                namedHealthy,
                "bounded named sections"
            );

            writeJarWithRawManifest(
                jarA,
                malformedNamed,
                malformedNamedManifest()
            );
            assertJavaManifestRejected(
                malformedNamed,
                "manifest syntax is invalid",
                "malformed named manifest section"
            );

            writeJarWithRawManifest(
                jarA,
                oversizedTotal,
                namedSectionManifest(
                    BoundedManifestMain
                        .MAX_MANIFEST_BYTES+
                    1024
                )
            );
            assertJavaManifestRejected(
                oversizedTotal,
                "plugin manifest exceeds",
                "oversized total manifest"
            );

            writeJarWithRawManifest(
                jarA,
                signedLooking,
                "META-INF/MANIFEST.MF",
                oversizedManifestMain(),
                "META-INF/TEST.SF",
                "Signature-Version: 1.0\r\n\r\n"
                    .getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    )
            );
            assertJavaManifestRejected(
                signedLooking,
                "manifest main section exceeds",
                "signed-looking oversized manifest"
            );
        }finally{
            Files.deleteIfExists(
                signedLooking
            );
            Files.deleteIfExists(
                oversizedTotal
            );
            Files.deleteIfExists(
                malformedNamed
            );
            Files.deleteIfExists(
                namedHealthy
            );
            Files.deleteIfExists(
                nearSpec
            );
            Files.deleteIfExists(
                ambiguous
            );
            Files.deleteIfExists(
                alias
            );
            Files.deleteIfExists(
                continued
            );
            Files.deleteIfExists(
                oversizedMain
            );
            Files.deleteIfExists(
                directory
            );
        }
    }

    private static void loadAndCloseJavaPlugin(
        Path jar,
        String label
    )throws Exception{
        PluginJarLoader.LoadedPlugin loaded=
            PluginJarLoader.load(
                jar,
                ENTRYPOINT
            );

        loaded.close();

        if(!loaded.closed())
            throw new AssertionError(
                label+
                " healthy load did not close"
            );
    }

    private static void assertJavaManifestRejected(
        Path jar,
        String expected,
        String label
    )throws Exception{
        final Path[] snapshot=
            new Path[1];
        boolean rejected=false;

        try{
            PluginJarLoader.load(
                jar,
                ENTRYPOINT,
                Plugin.class.getClassLoader(),
                (source,admitted)->{},
                (source,admitted)->
                    snapshot[0]=admitted
            );
        }catch(IllegalArgumentException failure){
            rejected=
                failure.getMessage()!=null&&
                failure.getMessage()
                    .contains(
                        expected
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "Java plugin manifest "+
                label+
                " was accepted"
            );

        assertSnapshotRetired(
            snapshot[0],
            label+
                " rejection"
        );
    }

    private static byte[] nearSpecManifestMain()
        throws Exception{
        java.io.ByteArrayOutputStream out=
            new java.io.ByteArrayOutputStream();

        out.write(
            "Manifest-Version: 1.0\r\n"
                .getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
        );
        writeFoldedManifestHeader(
            out,
            "X-Near",
            65535
        );
        out.write(
            "\r\n".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );

        if(out.size()>=
                BoundedManifestMain
                    .MAX_MAIN_SECTION_BYTES)
            throw new AssertionError(
                "near-spec manifest fixture exceeds project main cap"
            );

        return out.toByteArray();
    }

    private static void writeFoldedManifestHeader(
        java.io.ByteArrayOutputStream out,
        String name,
        int valueBytes
    )throws Exception{
        int remaining=valueBytes;
        int first=
            Math.min(
                60,
                remaining
            );

        out.write(
            (
                name+
                ": "+
                repeatAscii(
                    'v',
                    first
                )+
                "\r\n"
            ).getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );
        remaining-=first;

        while(remaining>0){
            int take=
                Math.min(
                    68,
                    remaining
                );
            out.write(
                (
                    " "+
                    repeatAscii(
                        'v',
                        take
                    )+
                    "\r\n"
                ).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            remaining-=take;
        }
    }

    private static String repeatAscii(
        char value,
        int count
    ){
        char[] chars=
            new char[count];
        java.util.Arrays.fill(
            chars,
            value
        );
        return new String(
            chars
        );
    }

    private static byte[] oversizedManifestMain()
        throws Exception{
        java.io.ByteArrayOutputStream out=
            new java.io.ByteArrayOutputStream();

        out.write(
            (
                "Manifest-Version: 1.0\r\n"+
                "X-Fill: "
            ).getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );

        for(int i=0;
            i<BoundedManifestMain
                .MAX_MAIN_SECTION_BYTES+
                1024;
            i++)
            out.write(
                'a'
            );

        return out.toByteArray();
    }

    private static byte[] namedSectionManifest(
        int targetBytes
    )throws Exception{
        java.io.ByteArrayOutputStream out=
            new java.io.ByteArrayOutputStream();

        out.write(
            (
                "Manifest-Version: 1.0\r\n"+
                "\r\n"
            ).getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            )
        );

        int section=0;

        while(out.size()<targetBytes){
            out.write(
                (
                    "Name: ignored/section/"+
                    section+
                    "\r\n"
                ).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            writeFoldedManifestHeader(
                out,
                "X-Fill",
                1024
            );
            out.write(
                "\r\n".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            section++;
        }

        return out.toByteArray();
    }

    private static byte[] malformedNamedManifest()
        throws Exception{
        return (
            "Manifest-Version: 1.0\r\n"+
            "\r\n"+
            "Name: broken/section\r\n"+
            "This line has no manifest attribute separator\r\n"+
            "\r\n"
        ).getBytes(
            java.nio.charset.StandardCharsets.UTF_8
        );
    }

    private static void writeJarWithAmbiguousManifest(
        Path source,
        Path target
    )throws Exception{
        try(JarFile input=
                new JarFile(
                    source.toFile()
                );
            JarOutputStream output=
                new JarOutputStream(
                    Files.newOutputStream(
                        target
                    )
                )){
            byte[] manifest=
                (
                    "Manifest-Version: 1.0\r\n"+
                    "\r\n"
                ).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                );

            output.putNextEntry(
                new JarEntry(
                    "META-INF/MANIFEST.MF"
                )
            );
            output.write(
                manifest
            );
            output.closeEntry();

            output.putNextEntry(
                new JarEntry(
                    "meta-inf/manifest.mf"
                )
            );
            output.write(
                manifest
            );
            output.closeEntry();

            copyJarEntriesWithoutManifest(
                input,
                output
            );
        }
    }

    private static void writeJarWithRawManifest(
        Path source,
        Path target,
        byte[] manifestBytes
    )throws Exception{
        writeJarWithRawManifest(
            source,
            target,
            "META-INF/MANIFEST.MF",
            manifestBytes,
            null,
            null
        );
    }

    private static void writeJarWithRawManifest(
        Path source,
        Path target,
        String manifestEntry,
        byte[] manifestBytes
    )throws Exception{
        writeJarWithRawManifest(
            source,
            target,
            manifestEntry,
            manifestBytes,
            null,
            null
        );
    }

    private static void writeJarWithRawManifest(
        Path source,
        Path target,
        String manifestEntry,
        byte[] manifestBytes,
        String extraEntry,
        byte[] extraBytes
    )throws Exception{
        try(JarFile input=
                new JarFile(
                    source.toFile()
                );
            JarOutputStream output=
                new JarOutputStream(
                    Files.newOutputStream(
                        target
                    )
                )){
            output.putNextEntry(
                new JarEntry(
                    manifestEntry
                )
            );
            output.write(
                manifestBytes
            );
            output.closeEntry();

            if(extraEntry!=null){
                output.putNextEntry(
                    new JarEntry(
                        extraEntry
                    )
                );
                if(extraBytes!=null)
                    output.write(
                        extraBytes
                    );
                output.closeEntry();
            }

            copyJarEntriesWithoutManifest(
                input,
                output
            );
        }
    }

    private static void copyJarEntriesWithoutManifest(
        JarFile input,
        JarOutputStream output
    )throws Exception{
        java.util.Enumeration<JarEntry>
            entries=
                input.entries();
        byte[] buffer=
            new byte[8192];

        while(entries.hasMoreElements()){
            JarEntry entry=
                entries.nextElement();

            if("META-INF/MANIFEST.MF"
                    .equalsIgnoreCase(
                        entry.getName()
                    ))
                continue;

            JarEntry copy=
                new JarEntry(
                    entry.getName()
                );
            output.putNextEntry(
                copy
            );

            if(!entry.isDirectory())
                try(java.io.InputStream in=
                        input.getInputStream(
                            entry
                        )){
                    int read;

                    while((read=
                            in.read(
                                buffer
                            ))!=-1)
                        output.write(
                            buffer,
                            0,
                            read
                        );
                }

            output.closeEntry();
        }
    }

    private static void assertJarIndexRejected(
        Path jarA,
        Path jarB
    )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "plugin-jar-index-"
            );
        Path poisoned=
            directory.resolve(
                "plugin.jar"
            );
        Path sibling=
            directory.resolve(
                "b.jar"
            );

        try{
            Files.copy(
                jarB,
                sibling,
                StandardCopyOption.REPLACE_EXISTING
            );
            writeJarWithIndex(
                jarA,
                poisoned,
                sibling.getFileName()
                    .toString()
            );

            final Path[] snapshot=
                new Path[1];
            boolean rejected=false;

            try{
                PluginJarLoader.load(
                    poisoned,
                    ENTRYPOINT,
                    Plugin.class.getClassLoader(),
                    (source,admitted)->{},
                    (source,admitted)->
                        snapshot[0]=admitted
                );
            }catch(IllegalArgumentException expected){
                rejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "JAR index is forbidden"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "Java plugin JAR Index was accepted"
                );

            assertSnapshotRetired(
                snapshot[0],
                "JAR Index rejection"
            );
        }finally{
            Files.deleteIfExists(
                poisoned
            );
            Files.deleteIfExists(
                sibling
            );
            Files.deleteIfExists(
                directory
            );
        }
    }

    private static void writeJarWithIndex(
        Path source,
        Path target,
        String siblingName
    )throws Exception{
        try(JarFile input=
                new JarFile(
                    source.toFile()
                );
            JarOutputStream output=
                new JarOutputStream(
                    Files.newOutputStream(
                        target
                    )
                )){
            java.util.Enumeration<JarEntry>
                entries=
                    input.entries();
            byte[] buffer=
                new byte[8192];

            while(entries.hasMoreElements()){
                JarEntry entry=
                    entries.nextElement();

                if("META-INF/INDEX.LIST"
                        .equals(
                            entry.getName()
                        ))
                    continue;

                JarEntry copy=
                    new JarEntry(
                        entry.getName()
                    );

                copy.setTime(
                    entry.getTime()
                );
                output.putNextEntry(
                    copy
                );

                if(!entry.isDirectory())
                    try(java.io.InputStream in=
                            input.getInputStream(
                                entry
                            )){
                        int read;

                        while((read=
                                in.read(
                                    buffer
                                ))!=-1)
                            output.write(
                                buffer,
                                0,
                                read
                            );
                    }

                output.closeEntry();
            }

            output.putNextEntry(
                new JarEntry(
                    "META-INF/INDEX.LIST"
                )
            );
            String index=
                "JarIndex-Version: 1.0\n\n"+
                siblingName+
                "\nfixture/privatepkg/\n\n";
            output.write(
                index.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            );
            output.closeEntry();
        }
    }

    private static void assertPostLoaderFailureRetiresSnapshot(
        Path jarA
    )throws Exception{
        final Path[] snapshot=
            new Path[1];
        boolean rejected=false;

        try{
            PluginJarLoader.load(
                jarA,
                "fixture.privatepkg.Version",
                Plugin.class.getClassLoader(),
                (source,admitted)->{},
                (source,admitted)->
                    snapshot[0]=admitted
            );
        }catch(IllegalArgumentException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "does not implement Plugin"
                    );
        }

        if(!rejected)
            throw new AssertionError(
                "post-loader invalid entrypoint was accepted"
            );

        assertSnapshotRetired(
            snapshot[0],
            "post-loader entrypoint rejection"
        );
    }

    private static void assertSnapshotRetired(
        Path snapshot,
        String phase
    ){
        if(snapshot==null)
            throw new AssertionError(
                phase+
                " did not expose private snapshot identity"
            );

        Path root=
            snapshot.getParent();

        if(Files.exists(
                snapshot)||
           (root!=null&&
            Files.exists(
                root)))
            throw new AssertionError(
                phase+
                " retained private snapshot/root"
            );
    }

    private static void writeJarWithManifestClasspath(
        Path source,
        Path target,
        String classPath
    )throws Exception{
        try(JarFile input=
                new JarFile(
                    source.toFile()
                )){
            Manifest manifest=
                input.getManifest()==null
                    ?new Manifest()
                    :new Manifest(
                        input.getManifest()
                    );

            Attributes attributes=
                manifest.getMainAttributes();

            if(attributes.getValue(
                    Attributes.Name.MANIFEST_VERSION)==null)
                attributes.put(
                    Attributes.Name.MANIFEST_VERSION,
                    "1.0"
                );

            attributes.put(
                Attributes.Name.CLASS_PATH,
                classPath
            );

            try(JarOutputStream output=
                    new JarOutputStream(
                        Files.newOutputStream(
                            target
                        ),
                        manifest
                    )){
                java.util.Enumeration<JarEntry>
                    entries=
                        input.entries();
                byte[] buffer=
                    new byte[8192];

                while(entries.hasMoreElements()){
                    JarEntry entry=
                        entries.nextElement();

                    if("META-INF/MANIFEST.MF"
                            .equalsIgnoreCase(
                                entry.getName()
                            ))
                        continue;

                    JarEntry copy=
                        new JarEntry(
                            entry.getName()
                        );

                    copy.setTime(
                        entry.getTime()
                    );
                    output.putNextEntry(
                        copy
                    );

                    if(!entry.isDirectory())
                        try(java.io.InputStream in=
                                input.getInputStream(
                                    entry
                                )){
                            int read;

                            while((read=
                                    in.read(
                                        buffer
                                    ))!=-1)
                                output.write(
                                    buffer,
                                    0,
                                    read
                                );
                        }

                    output.closeEntry();
                }
            }
        }
    }

    private static void assertArchiveSnapshotPinned(
        Path jarA,
        Path jarB
    )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "plugin-archive-snapshot-"
            );
        Path source=
            directory.resolve(
                "plugin.jar"
            );
        final Path[] admittedSnapshot=
            new Path[1];
        PluginJarLoader.LoadedPlugin loaded=null;

        try{
            Files.copy(
                jarA,
                source,
                StandardCopyOption.REPLACE_EXISTING
            );

            loaded=
                PluginJarLoader.load(
                    source,
                    ENTRYPOINT,
                    Plugin.class.getClassLoader(),
                    (
                        original,
                        snapshot
                    )->{
                        admittedSnapshot[0]=
                            snapshot;

                        Files.copy(
                            jarB,
                            original,
                            StandardCopyOption.REPLACE_EXISTING
                        );
                    }
                );

            if(admittedSnapshot[0]==null||
               !Files.isRegularFile(
                    admittedSnapshot[0]
                ))
                throw new AssertionError(
                    "validated private archive snapshot missing while runtime is live"
                );

            if(!source.equals(
                    loaded.source()
                ))
                throw new AssertionError(
                    "archive snapshot replaced diagnostic source identity"
                );

            PluginManifest manifest=
                loaded.manifest();

            if(!"isolation.a".equals(
                    manifest.id()))
                throw new AssertionError(
                    "post-validation pathname swap changed executed plugin expected=isolation.a actual="+
                    manifest.id()
                );

            Files.deleteIfExists(
                source
            );

            String report=
                report(
                    loaded
                );

            if(report==null||
               !report.startsWith(
                    "A|"))
                throw new AssertionError(
                    "plugin runtime stopped using admitted archive after original path deletion: "+
                    report
                );

            Path snapshot=
                loaded.snapshotPath();
            Path snapshotRoot=
                snapshot.getParent();

            loaded.close();

            if(Files.exists(
                    snapshot)||
               (snapshotRoot!=null&&
                Files.exists(
                    snapshotRoot)))
                throw new AssertionError(
                    "closed Java plugin retained private archive snapshot"
                );
        }finally{
            if(loaded!=null&&
               !loaded.closed())
                loaded.close();

            Files.deleteIfExists(
                source
            );
            Files.deleteIfExists(
                directory
            );
        }
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

            final Path[] snapshot=
                new Path[1];
            boolean rejected=false;

            try{
                PluginJarLoader.load(
                    jar,
                    "fixture.plugin.Missing",
                    Plugin.class.getClassLoader(),
                    (source,admitted)->{},
                    (source,admitted)->
                        snapshot[0]=admitted
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

            assertSnapshotRetired(
                snapshot[0],
                "reserved namespace rejection"
            );
        }finally{
            Files.deleteIfExists(
                jar
            );
        }
    }

    private static void assertExceptionalCloseReleasesRoots(
        Path jarA
    )throws Exception{
        int baselineDebt=
            PluginJarLoader
                .archiveCleanupDebtCount();

        PluginJarLoader.LoadedPlugin loaded=
            PluginJarLoader.load(
                jarA,
                ENTRYPOINT
            );
        Path snapshot=
            loaded.snapshotPath();
        Path root=
            snapshot.getParent();
        Path sentinel=
            root.resolve(
                "retirement-sentinel"
            );

        Files.write(
            sentinel,
            new byte[]{1}
        );

        Throwable observed=null;

        try{
            loaded.close();
        }catch(Throwable failure){
            observed=failure;
        }

        if(!(observed instanceof
                java.nio.file.DirectoryNotEmptyException))
            throw new AssertionError(
                "exceptional close did not surface snapshot-root retirement failure",
                observed
            );

        if(Files.exists(snapshot))
            throw new AssertionError(
                "exceptional close did not retire private archive JAR"
            );

        assertRuntimeReferencesReleased(
            loaded,
            "exceptional close"
        );

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt+1)
            throw new AssertionError(
                "exceptional close did not retain exactly one path-only cleanup debt"
            );

        Throwable retryFailure=
            PluginJarLoader
                .retryArchiveCleanupDebtOnce(
                    null
                );

        if(!(retryFailure instanceof
                java.nio.file.DirectoryNotEmptyException))
            throw new AssertionError(
                "bounded retry did not report still-blocked archive root",
                retryFailure
            );

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt+1)
            throw new AssertionError(
                "failed bounded retry dropped archive cleanup debt"
            );

        assertRuntimeReferencesReleased(
            loaded,
            "failed cleanup-debt retry"
        );

        loaded.close();

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt+1)
            throw new AssertionError(
                "duplicate runtime close changed path-only cleanup debt ownership"
            );

        Files.deleteIfExists(
            sentinel
        );

        World retryWorld=
            World.isolatedForTest(
                25L
            );
        retryWorld.close();

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt)
            throw new AssertionError(
                "World-close cleanup retry retained archive cleanup debt"
            );

        if(Files.exists(root))
            throw new AssertionError(
                "World-close cleanup retry retained archive root"
            );

        assertRuntimeReferencesReleased(
            loaded,
            "World-close cleanup-debt retry"
        );
    }

    private static void assertCaptureFailureCleanupDebt(
        Path jarA
    )throws Exception{
        int baselineDebt=
            PluginJarLoader
                .archiveCleanupDebtCount();
        IOException primary=
            new IOException(
                "fixture-capture-primary"
            );
        final Path[] root=
            new Path[1];
        final Path[] sentinel=
            new Path[1];

        Throwable observed=null;

        try{
            PluginJarLoader
                .captureArchiveForTest(
                    jarA,
                    (privateRoot,snapshot)->{
                        root[0]=privateRoot;
                        sentinel[0]=
                            privateRoot.resolve(
                                "capture-retirement-sentinel"
                            );

                        Files.write(
                            sentinel[0],
                            new byte[]{1}
                        );

                        throw primary;
                    }
                );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=primary)
            throw new AssertionError(
                "capture failure identity changed",
                observed
            );

        if(primary.getSuppressed().length!=1||
           !(primary.getSuppressed()[0] instanceof
                java.nio.file.DirectoryNotEmptyException))
            throw new AssertionError(
                "capture cleanup failure was not subordinate evidence"
            );

        if(root[0]==null||
           sentinel[0]==null||
           !Files.exists(
                root[0]
           )||
           !Files.exists(
                sentinel[0]
           ))
            throw new AssertionError(
                "capture failure fixture did not retain blocked private root"
            );

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt+1)
            throw new AssertionError(
                "capture failure did not transfer exactly one cleanup debt"
            );

        Files.deleteIfExists(
            sentinel[0]
        );

        Throwable retry=
            PluginJarLoader
                .retryArchiveCleanupDebtOnce(
                    null
                );

        if(retry!=null)
            throw new AssertionError(
                "capture-failure cleanup debt did not retire after blocker removal",
                retry
            );

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt)
            throw new AssertionError(
                "capture-failure cleanup debt remained after successful drain"
            );

        if(Files.exists(
                root[0]))
            throw new AssertionError(
                "capture-failure private root remained after successful drain"
            );
    }

    private static void assertValidationRollbackCleanupDebt(
        Path jarA
    )throws Exception{
        int baselineDebt=
            PluginJarLoader
                .archiveCleanupDebtCount();
        IOException primary=
            new IOException(
                "fixture-post-validation-primary"
            );
        final Path[] root=
            new Path[1];
        final Path[] sentinel=
            new Path[1];

        Throwable observed=null;

        try{
            PluginJarLoader.load(
                jarA,
                ENTRYPOINT,
                Plugin.class.getClassLoader(),
                (source,snapshot)->{
                    throw primary;
                },
                (source,snapshot)->{
                    root[0]=
                        snapshot.getParent();
                    sentinel[0]=
                        root[0].resolve(
                            "rollback-retirement-sentinel"
                        );

                    Files.write(
                        sentinel[0],
                        new byte[]{1}
                    );
                }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=primary)
            throw new AssertionError(
                "post-validation load failure identity changed",
                observed
            );

        if(primary.getSuppressed().length!=1||
           !(primary.getSuppressed()[0] instanceof
                java.nio.file.DirectoryNotEmptyException))
            throw new AssertionError(
                "post-validation cleanup failure was not subordinate evidence"
            );

        if(root[0]==null||
           sentinel[0]==null||
           !Files.exists(
                root[0]
           )||
           !Files.exists(
                sentinel[0]
           ))
            throw new AssertionError(
                "post-validation rollback fixture did not retain blocked private root"
            );

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt+1)
            throw new AssertionError(
                "post-validation rollback did not transfer exactly one cleanup debt"
            );

        Files.deleteIfExists(
            sentinel[0]
        );

        Throwable retry=
            PluginJarLoader
                .retryArchiveCleanupDebtOnce(
                    null
                );

        if(retry!=null)
            throw new AssertionError(
                "post-validation cleanup debt did not retire after blocker removal",
                retry
            );

        if(PluginJarLoader
                .archiveCleanupDebtCount()!=
                    baselineDebt)
            throw new AssertionError(
                "post-validation cleanup debt remained after successful drain"
            );

        if(Files.exists(
                root[0]))
            throw new AssertionError(
                "post-validation private root remained after successful drain"
            );
    }

    private static void assertLiveCloseRace(
        Path jarA
    )throws Exception{
        java.util.concurrent.CountDownLatch
            manifestEntered=
                new java.util.concurrent
                    .CountDownLatch(1);
        java.util.concurrent.CountDownLatch
            releaseManifest=
                new java.util.concurrent
                    .CountDownLatch(1);

        System.getProperties().put(
            "spawnpk.fixture.isolation.a.manifestEnteredLatch",
            manifestEntered
        );
        System.getProperties().put(
            "spawnpk.fixture.isolation.a.manifestReleaseLatch",
            releaseManifest
        );

        PluginJarLoader.LoadedPlugin loaded=
            PluginJarLoader.load(
                jarA,
                ENTRYPOINT
            );

        java.util.concurrent.atomic.AtomicReference<Throwable>
            callFailure=
                new java.util.concurrent.atomic
                    .AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable>
            closeFailure=
                new java.util.concurrent.atomic
                    .AtomicReference<>();

        Thread caller=
            new Thread(
                ()->{
                    try{
                        loaded.manifest();
                    }catch(Throwable failure){
                        callFailure.set(
                            failure
                        );
                    }
                },
                "java-plugin-live-call"
            );
        Thread closer=
            new Thread(
                ()->{
                    try{
                        loaded.close();
                    }catch(Throwable failure){
                        closeFailure.set(
                            failure
                        );
                    }
                },
                "java-plugin-close-race"
            );

        try{
            caller.start();

            if(!manifestEntered.await(
                    5L,
                    java.util.concurrent
                        .TimeUnit.SECONDS))
                throw new AssertionError(
                    "live manifest call did not enter fixture"
                );

            closer.start();

            long blockedDeadline=
                System.nanoTime()+
                java.util.concurrent.TimeUnit
                    .SECONDS.toNanos(
                        5L
                    );

            while(closer.getState()!=
                    Thread.State.BLOCKED){
                if(!closer.isAlive())
                    throw new AssertionError(
                        "close completed while live synchronized call still owned runtime"
                    );

                if(System.nanoTime()>=
                        blockedDeadline)
                    throw new AssertionError(
                        "close did not block behind live synchronized call"
                    );

                Thread.yield();
            }

            releaseManifest.countDown();

            caller.join(
                5_000L
            );
            closer.join(
                5_000L
            );

            if(caller.isAlive()||
               closer.isAlive())
                throw new AssertionError(
                    "serialized close race thread did not terminate"
                );

            if(callFailure.get()!=null)
                throw new AssertionError(
                    "pre-terminal live call failed",
                    callFailure.get()
                );

            if(closeFailure.get()!=null)
                throw new AssertionError(
                    "serialized close failed",
                    closeFailure.get()
                );

            boolean terminalDenied=false;

            try{
                loaded.manifest();
            }catch(IllegalStateException expected){
                terminalDenied=true;
            }

            if(!terminalDenied)
                throw new AssertionError(
                    "post-close call did not fail through terminal boundary"
                );

            assertRuntimeReferencesReleased(
                loaded,
                "live-close serialization"
            );
        }finally{
            releaseManifest.countDown();
            System.getProperties().remove(
                "spawnpk.fixture.isolation.a.manifestEnteredLatch"
            );
            System.getProperties().remove(
                "spawnpk.fixture.isolation.a.manifestReleaseLatch"
            );

            caller.join(
                5_000L
            );
            closer.join(
                5_000L
            );

            if(!loaded.closed())
                loaded.close();
        }
    }

    private static void assertRuntimeReferencesReleased(
        PluginJarLoader.LoadedPlugin loaded,
        String phase
    )throws Exception{
        for(String fieldName:
                new String[]{
                    "delegate",
                    "loader",
                    "snapshot"
                }){
            java.lang.reflect.Field field=
                loaded.getClass()
                    .getDeclaredField(
                        fieldName
                    );
            field.setAccessible(
                true
            );

            if(field.get(loaded)!=null)
                throw new AssertionError(
                    phase+
                    " retained runtime field "+
                    fieldName
                );
        }

        boolean manifestDenied=false;

        try{
            loaded.manifest();
        }catch(IllegalStateException expected){
            manifestDenied=true;
        }

        if(!manifestDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still exposed manifest"
            );

        boolean enableDenied=false;

        try{
            loaded.enable(
                null
            );
        }catch(IllegalStateException expected){
            enableDenied=true;
        }

        if(!enableDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still accepted enable"
            );

        boolean disableDenied=false;

        try{
            loaded.disable();
        }catch(IllegalStateException expected){
            disableDenied=true;
        }

        if(!disableDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still accepted disable"
            );

        boolean callbackDenied=false;

        try{
            loaded.callbackClassLoader();
        }catch(IllegalStateException expected){
            callbackDenied=true;
        }

        if(!callbackDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still exposed callback loader"
            );

        boolean delegateDenied=false;

        try{
            loaded.delegate();
        }catch(IllegalStateException expected){
            delegateDenied=true;
        }

        if(!delegateDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still exposed delegate"
            );

        boolean loaderDenied=false;

        try{
            loaded.classLoader();
        }catch(IllegalStateException expected){
            loaderDenied=true;
        }

        if(!loaderDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still exposed classloader"
            );

        boolean snapshotDenied=false;

        try{
            loaded.snapshotPath();
        }catch(IllegalStateException expected){
            snapshotDenied=true;
        }

        if(!snapshotDenied)
            throw new AssertionError(
                phase+
                " terminal runtime still exposed archive snapshot"
            );

        loaded.close();
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

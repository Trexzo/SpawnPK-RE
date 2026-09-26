package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManager;
import spk.plugin.api.PluginManifest;

public final class KotlinPluginDirectoryTest {
    public static final class ProbeEvent
        implements DomainEventBus.Event {}

    public static void main(
        String[] args
    )throws Exception{
        if(args.length!=3)
            throw new IllegalArgumentException(
                "expected healthy script, API JAR and Kotlin script runtime JAR"
            );

        Path fixture=
            Paths.get(args[0])
                .toAbsolutePath()
                .normalize();
        Path apiJar=
            Paths.get(args[1])
                .toAbsolutePath()
                .normalize();
        Path runtimeJar=
            Paths.get(args[2])
                .toAbsolutePath()
                .normalize();

        Path temp=
            Files.createTempDirectory(
                "kotlin-plugin-directory-"
            );
        Path root=
            temp.resolve(
                "plugins"
            ).resolve(
                "kotlin"
            );

        String oldApi=
            System.getProperty(
                KotlinPluginDirectory
                    .API_JAR_PROPERTY
            );
        String oldRuntime=
            System.getProperty(
                KotlinPluginDirectory
                    .SCRIPT_RUNTIME_PROPERTY
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
                "kotlin-directory-player"
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{4,3,2,1}
                )
            );

        boolean rootSymlinkChecked=false;

        try{
            int empty=
                KotlinPluginDirectory
                    .loadStartup(
                        world,
                        root
                    );

            if(empty!=0)
                throw new AssertionError(
                    "empty Kotlin directory loaded plugins: "+
                    empty
                );

            assertPreloadFailureCleanup(
                world,
                temp.resolve(
                    "failure-root"
                )
            );

            rootSymlinkChecked=
                assertRootSymlinkRejected(
                    world,
                    temp
                );

            System.setProperty(
                KotlinPluginDirectory
                    .API_JAR_PROPERTY,
                apiJar.toString()
            );
            System.setProperty(
                KotlinPluginDirectory
                    .SCRIPT_RUNTIME_PROPERTY,
                runtimeJar.toString()
            );

            Path startup=
                root.resolve(
                    "01-startup.kts"
                );
            Files.copy(
                fixture,
                startup
            );
            Files.write(
                root.resolve(
                    "README.txt"
                ),
                java.util.Collections
                    .singletonList(
                        "ignored"
                    ),
                StandardCharsets.UTF_8
            );

            int count=
                KotlinPluginDirectory
                    .loadStartup(
                        world,
                        root
                    );

            if(count!=1)
                throw new AssertionError(
                    "startup Kotlin plugin count mismatch: "+
                    count
                );

            assertCommandAfterEvent(
                world,
                player,
                generation,
                writer,
                "kscript",
                "KOTLIN_SCRIPT_EVENTS=1;tccl=true"
            );

            String source=
                new String(
                    Files.readAllBytes(
                        fixture
                    ),
                    StandardCharsets.UTF_8
                );
            source=
                source.replace(
                    "fixture.kotlin.script",
                    "fixture.kotlin.script.ondemand"
                ).replace(
                    "\"kscript\"",
                    "\"kscript2\""
                );

            Path onDemand=
                root.resolve(
                    "02-on-demand.kts"
                );
            Files.write(
                onDemand,
                source.getBytes(
                    StandardCharsets.UTF_8
                )
            );

            PluginHandle handle=
                KotlinPluginDirectory
                    .loadOnDemand(
                        world,
                        root,
                        onDemand
                    );

            if(handle==null||
               !handle.enabled()||
               !"fixture.kotlin.script.ondemand"
                    .equals(
                        handle.manifest()
                            .id()
                    ))
                throw new AssertionError(
                    "on-demand Kotlin plugin handle mismatch"
                );

            assertCommandAfterEvent(
                world,
                player,
                generation,
                writer,
                "kscript2",
                "KOTLIN_SCRIPT_EVENTS=1;tccl=true"
            );

            boolean outsideDenied=false;
            Path outside=
                temp.resolve(
                    "outside.kts"
                );
            Files.copy(
                fixture,
                outside
            );

            try{
                KotlinPluginDirectory
                    .loadOnDemand(
                        world,
                        root,
                        outside
                    );
            }catch(IOException expected){
                outsideDenied=true;
            }

            if(!outsideDenied)
                throw new AssertionError(
                    "on-demand Kotlin loader accepted script outside plugins/kotlin"
                );

            PluginManager manager=
                world.plugins();

            if(!manager.disable(
                    "fixture.kotlin.script.ondemand"))
                throw new AssertionError(
                    "on-demand Kotlin plugin disable failed"
                );

            if(!manager.disable(
                    "fixture.kotlin.script"))
                throw new AssertionError(
                    "startup Kotlin plugin disable failed"
                );
        }finally{
            restore(
                KotlinPluginDirectory
                    .API_JAR_PROPERTY,
                oldApi
            );
            restore(
                KotlinPluginDirectory
                    .SCRIPT_RUNTIME_PROPERTY,
                oldRuntime
            );

            world.close();

            if(Files.exists(temp))
                try(Stream<Path> paths=
                        Files.walk(temp)){
                    paths.sorted(
                            Comparator.reverseOrder()
                        )
                        .forEach(
                            path->{
                                try{
                                    Files.deleteIfExists(
                                        path
                                    );
                                }catch(IOException error){
                                    throw new RuntimeException(
                                        error
                                    );
                                }
                            }
                        );
                }
        }

        System.out.println(
            "KOTLIN_PLUGIN_DIRECTORY_PASS "+
            "emptyStartup=true "+
            "startupDiscovery=true "+
            "deterministicTopLevel=true "+
            "productionArtifactLocator=true "+
            "deterministicPreloadOrder=true "+
            "preloadFailureCleanup=true "+
            "rootSymlinkChecked="+rootSymlinkChecked+" "+
            "onDemand=true "+
            "rootConfinement=true "+
            "eventCallback=true "+
            "commandCallback=true"
        );
    }

    private static boolean assertRootSymlinkRejected(
        World world,
        Path temp
    )throws Exception{
        Path realRoot=
            temp.resolve(
                "real-kotlin-root"
            );
        Files.createDirectories(
            realRoot
        );

        Path linkRoot=
            temp.resolve(
                "linked-kotlin-root"
            );

        try{
            Files.createSymbolicLink(
                linkRoot,
                realRoot
            );
        }catch(UnsupportedOperationException|
               java.nio.file.FileSystemException|
               SecurityException unavailable){
            return false;
        }

        boolean startupRejected=false;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    linkRoot,
                    new TrackingLoader()
                );
        }catch(IOException expected){
            startupRejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "root symlink"
                    );
        }

        if(!startupRejected)
            throw new AssertionError(
                "Kotlin startup accepted symlinked plugin root"
            );

        Path realScript=
            realRoot.resolve(
                "ondemand.kts"
            );
        Files.write(
            realScript,
            java.util.Collections.singletonList(
                "// symlink root probe"
            ),
            StandardCharsets.UTF_8
        );

        boolean onDemandRejected=false;

        try{
            KotlinPluginDirectory
                .loadOnDemand(
                    world,
                    linkRoot,
                    linkRoot.resolve(
                        "ondemand.kts"
                    ),
                    new TrackingLoader()
                );
        }catch(IOException expected){
            onDemandRejected=
                expected.getMessage()!=null&&
                expected.getMessage()
                    .contains(
                        "root symlink"
                    );
        }

        if(!onDemandRejected)
            throw new AssertionError(
                "Kotlin on-demand loader accepted symlinked plugin root"
            );

        return true;
    }

    private static void assertPreloadFailureCleanup(
        World world,
        Path root
    )throws Exception{
        Files.createDirectories(
            root
        );

        Files.write(
            root.resolve(
                "b.kts"
            ),
            java.util.Collections.singletonList(
                "// b"
            ),
            StandardCharsets.UTF_8
        );
        Files.write(
            root.resolve(
                "A.kts"
            ),
            java.util.Collections.singletonList(
                "// A"
            ),
            StandardCharsets.UTF_8
        );

        Path nested=
            root.resolve(
                "nested"
            );
        Files.createDirectories(
            nested
        );
        Files.write(
            nested.resolve(
                "ignored.kts"
            ),
            java.util.Collections.singletonList(
                "// ignored"
            ),
            StandardCharsets.UTF_8
        );

        TrackingLoader loader=
            new TrackingLoader();

        boolean failed=false;

        try{
            KotlinPluginDirectory
                .loadStartup(
                    world,
                    root,
                    loader
                );
        }catch(IOException expected){
            failed=
                "fixture-preload-failure"
                    .equals(
                        expected.getMessage()
                    );
        }

        if(!failed)
            throw new AssertionError(
                "startup preload failure was not propagated"
            );

        if(loader.paths.size()!=2||
           !"A.kts".equals(
                loader.paths.get(0)
            )||
           !"b.kts".equals(
                loader.paths.get(1)
            ))
            throw new AssertionError(
                "Kotlin startup discovery order mismatch: "+
                loader.paths
            );

        if(loader.first==null||
           !loader.first.closed)
            throw new AssertionError(
                "startup preload failure retained first unowned runtime"
            );
    }

    private static final class TrackingLoader
        implements PluginLoader {

        final java.util.ArrayList<String> paths=
            new java.util.ArrayList<>();
        TrackingRuntime first;

        @Override public boolean supports(
            PluginSource source
        ){
            return source!=null&&
                !source.hasEntrypoint();
        }

        @Override public PluginRuntime load(
            PluginSource source
        )throws Exception{
            paths.add(
                source.path()
                    .getFileName()
                    .toString()
            );

            if(paths.size()==1){
                first=
                    new TrackingRuntime();
                return first;
            }

            throw new IOException(
                "fixture-preload-failure"
            );
        }
    }

    private static final class TrackingRuntime
        implements PluginRuntime {

        boolean closed;

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "fixture.kotlin.preload",
                "1.0",
                PluginApiVersion.CURRENT,
                java.util.Collections.emptyList()
            );
        }

        @Override public void enable(
            PluginContext context
        ){
        }

        @Override public void disable(){
        }

        @Override public ClassLoader callbackClassLoader(){
            if(closed)
                throw new IllegalStateException(
                    "tracking runtime closed"
                );

            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closed=true;
        }
    }

    private static void assertCommandAfterEvent(
        World world,
        WorldPlayer player,
        long generation,
        ServerPacketWriter writer,
        String command,
        String expected
    )throws Exception{
        final String[] result=
            new String[1];

        world.submitAndWait(
            player,
            generation,
            ()->{
                world.domainEvents()
                    .publish(
                        new ProbeEvent()
                    );

                ContentResult commandResult=
                    world.content()
                        .dispatchCommand(
                            player,
                            command,
                            writer
                        );

                result[0]=
                    commandResult==null
                        ?null
                        :commandResult.logText();
            },
            5_000L
        );

        if(!expected.equals(
                result[0]))
            throw new AssertionError(
                "Kotlin directory command result mismatch command="+
                command+
                " expected="+
                expected+
                " actual="+
                result[0]
            );
    }

    private static void restore(
        String key,
        String value
    ){
        if(value==null)
            System.clearProperty(
                key
            );
        else
            System.setProperty(
                key,
                value
            );
    }

    private KotlinPluginDirectoryTest(){}
}

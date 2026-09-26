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
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManager;

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
                "KOTLIN_SCRIPT_EVENTS=1"
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
                "KOTLIN_SCRIPT_EVENTS=1"
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
            "onDemand=true "+
            "rootConfinement=true "+
            "eventCallback=true "+
            "commandCallback=true"
        );
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

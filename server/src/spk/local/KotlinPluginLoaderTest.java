package spk.local;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginManager;

public final class KotlinPluginLoaderTest {
    public static final class ProbeEvent
        implements DomainEventBus.Event {}

    public static void main(
        String[] args
    )throws Exception{
        if(args.length<4)
            throw new IllegalArgumentException(
                "expected healthy script, denied script, API JAR and Kotlin compile dependencies"
            );

        Path healthy=
            Paths.get(args[0])
                .toAbsolutePath()
                .normalize();
        Path denied=
            Paths.get(args[1])
                .toAbsolutePath()
                .normalize();
        Path apiJar=
            Paths.get(args[2])
                .toAbsolutePath()
                .normalize();

        ArrayList<Path> compileClasspath=
            new ArrayList<>();

        for(int i=3;i<args.length;i++)
            compileClasspath.add(
                Paths.get(args[i])
                    .toAbsolutePath()
                    .normalize()
            );

        Class<?> loaderType=
            Class.forName(
                "spk.local.KotlinPluginLoader"
            );
        Constructor<?> constructor=
            loaderType.getConstructor(
                Path.class,
                List.class
            );
        PluginLoader loader=
            (PluginLoader)
                constructor.newInstance(
                    apiJar,
                    compileClasspath
                );

        PluginSource healthySource=
            PluginSource.script(
                healthy
            );

        if(!loader.supports(
                healthySource))
            throw new AssertionError(
                "Kotlin loader rejected .kts source"
            );

        if(loader.supports(
                PluginSource.of(
                    healthy,
                    "fixture.Entry"
                )))
            throw new AssertionError(
                "Kotlin loader accepted entrypoint-bearing source"
            );

        PluginRuntime runtime=
            loader.load(
                healthySource
            );

        if(runtime==null)
            throw new AssertionError(
                "Kotlin loader returned null runtime"
            );

        ClassLoader callbackLoader=
            runtime.callbackClassLoader();

        if(Class.forName(
                "spk.plugin.api.Plugin",
                false,
                callbackLoader
            )!=Plugin.class)
            throw new AssertionError(
                "Kotlin script lost parent plugin API identity"
            );

        assertServerInternalDenied(
            callbackLoader
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
                "kotlin-script-player"
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
            manager.enable(
                runtime
            );

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

                    ContentResult command=
                        world.content()
                            .dispatchCommand(
                                player,
                                "kscript",
                                writer
                            );

                    result[0]=
                        command==null
                            ?null
                            :command.logText();
                },
                5_000L
            );

            if(!"KOTLIN_SCRIPT_EVENTS=1"
                    .equals(
                        result[0]
                    ))
                throw new AssertionError(
                    "Kotlin script event/command result mismatch: "+
                    result[0]
                );

            if(!manager.disable(
                    "fixture.kotlin.script"))
                throw new AssertionError(
                    "Kotlin script plugin disable failed"
                );

            boolean terminal=true;

            try{
                runtime.callbackClassLoader();
                terminal=false;
            }catch(IllegalStateException expected){
            }

            if(!terminal)
                throw new AssertionError(
                    "terminal Kotlin runtime retained callback loader"
                );

            boolean deniedCompilation=false;

            try{
                loader.load(
                    PluginSource.script(
                        denied
                    )
                );
            }catch(IllegalArgumentException expected){
                String message=
                    expected.getMessage();

                deniedCompilation=
                    message!=null&&
                    (message.contains(
                        "Unresolved reference")||
                     message.contains(
                        "spk.local"));
            }

            if(!deniedCompilation)
                throw new AssertionError(
                    "Kotlin script compile boundary exposed spk.local"
                );
        }finally{
            world.close();
        }

        System.out.println(
            "KOTLIN_PLUGIN_LOADER_PASS "+
            "kts=true "+
            "apiOnlyCompile=true "+
            "serverInternalDenied=true "+
            "pluginApiIdentity=true "+
            "eventCallback=true "+
            "commandCallback=true "+
            "worldThreadLifecycle=true "+
            "terminalRuntime=true"
        );
    }

    private static void assertServerInternalDenied(
        ClassLoader loader
    )throws Exception{
        boolean denied=false;

        try{
            Class.forName(
                "spk.local.World",
                false,
                loader
            );
        }catch(ClassNotFoundException expected){
            denied=true;
        }

        if(!denied)
            throw new AssertionError(
                "Kotlin script callback loader exposed spk.local.World"
            );

        if(loader.getResource(
                "spk/local/World.class"
            )!=null)
            throw new AssertionError(
                "Kotlin script callback loader exposed spk.local.World resource"
            );
    }

    private KotlinPluginLoaderTest(){}
}

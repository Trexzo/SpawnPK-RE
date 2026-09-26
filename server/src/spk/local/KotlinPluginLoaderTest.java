package spk.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginManager;
import spk.plugin.api.PluginHandle;

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

        assertDisguisedServerDependencyRejected(
            constructor,
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

        Path failingScript=
            createEnableFailureScript();

        try{
            PluginHandle handle=
                manager.enable(
                    runtime
                );

            boolean ownedDuplicateRejected=
                false;

            try{
                manager.enable(
                    runtime
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
               !handle.enabled()||
               runtime.callbackClassLoader()!=
                    callbackLoader)
                throw new AssertionError(
                    "owned duplicate attempt terminalized live Kotlin runtime"
                );

            PluginRuntime duplicateRuntime=
                loader.load(
                    healthySource
                );

            boolean freshDuplicateRejected=
                false;

            try{
                manager.enable(
                    duplicateRuntime
                );
            }catch(IllegalStateException expected){
                freshDuplicateRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "already enabled"
                        );
            }

            if(!freshDuplicateRejected)
                throw new AssertionError(
                    "fresh duplicate Kotlin runtime was accepted"
                );

            assertRuntimeReleased(
                duplicateRuntime,
                "fresh duplicate rejection"
            );

            if(manager.plugin(
                    "fixture.kotlin.script")!=
                        handle||
               !handle.enabled())
                throw new AssertionError(
                    "fresh duplicate rejection disturbed live Kotlin runtime"
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

            assertHandleReleasedLoader(
                handle
            );
            assertRuntimeReleased(
                runtime,
                "explicit disable"
            );

            PluginRuntime failingRuntime=
                loader.load(
                    PluginSource.script(
                        failingScript
                    )
                );
            boolean enableFailureObserved=
                false;

            try{
                manager.enable(
                    failingRuntime
                );
            }catch(Exception expected){
                enableFailureObserved=
                    containsMessage(
                        expected,
                        "fixture-enable-failure"
                    );
            }

            if(!enableFailureObserved)
                throw new AssertionError(
                    "Kotlin enable failure was not propagated"
                );

            if(manager.plugin(
                    "fixture.kotlin.enablefail")!=
                        null)
                throw new AssertionError(
                    "failed Kotlin enable published plugin handle"
                );

            assertRuntimeReleased(
                failingRuntime,
                "enable failure"
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

            world.close();
            world.close();
        }finally{
            if(!world.closed())
                world.close();

            Files.deleteIfExists(
                failingScript
            );
        }

        System.out.println(
            "KOTLIN_PLUGIN_LOADER_PASS "+
            "kts=true "+
            "apiOnlyCompile=true "+
            "dependencyNamespaceFence=true "+
            "serverInternalDenied=true "+
            "pluginApiIdentity=true "+
            "eventCallback=true "+
            "commandCallback=true "+
            "worldThreadLifecycle=true "+
            "ownedDuplicateKeepsRuntime=true "+
            "freshDuplicateClosesRuntime=true "+
            "terminalHandleReleasesLoader=true "+
            "runtimeReferencesReleased=true "+
            "enableFailureClosesRuntime=true "+
            "worldCloseIdempotent=true "+
            "terminalRuntime=true"
        );
    }

    private static Path createEnableFailureScript()
        throws Exception{
        Path script=
            Files.createTempFile(
                "kotlin-plugin-enable-failure-",
                ".kts"
            );

        Files.write(
            script,
            java.util.Arrays.asList(
                "import spk.plugin.api.Plugin",
                "import spk.plugin.api.PluginApiVersion",
                "import spk.plugin.api.PluginContext",
                "import spk.plugin.api.PluginManifest",
                "",
                "object : Plugin {",
                "    override fun manifest(): PluginManifest =",
                "        PluginManifest(",
                "            \"fixture.kotlin.enablefail\",",
                "            \"1.0\",",
                "            PluginApiVersion.CURRENT,",
                "            emptyList<String>()",
                "        )",
                "",
                "    override fun enable(context: PluginContext) {",
                "        throw IllegalStateException(\"fixture-enable-failure\")",
                "    }",
                "}"
            ),
            java.nio.charset.StandardCharsets.UTF_8
        );

        return script;
    }

    private static boolean containsMessage(
        Throwable failure,
        String expected
    ){
        for(Throwable current=failure;
            current!=null;
            current=current.getCause()){
            String message=
                current.getMessage();

            if(message!=null&&
               message.contains(
                    expected
               ))
                return true;
        }

        return false;
    }

    private static void assertRuntimeReleased(
        PluginRuntime runtime,
        String phase
    )throws Exception{
        boolean terminal=true;

        try{
            runtime.callbackClassLoader();
            terminal=false;
        }catch(IllegalStateException expected){
        }

        if(!terminal)
            throw new AssertionError(
                phase+
                " retained public callback loader"
            );

        for(String fieldName:
                new String[]{
                    "delegate",
                    "callbackLoader",
                    "baseLoader"
                }){
            Field field=
                runtime.getClass()
                    .getDeclaredField(
                        fieldName
                    );
            field.setAccessible(
                true
            );

            if(field.get(runtime)!=null)
                throw new AssertionError(
                    phase+
                    " retained runtime field "+
                    fieldName
                );
        }
    }

    private static void assertHandleReleasedLoader(
        PluginHandle handle
    )throws Exception{
        Field pluginField=
            handle.getClass()
                .getDeclaredField(
                    "plugin"
                );
        pluginField.setAccessible(
            true
        );

        if(pluginField.get(handle)!=null)
            throw new AssertionError(
                "disabled Kotlin PluginHandle retained plugin instance"
            );

        Field tasksField=
            handle.getClass()
                .getDeclaredField(
                    "tasks"
                );
        tasksField.setAccessible(
            true
        );
        Object tasks=
            tasksField.get(handle);

        Field loaderField=
            tasks.getClass()
                .getDeclaredField(
                    "callbackLoader"
                );
        loaderField.setAccessible(
            true
        );

        if(loaderField.get(tasks)!=null)
            throw new AssertionError(
                "disabled Kotlin PluginHandle retained callback classloader"
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

        if(loader.getResources(
                "spk/local/World.class"
            ).hasMoreElements())
            throw new AssertionError(
                "Kotlin script callback loader exposed spk.local.World resource enumeration"
            );
    }

    private static void
        assertDisguisedServerDependencyRejected(
            Constructor<?> constructor,
            Path apiJar,
            List<Path> healthyClasspath
        )throws Exception{
        Path fake=
            Files.createTempFile(
                "kotlin-script-server-leak-",
                ".jar"
            );

        try{
            try(JarOutputStream out=
                    new JarOutputStream(
                        Files.newOutputStream(
                            fake
                        )
                    )){
                out.putNextEntry(
                    new JarEntry(
                        "spk/local/Fake.class"
                    )
                );
                out.write(
                    new byte[]{0}
                );
                out.closeEntry();
            }

            ArrayList<Path> poisoned=
                new ArrayList<>(
                    healthyClasspath
                );
            poisoned.add(fake);

            boolean rejected=false;

            try{
                constructor.newInstance(
                    apiJar,
                    poisoned
                );
            }catch(InvocationTargetException expected){
                Throwable cause=
                    expected.getCause();

                rejected=
                    cause instanceof
                        IllegalArgumentException&&
                    cause.getMessage()!=null&&
                    cause.getMessage()
                        .contains(
                            "contains SpawnPK"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "Kotlin loader accepted disguised server dependency"
                );
        }finally{
            Files.deleteIfExists(
                fake
            );
        }
    }

    private KotlinPluginLoaderTest(){}
}

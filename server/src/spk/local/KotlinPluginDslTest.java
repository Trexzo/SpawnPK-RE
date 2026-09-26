package spk.local;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import spk.content.api.ContentActionResult;
import spk.content.api.ContentInteractionResult;
import spk.content.api.ContentNpcOptionResult;
import spk.content.api.ContentProvenance;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.PluginHandle;
import spk.plugin.api.PluginManager;

public final class KotlinPluginDslTest {
    public static final class ProbeEvent
        implements DomainEventBus.Cancellable {
        private boolean cancelled;

        @Override public boolean isCancelled(){
            return cancelled;
        }

        @Override public void cancel(){
            cancelled=true;
        }
    }

    public static void main(
        String[] args
    )throws Exception{
        if(args.length<4)
            throw new IllegalArgumentException(
                "expected DSL script, denied raw-widget script, API JAR and Kotlin script runtime"
            );

        Path script=
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

        assertDslDependencyNamespaceFence(
            constructor,
            apiJar,
            compileClasspath
        );

        PluginRuntime runtime=
            loader.load(
                PluginSource.script(
                    script
                )
            );

        ClassLoader callbackLoader=
            runtime.callbackClassLoader();

        Class.forName(
            "spk.plugin.kotlin.ContentDslKt",
            false,
            callbackLoader
        );

        assertRawWidgetRuntimeDenied(
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
                "kotlin-dsl-player"
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
        int baselineListeners=
            world.domainEvents()
                .listenerCount();

        try{
            PluginHandle handle=
                manager.enable(
                    runtime
                );

            if(!handle.enabled())
                throw new AssertionError(
                    "DSL plugin not enabled"
                );

            assertBinding(
                world.content()
                    .commandBinding(
                        "dslcommand"
                    ),
                "fixture.kotlin.dsl",
                100
            );
            assertBinding(
                world.content()
                    .npcOptionBinding(
                        301,
                        2
                    ),
                "fixture.kotlin.dsl",
                100
            );
            assertBinding(
                world.content()
                    .itemOptionBinding(
                        201,
                        1
                    ),
                "fixture.kotlin.dsl",
                100
            );
            assertBinding(
                world.content()
                    .actionBinding(
                        "dsl.semantic"
                    ),
                "fixture.kotlin.dsl",
                140
            );

            if(world.domainEvents()
                    .listenerCount()!=
                baselineListeners+1)
                throw new AssertionError(
                    "DSL event subscription missing"
                );

            final ContentResult[] command=
                new ContentResult[1];
            final ContentNpcOptionResult[] npc=
                new ContentNpcOptionResult[1];
            final ContentInteractionResult[] item=
                new ContentInteractionResult[1];
            final ContentActionResult[] action=
                new ContentActionResult[1];

            world.submitAndWait(
                player,
                generation,
                ()->{
                    ProbeEvent event=
                        new ProbeEvent();
                    event.cancel();

                    world.domainEvents()
                        .publish(
                            event
                        );

                    command[0]=
                        world.content()
                            .dispatchCommand(
                                player,
                                "dslcommand one two",
                                writer
                            );
                    npc[0]=
                        world.content()
                            .dispatchNpcOption(
                                301,
                                2,
                                3200,
                                3201
                            );
                    item[0]=
                        world.content()
                            .dispatchItemOption(
                                201,
                                1
                            );
                    action[0]=
                        world.content()
                            .dispatchAction(
                                player,
                                "dsl.semantic"
                            );
                },
                5_000L
            );

            if(command[0]==null||
               !"DSL_COMMAND events=1 args=2"
                    .equals(
                        command[0]
                            .logText()
                    ))
                throw new AssertionError(
                    "DSL command mismatch: "+
                    command[0]
                );

            if(npc[0]==null||
               !npc[0].hasAction()||
               !"dsl.npc".equals(
                    npc[0]
                        .actionKey()
                ))
                throw new AssertionError(
                    "DSL NPC option mismatch: "+
                    npc[0]
                );

            if(item[0]==null||
               !"DSL_ITEM".equals(
                    item[0]
                        .outcome()
                ))
                throw new AssertionError(
                    "DSL item option mismatch: "+
                    item[0]
                );

            if(action[0]==null||
               !action[0].allowed())
                throw new AssertionError(
                    "DSL semantic action mismatch: "+
                    action[0]
                );

            if(!manager.disable(
                    "fixture.kotlin.dsl"))
                throw new AssertionError(
                    "DSL plugin disable failed"
                );

            if(world.domainEvents()
                    .listenerCount()!=
                baselineListeners)
                throw new AssertionError(
                    "DSL event subscription survived disable"
                );

            if(world.content()
                    .commandBinding(
                        "dslcommand"
                    )!=null||
               world.content()
                    .npcOptionBinding(
                        301,
                        2
                    )!=null||
               world.content()
                    .itemOptionBinding(
                        201,
                        1
                    )!=null||
               world.content()
                    .actionBinding(
                        "dsl.semantic"
                    )!=null)
                throw new AssertionError(
                    "DSL content registration survived disable"
                );

            boolean terminal=false;

            try{
                runtime.callbackClassLoader();
            }catch(IllegalStateException expected){
                terminal=true;
            }

            if(!terminal)
                throw new AssertionError(
                    "DSL Kotlin runtime retained callback loader"
                );

            boolean rawWidgetDenied=false;

            try{
                loader.load(
                    PluginSource.script(
                        denied
                    )
                );
            }catch(IllegalArgumentException expected){
                String message=
                    expected.getMessage();

                rawWidgetDenied=
                    message!=null&&
                    (message.contains(
                        "Unresolved reference")||
                     message.contains(
                        "spk.local")||
                     message.contains(
                        "WidgetActionClientRequest"));
            }

            if(!rawWidgetDenied)
                throw new AssertionError(
                    "DSL compile boundary exposed raw widget transport"
                );
        }finally{
            world.close();
        }

        System.out.println(
            "KOTLIN_PLUGIN_DSL_PASS "+
            "command=true "+
            "npcOption=true "+
            "itemOption=true "+
            "typedEvent=true "+
            "semanticAction=true "+
            "callbackTccl=true "+
            "customLocalLabProvenance=true "+
            "rawWidgetDenied=true "+
            "disableCleanup=true"
        );
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo info,
        String moduleId,
        int priority
    ){
        if(info==null)
            throw new AssertionError(
                "DSL binding missing"
            );

        if(!moduleId.equals(
                info.moduleId)||
           info.priority!=priority||
           info.provenance!=
                ContentProvenance
                    .CUSTOM_LOCALLAB)
            throw new AssertionError(
                "DSL binding metadata mismatch: "+
                info
            );
    }

    private static void
        assertRawWidgetRuntimeDenied(
            ClassLoader loader
        )throws Exception{
        boolean denied=false;

        try{
            Class.forName(
                "spk.local.WidgetActionClientRequest",
                false,
                loader
            );
        }catch(ClassNotFoundException expected){
            denied=true;
        }

        if(!denied)
            throw new AssertionError(
                "DSL callback loader exposed raw widget transport"
            );

        if(loader.getResource(
                "spk/local/WidgetActionClientRequest.class"
            )!=null)
            throw new AssertionError(
                "DSL callback loader exposed raw widget resource"
            );

        if(loader.getResources(
                "spk/local/WidgetActionClientRequest.class"
            ).hasMoreElements())
            throw new AssertionError(
                "DSL callback loader exposed raw widget resource enumeration"
            );
    }

    private static void
        assertDslDependencyNamespaceFence(
            Constructor<?> constructor,
            Path apiJar,
            List<Path> healthyClasspath
        )throws Exception{
        Path directory=
            Files.createTempDirectory(
                "kotlin-dsl-namespace-fence-"
            );
        Path fake=
            directory.resolve(
                "SpawnPKKotlinScriptRuntime.jar"
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
                        "spk/plugin/kotlin/Unexpected.class"
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
                            "outside the DSL allowlist"
                        );
            }

            if(!rejected)
                throw new AssertionError(
                    "Kotlin DSL dependency namespace fence accepted an unexpected SpawnPK class"
                );
        }finally{
            Files.deleteIfExists(
                fake
            );
            Files.deleteIfExists(
                directory
            );
        }
    }

    private KotlinPluginDslTest(){}
}

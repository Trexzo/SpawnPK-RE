package spk.local;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
        implements DomainEventBus.Event {}

    public static void main(String[] args)throws Exception{
        if(args.length!=3)
            throw new IllegalArgumentException(
                "expected DSL script, API JAR and Kotlin script runtime JAR"
            );

        Path script=Paths.get(args[0]).toAbsolutePath().normalize();
        Path apiJar=Paths.get(args[1]).toAbsolutePath().normalize();
        Path runtimeJar=Paths.get(args[2]).toAbsolutePath().normalize();

        Class<?> loaderType=Class.forName("spk.local.KotlinPluginLoader");
        Constructor<?> constructor=loaderType.getConstructor(Path.class,List.class);
        PluginLoader loader=(PluginLoader)constructor.newInstance(
            apiJar,
            Arrays.asList(runtimeJar)
        );

        PluginRuntime runtime=loader.load(PluginSource.script(script));
        ClassLoader callbackLoader=runtime.callbackClassLoader();

        if(callbackLoader.getResource(
                "spk/local/WidgetActionClientRequest.class")!=null)
            throw new AssertionError(
                "Kotlin DSL exposed raw widget transport class"
            );

        World world=World.isolatedForTest(25L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayerAndStart(
            player,
            "kotlin-dsl-player"
        );
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{7,8,9,10})
        );
        PluginManager manager=world.plugins();

        try{
            PluginHandle handle=manager.enable(runtime);

            assertBinding(world.content().commandBinding("kdsl"),"command");
            assertBinding(world.content().npcOptionBinding(12345,2),"npc");
            assertBinding(world.content().itemOptionBinding(4151,1),"item");
            assertBinding(world.content().actionBinding("dsl:action"),"action");

            AtomicReference<ContentResult> command=new AtomicReference<>();
            AtomicReference<ContentNpcOptionResult> npc=new AtomicReference<>();
            AtomicReference<ContentInteractionResult> item=new AtomicReference<>();
            AtomicReference<ContentActionResult> action=new AtomicReference<>();

            world.submitAndWait(
                player,
                generation,
                ()->{
                    try{
                        world.domainEvents().publish(new ProbeEvent());
                        command.set(
                            world.content().dispatchCommand(
                                player,"kdsl alpha beta",writer
                            )
                        );
                        npc.set(
                            world.content().dispatchNpcOption(
                                12345,2,3200,3201
                            )
                        );
                        item.set(
                            world.content().dispatchItemOption(4151,1)
                        );
                        action.set(
                            world.content().dispatchAction(
                                player,"dsl:action"
                            )
                        );
                    }catch(Exception failure){
                        throw new RuntimeException(failure);
                    }
                },
                5_000L
            );

            if(command.get()==null||
               !"DSL_COMMAND=alpha,beta;events=1;tccl=true"
                    .equals(command.get().logText()))
                throw new AssertionError(
                    "Kotlin DSL command mismatch: "+command.get()
                );

            if(npc.get()==null||
               !npc.get().hasAction()||
               !"dsl:npc".equals(npc.get().actionKey()))
                throw new AssertionError(
                    "Kotlin DSL NPC mismatch: "+npc.get()
                );

            if(item.get()==null||
               !"dsl:item".equals(item.get().outcome()))
                throw new AssertionError(
                    "Kotlin DSL item mismatch: "+item.get()
                );

            if(action.get()==null||
               action.get().allowed()||
               !"dsl:blocked".equals(action.get().reasonKey()))
                throw new AssertionError(
                    "Kotlin DSL action mismatch: "+action.get()
                );

            if(!manager.disable("fixture.kotlin.dsl")||
               handle.enabled())
                throw new AssertionError(
                    "Kotlin DSL plugin disable failed"
                );

            if(world.content().commandBinding("kdsl")!=null||
               world.content().npcOptionBinding(12345,2)!=null||
               world.content().itemOptionBinding(4151,1)!=null||
               world.content().actionBinding("dsl:action")!=null)
                throw new AssertionError(
                    "Kotlin DSL registrations survived disable"
                );

            boolean closed=false;
            try{
                runtime.callbackClassLoader();
            }catch(IllegalStateException expected){
                closed=true;
            }
            if(!closed)
                throw new AssertionError(
                    "Kotlin DSL runtime remained open after disable"
                );
        }finally{
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
            runtime.close();
        }

        System.out.println(
            "KOTLIN_PLUGIN_DSL_PASS "+
            "command=true npcOption=true itemOption=true event=true "+
            "semanticAction=true rawWidgetIds=false tccl=true "+
            "customProvenance=true disableCleanup=true jvmTarget=11"
        );
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String label
    ){
        if(binding==null||
           !"plugin:fixture.kotlin.dsl".equals(binding.moduleId)||
           binding.provenance!=ContentProvenance.CUSTOM_LOCALLAB)
            throw new AssertionError(
                "Kotlin DSL "+label+" binding mismatch: "+binding
            );
    }

    private KotlinPluginDslTest(){}
}

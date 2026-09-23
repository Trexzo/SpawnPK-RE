package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class MakeoverNpcContentActionOwnershipTest {
    public static void main(String[] args)throws Exception{
        actionResultContract();
        builtInActionStartsMakeover();
        higherPriorityOverrideSuppressesAction();
        unsupportedActionFailsClosed();

        System.out.println(
            "MAKEOVER_NPC_CONTENT_ACTION_OWNERSHIP_PASS "+
            "npc599=true "+
            "option1=true "+
            "module=locallab-core "+
            "provenance=CUSTOM_LOCALLAB "+
            "semanticAction=true "+
            "priorityOverride=true "+
            "unsupportedFailsClosed=true"
        );
    }

    private static void actionResultContract(){
        ContentNpcOptionResult action=
            ContentNpcOptionResult.action(
                "  LocalLab.Makeover-Mage  "
            );

        require(
            action.hasAction()&&
            "locallab.makeover-mage".equals(
                action.actionKey())&&
            action.service()==
                ContentNpcService.NONE,
            "semantic action normalization"
        );

        ContentNpcOptionResult handled=
            ContentNpcOptionResult.handled(
                ContentNpcService.TALK
            );

        require(
            !handled.hasAction()&&
            handled.actionKey()==null&&
            handled.service()==
                ContentNpcService.TALK,
            "ordinary semantic service result"
        );

        expect(
            IllegalArgumentException.class,
            ()->ContentNpcOptionResult.action(
                "bad action key"
            ),
            "unsafe action key"
        );
    }

    private static void builtInActionStartsMakeover()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry.BindingInfo binding=
                world.content()
                    .npcOptionBinding(
                        LocalLabCoreContentModule
                            .MAKEOVER_MAGE_NPC,
                        1
                    );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance
                        .CUSTOM_LOCALLAB,
                "built-in Make-over binding"
            );

            world.registerPlayer(
                player,
                "testprofile"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                routed(
                    world,
                    player
                );
            NpcEntity mage=
                adjacentMage(
                    player,
                    40
                );

            NpcInteractionRouter.Route generic=
                NpcInteractionRouter.resolve(
                    new NpcAction(
                        155,
                        mage.sceneIndex
                    ),
                    mage
                );

            require(
                generic.option==1&&
                generic.service==
                    NpcInteractionRouter
                        .Service.TALK,
                "generic NPC 599 route"
            );

            AtomicReference<String> result=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->result.set(
                    routed.handle(
                        new NpcAction(
                            155,
                            mage.sceneIndex
                        ),
                        mage,
                        writer(),
                        "[makeover-content-test] "
                    )
                ),
                5_000L
            );

            require(
                result.get()==null,
                "handled action emitted residual route "+
                result.get()
            );
            require(
                routed.makeoverMage()
                    .active(),
                "content action did not start Make-over"
            );

            DialogueSessionService.Snapshot snapshot=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                snapshot.active&&
                "node:intro".equals(
                    snapshot.nodeKey),
                "content action semantic dialogue state"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void
        higherPriorityOverrideSuppressesAction()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            world.content().installCustom(
                npcModule(
                    "makeover-override-talk",
                    200,
                    ContentNpcOptionResult.handled(
                        ContentNpcService.TALK
                    )
                )
            );

            ContentRegistry.BindingInfo binding=
                world.content()
                    .npcOptionBinding(
                        LocalLabCoreContentModule
                            .MAKEOVER_MAGE_NPC,
                        1
                    );

            require(
                binding!=null&&
                "makeover-override-talk".equals(
                    binding.moduleId)&&
                binding.priority==200,
                "higher-priority Make-over override"
            );

            world.registerPlayer(
                player,
                "testprofile"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                routed(
                    world,
                    player
                );
            NpcEntity mage=
                adjacentMage(
                    player,
                    41
                );

            AtomicReference<String> result=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->result.set(
                    routed.handle(
                        new NpcAction(
                            155,
                            mage.sceneIndex
                        ),
                        mage,
                        writer(),
                        "[makeover-content-test] "
                    )
                ),
                5_000L
            );

            require(
                result.get()!=null&&
                result.get().contains(
                    "DECODED_SEMANTIC_TALK"),
                "semantic TALK override result "+
                result.get()
            );
            require(
                !routed.makeoverMage()
                    .active(),
                "higher-priority TALK override still started Make-over"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void unsupportedActionFailsClosed()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            world.content().installCustom(
                npcModule(
                    "makeover-unsupported-action",
                    200,
                    ContentNpcOptionResult.action(
                        "custom.unsupported"
                    )
                )
            );

            world.registerPlayer(
                player,
                "testprofile"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                routed(
                    world,
                    player
                );
            NpcEntity mage=
                adjacentMage(
                    player,
                    42
                );

            AtomicReference<String> result=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->result.set(
                    routed.handle(
                        new NpcAction(
                            155,
                            mage.sceneIndex
                        ),
                        mage,
                        writer(),
                        "[makeover-content-test] "
                    )
                ),
                5_000L
            );

            require(
                result.get()!=null&&
                result.get().contains(
                    "REJECTED_UNSUPPORTED_CONTENT_ACTION"),
                "unsupported action did not fail closed "+
                result.get()
            );
            require(
                !routed.makeoverMage()
                    .active(),
                "unsupported action mutated Make-over state"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static ContentModule npcModule(
        String id,
        int priority,
        ContentNpcOptionResult result
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                registrar.npcOption(
                    LocalLabCoreContentModule
                        .MAKEOVER_MAGE_NPC,
                    1,
                    priority,
                    context->result
                );
            }
        };
    }

    private static LocalRoutedNpcInteractionHandler
        routed(
            World world,
            WorldPlayer player
        ){
        return new LocalRoutedNpcInteractionHandler(
            new NpcRegistry(),
            player.bank(),
            player.movement(),
            world.content(),
            player,
            player.equipment()
        );
    }

    private static NpcEntity adjacentMage(
        WorldPlayer player,
        int scene
    ){
        return new NpcEntity(
            scene,
            LocalLabCoreContentModule
                .MAKEOVER_MAGE_NPC,
            player.movement().x()+1,
            player.movement().y()
        );
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{71,72,73,74}
            )
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MakeoverNpcContentActionOwnershipTest(){}
}

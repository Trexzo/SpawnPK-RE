package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.MakeoverMageDialogueContent;

public final class MakeoverDialogueContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            AtomicReference<ContentRegistration>
                override=
                    new AtomicReference<>();

            world.content().installCustom(
                new ContentModule(){
                    @Override public String id(){
                        return "makeover-dialogue-override";
                    }

                    @Override public void register(
                        ContentRegistrar registrar
                    ){
                        override.set(
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                200,
                                context->
                                    ContentDialogueTransition
                                        .stay()
                            )
                        );
                    }
                }
            );

            ContentRegistry.BindingInfo selected=
                world.content().dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                );

            require(
                selected!=null&&
                "makeover-dialogue-override".equals(
                    selected.moduleId)&&
                selected.priority==200,
                "override binding not selected"
            );

            world.registerPlayer(
                player,
                "makeover-dialogue-content-owner"
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
                    44
                );

            world.submitAndWait(
                player,
                ()->{
                    try{
                        String residual=
                            routed.handle(
                                new NpcAction(
                                    155,
                                    mage.sceneIndex
                                ),
                                mage,
                                writer(
                                    new ByteArrayOutputStream()
                                ),
                                "[makeover-dialogue-owner] "
                            );

                        require(
                            residual==null,
                            "Make-over begin residual="+
                            residual
                        );
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                },
                5_000L
            );

            DialogueSessionService.Snapshot intro=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                intro.active&&
                MakeoverMageDialogueContent
                    .INTRO_NODE
                    .equals(intro.nodeKey)&&
                intro.revision==1L,
                "intro state before override transition"
            );

            ByteArrayOutputStream rejectedWire=
                new ByteArrayOutputStream();
            AtomicReference<Throwable> rejection=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        routed.makeoverMage()
                            .handleContinue(
                                StandardDialoguePresentationAdapter
                                    .namedNpcContinueWidget(1),
                                writer(rejectedWire),
                                "[makeover-dialogue-owner] "
                            );
                    }catch(Throwable failure){
                        rejection.set(failure);
                    }
                },
                5_000L
            );

            require(
                rejection.get() instanceof
                    IllegalStateException&&
                rejection.get().getMessage()!=null&&
                rejection.get().getMessage().contains(
                    "did not enter options"
                ),
                "invalid override did not fail closed "+
                rejection.get()
            );

            require(
                rejectedWire.size()==0,
                "invalid override emitted presentation bytes="+
                rejectedWire.size()
            );

            DialogueSessionService.Snapshot stillIntro=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                stillIntro.active&&
                MakeoverMageDialogueContent
                    .INTRO_NODE
                    .equals(stillIntro.nodeKey)&&
                stillIntro.revision==1L,
                "invalid override mutated semantic dialogue"
            );

            require(
                override.get()!=null&&
                override.get().unregister(),
                "override unregister"
            );

            ContentRegistry.BindingInfo restored=
                world.content().dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                );

            require(
                restored!=null&&
                "locallab-core".equals(
                    restored.moduleId)&&
                restored.priority==100&&
                restored.provenance==
                    ContentProvenance
                        .CUSTOM_LOCALLAB,
                "built-in Make-over dialogue policy not restored"
            );

            ByteArrayOutputStream acceptedWire=
                new ByteArrayOutputStream();
            AtomicReference<Boolean> accepted=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        ServerPacketWriter packets=
                            writer(acceptedWire);

                        accepted.set(
                            routed.makeoverMage()
                                .handleContinue(
                                    StandardDialoguePresentationAdapter
                                        .namedNpcContinueWidget(1),
                                    packets,
                                    "[makeover-dialogue-owner] "
                                )
                        );
                        packets.flush();
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                },
                5_000L
            );

            DialogueSessionService.Snapshot options=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                Boolean.TRUE.equals(
                    accepted.get())&&
                acceptedWire.size()>0&&
                options.active&&
                MakeoverMageDialogueContent
                    .OPTIONS_NODE
                    .equals(options.nodeKey)&&
                options.revision==2L,
                "restored built-in transition did not open options"
            );

            System.out.println(
                "MAKEOVER_DIALOGUE_CONTENT_OWNERSHIP_PASS "+
                "registryOwned=true "+
                "priorityOverride=true "+
                "invalidOverrideFailsClosed=true "+
                "partialWire=false "+
                "revisionPreserved=true "+
                "unregisterRestoresBuiltIn=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
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

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{81,82,83,84}
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MakeoverDialogueContentOwnershipTest(){}
}

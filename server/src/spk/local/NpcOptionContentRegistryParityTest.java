package spk.local;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.RuntimeProvenNpcInteractionModule;

public final class NpcOptionContentRegistryParityTest {
    public static void main(String[] args)throws Exception{
        testRuntimeProvenBankerParity();
        testHigherPrioritySemanticOverride();

        System.out.println(
            "NPC_OPTION_CONTENT_REGISTRY_PARITY_PASS "+
            "banker7605=true "+
            "wireParity=true "+
            "diagnosticParity=true "+
            "localRuntimeProven=true "+
            "priorityOverride=true"
        );
    }

    private static void testRuntimeProvenBankerParity()
        throws Exception{
        WorldPlayer legacyPlayer=
            new WorldPlayer();
        WorldPlayer contentPlayer=
            new WorldPlayer();

        NpcEntity legacyBanker=
            bankerFor(
                legacyPlayer.movement()
            );
        NpcEntity contentBanker=
            bankerFor(
                contentPlayer.movement()
            );

        NpcAction request=
            new NpcAction(
                155,
                legacyBanker.sceneIndex
            );

        ByteArrayOutputStream legacyWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream contentWire=
            new ByteArrayOutputStream();

        ServerPacketWriter legacyWriter=
            writer(legacyWire);
        ServerPacketWriter contentWriter=
            writer(contentWire);

        LocalRoutedNpcInteractionHandler legacy=
            new LocalRoutedNpcInteractionHandler(
                new NpcRegistry(),
                legacyPlayer.bank(),
                legacyPlayer.movement()
            );

        String expected=
            legacy.handle(
                request,
                legacyBanker,
                legacyWriter
            );

        World world=
            World.isolatedForTest(20L);

        try{
            ContentRegistry.BindingInfo binding=
                world.content()
                    .npcOptionBinding(
                        RuntimeProvenNpcInteractionModule
                            .BANKER_7605,
                        1
                    );

            if(binding==null||
               binding.provenance!=
                    ContentProvenance
                        .LOCAL_RUNTIME_PROVEN||
               !"runtime-proven-npc-interactions"
                    .equals(binding.moduleId))
                throw new AssertionError(
                    "banker provenance "+
                    binding
                );

            boolean conflict=false;
            try{
                world.content().installCustom(
                    npcModule(
                        "equal-priority-test",
                        100,
                        ContentNpcService.TALK
                    )
                );
            }catch(IllegalStateException expectedConflict){
                conflict=
                    expectedConflict.getMessage()
                        .contains(
                            "content binding conflict"
                        );
            }

            if(!conflict)
                throw new AssertionError(
                    "equal-priority NPC binding accepted"
                );

            ContentRegistry.BindingInfo afterConflict=
                world.content()
                    .npcOptionBinding(
                        RuntimeProvenNpcInteractionModule
                            .BANKER_7605,
                        1
                    );

            if(afterConflict==null||
               !"runtime-proven-npc-interactions"
                    .equals(afterConflict.moduleId))
                throw new AssertionError(
                    "failed registration changed active NPC binding "+
                    afterConflict
                );

            world.registerPlayer(
                contentPlayer,
                "testprofile"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                new LocalRoutedNpcInteractionHandler(
                    new NpcRegistry(),
                    contentPlayer.bank(),
                    contentPlayer.movement(),
                    world.content()
                );

            AtomicReference<String> actual=
                new AtomicReference<>();

            NpcAction contentRequest=
                new NpcAction(
                    155,
                    contentBanker.sceneIndex
                );

            world.submitAndWait(
                contentPlayer,
                ()->actual.set(
                    routed.handle(
                        contentRequest,
                        contentBanker,
                        contentWriter
                    )
                ),
                5_000L
            );

            if(!expected.equals(
                    actual.get()))
                throw new AssertionError(
                    "banker diagnostic changed expected="+
                    expected+
                    " actual="+actual.get()
                );

            if(!Arrays.equals(
                    legacyWire.toByteArray(),
                    contentWire.toByteArray()))
                throw new AssertionError(
                    "banker wire changed"
                );

            if(!legacyPlayer.bank().isOpen()||
               !contentPlayer.bank().isOpen())
                throw new AssertionError(
                    "banker open-state parity"
                );
        }finally{
            if(contentPlayer.registered())
                world.unregisterPlayer(
                    contentPlayer
                );
            world.close();
        }
    }

    private static void testHigherPrioritySemanticOverride()
        throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer executionPlayer=
            new WorldPlayer();

        try{
            world.content().installCustom(
                npcModule(
                    "test-npc-override",
                    200,
                    ContentNpcService.TALK
                )
            );

            ContentRegistry.BindingInfo selected=
                world.content()
                    .npcOptionBinding(
                        RuntimeProvenNpcInteractionModule
                            .BANKER_7605,
                        1
                    );

            if(selected==null||
               !"test-npc-override".equals(
                    selected.moduleId
               )||
               selected.priority!=200||
               selected.provenance!=
                    ContentProvenance
                        .CUSTOM_LOCALLAB)
                throw new AssertionError(
                    "NPC priority override not selected "+
                    selected
                );

            world.registerPlayer(
                executionPlayer,
                "testprofile"
            );
            world.start();

            WorldPlayer interactionState=
                new WorldPlayer();

            NpcEntity banker=
                bankerFor(
                    interactionState.movement()
                );

            LocalRoutedNpcInteractionHandler routed=
                new LocalRoutedNpcInteractionHandler(
                    new NpcRegistry(),
                    interactionState.bank(),
                    interactionState.movement(),
                    world.content()
                );

            AtomicReference<String> result=
                new AtomicReference<>();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            world.submitAndWait(
                executionPlayer,
                ()->result.set(
                    routed.handle(
                        new NpcAction(
                            155,
                            banker.sceneIndex
                        ),
                        banker,
                        writer(wire)
                    )
                ),
                5_000L
            );

            if(result.get()==null||
               !result.get().contains(
                    "result=DECODED_SEMANTIC_TALK"
               ))
                throw new AssertionError(
                    "NPC content override did not drive semantic route "+
                    result.get()
                );

            if(interactionState.bank()
                    .isOpen())
                throw new AssertionError(
                    "TALK override still opened bank"
                );

            if(wire.size()!=0)
                throw new AssertionError(
                    "TALK override unexpectedly emitted bank packets"
                );
        }finally{
            if(executionPlayer.registered())
                world.unregisterPlayer(
                    executionPlayer
                );
            world.close();
        }
    }

    private static ContentModule npcModule(
        String id,
        int priority,
        ContentNpcService service
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                registrar.npcOption(
                    RuntimeProvenNpcInteractionModule
                        .BANKER_7605,
                    1,
                    priority,
                    context->
                        ContentNpcOptionResult.handled(
                            service
                        )
                );
            }
        };
    }

    private static NpcEntity bankerFor(
        MovementState movement
    ){
        return new NpcEntity(
            204,
            RuntimeProvenNpcInteractionModule
                .BANKER_7605,
            movement.x()+1,
            movement.y()
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{21,22,23,24}
            )
        );
    }
}

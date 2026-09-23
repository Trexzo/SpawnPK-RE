package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.ContentProvenance;
import spk.content.builtin.RuntimeProvenNpcInteractionModule;

public final class BankerTalkToContentOwnershipTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer nativeBankPlayer=
            new WorldPlayer();
        WorldPlayer contentPlayer=
            new WorldPlayer();

        NpcEntity nativeBanker=
            bankerFor(
                nativeBankPlayer.movement()
            );
        NpcEntity contentBanker=
            bankerFor(
                contentPlayer.movement()
            );

        NpcInteractionRouter.Route genericTalk=
            NpcInteractionRouter.resolve(
                new NpcAction(
                    155,
                    contentBanker.sceneIndex
                ),
                contentBanker
            );

        if(genericTalk.option!=1||
           genericTalk.service!=
                NpcInteractionRouter.Service.TALK||
           !"EXACT_CLIENT_ACTION_SLOT".equals(
                genericTalk.authority))
            throw new AssertionError(
                "generic Talk-to still owns banker policy "+
                genericTalk
            );

        ByteArrayOutputStream nativeWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream contentWire=
            new ByteArrayOutputStream();

        ServerPacketWriter nativeWriter=
            writer(nativeWire);
        ServerPacketWriter contentWriter=
            writer(contentWire);

        LocalRoutedNpcInteractionHandler
            nativeBankHandler=
                new LocalRoutedNpcInteractionHandler(
                    new NpcRegistry(),
                    nativeBankPlayer.bank(),
                    nativeBankPlayer.movement()
                );

        String nativeResult=
            nativeBankHandler.handle(
                new NpcAction(
                    17,
                    nativeBanker.sceneIndex
                ),
                nativeBanker,
                nativeWriter
            );

        if(nativeResult==null||
           !nativeResult.contains(
                "V511_BANK_OPEN_NPC npc=7605")||
           !nativeBankPlayer.bank().isOpen())
            throw new AssertionError(
                "native option-3 bank oracle failed "+
                nativeResult
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
               !"runtime-proven-npc-interactions"
                    .equals(binding.moduleId)||
               binding.priority!=100||
               binding.provenance!=
                    ContentProvenance
                        .LOCAL_RUNTIME_PROVEN)
                throw new AssertionError(
                    "banker Talk-to content binding "+
                    binding
                );

            world.registerPlayer(
                contentPlayer,
                "testprofile"
            );
            world.start();

            LocalRoutedNpcInteractionHandler
                contentHandler=
                    new LocalRoutedNpcInteractionHandler(
                        new NpcRegistry(),
                        contentPlayer.bank(),
                        contentPlayer.movement(),
                        world.content()
                    );

            AtomicReference<String> result=
                new AtomicReference<>();

            world.submitAndWait(
                contentPlayer,
                ()->result.set(
                    contentHandler.handle(
                        new NpcAction(
                            155,
                            contentBanker.sceneIndex
                        ),
                        contentBanker,
                        contentWriter
                    )
                ),
                5_000L
            );

            String actual=result.get();

            if(actual==null||
               !actual.contains(
                    "V511_BANK_OPEN_NPC npc=7605")||
               !actual.contains(
                    "action=OPENED_ADJACENT_IMMEDIATE"))
                throw new AssertionError(
                    "content-owned Talk-to did not open bank "+
                    actual
                );

            if(!contentPlayer.bank().isOpen())
                throw new AssertionError(
                    "content-owned Talk-to left bank closed"
                );

            if(!Arrays.equals(
                    nativeWire.toByteArray(),
                    contentWire.toByteArray()))
                throw new AssertionError(
                    "content-owned Talk-to bank wire differs from native Bank option"
                );

            System.out.println(
                "BANKER_TALK_TO_CONTENT_OWNERSHIP_PASS "+
                "genericTalkService=TALK "+
                "contentService=BANK "+
                "module=runtime-proven-npc-interactions "+
                "provenance=LOCAL_RUNTIME_PROVEN "+
                "bankWireParity=true"
            );
        }finally{
            if(contentPlayer.registered())
                world.unregisterPlayer(
                    contentPlayer
                );
            world.close();
        }
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
                new int[]{31,32,33,34}
            )
        );
    }

    private BankerTalkToContentOwnershipTest(){}
}

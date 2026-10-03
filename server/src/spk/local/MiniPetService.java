package spk.local;

import java.io.IOException;

/** Configured mini-pet lifecycle. Selection is persistent; actor exists only while main pet exists. */
final class MiniPetService {
    static final class PreparedConfigure {
        final MiniPetDefinitionRepository.Def definition;
        final NpcEntity expectedMainPet;
        final NpcRegistry.PreparedMiniPetReplacement actorReplacement;
        final boolean expectedConfigured;
        final int expectedMiniItem;

        PreparedConfigure(
            MiniPetDefinitionRepository.Def definition,
            NpcEntity expectedMainPet,
            NpcRegistry.PreparedMiniPetReplacement actorReplacement,
            boolean expectedConfigured,
            int expectedMiniItem
        ){
            this.definition=definition;
            this.expectedMainPet=expectedMainPet;
            this.actorReplacement=actorReplacement;
            this.expectedConfigured=expectedConfigured;
            this.expectedMiniItem=expectedMiniItem;
        }

        boolean rejectedNoScene(){
            return expectedMainPet!=null&&actorReplacement==null;
        }
    }

    static final class PreparedOff {
        final boolean expectedConfigured;
        final int expectedMiniItem;
        final NpcRegistry.PreparedMiniPetRemoval actorRemoval;

        PreparedOff(
            boolean expectedConfigured,
            int expectedMiniItem,
            NpcRegistry.PreparedMiniPetRemoval actorRemoval
        ){
            this.expectedConfigured=expectedConfigured;
            this.expectedMiniItem=expectedMiniItem;
            this.actorRemoval=actorRemoval;
        }
    }

    PreparedConfigure prepareConfigure(
        int itemId,
        PetState state,
        NpcRegistry npcs,
        MovementState movement
    ){
        MiniPetDefinitionRepository.Def d=
            MiniPetDefinitionRepository.get(itemId);

        if(d==null)
            return null;

        NpcEntity main=
            npcs.pet();

        NpcRegistry.PreparedMiniPetReplacement replacement=
            main==null
                ?null
                :npcs.prepareMiniPetReplacement(
                    d,
                    movement
                );

        return new PreparedConfigure(
            d,
            main,
            replacement,
            state.miniConfigured(),
            state.miniItemId()
        );
    }

    String publishPreparedConfigure(
        PreparedConfigure prepared,
        NpcRegistry npcs,
        MovementState movement,
        ServerPacketWriter w
    )throws IOException{
        if(prepared==null)
            return "REJECTED_NOT_MINI_PET";

        if(prepared.rejectedNoScene())
            return "MINIPET_CONFIGURED_REJECTED_NO_FREE_SCENE_INDEX item="+
                prepared.definition.itemId;

        if(prepared.expectedMainPet==null)
            return "MINIPET_CONFIGURED item="+
                prepared.definition.itemId+
                " npc="+prepared.definition.npcId+
                " name="+prepared.definition.name+
                " authority="+prepared.definition.authority+
                " actor=MAIN_PET_NOT_OUT_SELECTION_PERSISTED";

        return npcs.publishMiniPetReplacement(
            prepared.actorReplacement,
            movement,
            w
        );
    }

    String commitPreparedConfigure(
        PreparedConfigure prepared,
        PetState state,
        NpcRegistry npcs,
        ServerPacketWriter sourceWriter,
        String actorResult
    ){
        if(prepared==null)
            return actorResult==null
                ?"REJECTED_NOT_MINI_PET"
                :actorResult;

        if(state.miniConfigured()!=prepared.expectedConfigured||
           state.miniItemId()!=prepared.expectedMiniItem)
            throw new IllegalStateException(
                "mini selection changed before prepared configure commit"
            );

        if(prepared.rejectedNoScene())
            return actorResult;

        if(npcs.pet()!=prepared.expectedMainPet)
            throw new IllegalStateException(
                "main pet changed before prepared mini configure commit"
            );

        if(prepared.expectedMainPet!=null){
            npcs.commitMiniPetReplacement(
                prepared.actorReplacement
            );
        }

        state.configureMini(
            prepared.definition.itemId
        );

        if(prepared.expectedMainPet!=null){
            npcs.relayCommittedMiniPetInteractionTarget(
                prepared.actorReplacement,
                sourceWriter
            );
        }

        return "MINIPET_CONFIGURED item="+
            prepared.definition.itemId+
            " npc="+prepared.definition.npcId+
            " name="+prepared.definition.name+
            " authority="+prepared.definition.authority+
            " actor="+
            actorResult;
    }

    PreparedOff prepareOff(
        PetState state,
        NpcRegistry npcs
    ){
        return new PreparedOff(
            state.miniConfigured(),
            state.miniItemId(),
            npcs.prepareMiniPetRemoval()
        );
    }

    String publishPreparedOff(
        PreparedOff prepared,
        NpcRegistry npcs,
        ServerPacketWriter w
    )throws IOException{
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        return npcs.publishMiniPetRemoval(
            prepared.actorRemoval,
            w
        );
    }

    String commitPreparedOff(
        PreparedOff prepared,
        PetState state,
        NpcRegistry npcs,
        String actorResult
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        if(state.miniConfigured()!=prepared.expectedConfigured||
           state.miniItemId()!=prepared.expectedMiniItem)
            throw new IllegalStateException(
                "mini selection changed before prepared disable commit"
            );

        npcs.commitMiniPetRemoval(
            prepared.actorRemoval
        );
        state.clearMini();

        return "MINIPET_DISABLED oldItem="+
            prepared.expectedMiniItem+
            " actor="+actorResult;
    }

    String configure(int itemId,PetState state,NpcRegistry npcs,MovementState movement,ServerPacketWriter w)throws IOException{
        PreparedConfigure prepared=
            prepareConfigure(
                itemId,
                state,
                npcs,
                movement
            );

        if(prepared==null)
            return "REJECTED_NOT_MINI_PET item="+itemId;

        w.beginBatch();
        boolean ended=false;
        String actor;

        try{
            actor=
                publishPreparedConfigure(
                    prepared,
                    npcs,
                    movement,
                    w
                );
            w.endBatch();
            ended=true;
        }catch(IOException failure){
            abortBatchAfterFailure(
                w,
                ended,
                failure
            );
            throw failure;
        }catch(RuntimeException failure){
            abortBatchAfterFailure(
                w,
                ended,
                failure
            );
            throw failure;
        }catch(Error failure){
            abortBatchAfterFailure(
                w,
                ended,
                failure
            );
            throw failure;
        }

        return commitPreparedConfigure(
            prepared,
            state,
            npcs,
            w,
            actor
        );
    }

    String off(PetState state,NpcRegistry npcs,ServerPacketWriter w)throws IOException{
        PreparedOff prepared=
            prepareOff(
                state,
                npcs
            );

        w.beginBatch();
        boolean ended=false;
        String actor;

        try{
            actor=
                publishPreparedOff(
                    prepared,
                    npcs,
                    w
                );
            w.endBatch();
            ended=true;
        }catch(IOException failure){
            abortBatchAfterFailure(
                w,
                ended,
                failure
            );
            throw failure;
        }catch(RuntimeException failure){
            abortBatchAfterFailure(
                w,
                ended,
                failure
            );
            throw failure;
        }catch(Error failure){
            abortBatchAfterFailure(
                w,
                ended,
                failure
            );
            throw failure;
        }

        return commitPreparedOff(
            prepared,
            state,
            npcs,
            actor
        );
    }

    private static void abortBatchAfterFailure(
        ServerPacketWriter writer,
        boolean ended,
        Throwable primary
    ){
        if(ended)
            return;

        try{
            writer.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }
    }

    String onMainPetSpawn(PetState state,NpcRegistry npcs,MovementState movement,ServerPacketWriter w)throws IOException{
        if(!state.miniConfigured())return "MINIPET_NONE_CONFIGURED"; MiniPetDefinitionRepository.Def d=MiniPetDefinitionRepository.get(state.miniItemId());
        if(d==null){state.clearMini();return "MINIPET_STALE_SELECTION_CLEARED";} return npcs.spawnOrReplaceMiniPet(d,movement,w);
    }

    String status(PetState state,NpcRegistry npcs){
        MiniPetDefinitionRepository.Def d=state.miniConfigured()?MiniPetDefinitionRepository.get(state.miniItemId()):null; NpcEntity a=npcs.miniPet();
        return "MINIPET_STATUS configured="+state.miniConfigured()+" item="+(d==null?-1:d.itemId)+" npc="+(d==null?-1:d.npcId)+" actorScene="+(a==null?-1:a.sceneIndex)+" mainPetScene="+(npcs.pet()==null?-1:npcs.pet().sceneIndex);
    }
}

package spk.local;

import java.io.IOException;

/** Configured mini-pet lifecycle. Selection is persistent; actor exists only while main pet exists. */
final class MiniPetService {
    static final class PreparedConfigure {
        final MiniPetDefinitionRepository.Def definition;
        final NpcRegistry.PreparedMiniPetReplacement replacement;

        PreparedConfigure(
            MiniPetDefinitionRepository.Def definition,
            NpcRegistry.PreparedMiniPetReplacement replacement
        ){
            this.definition=definition;
            this.replacement=replacement;
        }

        boolean selectionOnly(){
            return replacement==null;
        }
    }

    static final class PreparedDisable {
        final int oldItem;
        final NpcRegistry.PreparedMiniPetRemoval removal;

        PreparedDisable(
            int oldItem,
            NpcRegistry.PreparedMiniPetRemoval removal
        ){
            this.oldItem=oldItem;
            this.removal=removal;
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

        if(npcs.pet()==null)
            return new PreparedConfigure(
                d,
                null
            );

        NpcRegistry.PreparedMiniPetReplacement replacement=
            npcs.prepareMiniPetReplacement(
                d,
                movement
            );

        if(replacement==null)
            return new PreparedConfigure(
                d,
                null
            );

        return new PreparedConfigure(
            d,
            replacement
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

        if(prepared.replacement==null){
            if(npcs.pet()!=null)
                return "MINIPET_CONFIGURED_REJECTED_NO_FREE_SCENE_INDEX item="+
                    prepared.definition.itemId;

            return "MINIPET_CONFIGURED item="+
                prepared.definition.itemId+
                " npc="+prepared.definition.npcId+
                " name="+prepared.definition.name+
                " authority="+prepared.definition.authority+
                " actor=MAIN_PET_NOT_OUT_SELECTION_PERSISTED";
        }

        return npcs.publishMiniPetReplacement(
            prepared.replacement,
            movement,
            w
        );
    }

    void commitPreparedConfigure(
        PreparedConfigure prepared,
        PetState state,
        NpcRegistry npcs,
        ServerPacketWriter sourceWriter
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        if(prepared.replacement!=null)
            npcs.commitMiniPetReplacement(
                prepared.replacement
            );

        state.configureMini(
            prepared.definition.itemId
        );

        if(prepared.replacement!=null)
            npcs.relayCommittedMiniPetInteractionTarget(
                prepared.replacement,
                sourceWriter
            );
    }

    PreparedDisable prepareDisable(
        PetState state,
        NpcRegistry npcs
    ){
        return new PreparedDisable(
            state.miniItemId(),
            npcs.prepareMiniPetRemoval()
        );
    }

    String publishPreparedDisable(
        PreparedDisable prepared,
        NpcRegistry npcs,
        ServerPacketWriter w
    )throws IOException{
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        return npcs.publishMiniPetRemoval(
            prepared.removal,
            w
        );
    }

    void commitPreparedDisable(
        PreparedDisable prepared,
        PetState state,
        NpcRegistry npcs
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        npcs.commitMiniPetRemoval(
            prepared.removal
        );
        state.clearMini();
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

        if(prepared.replacement==null&&npcs.pet()!=null)
            return "MINIPET_CONFIGURED_REJECTED_NO_FREE_SCENE_INDEX item="+itemId;

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
            if(!ended)
                try{w.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{w.endBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{w.endBatch();}catch(Throwable ignored){}
            throw failure;
        }

        commitPreparedConfigure(
            prepared,
            state,
            npcs,
            w
        );

        return "MINIPET_CONFIGURED item="+itemId+
            " npc="+prepared.definition.npcId+
            " name="+prepared.definition.name+
            " authority="+prepared.definition.authority+
            " actor="+actor;
    }

    String off(PetState state,NpcRegistry npcs,ServerPacketWriter w)throws IOException{
        PreparedDisable prepared=
            prepareDisable(
                state,
                npcs
            );

        String actor=
            publishPreparedDisable(
                prepared,
                npcs,
                w
            );

        commitPreparedDisable(
            prepared,
            state,
            npcs
        );

        return "MINIPET_DISABLED oldItem="+
            prepared.oldItem+
            " actor="+actor;
    }

    String status(PetState state,NpcRegistry npcs){
        MiniPetDefinitionRepository.Def d=state.miniConfigured()?MiniPetDefinitionRepository.get(state.miniItemId()):null; NpcEntity a=npcs.miniPet();
        return "MINIPET_STATUS configured="+state.miniConfigured()+" item="+(d==null?-1:d.itemId)+" npc="+(d==null?-1:d.npcId)+" actorScene="+(a==null?-1:a.sceneIndex)+" mainPetScene="+(npcs.pet()==null?-1:npcs.pet().sceneIndex);
    }
}

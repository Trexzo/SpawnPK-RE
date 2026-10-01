package spk.local;

import java.io.IOException;

/** Configured mini-pet lifecycle. Selection is persistent; actor exists only while main pet exists. */
final class MiniPetService {
    String configure(int itemId,PetState state,NpcRegistry npcs,MovementState movement,ServerPacketWriter w)throws IOException{
        MiniPetDefinitionRepository.Def d=
            MiniPetDefinitionRepository.get(itemId);

        if(d==null)
            return "REJECTED_NOT_MINI_PET item="+itemId;

        if(npcs.pet()==null){
            state.configureMini(itemId);
            return "MINIPET_CONFIGURED item="+itemId+
                " npc="+d.npcId+
                " name="+d.name+
                " authority="+d.authority+
                " actor=MAIN_PET_NOT_OUT_SELECTION_PERSISTED";
        }

        NpcRegistry.PreparedMiniPetReplacement prepared=
            npcs.prepareMiniPetReplacement(
                d,
                movement
            );

        if(prepared==null)
            return "MINIPET_CONFIGURED_REJECTED_NO_FREE_SCENE_INDEX item="+
                itemId;

        w.beginBatch();
        boolean ended=false;
        String actor;

        try{
            actor=
                npcs.publishMiniPetReplacement(
                    prepared,
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

        npcs.commitMiniPetReplacement(
            prepared
        );
        state.configureMini(itemId);

        return "MINIPET_CONFIGURED item="+itemId+
            " npc="+d.npcId+
            " name="+d.name+
            " authority="+d.authority+
            " actor="+actor;
    }
    String off(PetState state,NpcRegistry npcs,ServerPacketWriter w)throws IOException{
        int old=state.miniItemId(); String actor=npcs.removeMiniPet(w); state.clearMini(); return "MINIPET_DISABLED oldItem="+old+" actor="+actor;
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

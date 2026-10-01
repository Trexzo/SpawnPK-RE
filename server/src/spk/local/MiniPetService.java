package spk.local;

import java.io.IOException;

/** Configured mini-pet lifecycle. Selection is persistent; actor exists only while main pet exists. */
final class MiniPetService {
    static final class PreparedConfigure {
        final MiniPetDefinitionRepository.Def definition;
        final boolean selectionOnly;
        final NpcRegistry.PreparedMiniPetReplacement actor;

        PreparedConfigure(
            MiniPetDefinitionRepository.Def definition,
            boolean selectionOnly,
            NpcRegistry.PreparedMiniPetReplacement actor
        ){
            this.definition=definition;
            this.selectionOnly=selectionOnly;
            this.actor=actor;
        }
    }

    static final class PreparedOff {
        final int oldItem;
        final NpcRegistry.PreparedMiniPetRemoval actor;

        PreparedOff(
            int oldItem,
            NpcRegistry.PreparedMiniPetRemoval actor
        ){
            this.oldItem=oldItem;
            this.actor=actor;
        }
    }

    PreparedConfigure prepareConfigure(
        int itemId,
        PetState state,
        NpcRegistry npcs,
        MovementState movement
    ){
        MiniPetDefinitionRepository.Def definition=
            MiniPetDefinitionRepository.get(itemId);

        if(definition==null)
            return null;

        if(npcs.pet()==null)
            return new PreparedConfigure(
                definition,
                true,
                null
            );

        return new PreparedConfigure(
            definition,
            false,
            npcs.prepareMiniPetReplacement(
                definition,
                movement
            )
        );
    }

    String publishConfigure(
        PreparedConfigure prepared,
        MovementState movement,
        NpcRegistry npcs,
        ServerPacketWriter writer
    )throws IOException{
        if(prepared==null)
            return "REJECTED_NOT_MINI_PET";

        if(prepared.selectionOnly)
            return "MAIN_PET_NOT_OUT_SELECTION_PERSISTED";

        if(prepared.actor==null)
            return "REJECTED_NO_FREE_SCENE_INDEX";

        return npcs.publishMiniPetReplacement(
            prepared.actor,
            movement,
            writer
        );
    }

    void commitConfigure(
        PreparedConfigure prepared,
        PetState state,
        NpcRegistry npcs
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        if(!prepared.selectionOnly){
            if(prepared.actor==null)
                throw new IllegalStateException(
                    "cannot commit rejected mini configure"
                );

            npcs.commitMiniPetReplacement(
                prepared.actor
            );
        }

        state.configureMini(
            prepared.definition.itemId
        );
    }

    PreparedOff prepareOff(
        PetState state,
        NpcRegistry npcs
    ){
        return new PreparedOff(
            state.miniItemId(),
            npcs.prepareMiniPetRemoval()
        );
    }

    String publishOff(
        PreparedOff prepared,
        NpcRegistry npcs,
        ServerPacketWriter writer
    )throws IOException{
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        return npcs.publishMiniPetRemoval(
            prepared.actor,
            writer
        );
    }

    void commitOff(
        PreparedOff prepared,
        PetState state,
        NpcRegistry npcs
    ){
        if(prepared==null)
            throw new NullPointerException(
                "prepared"
            );

        npcs.commitMiniPetRemoval(
            prepared.actor
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

        if(!prepared.selectionOnly&&
           prepared.actor==null)
            return "MINIPET_CONFIGURED_REJECTED_NO_FREE_SCENE_INDEX item="+
                itemId;

        String actor;

        if(prepared.selectionOnly){
            actor=
                publishConfigure(
                    prepared,
                    movement,
                    npcs,
                    w
                );
        }else{
            w.beginBatch();
            boolean ended=false;

            try{
                actor=
                    publishConfigure(
                        prepared,
                        movement,
                        npcs,
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
        }

        commitConfigure(
            prepared,
            state,
            npcs
        );

        MiniPetDefinitionRepository.Def d=
            prepared.definition;

        return "MINIPET_CONFIGURED item="+itemId+
            " npc="+d.npcId+
            " name="+d.name+
            " authority="+d.authority+
            " actor="+actor;
    }

    String off(PetState state,NpcRegistry npcs,ServerPacketWriter w)throws IOException{
        PreparedOff prepared=
            prepareOff(
                state,
                npcs
            );
        String actor=
            publishOff(
                prepared,
                npcs,
                w
            );
        commitOff(
            prepared,
            state,
            npcs
        );
        return "MINIPET_DISABLED oldItem="+
            prepared.oldItem+
            " actor="+actor;
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

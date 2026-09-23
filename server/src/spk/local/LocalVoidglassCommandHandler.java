package spk.local;

import java.io.IOException;
import java.util.Locale;

/**
 * LocalLab Voidglass custom-content command coordinator.
 *
 * R3 and legacy R1 remain explicitly LocalLab custom/prototype authority. This
 * handler centralizes their command/presentation orchestration without treating
 * any custom behavior as recovered SpawnPK server behavior.
 */
final class LocalVoidglassCommandHandler {
    static final class Outcome {
        final String text;
        final String saveReason;

        Outcome(String text,String saveReason){
            this.text=text;
            this.saveReason=saveReason;
        }

        static Outcome noSave(String text){
            return new Outcome(text,null);
        }
    }

    private final BankState bank;
    private final PetState petState;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final DevAuthorityWorkbench dev;
    private final VoidglassPetState voidglass;

    LocalVoidglassCommandHandler(
        BankState bank,
        PetState petState,
        NpcRegistry npcs,
        MovementState movement,
        DevAuthorityWorkbench dev,
        VoidglassPetState voidglass
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.voidglass=java.util.Objects.requireNonNull(
            voidglass,"voidglass");
    }

    Outcome handle(
        String[] p,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length<1)return null;

        if(p[0].equalsIgnoreCase("voidglass3")||
           p[0].equalsIgnoreCase("voidglass2")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"status";

            if(sub.equals("give")||sub.equals("item")){
                Outcome result=giveR3(serverPackets);
                return new Outcome(
                    "CUSTOM_PET_R3_VOIDGLASS "+result.text,
                    result.saveReason);
            }

            if(sub.equals("candidate")||sub.equals("c")){
                int index=p.length>=3?parseInt(p[2],-1):-1;
                return Outcome.noSave(
                    "CUSTOM_PET_R3_VOIDGLASS "+
                    selectR3Candidate(index,serverPackets));
            }

            if(sub.equals("next")){
                return Outcome.noSave(
                    "CUSTOM_PET_R3_VOIDGLASS "+
                    cycleR3Candidate(serverPackets));
            }

            if(sub.equals("proc")){
                return Outcome.noSave(
                    "CUSTOM_PET_R3_VOIDGLASS "+
                    triggerR3Proc(serverPackets));
            }

            if(sub.equals("status")||sub.equals("info")){
                return Outcome.noSave(
                    "CUSTOM_PET_R3_VOIDGLASS "+
                    statusR3());
            }

            if(sub.equals("reset")){
                if(!VoidglassR3CustomContent.active(
                    petState,npcs.pet())){
                    return Outcome.noSave(
                        "CUSTOM_PET_R3_VOIDGLASS_RESET result=REJECTED_ACTIVE_PET_NOT_VOIDGLASS_R3");
                }

                String candidate=npcs.previewPetDefinition(
                    VoidglassR3CustomContent.DEFAULT_NPC_ID,
                    movement,
                    serverPackets);
                String nativeState=npcs.setPetNativeState(
                    0,serverPackets);
                String particles=npcs.devSetParticleSelector(
                    null,movement,serverPackets);

                return Outcome.noSave(
                    "CUSTOM_PET_R3_VOIDGLASS_RESET candidate={"+
                    candidate+
                    "} native={"+
                    nativeState+
                    "} particles={"+
                    particles+
                    "}");
            }

            return Outcome.noSave(
                "CUSTOM_PET_R3_VOIDGLASS_HELP usage=::voidglass3 give|candidate <1>|next|proc|status|reset item=29999 note=voidglass2_alias_migrated_to_R3 nativeCompositorRetired=true customAuthority=LOCAL_DEV_ONLY");
        }

        if(p[0].equalsIgnoreCase("voidglass")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"status";
            NpcEntity activePet=npcs.pet();

            if(sub.equals("help")){
                return Outcome.noSave(
                    "CUSTOM_PET_R1_VOIDGLASS_HELP usage=::item 22960 -> Drop -> ::voidglass on | fx <6|8|auto> | proc | status | off note=R1_reuses_Vasa_client_definition_session_only");
            }

            if(sub.equals("on")||sub.equals("enable")){
                if(voidglass.active()){
                    return Outcome.noSave(
                        "CUSTOM_PET_R1_VOIDGLASS result=ALREADY_ACTIVE "+
                        voidglass.summary(
                            dev.petParticleSelector()));
                }

                if(!VoidglassPetProfile.matches(
                    petState,activePet)){
                    return Outcome.noSave(
                        "CUSTOM_PET_R1_VOIDGLASS result=REJECTED_NEED_BASE_VASA expected="+
                        VoidglassPetProfile.BASE_ITEM_ID+
                        "->"+
                        VoidglassPetProfile.BASE_NPC_ID+
                        " active="+
                        (petState.active()
                            ?petState.itemId()+"->"+petState.npcId()
                            :"none")+
                        " instructions=::item_22960_then_Drop");
                }

                Integer previous=dev.petParticleSelector();
                voidglass.activate(previous);

                String fx=npcs.devSetParticleSelector(
                    VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR,
                    movement,
                    serverPackets);
                String text=npcs.forcePetText(
                    "VOIDGLASS",serverPackets);

                return Outcome.noSave(
                    "CUSTOM_PET_R1_VOIDGLASS result=ENABLED content="+
                    VoidglassPetProfile.DISPLAY_NAME+
                    " baseItem="+VoidglassPetProfile.BASE_ITEM_ID+
                    " baseNpc="+VoidglassPetProfile.BASE_NPC_ID+
                    " model="+VoidglassPetProfile.WORLD_MODEL_ID+
                    " stand="+VoidglassPetProfile.STAND_ANIM+
                    " walkTurn="+VoidglassPetProfile.WALK_TURN_ANIM+
                    " fx="+fx+
                    " identityText="+text+
                    " "+
                    voidglass.summary(
                        dev.petParticleSelector()));
            }

            if(sub.equals("off")||sub.equals("disable")){
                if(!voidglass.active()){
                    return Outcome.noSave(
                        "CUSTOM_PET_R1_VOIDGLASS result=ALREADY_OFF");
                }

                Integer restore=
                    voidglass.clearAndRestoreSelector();
                String fx=npcs.devSetParticleSelector(
                    restore,movement,serverPackets);

                return Outcome.noSave(
                    "CUSTOM_PET_R1_VOIDGLASS result=DISABLED restoredFx="+
                    (restore==null?"AUTO":restore)+
                    " transport="+fx);
            }

            if(sub.equals("fx")){
                if(!voidglass.active()||
                   !VoidglassPetProfile.matches(
                       petState,activePet)){
                    return Outcome.noSave(
                        "CUSTOM_PET_R1_VOIDGLASS_FX result=REJECTED_NOT_ACTIVE");
                }

                String value=
                    p.length>=3
                        ?p[2].toLowerCase(Locale.ROOT)
                        :"auto";
                int selector=
                    value.equals("auto")||
                    value.equals("reset")
                        ?VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR
                        :parseInt(value,-1);

                if(!VoidglassPetProfile.allowedSelector(selector)){
                    return Outcome.noSave(
                        "CUSTOM_PET_R1_VOIDGLASS_FX result=REJECTED selector=6_or_8_or_auto");
                }

                voidglass.selectParticle(selector);
                String fx=npcs.devSetParticleSelector(
                    selector,movement,serverPackets);

                return Outcome.noSave(
                    "CUSTOM_PET_R1_VOIDGLASS_FX result=OK selector="+
                    selector+
                    " transport="+fx+
                    " visual="+
                    (selector==6
                        ?"MAGENTA"
                        :"CYAN_PINK_ALTERNATING"));
            }

            if(sub.equals("proc")){
                if(!voidglass.active()||
                   !VoidglassPetProfile.matches(
                       petState,activePet)){
                    return Outcome.noSave(
                        "CUSTOM_PET_R1_VOIDGLASS_PROC result=REJECTED_NOT_ACTIVE");
                }

                voidglass.recordProc();
                String text=npcs.forcePetText(
                    VoidglassPetProfile.PROC_TEXT,
                    serverPackets);

                serverPackets.varShort(
                    81,
                    CombatSync.player81GfxOnly(
                        VoidglassPetProfile.OWNER_PROC_GFX,
                        0,
                        0));

                return Outcome.noSave(
                    "CUSTOM_PET_R1_VOIDGLASS_PROC result=OK text="+
                    text+
                    " ownerAnim=NONE ownerGfx="+
                    VoidglassPetProfile.OWNER_PROC_GFX+
                    " procCount="+voidglass.procCount()+
                    " mechanic=PRESENTATION_ONLY_R1 combatAccuracyHook=DEFERRED");
            }

            if(sub.equals("status")||sub.equals("info")){
                return Outcome.noSave(
                    "CUSTOM_PET_R1_VOIDGLASS_STATUS "+
                    voidglass.summary(
                        dev.petParticleSelector())+
                    " visiblePet="+
                    (activePet==null
                        ?"none"
                        :activePet.petItemId+
                            "->"+
                            activePet.definitionId)+
                    " customNameServerSide=Voidglass_Nistirio"+
                    " clientDefinitionName=Vasa_nistirio_pet"+
                    " limitation=NEW_CLIENT_DEFINITION_NOT_PACKED_IN_R1");
            }

            return Outcome.noSave(
                "CUSTOM_PET_R1_VOIDGLASS_HELP usage=on | fx <6|8|auto> | proc | status | off");
        }

        return null;
    }

    Outcome giveR3(ServerPacketWriter writer)throws IOException{
        PetDefinitionRepository.Def definition=
            PetDefinitionRepository.get(
                VoidglassR3CustomContent.ITEM_ID);

        if(definition==null||
           definition.npcId!=
               VoidglassR3CustomContent.DEFAULT_NPC_ID){
            return Outcome.noSave(
                "VOIDGLASS_R3_GIVE_REJECTED serverDefinitionMissing=true");
        }

        String result=bank.spawnItem(
            VoidglassR3CustomContent.ITEM_ID,
            1,
            writer);

        return new Outcome(
            "VOIDGLASS_R3_GIVE "+result+
            " next=inventory_Drop normalLifecycle=true correctedClientRange=true legacy32760Retired=true",
            "CUSTOM_VOIDGLASS_R3_GIVE");
    }

    String selectR3Candidate(
        int index,
        ServerPacketWriter writer
    )throws IOException{
        if(!VoidglassR3CustomContent.active(
            petState,npcs.pet())){
            return "VOIDGLASS_R3_CANDIDATE_REJECTED activePetMustBe=item29999";
        }

        VoidglassR3CustomContent.Candidate candidate=
            VoidglassR3CustomContent.candidate(index);

        if(candidate==null){
            return "VOIDGLASS_R3_CANDIDATE_REJECTED expected=1 nativeCompositorRetired=true";
        }

        String result=npcs.previewPetDefinition(
            candidate.npcId,
            movement,
            writer);

        return "VOIDGLASS_R3_CANDIDATE_OK "+
            candidate.summary()+
            " transport={"+result+"}"+
            " persistence=DEFAULT_CANDIDATE_ON_RELOGIN";
    }

    String cycleR3Candidate(
        ServerPacketWriter writer
    )throws IOException{
        if(!VoidglassR3CustomContent.active(
            petState,npcs.pet())){
            return "VOIDGLASS_R3_CANDIDATE_REJECTED activePetMustBe=item29999";
        }

        VoidglassR3CustomContent.Candidate current=
            VoidglassR3CustomContent.candidateByNpc(
                npcs.pet().definitionId);
        int next=
            current==null
                ?1
                :(current.index%
                    VoidglassR3CustomContent.CANDIDATES.length)+1;

        return selectR3Candidate(next,writer);
    }

    String triggerR3Proc(
        ServerPacketWriter writer
    )throws IOException{
        if(!VoidglassR3CustomContent.active(
            petState,npcs.pet())){
            return "VOIDGLASS_R3_PROC_REJECTED activePetMustBe=item29999 customNpc12000";
        }

        VoidglassR3CustomContent.Candidate candidate=
            VoidglassR3CustomContent.candidateByNpc(
                npcs.pet().definitionId);

        if(candidate==null){
            candidate=
                VoidglassR3CustomContent.defaultCandidate();
        }

        String fx=npcs.animationAndGfxPet(
            candidate.stand,
            0,
            VoidglassR3CustomContent.PROC_GFX,
            0,
            0,
            writer);
        String text=npcs.forcePetText(
            VoidglassR3CustomContent.PROC_TEXT,
            writer);

        return "VOIDGLASS_R3_PROC_OK candidate="+
            candidate.index+
            " anim="+candidate.stand+
            " gfx="+VoidglassR3CustomContent.PROC_GFX+
            " text={"+text+"}"+
            " visual={"+fx+"}"+
            " customPipelineAsset=true gameplayModifier=NONE";
    }

    String statusR3(){
        NpcEntity pet=npcs.pet();
        VoidglassR3CustomContent.Candidate candidate=
            pet==null
                ?null
                :VoidglassR3CustomContent.candidateByNpc(
                    pet.definitionId);

        return VoidglassR3CustomContent.profile()+
            " active="+
            VoidglassR3CustomContent.active(
                petState,pet)+
            " candidate="+
            (candidate==null
                ?"none"
                :candidate.summary())+
            " currentPet="+
            (pet==null
                ?"none"
                :"item="+pet.petItemId+
                    " npc="+pet.definitionId+
                    " world="+pet.x+","+pet.y)+
            " currentMovementAuthority=R8.4/V9.12-derived-follow"+
            " pickupFix=R8.1";
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}

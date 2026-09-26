package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pet runtime/presentation test command coordinator.
 *
 * This owns the existing LocalLab pet-proc/charge visual fixtures and the
 * realtime nine-step presentation sequence. Combat M2 remains a fixture:
 * effect metadata is recorded/presented here but is not inserted into damage
 * or accuracy formulas.
 */
final class LocalPetRuntimeCommandHandler {
    private final PetState petState;
    private final PetEffectState petEffects;
    private final NpcRegistry npcs;
    private final MovementState movement;

    private int sequenceStep=-1;
    private long sequenceAt=Long.MAX_VALUE;

    LocalPetRuntimeCommandHandler(
        PetState petState,
        PetEffectState petEffects,
        NpcRegistry npcs,
        MovementState movement
    ){
        this.petState=java.util.Objects.requireNonNull(
            petState,"petState");
        this.petEffects=java.util.Objects.requireNonNull(
            petEffects,"petEffects");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(
            movement,"movement");
    }

    List<String> status(){
        NpcEntity pet=
            npcs.pet();

        return one(
            "V59_PET_STATUS active="+
            (pet!=null)+
            " petState="+
            (petState.active()
                ?petState.itemId()+"->"+
                    petState.npcId()
                :"none")+
            " visibleNpc="+
            (pet==null
                ?"none"
                :pet.definitionId)+
            " nativeFamily="+
            (pet==null
                ?"NONE"
                :PetPresentationProfile
                    .nativeStateFamily(
                        pet.definitionId
                    ))+
            " effectState={"+
            petEffects.summary()+
            "}"+
            " followOwnerRunning="+
            npcs.recentOwnerRunning()
        );
    }

    List<String> boost(
        ServerPacketWriter serverPackets
    )throws IOException{
        serverPackets.varShort(
            81,
            CombatSync.player81GfxOnly(
                1310,
                0,
                0
            )
        );

        return one(
            "V593_PET_BOOST_FIXTURE result=PLAYER_PRESENTATION anim=NONE gfx=1310 productionNormalPetEvidence=LIVE_COMPONENT_ISOLATION"
        );
    }

    List<String> scopeSnipe(
        ServerPacketWriter serverPackets
    )throws IOException{
        if(!scopesightActive()||
           npcs.pet()==null||
           npcs.pet().definitionId!=
               ScopesightPetProfile.NPC_ID)
            return one(
                "V58_SCOPESIGHT_SNIPE result=REJECTED_NO_ACTIVE_SCOPESIGHT activePet="+
                (
                    petState.active()
                        ?petState.itemId()+
                            "->"+
                            petState.npcId()
                        :"none"
                )
            );

        String result=
            npcs.forcePetText(
                ScopesightPetProfile
                    .NATIVE_TRIGGER_TEXT,
                serverPackets
            );

        return one(
            "V58_SCOPESIGHT_SNIPE result="+
            result+
            " nativeClientTrigger=true"
        );
    }

    List<String> proc(
        ServerPacketWriter serverPackets
    )throws IOException{
        serverPackets.varShort(
            81,
            CombatSync.player81GfxOnly(
                1310,
                0,
                0
            )
        );

        String snipe=
            "NOT_SCOPESIGHT";

        if(scopesightActive()&&
           npcs.pet()!=null&&
           npcs.pet().definitionId==
               ScopesightPetProfile.NPC_ID)
            snipe=
                npcs.forcePetText(
                    ScopesightPetProfile
                        .NATIVE_TRIGGER_TEXT,
                    serverPackets
                );

        return one(
            "V511_PET_PROC_FIXTURE playerAnim=NONE playerGfx=1310 scopesight="+
            snipe+
            " semantics=PRODUCTION_NORMAL_PET_BOOST_PRESENTATION"
        );
    }

    List<String> evilWolperProc(
        int state,
        ServerPacketWriter serverPackets
    )throws IOException{
        NpcEntity pet=npcs.pet();

        if(pet==null||
           !(pet.definitionId==6991||
             (pet.definitionId>=8124&&
              pet.definitionId<=8126))){
            return one(
                "V59_EVIL_WOLPER_PROC result=REJECTED_ACTIVE_PET_NOT_EVIL_WOLPER");
        }

        String nativeState=npcs.setPetNativeState(
            state,serverPackets);

        serverPackets.varShort(
            81,
            CombatSync.player81GfxOnly(
                PetPresentationProfile.OWNER_BOOST_GFX,
                0,
                0));

        return one(
            "V593_EVIL_WOLPER_PROC state="+nativeState+
            " ownerBoost=GFX1310_ONLY nativeIcon=sprite53 physicalBodyAnimation=UNRESOLVED_USE_pettest_anim");
    }

    List<String> temporossProc(
        int state,
        ServerPacketWriter serverPackets
    )throws IOException{
        NpcEntity pet=npcs.pet();

        if(pet==null||
           PetPresentationProfile.nativeStateFamily(
               pet.definitionId)!=
               PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF){
            return one(
                "V59_TEMPOROSS_PROC result=REJECTED_ACTIVE_PET_NOT_TEMPOROSS");
        }

        String animation=npcs.animatePet(
            PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE,
            0,
            serverPackets);
        String nativeState=npcs.setPetNativeState(
            state,serverPackets);

        return one(
            "V59_TEMPOROSS_PROC anim="+animation+
            " state="+nativeState+
            " anim15562Evidence=EXACT_CACHE_NAME_CANDIDATE nativeStateRenderer=EXACT_CLIENT");
    }

    List<String> charge(
        int charge,
        ServerPacketWriter serverPackets
    )throws IOException{
        NpcEntity pet=npcs.pet();

        if(pet==null||
           !PetPresentationProfile.isChargePet(
               pet.petItemId,
               pet.definitionId)){
            return one(
                "V59_BEHEMOTH_CHARGE result=REJECTED_ACTIVE_PET_NOT_CHARGE_FAMILY");
        }

        if(charge<0||charge>3){
            return one(
                "V59_BEHEMOTH_CHARGE result=REJECTED_RANGE expected=0..3");
        }

        petEffects.forceCharge(
            charge,System.currentTimeMillis());
        String state=npcs.setPetNativeState(
            charge,serverPackets);

        return one(
            "V59_BEHEMOTH_CHARGE result="+state+
            " effectState={"+petEffects.summary()+"}");
    }

    List<String> damage(
        int damage,
        ServerPacketWriter serverPackets
    )throws IOException{
        return one(
            applyDamage(
                damage,
                System.currentTimeMillis(),
                serverPackets,
                "MANUAL_BEHEMOTH_HIT"
            )
        );
    }

    List<String> handle(
        String[] p,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length<1)return null;

        if(p[0].equalsIgnoreCase("pettestall")){
            armSequence(System.currentTimeMillis());

            return one(
                "V59_PET_TEST_ALL_ARMED activePet="+
                (npcs.pet()==null
                    ?"none"
                    :npcs.pet().petItemId+
                        "->"+
                        npcs.pet().definitionId)+
                " sequence=owner827,playerAnim10184_ONLY,playerGfx1310_ONLY,combined10184+1310,state1,state2,state3,state0,specific intervalMs=1800");
        }

        if(p[0].equalsIgnoreCase("pettest")){
            String sub=
                p.length>=2
                    ?p[1].toLowerCase(Locale.ROOT)
                    :"help";

            if(sub.equals("help")){
                return one(
                    "V59_PET_TEST_HELP commands=::pettestall | ::pettest all | profile | lifecycle | boost | state <0..3> | charge <0..3> | damage <amount> | reset | anim <id> [delay] | gfx <id> [height] [delay] | animfx <anim> <gfx> [height] [delay] | preview <npcId> | snipe | tempoross [state]");
            }

            if(sub.equals("all")){
                armSequence(System.currentTimeMillis());

                return one(
                    "V59_PET_TEST_ALL_ARMED alias=pettest_all activePet="+
                    (npcs.pet()==null
                        ?"none"
                        :npcs.pet().petItemId+
                            "->"+
                            npcs.pet().definitionId)+
                    " intervalMs=1800");
            }

            if(sub.equals("profile")){
                NpcEntity active=npcs.pet();

                return one(
                    "V59_PET_TEST_PROFILE active="+
                    (active!=null)+
                    " pet="+
                    (active==null
                        ?"none"
                        :active.petItemId+
                            "->"+
                            active.definitionId)+
                    " nativeFamily="+
                    (active==null
                        ?"NONE"
                        :PetPresentationProfile.nativeStateFamily(
                            active.definitionId))+
                    " stateVisuals="+
                    (active==null
                        ?"none"
                        :PetPresentationProfile.nativeStateVisual(
                            active.definitionId,1)+
                            ","+
                            PetPresentationProfile.nativeStateVisual(
                                active.definitionId,2)+
                            ","+
                            PetPresentationProfile.nativeStateVisual(
                                active.definitionId,3))+
                    " lifecycle=owner827_noGfx"+
                    " boost=owner10184+gfx1310"+
                    " effectState={"+petEffects.summary()+"}");
            }

            if(sub.equals("lifecycle")){
                serverPackets.varShort(
                    81,
                    CombatSync.player81AnimationOnly(
                        PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION));

                return one(
                    "V59_PET_TEST lifecycle ownerAnim=827 ownerGfx=NONE evidence=V908_PRODUCTION_CERTIFIED");
            }

            if(sub.equals("boost")){
                serverPackets.varShort(
                    81,
                    CombatSync.player81GfxOnly(
                        PetPresentationProfile.OWNER_BOOST_GFX,
                        0,
                        0));

                return one(
                    "V593_PET_TEST boost ownerAnim=NONE ownerGfx=1310");
            }

            if(sub.equals("state")){
                int state=p.length>=3?parseInt(p[2],-1):-1;
                String result=npcs.setPetNativeState(
                    state,serverPackets);

                return one(
                    "V59_PET_TEST state result="+result);
            }

            if(sub.equals("charge")){
                int charge=p.length>=3?parseInt(p[2],-1):-1;
                NpcEntity active=npcs.pet();

                if(active==null||
                   !PetPresentationProfile.isChargePet(
                       active.petItemId,
                       active.definitionId)||
                   charge<0||
                   charge>3){
                    return one(
                        "V59_PET_TEST charge result=REJECTED expected=active_charge_pet_and_0..3");
                }

                petEffects.forceCharge(
                    charge,System.currentTimeMillis());
                String result=npcs.setPetNativeState(
                    charge,serverPackets);

                return one(
                    "V59_PET_TEST charge result="+result+
                    " effectState={"+petEffects.summary()+"}");
            }

            if(sub.equals("damage")){
                int damage=p.length>=3?parseInt(p[2],0):0;
                return one(
                    applyDamage(
                        damage,
                        System.currentTimeMillis(),
                        serverPackets,
                        "PETTEST_DAMAGE"));
            }

            if(sub.equals("reset")){
                NpcEntity active=npcs.pet();
                String result;

                if(active!=null&&
                   PetPresentationProfile.supportsNativeState(
                       active.definitionId)){
                    result=npcs.setPetNativeState(
                        0,serverPackets);
                }else{
                    result="NO_NATIVE_STATE_TO_CLEAR";
                }

                petEffects.forceCharge(
                    0,System.currentTimeMillis());

                return one(
                    "V59_PET_TEST reset result="+result+
                    " effectState={"+petEffects.summary()+"}");
            }

            if(sub.equals("anim")){
                int anim=p.length>=3?parseInt(p[2],-999):-999;
                int delay=p.length>=4?parseInt(p[3],0):0;
                String result=npcs.animatePet(
                    anim,delay,serverPackets);

                return one(
                    "V59_PET_TEST anim result="+result+
                    " semantics=RAW_LOCALHOST_VISUAL_PROBE");
            }

            if(sub.equals("gfx")){
                int gfx=p.length>=3?parseInt(p[2],-999):-999;
                int height=p.length>=4?parseInt(p[3],0):0;
                int delay=p.length>=5?parseInt(p[4],0):0;
                String result=npcs.gfxPet(
                    gfx,height,delay,serverPackets);

                return one(
                    "V59_PET_TEST gfx result="+result+
                    " codec=EXACT_PACKET65_MASK_0x80");
            }

            if(sub.equals("animfx")){
                int anim=p.length>=3?parseInt(p[2],-999):-999;
                int gfx=p.length>=4?parseInt(p[3],-999):-999;
                int height=p.length>=5?parseInt(p[4],0):0;
                int delay=p.length>=6?parseInt(p[5],0):0;
                String result=npcs.animationAndGfxPet(
                    anim,0,gfx,height,delay,serverPackets);

                return one(
                    "V59_PET_TEST animfx result="+result+
                    " semantics=RAW_LOCALHOST_VISUAL_PROBE");
            }

            if(sub.equals("preview")){
                int npc=p.length>=3?parseInt(p[2],-1):-1;
                String result=npcs.previewPetDefinition(
                    npc,movement,serverPackets);

                return one(
                    "V59_PET_TEST preview result="+result+
                    " note=NOT_PERSISTED_USE_TO_IDENTIFY_SCOOBY_COLOR_MAPPING");
            }

            if(sub.equals("snipe")){
                String result=
                    npcs.pet()!=null&&
                    npcs.pet().definitionId==8330
                        ?npcs.forcePetText(
                            "SNIPE",serverPackets)
                        :"REJECTED_ACTIVE_PET_NOT_SCOPESIGHT";

                return one(
                    "V59_PET_TEST snipe result="+result);
            }

            if(sub.equals("tempoross")){
                int state=p.length>=3?parseInt(p[2],1):1;

                if(npcs.pet()==null||
                   PetPresentationProfile.nativeStateFamily(
                       npcs.pet().definitionId)!=
                       PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF){
                    return one(
                        "V59_PET_TEST tempoross result=REJECTED_ACTIVE_PET_NOT_TEMPOROSS");
                }

                String animation=npcs.animatePet(
                    PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE,
                    0,
                    serverPackets);
                String nativeState=npcs.setPetNativeState(
                    state,serverPackets);

                return one(
                    "V59_PET_TEST tempoross anim="+animation+
                    " state="+nativeState+
                    " animationEvidence=EXACT_CACHE_NAME_CANDIDATE_NOT_RUNTIME_BOUND");
            }

            return one(
                "V59_PET_TEST result=UNKNOWN_SUBCOMMAND sub="+
                sub+
                " use=::pettest_help");
        }

        if(p[0].equalsIgnoreCase("petnpc")&&p.length>=2){
            int npc=parseInt(p[1],-1);
            String result=npcs.previewPetDefinition(
                npc,movement,serverPackets);

            return one(
                "V59_PET_NPC_PREVIEW result="+result+
                " recommendedScoobyCandidates=5159,5160,5162,6650");
        }

        return null;
    }

    String applyDamage(
        int damage,
        long now,
        ServerPacketWriter serverPackets,
        String source
    )throws IOException{
        if(damage<=0){
            return "V59_PET_DAMAGE source="+source+
                " result=IGNORED_NONPOSITIVE damage="+damage;
        }

        NpcEntity pet=npcs.pet();
        if(pet==null||
           !PetPresentationProfile.isChargePet(
               pet.petItemId,
               pet.definitionId)){
            return "V59_PET_DAMAGE source="+source+
                " damage="+damage+
                " result=NO_ACTIVE_CHARGE_PET";
        }

        boolean changed=petEffects.recordDamage(
            damage,now);
        String presentation="UNCHANGED";

        if(changed){
            presentation=npcs.setPetNativeState(
                petEffects.charge(),
                serverPackets);
        }

        return "V59_PET_DAMAGE source="+source+
            " damage="+damage+
            " chargeChanged="+changed+
            " presentation="+presentation+
            " effectState={"+petEffects.summary()+"}"+
            " modifiersRecordedOnly=true combatM2FormulaStillFixture=true";
    }

    void armSequence(long now){
        sequenceStep=0;
        sequenceAt=now;
    }

    boolean sequenceActive(){
        return sequenceStep>=0;
    }

    long sequenceAt(){
        return sequenceAt;
    }

    void failSequence(){
        sequenceStep=-1;
        sequenceAt=Long.MAX_VALUE;
    }

    String tickSequence(
        long now,
        ServerPacketWriter serverPackets
    )throws IOException{
        NpcEntity pet=npcs.pet();
        String log;

        switch(sequenceStep){
            case 0:
                serverPackets.varShort(
                    81,
                    CombatSync.player81AnimationOnly(
                        PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION));
                log=
                    "V591_PET_TEST_ALL step=1/9 ownerLifecycleAnim=827 gfx=NONE";
                break;

            case 1:
                serverPackets.varShort(
                    81,
                    CombatSync.player81AnimationOnly(
                        PetPresentationProfile.OWNER_BOOST_ANIMATION));
                log=
                    "V591_PET_TEST_ALL step=2/9 playerAnim10184_ONLY gfx=NONE purpose=NURSE_COMPONENT_ISOLATION";
                break;

            case 2:
                serverPackets.varShort(
                    81,
                    CombatSync.player81GfxOnly(
                        PetPresentationProfile.OWNER_BOOST_GFX,
                        0,
                        0));
                log=
                    "V591_PET_TEST_ALL step=3/9 playerGfx1310_ONLY anim=NONE purpose=NURSE_COMPONENT_ISOLATION";
                break;

            case 3:
                serverPackets.varShort(
                    81,
                    CombatSync.player81AnimationAndGfx(
                        PetPresentationProfile.OWNER_BOOST_ANIMATION,
                        PetPresentationProfile.OWNER_BOOST_GFX,
                        0,
                        0));
                log=
                    "V591_PET_TEST_ALL step=4/9 combined=10184+1310 purpose=REFERENCE_ONLY";
                break;

            case 4:
            case 5:
            case 6:
                int state=sequenceStep-3;
                String stateResult=
                    pet==null
                        ?"SKIP_NO_ACTIVE_PET"
                        :npcs.setPetNativeState(
                            state,serverPackets);
                log=
                    "V591_PET_TEST_ALL step="+
                    (sequenceStep+1)+
                    "/9 state="+state+
                    " result="+stateResult;
                break;

            case 7:
                String reset=
                    pet==null
                        ?"SKIP_NO_ACTIVE_PET"
                        :npcs.setPetNativeState(
                            0,serverPackets);
                log=
                    "V591_PET_TEST_ALL step=8/9 state=0 result="+
                    reset;
                break;

            case 8:
                String specific="NONE_FOR_THIS_PET";

                if(pet!=null&&pet.definitionId==8330){
                    specific=npcs.forcePetText(
                        "SNIPE",serverPackets);
                }else if(
                    pet!=null&&
                    PetPresentationProfile.nativeStateFamily(
                        pet.definitionId)==
                        PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF){
                    specific=
                        npcs.animatePet(
                            PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE,
                            0,
                            serverPackets)+
                        "; "+
                        npcs.setPetNativeState(
                            1,serverPackets);
                }else if(
                    pet!=null&&
                    PetPresentationProfile.nativeStateFamily(
                        pet.definitionId)==
                        PetPresentationProfile.NativeStateFamily.WOLPER_KRAMP_ACTIVE){
                    specific=npcs.setPetNativeState(
                        1,serverPackets);
                }

                log=
                    "V591_PET_TEST_ALL step=9/9 specific="+
                    specific;
                failSequence();
                return log;

            default:
                failSequence();
                return null;
        }

        sequenceStep++;
        sequenceAt=now+1800L;
        return log;
    }

    private boolean scopesightActive(){
        return petState.active()&&
            petState.itemId()==ScopesightPetProfile.ITEM_ID&&
            petState.npcId()==ScopesightPetProfile.NPC_ID;
    }

    private static List<String> one(String line){
        return Collections.singletonList(line);
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}

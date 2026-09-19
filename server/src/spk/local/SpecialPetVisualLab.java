package spk.local;

/**
 * Exact-current client special-pet renderer audit + LocalLab dev presentation labels.
 * Values marked CLIENT_HARDCODED are inspection authority only; the server must not
 * pretend they are packet fields.
 */
final class SpecialPetVisualLab {
    private SpecialPetVisualLab(){}

    static boolean isSpecial(int npc){ return npc==1334||npc==1335||npc==1336||npc==8210; }

    static String info(int npc,Integer explicitFx){
        String fx=explicitFx==null?"AUTO":String.valueOf(explicitFx);
        switch(npc){
            case 1334:
                return "DEV_PET_VISUAL_INFO npc=1334 name=Yoshiganger family=OWNER_COPY_SPECIAL_RENDERER ownerCopyEligible=interactionTarget>=32768 alpha=150 source=CLIENT_HARDCODED_RENDERER aI=319770 aISource=CLIENT_HARDCODED_DRAW_OVERRIDE_RESET_AFTER_DRAW bodyTint=YOSHIGANGER_SPECIAL sourceTint=CLIENT_SPECIAL_RENDER_PATH intrinsicFx=3 intrinsicFxSource=PRIOR_CURRENT_CLIENT_AUTHORITY explicitFx="+fx+" explicitFxSource=SERVER_SPAWN_OPTION compatibility=NPC_SPECIAL_RENDERER_ONLY+PLAYER_MORPH_SAFE_OBSERVED";
            case 1335:
                return "DEV_PET_VISUAL_INFO npc=1335 name=Doppelganger family=OWNER_COPY_SPECIAL_RENDERER ownerCopyEligible=interactionTarget>=32768 alpha=100 source=CLIENT_HARDCODED_RENDERER aI=NONE bodyTint=OWNER_COPY_DEFAULT intrinsicFx=6 intrinsicFxSource=PRIOR_CURRENT_CLIENT_AUTHORITY explicitFx="+fx+" explicitFxSource=SERVER_SPAWN_OPTION compatibility=NPC_SPECIAL_RENDERER_ONLY+PLAYER_MORPH_UNPROVEN";
            case 1336:
                return "DEV_PET_VISUAL_INFO npc=1336 name=Shadow family=OWNER_COPY_SPECIAL_RENDERER ownerCopyEligible=interactionTarget>=32768 alpha=SPECIAL_DEFAULT source=CLIENT_SPECIAL_RENDER_PATH aI=NONE bodyTint=SHADOW_SPECIAL intrinsicFx=UNKNOWN explicitFx="+fx+" explicitFxSource=SERVER_SPAWN_OPTION compatibility=NPC_SPECIAL_RENDERER_ONLY+PLAYER_MORPH_UNPROVEN";
            case 8210:
                return "DEV_PET_VISUAL_INFO npc=8210 name=Wondrous_Doppel family=OWNER_COPY_SPECIAL_RENDERER ownerCopyEligible=interactionTarget>=32768 alpha=100 source=CLIENT_HARDCODED_RENDERER aI=NONE bodyTint=DYNAMIC_BLUE_VIOLET bodyTintClock=Client.fg sourceTint=CLIENT_HARDCODED_SIN_CLOCK intrinsicFx=3 intrinsicFxSource=PRIOR_CURRENT_CLIENT_AUTHORITY explicitFx="+fx+" explicitFxSource=SERVER_SPAWN_OPTION ordinaryModelList=false compatibility=NPC_SPECIAL_RENDERER_ONLY+PLAYER_MORPH_UNSAFE_LIVE_OBSERVED";
            default:
                return "DEV_PET_VISUAL_INFO npc="+npc+" family=STANDARD_OR_UNCLASSIFIED ownerCopyEligible=UNKNOWN alpha=AUTO tint=AUTO aI=AUTO bodycycle=AUTO explicitFx="+fx+" compatibility=STANDARD_NPC_MODEL_OR_UNCLASSIFIED";
        }
    }

    static String inspectOnly(String component,int npc){
        if(!isSpecial(npc)) return "DEV_PET_VISUAL_"+component.toUpperCase()+" npc="+npc+" result=UNCLASSIFIED_USE_visual_info";
        return "DEV_PET_VISUAL_"+component.toUpperCase()+" npc="+npc+" result=INSPECT_ONLY source=CLIENT_HARDCODED_OR_DERIVED_RUNTIME_STATE requires=ISOLATED_LOCAL_DEV_CLIENT_HOOK authority=NOT_SERVER_PACKET_FIELD";
    }
}

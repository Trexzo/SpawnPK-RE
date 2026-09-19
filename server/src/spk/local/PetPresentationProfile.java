package spk.local;

/**
 * Exact/current-client pet presentation families plus narrowly scoped gameplay metadata.
 * Presentation IDs in this class come from pinned client bytecode/cache evidence.
 * Mechanics metadata is explicitly tagged where it comes from official SpawnPK update notes.
 */
final class PetPresentationProfile {
    static final int OWNER_DROP_PICKUP_ANIMATION = 827;
    static final int OWNER_BOOST_ANIMATION = 10184;
    static final int OWNER_BOOST_GFX = 1310;
    static final int TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE = 15562; // exact cache name: "Tempoross pet activation"

    enum NativeStateFamily {
        NONE,
        BEHEMOTH_CHARGES,      // state 1/2/3 => 1/2/3 x native sprite 22
        TEMPOROSS_DEBUFF,      // state 1/2/3 => native sprites 59/58/369
        WOLPER_KRAMP_ACTIVE    // any state >0 => native sprite 53
    }

    private PetPresentationProfile() {}

    static NativeStateFamily nativeStateFamily(int npcId) {
        if (npcId == 6650 || (npcId >= 5159 && npcId <= 5163) || npcId == 6049)
            return NativeStateFamily.BEHEMOTH_CHARGES;
        if (npcId >= 8184 && npcId <= 8186)
            return NativeStateFamily.TEMPOROSS_DEBUFF;
        if (npcId == 3962 || npcId == 3965 || npcId == 6991 || (npcId >= 8124 && npcId <= 8126))
            return NativeStateFamily.WOLPER_KRAMP_ACTIVE;
        return NativeStateFamily.NONE;
    }

    static boolean supportsNativeState(int npcId) { return nativeStateFamily(npcId) != NativeStateFamily.NONE; }

    static String nativeStateVisual(int npcId, int state) {
        NativeStateFamily f=nativeStateFamily(npcId);
        if(state<0||state>3) return "INVALID_STATE";
        switch(f){
            case BEHEMOTH_CHARGES:
                return state==0?"NONE":state+"x_NATIVE_SPRITE_22";
            case TEMPOROSS_DEBUFF:
                return state==0?"NONE":(state==1?"NATIVE_SPRITE_59":state==2?"NATIVE_SPRITE_58":"NATIVE_SPRITE_369");
            case WOLPER_KRAMP_ACTIVE:
                return state==0?"NONE":"NATIVE_SPRITE_53";
            default:return "UNSUPPORTED";
        }
    }

    static boolean isScopesight(int itemId,int npcId){ return itemId==28888 && npcId==8330; }
    static boolean isSolarBehemoth(int itemId,int npcId){ return itemId==25415 || npcId==6650; }
    static boolean isUnholyBehemoth(int itemId,int npcId){
        return itemId==25425 || (itemId>=24016&&itemId<=24019) || (npcId>=5159&&npcId<=5163);
    }
    static boolean isAncientHydra(int itemId,int npcId){ return itemId==22947 || npcId==6049; }
    static boolean isChargePet(int itemId,int npcId){ return isSolarBehemoth(itemId,npcId)||isUnholyBehemoth(itemId,npcId)||isAncientHydra(itemId,npcId); }

    static int damagePerCharge(int itemId,int npcId){
        if(isAncientHydra(itemId,npcId)) return 50; // official Nov 2022 update
        if(isSolarBehemoth(itemId,npcId)||isUnholyBehemoth(itemId,npcId)) return 75; // official Jul 2026 update
        return 0;
    }

    static long chargeResetMs(int itemId,int npcId){
        if(isUnholyBehemoth(itemId,npcId)) return 20_000L;
        if(isSolarBehemoth(itemId,npcId)||isAncientHydra(itemId,npcId)) return 10_000L;
        return 0L;
    }

    static String chargeMechanicsSummary(int itemId,int npcId,int charge){
        if(charge<=0) return "no_charge_bonus";
        if(isSolarBehemoth(itemId,npcId)){
            if(charge==1)return "phase1: +10% damage";
            if(charge==2)return "phase2: +15% defences";
            return "phase3: +15% accuracy";
        }
        if(isUnholyBehemoth(itemId,npcId)){
            if(charge==1)return "phase1: +15% damage; smite 1/6 damage";
            if(charge==2)return "phase2: +25% defences; smite 1/5 damage";
            return "phase3: +25% accuracy; smite 1/4 damage";
        }
        if(isAncientHydra(itemId,npcId)) return "defence-ignore scaling charge="+charge+" (visual/state exact; exact scale deferred)";
        return "unknown";
    }
}

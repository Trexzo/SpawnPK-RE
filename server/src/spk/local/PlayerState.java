package spk.local;

import java.util.Arrays;

/**
 * Server-owned player state used by the exact current client surfaces.
 *
 * v5.8 adds three evidence-backed presentation/state channels:
 *  - native icon item -> optional appearance bs item id (V9.08 certified)
 *  - nurse status/special restoration (server-authority localhost contract)
 *  - Scopesight maintained Magic/Ranged levels (official effect profile;
 *    full combat modifiers remain intentionally deferred).
 */
final class PlayerState {
    static final class PreparedScopesightMaintenance {
        final int expectedRanged;
        final int expectedMagic;
        final int nextRanged;
        final int nextMagic;
        final int changedMask;

        PreparedScopesightMaintenance(
            int expectedRanged,
            int expectedMagic,
            int nextRanged,
            int nextMagic,
            int changedMask
        ){
            this.expectedRanged=expectedRanged;
            this.expectedMagic=expectedMagic;
            this.nextRanged=nextRanged;
            this.nextMagic=nextMagic;
            this.changedMask=changedMask;
        }

        int levelForSkill(
            int skill
        ){
            if(skill==RANGED)
                return nextRanged;
            if(skill==MAGIC)
                return nextMagic;
            throw new IllegalArgumentException(
                "Scopesight prepared skill "+
                skill
            );
        }
    }

    static final int ATTACK=0, DEFENCE=1, STRENGTH=2, HITPOINTS=3, RANGED=4, PRAYER=5, MAGIC=6;
    static final int COMBAT_SKILL_COUNT=7;
    static final int XP_99=13_034_431;
    static final int DEFAULT_COMBAT_LEVEL=126;
    private static final int[] DEFAULT_COMP_SELECTORS={13,9,7,9,7,5};

    private final int[] current=new int[COMBAT_SKILL_COUNT];
    private final int[] xp=new int[COMBAT_SKILL_COUNT];
    private final int[] compSelectors=DEFAULT_COMP_SELECTORS.clone();
    private int characterGender=CharacterDesignProfile.MALE;
    private final int[] characterKits=
        CharacterDesignProfile.defaultKits(
            CharacterDesignProfile.MALE
        );
    private final int[] characterColours=
        CharacterDesignProfile.defaultColours();

    /** Exact-current optional extra player appearance item (rs.a.k.bs), -1 absent. */
    private int nativeIconItemId=-1;
    /**
     * Exact-current per-player appearance role/rank (rs.a.k.aC).
     *
     * This is session presentation state, deliberately excluded from account
     * snapshots and independent from login privilege Client.cT. The original
     * SpawnPK named-rank -> numeric aC mapping remains unknown server authority;
     * localhost development tooling may set this channel explicitly.
     */
    private int appearanceRank;
    private boolean appearanceRankOverride;
    /** Dedicated bottom-center COSMETIC channel. Ammo/arrows remain ordinary equipment slot 13. */
    private final CosmeticState cosmetic=new CosmeticState();

    /** LocalLab-owned mechanics state. Percent-like special energy 0..100. */
    private int specialEnergy=100;
    private int poison;
    private int venom;
    private int sicken;

    PlayerState(){
        Arrays.fill(current,99);
        Arrays.fill(xp,XP_99);
    }

    int currentLevel(int skill){ return current[skill]; }
    int xp(int skill){ return xp[skill]; }
    int combatLevel(){ return DEFAULT_COMBAT_LEVEL; }
    int[] compSelectors(){ return compSelectors.clone(); }
    int characterGender(){ return characterGender; }
    int[] characterKits(){ return characterKits.clone(); }
    int[] characterColours(){ return characterColours.clone(); }
    int nativeIconItemId(){ return nativeIconItemId; }
    int appearanceRank(){ return appearanceRank; }
    boolean hasAppearanceRankOverride(){ return appearanceRankOverride; }
    void setAppearanceRank(int rank){
        if(rank<Short.MIN_VALUE||rank>Short.MAX_VALUE)
            throw new IllegalArgumentException(
                "appearance rank signed-short"
            );
        appearanceRank=rank;
        appearanceRankOverride=true;
    }
    void clearAppearanceRankOverride(){
        appearanceRank=0;
        appearanceRankOverride=false;
    }
    CosmeticState cosmetic(){ return cosmetic; }
    int specialEnergy(){ return specialEnergy; }
    int poison(){ return poison; }
    int venom(){ return venom; }
    int sicken(){ return sicken; }

    /** Compatibility accessor retained for old v5.6/v5.7 tests. Old bg-index path stays disabled. */
    int collectionIconIndex(){ return 0; }

    /**
     * Exact-current correction: rs.a.k.bs is the dedicated COSMETIC channel, not ammo.
     * Ammo/arrows stay in ordinary equipment slot 13 and may coexist with a cosmetic icon.
     */
    int syncEquipmentPresentation(EquipmentState equipment){
        int old=nativeIconItemId;
        nativeIconItemId=cosmetic.active()?cosmetic.itemId():-1;
        return old;
    }

    boolean setCurrentLevel(int skill,int level){
        if(skill<0 || skill>=COMBAT_SKILL_COUNT) throw new IllegalArgumentException("skill");
        int v=Math.max(1,Math.min(255,level));
        if(current[skill]==v)return false;
        current[skill]=v;
        return true;
    }

    void setXp(int skill,int value){
        if(skill<0 || skill>=COMBAT_SKILL_COUNT)
            throw new IllegalArgumentException("skill");
        xp[skill]=Math.max(0,value);
    }

    /**
     * HP-specific world lifecycle mutation. Unlike ordinary skill-level setters,
     * hitpoints must be able to reach zero so death can be represented.
     */
    int applyHitpointsDamage(int amount){
        int requested=Math.max(0,amount);
        int before=current[HITPOINTS];
        int after=Math.max(0,before-requested);
        current[HITPOINTS]=after;
        return before-after;
    }

    void restoreHitpointsDefault(){
        current[HITPOINTS]=99;
    }

    boolean alive(){
        return current[HITPOINTS]>0;
    }

    /**
     * User-requested localhost nurse authority. The client proves presentation,
     * not these server decisions; keep the mechanic explicit here rather than
     * pretending it was recovered from client.jar.
     * Returns a bit-mask of combat skills whose visible current level changed.
     */
    int restoreNurse(){
        int changed=0;
        for(int i=0;i<COMBAT_SKILL_COUNT;i++){
            if(current[i]!=99){current[i]=99;changed|=1<<i;}
        }
        specialEnergy=100;
        poison=0; venom=0; sicken=0;
        return changed;
    }

    /** Test/localhost status substrate for nurse certification. */
    void setNegativeEffects(int poison,int venom,int sicken){
        this.poison=Math.max(0,poison);
        this.venom=Math.max(0,venom);
        this.sicken=Math.max(0,sicken);
    }
    void setSpecialEnergy(int value){ specialEnergy=Math.max(0,Math.min(100,value)); }

    /**
     * Scopesight Vasa maintained levels. While active, never lower a stronger
     * value. On removal, only unwind the exact maintained fixture values so this
     * milestone cannot erase future/other boost states.
     */
    PreparedScopesightMaintenance prepareScopesightMaintenance(
        boolean active
    ){
        int ranged=current[RANGED];
        int magic=current[MAGIC];
        int nextRanged=ranged;
        int nextMagic=magic;
        int changed=0;

        if(active){
            if(nextRanged<114){
                nextRanged=114;
                changed|=1<<RANGED;
            }
            if(nextMagic<109){
                nextMagic=109;
                changed|=1<<MAGIC;
            }
        }else{
            if(nextRanged==114){
                nextRanged=99;
                changed|=1<<RANGED;
            }
            if(nextMagic==109){
                nextMagic=99;
                changed|=1<<MAGIC;
            }
        }

        return new PreparedScopesightMaintenance(
            ranged,
            magic,
            nextRanged,
            nextMagic,
            changed
        );
    }

    void commitScopesightMaintenance(
        PreparedScopesightMaintenance prepared
    ){
        if(prepared==null)
            throw new NullPointerException("prepared");

        if(current[RANGED]!=prepared.expectedRanged||
           current[MAGIC]!=prepared.expectedMagic)
            throw new IllegalStateException(
                "Scopesight skill preimage changed before commit"
            );

        current[RANGED]=prepared.nextRanged;
        current[MAGIC]=prepared.nextMagic;
    }

    int syncScopesightMaintenance(boolean active){
        PreparedScopesightMaintenance prepared=
            prepareScopesightMaintenance(active);
        commitScopesightMaintenance(prepared);
        return prepared.changedMask;
    }

    boolean setCompSelectors(int[] values){
        if(values==null || values.length!=6) return false;
        for(int v:values) if(v<0 || v>19) return false;
        System.arraycopy(values,0,compSelectors,0,6);
        return true;
    }

    boolean setCharacterAppearance(
        int gender,
        int[] kits,
        int[] colours
    ){
        if(!CharacterDesignProfile.valid(
                gender,
                kits,
                colours))
            return false;

        characterGender=gender;
        System.arraycopy(
            kits,
            0,
            characterKits,
            0,
            characterKits.length
        );
        System.arraycopy(
            colours,
            0,
            characterColours,
            0,
            characterColours.length
        );
        return true;
    }

    String characterAppearanceSummary(){
        return "gender="+characterGender+
            " kits="+Arrays.toString(characterKits)+
            " colours="+Arrays.toString(characterColours);
    }

    String compSelectorSummary(){ return Arrays.toString(compSelectors); }

}

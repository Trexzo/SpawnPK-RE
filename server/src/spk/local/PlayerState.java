package spk.local;

import java.util.Arrays;
import java.util.Properties;

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
    static final int ATTACK=0, DEFENCE=1, STRENGTH=2, HITPOINTS=3, RANGED=4, PRAYER=5, MAGIC=6;
    static final int COMBAT_SKILL_COUNT=7;
    static final int XP_99=13_034_431;
    static final int DEFAULT_COMBAT_LEVEL=126;
    private static final int[] DEFAULT_COMP_SELECTORS={13,9,7,9,7,5};

    private final int[] current=new int[COMBAT_SKILL_COUNT];
    private final int[] xp=new int[COMBAT_SKILL_COUNT];
    private final int[] compSelectors=DEFAULT_COMP_SELECTORS.clone();

    /** Exact-current optional extra player appearance item (rs.a.k.bs), -1 absent. */
    private int nativeIconItemId=-1;
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
    int nativeIconItemId(){ return nativeIconItemId; }
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
    int syncScopesightMaintenance(boolean active){
        int changed=0;
        if(active){
            if(current[RANGED]<114){current[RANGED]=114;changed|=1<<RANGED;}
            if(current[MAGIC]<109){current[MAGIC]=109;changed|=1<<MAGIC;}
        } else {
            if(current[RANGED]==114){current[RANGED]=99;changed|=1<<RANGED;}
            if(current[MAGIC]==109){current[MAGIC]=99;changed|=1<<MAGIC;}
        }
        return changed;
    }

    boolean setCompSelectors(int[] values){
        if(values==null || values.length!=6) return false;
        for(int v:values) if(v<0 || v>19) return false;
        System.arraycopy(values,0,compSelectors,0,6);
        return true;
    }

    void saveAccountProperties(Properties p){
        for(int i=0;i<COMBAT_SKILL_COUNT;i++){
            p.setProperty("skill."+i+".current",Integer.toString(current[i]));
            p.setProperty("skill."+i+".xp",Integer.toString(xp[i]));
        }
        for(int i=0;i<compSelectors.length;i++)
            p.setProperty("comp.selector."+i,Integer.toString(compSelectors[i]));
        p.setProperty("combat.special.energy",Integer.toString(specialEnergy));
        p.setProperty("status.poison",Integer.toString(poison));
        p.setProperty("status.venom",Integer.toString(venom));
        p.setProperty("status.sicken",Integer.toString(sicken));
        p.setProperty("cosmetic.itemId",Integer.toString(cosmetic.itemId()));
    }

    void loadAccountProperties(Properties p){
        for(int i=0;i<COMBAT_SKILL_COUNT;i++){
            current[i]=boundedInt(p.getProperty("skill."+i+".current"),99,1,255);
            xp[i]=boundedInt(p.getProperty("skill."+i+".xp"),XP_99,0,Integer.MAX_VALUE);
        }
        for(int i=0;i<compSelectors.length;i++)
            compSelectors[i]=boundedInt(p.getProperty("comp.selector."+i),DEFAULT_COMP_SELECTORS[i],0,19);
        specialEnergy=boundedInt(p.getProperty("combat.special.energy"),100,0,100);
        poison=boundedInt(p.getProperty("status.poison"),0,0,Integer.MAX_VALUE);
        venom=boundedInt(p.getProperty("status.venom"),0,0,Integer.MAX_VALUE);
        sicken=boundedInt(p.getProperty("status.sicken"),0,0,Integer.MAX_VALUE);
        int cosmeticItem=boundedInt(p.getProperty("cosmetic.itemId"),-1,-1,65535);
        if(cosmeticItem>0 && ItemCatalog.isNativePlayerIcon(cosmeticItem)) cosmetic.set(cosmeticItem); else cosmetic.clear();
        nativeIconItemId=cosmetic.active()?cosmetic.itemId():-1;
    }

    String compSelectorSummary(){ return Arrays.toString(compSelectors); }

    private static int boundedInt(String s,int fallback,int min,int max){
        try{
            int v=Integer.parseInt(s);
            return v<min||v>max?fallback:v;
        }catch(Exception e){return fallback;}
    }
}

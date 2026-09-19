package spk.local;

import java.util.*;

/**
 * Per-session, localhost-only development overrides. Nothing in this object is
 * persisted to account state. Normal gameplay code must keep a safe native
 * default when an override is absent.
 */
final class DevAuthorityWorkbench {
    private Integer petParticleSelector; // null = AUTO/field absent
    private boolean petFollowFrozen;
    private Long petFollowDelayMs;        // null = normal client-speed cadence
    private final HashMap<Integer,Integer> combatAnimationByWeapon=new HashMap<>(); // -1 = explicitly off
    private final LinkedHashMap<Integer,Integer> petNpcBindingByItem=new LinkedHashMap<>();
    private final LinkedHashMap<Integer,Integer> petSpriteBindingByItem=new LinkedHashMap<>();
    private Integer playerNpcTransformId; // null = normal player appearance
    private final DevProtocolTrace trace=new DevProtocolTrace();

    Integer petParticleSelector(){ return petParticleSelector; }
    void setPetParticleSelector(Integer v){
        if(v!=null && (v<0||v>255)) throw new IllegalArgumentException("particle selector 0..255");
        petParticleSelector=v;
    }
    boolean petFollowFrozen(){ return petFollowFrozen; }
    void setPetFollowFrozen(boolean v){ petFollowFrozen=v; }
    Long petFollowDelayMs(){ return petFollowDelayMs; }
    void setPetFollowDelayMs(Long v){
        if(v!=null && (v<100L||v>5000L)) throw new IllegalArgumentException("follow delay 100..5000ms");
        petFollowDelayMs=v;
    }
    void resetPetMovement(){ petFollowFrozen=false; petFollowDelayMs=null; }

    boolean hasCombatAnimationOverride(int weaponId){ return combatAnimationByWeapon.containsKey(weaponId); }
    Integer combatAnimationOverride(int weaponId){ return combatAnimationByWeapon.get(weaponId); }
    void setCombatAnimationOverride(int weaponId,Integer animationId){
        if(animationId==null){ combatAnimationByWeapon.remove(weaponId); return; }
        if(animationId<-1||animationId>0xffff) throw new IllegalArgumentException("animation -1..65535");
        combatAnimationByWeapon.put(weaponId,animationId);
    }

    Integer petNpcBinding(int itemId){ return petNpcBindingByItem.get(itemId); }
    void setPetNpcBinding(int itemId,Integer npcId){
        if(npcId==null){ petNpcBindingByItem.remove(itemId); return; }
        if(npcId<0||npcId>16383) throw new IllegalArgumentException("npc binding 0..16383");
        petNpcBindingByItem.put(itemId,npcId);
    }
    Integer petSpriteBinding(int itemId){ return petSpriteBindingByItem.get(itemId); }
    void setPetSpriteBinding(int itemId,Integer previewItemId){
        if(previewItemId==null){ petSpriteBindingByItem.remove(itemId); return; }
        if(previewItemId<0) throw new IllegalArgumentException("sprite preview item >=0");
        petSpriteBindingByItem.put(itemId,previewItemId);
    }
    Map<Integer,Integer> petSpriteBindings(){ return new LinkedHashMap<>(petSpriteBindingByItem); }
    Map<Integer,Integer> petNpcBindings(){ return new LinkedHashMap<>(petNpcBindingByItem); }
    void clearPetBindings(){ petNpcBindingByItem.clear(); petSpriteBindingByItem.clear(); }
    void clearPetBinding(int itemId){ petNpcBindingByItem.remove(itemId); petSpriteBindingByItem.remove(itemId); }

    Integer playerNpcTransformId(){ return playerNpcTransformId; }
    void setPlayerNpcTransformId(Integer npcId){
        // Exact appearance transport is u16, but the exact-current NPC actor space
        // used by this client family is bounded to 14-bit definition ids. Keep the
        // dev tool fail-closed rather than making an invalid definition crash likely.
        if(npcId!=null && (npcId<0||npcId>16383)) throw new IllegalArgumentException("npc transform 0..16383");
        playerNpcTransformId=npcId;
    }

    DevProtocolTrace trace(){ return trace; }

    void resetAll(){
        petParticleSelector=null;
        petFollowFrozen=false;
        petFollowDelayMs=null;
        combatAnimationByWeapon.clear();
        playerNpcTransformId=null;
        clearPetBindings();
        trace.clear();
    }

    String summary(){
        return "petFx="+(petParticleSelector==null?"AUTO":petParticleSelector)+
            " petFollow="+(petFollowFrozen?"FROZEN":"LIVE")+
            " petDelay="+(petFollowDelayMs==null?"NORMAL":petFollowDelayMs+"ms")+
            " playerMorph="+(playerNpcTransformId==null?"NORMAL":playerNpcTransformId)+
            " combatAnimOverrides="+combatAnimationByWeapon+
            " petNpcBindings="+petNpcBindingByItem+" petSpriteBindings="+petSpriteBindingByItem+
            " "+trace.summary();
    }
}

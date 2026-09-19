package spk.local;

import java.util.*;

/**
 * LocalLab weapon presentation registry.
 *
 * R2 imports attack animations for the statically useful/current resolver rows,
 * while retaining explicit evidence boundaries.  Mechanics for R2 candidate rows
 * use LOCAL_HARNESS fallback speed/range solely so the dummy loop can execute.
 * Damage/accuracy remain fixture/deferred exactly as before.
 */
final class CombatWeaponRepository {
    private static final LinkedHashMap<Integer,CombatWeaponProfile> PROFILES = build();

    private CombatWeaponRepository() {}

    static CombatWeaponProfile resolve(int itemId){ return PROFILES.get(itemId); }
    static Collection<CombatWeaponProfile> all(){ return Collections.unmodifiableCollection(PROFILES.values()); }
    static int count(){ return PROFILES.size(); }
    static int r2AttackAuthorityCount(){return WeaponAttackAuthorityRepository.count();}
    static int r2ProvenRuntimeCount(){return WeaponAttackAuthorityRepository.provenRuntimeCount();}

    static int withProductionPose(){
        int n=0; for(CombatWeaponProfile p:PROFILES.values()) if(p.certainty==CombatWeaponProfile.Certainty.PRODUCTION_POSE_ONLY) n++; return n;
    }
    static int withInferredPresentation(){
        int n=0; for(CombatWeaponProfile p:PROFILES.values()) if(p.certainty==CombatWeaponProfile.Certainty.INFERRED_PRESENTATION) n++; return n;
    }
    static int mechanicsResolved(){
        int n=0; for(CombatWeaponProfile p:PROFILES.values()) if(p.mechanicsResolved()) n++; return n;
    }
    static int currentEquipActionWeaponCount(){
        int n=0;
        for(CombatWeaponProfile p:PROFILES.values()) if(ItemCatalog.canWieldOrWear(p.itemId)) n++;
        return n;
    }

    private static LinkedHashMap<Integer,CombatWeaponProfile> build(){
        ArrayList<ItemCatalog.Meta> items=new ArrayList<>(ItemCatalog.all());
        items.sort(Comparator.comparingInt(m->m.id));
        LinkedHashMap<Integer,CombatWeaponProfile> out=new LinkedHashMap<>();
        for(ItemCatalog.Meta item:items){
            EquipmentMetadataRepository.Meta eq=EquipmentMetadataRepository.resolve(item.id);
            if(eq==null || eq.slot!=EquipmentSlot.WEAPON) continue;
            out.put(item.id,baseProfile(item,item.id,eq.evidence));
        }

        // R2 presentation activation.  This intentionally does NOT import the two
        // Scorching 15624 rows: that static candidate was live-crash rejected.
        for(WeaponAttackAuthorityRepository.Row r:WeaponAttackAuthorityRepository.all()){
            ItemCatalog.Meta item=ItemDefinitionRepository.get(r.itemId);
            if(item==null) continue;
            CombatWeaponProfile old=out.get(r.itemId);
            if(old==null){
                EquipmentMetadataRepository.Meta eq=EquipmentMetadataRepository.resolveKnownSlot(r.itemId,EquipmentSlot.WEAPON);
                String ev=eq==null?"R2_WIELD_UNIVERSE_WEAPON_SLOT":eq.evidence;
                old=baseProfile(item,r.itemId,ev);
            }
            out.put(r.itemId,new CombatWeaponProfile(r.itemId,old.itemName,old.combatInterfaceRoot,r.damageClass,
                r.speedTicks,r.range,r.attackAnimation,r.projectileId,r.gfxId,-1,old.certainty,
                old.evidence+"+R2_ATTACK_ANIM="+r.attackAnimation+"+ATTACK_AUTHORITY="+r.attackAuthority+
                "+SPEED_AUTHORITY="+r.speedAuthority+"+RANGE_AUTHORITY="+r.rangeAuthority+"+R2_EVIDENCE="+r.evidence));
        }

        // Existing local gameplay authority remains stronger than R2 fallback timing.
        putM2(out,28526,CombatWeaponProfile.DamageClass.MELEE,3,1,15552,-1,-1,
            "M2_BLOODREND+ATTACK_ANIM_15552_STRONG_CACHE_NAME_MATCH+SPEED_3T_USER_AUTHORITY+RANGE_1_LOCAL_HARNESS");
        // Scorching remains a crash-guarded exception. CombatEngine suppresses 15624
        // in normal play while still allowing its fixture damage loop and explicit dev trial.
        putM2(out,28860,CombatWeaponProfile.DamageClass.RANGED,3,10,15624,4136,4135,
            "M2_SCORCHING_BOW_I+ATTACK_ANIM_15624_STATIC_CANDIDATE_LIVE_REJECTED+GFX_4135+PROJECTILE_4136_CACHE_CANDIDATE+SPEED_3T_USER_AUTHORITY+RANGE_10_LOCAL_HARNESS_NOT_PRODUCTION");
        return out;
    }

    private static CombatWeaponProfile baseProfile(ItemCatalog.Meta item,int itemId,String slotEvidence){
        int root=CombatInterfaceRepository.forWeapon(itemId);
        CombatWeaponProfile.DamageClass dc=damageClassFromRoot(root);
        WeaponPoseRepository.Resolution pose=WeaponPoseRepository.resolve(itemId);
        CombatWeaponProfile.Certainty certainty;
        String evidence="EQUIPMENT_SLOT="+slotEvidence+"+CLIENT_COMBAT_ROOT="+root;
        if(pose!=null){
            String pe=pose.evidence==null?"":pose.evidence;
            if(pe.contains("PROVEN_PRODUCTION_POSE")||pe.contains("V902_PRODUCTION_APPEARANCE"))
                certainty=CombatWeaponProfile.Certainty.PRODUCTION_POSE_ONLY;
            else certainty=CombatWeaponProfile.Certainty.INFERRED_PRESENTATION;
            evidence += "+POSE="+pe;
        } else {
            WeaponPoseRepository.Resolution semantic=WeaponPoseRepository.resolveSemanticFamily(itemId);
            if(semantic!=null){certainty=CombatWeaponProfile.Certainty.INFERRED_PRESENTATION;evidence += "+POSE="+semantic.evidence;}
            else {certainty=CombatWeaponProfile.Certainty.CUSTOM_UNKNOWN;evidence += "+POSE=UNRESOLVED";}
        }
        return new CombatWeaponProfile(itemId,item.name,root,dc,-1,-1,-1,-1,-1,-1,certainty,
            evidence+"+ATTACK_SEMANTICS=UNRESOLVED_SERVER_AUTHORITY");
    }

    private static void putM2(LinkedHashMap<Integer,CombatWeaponProfile> out,int id,CombatWeaponProfile.DamageClass dc,int speed,int range,int anim,int projectile,int gfx,String evidence){
        CombatWeaponProfile old=out.get(id);
        if(old==null){
            ItemCatalog.Meta item=ItemDefinitionRepository.get(id);
            if(item==null)throw new IllegalStateException("M2 weapon missing from item corpus: "+id);
            old=baseProfile(item,id,"M2_EXPLICIT_WEAPON_SLOT");
        }
        out.put(id,new CombatWeaponProfile(id,old.itemName,old.combatInterfaceRoot,dc,speed,range,anim,projectile,gfx,-1,old.certainty,old.evidence+"+"+evidence));
    }

    private static CombatWeaponProfile.DamageClass damageClassFromRoot(int root){
        if(root==CombatInterfaceRepository.BOW || root==CombatInterfaceRepository.CROSSBOW || root==CombatInterfaceRepository.THROWN)
            return CombatWeaponProfile.DamageClass.RANGED;
        if(root==CombatInterfaceRepository.STAFF)
            return CombatWeaponProfile.DamageClass.MAGIC;
        if(root==CombatInterfaceRepository.UNARMED) return CombatWeaponProfile.DamageClass.UNKNOWN;
        return CombatWeaponProfile.DamageClass.MELEE;
    }
}

package spk.local;

/**
 * Combat-semantic profile kept separate from the packet-81 hold/walk pose profile.
 * Unknown server-authoritative fields stay -1; M1 never substitutes invented values.
 */
final class CombatWeaponProfile {
    enum DamageClass { MELEE, RANGED, MAGIC, UNKNOWN }
    enum Certainty { CURRENT_CLIENT_PRESENTATION, PRODUCTION_POSE_ONLY, INFERRED_PRESENTATION, CUSTOM_UNKNOWN }

    final int itemId;
    final String itemName;
    final int combatInterfaceRoot;
    final DamageClass damageClassCandidate;
    final int attackSpeedTicks;       // -1 until production/server evidence exists
    final int attackRange;            // -1 until production/server evidence exists
    final int attackAnimation;        // -1 until production observation/static server metadata exists
    final int projectileId;           // -1 unknown/none unresolved
    final int gfxId;                  // -1 unknown/none unresolved
    final int specialCost;            // -1 unknown/unsupported unresolved
    final Certainty certainty;
    final String evidence;

    CombatWeaponProfile(int itemId,String itemName,int combatInterfaceRoot,DamageClass damageClassCandidate,
                        int attackSpeedTicks,int attackRange,int attackAnimation,int projectileId,int gfxId,int specialCost,
                        Certainty certainty,String evidence){
        this.itemId=itemId; this.itemName=itemName; this.combatInterfaceRoot=combatInterfaceRoot;
        this.damageClassCandidate=damageClassCandidate; this.attackSpeedTicks=attackSpeedTicks; this.attackRange=attackRange;
        this.attackAnimation=attackAnimation; this.projectileId=projectileId; this.gfxId=gfxId; this.specialCost=specialCost;
        this.certainty=certainty; this.evidence=evidence;
    }

    boolean hasAttackAnimation(){ return attackAnimation>=0; }
    boolean mechanicsResolved(){ return attackSpeedTicks>0 && attackRange>0 && attackAnimation>=0; }

    @Override public String toString(){
        return "CombatWeaponProfile{item="+itemId+",name="+itemName+",root="+combatInterfaceRoot+
            ",class="+damageClassCandidate+",speed="+attackSpeedTicks+",range="+attackRange+
            ",attackAnim="+attackAnimation+",projectile="+projectileId+",gfx="+gfxId+
            ",specCost="+specialCost+",certainty="+certainty+",evidence="+evidence+"}";
    }
}

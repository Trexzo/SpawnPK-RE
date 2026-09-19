package spk.local;

/** TSV report generator for the M1 combat weapon audit. */
public final class CombatWeaponCoverageReport {
    public static void main(String[] args){
        System.out.println("itemId\tname\tcombatRoot\tdamageClassCandidate\tattackSpeedTicks\tattackRange\tattackAnimation\tprojectileId\tgfxId\tspecialCost\tcertainty\tevidence");
        for(CombatWeaponProfile p:CombatWeaponRepository.all()){
            if(!ItemCatalog.canWieldOrWear(p.itemId)) continue; // exact 436 current equip-action weapon candidates
            System.out.println(p.itemId+"\t"+clean(p.itemName)+"\t"+p.combatInterfaceRoot+"\t"+p.damageClassCandidate+"\t"+
                p.attackSpeedTicks+"\t"+p.attackRange+"\t"+p.attackAnimation+"\t"+p.projectileId+"\t"+p.gfxId+"\t"+p.specialCost+"\t"+p.certainty+"\t"+clean(p.evidence));
        }
    }
    private static String clean(String s){return s==null?"":s.replace('\t',' ').replace('\n',' ');}
}

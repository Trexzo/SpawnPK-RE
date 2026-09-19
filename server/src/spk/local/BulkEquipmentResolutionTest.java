package spk.local;

import java.util.*;

/** v5.2.3 current-catalogue bulk equipment reconciliation audit. */
public final class BulkEquipmentResolutionTest {
    public static void main(String[] args) {
        int candidates=0,resolved=0;
        EnumMap<EquipmentSlot,Integer> slots=new EnumMap<>(EquipmentSlot.class);
        List<String> unresolved=new ArrayList<>();
        for (ItemCatalog.Meta item: ItemCatalog.all()) {
            boolean candidate=ItemCatalog.canWieldOrWear(item.id);
            if (!candidate) continue;
            candidates++;
            EquipmentMetadataRepository.Meta m=EquipmentMetadataRepository.resolve(item.id);
            if (m==null) unresolved.add(item.id+" "+item.name+" actions="+Arrays.toString(item.actions));
            else { resolved++; slots.merge(m.slot,1,Integer::sum); }
        }
        // Candidate count intentionally includes clone/fullClone/equipClone inherited equip actions.
        require(candidates > 1148,"effective equip candidate count unexpectedly low: "+candidates);
        require(unresolved.isEmpty(),"unresolved current equip-action rows: "+unresolved);
        require(candidates==EquipmentMetadataRepository.currentActionCandidateCount(),"candidate count mismatch repo="+EquipmentMetadataRepository.currentActionCandidateCount()+" scan="+candidates);
        require(resolved==EquipmentMetadataRepository.currentActionResolvedCount(),"resolved count mismatch repo="+EquipmentMetadataRepository.currentActionResolvedCount()+" scan="+resolved);
        require(EquipmentMetadataRepository.productionObservedCount()==188,"production-observed equipment slot count changed: "+EquipmentMetadataRepository.productionObservedCount());

        expect(21701,EquipmentSlot.WEAPON,true);   // Phantom scythe -> clone 1419 Scythe
        expect(28021,EquipmentSlot.WEAPON,true);   // Corrupted scythe of vitur
        expect(23984,EquipmentSlot.AMULET,false);  // Wanderer's amulet (i) -> clone Salve amulet
        expect(23630,EquipmentSlot.RING,false);    // Wanderer's ring (i)
        expect(21956,EquipmentSlot.AMMO,false);    // Easter icon -> Collector icon family
        expect(27245,EquipmentSlot.CAPE,false);    // Scroll sack
        expect(23141,EquipmentSlot.HEAD,false);
        expect(23142,EquipmentSlot.CHEST,false);
        expect(23143,EquipmentSlot.LEGS,false);
        expect(24023,EquipmentSlot.WEAPON,true);
        expect(24024,EquipmentSlot.WEAPON,true);

        EquipmentMetadataRepository.Meta phantom=EquipmentMetadataRepository.resolve(21701);
        require(phantom.pose==EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY,"phantom scythe pose family");
        EquipmentMetadataRepository.Meta corrupt=EquipmentMetadataRepository.resolve(28021);
        require(corrupt.pose==EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY,"corrupted scythe pose family");

        System.out.println("V523_BULK_EQUIPMENT_RECONCILIATION_PASS candidates="+candidates+" resolved="+resolved+" unresolved=0 slots="+slots);
    }

    private static void expect(int id,EquipmentSlot slot,boolean twoHanded) {
        EquipmentMetadataRepository.Meta m=EquipmentMetadataRepository.resolve(id);
        require(m!=null,"item "+id+" unresolved");
        require(m.slot==slot,"item "+id+" slot "+m.slot+" != "+slot+" evidence="+m.evidence);
        if (twoHanded) require(m.twoHanded,"item "+id+" should be two-handed evidence="+m.evidence);
    }
    private static void require(boolean ok,String msg) { if(!ok) throw new AssertionError(msg); }
}

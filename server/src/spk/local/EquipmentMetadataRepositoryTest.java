package spk.local;

/** Static audit of slot resolution and pose-profile separation. */
public final class EquipmentMetadataRepositoryTest {
    public static void main(String[] args) throws Exception {
        EquipmentMetadataRepository.Meta bloodrend = EquipmentMetadataRepository.resolve(28526);
        if (bloodrend == null) throw new AssertionError("Bloodrend unresolved");
        if (bloodrend.slot != EquipmentSlot.WEAPON || bloodrend.appearanceSlot != 3) throw new AssertionError("Bloodrend slot="+bloodrend.appearanceSlot);
        if (!bloodrend.twoHanded) throw new AssertionError("Bloodrend twoHanded=false");
        if (bloodrend.pose != EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY) throw new AssertionError("Bloodrend pose");

        EquipmentMetadataRepository.Meta sanguine = EquipmentMetadataRepository.resolve(28525);
        EquipmentMetadataRepository.Meta vitur = EquipmentMetadataRepository.resolve(21566);
        if (sanguine == null || vitur == null) throw new AssertionError("Scythe clone family unresolved");
        if (sanguine.pose != bloodrend.pose || vitur.pose != bloodrend.pose) throw new AssertionError("Scythe profile not reusable");

        EquipmentMetadataRepository.Meta whip = EquipmentMetadataRepository.resolve(4151);
        if (whip == null || whip.slot != EquipmentSlot.WEAPON || whip.appearanceSlot != 3 || whip.twoHanded) throw new AssertionError("Whip metadata");

        EquipmentMetadataRepository.Meta dyedVitur = EquipmentMetadataRepository.resolve(24023);
        if (dyedVitur == null || dyedVitur.slot != EquipmentSlot.WEAPON || !dyedVitur.twoHanded)
            throw new AssertionError("Dyed scythe 24023 slot inheritance="+(dyedVitur==null?"null":dyedVitur.slot));
        if (dyedVitur.pose != EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY)
            throw new AssertionError("Dyed scythe 24023 did not inherit scythe pose");


        // Current custom metadata exposes Wield on this cape. Bulk reconciliation must
        // let the semantic CAPE family win before the Wield->weapon fallback.
        if (!ItemDefinitionRepository.hasWieldAction(21026)) throw new AssertionError("fixture no longer has Wield");
        EquipmentMetadataRepository.Meta infernalCape=EquipmentMetadataRepository.resolve(21026);
        if (infernalCape==null || infernalCape.slot!=EquipmentSlot.CAPE)
            throw new AssertionError("Wield action overrode cape slot for 21026: "+(infernalCape==null?"null":infernalCape.slot));

        // Name-only scythe kit must not become a weapon family member.
        if (EquipmentMetadataRepository.resolve(28524) != null)
            throw new AssertionError("scythe kit incorrectly resolved as equipment");

        EquipmentMetadataRepository.Meta runePouch = EquipmentMetadataRepository.resolve(27475);
        if (runePouch == null || runePouch.slot != EquipmentSlot.AMMO)
            throw new AssertionError("data-backed override 27475=" + (runePouch == null ? "null" : runePouch.slot));
        if (!EquipmentMetadataRepository.resolutionEvidence(27475).contains("CURRENT_ITEM_AMBIGUITY_OVERRIDE"))
            throw new AssertionError("override provenance=" + EquipmentMetadataRepository.resolutionEvidence(27475));
        // v5.2 generic clone/equipClone slot inheritance fixtures.
        EquipmentMetadataRepository.Meta ultimate = EquipmentMetadataRepository.resolve(27034);
        if (ultimate == null || ultimate.slot != EquipmentSlot.HEAD || ultimate.coverage != EquipmentMetadataRepository.Coverage.FULL_HELM)
            throw new AssertionError("Ultimate slayer helmet slot inheritance="+(ultimate==null?"null":ultimate.slot));
        EquipmentMetadataRepository.Meta wanderer = EquipmentMetadataRepository.resolve(27486);
        if (wanderer == null || wanderer.slot != EquipmentSlot.FEET)
            throw new AssertionError("Wanderer boots slot inheritance="+(wanderer==null?"null":wanderer.slot));

        int[] pose = bloodrend.pose.toArray();
        if (pose.length != 7) throw new AssertionError("pose fields="+pose.length);
        int[] expected={15692,823,1146,820,821,822,1210};
        for(int i=0;i<7;i++) if(pose[i]!=expected[i]) throw new AssertionError("pose["+i+"]="+pose[i]);
        if (!bloodrend.pose.hasWeaponSpecificField(EquipmentPoseProfile.STAND)) throw new AssertionError("stand evidence mask");
        if (!bloodrend.pose.weaponSpecificComplete()) throw new AssertionError("production scythe profile not marked complete");

        System.out.println("V521_EQUIPMENT_METADATA_PASS"
                         + " bloodrendSlot=WEAPON twoHanded=true scytheProfileReusable=true"
                         + " pose7=15692,823,1146,820,821,822,1210 specificMask=0x7f complete=true"
                         + " ultimate27034=HEAD_via_equipClone21724 wanderer27486=FEET_via_clone22830 dyedVitur24023=WEAPON_via_clone21566_scythePose"
                         + " wieldCape21026ResolvedCAPE=true scytheKit28524Rejected=true override27475DataBackedAMMO=true");
    }
}

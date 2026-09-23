package spk.local;

public final class VoidglassR3CustomContentTest {
    public static void main(String[] args) {
        VoidglassR3CustomContent.ensureRuntimePetMapping();

        if (VoidglassR3CustomContent.ITEM_ID != 29999 ||
            VoidglassR3CustomContent.DEFAULT_NPC_ID != 12000)
            throw new AssertionError("identity");

        if (VoidglassR3CustomContent.CANDIDATES.length != 1)
            throw new AssertionError(
                "native compositor candidates were not retired: " +
                VoidglassR3CustomContent.CANDIDATES.length
            );

        if (!ItemDefinitionRepository.exists(29999))
            throw new AssertionError("server item missing");
        if (!"Voidglass Nistirio".equals(ItemDefinitionRepository.name(29999)))
            throw new AssertionError(
                "item name=" + ItemDefinitionRepository.name(29999)
            );
        if (!"Drop".equalsIgnoreCase(
                ItemActionResolver.inventoryOption5Semantic(29999)))
            throw new AssertionError("drop action");

        CustomAssetAuthoringRepository.Asset asset =
            CustomAssetAuthoringRepository.requireContentKey("voidglass_nistirio");
        if (asset.modelId != 79999 ||
            asset.textureId != 278 ||
            asset.mappingTriangles != 12 ||
            asset.modelFamily !=
                CustomAssetAuthoringRepository.ModelFamily.LEGACY_TEXTURED_SKINNED ||
            asset.skinMode != CustomAssetAuthoringRepository.SkinMode.RIGID_ONE_HOT ||
            asset.hierarchyMode !=
                CustomAssetAuthoringRepository.HierarchyMode.GUARDED_RIGID)
            throw new AssertionError("v308 custom authoring contract");

        PetDefinitionRepository.Def d = PetDefinitionRepository.get(29999);
        if (d == null ||
            d.npcId != 12000 ||
            d.standAnim != 1662 ||
            d.walkAnim != 1663 ||
            !"79999".equals(d.models) ||
            !d.provenance.startsWith("CUSTOM_LOCALLAB"))
            throw new AssertionError("mapping=" + d);

        if (PetDefinitionRepository.get(32760) != null)
            throw new AssertionError("legacy invalid item mapping still present");

        VoidglassR3CustomContent.Candidate candidate =
            VoidglassR3CustomContent.defaultCandidate();
        if (!"79999".equals(candidate.models))
            throw new AssertionError("custom model=" + candidate.models);
        if (VoidglassR3CustomContent.PROC_GFX != 5042)
            throw new AssertionError("proc gfx=" + VoidglassR3CustomContent.PROC_GFX);

        System.out.println(
            "V5185_VOIDGLASS_R3_CUSTOM_CONTENT_PASS " +
            "item=29999 npc=12000 model=79999 texture=278 " +
            "legacyModelFamily=true rigidOneHot=true " +
            "nativeCompositorCandidatesRetired=true reflectionMutation=false"
        );
    }

    private VoidglassR3CustomContentTest() {}
}

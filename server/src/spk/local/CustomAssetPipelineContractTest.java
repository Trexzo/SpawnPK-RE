package spk.local;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class CustomAssetPipelineContractTest {
    public static void main(String[] args) throws Exception {
        CustomAssetAuthoringRepository.Asset asset =
            CustomAssetAuthoringRepository.requireContentKey("voidglass_nistirio");

        if (asset.kind != CustomAssetAuthoringRepository.Kind.PET)
            throw new AssertionError("kind=" + asset.kind);
        if (asset.itemId != 29999 || asset.npcId != 12000 || asset.modelId != 79999)
            throw new AssertionError("identity");
        if (asset.textureId != 278 || asset.mappingTriangles != 12)
            throw new AssertionError("texture/mapping");
        if (asset.animationMode != CustomAssetAuthoringRepository.AnimationMode.NONE ||
            asset.standAnim != -1 || asset.walkAnim != -1 ||
            asset.frameGroupId != -1 || asset.sequenceId != -1)
            throw new AssertionError("unproven animation binding was retained");
        if (asset.modelFamily !=
            CustomAssetAuthoringRepository.ModelFamily.LEGACY_TEXTURED_SKINNED)
            throw new AssertionError("model family");
        if (asset.skinMode != CustomAssetAuthoringRepository.SkinMode.RIGID_ONE_HOT)
            throw new AssertionError("skin mode");
        if (asset.hierarchyMode !=
            CustomAssetAuthoringRepository.HierarchyMode.GUARDED_RIGID)
            throw new AssertionError("hierarchy");
        if (!asset.provenance.startsWith("CUSTOM_LOCALLAB"))
            throw new AssertionError("provenance");

        CustomAssetAuthoringRepository.Asset standaloneItem =
            new CustomAssetAuthoringRepository.Asset(
                CustomAssetAuthoringRepository.Kind.ITEM,
                "test_item_no_npc",
                29998,
                -1,
                "Test Item",
                79998,
                CustomAssetAuthoringRepository.ModelContext.PRIMARY,
                -1,
                0,
                CustomAssetAuthoringRepository.ModelFamily.LEGACY_TEXTURED_SKINNED,
                CustomAssetAuthoringRepository.SkinMode.RIGID_ONE_HOT,
                CustomAssetAuthoringRepository.HierarchyMode.GUARDED_RIGID,
                CustomAssetAuthoringRepository.TextureMode.NONE,
                CustomAssetAuthoringRepository.AnimationMode.NONE,
                -1,
                -1,
                -1,
                -1,
                -1,
                CustomAssetAuthoringRepository.GfxMode.NONE,
                CustomAssetAuthoringRepository.GfxModelContext.EXACT_CURRENT,
                -1,
                null,
                false,
                CustomAssetAuthoringRepository.EquipmentCoverage.NONE,
                "CUSTOM_LOCALLAB_TEST_ITEM"
            );
        if (standaloneItem.npcId != -1 || standaloneItem.npcSize != -1)
            throw new AssertionError("non-pet sentinel contract");

        boolean nonPetNpcSizeRejected = false;
        try {
            new CustomAssetAuthoringRepository.Asset(
                CustomAssetAuthoringRepository.Kind.EQUIPMENT,
                "test_equipment_bad_npc_size",
                29997,
                -1,
                "Test Equipment",
                79997,
                CustomAssetAuthoringRepository.ModelContext.PRIMARY,
                -1,
                0,
                CustomAssetAuthoringRepository.ModelFamily.LEGACY_TEXTURED_SKINNED,
                CustomAssetAuthoringRepository.SkinMode.RIGID_ONE_HOT,
                CustomAssetAuthoringRepository.HierarchyMode.GUARDED_RIGID,
                CustomAssetAuthoringRepository.TextureMode.NONE,
                CustomAssetAuthoringRepository.AnimationMode.NONE,
                -1,
                -1,
                -1,
                -1,
                -1,
                CustomAssetAuthoringRepository.GfxMode.NONE,
                CustomAssetAuthoringRepository.GfxModelContext.EXACT_CURRENT,
                1,
                EquipmentSlot.HEAD,
                false,
                CustomAssetAuthoringRepository.EquipmentCoverage.NONE,
                "CUSTOM_LOCALLAB_TEST_EQUIPMENT"
            );
        } catch (IllegalArgumentException expected) {
            nonPetNpcSizeRejected =
                expected.getMessage().contains("non-pet asset must use npcSize=-1 sentinel");
        }
        if (!nonPetNpcSizeRejected)
            throw new AssertionError("non-pet npcSize drift was not rejected");

        CustomDefinitionOverlayRepository.Overlay item =
            CustomDefinitionOverlayRepository.item(29999);
        CustomDefinitionOverlayRepository.Overlay npc =
            CustomDefinitionOverlayRepository.npc(12000);
        if (item == null || !"79999".equals(item.field("modelId")))
            throw new AssertionError("item overlay");
        if (npc == null || !"79999".equals(npc.field("models")))
            throw new AssertionError("npc overlay");
        if (!"-1".equals(npc.field("standAnim")) ||
            !"-1".equals(npc.field("walkAnim")))
            throw new AssertionError("npc animation overlay");

        if (!asset.name.equals(item.field("name")) ||
            !asset.name.equals(npc.field("name")))
            throw new AssertionError("definition name projection");
        if (!"true".equals(npc.field("pet")) ||
            !String.valueOf(asset.npcSize).equals(npc.field("size")))
            throw new AssertionError("npc semantic projection");

        assertProjectionRejected(
            asset,
            withField(item, "name", "Drifted item name"),
            npc,
            "ITEM overlay name mismatch"
        );
        assertProjectionRejected(
            asset,
            item,
            withField(npc, "name", "Drifted NPC name"),
            "NPC overlay name mismatch"
        );
        assertProjectionRejected(
            asset,
            item,
            withField(npc, "models", "79999,80000"),
            "NPC overlay model mismatch"
        );
        assertProjectionRejected(
            asset,
            item,
            withField(npc, "pet", "false"),
            "NPC overlay pet mismatch"
        );
        assertProjectionRejected(
            asset,
            item,
            withField(npc, "size", "2"),
            "NPC overlay size mismatch"
        );

        Map<String,String> itemClone = new LinkedHashMap<>();
        itemClone.put("clone", "100");
        itemClone.put("fullClone", "200");
        if (CustomDefinitionOverlayPolicy.itemCloneSource(itemClone) != 200)
            throw new AssertionError("fullClone precedence");
        if (!CustomDefinitionOverlayPolicy.itemEffectiveOsrs(itemClone, true))
            throw new AssertionError("source osrs inheritance");
        itemClone.put("osrs", "false");
        if (CustomDefinitionOverlayPolicy.itemEffectiveOsrs(itemClone, true))
            throw new AssertionError("explicit osrs override");

        assertRejectedItemField("equipClone");
        assertRejectedItemField("cloneEquip");
        assertRejectedItemField("param_1");
        assertRejectedItemField("unknownField");

        PetDefinitionRepository.Def pet = PetDefinitionRepository.get(29999);
        if (pet == null || pet.npcId != 12000 ||
            pet.standAnim != -1 || pet.walkAnim != -1 ||
            !"79999".equals(pet.models) ||
            !pet.provenance.startsWith("CUSTOM_LOCALLAB"))
            throw new AssertionError("server pet mapping=" + pet);

        String baseSnapshot = validSnapshot(false, true);
        CustomAssetNamespaceSnapshot snapshot =
            CustomAssetNamespaceSnapshot.parse(new StringReader(baseSnapshot));
        CustomAssetNamespacePreflight.Result first =
            CustomAssetNamespacePreflight.run(snapshot);
        CustomAssetNamespacePreflight.Result second =
            CustomAssetNamespacePreflight.run(snapshot);
        if (!first.planSha256.equals(second.planSha256))
            throw new AssertionError("non-deterministic plan hash");
        if (first.claims != 4 || first.references != 1)
            throw new AssertionError(
                "claim/reference counts=" + first.claims + "/" + first.references
            );

        LinkedHashSet<CustomAssetNamespacePreflight.IdKey> sharedTextureClaims =
            new LinkedHashSet<>();
        CustomAssetNamespacePreflight.claimShared(
            snapshot,
            sharedTextureClaims,
            CustomAssetNamespaceSnapshot.Namespace.TEXTURE,
            CustomAssetNamespaceSnapshot.Context.GLOBAL,
            278,
            "shared-atlas:first"
        );
        CustomAssetNamespacePreflight.claimShared(
            snapshot,
            sharedTextureClaims,
            CustomAssetNamespaceSnapshot.Namespace.TEXTURE,
            CustomAssetNamespaceSnapshot.Context.GLOBAL,
            278,
            "shared-atlas:second"
        );
        if (sharedTextureClaims.size() != 1)
            throw new AssertionError(
                "slot-278 shared atlas should remain one namespace allocation"
            );

        LinkedHashSet<CustomAssetNamespacePreflight.IdKey> exclusiveModelClaims =
            new LinkedHashSet<>();
        CustomAssetNamespacePreflight.claim(
            snapshot,
            exclusiveModelClaims,
            CustomAssetNamespaceSnapshot.Namespace.MODEL,
            CustomAssetNamespaceSnapshot.Context.PRIMARY,
            79999,
            "exclusive-model:first"
        );
        boolean duplicateExclusiveClaimRejected = false;
        try {
            CustomAssetNamespacePreflight.claim(
                snapshot,
                exclusiveModelClaims,
                CustomAssetNamespaceSnapshot.Namespace.MODEL,
                CustomAssetNamespaceSnapshot.Context.PRIMARY,
                79999,
                "exclusive-model:second"
            );
        } catch (IllegalStateException expected) {
            duplicateExclusiveClaimRejected =
                expected.getMessage().contains("duplicate custom namespace claim");
        }
        if (!duplicateExclusiveClaimRejected)
            throw new AssertionError("exclusive model claim was incorrectly shareable");

        boolean collisionRejected = false;
        try {
            CustomAssetNamespaceSnapshot collided =
                CustomAssetNamespaceSnapshot.parse(
                    new StringReader(validSnapshot(true, true))
                );
            CustomAssetNamespacePreflight.run(collided);
        } catch (IllegalStateException expected) {
            collisionRejected =
                expected.getMessage().contains("CUSTOM_ASSET_NAMESPACE_COLLISION");
        }
        if (!collisionRejected)
            throw new AssertionError("model namespace collision was not rejected");

        boolean unresolvedGfxContextRejected = false;
        try {
            CustomAssetNamespaceSnapshot missingGfxContext =
                CustomAssetNamespaceSnapshot.parse(
                    new StringReader(validSnapshot(false, false))
                );
            CustomAssetNamespacePreflight.run(missingGfxContext);
        } catch (IllegalStateException expected) {
            unresolvedGfxContextRejected =
                expected.getMessage().contains("UNRESOLVED_GFX_MODEL_CONTEXT");
        }
        if (!unresolvedGfxContextRejected)
            throw new AssertionError("missing GFX model context was not rejected");

        System.out.println(
            "CUSTOM_ASSET_PIPELINE_CONTRACT_PASS " +
            "modelFamily=legacy_textured_skinned " +
            "skinMode=rigid_one_hot " +
            "mappingCapacity=64 " +
            "textureBootstrap=278 " +
            "cloneOrdering=true " +
            "definitionProjectionParity=true " +
            "sharedTextureAtlas=true " +
            "staticUntilAnimationProven=true " +
            "nonPetNpcSizeSentinel=true " +
            "namespaceCollisionRejected=true " +
            "gfxContextRequired=true " +
            "planSha256=" + first.planSha256
        );
    }

    private static void assertProjectionRejected(
        CustomAssetAuthoringRepository.Asset asset,
        CustomDefinitionOverlayRepository.Overlay item,
        CustomDefinitionOverlayRepository.Overlay npc,
        String expectedMessage
    ) {
        boolean rejected = false;
        try {
            CustomAssetNamespacePreflight.validateDefinitionProjection(asset, item, npc);
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage().contains(expectedMessage);
        }
        if (!rejected)
            throw new AssertionError(
                "definition projection drift was not rejected: " + expectedMessage
            );
    }

    private static CustomDefinitionOverlayRepository.Overlay withField(
        CustomDefinitionOverlayRepository.Overlay source,
        String field,
        String value
    ) {
        Map<String,String> fields = new LinkedHashMap<>(source.fields());
        fields.put(field, value);
        Map<String,String> provenance = new LinkedHashMap<>();
        for (String name : fields.keySet())
            provenance.put(name, "CUSTOM_LOCALLAB_TEST_PROJECTION");
        return new CustomDefinitionOverlayRepository.Overlay(
            source.kind, source.id, fields, provenance
        );
    }

    private static void assertRejectedItemField(String field) {
        boolean rejected = false;
        try {
            CustomDefinitionOverlayPolicy.validateField(
                CustomDefinitionOverlayPolicy.Kind.ITEM, field, "1"
            );
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected)
            throw new AssertionError("field should fail closed: " + field);
    }

    private static String validSnapshot(boolean collideModel, boolean includeGfxContext) {
        StringBuilder s = new StringBuilder();
        s.append("clientSha256\t")
            .append(CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256)
            .append('\n');
        s.append("scope\tBASE_PLUS_EXACT_OVERRIDES\n");
        s.append("recordType\tnamespace\tcontext\tvalue\n");
        s.append("CAPACITY\tITEM\tGLOBAL\t30000\n");
        s.append("CAPACITY\tNPC\tGLOBAL\t16384\n");
        s.append("CAPACITY\tMODEL\tPRIMARY\t100000\n");
        s.append("CAPACITY\tTEXTURE\tGLOBAL\t340\n");
        s.append("CAPACITY\tANIMATION\tGLOBAL\t35260\n");
        s.append("CAPACITY\tGFX\tGLOBAL\t7964\n");
        s.append("CAPACITY\tFRAME_GROUP\tGLOBAL\t65536\n");
        s.append("PRESENT\tGFX\tGLOBAL\t5042\n");
        if (includeGfxContext)
            s.append("GFX_CONTEXT\tGFX\tPRIMARY\t5042\n");
        if (collideModel)
            s.append("PRESENT\tMODEL\tPRIMARY\t79999\n");
        return s.toString();
    }

    private CustomAssetPipelineContractTest() {}
}

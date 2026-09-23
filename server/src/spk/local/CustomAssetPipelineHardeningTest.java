package spk.local;

import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

public final class CustomAssetPipelineHardeningTest {
    public static void main(String[] args) throws Exception {
        CustomAssetAuthoringRepository.Asset asset =
            CustomAssetAuthoringRepository.requireContentKey("voidglass_nistirio");
        CustomDefinitionOverlayRepository.Overlay item =
            CustomDefinitionOverlayRepository.item(asset.itemId);
        CustomDefinitionOverlayRepository.Overlay npc =
            CustomDefinitionOverlayRepository.npc(asset.npcId);

        if (item == null || npc == null)
            throw new AssertionError("missing production overlays");

        if (!"null,null,null,null,Drop".equals(item.field("actions")) ||
            !"2086".equals(item.field("zoom")) ||
            !"567,2031".equals(item.field("rotations")) ||
            !"-4,0".equals(item.field("offsets")) ||
            !"0".equals(item.field("zan2d")))
            throw new AssertionError("proven item definition fields");

        CustomAssetNamespacePreflight.validateDefinitionProjection(
            asset,
            item,
            npc
        );

        boolean badActionsRejected = false;
        try {
            CustomDefinitionOverlayPolicy.validateField(
                CustomDefinitionOverlayPolicy.Kind.ITEM,
                "actions",
                "null,null"
            );
        } catch (IllegalArgumentException expected) {
            badActionsRejected =
                expected.getMessage().contains("exactly five");
        }
        if (!badActionsRejected)
            throw new AssertionError("invalid item action vector accepted");

        CustomAssetNamespaceSnapshot snapshot =
            CustomAssetNamespaceSnapshot.parse(
                new StringReader(validSnapshot())
            );

        boolean sharedModelRejected = false;
        try {
            CustomAssetNamespacePreflight.claimShared(
                snapshot,
                new LinkedHashSet<>(),
                CustomAssetNamespaceSnapshot.Namespace.MODEL,
                CustomAssetNamespaceSnapshot.Context.PRIMARY,
                79999,
                "hardening:model"
            );
        } catch (IllegalStateException expected) {
            sharedModelRejected =
                expected.getMessage().contains(
                    "SHARED_CUSTOM_NAMESPACE_UNSUPPORTED"
                );
        }
        if (!sharedModelRejected)
            throw new AssertionError("non-texture shared claim accepted");

        assertSnapshotRejected(
            validSnapshot() +
                "PRESENT\tITEM\tPRIMARY\t29999\n",
            "ITEM namespace requires GLOBAL context"
        );
        assertSnapshotRejected(
            validSnapshot() +
                "PRESENT\tMODEL\tGLOBAL\t79999\n",
            "MODEL namespace requires PRIMARY or OSRS context"
        );

        CustomAssetNamespaceSnapshot uppercase =
            CustomAssetNamespaceSnapshot.parse(
                new StringReader(
                    validSnapshot().replace(
                        CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256,
                        CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256
                            .toUpperCase(Locale.ROOT)
                    )
                )
            );
        if (!snapshot.fingerprintSha256().equals(
                uppercase.fingerprintSha256()))
            throw new AssertionError(
                "client SHA casing changed snapshot fingerprint"
            );

        CustomDefinitionOverlayRepository.Overlay orphan =
            overlay(
                CustomDefinitionOverlayPolicy.Kind.ITEM,
                29998,
                "name",
                "Orphan"
            );
        boolean orphanRejected = false;
        try {
            CustomAssetNamespacePreflight.validateOverlayOwnership(
                CustomAssetAuthoringRepository.all(),
                Collections.singletonList(orphan)
            );
        } catch (IllegalStateException expected) {
            orphanRejected =
                expected.getMessage().contains(
                    "ORPHAN_CUSTOM_DEFINITION_OVERLAY ITEM:29998"
                );
        }
        if (!orphanRejected)
            throw new AssertionError("orphan overlay accepted");

        CustomDefinitionOverlayRepository.Overlay cloneItem =
            overlay(
                CustomDefinitionOverlayPolicy.Kind.ITEM,
                asset.itemId,
                "clone",
                "100"
            );
        CustomDefinitionOverlayRepository.Overlay cloneNpc =
            overlay(
                CustomDefinitionOverlayPolicy.Kind.NPC,
                asset.npcId,
                "clone",
                "200"
            );

        boolean unresolvedCloneRejected = false;
        try {
            CustomAssetNamespacePreflight.referenceDefinitionCloneSources(
                snapshot,
                new LinkedHashSet<>(),
                asset,
                cloneItem,
                cloneNpc
            );
        } catch (IllegalStateException expected) {
            unresolvedCloneRejected =
                expected.getMessage().contains(
                    "UNRESOLVED_EXACT_REFERENCE"
                );
        }
        if (!unresolvedCloneRejected)
            throw new AssertionError("missing clone source accepted");

        CustomAssetNamespaceSnapshot cloneSnapshot =
            CustomAssetNamespaceSnapshot.parse(
                new StringReader(
                    validSnapshot() +
                    "PRESENT\tITEM\tGLOBAL\t100\n" +
                    "PRESENT\tNPC\tGLOBAL\t200\n"
                )
            );
        LinkedHashSet<CustomAssetNamespacePreflight.IdKey> cloneRefs =
            new LinkedHashSet<>();
        CustomAssetNamespacePreflight.referenceDefinitionCloneSources(
            cloneSnapshot,
            cloneRefs,
            asset,
            cloneItem,
            cloneNpc
        );
        if (cloneRefs.size() != 2)
            throw new AssertionError(
                "clone reference count=" + cloneRefs.size()
            );

        boolean unboundSequenceRejected = false;
        try {
            customFrameAsset(-1, -1);
        } catch (IllegalArgumentException expected) {
            unboundSequenceRejected =
                expected.getMessage().contains(
                    "sequenceId must be bound"
                );
        }
        if (!unboundSequenceRejected)
            throw new AssertionError("unbound custom sequence accepted");

        CustomAssetAuthoringRepository.Asset custom =
            customFrameAsset(30009, 1662);
        CustomAssetNamespaceSnapshot animationSnapshot =
            CustomAssetNamespaceSnapshot.parse(
                new StringReader(
                    validSnapshot() +
                    "PRESENT\tANIMATION\tGLOBAL\t1662\n"
                )
            );
        LinkedHashSet<CustomAssetNamespacePreflight.IdKey> claims =
            new LinkedHashSet<>();
        LinkedHashSet<CustomAssetNamespacePreflight.IdKey> refs =
            new LinkedHashSet<>();
        CustomAssetNamespacePreflight.recordAnimationClaimsAndReferences(
            animationSnapshot,
            claims,
            refs,
            custom
        );
        if (claims.size() != 2 || refs.size() != 1)
            throw new AssertionError(
                "custom animation claim/reference split=" +
                claims.size() + "/" + refs.size()
            );

        System.out.println(
            "CUSTOM_ASSET_PIPELINE_HARDENING_PASS " +
            "sharedNamespaceScope=true " +
            "namespaceContextMatrix=true " +
            "overlayOwnership=true " +
            "cloneSourcePreflight=true " +
            "customSequenceBinding=true " +
            "canonicalSnapshotSha=true " +
            "clientDropParity=true " +
            "provenItemSpriteCamera=true"
        );
    }

    private static CustomDefinitionOverlayRepository.Overlay overlay(
        CustomDefinitionOverlayPolicy.Kind kind,
        int id,
        String field,
        String value
    ) {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put(field, value);
        Map<String,String> provenance = new LinkedHashMap<>();
        provenance.put(field, "CUSTOM_LOCALLAB_TEST_HARDENING");
        return new CustomDefinitionOverlayRepository.Overlay(
            kind,
            id,
            fields,
            provenance
        );
    }

    private static CustomAssetAuthoringRepository.Asset customFrameAsset(
        int standAnim,
        int walkAnim
    ) {
        return new CustomAssetAuthoringRepository.Asset(
            CustomAssetAuthoringRepository.Kind.PET,
            "custom_frame_test",
            29998,
            12001,
            "Custom Frame Test",
            79998,
            CustomAssetAuthoringRepository.ModelContext.PRIMARY,
            278,
            12,
            CustomAssetAuthoringRepository.ModelFamily.LEGACY_TEXTURED_SKINNED,
            CustomAssetAuthoringRepository.SkinMode.RIGID_ONE_HOT,
            CustomAssetAuthoringRepository.HierarchyMode.GUARDED_RIGID,
            CustomAssetAuthoringRepository.TextureMode.SLOT_278_ATLAS,
            CustomAssetAuthoringRepository.AnimationMode.CUSTOM_FRAME_GROUP,
            standAnim,
            walkAnim,
            3990,
            30009,
            -1,
            CustomAssetAuthoringRepository.GfxMode.NONE,
            CustomAssetAuthoringRepository.GfxModelContext.EXACT_CURRENT,
            1,
            null,
            false,
            CustomAssetAuthoringRepository.EquipmentCoverage.NONE,
            "CUSTOM_LOCALLAB_TEST_CUSTOM_FRAME"
        );
    }

    private static void assertSnapshotRejected(
        String body,
        String expectedMessage
    ) throws Exception {
        boolean rejected = false;
        try {
            CustomAssetNamespaceSnapshot.parse(
                new StringReader(body)
            );
        } catch (java.io.IOException expected) {
            rejected =
                expected.getMessage().contains(expectedMessage);
        }
        if (!rejected)
            throw new AssertionError(
                "snapshot did not reject: " + expectedMessage
            );
    }

    private static String validSnapshot() {
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
        return s.toString();
    }

    private CustomAssetPipelineHardeningTest() {}
}

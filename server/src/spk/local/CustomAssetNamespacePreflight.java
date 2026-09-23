package spk.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Fail-closed exact-v308 namespace preflight for source-controlled custom authoring.
 *
 * Claimed IDs must be absent from the exact-current census and within its declared
 * capacity. Referenced stock IDs must be present. Existing GFX references additionally
 * require a recovered PRIMARY/OSRS model context in the snapshot.
 */
final class CustomAssetNamespacePreflight {
    static final class Result {
        final int assets;
        final int claims;
        final int references;
        final String snapshotSha256;
        final String definitionSha256;
        final String planSha256;

        Result(
            int assets,
            int claims,
            int references,
            String snapshotSha256,
            String definitionSha256,
            String planSha256
        ) {
            this.assets = assets;
            this.claims = claims;
            this.references = references;
            this.snapshotSha256 = snapshotSha256;
            this.definitionSha256 = definitionSha256;
            this.planSha256 = planSha256;
        }

        String marker() {
            return "CUSTOM_ASSET_NAMESPACE_PREFLIGHT_PASS assets=" + assets +
                " claims=" + claims +
                " references=" + references +
                " clientSha256=" +
                CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256 +
                " snapshotSha256=" + snapshotSha256 +
                " definitionSha256=" + definitionSha256 +
                " planSha256=" + planSha256;
        }
    }

    private static final class IdKey {
        final CustomAssetNamespaceSnapshot.Namespace namespace;
        final CustomAssetNamespaceSnapshot.Context context;
        final int id;

        IdKey(
            CustomAssetNamespaceSnapshot.Namespace namespace,
            CustomAssetNamespaceSnapshot.Context context,
            int id
        ) {
            this.namespace = namespace;
            this.context = context;
            this.id = id;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof IdKey)) return false;
            IdKey k = (IdKey) other;
            return namespace == k.namespace && context == k.context && id == k.id;
        }

        @Override public int hashCode() {
            return Objects.hash(namespace, context, id);
        }

        @Override public String toString() {
            return namespace + ":" + context + ":" + id;
        }
    }

    private CustomAssetNamespacePreflight() {}

    static Result run(CustomAssetNamespaceSnapshot snapshot) {
        List<CustomAssetAuthoringRepository.Asset> assets =
            CustomAssetAuthoringRepository.all();
        LinkedHashSet<IdKey> claims = new LinkedHashSet<>();
        LinkedHashSet<IdKey> references = new LinkedHashSet<>();

        for (CustomAssetAuthoringRepository.Asset asset : assets) {
            validateDefinitionProjection(asset);

            claim(
                snapshot, claims,
                CustomAssetNamespaceSnapshot.Namespace.ITEM,
                CustomAssetNamespaceSnapshot.Context.GLOBAL,
                asset.itemId,
                asset.contentKey + ":item"
            );

            if (asset.kind == CustomAssetAuthoringRepository.Kind.PET) {
                claim(
                    snapshot, claims,
                    CustomAssetNamespaceSnapshot.Namespace.NPC,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.npcId,
                    asset.contentKey + ":npc"
                );
            }

            CustomAssetNamespaceSnapshot.Context modelContext =
                modelContext(asset.modelContext);
            claim(
                snapshot, claims,
                CustomAssetNamespaceSnapshot.Namespace.MODEL,
                modelContext,
                asset.modelId,
                asset.contentKey + ":model"
            );

            if (asset.textureMode != CustomAssetAuthoringRepository.TextureMode.NONE) {
                claim(
                    snapshot, claims,
                    CustomAssetNamespaceSnapshot.Namespace.TEXTURE,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.textureId,
                    asset.contentKey + ":texture"
                );
            }

            if (asset.animationMode ==
                CustomAssetAuthoringRepository.AnimationMode.REUSE_EXISTING) {
                reference(
                    snapshot, references,
                    CustomAssetNamespaceSnapshot.Namespace.ANIMATION,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.standAnim,
                    asset.contentKey + ":standAnim"
                );
                reference(
                    snapshot, references,
                    CustomAssetNamespaceSnapshot.Namespace.ANIMATION,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.walkAnim,
                    asset.contentKey + ":walkAnim"
                );
            } else {
                claim(
                    snapshot, claims,
                    CustomAssetNamespaceSnapshot.Namespace.FRAME_GROUP,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.frameGroupId,
                    asset.contentKey + ":frameGroup"
                );
                claim(
                    snapshot, claims,
                    CustomAssetNamespaceSnapshot.Namespace.ANIMATION,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.sequenceId,
                    asset.contentKey + ":sequence"
                );
                if (asset.standAnim >= 0) {
                    reference(
                        snapshot, references,
                        CustomAssetNamespaceSnapshot.Namespace.ANIMATION,
                        CustomAssetNamespaceSnapshot.Context.GLOBAL,
                        asset.standAnim,
                        asset.contentKey + ":standAnim"
                    );
                }
                if (asset.walkAnim >= 0) {
                    reference(
                        snapshot, references,
                        CustomAssetNamespaceSnapshot.Namespace.ANIMATION,
                        CustomAssetNamespaceSnapshot.Context.GLOBAL,
                        asset.walkAnim,
                        asset.contentKey + ":walkAnim"
                    );
                }
            }

            if (asset.gfxMode ==
                CustomAssetAuthoringRepository.GfxMode.REFERENCE_EXISTING) {
                reference(
                    snapshot, references,
                    CustomAssetNamespaceSnapshot.Namespace.GFX,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.gfxId,
                    asset.contentKey + ":gfx"
                );

                CustomAssetNamespaceSnapshot.Context exactContext =
                    snapshot.gfxModelContext(asset.gfxId);
                if (exactContext == null)
                    throw new IllegalStateException(
                        "UNRESOLVED_GFX_MODEL_CONTEXT contentKey=" +
                        asset.contentKey + " gfxId=" + asset.gfxId
                    );
                if (asset.gfxModelContext !=
                    CustomAssetAuthoringRepository.GfxModelContext.EXACT_CURRENT) {
                    CustomAssetNamespaceSnapshot.Context expected =
                        gfxContext(asset.gfxModelContext);
                    if (exactContext != expected)
                        throw new IllegalStateException(
                            "GFX_MODEL_CONTEXT_MISMATCH contentKey=" +
                            asset.contentKey + " gfxId=" + asset.gfxId +
                            " expected=" + expected + " actual=" + exactContext
                        );
                }
            } else if (asset.gfxMode ==
                CustomAssetAuthoringRepository.GfxMode.CUSTOM) {
                claim(
                    snapshot, claims,
                    CustomAssetNamespaceSnapshot.Namespace.GFX,
                    CustomAssetNamespaceSnapshot.Context.GLOBAL,
                    asset.gfxId,
                    asset.contentKey + ":gfx"
                );
                CustomAssetNamespaceSnapshot.Context gfxContext =
                    gfxContext(asset.gfxModelContext);
                if (gfxContext != modelContext)
                    throw new IllegalStateException(
                        "custom GFX currently must address the authored model in the " +
                        "same exact cache context: " + asset.contentKey
                    );
            }
        }

        String planSha = planSha256(snapshot, assets, claims, references);
        return new Result(
            assets.size(),
            claims.size(),
            references.size(),
            snapshot.fingerprintSha256(),
            CustomDefinitionOverlayRepository.fingerprintSha256(),
            planSha
        );
    }

    private static void validateDefinitionProjection(
        CustomAssetAuthoringRepository.Asset asset
    ) {
        CustomDefinitionOverlayRepository.Overlay item =
            CustomDefinitionOverlayRepository.item(asset.itemId);
        if (item == null)
            throw new IllegalStateException(
                "missing authored ITEM overlay for " + asset.contentKey
            );
        String modelId = item.field("modelId");
        if (modelId == null || Integer.parseInt(modelId) != asset.modelId)
            throw new IllegalStateException(
                "ITEM overlay modelId mismatch for " + asset.contentKey
            );

        if (asset.kind == CustomAssetAuthoringRepository.Kind.PET) {
            CustomDefinitionOverlayRepository.Overlay npc =
                CustomDefinitionOverlayRepository.npc(asset.npcId);
            if (npc == null)
                throw new IllegalStateException(
                    "missing authored NPC overlay for " + asset.contentKey
                );
            if (!integerListContains(npc.field("models"), asset.modelId))
                throw new IllegalStateException(
                    "NPC overlay model mismatch for " + asset.contentKey
                );
            if (!String.valueOf(asset.standAnim).equals(npc.field("standAnim")) ||
                !String.valueOf(asset.walkAnim).equals(npc.field("walkAnim")))
                throw new IllegalStateException(
                    "NPC overlay animation mismatch for " + asset.contentKey
                );
        }
    }

    private static boolean integerListContains(String value, int expected) {
        if (value == null) return false;
        for (String raw : value.split(",")) {
            if (Integer.parseInt(raw.trim()) == expected) return true;
        }
        return false;
    }

    private static void claim(
        CustomAssetNamespaceSnapshot snapshot,
        Set<IdKey> claims,
        CustomAssetNamespaceSnapshot.Namespace namespace,
        CustomAssetNamespaceSnapshot.Context context,
        int id,
        String label
    ) {
        requireInCapacity(snapshot, namespace, context, id, label);
        IdKey key = new IdKey(namespace, context, id);
        if (!claims.add(key))
            throw new IllegalStateException(
                "duplicate custom namespace claim " + key + " at " + label
            );
        if (snapshot.contains(namespace, context, id))
            throw new IllegalStateException(
                "CUSTOM_ASSET_NAMESPACE_COLLISION " + key + " at " + label
            );
    }

    private static void reference(
        CustomAssetNamespaceSnapshot snapshot,
        Set<IdKey> references,
        CustomAssetNamespaceSnapshot.Namespace namespace,
        CustomAssetNamespaceSnapshot.Context context,
        int id,
        String label
    ) {
        requireInCapacity(snapshot, namespace, context, id, label);
        IdKey key = new IdKey(namespace, context, id);
        if (!snapshot.contains(namespace, context, id))
            throw new IllegalStateException(
                "UNRESOLVED_EXACT_REFERENCE " + key + " at " + label
            );
        references.add(key);
    }

    private static void requireInCapacity(
        CustomAssetNamespaceSnapshot snapshot,
        CustomAssetNamespaceSnapshot.Namespace namespace,
        CustomAssetNamespaceSnapshot.Context context,
        int id,
        String label
    ) {
        int capacity = snapshot.capacity(namespace, context);
        if (capacity <= 0)
            throw new IllegalStateException(
                "MISSING_EXACT_CAPACITY " + namespace + ":" + context +
                " at " + label
            );
        if (id < 0 || id >= capacity)
            throw new IllegalStateException(
                "ID_OUTSIDE_EXACT_CAPACITY " + namespace + ":" + context +
                ":" + id + " capacity=" + capacity + " at " + label
            );
    }

    private static CustomAssetNamespaceSnapshot.Context modelContext(
        CustomAssetAuthoringRepository.ModelContext context
    ) {
        return context == CustomAssetAuthoringRepository.ModelContext.PRIMARY
            ? CustomAssetNamespaceSnapshot.Context.PRIMARY
            : CustomAssetNamespaceSnapshot.Context.OSRS;
    }

    private static CustomAssetNamespaceSnapshot.Context gfxContext(
        CustomAssetAuthoringRepository.GfxModelContext context
    ) {
        if (context == CustomAssetAuthoringRepository.GfxModelContext.PRIMARY)
            return CustomAssetNamespaceSnapshot.Context.PRIMARY;
        if (context == CustomAssetAuthoringRepository.GfxModelContext.OSRS)
            return CustomAssetNamespaceSnapshot.Context.OSRS;
        throw new IllegalArgumentException("EXACT_CURRENT has no authored cache context");
    }

    private static String planSha256(
        CustomAssetNamespaceSnapshot snapshot,
        List<CustomAssetAuthoringRepository.Asset> assets,
        Set<IdKey> claims,
        Set<IdKey> references
    ) {
        ArrayList<String> normalizedAssets = new ArrayList<>();
        for (CustomAssetAuthoringRepository.Asset asset : assets)
            normalizedAssets.add(asset.normalized());
        Collections.sort(normalizedAssets);

        ArrayList<String> normalizedClaims = new ArrayList<>();
        for (IdKey key : claims) normalizedClaims.add(key.toString());
        Collections.sort(normalizedClaims);

        ArrayList<String> normalizedReferences = new ArrayList<>();
        for (IdKey key : references) normalizedReferences.add(key.toString());
        Collections.sort(normalizedReferences);

        StringBuilder plan = new StringBuilder();
        plan.append("client=").append(snapshot.clientSha256()).append('\n');
        plan.append("scope=").append(snapshot.scope()).append('\n');
        plan.append("snapshot=").append(snapshot.fingerprintSha256()).append('\n');
        plan.append("definitions=")
            .append(CustomDefinitionOverlayRepository.fingerprintSha256()).append('\n');
        for (String row : normalizedAssets)
            plan.append("ASSET\t").append(row).append('\n');
        for (String row : normalizedClaims)
            plan.append("CLAIM\t").append(row).append('\n');
        for (String row : normalizedReferences)
            plan.append("REFERENCE\t").append(row).append('\n');
        return sha256(plan.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes)
                out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

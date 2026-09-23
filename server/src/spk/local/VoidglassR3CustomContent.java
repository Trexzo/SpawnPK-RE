package spk.local;

/**
 * LOCAL DEVELOPMENT CONTENT ONLY.
 *
 * The rejected R3 native-compositor candidates are retired. The compatibility surface
 * remains so existing server lifecycle code can reference the same item/pet identity,
 * but the visual contract now comes from the exact-v308 custom-asset authoring manifest.
 */
final class VoidglassR3CustomContent {
    static final String NAME = "Voidglass Nistirio";
    static final int LEGACY_R2_ITEM_ID = 32760;
    static final int ITEM_ID = 29999;
    static final int DEFAULT_NPC_ID = 12000;
    static final String PROC_TEXT = "VOIDGLASS RIFT";
    static final String PROVENANCE = "CUSTOM_LOCALLAB_VOIDGLASS_V308_PIPELINE";

    private static final CustomAssetAuthoringRepository.Asset ASSET =
        CustomAssetAuthoringRepository.requireContentKey("voidglass_nistirio");

    static final int PROC_GFX = ASSET.gfxId;

    static final class Candidate {
        final int index, npcId, stand, walk, scale;
        final String name, models, description;

        Candidate(
            int index,
            int npcId,
            String name,
            String models,
            int stand,
            int walk,
            int scale,
            String description
        ) {
            this.index = index;
            this.npcId = npcId;
            this.name = name;
            this.models = models;
            this.stand = stand;
            this.walk = walk;
            this.scale = scale;
            this.description = description;
        }

        String summary() {
            return index + ":" + name +
                " npc=" + npcId +
                " models=" + models +
                " anim=" + stand + "/" + walk +
                " scale=" + scale;
        }
    }

    /**
     * Compatibility-only candidate view. There is now exactly one authored custom model
     * identity; the former four existing-model compositor alternatives are gone.
     */
    static final Candidate[] CANDIDATES = {
        new Candidate(
            1,
            ASSET.npcId,
            ASSET.name,
            String.valueOf(ASSET.modelId),
            ASSET.standAnim,
            ASSET.walkAnim,
            ASSET.npcSize,
            "Exact-v308 custom-pipeline model claim; namespace preflight required"
        )
    };

    static {
        if (ASSET.kind != CustomAssetAuthoringRepository.Kind.PET ||
            ASSET.itemId != ITEM_ID ||
            ASSET.npcId != DEFAULT_NPC_ID ||
            !NAME.equals(ASSET.name) ||
            !PROVENANCE.equals(ASSET.provenance)) {
            throw new ExceptionInInitializerError(
                "Voidglass authoring identity drift: " + ASSET.normalized()
            );
        }
    }

    static Candidate candidate(int index) {
        for (Candidate candidate : CANDIDATES)
            if (candidate.index == index) return candidate;
        return null;
    }

    static Candidate candidateByNpc(int npcId) {
        for (Candidate candidate : CANDIDATES)
            if (candidate.npcId == npcId) return candidate;
        return null;
    }

    static Candidate defaultCandidate() {
        return CANDIDATES[0];
    }

    /**
     * Historical compatibility seam. This method no longer mutates
     * PetDefinitionRepository through reflection; it only verifies that normal repository
     * loading consumed the authored CUSTOM_LOCALLAB PET row.
     */
    static void ensureRuntimePetMapping() {
        if (PetDefinitionRepository.get(LEGACY_R2_ITEM_ID) != null)
            throw new IllegalStateException(
                "legacy invalid Voidglass R2 item mapping is still present"
            );

        PetDefinitionRepository.Def d = PetDefinitionRepository.get(ITEM_ID);
        if (d == null ||
            d.npcId != ASSET.npcId ||
            d.standAnim != ASSET.standAnim ||
            d.walkAnim != ASSET.walkAnim ||
            !String.valueOf(ASSET.modelId).equals(d.models) ||
            !ASSET.provenance.equals(d.provenance)) {
            throw new IllegalStateException(
                "Voidglass authored pet mapping not loaded normally: " + d
            );
        }
    }

    static boolean active(PetState state, NpcEntity pet) {
        return state != null &&
            state.active() &&
            state.itemId() == ITEM_ID &&
            pet != null &&
            pet.pet &&
            pet.petItemId == ITEM_ID &&
            pet.definitionId == ASSET.npcId;
    }

    static String profile() {
        return NAME +
            " item=" + ITEM_ID +
            " npc=" + ASSET.npcId +
            " model=" + ASSET.modelId +
            " modelContext=" + ASSET.modelContext +
            " texture=" + ASSET.textureId +
            " mappings=" + ASSET.mappingTriangles +
            " procGfx=" + PROC_GFX +
            " authority=" + ASSET.provenance;
    }

    static String boundary() {
        return "Voidglass is CUSTOM_LOCALLAB authoring. Model/texture IDs are claims " +
            "that require exact-v308 BASE_PLUS_EXACT_OVERRIDES namespace preflight; " +
            "cache output may target isolated copies only.";
    }

    private VoidglassR3CustomContent() {}
}

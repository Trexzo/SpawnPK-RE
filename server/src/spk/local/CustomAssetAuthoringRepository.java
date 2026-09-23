package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Safe, source-controlled authoring metadata for LocalLab custom assets.
 *
 * This file describes desired identities and exact-v308 compatibility constraints.
 * It never contains or mutates proprietary client/cache bytes.
 */
final class CustomAssetAuthoringRepository {
    static final String EXACT_V308_CLIENT_SHA256 =
        "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6";

    enum Kind { PET, ITEM, EQUIPMENT }
    enum ModelContext { PRIMARY, OSRS }
    enum ModelFamily { LEGACY_TEXTURED_SKINNED }
    enum SkinMode { RIGID_ONE_HOT }
    enum HierarchyMode { GUARDED_RIGID }
    enum TextureMode { NONE, SLOT_278_ATLAS }
    enum AnimationMode { REUSE_EXISTING, CUSTOM_FRAME_GROUP }
    enum GfxMode { NONE, REFERENCE_EXISTING, CUSTOM }
    enum GfxModelContext { EXACT_CURRENT, PRIMARY, OSRS }
    enum EquipmentCoverage { NONE, FULL_HELM, FULL_BODY }

    static final class Asset {
        final Kind kind;
        final String contentKey;
        final int itemId;
        final int npcId;
        final String name;
        final int modelId;
        final ModelContext modelContext;
        final int textureId;
        final int mappingTriangles;
        final ModelFamily modelFamily;
        final SkinMode skinMode;
        final HierarchyMode hierarchyMode;
        final TextureMode textureMode;
        final AnimationMode animationMode;
        final int standAnim;
        final int walkAnim;
        final int frameGroupId;
        final int sequenceId;
        final int gfxId;
        final GfxMode gfxMode;
        final GfxModelContext gfxModelContext;
        final int npcSize;
        final EquipmentSlot equipmentSlot;
        final boolean twoHanded;
        final EquipmentCoverage coverage;
        final String provenance;

        Asset(
            Kind kind,
            String contentKey,
            int itemId,
            int npcId,
            String name,
            int modelId,
            ModelContext modelContext,
            int textureId,
            int mappingTriangles,
            ModelFamily modelFamily,
            SkinMode skinMode,
            HierarchyMode hierarchyMode,
            TextureMode textureMode,
            AnimationMode animationMode,
            int standAnim,
            int walkAnim,
            int frameGroupId,
            int sequenceId,
            int gfxId,
            GfxMode gfxMode,
            GfxModelContext gfxModelContext,
            int npcSize,
            EquipmentSlot equipmentSlot,
            boolean twoHanded,
            EquipmentCoverage coverage,
            String provenance
        ) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.contentKey = requireText(contentKey, "contentKey");
            this.itemId = itemId;
            this.npcId = npcId;
            this.name = requireText(name, "name");
            this.modelId = modelId;
            this.modelContext = Objects.requireNonNull(modelContext, "modelContext");
            this.textureId = textureId;
            this.mappingTriangles = mappingTriangles;
            this.modelFamily = Objects.requireNonNull(modelFamily, "modelFamily");
            this.skinMode = Objects.requireNonNull(skinMode, "skinMode");
            this.hierarchyMode = Objects.requireNonNull(hierarchyMode, "hierarchyMode");
            this.textureMode = Objects.requireNonNull(textureMode, "textureMode");
            this.animationMode = Objects.requireNonNull(animationMode, "animationMode");
            this.standAnim = standAnim;
            this.walkAnim = walkAnim;
            this.frameGroupId = frameGroupId;
            this.sequenceId = sequenceId;
            this.gfxId = gfxId;
            this.gfxMode = Objects.requireNonNull(gfxMode, "gfxMode");
            this.gfxModelContext = Objects.requireNonNull(gfxModelContext, "gfxModelContext");
            this.npcSize = npcSize;
            this.equipmentSlot = equipmentSlot;
            this.twoHanded = twoHanded;
            this.coverage = Objects.requireNonNull(coverage, "coverage");
            this.provenance = requireText(provenance, "provenance");
            validate();
        }

        private void validate() {
            if (itemId < 0 || itemId > 29999)
                throw new IllegalArgumentException("itemId outside exact v308 item table: " + itemId);
            if (kind == Kind.PET && (npcId < 0 || npcId > 16383))
                throw new IllegalArgumentException("pet npcId outside packet-65 range: " + npcId);
            if (kind != Kind.PET && npcId >= 0)
                throw new IllegalArgumentException("non-pet asset must not claim npcId: " + npcId);
            if (modelId < 0)
                throw new IllegalArgumentException("modelId must be non-negative");
            if (mappingTriangles < 0 || mappingTriangles > 64)
                throw new IllegalArgumentException(
                    "legacy textured model mapping-triangle capacity exceeded: " + mappingTriangles
                );

            if (textureMode == TextureMode.NONE) {
                if (textureId >= 0)
                    throw new IllegalArgumentException("textureId supplied while textureMode=NONE");
            } else {
                if (textureId != 278)
                    throw new IllegalArgumentException(
                        "only the proven exact-v308 slot-278 bootstrap is currently supported"
                    );
            }

            if (animationMode == AnimationMode.REUSE_EXISTING) {
                if (standAnim < 0 || walkAnim < 0)
                    throw new IllegalArgumentException("existing animation reuse requires stand/walk ids");
                if (frameGroupId >= 0 || sequenceId >= 0)
                    throw new IllegalArgumentException(
                        "REUSE_EXISTING must not claim custom frame-group/sequence ids"
                    );
            } else {
                if (frameGroupId < 0 || sequenceId < 0)
                    throw new IllegalArgumentException(
                        "CUSTOM_FRAME_GROUP requires explicit frameGroupId and sequenceId"
                    );
            }

            if (gfxMode == GfxMode.NONE) {
                if (gfxId >= 0)
                    throw new IllegalArgumentException("gfxId supplied while gfxMode=NONE");
                if (gfxModelContext != GfxModelContext.EXACT_CURRENT)
                    throw new IllegalArgumentException("NONE gfx must use EXACT_CURRENT sentinel context");
            } else if (gfxMode == GfxMode.REFERENCE_EXISTING) {
                if (gfxId < 0)
                    throw new IllegalArgumentException("REFERENCE_EXISTING requires gfxId");
            } else {
                if (gfxId < 0)
                    throw new IllegalArgumentException("CUSTOM gfx requires gfxId");
                if (gfxModelContext == GfxModelContext.EXACT_CURRENT)
                    throw new IllegalArgumentException(
                        "CUSTOM gfx must declare PRIMARY or OSRS model context"
                    );
            }

            if (npcSize <= 0)
                throw new IllegalArgumentException("npcSize must be positive");
            if (kind == Kind.EQUIPMENT && equipmentSlot == null)
                throw new IllegalArgumentException("EQUIPMENT requires equipmentSlot");
            if (equipmentSlot == null && (twoHanded || coverage != EquipmentCoverage.NONE))
                throw new IllegalArgumentException(
                    "equipment flags require an explicit equipmentSlot"
                );
            if (!provenance.startsWith("CUSTOM_LOCALLAB"))
                throw new IllegalArgumentException(
                    "custom authoring provenance must remain CUSTOM_LOCALLAB: " + provenance
                );
        }

        String normalized() {
            return kind + "\t" + contentKey + "\t" + itemId + "\t" + npcId + "\t" +
                name + "\t" + modelId + "\t" + modelContext + "\t" + textureId + "\t" +
                mappingTriangles + "\t" + modelFamily + "\t" + skinMode + "\t" +
                hierarchyMode + "\t" + textureMode + "\t" + animationMode + "\t" +
                standAnim + "\t" + walkAnim + "\t" + frameGroupId + "\t" + sequenceId + "\t" +
                gfxId + "\t" + gfxMode + "\t" + gfxModelContext + "\t" + npcSize + "\t" +
                (equipmentSlot == null ? "-" : equipmentSlot.name()) + "\t" +
                twoHanded + "\t" + coverage + "\t" + provenance;
        }
    }

    private static final List<Asset> ALL = load();
    private static final Map<String,Asset> BY_KEY = byKey(ALL);
    private static final Map<Integer,Asset> BY_ITEM = byItem(ALL);

    private CustomAssetAuthoringRepository() {}

    static List<Asset> all() { return ALL; }

    static Asset byContentKey(String contentKey) {
        return BY_KEY.get(contentKey);
    }

    static Asset requireContentKey(String contentKey) {
        Asset asset = byContentKey(contentKey);
        if (asset == null) throw new IllegalStateException("missing custom asset: " + contentKey);
        return asset;
    }

    static Asset byItem(int itemId) {
        return BY_ITEM.get(itemId);
    }

    static List<Asset> parse(Reader reader) throws IOException {
        BufferedReader r = reader instanceof BufferedReader
            ? (BufferedReader) reader
            : new BufferedReader(reader);

        String header = r.readLine();
        String expected =
            "kind\tcontentKey\titemId\tnpcId\tname\tmodelId\tmodelContext\ttextureId\t" +
            "mappingTriangles\tmodelFamily\tskinMode\thierarchyMode\ttextureMode\tanimationMode\t" +
            "standAnim\twalkAnim\tframeGroupId\tsequenceId\tgfxId\tgfxMode\tgfxModelContext\t" +
            "npcSize\tequipmentSlot\ttwoHanded\tcoverage\tprovenance";
        if (!expected.equals(header))
            throw new IOException("bad custom asset authoring header");

        ArrayList<Asset> out = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] a = line.split("\\t", -1);
            if (a.length != 26)
                throw new IOException("bad custom asset authoring row: " + line);

            EquipmentSlot slot = "-".equals(a[22]) || "NONE".equals(a[22])
                ? null : EquipmentSlot.valueOf(a[22]);
            Asset asset = new Asset(
                Kind.valueOf(a[0]),
                a[1],
                pi(a[2]),
                pi(a[3]),
                a[4],
                pi(a[5]),
                ModelContext.valueOf(a[6]),
                pi(a[7]),
                pi(a[8]),
                ModelFamily.valueOf(a[9]),
                SkinMode.valueOf(a[10]),
                HierarchyMode.valueOf(a[11]),
                TextureMode.valueOf(a[12]),
                AnimationMode.valueOf(a[13]),
                pi(a[14]),
                pi(a[15]),
                pi(a[16]),
                pi(a[17]),
                pi(a[18]),
                GfxMode.valueOf(a[19]),
                GfxModelContext.valueOf(a[20]),
                pi(a[21]),
                slot,
                parseBooleanStrict(a[23], "twoHanded"),
                EquipmentCoverage.valueOf(a[24]),
                a[25]
            );
            out.add(asset);
        }
        out.sort(Comparator.comparing((Asset x) -> x.contentKey));
        return Collections.unmodifiableList(out);
    }

    private static List<Asset> load() {
        try {
            Path path = resolveData("custom_asset_authoring.tsv");
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return parse(reader);
            }
        } catch (IOException | RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Path resolveData(String name) throws IOException {
        Path path = Paths.get("server", "data", name);
        if (Files.isRegularFile(path)) return path;
        path = Paths.get("data", name);
        if (Files.isRegularFile(path)) return path;

        InputStream in = CustomAssetAuthoringRepository.class.getResourceAsStream("/spk/local/" + name);
        if (in != null) {
            Path tmp = Files.createTempFile("spk-" + name.replace('.', '-'), ".tmp");
            try (InputStream source = in) {
                Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp.toFile().deleteOnExit();
            return tmp;
        }
        throw new FileNotFoundException(name);
    }

    private static Map<String,Asset> byKey(List<Asset> assets) {
        LinkedHashMap<String,Asset> out = new LinkedHashMap<>();
        for (Asset asset : assets) {
            Asset previous = out.put(asset.contentKey, asset);
            if (previous != null)
                throw new ExceptionInInitializerError("duplicate custom contentKey: " + asset.contentKey);
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<Integer,Asset> byItem(List<Asset> assets) {
        LinkedHashMap<Integer,Asset> out = new LinkedHashMap<>();
        for (Asset asset : assets) {
            Asset previous = out.put(asset.itemId, asset);
            if (previous != null)
                throw new ExceptionInInitializerError("duplicate custom itemId: " + asset.itemId);
        }
        return Collections.unmodifiableMap(out);
    }

    private static int pi(String value) {
        return Integer.parseInt(value.trim());
    }

    private static boolean parseBooleanStrict(String value, String field) {
        String normalized = value.trim();
        if ("true".equals(normalized)) return true;
        if ("false".equals(normalized)) return false;
        throw new IllegalArgumentException(field + " must be true/false: " + value);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(field);
        String trimmed = value.trim();
        if (trimmed.indexOf('\t') >= 0 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0)
            throw new IllegalArgumentException(field + " contains control separator");
        return trimmed;
    }
}

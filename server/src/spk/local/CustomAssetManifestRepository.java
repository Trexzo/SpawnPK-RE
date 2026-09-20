package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Structured LocalLab custom-content asset manifest.
 *
 * This repository describes server-visible identities that an external exact-current
 * client/cache packer must provide. It does not claim that custom rows were recovered
 * from the original SpawnPK server/cache.
 */
final class CustomAssetManifestRepository {
    enum Kind { PET_VARIANT }

    static final class Asset {
        final Kind kind;
        final String contentKey;
        final int variant;
        final int itemId;
        final int npcId;
        final String name;
        final String models;
        final int standAnim;
        final int walkAnim;
        final int scale;
        final int gfxId;
        final String text;
        final String description;
        final String provenance;

        Asset(Kind kind, String contentKey, int variant, int itemId, int npcId,
              String name, String models, int standAnim, int walkAnim, int scale,
              int gfxId, String text, String description, String provenance) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.contentKey = requireText(contentKey, "contentKey");
            this.variant = variant;
            this.itemId = itemId;
            this.npcId = npcId;
            this.name = requireText(name, "name");
            this.models = requireText(models, "models");
            this.standAnim = standAnim;
            this.walkAnim = walkAnim;
            this.scale = scale;
            this.gfxId = gfxId;
            this.text = requireText(text, "text");
            this.description = requireText(description, "description");
            this.provenance = requireText(provenance, "provenance");
            validate();
        }

        private void validate() {
            if (variant <= 0) throw new IllegalArgumentException("variant must be positive: " + variant);
            if (itemId < 0 || itemId > 29999)
                throw new IllegalArgumentException("itemId outside exact-current client table: " + itemId);
            if (npcId < 0 || npcId > 16383)
                throw new IllegalArgumentException("npcId outside packet-65 range: " + npcId);
            if (standAnim < 0 || standAnim > 65535 || walkAnim < 0 || walkAnim > 65535)
                throw new IllegalArgumentException("animation outside u16 range");
            if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
            if (gfxId < 0 || gfxId > 65535) throw new IllegalArgumentException("gfx outside u16 range: " + gfxId);
            if (!provenance.startsWith("CUSTOM_LOCALLAB"))
                throw new IllegalArgumentException("custom asset provenance must remain CUSTOM_LOCALLAB: " + provenance);
            for (String raw : models.split(",")) {
                int model = Integer.parseInt(raw.trim());
                if (model < 0 || model > 65535)
                    throw new IllegalArgumentException("model outside u16 range: " + model);
            }
        }
    }

    private static final List<Asset> ALL = load();
    private static final Map<String,List<Asset>> BY_KEY = indexByKey(ALL);

    private CustomAssetManifestRepository() {}

    static List<Asset> all() { return ALL; }

    static List<Asset> byContentKey(String contentKey) {
        List<Asset> assets = BY_KEY.get(contentKey);
        return assets == null ? Collections.emptyList() : assets;
    }

    private static List<Asset> load() {
        ArrayList<Asset> out = new ArrayList<>();
        Path p;
        try {
            p = resolveData("custom_asset_manifest.tsv");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        Set<String> variantKeys = new HashSet<>();
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null || !header.startsWith("kind\tcontentKey\tvariant\t"))
                throw new IOException("bad custom asset manifest header");
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) == '#') continue;
                String[] a = line.split("\\t", -1);
                if (a.length < 14) throw new IOException("bad custom asset row: " + line);
                Asset asset = new Asset(
                    Kind.valueOf(a[0]), a[1], pi(a[2]), pi(a[3]), pi(a[4]),
                    a[5], a[6], pi(a[7]), pi(a[8]), pi(a[9]), pi(a[10]),
                    a[11], a[12], a[13]
                );
                String variantKey = asset.contentKey + "#" + asset.variant;
                if (!variantKeys.add(variantKey))
                    throw new IOException("duplicate custom asset variant: " + variantKey);
                out.add(asset);
            }
        } catch (IOException | RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }

        out.sort(Comparator.comparing((Asset a) -> a.contentKey).thenComparingInt(a -> a.variant));
        return Collections.unmodifiableList(out);
    }

    private static Map<String,List<Asset>> indexByKey(List<Asset> all) {
        LinkedHashMap<String,List<Asset>> mutable = new LinkedHashMap<>();
        for (Asset asset : all)
            mutable.computeIfAbsent(asset.contentKey, k -> new ArrayList<>()).add(asset);
        LinkedHashMap<String,List<Asset>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String,List<Asset>> e : mutable.entrySet())
            frozen.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        return Collections.unmodifiableMap(frozen);
    }

    private static Path resolveData(String name) throws IOException {
        Path p = Paths.get("server", "data", name);
        if (Files.isRegularFile(p)) return p;
        p = Paths.get("data", name);
        if (Files.isRegularFile(p)) return p;
        InputStream in = CustomAssetManifestRepository.class.getResourceAsStream("/spk/local/" + name);
        if (in != null) {
            Path tmp = Files.createTempFile("spk-" + name.replace('.', '-'), ".tmp");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            in.close();
            tmp.toFile().deleteOnExit();
            return tmp;
        }
        throw new FileNotFoundException(name);
    }

    private static int pi(String s) { return Integer.parseInt(s.trim()); }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field);
        return value.trim();
    }
}

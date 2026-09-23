package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Source-controlled exact-v308 item/NPC overlay authoring metadata.
 *
 * One row equals one explicit field, so every authored field carries its own authority.
 * Binary i.bin/e.bin serialization intentionally lives outside this repository layer.
 */
final class CustomDefinitionOverlayRepository {
    static final class Overlay {
        final CustomDefinitionOverlayPolicy.Kind kind;
        final int id;
        private final Map<String,String> fields;
        private final Map<String,String> provenanceByField;

        Overlay(
            CustomDefinitionOverlayPolicy.Kind kind,
            int id,
            Map<String,String> fields,
            Map<String,String> provenanceByField
        ) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.id = id;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            this.provenanceByField =
                Collections.unmodifiableMap(new LinkedHashMap<>(provenanceByField));
        }

        Map<String,String> fields() { return fields; }

        String field(String name) { return fields.get(name); }

        String provenance(String field) { return provenanceByField.get(field); }

        int cloneSourceId() {
            return kind == CustomDefinitionOverlayPolicy.Kind.ITEM
                ? CustomDefinitionOverlayPolicy.itemCloneSource(fields)
                : CustomDefinitionOverlayPolicy.npcCloneSource(fields);
        }

        String normalized() {
            ArrayList<String> names = new ArrayList<>(fields.keySet());
            Collections.sort(names);
            StringBuilder out = new StringBuilder(kind + "\t" + id);
            for (String name : names) {
                out.append('\n')
                    .append(name).append('=').append(fields.get(name))
                    .append('\t').append(provenanceByField.get(name));
            }
            return out.toString();
        }
    }

    private static final List<Overlay> ALL = load();
    private static final Map<Integer,Overlay> ITEMS = index(
        ALL, CustomDefinitionOverlayPolicy.Kind.ITEM
    );
    private static final Map<Integer,Overlay> NPCS = index(
        ALL, CustomDefinitionOverlayPolicy.Kind.NPC
    );
    private static final String FINGERPRINT = fingerprint(ALL);

    private CustomDefinitionOverlayRepository() {}

    static List<Overlay> all() { return ALL; }

    static Overlay item(int id) { return ITEMS.get(id); }

    static Overlay npc(int id) { return NPCS.get(id); }

    static String fingerprintSha256() { return FINGERPRINT; }

    static List<Overlay> parse(Reader reader) throws IOException {
        BufferedReader r = reader instanceof BufferedReader
            ? (BufferedReader) reader
            : new BufferedReader(reader);
        String header = r.readLine();
        if (!"kind\tid\tfield\tvalue\tprovenance".equals(header))
            throw new IOException("bad custom definition overlay header");

        LinkedHashMap<String,MutableOverlay> grouped = new LinkedHashMap<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] a = line.split("\\t", -1);
            if (a.length != 5)
                throw new IOException("bad custom definition overlay row: " + line);

            CustomDefinitionOverlayPolicy.Kind kind =
                CustomDefinitionOverlayPolicy.Kind.valueOf(a[0]);
            int id = Integer.parseInt(a[1]);
            if (id < 0)
                throw new IOException("negative custom definition id: " + id);
            String field = a[2];
            String value = a[3];
            String provenance = a[4];
            if (!provenance.startsWith("CUSTOM_LOCALLAB"))
                throw new IOException(
                    "custom definition provenance must remain CUSTOM_LOCALLAB: " + provenance
                );
            CustomDefinitionOverlayPolicy.validateField(kind, field, value);

            String key = kind + ":" + id;
            MutableOverlay mutable = grouped.computeIfAbsent(
                key, ignored -> new MutableOverlay(kind, id)
            );
            if (mutable.fields.put(field, value) != null)
                throw new IOException("duplicate custom definition field: " + key + ":" + field);
            mutable.provenance.put(field, provenance);
        }

        ArrayList<Overlay> out = new ArrayList<>();
        for (MutableOverlay mutable : grouped.values())
            out.add(mutable.freeze());
        out.sort(
            Comparator.comparing((Overlay x) -> x.kind.name())
                .thenComparingInt(x -> x.id)
        );
        return Collections.unmodifiableList(out);
    }

    private static List<Overlay> load() {
        try {
            Path path = CustomAssetAuthoringRepository.resolveData(
                "custom_definition_overlays.tsv"
            );
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return parse(reader);
            }
        } catch (IOException | RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Map<Integer,Overlay> index(
        List<Overlay> overlays,
        CustomDefinitionOverlayPolicy.Kind kind
    ) {
        LinkedHashMap<Integer,Overlay> out = new LinkedHashMap<>();
        for (Overlay overlay : overlays) {
            if (overlay.kind != kind) continue;
            Overlay previous = out.put(overlay.id, overlay);
            if (previous != null)
                throw new ExceptionInInitializerError(
                    "duplicate custom definition overlay: " + kind + ":" + overlay.id
                );
        }
        return Collections.unmodifiableMap(out);
    }

    private static String fingerprint(List<Overlay> overlays) {
        StringBuilder normalized = new StringBuilder();
        for (Overlay overlay : overlays)
            normalized.append(overlay.normalized()).append('\n');
        return sha256(normalized.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class MutableOverlay {
        final CustomDefinitionOverlayPolicy.Kind kind;
        final int id;
        final LinkedHashMap<String,String> fields = new LinkedHashMap<>();
        final LinkedHashMap<String,String> provenance = new LinkedHashMap<>();

        MutableOverlay(CustomDefinitionOverlayPolicy.Kind kind, int id) {
            this.kind = kind;
            this.id = id;
        }

        Overlay freeze() {
            return new Overlay(kind, id, fields, provenance);
        }
    }
}

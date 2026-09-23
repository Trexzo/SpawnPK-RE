package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Exact-current namespace inventory supplied by an external cache/client census.
 *
 * The snapshot contains identifiers and capacity/context metadata only. It is not a
 * cache dump and is intentionally not committed as production cache material.
 */
final class CustomAssetNamespaceSnapshot {
    static final String REQUIRED_SCOPE = "BASE_PLUS_EXACT_OVERRIDES";

    enum Namespace { ITEM, NPC, MODEL, ANIMATION, GFX, TEXTURE, FRAME_GROUP }
    enum Context { GLOBAL, PRIMARY, OSRS }

    private static final class Key {
        final Namespace namespace;
        final Context context;
        final int id;

        Key(Namespace namespace, Context context, int id) {
            this.namespace = Objects.requireNonNull(namespace, "namespace");
            this.context = Objects.requireNonNull(context, "context");
            this.id = id;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key k = (Key) other;
            return namespace == k.namespace && context == k.context && id == k.id;
        }

        @Override public int hashCode() {
            return Objects.hash(namespace, context, id);
        }

        @Override public String toString() {
            return namespace + ":" + context + ":" + id;
        }
    }

    private static final class CapacityKey {
        final Namespace namespace;
        final Context context;

        CapacityKey(Namespace namespace, Context context) {
            this.namespace = namespace;
            this.context = context;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof CapacityKey)) return false;
            CapacityKey k = (CapacityKey) other;
            return namespace == k.namespace && context == k.context;
        }

        @Override public int hashCode() {
            return Objects.hash(namespace, context);
        }

        @Override public String toString() {
            return namespace + ":" + context;
        }
    }

    private final String clientSha256;
    private final String scope;
    private final Map<CapacityKey,Integer> capacities;
    private final Set<Key> present;
    private final Map<Integer,Context> gfxModelContexts;
    private final String fingerprintSha256;

    private CustomAssetNamespaceSnapshot(
        String clientSha256,
        String scope,
        Map<CapacityKey,Integer> capacities,
        Set<Key> present,
        Map<Integer,Context> gfxModelContexts
    ) {
        this.clientSha256 = clientSha256;
        this.scope = scope;
        this.capacities = Collections.unmodifiableMap(new LinkedHashMap<>(capacities));
        this.present = Collections.unmodifiableSet(new LinkedHashSet<>(present));
        this.gfxModelContexts =
            Collections.unmodifiableMap(new LinkedHashMap<>(gfxModelContexts));
        this.fingerprintSha256 = computeFingerprint();
    }

    static CustomAssetNamespaceSnapshot load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    static CustomAssetNamespaceSnapshot parse(Reader reader) throws IOException {
        BufferedReader r = reader instanceof BufferedReader
            ? (BufferedReader) reader
            : new BufferedReader(reader);

        String clientLine = nextDataLine(r);
        String scopeLine = nextDataLine(r);
        String header = nextDataLine(r);

        if (clientLine == null || !clientLine.startsWith("clientSha256\t"))
            throw new IOException("namespace snapshot missing clientSha256");
        String clientSha = clientLine.substring("clientSha256\t".length()).trim();
        if (!CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256.equalsIgnoreCase(clientSha))
            throw new IOException(
                "namespace snapshot client SHA mismatch: expected=" +
                CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256 +
                " actual=" + clientSha
            );
        clientSha = CustomAssetAuthoringRepository.EXACT_V308_CLIENT_SHA256;

        if (scopeLine == null || !scopeLine.startsWith("scope\t"))
            throw new IOException("namespace snapshot missing scope");
        String scope = scopeLine.substring("scope\t".length()).trim();
        if (!REQUIRED_SCOPE.equals(scope))
            throw new IOException(
                "namespace snapshot scope must be " + REQUIRED_SCOPE + ": " + scope
            );

        if (!"recordType\tnamespace\tcontext\tvalue".equals(header))
            throw new IOException("bad namespace snapshot record header");

        LinkedHashMap<CapacityKey,Integer> capacities = new LinkedHashMap<>();
        LinkedHashSet<Key> present = new LinkedHashSet<>();
        LinkedHashMap<Integer,Context> gfxContexts = new LinkedHashMap<>();

        String line;
        while ((line = r.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] a = line.split("\\t", -1);
            if (a.length != 4)
                throw new IOException("bad namespace snapshot row: " + line);

            String type = a[0];
            Namespace namespace = Namespace.valueOf(a[1]);
            Context context = Context.valueOf(a[2]);
            int value;
            try {
                value = Integer.parseInt(a[3]);
            } catch (RuntimeException e) {
                throw new IOException("bad namespace snapshot integer: " + line, e);
            }

            if ("CAPACITY".equals(type)) {
                if (value <= 0)
                    throw new IOException("capacity must be positive: " + line);
                validateNamespaceContext(namespace, context, line);
                CapacityKey key = new CapacityKey(namespace, context);
                if (capacities.put(key, value) != null)
                    throw new IOException("duplicate capacity: " + key);
            } else if ("PRESENT".equals(type)) {
                if (value < 0)
                    throw new IOException("negative namespace id: " + line);
                validateNamespaceContext(namespace, context, line);
                Key key = new Key(namespace, context, value);
                if (!present.add(key))
                    throw new IOException("duplicate namespace id: " + key);
            } else if ("GFX_CONTEXT".equals(type)) {
                if (namespace != Namespace.GFX)
                    throw new IOException("GFX_CONTEXT requires namespace GFX: " + line);
                if (context != Context.PRIMARY && context != Context.OSRS)
                    throw new IOException(
                        "GFX_CONTEXT requires PRIMARY or OSRS context: " + line
                    );
                if (value < 0)
                    throw new IOException("negative GFX context id: " + line);
                Context previous = gfxContexts.put(value, context);
                if (previous != null)
                    throw new IOException(
                        (previous == context
                            ? "duplicate GFX model context for "
                            : "conflicting GFX model context for ") +
                        value
                    );
            } else {
                throw new IOException("unknown namespace snapshot recordType: " + type);
            }
        }

        return new CustomAssetNamespaceSnapshot(
            clientSha, scope, capacities, present, gfxContexts
        );
    }

    private static void validateNamespaceContext(
        Namespace namespace,
        Context context,
        String line
    ) throws IOException {
        if (namespace == Namespace.MODEL) {
            if (context != Context.PRIMARY && context != Context.OSRS)
                throw new IOException(
                    "MODEL namespace requires PRIMARY or OSRS context: " + line
                );
            return;
        }
        if (context != Context.GLOBAL)
            throw new IOException(
                namespace + " namespace requires GLOBAL context: " + line
            );
    }

    String clientSha256() { return clientSha256; }

    String scope() { return scope; }

    String fingerprintSha256() { return fingerprintSha256; }

    boolean contains(Namespace namespace, Context context, int id) {
        return present.contains(new Key(namespace, context, id));
    }

    int capacity(Namespace namespace, Context context) {
        Integer value = capacities.get(new CapacityKey(namespace, context));
        return value == null ? -1 : value;
    }

    Context gfxModelContext(int gfxId) {
        return gfxModelContexts.get(gfxId);
    }

    private String computeFingerprint() {
        ArrayList<String> rows = new ArrayList<>();
        for (Map.Entry<CapacityKey,Integer> e : capacities.entrySet())
            rows.add("CAPACITY\t" + e.getKey() + "\t" + e.getValue());
        for (Key key : present)
            rows.add("PRESENT\t" + key);
        for (Map.Entry<Integer,Context> e : gfxModelContexts.entrySet())
            rows.add("GFX_CONTEXT\t" + e.getValue() + "\t" + e.getKey());
        Collections.sort(rows);

        StringBuilder normalized = new StringBuilder();
        normalized.append("clientSha256\t").append(clientSha256).append('\n');
        normalized.append("scope\t").append(scope).append('\n');
        for (String row : rows)
            normalized.append(row).append('\n');
        return sha256(normalized.toString());
    }

    private static String nextDataLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty() && !line.startsWith("#"))
                return line;
        }
        return null;
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

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import rs.cache.a;

public final class VerifyR13Profile {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest(bytes)) {
            out.append(String.format("%02x", value));
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: isolated-home");
        }

        Path home = Path.of(args[0]);
        Path cache = home.resolve(".spawnpk");

        byte[] config = Files.readAllBytes(cache.resolve("configs/i.bin"));
        Class<?> mapperClass =
            Class.forName("org.msgpack.jackson.dataformat.MessagePackMapper");
        Object mapper = mapperClass.getConstructor().newInstance();
        Class<?> typeRefClass =
            Class.forName("com.fasterxml.jackson.core.type.TypeReference");
        Object typeRef = Class.forName("rs.t.b").getConstructor().newInstance();

        Map<Integer, Map<String, Object>> root =
            (Map<Integer, Map<String, Object>>) mapperClass
                .getMethod("readValue", byte[].class, typeRefClass)
                .invoke(mapper, config, typeRef);

        require(
            ((Number) root.get(29999).get("modelId")).intValue() == 79999,
            "item/model binding"
        );
        System.out.println("R13_PROFILE_CONFIG_PASS records=" + root.size());

        byte[] modelBytes = Files.readAllBytes(cache.resolve("raw/79999.dat"));
        require(
            sha256(modelBytes).equals(
                "6cf617b5e14e60b5bc58d4f1c72e11476f09382d40a72f49be122009157c7fad"
            ),
            "model SHA"
        );

        Class<?> modelClass = Class.forName("rs.a.h");
        Class<?> contextClass = Class.forName("rs.a.a.a");
        Class<?> cacheClass = Class.forName("rs.cache.e");
        modelClass
            .getMethod("a", int.class, cacheClass)
            .invoke(null, 0, null);
        modelClass
            .getMethod("a", byte[].class, int.class, boolean.class)
            .invoke(null, modelBytes, 79999, false);

        Object context =
            contextClass.getConstructor(boolean.class).newInstance(false);
        Object model =
            modelClass.getConstructor(int.class, contextClass)
                .newInstance(79999, context);

        require(
            modelClass.getField("ad").getInt(model) == 8 &&
            modelClass.getField("ah").getInt(model) == 12 &&
            modelClass.getField("av").getInt(model) == 12,
            "model shape"
        );
        System.out.println(
            "R13_PROFILE_MODEL_PASS sha256=" + sha256(modelBytes)
        );

        byte[] textureArchive;
        try (
            RandomAccessFile dat =
                new RandomAccessFile(
                    cache.resolve("main_file_cache.dat").toFile(),
                    "r"
                );
            RandomAccessFile idx =
                new RandomAccessFile(
                    cache.resolve("main_file_cache.idx0").toFile(),
                    "r"
                )
        ) {
            textureArchive = new a(dat, idx, 1).a(6);
        }

        require(
            textureArchive != null,
            "texture archive missing"
        );

        require(
            textureArchive.length >= 6,
            "texture archive header"
        );

        int rawLength =
            ((textureArchive[0] & 0xff) << 16) |
            ((textureArchive[1] & 0xff) << 8) |
            (textureArchive[2] & 0xff);
        int packedLength =
            ((textureArchive[3] & 0xff) << 16) |
            ((textureArchive[4] & 0xff) << 8) |
            (textureArchive[5] & 0xff);

        require(
            packedLength == 0,
            "R13 generated texture archive transport"
        );

        byte[] textureBody;
        try(
            GZIPInputStream gzip=
                new GZIPInputStream(
                    new ByteArrayInputStream(
                        textureArchive,
                        6,
                        textureArchive.length - 6
                    )
                );
            ByteArrayOutputStream body=
                new ByteArrayOutputStream(
                    rawLength
                )
        ){
            gzip.transferTo(
                body
            );
            textureBody=
                body.toByteArray();
        }

        require(
            textureBody.length == rawLength &&
            sha256(textureBody).equals(
                "596f6e438a2f3dd8141d1d5c757a50ff38ad30dbee361921b163ae73d599b4f8"
            ),
            "texture archive semantic-body SHA"
        );

        Class<?> jagClass = Class.forName("rs.x.f");
        Object jag =
            jagClass.getConstructor(byte[].class, String.class)
                .newInstance(textureArchive, "textures");

        Class<?> textureClass = Class.forName("rs.l.E");
        Class<?> spriteClass = Class.forName("rs.l.a");
        textureClass.getMethod("a", jagClass).invoke(null, jag);

        Object texture =
            ((Object[]) textureClass.getField("y").get(null))[278];
        require(texture != null, "texture 278");

        int width = spriteClass.getField("j").getInt(texture);
        int height = spriteClass.getField("k").getInt(texture);
        require(width == 64 && height == 64, "texture shape");

        System.out.println(
            "R13_PROFILE_TEXTURE_PASS semanticBodySha256=" +
            sha256(textureBody) +
            " transportSha256=" + sha256(textureArchive) +
            " runtime=" + width + "x" + height
        );
        System.out.println("R13_ISOLATED_PROFILE_EXACT_V308_PASS");
    }

    private VerifyR13Profile() {}
}

import java.io.*;
import java.nio.file.*;
import java.util.*;
import rs.cache.a;

public final class R13CacheArchiveTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                "usage: extract|inject dat idx store fileId file"
            );
        }

        String operation = args[0];
        int store = Integer.parseInt(args[3]);
        int fileId = Integer.parseInt(args[4]);
        Path file = Path.of(args[5]);

        if (operation.equals("extract")) {
            try (
                RandomAccessFile dat = new RandomAccessFile(args[1], "r");
                RandomAccessFile idx = new RandomAccessFile(args[2], "r")
            ) {
                byte[] raw = new a(dat, idx, store).a(fileId);
                if (raw == null) {
                    throw new AssertionError("cache record missing");
                }
                Files.write(file, raw);
                System.out.println(
                    "R13_CACHE_EXTRACT_PASS store=" + store +
                    " fileId=" + fileId +
                    " bytes=" + raw.length
                );
            }
            return;
        }

        if (operation.equals("inject")) {
            byte[] raw = Files.readAllBytes(file);
            try (
                RandomAccessFile dat = new RandomAccessFile(args[1], "rw");
                RandomAccessFile idx = new RandomAccessFile(args[2], "rw")
            ) {
                a cache = new a(dat, idx, store);
                if (!cache.a(raw.length, raw, fileId)) {
                    throw new AssertionError("cache write failed");
                }

                byte[] reread = cache.a(fileId);
                if (reread == null || !Arrays.equals(raw, reread)) {
                    throw new AssertionError("cache write readback mismatch");
                }
            }

            System.out.println(
                "R13_CACHE_INJECT_PASS store=" + store +
                " fileId=" + fileId +
                " bytes=" + raw.length
            );
            return;
        }

        throw new IllegalArgumentException("unknown operation: " + operation);
    }

    private R13CacheArchiveTool() {}
}

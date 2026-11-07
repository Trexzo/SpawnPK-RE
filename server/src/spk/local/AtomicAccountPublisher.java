package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;

/**
 * LocalLab account snapshot publication shared by the current repository
 * and the old profile/accessory compatibility writers.
 *
 * Properties are encoded before touching the filesystem. A unique, adjacent
 * staging file is written and force(true)'d before a REQUIRED atomic replace.
 * There is no non-atomic fallback. A failed publication preserves the existing
 * target unless the underlying filesystem violates its atomic-move contract.
 *
 * A forced file is not by itself a power-loss durability guarantee: parent
 * directory fsync, non-cooperative writers and storage hardware are separate.
 */
final class AtomicAccountPublisher {
    @FunctionalInterface
    interface Replacement {
        void replace(Path staged, Path target) throws IOException;
    }

    private static final Replacement ATOMIC_REPLACE = (staged, target) -> {
        try {
            Files.move(staged, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException(
                "R16_ATOMIC_ACCOUNT_PUBLISH_DENIED atomic replace unsupported",
                unsupported);
        }
    };

    private AtomicAccountPublisher() {}

    static void publish(Path target, Properties properties, String comment)
            throws IOException {
        publish(target, properties, comment, ATOMIC_REPLACE);
    }

    /** Test seam: replacement action runs only after staged bytes are forced. */
    static void publish(Path target, Properties properties, String comment,
                        Replacement replacement) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(replacement, "replacement");

        Path canonical = target.toAbsolutePath().normalize();
        Path parent = canonical.getParent();
        if (parent == null) {
            throw new IOException("R16_ATOMIC_ACCOUNT_PUBLISH_DENIED no parent");
        }
        checkNoSymlinkAncestors(parent);
        Files.createDirectories(parent);
        checkNoSymlinkAncestors(parent);
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("R16_ATOMIC_ACCOUNT_PUBLISH_DENIED invalid parent");
        }
        checkTarget(canonical);

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        properties.store(encoded, comment);
        byte[] bytes = encoded.toByteArray();

        Path staged = null;
        Throwable primaryFailure = null;
        try {
            staged = Files.createTempFile(parent,
                "." + canonical.getFileName() + ".r16-", ".stage");
            try (FileChannel channel = FileChannel.open(staged,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer input = ByteBuffer.wrap(bytes);
                while (input.hasRemaining()) {
                    channel.write(input);
                }
                channel.force(true);
            }
            // Reject a symlink swap or an unreplaceable directory before move.
            checkTarget(canonical);
            replacement.replace(staged, canonical);
        } catch (IOException | RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException cleanup) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanup);
                    } else {
                        throw cleanup;
                    }
                }
            }
        }
    }

    private static void checkTarget(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                "R16_ATOMIC_ACCOUNT_PUBLISH_DENIED target is not a regular file");
        }
    }

    private static void checkNoSymlinkAncestors(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException(
                    "R16_ATOMIC_ACCOUNT_PUBLISH_DENIED symlink parent");
            }
        }
    }
}

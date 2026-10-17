package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.355/356: real guarded World save after a LIVE ACTIVE mutation.
 * Asserts unchanged account bytes AND inode, rather than merely
 * accepting a post-move uncertain exception after disk replacement.
 */
final class G21355ForcedWorldActiveContinuityFixture {
    enum Scenario { SAME_BYTES_NEW_INODE, SAME_INODE_CANONICAL_NONCE_DRIFT }
    private G21355ForcedWorldActiveContinuityFixture() {}

    private static void check(boolean condition, String explanation) {
        if (!condition) throw new AssertionError("G21.355/356 " + explanation);
    }

    static void run(Scenario scenario) throws Exception {
        Path root = Files.createTempDirectory("g21355-world-active-");
        String force = FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String neg = LocalFinalSaveNegativeWitness.PROPERTY;
        String oldForce = System.getProperty(force);
        String oldNeg = System.getProperty(neg);
        System.clearProperty(force);
        System.clearProperty(neg);
        try {
            Path selected = root.resolve("selected.properties");
            Path active = LocalDurableAccountReviewIntentPrimitive.markerPath(selected);
            AtomicBoolean sawHook = new AtomicBoolean(false);
            FilePlayerRepository repository = new FilePlayerRepository(
                username -> root.resolve(username + ".properties"),
                username -> {},
                username -> {
                    sawHook.set(true);
                    check(Files.isRegularFile(active, LinkOption.NOFOLLOW_LINKS),
                        "selected ACTIVE absent inside real publication callback");
                    byte[] original = Files.readAllBytes(active);
                    BasicFileAttributes before = Files.readAttributes(
                        active, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (scenario == Scenario.SAME_BYTES_NEW_INODE) {
                        Path retained = root.resolve("retained-negative-active");
                        Files.move(active, retained);
                        Files.write(active, original,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                        check(!Files.isSameFile(active, retained) &&
                            Arrays.equals(original, Files.readAllBytes(active)),
                            "ACTIVE same-byte inode substitution not installed");
                    } else {
                        String contents = new String(original, StandardCharsets.UTF_8);
                        String prefix = "untrustedSessionNonce=";
                        int pos = contents.indexOf(prefix);
                        check(pos >= 0, "canonical ACTIVE nonce missing");
                        pos += prefix.length();
                        check(pos < contents.length(), "nonce empty");
                        char current = contents.charAt(pos);
                        byte replacement = (byte)(current == 'a' ? 'b' : 'a');
                        FileTime previousTime = before.lastModifiedTime();
                        try (FileChannel channel = FileChannel.open(active,
                                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                            channel.position(pos);
                            check(channel.write(ByteBuffer.wrap(new byte[]{replacement})) == 1,
                                "ACTIVE nonce byte mutation not written");
                            channel.force(true);
                        }
                        Files.setLastModifiedTime(active, previousTime);
                        BasicFileAttributes after = Files.readAttributes(active,
                            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        check(Objects.equals(before.fileKey(), after.fileKey()) &&
                            before.size() == after.size() &&
                            previousTime.equals(after.lastModifiedTime()) &&
                            !Arrays.equals(original, Files.readAllBytes(active)),
                            "ACTIVE same-inode metadata-restored byte mutation absent");
                    }
                    // The mutation is deliberately STILL CANONICAL and
                    // must not be rejected by a trivial presence check.
                    LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                        selected);
                });
            PlayerSnapshot snapshot = PlayerSnapshotCodec.capture(
                "selected", new WorldPlayer());
            repository.save(snapshot);
            BasicFileAttributes originalAccount = Files.readAttributes(
                selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            byte[] originalBytes = Files.readAllBytes(selected);
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(selected);
            System.setProperty(force, "true");
            System.setProperty(neg, "true");
            IOException refusal = null;
            try { repository.saveForWorld(snapshot); }
            catch (IOException expected) { refusal = expected; }
            check(sawHook.get(), "real World publication callback not reached");
            check(refusal != null &&
                String.valueOf(refusal.getMessage()).contains(
                    "G21.275 WORLD_ACTIVE_REVIEW_MARKER_CHANGED_NO_ACK"),
                "missing explicit pre-ATOMIC_MOVE ACTIVE continuity veto: " + refusal);
            BasicFileAttributes afterAccount = Files.readAttributes(
                selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            check(Arrays.equals(originalBytes, Files.readAllBytes(selected)) &&
                Objects.equals(originalAccount.fileKey(), afterAccount.fileKey()) &&
                repository.forcedWorldPostimageCountForTest() == 0 &&
                Files.isRegularFile(active, LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(LocalFinalSaveNegativeWitness.witnessPath(selected),
                    LinkOption.NOFOLLOW_LINKS),
                "uncertain ACTIVE mutation overwrote account or claimed confirmation");
            String id = scenario == Scenario.SAME_BYTES_NEW_INODE ? "21355" : "21356";
            System.out.println("G" + id + "_ACTIVE_PREMOVE_CONTINUITY_PASS" +
                " realGuardedWorldSave=true" +
                " mutatedMarkerCanonical=true" +
                " originalAccountInodeAndBytesPreserved=true" +
                " successfulPostimages=0" +
                " finalSaveReceipt=false");
        } finally {
            if (oldForce == null) System.clearProperty(force);
            else System.setProperty(force, oldForce);
            if (oldNeg == null) System.clearProperty(neg);
            else System.setProperty(neg, oldNeg);
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new)) Files.deleteIfExists(path);
            }
        }
    }
}

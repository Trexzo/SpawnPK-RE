package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.360-363: real FilePlayerRepository guarded forced World writes,
 * with actual selected filesystem postimage and same-account isolation.
 */
final class G21360ForcedWorldAccountPreimageFixture {
    enum Scenario { LATE_BYTES, LATE_INODE, FOREIGN_DRIFT, FIRST_ABSENT }
    private G21360ForcedWorldAccountPreimageFixture() {}
    private static void check(boolean okay, String message) {
        if (!okay) throw new AssertionError("G21.360-363 " + message);
    }

    static void run(Scenario scenario) throws Exception {
        Path root = Files.createTempDirectory("g21360-world-account-preimage-");
        String forceKey = FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String negativeKey = LocalFinalSaveNegativeWitness.PROPERTY;
        String originalForce = System.getProperty(forceKey);
        String originalNegative = System.getProperty(negativeKey);
        System.clearProperty(forceKey);
        System.clearProperty(negativeKey);
        try {
            Path selected = root.resolve("selected.properties");
            Path foreign = root.resolve("foreign.properties");
            Path active = LocalDurableAccountReviewIntentPrimitive.markerPath(selected);
            Path preservedOldInode = root.resolve("original-account-preserved");
            AtomicBoolean callbackReached = new AtomicBoolean(false);
            AtomicReference<byte[]> injectedBytes = new AtomicReference<>();
            AtomicReference<Object> injectedFileKey = new AtomicReference<>();
            AtomicReference<byte[]> foreignMutation = new AtomicReference<>();
            FilePlayerRepository repo = new FilePlayerRepository(
                username -> root.resolve(username + ".properties"),
                username -> {},
                username -> {
                    callbackReached.set(true);
                    if (scenario == Scenario.FIRST_ABSENT) {
                        check(!Files.exists(selected, LinkOption.NOFOLLOW_LINKS),
                            "first-account setup already has disk state");
                        return;
                    }
                    if (scenario == Scenario.FOREIGN_DRIFT) {
                        Files.write(foreign, new byte[]{9, 8, 7, 6},
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                        foreignMutation.set(Files.readAllBytes(foreign));
                        return;
                    }
                    byte[] prior = Files.readAllBytes(selected);
                    BasicFileAttributes before = Files.readAttributes(selected,
                        BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (scenario == Scenario.LATE_BYTES) {
                        byte replacement = (byte)(prior[0] ^ 0x01);
                        FileTime oldMtime = before.lastModifiedTime();
                        try(FileChannel channel = FileChannel.open(selected,
                                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                            check(channel.write(ByteBuffer.wrap(
                                new byte[]{replacement})) == 1,
                                "one-byte disk change not applied");
                            channel.force(true);
                        }
                        Files.setLastModifiedTime(selected, oldMtime);
                        BasicFileAttributes now = Files.readAttributes(selected,
                            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        check(Objects.equals(before.fileKey(), now.fileKey()) &&
                            before.size() == now.size() &&
                            Objects.equals(oldMtime, now.lastModifiedTime()) &&
                            !Arrays.equals(prior, Files.readAllBytes(selected)),
                            "restored-metadata in-place mutation not installed");
                    } else {
                        Files.move(selected, preservedOldInode);
                        Files.write(selected, prior, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        check(!Files.isSameFile(selected, preservedOldInode) &&
                            Arrays.equals(prior, Files.readAllBytes(selected)),
                            "same-byte new-inode account replacement absent");
                    }
                    injectedBytes.set(Files.readAllBytes(selected));
                    injectedFileKey.set(Files.readAttributes(selected,
                        BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                        .fileKey());
                    LocalDurableAccountReviewIntentPrimitive
                        .requireReviewMarker(selected);
                });

            PlayerSnapshot snapshot = PlayerSnapshotCodec.capture(
                "selected", new WorldPlayer());
            if (scenario != Scenario.FIRST_ABSENT) repo.save(snapshot);
            if (scenario == Scenario.FOREIGN_DRIFT)
                Files.write(foreign, new byte[]{1, 2, 3, 4});
            boolean initiallyPresent = Files.exists(
                selected, LinkOption.NOFOLLOW_LINKS);
            check(initiallyPresent == (scenario != Scenario.FIRST_ABSENT),
                "incorrect account preimage fixture");
            byte[] existingBytes = initiallyPresent ? Files.readAllBytes(selected) : null;
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(selected);
            byte[] markerBytes = Files.readAllBytes(active);

            System.setProperty(forceKey, "true");
            System.setProperty(negativeKey, "true");
            IOException failed = null;
            try { repo.saveForWorld(snapshot); }
            catch(IOException error) { failed = error; }

            check(callbackReached.get(), "real publication callback not exercised");
            if (scenario == Scenario.LATE_BYTES || scenario == Scenario.LATE_INODE) {
                check(failed != null &&
                    String.valueOf(failed.getMessage()).contains(
                        "G21.360 WORLD_ACCOUNT_PREIMAGE_CHANGED_BEFORE_MOVE"),
                    "selected account drift was not blocked before move: " + failed);
                check(Arrays.equals(injectedBytes.get(),
                        Files.readAllBytes(selected)) &&
                    Objects.equals(injectedFileKey.get(),
                        Files.readAttributes(selected,
                            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                            .fileKey()) &&
                    repo.forcedWorldPostimageCountForTest() == 0 &&
                    (scenario != Scenario.LATE_INODE ||
                        Arrays.equals(existingBytes,
                            Files.readAllBytes(preservedOldInode))),
                    "forced World move overwrote uncooperative account bytes or inode");
            } else {
                check(failed == null &&
                    Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS) &&
                    repo.forcedWorldPostimageCountForTest() == 1,
                    "healthy selected / first-time forced save failed: " + failed);
                if(scenario == Scenario.FOREIGN_DRIFT)
                    check(Arrays.equals(foreignMutation.get(),
                        Files.readAllBytes(foreign)),
                        "unrelated account mutation lost");
            }
            check(Arrays.equals(markerBytes, Files.readAllBytes(active)) &&
                !Files.exists(LocalFinalSaveNegativeWitness.witnessPath(
                    selected), LinkOption.NOFOLLOW_LINKS),
                "no fabricated final-review witness or ACTIVE mutation allowed");
            String id = scenario == Scenario.LATE_BYTES ? "21360" :
                scenario == Scenario.LATE_INODE ? "21361" :
                scenario == Scenario.FOREIGN_DRIFT ? "21362" : "21363";
            System.out.println("G"+id+"_FORCED_WORLD_ACCOUNT_PREIMAGE_PASS" +
                " realRepositoryWorldSave=true" +
                " selectedDriftRejectedBeforeMove=" +
                    (scenario == Scenario.LATE_BYTES || scenario == Scenario.LATE_INODE) +
                " healthyControl=" +
                    (scenario == Scenario.FOREIGN_DRIFT || scenario == Scenario.FIRST_ABSENT) +
                " noAuthenticatedAck=true automaticRecovery=false");
        } finally {
            if (originalForce == null) System.clearProperty(forceKey);
            else System.setProperty(forceKey, originalForce);
            if (originalNegative == null) System.clearProperty(negativeKey);
            else System.setProperty(negativeKey, originalNegative);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}

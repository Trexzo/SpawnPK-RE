package spk.local;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Exact guarded repository save; no manufactured receipt or replay. */
final class G21357ForcedWorldActiveAliasFixture {
    enum Scenario { PREEXISTING, LATE, FOREIGN }
    private G21357ForcedWorldActiveAliasFixture() {}

    private static void check(boolean actual, String why) {
        if (!actual) throw new AssertionError("G21.357-359 " + why);
    }

    static void run(Scenario scenario) throws Exception {
        Path root = Files.createTempDirectory("g21357-world-active-alias-");
        String force = FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String neg = LocalFinalSaveNegativeWitness.PROPERTY;
        String oldForce = System.getProperty(force);
        String oldNeg = System.getProperty(neg);
        System.clearProperty(force);
        System.clearProperty(neg);
        try {
            Path selected = root.resolve("selected.properties");
            Path active = LocalDurableAccountReviewIntentPrimitive.markerPath(selected);
            Path alias = root.resolve("active-observed-hardlink-alias");
            AtomicBoolean reachedPublication = new AtomicBoolean(false);
            FilePlayerRepository repo = new FilePlayerRepository(
                username -> root.resolve(username + ".properties"),
                username -> {},
                username -> {
                    reachedPublication.set(true);
                    if (scenario == Scenario.LATE) {
                        byte[] markerBefore = Files.readAllBytes(active);
                        BasicFileAttributes identityBefore = Files.readAttributes(
                            active, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        Files.createLink(alias, active);
                        BasicFileAttributes identityAfter = Files.readAttributes(
                            active, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        check(Objects.equals(identityBefore.fileKey(),
                            identityAfter.fileKey()) &&
                            identityBefore.size() == identityAfter.size() &&
                            Arrays.equals(markerBefore, Files.readAllBytes(active)) &&
                            Files.isSameFile(alias, active),
                            "late hardlink did not preserve exact ACTIVE identity/bytes");
                    }
                });
            PlayerSnapshot snapshot = PlayerSnapshotCodec.capture(
                "selected", new WorldPlayer());
            repo.save(snapshot);
            byte[] previousAccount = Files.readAllBytes(selected);
            BasicFileAttributes previousIdentity = Files.readAttributes(
                selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(selected);
            byte[] exactActive = Files.readAllBytes(active);
            Path foreignActive = null;
            Path foreignAlias = null;

            if (scenario == Scenario.PREEXISTING) {
                Files.createLink(alias, active);
                check(Files.isSameFile(alias, active),
                    "preexisting hardlink did not alias exact ACTIVE");
            } else if (scenario == Scenario.FOREIGN) {
                Path foreign = root.resolve("foreign.properties");
                Files.write(foreign, new byte[]{1, 2, 3});
                foreignActive = LocalDurableAccountReviewIntentPrimitive
                    .writeActiveReview(foreign);
                foreignAlias = root.resolve("foreign-active-hardlink");
                Files.createLink(foreignAlias, foreignActive);
                check(Files.isSameFile(foreignActive, foreignAlias),
                    "foreign negative marker hardlink not installed");
            }

            System.setProperty(force, "true");
            System.setProperty(neg, "true");
            IOException failure = null;
            try { repo.saveForWorld(snapshot); }
            catch (IOException caught) { failure = caught; }

            if (scenario == Scenario.FOREIGN) {
                check(failure == null, "unrelated hardlink blocked selected: " + failure);
                check(reachedPublication.get() &&
                    repo.forcedWorldPostimageCountForTest() == 1 &&
                    Files.isSameFile(foreignActive, foreignAlias) &&
                    Arrays.equals(exactActive, Files.readAllBytes(active)),
                    "healthy selected publication or negative isolation failed");
            } else {
                String expected = scenario == Scenario.PREEXISTING
                    ? "G21.357 WORLD_ACTIVE_REVIEW_HARDLINK_NEGATIVE"
                    : "G21.358 WORLD_ACTIVE_REVIEW_LATE_ALIAS_NEGATIVE";
                check(failure != null &&
                    String.valueOf(failure.getMessage()).contains(expected),
                    "missing correct pre-move ACTIVE nlink veto: " + failure);
                BasicFileAttributes after = Files.readAttributes(selected,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                check(Objects.equals(previousIdentity.fileKey(), after.fileKey()) &&
                    Arrays.equals(previousAccount, Files.readAllBytes(selected)) &&
                    Files.isSameFile(alias, active) &&
                    Arrays.equals(exactActive, Files.readAllBytes(active)) &&
                    repo.forcedWorldPostimageCountForTest() == 0,
                    "uncertain alias changed selected account, marker, or success");
                if (scenario == Scenario.PREEXISTING) {
                    check(!reachedPublication.get(),
                        "preexisting ACTIVE alias reached publication callback");
                    try (Stream<Path> names = Files.list(root)) {
                        check(names.noneMatch(path -> path.getFileName().toString()
                            .contains(".g21170-world-")),
                            "preexisting ACTIVE alias allocated World temp");
                    }
                } else {
                    check(reachedPublication.get(),
                        "late hardlink test missed real guarded publication");
                }
            }
            check(!Files.exists(LocalFinalSaveNegativeWitness.witnessPath(
                    selected), LinkOption.NOFOLLOW_LINKS),
                "untrusted ACTIVE alias invented a final-save witness");
            System.out.println("G" +
                (scenario == Scenario.PREEXISTING ? "21357" :
                 scenario == Scenario.LATE ? "21358" : "21359") +
                "_WORLD_ACTIVE_ALIAS_" +
                (scenario == Scenario.FOREIGN ? "ISOLATION" : "PREMOVE_DENIAL") +
                "_PASS realGuardedSave=true" +
                " selectedPreimagePreserved=" + (scenario != Scenario.FOREIGN) +
                " unrelatedNegativeIgnored=" + (scenario == Scenario.FOREIGN) +
                " finalAck=false automaticRecovery=false");
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

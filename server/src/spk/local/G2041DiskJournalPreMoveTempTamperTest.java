package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

/** Actual forced pending bytes drift cannot replace an existing reservation. */
public final class G2041DiskJournalPreMoveTempTamperTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("g2041-pre-move-bytes-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20412041-aaaa-4aaa-8aaa-204120412041");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Path original = dir.resolve(id + ".event");
                byte[] before = Files.readAllBytes(original);
                final Path[] pendingFile = new Path[1];
                String refused = null;
                try {
                    journal.reserveWithTestCutpoint(f.world, death, id,
                        LocalLabTerminalDeliveryAssessment.Consumer.G4,
                        (pending, destination) -> {
                            pendingFile[0] = pending;
                            if (!destination.equals(original))
                                throw new AssertionError("wrong event destination");
                            Files.write(pending, "outside-writer".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8),
                                StandardOpenOption.TRUNCATE_EXISTING);
                        });
                } catch (IOException expected) { refused = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_PENDING_BYTES_CHANGED_BEFORE_MOVE".equals(refused) &&
                    Arrays.equals(before, Files.readAllBytes(original)) &&
                    pendingFile[0] != null && Files.exists(pendingFile[0]) &&
                    journal.snapshot().reservations == 1 &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "pending byte mutation must fail BEFORE moving over old event");
                String postFailure = null;
                try { journal.auditedSnapshot(); }
                catch (IOException expected) { postFailure = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE".equals(postFailure),
                    "failed pending file stays unresolved rather than auto-cleaned");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(dir); }
        System.out.println("G2041_DISK_JOURNAL_PENDING_BYTES_PASS" +
            " inPublicationTamperDenied=true oldRecordPreserved=true" +
            " orphanRetained=true noGrant=true");
    }
    private G2041DiskJournalPreMoveTempTamperTest() {}
}

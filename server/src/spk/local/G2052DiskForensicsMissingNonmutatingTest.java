package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pure inspection cannot create a journal directory or lock file. */
public final class G2052DiskForensicsMissingNonmutatingTest {
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("g2052-forensics-missing-");
        try {
            Path absent = scratch.resolve("never-created-journal");
            LocalLabExperimentalDiskJournalForensics.Report r =
                LocalLabExperimentalDiskJournalForensics.inspectExisting(absent);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state == LocalLabExperimentalDiskJournalForensics.State
                    .NO_EXISTING_JOURNAL &&
                "DISK_FORENSICS_DIRECTORY_MISSING".equals(r.reasonCode) &&
                r.observedEvents == 0 && r.observedReservations == 0 &&
                Files.notExists(absent) &&
                r.isUnlockedReadOnlyObservation() &&
                !r.safeToReplayAnyConsumer() &&
                !r.hasDurableConsumerGrantAcknowledgement() &&
                !r.hasExclusiveCrossJvmOwnerEpoch() &&
                !r.hasAuthenticatedOriginalDeathEvent(),
                "missing forensic target has no filesystem creation or grant authority");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2052_FORENSICS_MISSING_READ_ONLY_PASS"+
            " directoryCreated=false lockCreated=false safeReplay=false");
    }
    private G2052DiskForensicsMissingNonmutatingTest() {}
}

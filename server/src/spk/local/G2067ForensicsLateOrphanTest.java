package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** A crash-like temp appearing after first scan invalidates prior clean report. */
public final class G2067ForensicsLateOrphanTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("g2067-orphan-between-scans-");
        try {
            G2064ForensicsTwoPassStableTest.seed(dir);
            Path event=dir.resolve(G2064ForensicsTwoPassStableTest.ID+".event");
            byte[] original=Files.readAllBytes(event);
            Path orphan=dir.resolve("unpublished-external.tmp");
            LocalLabExperimentalDiskJournalForensics.Report r=
                LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoint(
                    dir, () -> Files.write(orphan,new byte[]{1,2,3}));
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state==LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSTABLE_OBSERVATION &&
                Files.exists(orphan) &&
                Arrays.equals(original,Files.readAllBytes(event)) &&
                r.isUnlockedReadOnlyObservation(),
                "unexpected pending temp invalidates first clean observation");
            LocalLabExperimentalDiskJournalForensics.Report after=
                LocalLabExperimentalDiskJournalForensics.inspectExisting(dir);
            G2025DiskAttemptJournalRestartWitnessTest.check(
                after.state==LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_ORPHAN_PENDING,
                "subsequent independent scan still diagnoses unresolved temp");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(dir); }
        System.out.println("G2067_FORENSICS_LATE_PENDING_PASS"
            +" unexpectedTempRetained=true unstableRead=true noCleanup=true");
    }
    private G2067ForensicsLateOrphanTest() {}
}

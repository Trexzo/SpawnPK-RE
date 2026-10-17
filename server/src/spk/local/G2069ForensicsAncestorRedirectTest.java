package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Parent symlink created between observations to SAME journal inode is blocked. */
public final class G2069ForensicsAncestorRedirectTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2069-parent-redirect-");
        Path parent=Files.createDirectory(scratch.resolve("old-parent"));
        Path journal=Files.createDirectory(parent.resolve("journal"));
        Path relocated=scratch.resolve("moved-parent");
        try {
            G2064ForensicsTwoPassStableTest.seed(journal);
            LocalLabExperimentalDiskJournalForensics.Report r=
                LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoint(
                    journal, () -> {
                        Files.move(parent,relocated,StandardCopyOption.ATOMIC_MOVE);
                        Files.createSymbolicLink(parent,relocated);
                    });
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state==LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSTABLE_OBSERVATION &&
                Files.exists(relocated.resolve("journal").resolve(
                    G2064ForensicsTwoPassStableTest.ID+".event")) &&
                !r.safeToReplayAnyConsumer(),
                "redirected ancestor is not an unchanged observed path");
        } finally {
            Files.deleteIfExists(parent);
            G2025DiskAttemptJournalRestartWitnessTest.erase(relocated);
            G2025DiskAttemptJournalRestartWitnessTest.erase(scratch);
        }
        System.out.println("G2069_FORENSICS_PARENT_REDIRECT_PASS"
            +" journalInodeReused=true symlinkAncestorDenied=true replay=false");
    }
    private G2069ForensicsAncestorRedirectTest() {}
}

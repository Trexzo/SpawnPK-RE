package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Lock filename reuse between scans is observable, not global owner authority. */
public final class G2068ForensicsLockInodeSwapTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2068-lock-swap-");
        Path dir=Files.createDirectory(scratch.resolve("journal"));
        try {
            G2064ForensicsTwoPassStableTest.seed(dir);
            LocalLabExperimentalDiskJournalForensics.Report r=
                LocalLabExperimentalDiskJournalForensics.inspectExistingWithTestCutpoint(
                    dir, () -> {
                        Path replacement=scratch.resolve("replacement-lock");
                        Files.write(replacement,new byte[0]);
                        Files.move(replacement,dir.resolve(".journal.lock"),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    });
            G2025DiskAttemptJournalRestartWitnessTest.check(
                r.state==LocalLabExperimentalDiskJournalForensics.State
                    .BLOCKED_UNSTABLE_OBSERVATION &&
                r.observedEvents==1 &&
                !r.hasExclusiveCrossJvmOwnerEpoch() &&
                !r.safeToReplayAnyConsumer(),
                "different lock inode with same bytes cannot imply owner continuity");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2068_FORENSICS_TWO_PASS_LOCK_SWAP_PASS"
            +" lockFileIdentityChanged=true globalEpoch=false unstable=true");
    }
    private G2068ForensicsLockInodeSwapTest() {}
}

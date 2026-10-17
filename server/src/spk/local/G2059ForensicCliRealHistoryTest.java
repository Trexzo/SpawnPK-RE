package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Independent CLI process reports real NPC reservations, never grant ACK. */
public final class G2059ForensicCliRealHistoryTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2059-cli-real-");
        Path dir=Files.createDirectory(scratch.resolve("journal"));
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=f.settle();
            UUID id=UUID.fromString("20592059-aaaa-4aaa-8aaa-205920592059");
            try (LocalLabExperimentalDiskAttemptJournal journal=
                    LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                journal.reserve(f.world,terminal,id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                journal.reserve(f.world,terminal,id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G4);
            }
            Path record=dir.resolve(id+".event");
            byte[] before=Files.readAllBytes(record);
            G2058ForensicCliProcessTest.Result result=
                G2058ForensicCliProcessTest.invoke(dir,dir.toString());
            result.assertStatus(0,"READABLE_UNAUTHENTICATED_HISTORY");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                result.text.contains("events=1 reservations=2") &&
                result.text.contains("originalEventAuthority=false") &&
                result.text.contains("ownerEpoch=false") &&
                Arrays.equals(before,Files.readAllBytes(record)) &&
                f.world.pvmRecords().receiptCount()==1L,
                "real death and two reservations remain non-granting CLI evidence");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2059_FORENSICS_CLI_REAL_HISTORY_PASS"+
            " independentJvm=true stages=2 historicalOnly=true");
    }
    private G2059ForensicCliRealHistoryTest() {}
}

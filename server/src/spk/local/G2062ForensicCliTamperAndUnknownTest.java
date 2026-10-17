package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Operator CLI never repairs foreign event bytes or unknown sidefiles. */
public final class G2062ForensicCliTamperAndUnknownTest {
    public static void main(String[] args) throws Exception {
        Path scratch=Files.createTempDirectory("g2062-cli-bad-");
        Path dir=Files.createDirectory(scratch.resolve("journal"));
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death=f.settle();
            UUID id=UUID.fromString("20622062-dddd-4ddd-8ddd-206220622062");
            try (LocalLabExperimentalDiskAttemptJournal j=
                    LocalLabExperimentalDiskAttemptJournal.open(dir)) {
                j.reserve(f.world,death,id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }
            Path record=dir.resolve(id+".event");
            byte[] corrupt=Files.readAllBytes(record);
            corrupt[corrupt.length-2]=(byte)(corrupt[corrupt.length-2]=='a'?'b':'a');
            Files.write(record,corrupt);
            G2058ForensicCliProcessTest.Result damaged=
                G2058ForensicCliProcessTest.invoke(dir,dir.toString());
            damaged.assertStatus(3,"BLOCKED_MALFORMED_OR_CORRUPT_RECORD");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                Arrays.equals(corrupt,Files.readAllBytes(record)),
                "corrupt evidence cannot be auto-repaired by cli");
            Files.delete(record);
            Path unknown=dir.resolve("operator-sidefile");
            Files.write(unknown,new byte[]{1,2});
            G2058ForensicCliProcessTest.Result unexpected=
                G2058ForensicCliProcessTest.invoke(dir,dir.toString());
            unexpected.assertStatus(3,"BLOCKED_UNSAFE_PATH");
            G2025DiskAttemptJournalRestartWitnessTest.check(
                unexpected.text.contains("DISK_FORENSICS_UNKNOWN_ENTRY") &&
                Files.exists(unknown) &&
                f.world.pvmRecords().receiptCount()==1L,
                "CLI refuses unexpected files while leaving bytes untouched");
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(scratch); }
        System.out.println("G2062_FORENSICS_CLI_TAMPER_UNKNOWN_PASS"+
            " blockedExit3=true bytesUnchanged=true noRecovery=true");
    }
    private G2062ForensicCliTamperAndUnknownTest() {}
}

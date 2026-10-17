package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.UUID;

/** Valid recomputed hash and restored metadata are NOT evidence of an owned write. */
public final class G2036DiskJournalRechecksummedForeignRewriteTest {
    private static String hash(String body) throws Exception {
        byte[] raw = MessageDigest.getInstance("SHA-256").digest(
            body.getBytes(StandardCharsets.UTF_8));
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            result.append(hex[(b >>> 4) & 15]).append(hex[b & 15]);
        }
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("g2036-valid-tamper-");
        try (G2017TerminalAttemptFenceRealDeathTest.Fixture f =
                 new G2017TerminalAttemptFenceRealDeathTest.Fixture()) {
            MonsterSpawnerPvmRuntime.FinalizeResult death = f.settle();
            UUID id = UUID.fromString("20362036-dddd-4ddd-8ddd-203620362036");
            try (LocalLabExperimentalDiskAttemptJournal journal =
                     LocalLabExperimentalDiskAttemptJournal.open(directory)) {
                journal.reserve(f.world, death, id,
                    LocalLabTerminalDeliveryAssessment.Consumer.G1);
                Path record = directory.resolve(id + ".event");
                FileTime originalMtime = Files.getLastModifiedTime(record);
                byte[] before = Files.readAllBytes(record);
                String[] parts = new String(before, StandardCharsets.UTF_8)
                    .split("\n", -1);
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    parts.length == 6 && "G1".equals(parts[3]),
                    "genuine settled NPC created canonical one-stage record");
                parts[3] = "G4";
                String body = parts[0] + "\n" + parts[1] + "\n" +
                    parts[2] + "\n" + parts[3] + "\n";
                byte[] foreign = (body + hash(body) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    before.length == foreign.length,
                    "foreign record preserves exact serialized byte length");
                Files.write(record, foreign);
                Files.setLastModifiedTime(record, originalMtime);
                String refusal = null;
                try { journal.auditedSnapshot(); }
                catch (IOException expected) { refusal = expected.getMessage(); }
                G2025DiskAttemptJournalRestartWitnessTest.check(
                    "DISK_ATTEMPT_DISK_STATE_DIVERGED".equals(refusal) &&
                    journal.snapshot().entries.get(id).reserved.contains(
                        LocalLabTerminalDeliveryAssessment.Consumer.G1) &&
                    !journal.snapshot().entries.get(id).reserved.contains(
                        LocalLabTerminalDeliveryAssessment.Consumer.G4) &&
                    java.util.Arrays.equals(foreign, Files.readAllBytes(record)) &&
                    f.world.pvmRecords().receiptCount() == 1L,
                    "valid checksum with foreign changed stage still refuses");
            }
        } finally { G2025DiskAttemptJournalRestartWitnessTest.erase(directory); }
        System.out.println("G2036_DISK_JOURNAL_VALID_FOREIGN_REWRITE_PASS"+
            " checksumRecomputed=true metadataRestored=true auditedDriftDenied=true");
    }
    private G2036DiskJournalRechecksummedForeignRewriteTest() {}
}

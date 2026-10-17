package spk.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only, UNLOCKED incident triage for the experimental NON-GRANTING journal.
 *
 * TWO sequential checks detect changes visible between scans, but do NOT
 * acquire any cross-process lock or make a snapshot atomic or authoritative.
 * A hostile writer can change files and restore them between observations.
 */
final class LocalLabExperimentalDiskJournalForensics {
    enum State {
        NO_EXISTING_JOURNAL,
        READABLE_UNAUTHENTICATED_HISTORY,
        BLOCKED_ORPHAN_PENDING,
        BLOCKED_UNSAFE_PATH,
        BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
        BLOCKED_UNAVAILABLE_CAPABILITY,
        BLOCKED_UNCERTAIN_IO,
        BLOCKED_UNSTABLE_OBSERVATION
    }

    static final class Report {
        final State state;
        final String reasonCode;
        // Counts on blocked or concurrent states can be partial/stale.
        final int observedEvents;
        final int observedReservations;

        Report(State state, String reason, int events, int reservations) {
            this.state = Objects.requireNonNull(state, "state");
            this.reasonCode = Objects.requireNonNull(reason, "reason");
            this.observedEvents = events;
            this.observedReservations = reservations;
        }
        boolean hasAuthenticatedOriginalDeathEvent() { return false; }
        boolean hasDurableConsumerGrantAcknowledgement() { return false; }
        boolean hasExclusiveCrossJvmOwnerEpoch() { return false; }
        boolean safeToReplayAnyConsumer() { return false; }
        boolean isUnlockedReadOnlyObservation() { return true; }
    }

    private static final class Witness {
        final Object fileKey;
        final byte[] content;
        Witness(Object key, byte[] content) {
            this.fileKey = Objects.requireNonNull(key, "fileKey");
            this.content = content.clone();
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Witness)) return false;
            Witness that = (Witness) other;
            return Objects.equals(fileKey, that.fileKey) &&
                   Arrays.equals(content, that.content);
        }
        @Override public int hashCode() {
            return 31 * fileKey.hashCode() + Arrays.hashCode(content);
        }
    }
    private static final class Pass {
        final Report report;
        final Object directoryKey;
        final Map<String, Witness> files;
        Pass(Report report, Object directoryKey, Map<String, Witness> files) {
            this.report = report;
            this.directoryKey = directoryKey;
            this.files = files;
        }
    }

    @FunctionalInterface
    interface ObservationTestCutpoint {
        void afterFirstPass() throws IOException;
    }

    static Report inspectExisting(Path input) {
        return inspectExistingWithTestCutpoint(input, null);
    }

    /**
     * The hook exists ONLY to simulate outside mutations BETWEEN two real
     * DirectoryStream scans in deterministic regression tests.
     */
    static Report inspectExistingWithTestCutpoint(
            Path input, ObservationTestCutpoint afterFirstPass) {
        final Path root;
        try {
            root = Objects.requireNonNull(input, "journalDirectory")
                .toAbsolutePath().normalize();
        } catch (RuntimeException badPath) {
            return new Report(State.BLOCKED_UNCERTAIN_IO,
                "DISK_FORENSICS_INVALID_INPUT", 0, 0);
        }
        Pass first = scanOnce(root);
        if (first.report.state != State.READABLE_UNAUTHENTICATED_HISTORY)
            return first.report;
        try {
            if (afterFirstPass != null) afterFirstPass.afterFirstPass();
        } catch (IOException | RuntimeException injectionFailure) {
            return new Report(State.BLOCKED_UNSTABLE_OBSERVATION,
                "DISK_FORENSICS_SCAN_INTERRUPTED", first.report.observedEvents,
                first.report.observedReservations);
        }
        Pass second = scanOnce(root);
        if (second.report.state != State.READABLE_UNAUTHENTICATED_HISTORY ||
            !Objects.equals(first.directoryKey, second.directoryKey) ||
            !first.files.equals(second.files) ||
            first.report.observedEvents != second.report.observedEvents ||
            first.report.observedReservations != second.report.observedReservations) {
            return new Report(State.BLOCKED_UNSTABLE_OBSERVATION,
                "DISK_FORENSICS_UNSTABLE_OBSERVATION",
                first.report.observedEvents, first.report.observedReservations);
        }
        // Clean only means that TWO successive unlocked observations agreed.
        // It cannot prove durability, ownership, an original death or grant.
        return second.report;
    }

    private static Pass result(State state, String reason, int events,
                               int reservations, Object rootKey,
                               Map<String, Witness> evidence) {
        return new Pass(new Report(state, reason, events, reservations),
                        rootKey, evidence);
    }

    private static Pass scanOnce(Path root) {
        int count = 0, stages = 0;
        Object rootKey = null;
        Map<String, Witness> evidence = new LinkedHashMap<String, Witness>();
        try {
            LocalLabExperimentalDiskAttemptJournal.requireSafeAncestorChain(root);
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS))
                return result(State.NO_EXISTING_JOURNAL,
                    "DISK_FORENSICS_DIRECTORY_MISSING", 0, 0, null, evidence);
            if (Files.isSymbolicLink(root) ||
                !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
                return result(State.BLOCKED_UNSAFE_PATH,
                    "DISK_FORENSICS_UNSAFE_DIRECTORY", 0, 0, null, evidence);
            rootKey = LocalLabExperimentalDiskAttemptJournal.requireDirectoryKey(root);
            boolean sawLock = false;
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(root)) {
                for (Path entry : paths) {
                    String name = entry.getFileName().toString();
                    if (Files.isSymbolicLink(entry) ||
                        !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                        return result(State.BLOCKED_UNSAFE_PATH,
                            "DISK_FORENSICS_UNSAFE_ENTRY", count, stages, rootKey, evidence);
                    LocalLabExperimentalDiskAttemptJournal.requireSingleLink(entry);
                    Object key = LocalLabExperimentalDiskAttemptJournal.requireFileKey(entry);
                    byte[] raw = LocalLabExperimentalDiskAttemptJournal.readBoundedRecord(entry);
                    // Track exact bounded bytes and inode identity, including
                    // lock file identity, without serializing them to output.
                    if (evidence.put(name, new Witness(key, raw)) != null)
                        return result(State.BLOCKED_UNCERTAIN_IO,
                            "DISK_FORENSICS_DUPLICATE_FILENAME", count, stages,
                            rootKey, evidence);
                    if (".journal.lock".equals(name)) {
                        sawLock = true;
                        continue;
                    }
                    if (name.endsWith(".tmp"))
                        return result(State.BLOCKED_ORPHAN_PENDING,
                            "DISK_FORENSICS_UNRESOLVED_PENDING", count, stages,
                            rootKey, evidence);
                    if (!name.endsWith(".event"))
                        return result(State.BLOCKED_UNSAFE_PATH,
                            "DISK_FORENSICS_UNKNOWN_ENTRY", count, stages,
                            rootKey, evidence);
                    String idText = name.substring(0, name.length() - 6);
                    UUID id;
                    try { id = UUID.fromString(idText); }
                    catch (IllegalArgumentException invalid) {
                        return result(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                            "DISK_FORENSICS_INVALID_EVENT_FILENAME", count, stages,
                            rootKey, evidence);
                    }
                    if (!id.toString().equals(idText))
                        return result(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                            "DISK_FORENSICS_NONCANONICAL_EVENT_FILENAME",
                            count, stages, rootKey, evidence);
                    LocalLabExperimentalDiskAttemptJournal.Entry parsed =
                        LocalLabExperimentalDiskAttemptJournal.parse(raw, id);
                    count++;
                    stages += parsed.reserved.size();
                    if (stages > LocalLabExperimentalDiskAttemptJournal.MAX_ATTEMPTS)
                        return result(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                            "DISK_FORENSICS_CAPACITY_OVERFLOW", count, stages,
                            rootKey, evidence);
                }
            }
            if (!sawLock)
                return result(State.BLOCKED_UNSAFE_PATH,
                    "DISK_FORENSICS_LOCK_FILE_MISSING", count, stages,
                    rootKey, evidence);
            return result(State.READABLE_UNAUTHENTICATED_HISTORY,
                "DISK_FORENSICS_READABLE_NO_GRANT_AUTHORITY", count, stages,
                rootKey, evidence);
        } catch (IOException error) {
            String reason = error.getMessage();
            if (reason == null) reason = "DISK_FORENSICS_IO_UNSPECIFIED";
            if (reason.contains("INTEGRITY") ||
                reason.contains("MALFORMED") ||
                reason.contains("RECORD") ||
                reason.contains("NONCANONICAL") ||
                reason.contains("STAGE") ||
                reason.contains("CAPACITY"))
                return result(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                    reason, count, stages, rootKey, evidence);
            if (reason.contains("MULTIPLE_OR_UNKNOWN_LINKS"))
                return result(State.BLOCKED_UNSAFE_PATH,
                    reason, count, stages, rootKey, evidence);
            if (reason.contains("UNAVAILABLE") ||
                reason.contains("FILE_IDENTITY") ||
                reason.contains("LINK_COUNT"))
                return result(State.BLOCKED_UNAVAILABLE_CAPABILITY,
                    reason, count, stages, rootKey, evidence);
            if (reason.contains("LINK") ||
                reason.contains("UNSAFE") ||
                reason.contains("DIRECTORY"))
                return result(State.BLOCKED_UNSAFE_PATH,
                    reason, count, stages, rootKey, evidence);
            return result(State.BLOCKED_UNCERTAIN_IO,
                reason, count, stages, rootKey, evidence);
        }
    }

    private LocalLabExperimentalDiskJournalForensics() {}
}

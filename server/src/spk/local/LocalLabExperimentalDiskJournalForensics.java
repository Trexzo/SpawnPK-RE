package spk.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only, UNLOCKED incident triage for the experimental NON-GRANTING journal.
 *
 * A well-formed reservation file is NOT an authenticated death, grant ACK,
 * replay authorization, or proof of cross-process ownership. Does not create
 * a journal directory, lock file, or change/delete any forensic artifact.
 */
final class LocalLabExperimentalDiskJournalForensics {
    enum State {
        NO_EXISTING_JOURNAL,
        READABLE_UNAUTHENTICATED_HISTORY,
        BLOCKED_ORPHAN_PENDING,
        BLOCKED_UNSAFE_PATH,
        BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
        BLOCKED_UNAVAILABLE_CAPABILITY,
        BLOCKED_UNCERTAIN_IO
    }

    static final class Report {
        final State state;
        final String reasonCode;
        // Counts are non-authoritative observations. On blocked results they
        // can be partial; do not interpret them as a complete disk snapshot.
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

    static Report inspectExisting(Path input) {
        Path root = Objects.requireNonNull(input, "journalDirectory")
            .toAbsolutePath().normalize();
        int count = 0, stages = 0;
        try {
            LocalLabExperimentalDiskAttemptJournal.requireSafeAncestorChain(root);
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS))
                return new Report(State.NO_EXISTING_JOURNAL,
                    "DISK_FORENSICS_DIRECTORY_MISSING", 0, 0);
            if (Files.isSymbolicLink(root) ||
                !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
                return new Report(State.BLOCKED_UNSAFE_PATH,
                    "DISK_FORENSICS_UNSAFE_DIRECTORY", 0, 0);
            LocalLabExperimentalDiskAttemptJournal.requireDirectoryKey(root);
            boolean sawLock = false;
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(root)) {
                for (Path entry : paths) {
                    String name = entry.getFileName().toString();
                    if (Files.isSymbolicLink(entry) ||
                        !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                        return new Report(State.BLOCKED_UNSAFE_PATH,
                            "DISK_FORENSICS_UNSAFE_ENTRY", count, stages);
                    LocalLabExperimentalDiskAttemptJournal.requireSingleLink(entry);
                    LocalLabExperimentalDiskAttemptJournal.requireFileKey(entry);
                    if (".journal.lock".equals(name)) {
                        sawLock = true;
                        continue;
                    }
                    if (name.endsWith(".tmp"))
                        return new Report(State.BLOCKED_ORPHAN_PENDING,
                            "DISK_FORENSICS_UNRESOLVED_PENDING", count, stages);
                    if (!name.endsWith(".event"))
                        return new Report(State.BLOCKED_UNSAFE_PATH,
                            "DISK_FORENSICS_UNKNOWN_ENTRY", count, stages);
                    String idText = name.substring(0, name.length() - 6);
                    UUID id;
                    try { id = UUID.fromString(idText); }
                    catch (IllegalArgumentException bad) {
                        return new Report(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                            "DISK_FORENSICS_INVALID_EVENT_FILENAME", count, stages);
                    }
                    if (!id.toString().equals(idText))
                        return new Report(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                            "DISK_FORENSICS_NONCANONICAL_EVENT_FILENAME", count, stages);
                    LocalLabExperimentalDiskAttemptJournal.Entry event =
                        LocalLabExperimentalDiskAttemptJournal.parse(
                            LocalLabExperimentalDiskAttemptJournal.readBoundedRecord(entry),
                            id);
                    count++;
                    stages += event.reserved.size();
                    if (stages > LocalLabExperimentalDiskAttemptJournal.MAX_ATTEMPTS)
                        return new Report(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                            "DISK_FORENSICS_CAPACITY_OVERFLOW", count, stages);
                }
            }
            if (!sawLock)
                return new Report(State.BLOCKED_UNSAFE_PATH,
                    "DISK_FORENSICS_LOCK_FILE_MISSING", count, stages);
            // No filesystem lock is taken. This result says the observed
            // records parsed, NOT that they were stable or reward-authoritative.
            return new Report(State.READABLE_UNAUTHENTICATED_HISTORY,
                "DISK_FORENSICS_READABLE_NO_GRANT_AUTHORITY", count, stages);
        } catch (IOException error) {
            String reason = error.getMessage();
            if (reason == null) reason = "DISK_FORENSICS_IO_UNSPECIFIED";
            if (reason.contains("INTEGRITY") ||
                reason.contains("MALFORMED") ||
                reason.contains("RECORD") ||
                reason.contains("NONCANONICAL") ||
                reason.contains("STAGE") ||
                reason.contains("CAPACITY"))
                return new Report(State.BLOCKED_MALFORMED_OR_CORRUPT_RECORD,
                    reason, count, stages);
            if (reason.contains("MULTIPLE_OR_UNKNOWN_LINKS"))
                return new Report(State.BLOCKED_UNSAFE_PATH, reason, count, stages);
            if (reason.contains("UNAVAILABLE") ||
                reason.contains("FILE_IDENTITY") ||
                reason.contains("LINK_COUNT"))
                return new Report(State.BLOCKED_UNAVAILABLE_CAPABILITY,
                    reason, count, stages);
            if (reason.contains("LINK") ||
                reason.contains("UNSAFE") ||
                reason.contains("DIRECTORY"))
                return new Report(State.BLOCKED_UNSAFE_PATH,
                    reason, count, stages);
            return new Report(State.BLOCKED_UNCERTAIN_IO, reason, count, stages);
        }
    }

    private LocalLabExperimentalDiskJournalForensics() {}
}

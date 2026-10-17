package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * EXPERIMENTAL NON-GRANTING disk reservation witness.
 *
 * Does not run callbacks, write account grants, ACK consumers, replay a death,
 * or provide an original SpawnPK event ID. Caller-provided UUIDs are only a
 * test harness reference, NOT server authority. An interrupted or ambiguous
 * publication fails closed. This class is deliberately NOT wired to gameplay.
 */
final class LocalLabExperimentalDiskAttemptJournal implements AutoCloseable {
    static final int MAX_ATTEMPTS = 256;
    private static final String VERSION = "SPK_EXPERIMENTAL_NO_GRANTS_V1";
    private static final String LOCK_FILE = ".journal.lock";

    static final class Entry {
        final UUID testOnlyEventId;
        final String observedFingerprint;
        final Set<LocalLabTerminalDeliveryAssessment.Consumer> reserved;

        Entry(UUID id, String fingerprint,
              Set<LocalLabTerminalDeliveryAssessment.Consumer> stages) {
            testOnlyEventId = id;
            observedFingerprint = fingerprint;
            EnumSet<LocalLabTerminalDeliveryAssessment.Consumer> copy =
                EnumSet.noneOf(LocalLabTerminalDeliveryAssessment.Consumer.class);
            copy.addAll(stages);
            reserved = Collections.unmodifiableSet(copy);
        }
    }

    static final class Snapshot {
        final Map<UUID, Entry> entries;
        final int reservations;
        Snapshot(Map<UUID, Entry> src, int total) {
            entries = Collections.unmodifiableMap(new LinkedHashMap<UUID, Entry>(src));
            reservations = total;
        }
        boolean hasAtomicConsumerGrantAcknowledgement() { return false; }
        boolean safeToReplayAfterRestart() { return false; }
        boolean hasOriginalServerEventAuthority() { return false; }
    }

    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock exclusiveLock;
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();
    private int total;
    private boolean closed;

    private LocalLabExperimentalDiskAttemptJournal(
        Path directory, FileChannel channel, FileLock lock) {
        this.directory = directory;
        lockChannel = channel;
        exclusiveLock = lock;
    }

    static LocalLabExperimentalDiskAttemptJournal open(Path input)
        throws IOException {
        Path root = Objects.requireNonNull(input, "directory").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root))
            throw new IOException("DISK_ATTEMPT_UNSAFE_DIRECTORY_LINK");
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(root))
            throw new IOException("DISK_ATTEMPT_UNSAFE_DIRECTORY");
        Path lockPath = root.resolve(LOCK_FILE);
        if (Files.isSymbolicLink(lockPath))
            throw new IOException("DISK_ATTEMPT_UNSAFE_LOCK_LINK");
        FileChannel channel = FileChannel.open(lockPath,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
        FileLock lock = null;
        try {
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                throw new IOException("DISK_ATTEMPT_DIRECTORY_LOCKED", busy);
            }
            if (lock == null)
                throw new IOException("DISK_ATTEMPT_DIRECTORY_LOCKED");
            LocalLabExperimentalDiskAttemptJournal journal =
                new LocalLabExperimentalDiskAttemptJournal(root, channel, lock);
            journal.loadStrictly();
            return journal;
        } catch (IOException | RuntimeException failed) {
            if (lock != null) lock.release();
            channel.close();
            throw failed;
        }
    }

    private void loadStrictly() throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (LOCK_FILE.equals(name)) continue;
                if (Files.isSymbolicLink(path) ||
                    !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
                    !name.endsWith(".event"))
                    throw new IOException("DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE");
                String uuidText = name.substring(0, name.length() - 6);
                UUID id;
                try { id = UUID.fromString(uuidText); }
                catch (IllegalArgumentException bad) {
                    throw new IOException("DISK_ATTEMPT_INVALID_EVENT_FILENAME", bad);
                }
                if (!id.toString().equals(uuidText) || entries.containsKey(id))
                    throw new IOException("DISK_ATTEMPT_NONCANONICAL_EVENT_FILENAME");
                if (Files.size(path) > 4096L)
                    throw new IOException("DISK_ATTEMPT_OVERSIZE_RECORD");
                byte[] raw = Files.readAllBytes(path);
                Entry entry = parse(raw, id);
                entries.put(id, entry);
                total += entry.reserved.size();
                if (total > MAX_ATTEMPTS)
                    throw new IOException("DISK_ATTEMPT_CAPACITY_CORRUPTION");
            }
        }
    }

    synchronized Snapshot snapshot() {
        ensureOpen();
        return new Snapshot(entries, total);
    }

    synchronized void reserve(World world,
                              MonsterSpawnerPvmRuntime.FinalizeResult terminal,
                              UUID testOnlyEventId,
                              LocalLabTerminalDeliveryAssessment.Consumer consumer)
        throws IOException {
        ensureOpen();
        Objects.requireNonNull(testOnlyEventId, "testOnlyEventId");
        Objects.requireNonNull(consumer, "consumer");
        // This checks exact World and genuine canonical finalization/ground
        // receipt before ANY disk mutation. IdentityHashCode is intentionally
        // only process-local; it must NEVER become a restart-safe grant key.
        LocalLabSettledDeathEnvelope death =
            LocalLabSettledDeathEnvelope.observe(world, terminal);
        String fingerprint = fingerprint(death);
        Entry previous = entries.get(testOnlyEventId);
        if (previous != null &&
            !previous.observedFingerprint.equals(fingerprint))
            throw new IllegalStateException("DISK_ATTEMPT_CONFLICTING_DEATH_EVIDENCE");
        if (previous != null && previous.reserved.contains(consumer))
            throw new IllegalStateException("DISK_ATTEMPT_DUPLICATE_CONSUMER");
        if (total >= MAX_ATTEMPTS)
            throw new IllegalStateException("DISK_ATTEMPT_CAPACITY_FAIL_CLOSED");

        EnumSet<LocalLabTerminalDeliveryAssessment.Consumer> stages =
            EnumSet.noneOf(LocalLabTerminalDeliveryAssessment.Consumer.class);
        if (previous != null) stages.addAll(previous.reserved);
        stages.add(consumer);
        Entry next = new Entry(testOnlyEventId, fingerprint, stages);
        Path destination = directory.resolve(testOnlyEventId.toString() + ".event");
        if (Files.isSymbolicLink(destination))
            throw new IOException("DISK_ATTEMPT_UNSAFE_EVENT_LINK");
        Path pending = directory.resolve(testOnlyEventId.toString() + "." +
                                            UUID.randomUUID() + ".tmp");
        // A failed publication deliberately leaves its temp for fail-closed
        // investigation; reopening NEVER silently deletes unknown temp files.
        byte[] payload = encode(next);
        try (FileChannel channel = FileChannel.open(pending,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer bytes = ByteBuffer.wrap(payload);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(pending, destination,
                   StandardCopyOption.ATOMIC_MOVE,
                   StandardCopyOption.REPLACE_EXISTING);
        // No callback is ever invoked here, even after a successful disk move.
        // Directory fsync and hostile cross-process path swaps are NOT proven.
        entries.put(testOnlyEventId, next);
        total++;
    }

    private static String fingerprint(LocalLabSettledDeathEnvelope e) {
        StringBuilder b = new StringBuilder();
        b.append(e.npcId).append('\n').append(e.definitionId).append('\n')
            .append(e.deathTile).append('\n').append(e.deathTick).append('\n')
            .append(e.recipientRef).append('\n')
            .append(e.spawnRecipientGeneration).append('\n')
            .append(e.observedCurrentGeneration).append('\n')
            .append(e.originStatus).append('\n');
        b.append(e.observedCurrentPlayer == null ? "absent" :
                 "current:" + System.identityHashCode(e.observedCurrentPlayer));
        return sha256(b.toString());
    }

    private static String stages(Entry e) {
        StringBuilder b = new StringBuilder();
        for (LocalLabTerminalDeliveryAssessment.Consumer consumer :
                LocalLabTerminalDeliveryAssessment.Consumer.values()) {
            if (!e.reserved.contains(consumer)) continue;
            if (b.length() != 0) b.append(',');
            b.append(consumer.name());
        }
        return b.toString();
    }

    private static byte[] encode(Entry e) {
        String body = VERSION + "\n" + e.testOnlyEventId + "\n" +
            e.observedFingerprint + "\n" + stages(e) + "\n";
        return (body + sha256(body) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static Entry parse(byte[] data, UUID expected) throws IOException {
        String text = new String(data, StandardCharsets.UTF_8);
        String[] parts = text.split("\n", -1);
        if (parts.length != 6 || !parts[5].isEmpty() ||
            !VERSION.equals(parts[0]) ||
            !expected.toString().equals(parts[1]) ||
            !parts[2].matches("[0-9a-f]{64}") ||
            !parts[4].matches("[0-9a-f]{64}"))
            throw new IOException("DISK_ATTEMPT_MALFORMED_RECORD");
        String body = parts[0] + "\n" + parts[1] + "\n" +
                      parts[2] + "\n" + parts[3] + "\n";
        if (!sha256(body).equals(parts[4]))
            throw new IOException("DISK_ATTEMPT_RECORD_INTEGRITY_FAILURE");
        EnumSet<LocalLabTerminalDeliveryAssessment.Consumer> stages =
            EnumSet.noneOf(LocalLabTerminalDeliveryAssessment.Consumer.class);
        if (parts[3].isEmpty())
            throw new IOException("DISK_ATTEMPT_EMPTY_RESERVATIONS");
        try {
            for (String name : parts[3].split(",", -1))
                if (!stages.add(LocalLabTerminalDeliveryAssessment.Consumer.valueOf(name)))
                    throw new IllegalArgumentException("duplicate stage");
        } catch (IllegalArgumentException bad) {
            throw new IOException("DISK_ATTEMPT_UNKNOWN_OR_DUPLICATE_STAGE", bad);
        }
        Entry result = new Entry(expected, parts[2], stages);
        if (!stages(result).equals(parts[3]))
            throw new IOException("DISK_ATTEMPT_NONCANONICAL_STAGE_ORDER");
        return result;
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder b = new StringBuilder(bytes.length * 2);
            for (byte v : bytes) {
                b.append(hex[(v >>> 4) & 15]).append(hex[v & 15]);
            }
            return b.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 missing", impossible);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("DISK_ATTEMPT_JOURNAL_CLOSED");
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { exclusiveLock.release(); }
        finally { lockChannel.close(); }
    }
}

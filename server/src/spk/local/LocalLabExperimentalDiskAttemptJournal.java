package spk.local;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
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
    private final Object originalLockKey;
    private final Map<UUID, Object> recordKeys = new LinkedHashMap<UUID, Object>();
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();
    private int total;
    private boolean closed;

    private LocalLabExperimentalDiskAttemptJournal(
        Path directory, FileChannel channel, FileLock lock, Object lockKey) {
        this.directory = directory;
        lockChannel = channel;
        exclusiveLock = lock;
        originalLockKey = lockKey;
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
            requireSingleLink(lockPath);
            LocalLabExperimentalDiskAttemptJournal journal =
                new LocalLabExperimentalDiskAttemptJournal(
                    root, channel, lock, requireFileKey(lockPath));
            journal.loadStrictly();
            return journal;
        } catch (IOException | RuntimeException failed) {
            if (lock != null) lock.release();
            channel.close();
            throw failed;
        }
    }

    private static final class DiskScan {
        final Map<UUID, Entry> found = new LinkedHashMap<UUID, Entry>();
        final Map<UUID, Object> keys = new LinkedHashMap<UUID, Object>();
        int count;
    }

    private static void requireSingleLink(Path path) throws IOException {
        try {
            Object value = Files.getAttribute(path, "unix:nlink",
                LinkOption.NOFOLLOW_LINKS);
            if (!(value instanceof Number) ||
                ((Number)value).longValue() != 1L)
                throw new IOException("DISK_ATTEMPT_MULTIPLE_OR_UNKNOWN_LINKS");
        } catch (UnsupportedOperationException unsupported) {
            // Unsupported filesystems must fail closed; this prototype
            // is not promised to run on every Windows filesystem/provider.
            throw new IOException("DISK_ATTEMPT_LINK_COUNT_UNAVAILABLE", unsupported);
        }
    }

    private static Object requireFileKey(Path path) throws IOException {
        BasicFileAttributes a = Files.readAttributes(path,
            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isRegularFile() || a.fileKey() == null)
            throw new IOException("DISK_ATTEMPT_FILE_IDENTITY_UNAVAILABLE");
        return a.fileKey();
    }

    private static byte[] readBoundedRecord(Path path) throws IOException {
        try (FileChannel ch = FileChannel.open(path,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.allocate(4097);
            while (buffer.hasRemaining()) {
                int n = ch.read(buffer);
                if (n < 0) break;
                if (n == 0) throw new IOException("DISK_ATTEMPT_RECORD_READ_STALLED");
            }
            if (buffer.position() > 4096)
                throw new IOException("DISK_ATTEMPT_OVERSIZE_RECORD");
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    private DiskScan scanDirectory() throws IOException {
        if (Files.isSymbolicLink(directory) ||
            !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("DISK_ATTEMPT_UNSAFE_DIRECTORY");
        DiskScan scan = new DiskScan();
        boolean foundLock = false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (Files.isSymbolicLink(path) ||
                    !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE");
                requireSingleLink(path);
                Object key = requireFileKey(path);
                if (LOCK_FILE.equals(name)) {
                    if (foundLock || !Objects.equals(originalLockKey, key))
                        throw new IOException("DISK_ATTEMPT_LOCK_IDENTITY_CHANGED");
                    foundLock = true;
                    continue;
                }
                if (!name.endsWith(".event"))
                    throw new IOException("DISK_ATTEMPT_UNKNOWN_OR_UNSAFE_FILE");
                String uuidText = name.substring(0, name.length() - 6);
                UUID id;
                try { id = UUID.fromString(uuidText); }
                catch (IllegalArgumentException bad) {
                    throw new IOException("DISK_ATTEMPT_INVALID_EVENT_FILENAME", bad);
                }
                if (!id.toString().equals(uuidText) || scan.found.containsKey(id))
                    throw new IOException("DISK_ATTEMPT_NONCANONICAL_EVENT_FILENAME");
                Entry entry = parse(readBoundedRecord(path), id);
                scan.found.put(id, entry);
                scan.keys.put(id, key);
                scan.count += entry.reserved.size();
                if (scan.count > MAX_ATTEMPTS)
                    throw new IOException("DISK_ATTEMPT_CAPACITY_CORRUPTION");
            }
        }
        if (!foundLock)
            throw new IOException("DISK_ATTEMPT_LOCK_IDENTITY_CHANGED");
        return scan;
    }

    private void loadStrictly() throws IOException {
        DiskScan scan = scanDirectory();
        entries.putAll(scan.found);
        recordKeys.putAll(scan.keys);
        total = scan.count;
    }

    private void requireNoExternalChanges() throws IOException {
        DiskScan actual = scanDirectory();
        if (actual.count != total ||
            actual.found.size() != entries.size() ||
            !actual.found.keySet().equals(entries.keySet()) ||
            !actual.keys.equals(recordKeys))
            throw new IOException("DISK_ATTEMPT_DISK_STATE_DIVERGED");
        for (Map.Entry<UUID, Entry> expected : entries.entrySet()) {
            Entry now = actual.found.get(expected.getKey());
            if (now == null ||
                !expected.getValue().observedFingerprint.equals(now.observedFingerprint) ||
                !expected.getValue().reserved.equals(now.reserved))
                throw new IOException("DISK_ATTEMPT_DISK_STATE_DIVERGED");
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
        // Revalidate every existing disk record before ANY new publication.
        // The lock only excludes cooperative owners; an outside writer may
        // change unrelated files while this process remains open.
        requireNoExternalChanges();
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
        // A failure after ATOMIC_MOVE must not authorize another attempt.
        // A subsequent operation rescans and fails closed if our in-memory
        // state did not reach this point. This is not a durable grant ACK.
        requireSingleLink(destination);
        Object newFileKey = requireFileKey(destination);
        entries.put(testOnlyEventId, next);
        recordKeys.put(testOnlyEventId, newFileKey);
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

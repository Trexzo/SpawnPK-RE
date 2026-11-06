package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * One LOCAL LAB server process owns its entire persistent account directory
 * (both opensrc and src profiles) until its shutdown coordinator has finished.
 *
 * This protects against a second cooperative LocalLab JVM silently loading and
 * replacing the same account snapshots. The OS releases FileLock on JVM death;
 * the lock file itself is intentionally persistent. This does NOT certify
 * network-file-system locks, legacy writers or power-loss durability.
 */
final class LocalAccountDirectoryLease implements AutoCloseable {
    static final String LOCK_FILENAME = ".spawnpk-local-account-owner.lock";

    private final FileChannel channel;
    private final FileLock lock;
    private boolean closed;

    private LocalAccountDirectoryLease(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static LocalAccountDirectoryLease acquire(Path accountFile) throws IOException {
        Path account = Objects.requireNonNull(accountFile, "accountFile")
            .toAbsolutePath().normalize();
        Path parent = account.getParent();
        if (parent == null) {
            throw new IOException("R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED no account directory");
        }

        if (Files.isSymbolicLink(parent)) {
            throw new IOException("R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED symlink account directory");
        }
        Files.createDirectories(parent);
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED account directory invalid");
        }

        Path lockFile = parent.resolve(LOCK_FILENAME);
        if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED lock path not regular");
        }

        FileChannel opened = FileChannel.open(lockFile,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock exclusive;
            try {
                exclusive = opened.tryLock();
            } catch (OverlappingFileLockException overlap) {
                exclusive = null;
            }
            if (exclusive == null) {
                throw new IOException(
                    "R15_ACCOUNT_DIRECTORY_LEASE_BLOCKED already owned by another JVM");
            }
            return new LocalAccountDirectoryLease(opened, exclusive);
        } catch (IOException | RuntimeException failure) {
            try {
                opened.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}

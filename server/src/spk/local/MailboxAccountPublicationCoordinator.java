package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * G21.39: cooperating, account-local publication boundary.
 *
 * The same canonical account path is used by the guarded World save
 * and the no-clobber review-marker publisher. On supported filesystems,
 * a stable sibling advisory FileLock coordinates distinct JVMs.
 *
 * A reference-counted JVM monitor is scoped to each lock path, not the
 * entire repository. Different accounts can publish concurrently and
 * idle entries are removed. Registry bookkeeping never surrounds I/O.
 *
 * Not a crash-durable multi-file transaction, original server policy,
 * lock against uncooperative writers, or any reward-grant authority.
 * Persistent lock files MUST NOT be unlinked/recreated during use.
 */
final class MailboxAccountPublicationCoordinator {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2138_COOPERATING_FILE_PUBLICATION_NO_GRANT";

    private static final String SUFFIX=
        ".g2138-mailbox-publication.lock";

    // G21.39: only short monitor registration/retirement holds REGISTRY.
    // A queued waiter counts as a reference until it leaves, so monitor
    // identity cannot be replaced while a holder or waiter exists.
    private static final Object REGISTRY=new Object();
    private static final Map<Path,JvmLease> ACTIVE=new HashMap<>();

    private static final class JvmLease {
        final ReentrantLock monitor=new ReentrantLock(true);
        int references;
    }

    interface Operation<T> {
        T execute()throws IOException;
    }

    static Path lockPath(Path accountFile){
        Path file=Objects.requireNonNull(accountFile,"accountFile")
            .toAbsolutePath().normalize();
        if(file.getParent()==null)
            throw new IllegalArgumentException(
                "G21.38 account file missing parent"
            );
        return file.resolveSibling(
            file.getFileName().toString()+SUFFIX
        );
    }

    private static JvmLease acquire(Path lock){
        synchronized(REGISTRY){
            JvmLease lease=ACTIVE.get(lock);
            if(lease==null){
                lease=new JvmLease();
                ACTIVE.put(lock,lease);
            }
            lease.references++;
            return lease;
        }
    }

    private static void release(Path lock,JvmLease lease){
        synchronized(REGISTRY){
            // A JVM monitor stays registered while held or queued.
            // Never remove the lease when any other writer has entered
            // acquire(), including a waiter blocked on lease.monitor.
            if(ACTIVE.get(lock)!=lease||lease.references<=0)
                throw new IllegalStateException(
                    "G21.39 JVM publication lease identity corrupted"
                );
            lease.references--;
            if(lease.references==0)
                ACTIVE.remove(lock);
        }
    }

    /** Test diagnostic only: counts idle-leak candidates, no user state. */
    static int activeJvmLeaseCount(){
        synchronized(REGISTRY){
            return ACTIVE.size();
        }
    }

    static <T> T withExclusivePublication(
        Path accountFile,Operation<T> operation
    )throws IOException{
        Objects.requireNonNull(operation,"operation");
        final Path lock=lockPath(accountFile);
        final JvmLease lease=acquire(lock);
        try{
            // G21.39: ONLY this account's Java monitor is held across
            // filesystem work. Independent accounts are not blocked by
            // a slow or stalled writer on some other account.
            lease.monitor.lock();
            try{
                Files.createDirectories(lock.getParent());
                try(FileChannel channel=FileChannel.open(
                        lock,StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                    );
                    FileLock ownership=channel.lock()){
                    if(!ownership.isValid())
                        throw new IOException(
                            "G21.38 account publication lock unavailable"
                        );
                    return operation.execute();
                }catch(OverlappingFileLockException overlap){
                    throw new IOException(
                        "G21.38 overlapping account publication lock",
                        overlap
                    );
                }
            }finally{
                lease.monitor.unlock();
            }
        }finally{
            release(lock,lease);
        }
    }

    /**
     * G21.73: recovery-FORENSICS ONLY. A bounded observation must
     * not hang indefinitely behind a cooperating JVM or another
     * process holding the account's OS advisory publication lock.
     *
     * The deadline covers LOCK ACQUISITION only; disk inspection may
     * take longer. Neither acquiring nor timing out confers grant,
     * replay, restart admission or settlement authority.
     */
    /** G21.77: forensic-only NOFOLLOW ancestry check BEFORE lock-file I/O. */
    private static void verifyForensicLockAncestry(Path lock)
        throws IOException{
        for(Path dir=lock.getParent();dir!=null;dir=dir.getParent()){
            BasicFileAttributes attrs=Files.readAttributes(
                dir,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isDirectory()||attrs.isSymbolicLink()||
               attrs.fileKey()==null)
                throw new IOException(
                    "G21.77 RECOVERY_LOCK_ANCESTRY_UNSAFE_NO_GRANT "+dir);
        }
    }

    /** Existing lock leaf must never be a symlink, directory, or device. */
    private static void verifyForensicLockLeaf(Path lock)
        throws IOException{
        try{
            BasicFileAttributes a=Files.readAttributes(
                lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!a.isRegularFile()||a.isSymbolicLink())
                throw new IOException(
                    "G21.77 RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT "+lock);
        }catch(NoSuchFileException absent){
            // Opening a not-yet-created leaf is allowed only after
            // preflight; NOFOLLOW_LINKS on FileChannel.open closes the
            // final-component symlink race.
        }
    }

    static <T> T withExclusivePublicationBounded(
        Path accountFile,long timeoutMillis,Operation<T> operation
    )throws IOException{
        Objects.requireNonNull(operation,"operation");
        if(timeoutMillis<1L||timeoutMillis>60000L)
            throw new IllegalArgumentException(
                "G21.73 invalid bounded publication lock timeout");
        final Path lock=lockPath(accountFile);
        // Refuse redirected ancestors before creating the lock leaf.
        verifyForensicLockAncestry(lock);
        verifyForensicLockLeaf(lock);
        final long started=System.nanoTime();
        final long budget=TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        final JvmLease lease=acquire(lock);
        boolean jvmOwned=false;
        try{
            try{
                jvmOwned=lease.monitor.tryLock(
                    budget,TimeUnit.NANOSECONDS
                );
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "G21.73 RECOVERY_PUBLICATION_INTERRUPTED_NO_GRANT",
                    interrupted
                );
            }
            if(!jvmOwned)
                throw new IOException(
                    "G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT"
                );
            verifyForensicLockAncestry(lock);
            verifyForensicLockLeaf(lock);
            try(FileChannel channel=FileChannel.open(
                    lock,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS
                )){
                verifyForensicLockAncestry(lock);
                for(;;){
                    FileLock osOwned=null;
                    try{
                        osOwned=channel.tryLock();
                    }catch(OverlappingFileLockException busy){
                        // Another channel, possibly in another JVM,
                        // already owns this stable advisory lock.
                    }
                    if(osOwned!=null){
                        try(FileLock held=osOwned){
                            if(!held.isValid())
                                throw new IOException(
                                    "G21.73 RECOVERY_PUBLICATION_INVALID_LOCK"
                                );
                            if(System.nanoTime()-started>=budget)
                                throw new IOException(
                                    "G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT"
                                );
                            return operation.execute();
                        }
                    }
                    long remaining=budget-
                        (System.nanoTime()-started);
                    if(remaining<=0L)
                        throw new IOException(
                            "G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT"
                        );
                    try{
                        TimeUnit.NANOSECONDS.sleep(
                            Math.min(remaining,
                                TimeUnit.MILLISECONDS.toNanos(10L))
                        );
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "G21.73 RECOVERY_PUBLICATION_INTERRUPTED_NO_GRANT",
                            interrupted
                        );
                    }
                }
            }
        }finally{
            if(jvmOwned)lease.monitor.unlock();
            release(lock,lease);
        }
    }

    private MailboxAccountPublicationCoordinator(){}
}

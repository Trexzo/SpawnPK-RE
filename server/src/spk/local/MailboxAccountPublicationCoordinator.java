package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        final Object monitor=new Object();
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
            synchronized(lease.monitor){
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
            }
        }finally{
            release(lock,lease);
        }
    }

    private MailboxAccountPublicationCoordinator(){}
}

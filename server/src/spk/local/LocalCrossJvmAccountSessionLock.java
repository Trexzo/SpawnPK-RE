package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * G21.185 EXPERIMENTAL cooperating-process session exclusion.
 *
 * A stable sibling file is locked through the operating system for the
 * entire admitted session; its filename is NEVER unlinked on release.
 * This does not fence uncooperative/raw account writers, provide durable
 * negative intent after JVM death, or defend against hostile path
 * replacement/unlink races. Enable only before any sessions are admitted.
 */
final class LocalCrossJvmAccountSessionLock {
    static final String PROPERTY=
        "spk.local.experimentalCrossJvmAccountSessionLock";
    // G21.221 default-OFF advisory ONLY: check that the stable lock
    // NAME still resolves to the originally observed filesystem object
    // at a later owner check. This cannot close the external swap race.
    static final String CONTINUITY_PROPERTY=
        "spk.local.experimentalSessionLockObjectContinuity";


    private LocalCrossJvmAccountSessionLock(){}

    static final class Handle implements AutoCloseable {
        private final Path file;
        private final FileChannel channel;
        private final FileLock lock;
        private final boolean continuityEnabled;
        private final Object observedFileKey;
        private boolean closed;

        private Handle(
            Path file,FileChannel channel,FileLock lock,
            boolean continuityEnabled,Object observedFileKey
        ){
            this.file=file;
            this.channel=channel;
            this.lock=lock;
            this.continuityEnabled=continuityEnabled;
            this.observedFileKey=observedFileKey;
        }

        void requireOriginalLockNameStillPresent()throws IOException{
            if(!continuityEnabled)return;
            final BasicFileAttributes attrs;
            try{
                attrs=Files.readAttributes(
                    file,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            }catch(IOException missing){
                throw new IOException(
                    "G21.221 SESSION_LOCK_FILE_OBJECT_CHANGED"+
                    " originalNameMissing=true",missing);
            }
            if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
               attrs.fileKey()==null||
               !observedFileKey.equals(attrs.fileKey()))
                throw new IOException(
                    "G21.221 SESSION_LOCK_FILE_OBJECT_CHANGED"+
                    " observedOriginalKeyMismatch=true");
        }

        @Override public synchronized void close()throws IOException{
            if(closed)return;
            // A failure to release must not be hidden as a successful
            // session handoff; the same-JVM owner retains its token.
            lock.release();
            channel.close();
            closed=true;
        }

        /**
         * R61: operation-scoped borrowing of the ORIGINAL acquired OS
         * lock. The owner keeps sole close authority; this never
         * creates a second FileLock or releases the borrowed handle.
         * Synchronize with close() so the OS lock cannot be closed
         * by the cooperative owner during the protected operation.
         *
         * No account ownership is authenticated by this method.
         */
        @FunctionalInterface interface LockedOperation<T> {
            T execute()throws IOException;
        }
        synchronized <T> T withHeldOriginalAccount(
            Path exactAccount,LockedOperation<T> operation
        )throws IOException{
            if(exactAccount==null||operation==null)
                throw new IOException("R61_BORROWED_LOCK_ARGUMENT_INVALID");
            final Path canonical=exactAccount.toAbsolutePath().normalize();
            if(canonical.getParent()==null||
               canonical.getFileName()==null)
                throw new IOException("R61_BORROWED_LOCK_ACCOUNT_INVALID");
            final Path expected=canonical.resolveSibling(
                canonical.getFileName().toString()+".session.lck");
            // Exact selected path, never a filename-only or same-
            // parent alias comparison.
            if(!file.equals(expected))
                throw new IOException(
                    "R61_BORROWED_LOCK_WRONG_ACCOUNT_DENIED");
            if(closed||!channel.isOpen()||!lock.isValid())
                throw new IOException(
                    "R61_BORROWED_LOCK_RELEASED_DENIED");
            requireOriginalLockNameStillPresent();
            T result=operation.execute();
            if(closed||!channel.isOpen()||!lock.isValid())
                throw new IOException(
                    "R61_BORROWED_LOCK_RELEASED_DURING_OPERATION");
            requireOriginalLockNameStillPresent();
            return result;
        }

        Path fileForTest(){return file;}
    }

    // R34 reuse: opt-in same-JVM inode guard also needs NOFOLLOW
    // ancestor checks when the separate cross-JVM OS lock is disabled.
    // The scan is observational, not an atomic path-component pin.
    static void requireNoSymlinkAncestors(Path parent)
        throws IOException{
        Path walk=parent;
        while(walk!=null){
            if(Files.isSymbolicLink(walk))
                throw new IOException(
                    "G21.226 ACCOUNT_LOCK_SYMLINK_ANCESTOR_NOFOLLOW");
            if(Files.exists(walk,LinkOption.NOFOLLOW_LINKS)&&
               !Files.isDirectory(walk,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "G21.226 ACCOUNT_LOCK_NON_DIRECTORY_ANCESTOR");
            walk=walk.getParent();
        }
    }

    static Handle acquire(Path accountFile)throws IOException{
        if(accountFile==null)
            throw new NullPointerException("accountFile");
        if(Boolean.getBoolean(CONTINUITY_PROPERTY)&&
           !Boolean.getBoolean(PROPERTY))
            throw new IOException(
                "G21.221 CONTINUITY_REQUIRES_CROSS_JVM_LOCK");
        Path canonical=accountFile.toAbsolutePath().normalize();
        Path parent=canonical.getParent();
        if(parent==null)
            throw new IOException(
                "G21.185 ACCOUNT_LOCK_NO_PARENT");
        // G21.226: checking only the immediate parent missed a
        // symlinked grandparent. Reject existing symbolic-link/non-dir
        // ancestors BEFORE directory creation and recheck afterward.
        // This is a best-effort NOFOLLOW preflight, not atomic against
        // a hostile concurrent pathname swap or durable owner epoch.
        requireNoSymlinkAncestors(parent);
        Files.createDirectories(parent);
        requireNoSymlinkAncestors(parent);
        Path lockPath=canonical.resolveSibling(
            canonical.getFileName().toString()+".session.lck");

        FileChannel channel=FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        );

        boolean transferred=false;
        try{
            if(!Files.isRegularFile(
                    lockPath,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "G21.185 ACCOUNT_LOCK_NOT_REGULAR");
            final FileLock lock;
            try{
                lock=channel.tryLock();
            }catch(OverlappingFileLockException busy){
                throw new IOException(
                    "G21.185 ACCOUNT_SESSION_LOCK_BUSY",busy);
            }
            if(lock==null)
                throw new IOException(
                    "G21.185 ACCOUNT_SESSION_LOCK_BUSY");
            boolean continuity=Boolean.getBoolean(
                CONTINUITY_PROPERTY);
            Object key=null;
            if(continuity){
                BasicFileAttributes attrs=Files.readAttributes(
                    lockPath,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
                   attrs.fileKey()==null)
                    throw new IOException(
                        "G21.221 INITIAL_LOCK_FILE_IDENTITY_UNSAFE");
                key=attrs.fileKey();
            }
            Handle handle=new Handle(
                lockPath,channel,lock,continuity,key);
            handle.requireOriginalLockNameStillPresent();
            transferred=true;
            return handle;
        }finally{
            if(!transferred)
                channel.close();
        }
    }
}

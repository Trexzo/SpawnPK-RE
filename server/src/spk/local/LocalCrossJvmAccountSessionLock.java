package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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

    private LocalCrossJvmAccountSessionLock(){}

    static final class Handle implements AutoCloseable {
        private final Path file;
        private final FileChannel channel;
        private final FileLock lock;
        private boolean closed;

        private Handle(Path file,FileChannel channel,FileLock lock){
            this.file=file;
            this.channel=channel;
            this.lock=lock;
        }

        @Override public synchronized void close()throws IOException{
            if(closed)return;
            // A failure to release must not be hidden as a successful
            // session handoff; the same-JVM owner retains its token.
            lock.release();
            channel.close();
            closed=true;
        }

        Path fileForTest(){return file;}
    }

    static Handle acquire(Path accountFile)throws IOException{
        if(accountFile==null)
            throw new NullPointerException("accountFile");
        Path canonical=accountFile.toAbsolutePath().normalize();
        Path parent=canonical.getParent();
        if(parent==null)
            throw new IOException(
                "G21.185 ACCOUNT_LOCK_NO_PARENT");
        Files.createDirectories(parent);
        if(Files.isSymbolicLink(parent))
            throw new IOException(
                "G21.185 ACCOUNT_LOCK_SYMLINK_PARENT");
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
            Handle handle=new Handle(lockPath,channel,lock);
            transferred=true;
            return handle;
        }finally{
            if(!transferred)
                channel.close();
        }
    }
}

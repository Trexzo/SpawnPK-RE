package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * G21.38: narrow, cooperating-writer file publication boundary.
 *
 * The SAME canonical account path is used by FilePlayerRepository's
 * admitted World save and MailboxDurableReviewFence's no-clobber
 * publisher. A stable sibling lock file synchronizes the two operations
 * across JVMs on file systems honouring Java's advisory FileLock.
 *
 * Only the final review-marker publish or file replacement is locked;
 * normal snapshot encoding and World gameplay never run under this lock.
 * This is NOT a durable multi-file transaction, a hardware crash
 * guarantee, protection from uncooperative external writers, or a
 * reward-grant authority. Never delete the lock file: unlinking it could
 * allow concurrent file-lock generations on the same account.
 */
final class MailboxAccountPublicationCoordinator {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2138_COOPERATING_FILE_PUBLICATION_NO_GRANT";

    private static final Object SAME_JVM_LOCK=new Object();
    private static final String SUFFIX=
        ".g2138-mailbox-publication.lock";

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

    static <T> T withExclusivePublication(
        Path accountFile,Operation<T> operation
    )throws IOException{
        Objects.requireNonNull(operation,"operation");
        final Path lock=lockPath(accountFile);
        // This monitor prevents OverlappingFileLockException between
        // separate writer instances within the same JVM; the FileLock
        // coordinates separate cooperating JVM processes.
        synchronized(SAME_JVM_LOCK){
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
    }

    private MailboxAccountPublicationCoordinator(){}
}

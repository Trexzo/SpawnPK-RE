package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * G21.47 negative-only receipt-uncertainty marker.
 *
 * publishWhileAccountLocked MUST be called inside the same
 * MailboxAccountPublicationCoordinator lock as the strict account
 * replacement. This marker is deliberately NOT a reward COMMIT record,
 * contains no positive claim identity and is NEVER auto-removed.
 * A crash between account move and publication is not protected.
 */
final class MailboxStrictUncertainFence {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2147_STRICT_UNCONFIRMED_NO_GRANT";
    private static final String SUFFIX=".g2147-strict-uncertain";
    private static final byte[] CONTENT=(
        "SPK-G2147-STRICT-PREPARED-UNCERTAIN-V1\n"+
        "REVIEW_REQUIRED_NO_GRANT\n"+
        "NO_REPLAY_NO_ROLLBACK_NO_AUTO_RELEASE\n"
    ).getBytes(StandardCharsets.US_ASCII);

    static Path path(Path accountFile){
        Path absolute=accountFile.toAbsolutePath().normalize();
        return absolute.resolveSibling(
            absolute.getFileName().toString()+SUFFIX
        );
    }

    static boolean present(Path accountFile)throws IOException{
        try{
            Files.readAttributes(
                path(accountFile),BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            return true;
        }catch(NoSuchFileException absent){
            return false;
        }
    }

    static void requireClear(Path accountFile)throws IOException{
        if(present(accountFile))
            throw new IOException(
                "G21.47 STRICT_UNCONFIRMED_NEGATIVE_FENCE "+
                "accountFile="+accountFile+
                " action=MANUAL_REVIEW_REJECT_WORLD"
            );
    }

    /**
     * This does not acquire a second FileLock: caller already owns
     * this account's publication lock, which prevents lock recursion.
     */
    static void publishWhileAccountLocked(Path accountFile)
        throws IOException{
        Path marker=path(accountFile);
        if(present(accountFile))
            return; // Existing marker is immutable and already vetoes.
        Path parent=marker.getParent();
        Files.createDirectories(parent);
        Path tmp=Files.createTempFile(
            parent,marker.getFileName().toString()+".writing-",".tmp"
        );
        try{
            try(FileChannel channel=FileChannel.open(
                    tmp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer bytes=ByteBuffer.wrap(CONTENT);
                while(bytes.hasRemaining())
                    channel.write(bytes);
                channel.force(true);
            }
            // Exclusive hardlink name creation: no clobber on conflict.
            // A conflicting present name still enforces negative veto.
            try{
                Files.createLink(marker,tmp);
            }catch(java.nio.file.FileAlreadyExistsException exists){
                return;
            }
            try(FileChannel directory=FileChannel.open(
                    parent,StandardOpenOption.READ)){
                directory.force(true);
            }
        }finally{
            Files.deleteIfExists(tmp);
        }
    }

    private MailboxStrictUncertainFence(){}
}

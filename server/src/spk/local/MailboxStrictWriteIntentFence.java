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
import java.util.Arrays;
import java.util.Objects;

/**
 * G21.48: transient NEGATIVE, write-ahead intent for a concrete
 * file-backed strict PREPARED World checkpoint.
 *
 * The marker is published + directory-forced BEFORE account ATOMIC_MOVE
 * and removed + directory-forced only AFTER account force and final
 * owner-congruence check. A stranded/malformed/symlink marker is an
 * admission veto. Not a positive Mailbox/item transaction receipt.
 *
 * Both methods MUST execute inside the same account's G21.38-40
 * MailboxAccountPublicationCoordinator lock; no nested acquisition.
 * Permanent G21.32 and G21.47 negative markers are never cleared.
 */
final class MailboxStrictWriteIntentFence {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2148_STRICT_WRITE_INTENT_NO_GRANT";
    static final String SUFFIX=".g2148-strict-write-intent";
    private static final String FORMAT=
        "SPK-G2148-STRICT-WRITE-IN-PROGRESS-V1";

    private final FilePlayerRepository.PathResolver resolver;

    MailboxStrictWriteIntentFence(
        FilePlayerRepository.PathResolver resolver
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
    }

    Path fencePath(String account){
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.48 noncanonical account"
            );
        Path file=Objects.requireNonNull(
            resolver.resolve(account),"account path"
        ).toAbsolutePath().normalize();
        return file.resolveSibling(
            file.getFileName().toString()+SUFFIX
        );
    }

    boolean present(String account)throws IOException{
        try{
            Files.readAttributes(
                fencePath(account),BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            return true;
        }catch(NoSuchFileException missing){
            return false;
        }
    }

    private static byte[] record(String account,String sha)
        throws IOException{
        if(sha==null||!sha.matches("[0-9a-f]{64}"))
            throw new IOException("G21.48 invalid prepared digest");
        return (FORMAT+"\n"+account+"\n"+sha+"\n"+
            "IN_PROGRESS_NO_GRANT\n").getBytes(
                StandardCharsets.US_ASCII
            );
    }

    void armInsidePublicationLock(
        String account,String preparedSha
    )throws IOException{
        Path marker=fencePath(account);
        if(present(account))
            throw new IOException(
                "G21.48 existing strict write intent; manual review"
            );
        byte[] bytes=record(account,preparedSha);
        Path parent=Objects.requireNonNull(
            marker.getParent(),"intent parent"
        );
        Files.createDirectories(parent);
        Path temp=Files.createTempFile(
            parent,marker.getFileName().toString()+".write-",".tmp"
        );
        try{
            try(FileChannel channel=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            // Publish write-ahead BEFORE modifying account file.
            // No replace/copy fallback; any duplicate fails closed.
            Files.createLink(marker,temp);
            forceDirectory(parent);
        }finally{
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Only the SAME confirmed strict operation can clean up its
     * transient in-progress intent. This is NOT an automatic release
     * API for permanent negative review markers.
     */
    void clearOnlyAfterConfirmedInsidePublicationLock(
        String account,String expectedPreparedSha
    )throws IOException{
        Path marker=fencePath(account);
        byte[] expected=record(account,expectedPreparedSha);
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)||
           !Arrays.equals(
               MailboxNegativeMarkerBoundedRead.read(
                   marker,expected.length,expected.length
               ),expected))
            throw new IOException(
                "G21.48 intent absent or identity diverged; "+
                "no confirmed checkpoint receipt"
            );
        Files.delete(marker);
        forceDirectory(marker.getParent());
    }

    private static void forceDirectory(Path parent)throws IOException{
        try(FileChannel directory=FileChannel.open(
                parent,StandardOpenOption.READ)){
            directory.force(true);
        }
    }
}

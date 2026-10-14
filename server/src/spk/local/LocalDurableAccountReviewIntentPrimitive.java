package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * G21.188 EXPERIMENTAL STANDALONE PRIMITIVE: a durable, NO-RELEASE,
 * fail-closed account review-intent witness. NOT wired to login or
 * world persistence; therefore NOT an account crash recovery solution.
 *
 * A forced ACTIVE intent is never automatically deleted or cleared.
 * Directory fsync failure also leaves the intent as an untrusted
 * fail-closed negative. No grant, replay, repair, or cleanup authority.
 */
final class LocalDurableAccountReviewIntentPrimitive {
    private static final String MAGIC="G21188_ACTIVE_REVIEW_V1";

    private LocalDurableAccountReviewIntentPrimitive(){}

    static Path markerPath(Path accountFile)throws IOException{
        if(accountFile==null)
            throw new IOException("G21.188 NULL_ACCOUNT_PATH");
        Path account=accountFile.toAbsolutePath().normalize();
        Path name=account.getFileName();
        if(name==null||account.getParent()==null)
            throw new IOException("G21.188 INVALID_ACCOUNT_PATH");
        return account.resolveSibling(name.toString()+
            ".g21188-active-review");
    }

    static void requireNoReviewMarker(Path accountFile)
        throws IOException{
        Path marker=markerPath(accountFile);
        // Existence, not the contents/validity of a marker, is a
        // negative. Malformed or hostile sidecars are NEVER ignored.
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "G21.188 ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY");
        // Fail closed on inaccessible paths, not just existing leaves.
        Path parent=marker.getParent();
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(accountFile,false);
        if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("G21.188 ACCOUNT_INTENT_PARENT_UNSAFE");
        try(java.nio.file.DirectoryStream<Path> verify=
                Files.newDirectoryStream(parent)){
            // Opening the verified directory is an accessibility
            // preflight, not a defense against raw path replacement.
        }
    }

    static Path writeActiveReview(Path accountFile)throws IOException{
        Path account=accountFile.toAbsolutePath().normalize();
        Path marker=markerPath(account);
        requireNoReviewMarker(account);
        Path parent=marker.getParent();

        String witness=
            MAGIC+"\n"+
            "accountSha256="+pathFingerprint(account)+"\n"+
            "untrustedSessionNonce="+UUID.randomUUID()+"\n"+
            "state=ACTIVE_NEEDS_MANUAL_REVIEW\n";

        boolean allocated=false;
        try(FileChannel file=FileChannel.open(marker,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            allocated=true;
            ByteBuffer bytes=ByteBuffer.wrap(
                witness.getBytes(StandardCharsets.UTF_8));
            while(bytes.hasRemaining())
                file.write(bytes);
            file.force(true);
        }catch(java.nio.file.FileAlreadyExistsException existing){
            throw new IOException(
                "G21.188 ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY",
                existing);
        }

        if(!allocated)
            throw new IOException("G21.188 INTENT_NOT_ALLOCATED");

        // In particular, do not report a successful durable creation
        // merely because the intent's bytes were forced. The parent's
        // directory entry also has to be forced; unsupported filesystems
        // fail closed, retaining the marker with no automatic rollback.
        try(FileChannel directory=FileChannel.open(parent,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            directory.force(true);
        }

        BasicFileAttributes attrs=Files.readAttributes(
            marker,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null)
            throw new IOException(
                "G21.188 INTENT_POSTWRITE_IDENTITY_UNSAFE_NO_AUTHORITY");

        requireReviewMarker(account);
        return marker;
    }

    static void requireReviewMarker(Path accountFile)
        throws IOException{
        Path marker=markerPath(accountFile);
        BasicFileAttributes attributes=Files.readAttributes(
            marker,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        if(!attributes.isRegularFile()||
           attributes.isSymbolicLink())
            throw new IOException(
                "G21.188 REVIEW_MARKER_NOT_REGULAR_NO_AUTHORITY");
        // The complete first line is format evidence only.
        // Unexpected/malformed content is NOT treated as clean.
        String text=new String(
            Files.readAllBytes(marker),StandardCharsets.UTF_8);
        if(!text.startsWith(MAGIC+"\n")||
           !text.contains("state=ACTIVE_NEEDS_MANUAL_REVIEW\n"))
            throw new IOException(
                "G21.188 REVIEW_MARKER_INVALID_NO_AUTO_CLEAR");
    }

    private static String pathFingerprint(Path path)throws IOException{
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                path.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();
            for(byte value:digest){
                String pair=Integer.toHexString(value&255);
                if(pair.length()==1)hex.append('0');
                hex.append(pair);
            }
            return hex.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException(
                "G21.188 SHA256_UNAVAILABLE",unavailable);
        }
    }
}

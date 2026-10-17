package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
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
    private static final int MAX_REVIEW_MARKER_BYTES=4096;
    // Test-only deterministic race injection; never configured by
    // production code and never used to grant owner authority.
    private static volatile Runnable afterMarkerReadForTest;
    static void setAfterMarkerReadForTest(Runnable callback){
        afterMarkerReadForTest=callback;
    }


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
        BasicFileAttributes initial=Files.readAttributes(
            marker,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        if(!initial.isRegularFile()||initial.isSymbolicLink()||
           initial.fileKey()==null)
            throw new IOException(
                "G21.188 REVIEW_MARKER_NOT_REGULAR_NO_AUTHORITY");
        if(initial.size()>MAX_REVIEW_MARKER_BYTES)
            throw new IOException(
                "G21.222 REVIEW_MARKER_OVERSIZE_NEGATIVE_NO_CLEAR");

        // G21.222: bound memory even when the file grows between
        // stat and read; open with NOFOLLOW so a swapped symlink leaf
        // cannot redirect this inspection to another filesystem file.
        final java.nio.ByteBuffer bounded=java.nio.ByteBuffer.allocate(
            MAX_REVIEW_MARKER_BYTES+1);
        try(FileChannel input=FileChannel.open(
                marker,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            while(bounded.hasRemaining()){
                int count=input.read(bounded);
                if(count<0)break;
                if(count==0)
                    throw new IOException(
                        "G21.222 REVIEW_MARKER_READ_NO_PROGRESS");
            }
        }
        if(bounded.position()>MAX_REVIEW_MARKER_BYTES)
            throw new IOException(
                "G21.222 REVIEW_MARKER_OVERSIZE_NEGATIVE_NO_CLEAR");
        // G21.224: an in-place mutation can retain the SAME inode
        // even as its contents/length change while being inspected.
        // This read-only diagnostic must not report that snapshot as
        // a stable well-formed marker. This is NOT an integrity MAC.
        Runnable raceHook=afterMarkerReadForTest;
        if(raceHook!=null)raceHook.run();
        // G21.225: metadata alone is not content integrity. A writer
        // can rewrite the SAME inode and restore its recorded mtime.
        // Read the bounded content AGAIN through NOFOLLOW and require
        // byte-for-byte equality with the first read. This narrows a
        // diagnostic race; it is not an authenticated durable receipt.
        java.nio.ByteBuffer verification=java.nio.ByteBuffer.allocate(
            MAX_REVIEW_MARKER_BYTES+1);
        try(FileChannel input=FileChannel.open(
                marker,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            while(verification.hasRemaining()){
                int count=input.read(verification);
                if(count<0)break;
                if(count==0)
                    throw new IOException(
                        "G21.225 REVIEW_MARKER_SECOND_READ_NO_PROGRESS");
            }
        }
        if(verification.position()!=bounded.position())
            throw new IOException(
                "G21.225 REVIEW_MARKER_CONTENT_DRIFT_NEGATIVE");
        for(int i=0;i<bounded.position();i++){
            if(verification.get(i)!=bounded.get(i))
                throw new IOException(
                    "G21.225 REVIEW_MARKER_CONTENT_DRIFT_NEGATIVE");
        }

        BasicFileAttributes after=Files.readAttributes(
            marker,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.isSymbolicLink()||
           after.fileKey()==null||
           !initial.fileKey().equals(after.fileKey()))
            throw new IOException(
                "G21.222 REVIEW_MARKER_FILE_IDENTITY_DIVERGED");
        if(after.size()!=initial.size()||
           after.size()!=bounded.position()||
           !initial.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IOException(
                "G21.224 REVIEW_MARKER_MUTATED_DURING_READ_NEGATIVE");

        bounded.flip();
        final String text;
        try{
            text=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(bounded).toString();
        }catch(CharacterCodingException invalid){
            throw new IOException(
                "G21.222 REVIEW_MARKER_MALFORMED_UTF8_NEGATIVE",
                invalid);
        }
        // G21.223: format evidence remains NEGATIVE ONLY, but exact
        // parsing also prevents misleading cross-account/duplicated
        // marker fields from being misrepresented as an authenticated
        // marker belonging to the requested account. A valid marker
        // NEVER authorizes retirement or positive admission.
        if(!text.startsWith(MAGIC+"\n")||
           !text.contains("state=ACTIVE_NEEDS_MANUAL_REVIEW\n"))
            throw new IOException(
                "G21.188 REVIEW_MARKER_INVALID_NO_AUTO_CLEAR");
        final String[] fields=text.split("\\n",-1);
        final String expectedFingerprint=
            pathFingerprint(accountFile.toAbsolutePath().normalize());
        boolean valid=fields.length==5&&
            fields[4].isEmpty()&&
            MAGIC.equals(fields[0])&&
            ("accountSha256="+expectedFingerprint).equals(fields[1])&&
            "state=ACTIVE_NEEDS_MANUAL_REVIEW".equals(fields[3]);
        if(!valid)
            throw new IOException(
                "G21.223 REVIEW_MARKER_CANONICAL_FIELDS_REQUIRED");
        String id=fields[2];
        if(!id.startsWith("untrustedSessionNonce="))
            throw new IOException(
                "G21.223 REVIEW_MARKER_NONCE_FIELD_MISSING");
        String nonce=id.substring("untrustedSessionNonce=".length());
        try{
            UUID parsed=UUID.fromString(nonce);
            if(!nonce.equals(parsed.toString()))
                throw new IllegalArgumentException("noncanonical UUID");
        }catch(IllegalArgumentException invalid){
            throw new IOException(
                "G21.223 REVIEW_MARKER_NONCANONICAL_NONCE",invalid);
        }
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

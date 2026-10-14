package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * G21.191: standalone, NOT WIRED, account-file postimage publication
 * primitive for controlled laboratory cutpoint evidence.
 *
 * Force the unique temp file, require atomic rename (NO fallback),
 * force the parent directory, then independently read and verify the
 * exact raw postimage. This is NOT an automatic ACTIVE intent receipt:
 * there is no account epoch, owned session lock, marker retirement or
 * crash-failure cleanup. Existing FilePlayerRepository is UNCHANGED.
 */
final class LocalForcedAccountPostimagePrimitive {
    static final int MAX_BYTES=8*1024*1024;

    enum Stage {
        TEMP_FILE_FORCED,
        ATOMIC_REPLACE_BEFORE_DIR_FORCE,
        DIR_FORCED_BEFORE_VERIFY
    }

    @FunctionalInterface interface Cutpoint {
        void reached(Stage stage)throws IOException;
    }

    static final class Receipt {
        final Path file;
        final String sha256;
        final int length;
        final boolean fileForced;
        final boolean directoryForced;
        final boolean exactReadback;

        private Receipt(Path file,String sha256,int length) {
            this.file=file;
            this.sha256=sha256;
            this.length=length;
            this.fileForced=true;
            this.directoryForced=true;
            this.exactReadback=true;
        }
    }

    private LocalForcedAccountPostimagePrimitive(){}

    static Receipt publish(Path requested,byte[] postimage)
        throws IOException {
        return publish(requested,postimage,stage->{});
    }

    static Receipt publish(
        Path requested,byte[] postimage,Cutpoint cutpoint
    )throws IOException {
        if(requested==null||postimage==null||cutpoint==null)
            throw new IOException("G21.191 NULL_PUBLISH_ARGUMENT");
        if(postimage.length==0||postimage.length>MAX_BYTES)
            throw new IOException(
                "G21.191 POSTIMAGE_SIZE_OUT_OF_BOUNDS");

        final Path file=requested.toAbsolutePath().normalize();
        final Path parent=file.getParent();
        if(parent==null||file.getFileName()==null)
            throw new IOException("G21.191 ACCOUNT_PATH_INVALID");

        // Exact snapshot path, no symlink ancestor by the current
        // cooperative repository policy. This does NOT pin raw-writer
        // or hostile rename/unlink adversaries across the operation.
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(file,false);
        if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("G21.191 PARENT_NOT_REGULAR_DIRECTORY");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
            BasicFileAttributes previous=Files.readAttributes(
                file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!previous.isRegularFile()||previous.isSymbolicLink())
                throw new IOException(
                    "G21.191 EXISTING_ACCOUNT_NOT_REGULAR");
        }

        final byte[] expected=sha256(postimage);
        // Unique temp. A failed publication deliberately leaves its
        // allocated temp for offline inspection, NEVER automatically
        // unlinks a foreign inode or ACTIVE review sidecar.
        final Path temporary=Files.createTempFile(
            parent,file.getFileName().toString()+".g21191-forced-",
            ".tmp");
        final BasicFileAttributes original=regularTemp(temporary);
        try(FileChannel channel=FileChannel.open(
                temporary,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer buffer=ByteBuffer.wrap(postimage);
            while(buffer.hasRemaining())
                channel.write(buffer);
            channel.force(true);
        }
        requireSameTemp(temporary,original);
        requireExact(temporary,postimage,expected);
        cutpoint.reached(Stage.TEMP_FILE_FORCED);

        requireSameTemp(temporary,original);
        requireExact(temporary,postimage,expected);
        try{
            Files.move(temporary,file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        }catch(AtomicMoveNotSupportedException unsupported){
            throw new IOException(
                "G21.191 ATOMIC_REPLACE_UNSUPPORTED_NO_FALLBACK",
                unsupported);
        }
        cutpoint.reached(Stage.ATOMIC_REPLACE_BEFORE_DIR_FORCE);

        // A rename visible to this JVM is not yet a durable account
        // publication receipt; a directory force is mandatory.
        try(FileChannel directory=FileChannel.open(
                parent,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            directory.force(true);
        }
        cutpoint.reached(Stage.DIR_FORCED_BEFORE_VERIFY);

        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(file,false);
        BasicFileAttributes published=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!published.isRegularFile()||published.isSymbolicLink()||
           published.size()!=postimage.length)
            throw new IOException(
                "G21.191 PUBLISHED_OBJECT_UNSAFE_NO_RECEIPT");
        requireExact(file,postimage,expected);
        return new Receipt(file,toHex(expected),postimage.length);
    }

    private static BasicFileAttributes regularTemp(Path temp)
        throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            temp,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()||a.isSymbolicLink()||a.fileKey()==null)
            throw new IOException(
                "G21.191 TEMP_IDENTITY_UNSAFE_NO_RECEIPT");
        return a;
    }

    private static void requireSameTemp(
        Path temp,BasicFileAttributes expected
    )throws IOException{
        BasicFileAttributes actual=regularTemp(temp);
        if(!expected.fileKey().equals(actual.fileKey()))
            throw new IOException(
                "G21.191 TEMP_INODE_CHANGED_NO_RECEIPT");
    }

    private static void requireExact(
        Path file,byte[] original,byte[] digest
    )throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.size()!=original.length)
            throw new IOException(
                "G21.191 POSTIMAGE_NOT_REGULAR_OR_SIZE_MISMATCH");
        byte[] actual=Files.readAllBytes(file);
        if(!MessageDigest.isEqual(sha256(actual),digest)||
           !Arrays.equals(actual,original))
            throw new IOException(
                "G21.191 POSTIMAGE_DIGEST_MISMATCH_NO_RECEIPT");
    }

    private static byte[] sha256(byte[] bytes)throws IOException{
        try{
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }catch(NoSuchAlgorithmException missing){
            throw new IOException("G21.191 SHA256_UNAVAILABLE",missing);
        }
    }

    private static String toHex(byte[] bytes){
        StringBuilder b=new StringBuilder(bytes.length*2);
        for(byte x:bytes){
            String part=Integer.toHexString(x&255);
            if(part.length()==1)b.append('0');
            b.append(part);
        }
        return b.toString();
    }
}

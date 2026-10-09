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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * G21.47: independently persisted NEGATIVE review obligation when a
 * strict PREPARED World account file has already been published but
 * its World owner no longer matches, so no ordinary strict receipt
 * may be returned. This is NOT a Mailbox reward-settlement record.
 *
 * The publisher is called from inside the G21.38 account FileLock.
 * It MUST NOT reenter MailboxAccountPublicationCoordinator here.
 * An uncooperative writer or a process crash before marker creation
 * remains outside the guarantee.
 */
final class MailboxStrictUnconfirmedReviewFence {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2147_STRICT_POSTPUBLICATION_NEGATIVE_NO_GRANT";
    static final String STATE="REVIEW_REQUIRED_NO_GRANT";
    private static final String FORMAT=
        "SPK-G2147-STRICT-POSTPUBLICATION-UNCERTAIN-V1";
    private static final String SUFFIX=
        ".g2147-strict-postpublication-review";
    private static final int MAX_BYTES=512;

    static final class Record {
        final String account;
        final String strictSnapshotSha256;
        final String state=STATE;
        final String authority=AUTHORITY;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean admissionAuthorized=false;

        Record(String account,String sha){
            this.account=account;
            this.strictSnapshotSha256=sha;
        }
    }

    static Path fencePath(Path accountFile){
        Path file=Objects.requireNonNull(
            accountFile,"accountFile"
        ).toAbsolutePath().normalize();
        return file.resolveSibling(
            file.getFileName().toString()+SUFFIX
        );
    }

    /** Presence is negative authority even when content is corrupted. */
    static boolean present(Path accountFile)throws IOException{
        try{
            Files.readAttributes(
                fencePath(accountFile),
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
            return true;
        }catch(NoSuchFileException missing){
            return false;
        }
    }

    /**
     * Called ONLY after a strict account ATOMIC_MOVE + directory force
     * while the same publication FileLock is still held. A result or
     * exception is never a positive grant or rollback authorization.
     *
     * Exclusive hard-link publication cannot overwrite an existing
     * (even corrupted) marker. Unsupported hard links fail closed.
     */
    static Record armInsidePublicationLock(
        Path accountFile,String account,String strictSha256
    )throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}")||
           !isSha(strictSha256))
            throw new IOException(
                "G21.47 noncanonical strict quarantine identity"
            );
        Path marker=fencePath(accountFile);
        Path parent=Objects.requireNonNull(
            marker.getParent(),"G21.47 marker parent"
        );
        if(present(accountFile))
            throw new IOException(
                "G21.47 strict postpublication marker already exists"
            );
        Record record=new Record(account,strictSha256);
        byte[] payload=payload(record);
        byte[] data=(new String(payload,StandardCharsets.US_ASCII)+
            hex(sha256(payload))+"\n").getBytes(StandardCharsets.US_ASCII);
        if(data.length>MAX_BYTES)
            throw new IOException("G21.47 oversized review marker");

        Files.createDirectories(parent);
        Path temp=Files.createTempFile(
            parent,marker.getFileName().toString()+".g2147-",".tmp"
        );
        try{
            try(FileChannel channel=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer bytes=ByteBuffer.wrap(data);
                while(bytes.hasRemaining())channel.write(bytes);
                channel.force(true);
            }
            // Create final name atomically, exclusively; no overwrite.
            Files.createLink(marker,temp);
            Files.delete(temp);
            try(FileChannel directory=FileChannel.open(
                    parent,StandardOpenOption.READ)){
                directory.force(true);
            }
            return record;
        }finally{
            // Never unlink the published marker, even after force failure.
            Files.deleteIfExists(temp);
        }
    }

    /** Diagnostic only: malformed content NEVER removes the veto. */
    static Record inspect(Path accountFile)throws IOException{
        byte[] bytes=Files.readAllBytes(fencePath(accountFile));
        if(bytes.length<80||bytes.length>MAX_BYTES)
            throw new IOException("G21.47 marker size invalid");
        String raw=new String(bytes,StandardCharsets.US_ASCII);
        if(!Arrays.equals(
                raw.getBytes(StandardCharsets.US_ASCII),bytes))
            throw new IOException("G21.47 marker is not ASCII");
        String[] parts=raw.split("\n",-1);
        if(parts.length!=6||!parts[5].isEmpty()||
           !FORMAT.equals(parts[0])||
           !STATE.equals(parts[1])||
           !parts[2].matches("[a-z0-9_-]{1,64}")||
           !isSha(parts[3])||
           !parts[4].matches("[0-9a-f]{64}"))
            throw new IOException("G21.47 marker format invalid");
        String prefix=parts[0]+"\n"+parts[1]+"\n"+
            parts[2]+"\n"+parts[3]+"\n";
        if(!parts[4].equals(hex(sha256(
                prefix.getBytes(StandardCharsets.US_ASCII)))))
            throw new IOException("G21.47 marker checksum invalid");
        return new Record(parts[2],parts[3]);
    }

    private static byte[] payload(Record record){
        return (FORMAT+"\n"+STATE+"\n"+
            record.account+"\n"+record.strictSnapshotSha256+"\n")
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean isSha(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }

    private static byte[] sha256(byte[] bytes){
        try{
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }catch(NoSuchAlgorithmException unavailable){
            throw new IllegalStateException(
                "SHA-256 required for strict negative review fence",
                unavailable
            );
        }
    }

    private static String hex(byte[] bytes){
        char[] result=new char[bytes.length*2];
        final char[] digits="0123456789abcdef".toCharArray();
        for(int i=0;i<bytes.length;i++){
            int unsigned=bytes[i]&255;
            result[2*i]=digits[unsigned>>>4];
            result[2*i+1]=digits[unsigned&15];
        }
        return new String(result);
    }

    private MailboxStrictUnconfirmedReviewFence(){}
}

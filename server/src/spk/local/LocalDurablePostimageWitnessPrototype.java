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
import java.util.Arrays;

/**
 * G21.199 UNUSED, NON-ADMITTING experimental diagnostic only.
 *
 * A forced immutable witness binds the structural G21.196 reservation
 * and generation-binding record to the bytes of a currently observed
 * account file. The caller MUST separately know the World final save
 * succeeded; this helper does not authenticate the caller/World,
 * guarantee the underlying account was forced, or confer release rights.
 *
 * The witness NEVER authorizes login, recovery, grant or retiring the
 * independent G21.188 ACTIVE marker. No runtime production wiring.
 */
final class LocalDurablePostimageWitnessPrototype {
    private static final String MAGIC="G21199_POSTIMAGE_WITNESS_V1";
    private static final String SUFFIX=".g21199-postimage-witness";
    private static final long MAX_ACCOUNT_BYTES=32L*1024L*1024L;
    private static final int MAX_WITNESS_BYTES=4096;

    private LocalDurablePostimageWitnessPrototype(){}

    static Path witnessPath(Path account)throws IOException{
        if(account==null)throw new IOException("G21.199 NULL_ACCOUNT");
        Path exact=account.toAbsolutePath().normalize();
        if(exact.getFileName()==null||exact.getParent()==null)
            throw new IOException("G21.199 INVALID_ACCOUNT");
        return exact.resolveSibling(exact.getFileName().toString()+SUFFIX);
    }

    private static void safeParent(Path account)throws IOException{
        MailboxAccountPublicationCoordinator
            .requireSafeWorldTempAncestry(account,false);
        Path parent=account.toAbsolutePath().normalize().getParent();
        if(parent==null||
           !Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("G21.199 UNSAFE_PARENT");
        try(java.nio.file.DirectoryStream<Path> stream=
                Files.newDirectoryStream(parent)){
            // Accessibility check, not a raw-writer race exclusion.
        }
    }

    private static byte[] readIdentity(Path file,long max)
        throws IOException{
        BasicFileAttributes before=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null||before.size()<=0||before.size()>max)
            throw new IOException("G21.199 UNSAFE_FILE_IDENTITY_OR_SIZE");
        byte[] data=Files.readAllBytes(file);
        BasicFileAttributes after=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(data.length==0||data.length>max||
           !after.isRegularFile()||after.isSymbolicLink()||
           !before.fileKey().equals(after.fileKey())||
           before.size()!=data.length||after.size()!=data.length)
            throw new IOException("G21.199 FILE_CHANGED_DURING_OBSERVATION");
        return data;
    }

    private static String sha(byte[] input)throws IOException{
        byte[] digest;
        try{
            digest=MessageDigest.getInstance("SHA-256").digest(input);
        }catch(NoSuchAlgorithmException failure){
            throw new IOException("G21.199 SHA256_UNAVAILABLE",failure);
        }
        StringBuilder out=new StringBuilder();
        for(byte b:digest){
            String p=Integer.toHexString(b&255);
            if(p.length()==1)out.append('0');
            out.append(p);
        }
        return out.toString();
    }

    private static LocalDurableOwnerEpochPrototype.Observation bound(
        Path account
    )throws IOException{
        LocalDurableOwnerEpochPrototype.Observation observed=
            LocalDurableOwnerEpochPrototype.inspect(account);
        if(!observed.generationBound||observed.worldGeneration<=0)
            throw new IOException("G21.199 NO_BOUND_GENERATION_NO_AUTHORITY");
        return observed;
    }

    static Path publishAfterConfirmedSave(Path accountFile)
        throws IOException{
        // Called only by a test after separately verifying a real final
        // World save. This API does NOT itself verify provenance or force.
        Path account=accountFile.toAbsolutePath().normalize();
        safeParent(account);
        LocalDurableOwnerEpochPrototype.Observation observed=bound(account);
        byte[] actual=readIdentity(account,MAX_ACCOUNT_BYTES);
        Path target=witnessPath(account);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("G21.199 WITNESS_ALREADY_EXISTS_NO_OVERWRITE");
        String data=MAGIC+"\n"+
            "reservationSha256="+observed.reservation.reservationSha+"\n"+
            "accountSha256="+observed.reservation.accountSha+"\n"+
            "worldGeneration="+observed.worldGeneration+"\n"+
            "accountFileSha256="+sha(actual)+"\n"+
            "accountSize="+actual.length+"\n"+
            "state=WITNESSED_NO_ADMISSION_OR_RETIREMENT\n";
        byte[] record=data.getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(target,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer bytes=ByteBuffer.wrap(record);
            while(bytes.hasRemaining())channel.write(bytes);
            channel.force(true);
        }catch(java.nio.file.FileAlreadyExistsException collision){
            throw new IOException(
                "G21.199 WITNESS_ALREADY_EXISTS_NO_OVERWRITE",collision);
        }
        // Failed directory sync leaves a negative/uncertain witness.
        try(FileChannel dir=FileChannel.open(target.getParent(),
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            dir.force(true);
        }
        if(!Arrays.equals(record,readIdentity(target,MAX_WITNESS_BYTES)))
            throw new IOException("G21.199 WITNESS_READBACK_MISMATCH");
        requireMatchingCurrentPostimage(account);
        return target;
    }

    static void requireMatchingCurrentPostimage(Path accountFile)
        throws IOException{
        Path account=accountFile.toAbsolutePath().normalize();
        safeParent(account);
        LocalDurableOwnerEpochPrototype.Observation observed=bound(account);
        String text=new String(readIdentity(
            witnessPath(account),MAX_WITNESS_BYTES),
            StandardCharsets.UTF_8);
        String[] lines=text.split("\n",-1);
        if(lines.length!=8||
           !MAGIC.equals(lines[0])||
           !lines[1].equals("reservationSha256="+
               observed.reservation.reservationSha)||
           !lines[2].equals("accountSha256="+
               observed.reservation.accountSha)||
           !lines[3].equals("worldGeneration="+
               observed.worldGeneration)||
           !lines[4].startsWith("accountFileSha256=")||
           !lines[5].startsWith("accountSize=")||
           !"state=WITNESSED_NO_ADMISSION_OR_RETIREMENT".equals(lines[6])||
           !lines[7].isEmpty())
            throw new IOException("G21.199 WITNESS_BINDING_MISMATCH_NO_AUTHORITY");
        byte[] bytes=readIdentity(account,MAX_ACCOUNT_BYTES);
        if(!lines[4].equals("accountFileSha256="+sha(bytes))||
           !lines[5].equals("accountSize="+bytes.length))
            throw new IOException("G21.199 ACCOUNT_POSTIMAGE_DRIFT_NO_AUTHORITY");
    }
}

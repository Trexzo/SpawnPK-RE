package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * G21.246 EXPERIMENTAL: unique, no-follow cooperating RAW account temp.
 *
 * This protects cooperating writers from the fixed historical .tmp leaf.
 * The last attribute observation is NOT an atomic external attack fence,
 * does not prove exact serialized bytes, and does not establish recovery.
 */
final class LocalOptInRawTemp {
    static final String PROPERTY=
        "spk.local.experimentalRawUniqueTempNoFollow";
    private LocalOptInRawTemp(){}

    static boolean enabled()throws IOException{
        if(!Boolean.getBoolean(PROPERTY))return false;
        if(!Boolean.getBoolean(
                LocalJvmAccountSessionLease.PROPERTY)||
           !Boolean.getBoolean(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_REQUIRES_LOCKED_CONTINUITY_OPT_IN");
        return true;
    }

    static Path create(Path account)throws IOException{
        Objects.requireNonNull(account,"account");
        return Files.createTempFile(
            account.getParent(),
            account.getFileName().toString()+".g21246-raw-",
            ".tmp"
        );
    }

    static BasicFileAttributes observe(Path temp)throws IOException{
        final BasicFileAttributes attrs;
        try{
            attrs=Files.readAttributes(
                temp,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(IOException problem){
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_OBJECT_CHANGED",problem);
        }
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null)
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_OBJECT_UNSAFE");
        return attrs;
    }

    static void requireSame(Path temp,BasicFileAttributes original)
        throws IOException{
        if(original==null)return;
        BasicFileAttributes now=observe(temp);
        if(!Objects.equals(original.fileKey(),now.fileKey())||
           !Objects.equals(original.creationTime(),now.creationTime()))
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_OBJECT_CHANGED");
    }

    // G21.247: bounded NOFOLLOW read of the same originally allocated
    // temp inode. This detects in-place byte edits after serialization,
    // even if length and timestamps are restored.
    private static byte[] fingerprint(
        Path temp,BasicFileAttributes original
    )throws IOException{
        requireSame(temp,original);
        final MessageDigest digest;
        try{
            digest=MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException(
                "G21.247 RAW_TEMP_SHA256_UNAVAILABLE",impossible);
        }
        final long maxBytes=64L*1024L*1024L;
        long count=0L;
        try(InputStream input=Files.newInputStream(
                temp,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] buf=new byte[8192];
            for(int n;(n=input.read(buf))!=-1;){
                count+=n;
                if(count>maxBytes)
                    throw new IOException(
                        "G21.247 SAFE_RAW_TEMP_EXCEEDS_64MIB_BOUND");
                digest.update(buf,0,n);
            }
        }
        requireSame(temp,original);
        return digest.digest();
    }

    static byte[] seal(
        Path temp,BasicFileAttributes original
    )throws IOException{
        return fingerprint(temp,original);
    }

    static void requireSealed(
        Path temp,BasicFileAttributes original,byte[] expected
    )throws IOException{
        if(expected==null||
           !MessageDigest.isEqual(expected,fingerprint(temp,original)))
            throw new IOException(
                "G21.247 SAFE_RAW_TEMP_BYTES_CHANGED");
    }

    static boolean stillOwns(Path temp,BasicFileAttributes original){
        if(original==null)return false;
        try{
            requireSame(temp,original);
            return true;
        }catch(IOException problem){
            return false;
        }
    }
}

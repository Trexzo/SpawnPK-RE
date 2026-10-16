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
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.289: a negative sidecar changed in place after the first bounded
 * account digest cannot produce a stable read-only forensic report,
 * even if inode, size and modification time appear unchanged.
 */
public final class G21289ForensicNegativeSidecarRestoredMtimeDriftTest {
    private G21289ForensicNegativeSidecarRestoredMtimeDriftTest(){}
    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError("G21.289 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21289-sidecar-drift-");
        try{
            Path selected=root.resolve("selected.properties");
            byte[] selectedBytes="selected-account-data".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selected,selectedBytes);
            Path sidecar=LocalDurableAccountReviewIntentPrimitive
                .markerPath(selected);
            byte[] original="forensic-marker-phase=BEFORE\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(sidecar,original);
            FileTime oldMtime=Files.getLastModifiedTime(sidecar,
                LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes identity=Files.readAttributes(sidecar,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(identity.isRegularFile()&&identity.fileKey()!=null,
                "valid regular original marker required");
            AtomicBoolean injected=new AtomicBoolean(false);
            boolean denied=false;
            try{
                LocalAccountForensicEvidence.inspect(selected,path->{
                    check(selected.toAbsolutePath().normalize().equals(path),
                        "wrong exact account at test seam");
                    try(FileChannel writable=FileChannel.open(sidecar,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)){
                        writable.position(original.length-3);
                        writable.write(ByteBuffer.wrap(new byte[]{'Z'}));
                        writable.force(true);
                    }
                    Files.setLastModifiedTime(sidecar,oldMtime);
                    BasicFileAttributes restored=Files.readAttributes(
                        sidecar,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    check(identity.fileKey().equals(restored.fileKey())&&
                        restored.size()==identity.size()&&
                        restored.lastModifiedTime().equals(oldMtime),
                        "real same-inode and metadata-restored edit required");
                    injected.set(true);
                });
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "G21.289 NEGATIVE_SIDECAR_BYTES_CHANGED_DURING_READ");
            }
            check(injected.get()&&denied,
                "metadata-restored marker mutation was not refused");
            byte[] postMarker=Files.readAllBytes(sidecar);
            check(postMarker.length==original.length&&
                !Arrays.equals(postMarker,original),
                "test did not modify a same-length sidecar");
            check(Arrays.equals(selectedBytes,Files.readAllBytes(selected)),
                "forensic read changed selected account bytes");
            LocalAccountForensicEvidence.Evidence stable=
                LocalAccountForensicEvidence.inspect(selected);
            check(stable.observedNegativeNames==1&&
                "PRESENT_REGULAR_NEGATIVE".equals(
                    stable.negativeSidecars.get("activeReview"))&&
                stable.report().contains("loginAuthorized=false")&&
                stable.report().contains("finalSaveAck=false"),
                "changed but stable negative sidecar promoted to authority");
            try(Stream<Path> files=Files.list(root)){
                check(files.count()==2,
                    "scanner created lock, temp or sidecar");
            }
            System.out.println(
                "G21289_FORENSIC_RESTORED_MTIME_SIDECAR_BYTES_DRIFT_PASS"+
                " exactSelectedReview=true"+
                " inodeSizeMtimeRestored=true"+
                " secondSidecarDigestVeto=true"+
                " negativeStillNotAuthority=true"+
                " selectedAccountUntouched=true");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path path:(Iterable<Path>)all.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}

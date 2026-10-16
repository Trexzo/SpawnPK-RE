package spk.local;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.290: a foreign account's negative marker byte drift, even at
 * the deterministic selected inspection seam, is not a global veto.
 */
public final class G21290ForensicUnrelatedMarkerByteDriftControlTest {
    private G21290ForensicUnrelatedMarkerByteDriftControlTest(){}
    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError("G21.290 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21290-unrelated-marker-");
        try{
            Path selected=root.resolve("selected.properties");
            Path other=root.resolve("other.properties");
            byte[] selectedData="selected bytes".getBytes(
                StandardCharsets.US_ASCII);
            byte[] otherData="other bytes".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selected,selectedData);
            Files.write(other,otherData);
            Path selectedMarker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(selected);
            Path otherMarker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(other);
            byte[] stableMarker="selected-phase=STABLE\n".getBytes(
                StandardCharsets.US_ASCII);
            byte[] changingMarker="other-phase=BEFORE\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(selectedMarker,stableMarker);
            Files.write(otherMarker,changingMarker);
            FileTime otherMtime=Files.getLastModifiedTime(otherMarker,
                LinkOption.NOFOLLOW_LINKS);
            AtomicBoolean modified=new AtomicBoolean(false);
            LocalAccountForensicEvidence.Evidence evidence=
                LocalAccountForensicEvidence.inspect(selected,path->{
                    check(selected.toAbsolutePath().normalize().equals(path),
                        "wrong selected account seam");
                    try(FileChannel writable=FileChannel.open(otherMarker,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)){
                        writable.position(changingMarker.length-3);
                        writable.write(ByteBuffer.wrap(new byte[]{'Z'}));
                        writable.force(true);
                    }
                    Files.setLastModifiedTime(otherMarker,otherMtime);
                    modified.set(true);
                });
            check(modified.get(),"unrelated marker test edit never ran");
            check("PRESENT_REGULAR".equals(evidence.accountState)&&
                evidence.observedNegativeNames==1&&
                "PRESENT_REGULAR_NEGATIVE".equals(
                    evidence.negativeSidecars.get("activeReview")),
                "foreign marker edit influenced selected evidence scope");
            check(Arrays.equals(stableMarker,Files.readAllBytes(selectedMarker))&&
                Arrays.equals(selectedData,Files.readAllBytes(selected))&&
                Arrays.equals(otherData,Files.readAllBytes(other)),
                "read-only audit changed selected or foreign source bytes");
            check(!Arrays.equals(changingMarker,
                Files.readAllBytes(otherMarker))&&
                evidence.report().contains("recoveryAuthorized=false")&&
                evidence.report().contains("ownerEpochProven=false"),
                "unrelated changed marker confused report authority");
            try(Stream<Path> files=Files.list(root)){
                check(files.count()==4,
                    "inspector created or removed any filesystem leaf");
            }
            System.out.println(
                "G21290_FORENSIC_UNRELATED_MARKER_BYTE_DRIFT_CONTROL_PASS"+
                " exactAccountScope=true"+
                " changedForeignMarkerUnaffected=true"+
                " selectedMarkerPreserved=true"+
                " noGlobalForensicVeto=true"+
                " noLoginOrRecoveryAuthority=true");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path path:(Iterable<Path>)all.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
}

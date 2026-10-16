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
 * G21.281: second bounded NOFOLLOW account digest must refuse a
 * same-inode in-place edit after the first pass, EVEN when the
 * original size and mtime have been restored by a foreign actor.
 */
public final class G21281ForensicSameInodeRestoredMtimeDriftTest {
    private G21281ForensicSameInodeRestoredMtimeDriftTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.281 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21281-forensic-byte-drift-");
        Path account=root.resolve("named-account.properties");
        Path unrelated=root.resolve("other-account.properties");
        try{
            byte[] before=("forensic-account-id=canonical\n"+
                "phase=BEFORE\n").getBytes(StandardCharsets.US_ASCII);
            byte[] foreign="do-not-touch".getBytes(StandardCharsets.US_ASCII);
            Files.write(account,before);
            Files.write(unrelated,foreign);
            BasicFileAttributes original=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(original.fileKey()!=null,"filesystem has no file key");
            FileTime originalTime=Files.getLastModifiedTime(account,
                LinkOption.NOFOLLOW_LINKS);
            long initialEntries;
            try(Stream<Path> ls=Files.list(root)){
                initialEntries=ls.count();
            }
            AtomicBoolean modified=new AtomicBoolean(false);
            boolean rejected=false;
            try{
                LocalAccountForensicEvidence.inspect(account,selected->{
                    check(account.toAbsolutePath().normalize().equals(
                        selected),"unexpected scanner target");
                    try(FileChannel ch=FileChannel.open(account,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)){
                        ch.position(before.length-3);
                        ch.write(ByteBuffer.wrap(new byte[]{'N'}));
                        ch.force(true);
                    }
                    Files.setLastModifiedTime(account,originalTime);
                    BasicFileAttributes after=Files.readAttributes(account,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    check(original.fileKey().equals(after.fileKey())&&
                        original.size()==after.size()&&
                        originalTime.equals(after.lastModifiedTime()),
                        "metadata restoration precondition not met");
                    modified.set(true);
                });
            }catch(IOException expected){
                rejected=String.valueOf(expected.getMessage()).contains(
                    "G21.281 ACCOUNT_BYTES_CHANGED_DURING_FORENSIC_READ");
            }
            check(modified.get()&&rejected,
                "same-inode metadata-restored edit was reported clean");
            byte[] changed=Files.readAllBytes(account);
            check(!Arrays.equals(before,changed)&&
                changed.length==before.length,
                "test edit was not made at fixed account length");
            check(Arrays.equals(foreign,Files.readAllBytes(unrelated)),
                "forensic scan changed unrelated account");
            try(Stream<Path> ls=Files.list(root)){
                check(ls.count()==initialEntries,
                    "scan created lock, sidecar or temporary");
            }
            check(!Files.exists(LocalDurableAccountReviewIntentPrimitive
                .markerPath(account),LinkOption.NOFOLLOW_LINKS),
                "scanner created a review marker");
            System.out.println(
                "G21281_FORENSIC_RESTORED_MTIME_ACCOUNT_BYTE_DRIFT_DENIED_PASS"+
                " actualFileChannelInodeEdit=true"+
                " originalFileKeyAndMtimeAndSizePreserved=true"+
                " secondSha256DetectedDifference=true"+
                " noCleanReportOnDrift=true"+
                " unrelatedAccountPreserved=true"+
                " noOwnedFileOrLockCreated=true"+
                " noAtomicSnapshotClaim=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}

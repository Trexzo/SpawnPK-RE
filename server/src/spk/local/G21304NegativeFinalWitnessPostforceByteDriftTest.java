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

/** G21.304: forced witness same-inode/same-size/mtime-restored byte drift. */
public final class G21304NegativeFinalWitnessPostforceByteDriftTest {
    private G21304NegativeFinalWitnessPostforceByteDriftTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.304 "+message);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21304-final-byte-drift-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] saved="selected-inode".getBytes(StandardCharsets.US_ASCII);
            Files.write(account,saved);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] reviewBytes=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            AtomicBoolean injected=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                path->{
                    check(witness.equals(path),"wrong exact witness");
                    BasicFileAttributes before=Files.readAttributes(path,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    FileTime originalMtime=before.lastModifiedTime();
                    // Write ONE actual different byte, not append or
                    // unlink: inode and length remain unchanged.
                    try(FileChannel channel=FileChannel.open(path,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)){
                        channel.position(2L);
                        channel.write(ByteBuffer.wrap(new byte[]{'X'}));
                        channel.force(true);
                    }
                    Files.setLastModifiedTime(path,originalMtime);
                    BasicFileAttributes after=Files.readAttributes(path,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    check(before.fileKey().equals(after.fileKey())&&
                        before.size()==after.size()&&
                        originalMtime.equals(after.lastModifiedTime()),
                        "same-inode restored-metadata precondition missing");
                    injected.set(true);
                });
            boolean refused=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,10L);
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "G21.304 NEGATIVE_FINAL_WITNESS_POSTFORCE_BYTES_CHANGED");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(injected.get()&&refused,
                "post-force same-inode changed bytes were accepted");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(saved,Files.readAllBytes(account))&&
                Arrays.equals(reviewBytes,Files.readAllBytes(active)),
                "failed readback cleaned or rewrote negative evidence");
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(IOException expected){
                veto=String.valueOf(expected.getMessage()).contains(
                    "G21.302 NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            check(veto,"tampered witness incorrectly admitted account");
            System.out.println(
                "G21304_FINAL_WITNESS_POSTFORCE_BYTE_DRIFT_PASS"+
                " sameInodeAndRestoredMetadata=true"+
                " boundedNofollowReadbackRefused=true"+
                " accountAndACTIVEIntact=true"+
                " partialWitnessRetainedNegative=true"+
                " authenticatedReceipt=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterDirectoryForceForTest(null);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}

package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.305: same-sized, byte-identical different inode cannot fake witness. */
public final class G21305NegativeFinalWitnessSameBytesInodeSwapTest {
    private G21305NegativeFinalWitnessSameBytesInodeSwapTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.305 "+message);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21305-witness-swap-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] saved="unmodified-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,saved);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] originalReview=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            Path displaced=root.resolve("displaced-negative-witness");
            AtomicBoolean injected=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                path->{
                    check(path.equals(witness),"wrong witness hook");
                    BasicFileAttributes before=Files.readAttributes(path,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    byte[] bytes=Files.readAllBytes(path);
                    Files.move(path,displaced);
                    Files.write(path,bytes,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                    BasicFileAttributes after=Files.readAttributes(path,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    check(before.size()==after.size()&&
                        !before.fileKey().equals(after.fileKey())&&
                        Arrays.equals(bytes,Files.readAllBytes(path)),
                        "different-inode same-content swap precondition");
                    injected.set(true);
                });
            boolean refused=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,12L);
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "G21.305 NEGATIVE_FINAL_WITNESS_FILE_OBJECT_CHANGED");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(injected.get()&&refused,
                "same-size same-bytes inode replacement was accepted");
            check(Files.isRegularFile(displaced,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(saved,Files.readAllBytes(account))&&
                Arrays.equals(originalReview,Files.readAllBytes(active)),
                "inode-swap refusal modified or cleaned source evidence");
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(IOException expected){veto=true;}
            check(veto,"replaced witness allowed later session login");
            System.out.println(
                "G21305_FINAL_WITNESS_SAME_BYTES_INODE_SWAP_PASS"+
                " sameContentNewInodeRejected=true"+
                " forcedOriginalRetainedAsNegative=true"+
                " swappedWitnessStillNegative=true"+
                " noAutomaticCleanup=true"+
                " finalSaveAck=false");
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

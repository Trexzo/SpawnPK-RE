package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.314: replacing canonical ACTIVE with byte-identical new inode fails. */
public final class G21314NegativeFinalWitnessActiveIdentityDriftTest {
    private G21314NegativeFinalWitnessActiveIdentityDriftTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.314 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21314-marker-object-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] data="g21314-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,data);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] marker=Files.readAllBytes(active);
            Path oldMarker=root.resolve("original-ACTIVE-preserved");
            Path output=LocalFinalSaveNegativeWitness
                .witnessPath(account);
            BasicFileAttributes before=Files.readAttributes(active,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            AtomicBoolean swapped=new AtomicBoolean();
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                leaf->{
                    check(output.equals(leaf),"unexpected output file");
                    Files.move(active,oldMarker);
                    Files.write(active,marker);
                    BasicFileAttributes next=Files.readAttributes(
                        active,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    check(before.size()==next.size()&&
                        !before.fileKey().equals(next.fileKey())&&
                        Arrays.equals(marker,Files.readAllBytes(active)),
                        "same-bytes new-ACTIVE-inode precondition missing");
                    swapped.set(true);
                });
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,3L);
            }catch(IOException expected){
                veto=String.valueOf(expected.getMessage()).contains(
                    "G21.314 FINAL_WITNESS_ACTIVE_OBJECT_DRIFT_NEGATIVE");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(swapped.get()&&veto,
                "same-byte ACTIVE replacement was treated stable");
            check(Files.isRegularFile(output,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(marker,Files.readAllBytes(active))&&
                Arrays.equals(marker,Files.readAllBytes(oldMarker))&&
                Arrays.equals(data,Files.readAllBytes(account)),
                "refusal cleaned evidence or changed account data");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(account);
            }catch(IOException expected){denied=true;}
            check(denied,"uncertain witness admitted a session");
            System.out.println(
                "G21314_FINAL_WITNESS_ACTIVE_OBJECT_DRIFT_PASS"+
                " sameContentNewInodeRejected=true"+
                " originalMarkerRetained=true"+
                " selectedMarkerStillNegative=true"+
                " finalWitnessNegative=true"+
                " safeRecovery=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterDirectoryForceForTest(null);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}

package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.321: inherited ACTIVE alias must be rejected BEFORE CREATE_NEW. */
public final class G21321PreexistingActiveHardlinkAdmissionNegativeTest {
    private G21321PreexistingActiveHardlinkAdmissionNegativeTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.321 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path dir=Files.createTempDirectory("g21321-prior-active-link-");
        try{
            Path account=dir.resolve("selected.properties");
            byte[] bytes="preexisting-ACTIVE-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,bytes);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] originalMarker=Files.readAllBytes(active);
            Path secondName=dir.resolve("existing-ACTIVE-alias");
            Files.createLink(secondName,active);
            check(((Number)Files.getAttribute(active,"unix:nlink",
                    LinkOption.NOFOLLOW_LINKS)).longValue()==2L&&
                Arrays.equals(originalMarker,Files.readAllBytes(secondName)),
                "real preexisting alias");
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            boolean negative=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,321L);
            }catch(IOException expected){
                negative=String.valueOf(expected.getMessage()).contains(
                    "G21.319 FINAL_WITNESS_ACTIVE_HARDLINK_NEGATIVE");
            }
            check(negative,"existing ACTIVE alias was admitted");
            check(!Files.exists(witness,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(originalMarker,Files.readAllBytes(active))&&
                Arrays.equals(bytes,Files.readAllBytes(account)),
                "preflight illegally created witness or altered source");
            boolean denied=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account);
            }catch(IOException expected){denied=true;}
            check(denied,"retained ACTIVE lost login veto");
            System.out.println(
                "G21321_PREEXISTING_ACTIVE_HARDLINK_PREFLIGHT_PASS"+
                " realTwoLinkInode=true"+
                " preCreateNewRejection=true"+
                " noWitnessCreatedOnRefusal=true"+
                " ACTIVEAndAccountPreserved=true"+
                " admissionAlwaysNegative=true");
        }finally{
            try(Stream<Path> walk=Files.walk(dir)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}

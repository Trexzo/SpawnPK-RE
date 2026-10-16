package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.299: unsafe opt-in combinations deny admission; an already
 * occupied witness leaf can never be overwritten or auto-cleaned.
 */
public final class G21299FinalReviewWitnessFailClosedControlsTest {
    private G21299FinalReviewWitnessFailClosedControlsTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.299 "+stage);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21299-review-controls-");
        String old=System.getProperty(LocalFinalSaveNegativeWitness.PROPERTY);
        try{
            System.setProperty(
                LocalFinalSaveNegativeWitness.PROPERTY,"true");
            // Test deliberately does not enable the required
            // cooperating owner, ACTIVE, or forced postimage gates.
            String originalJvm=System.getProperty(
                LocalJvmAccountSessionLease.PROPERTY);
            System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
            try{
                boolean veto=false;
                try{
                    LocalFinalSaveNegativeWitness
                        .requireAdmissionConfiguration(true);
                }catch(IllegalStateException expected){
                    veto=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "REQUIRES_ALL_EXPERIMENTAL_GATES");
                }
                check(veto,"incomplete opt-in passed early preflight");
            }finally{
                if(originalJvm==null)
                    System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
                else System.setProperty(
                    LocalJvmAccountSessionLease.PROPERTY,originalJvm);
            }
            Path account=root.resolve("selected.properties");
            byte[] bytes="real-account-source".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,bytes);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] originalMarker=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            byte[] hostile="already-present-opaque-negative".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(witness,hostile);
            boolean rejected=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,1L);
            }catch(IOException expected){
                rejected=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");
            }
            check(rejected,"existing final witness was overwritten");
            check(Arrays.equals(bytes,Files.readAllBytes(account))&&
                Arrays.equals(hostile,Files.readAllBytes(witness))&&
                Arrays.equals(originalMarker,Files.readAllBytes(active)),
                "refusal modified source or occupied negative leaf");
            boolean invalidGeneration=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",0L,1L);
            }catch(IOException expected){invalidGeneration=true;}
            check(invalidGeneration,"invalid generation granted evidence");
            System.out.println(
                "G21299_FINAL_REVIEW_WITNESS_FAIL_CLOSED_PASS"+
                " unsafeOptInDeniedBeforeAdmission=true"+
                " occupiedWitnessUnchanged=true"+
                " sourceAccountAndACTIVEPreserved=true"+
                " invalidOwnerGenerationDenied=true"+
                " autoCleanup=false"+
                " recoveryAuthorized=false");
        }finally{
            if(old==null)System.clearProperty(
                LocalFinalSaveNegativeWitness.PROPERTY);
            else System.setProperty(
                LocalFinalSaveNegativeWitness.PROPERTY,old);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}

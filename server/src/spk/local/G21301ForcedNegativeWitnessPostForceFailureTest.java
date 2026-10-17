package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.301 after forced witness FILE+DIRECTORY but before
 * readback, failure cannot be interpreted as receipt ACK,
 * cleanup/retry or license to retire the ACTIVE review marker.
 */
public final class G21301ForcedNegativeWitnessPostForceFailureTest {
    private G21301ForcedNegativeWitnessPostForceFailureTest(){}
    private static void check(boolean okay,String why){
        if(!okay)throw new AssertionError("G21.301 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21301-after-force-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] accountData="unchanged-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,accountData);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] markerBytes=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness
                .witnessPath(account);
            AtomicBoolean injected=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                leaf->{
                    check(witness.equals(leaf)&&
                        Files.isRegularFile(leaf,
                            LinkOption.NOFOLLOW_LINKS),
                        "test fault did not reach forced witness");
                    injected.set(true);
                    throw new IOException(
                        "G21.301 TEST_FORCED_POSTIMAGE_UNCERTAIN");
                });
            boolean failed=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,11L);
            }catch(IOException expected){
                failed=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "TEST_FORCED_POSTIMAGE_UNCERTAIN");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(injected.get()&&failed,
                "post-force failure was misreported as success");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(markerBytes,Files.readAllBytes(active))&&
                Arrays.equals(accountData,Files.readAllBytes(account)),
                "uncertain state cleaned or mutated account/ACTIVE");
            boolean repeated=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,12L);
            }catch(IOException expected){
                repeated=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");
            }
            check(repeated,"failed witness was retried or overwritten");
            boolean denied=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account);
            }catch(IOException expected){denied=true;}
            check(denied,
                "post-force failure promoted future account admission");
            System.out.println(
                "G21301_FINAL_WITNESS_POST_FORCE_UNCERTAIN_PASS"+
                " actualFileAndDirectoryForced=true"+
                " explicitLateFailureObserved=true"+
                " evidenceNeverDeletedOrRetried=true"+
                " ACTIVEUnchanged=true"+
                " finalSaveAck=false"+
                " noPositiveRecovery=true");
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

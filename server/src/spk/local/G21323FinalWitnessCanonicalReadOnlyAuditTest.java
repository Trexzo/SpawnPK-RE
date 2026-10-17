package spk.local;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** G21.323: stable real published witness is STILL untrusted negative. */
public final class G21323FinalWitnessCanonicalReadOnlyAuditTest {
    private G21323FinalWitnessCanonicalReadOnlyAuditTest(){}
    private static void req(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.323 "+why);
    }
    public static void main(String[] args)throws Exception{
        req(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21323-final-audit-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"canonical-account".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",3L,7L);
            byte[] accountBytes=Files.readAllBytes(account);
            byte[] markerBytes=Files.readAllBytes(active);
            byte[] witnessBytes=Files.readAllBytes(witness);
            long beforeCount;
            try(Stream<Path> list=Files.list(root)){beforeCount=list.count();}
            LocalFinalSaveWitnessForensicAudit.Evidence result=
                LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected");
            req(result.state==LocalFinalSaveWitnessForensicAudit.State
                    .MATCHES_UNTRUSTED_NEGATIVE,
                "canonical negative did not match selected observation");
            req("3".equals(result.untrustedGeneration)&&
                "7".equals(result.untrustedSaveSequence)&&
                result.witnessSha256.matches("SHA256:[0-9a-f]{64}")&&
                result.report().contains("finalSaveAck=false")&&
                result.report().contains("recoveryAuthorized=false"),
                "canonical diagnostic promoted to receipt or wrong fields");
            long afterCount;
            try(Stream<Path> list=Files.list(root)){afterCount=list.count();}
            req(beforeCount==afterCount&&
                Arrays.equals(accountBytes,Files.readAllBytes(account))&&
                Arrays.equals(markerBytes,Files.readAllBytes(active))&&
                Arrays.equals(witnessBytes,Files.readAllBytes(witness)),
                "read-only audit modified filesystem");
            boolean admissionRefused=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(java.io.IOException expected){admissionRefused=true;}
            req(admissionRefused,"canonical audit incorrectly authorized login");
            System.out.println(
                "G21323_FINAL_WITNESS_CANONICAL_READ_ONLY_AUDIT_PASS"+
                " exactAccountAndACTIVE=true"+
                " boundedWitnessDigest=true"+
                " strictlyNegativeClassification=true"+
                " noFilesystemMutation=true"+
                " noOwnerReceipt=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}

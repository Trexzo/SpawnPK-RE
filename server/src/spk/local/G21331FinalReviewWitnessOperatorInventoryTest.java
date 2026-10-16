package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
public final class G21331FinalReviewWitnessOperatorInventoryTest{
    private static void ok(boolean v){if(!v)throw new AssertionError("G21.331");}
    static void cleanup(Path root)throws Exception{
        try(Stream<Path> walk=Files.walk(root)){
            for(Path p:walk.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new))Files.deleteIfExists(p);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g21331-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected".getBytes(StandardCharsets.US_ASCII));
            LocalAccountForensicEvidence.Evidence empty=LocalAccountForensicEvidence.inspect(account);
            ok("ABSENT".equals(empty.finalReviewWitnessState));
            Path active=LocalDurableAccountReviewIntentPrimitive.writeActiveReview(account);
            LocalFinalSaveNegativeWitness.publishOnce(account,"selected",1L,1L);
            LocalAccountForensicEvidence.Evidence with=LocalAccountForensicEvidence.inspect(account);
            ok(with.negativeSidecars.size()==5&&with.observedNegativeNames==1);
            ok("PRESENT_REGULAR_NEGATIVE".equals(with.finalReviewWitnessState));
            ok(with.finalReviewWitnessContent.matches("SHA256:[0-9a-f]{64}"));
            Files.delete(active); // simulate foreign/manual unsafe removal
            LocalAccountForensicEvidence.Evidence still=LocalAccountForensicEvidence.inspect(account);
            ok(still.observedNegativeNames==0&&still.negativeSidecars.size()==5);
            ok("PRESENT_REGULAR_NEGATIVE".equals(still.finalReviewWitnessState));
            ok(with.finalReviewWitnessContent.equals(still.finalReviewWitnessContent));
            ok(still.report().contains("finalReviewWitnessContent=SHA256:"));
            ok(still.report().contains("recoveryAuthorized=false"));
            boolean refused=false;
            try{LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);}
            catch(IOException x){refused=true;}
            ok(refused);
            System.out.println("G21331_FINAL_WITNESS_OPERATOR_INVENTORY_PASS historicalFiveNames=true retainedNegativeAfterACTIVEAbsent=true noRecoveryAuthority=true");
        }finally{cleanup(root);}
    }
}

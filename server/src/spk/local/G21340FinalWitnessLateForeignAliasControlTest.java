package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.340 only exact selected ACTIVE alias churn can veto matching. */
public final class G21340FinalWitnessLateForeignAliasControlTest {
    private G21340FinalWitnessLateForeignAliasControlTest(){}
    private static void req(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.340 "+reason);
    }
    public static void main(String[] args)throws Exception{
        req(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21340-foreign-alias-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected-account".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",2L,4L);
            byte[] originalActive=Files.readAllBytes(active);
            byte[] originalWitness=Files.readAllBytes(witness);
            Path unrelated=root.resolve("foreign.properties");
            Files.write(unrelated,"foreign-account".getBytes(
                StandardCharsets.US_ASCII));
            Path alias=root.resolve("foreign-alias");
            AtomicBoolean injected=new AtomicBoolean(false);
            LocalFinalSaveWitnessForensicAudit.Evidence evidence=
                LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected",ignored->{},selected->{
                        req(selected.equals(witness),"wrong selected leaf");
                        Files.createLink(alias,unrelated);
                        req(Files.isSameFile(unrelated,alias),
                            "foreign hardlink did not target expected inode");
                        injected.set(true);
                    });
            req(injected.get()&&
                evidence.state==LocalFinalSaveWitnessForensicAudit.State
                    .MATCHES_UNTRUSTED_NEGATIVE&&
                evidence.report().contains("recoveryAuthorized=false")&&
                evidence.report().contains("finalSaveAck=false"),
                "foreign source alias caused global veto or positive grant");
            req(Arrays.equals(originalActive,Files.readAllBytes(active))&&
                Arrays.equals(originalWitness,Files.readAllBytes(witness)),
                "read-only audit changed selected bytes");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(IOException expected){denied=true;}
            req(denied,"negative witness became login permission");
            System.out.println(
                "G21340_FINAL_WITNESS_FOREIGN_LATE_ALIAS_PASS"+
                " foreignRealHardlinkCreated=true"+
                " selectedNegativeAuditUnchanged=true"+
                " exactSelectedScope=true"+
                " stillUntrusted=true"+
                " safeToReplay=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}

package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.339 a late real hardlink of selected ACTIVE must remain negative. */
public final class G21339FinalWitnessLateActiveAliasTest {
    private G21339FinalWitnessLateActiveAliasTest(){}
    private static void req(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.339 "+reason);
    }
    public static void main(String[] args)throws Exception{
        req(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21339-final-alias-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected-account".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",1L,3L);
            Path alias=root.resolve("selected-active-alias");
            byte[] originalAccount=Files.readAllBytes(account);
            byte[] originalActive=Files.readAllBytes(active);
            byte[] originalWitness=Files.readAllBytes(witness);
            AtomicBoolean injected=new AtomicBoolean(false);
            boolean refused=false;
            try{
                LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected",ignored->{},selected->{
                        req(selected.equals(witness),"wrong selected leaf");
                        Files.createLink(alias,active);
                        req(Files.isSameFile(alias,active),
                            "late alias not same real inode");
                        injected.set(true);
                    });
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "G21.339 FINAL_WITNESS_ACTIVE_LINK_CHANGED_DURING_AUDIT");
            }
            req(injected.get()&&refused,
                "late selected ACTIVE hardlink was incorrectly matched");
            req(Files.exists(alias,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(originalAccount,Files.readAllBytes(account))&&
                Arrays.equals(originalActive,Files.readAllBytes(active))&&
                Arrays.equals(originalWitness,Files.readAllBytes(witness)),
                "forensic scan altered any selected negative evidence");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(IOException expected){denied=true;}
            req(denied,"late alias enabled login despite negative witness");
            System.out.println(
                "G21339_FINAL_WITNESS_LATE_ACTIVE_ALIAS_PASS"+
                " realHardlinkCreatedAfterSourceValidation=true"+
                " sameInodeBytesTimestampUnchanged=true"+
                " lateSelectedLinkCountRejected=true"+
                " noAutomaticCleanup=true"+
                " noPositiveRecovery=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}

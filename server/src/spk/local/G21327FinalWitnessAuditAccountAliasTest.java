package spk.local;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
public final class G21327FinalWitnessAuditAccountAliasTest {
 private G21327FinalWitnessAuditAccountAliasTest(){}
 private static void req(boolean yes,String msg){
   if(!yes)throw new AssertionError("G21.327 "+msg);
 }
 public static void main(String[] args)throws Exception{
   req(args.length==0,"unexpected args");
   Path root=Files.createTempDirectory("g327-source-alias-");
   try{
     Path account=root.resolve("selected.properties");
     byte[] accountOriginal="unchanged-selected".getBytes(StandardCharsets.US_ASCII);
     Files.write(account,accountOriginal);
     Path active=LocalDurableAccountReviewIntentPrimitive.writeActiveReview(account);
     Path witness=LocalFinalSaveNegativeWitness.publishOnce(account,"selected",3L,5L);
     byte[] markerOriginal=Files.readAllBytes(active);
     byte[] witnessOriginal=Files.readAllBytes(witness);
     Path alias=root.resolve("selected-other-inode-name");
Files.createLink(alias,account);
req(LocalFinalSaveWitnessForensicAudit.inspect(account,"selected").state==LocalFinalSaveWitnessForensicAudit.State.SOURCE_LINK_COUNT_UNPROVEN_NEGATIVE,
    "selected hardlink was mistaken for one-owner match");
Files.delete(alias);
req(LocalFinalSaveWitnessForensicAudit.inspect(account,"selected").state==LocalFinalSaveWitnessForensicAudit.State.MATCHES_UNTRUSTED_NEGATIVE,
    "healthy single-link negative evidence unexpectedly changed");
     req(Arrays.equals(accountOriginal,Files.readAllBytes(account)) &&
         Arrays.equals(markerOriginal,Files.readAllBytes(active)) &&
         Arrays.equals(witnessOriginal,Files.readAllBytes(witness)),
         "forensic inspection modified source evidence");
     boolean denied=false;
     try{LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);}
     catch(java.io.IOException expected){denied=true;}
     req(denied,"forensic observation promoted login authority");
     System.out.println("G327_FINAL_WITNESS_SOURCE_ALIAS_AUDIT_PASS"+
         " genuineHardlinks=true noGrantOrReplay=true readOnly=true");
   }finally{
     try(Stream<Path> walk=Files.walk(root)){
       for(Path file:walk.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
         Files.deleteIfExists(file);
     }
   }
 }
}

package spk.local;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
public final class G21330FinalWitnessAuditMultipleSelectedAliasesTest {
 private G21330FinalWitnessAuditMultipleSelectedAliasesTest(){}
 private static void req(boolean yes,String msg){
   if(!yes)throw new AssertionError("G21.330 "+msg);
 }
 public static void main(String[] args)throws Exception{
   req(args.length==0,"unexpected args");
   Path root=Files.createTempDirectory("g330-source-alias-");
   try{
     Path account=root.resolve("selected.properties");
     byte[] accountOriginal="unchanged-selected".getBytes(StandardCharsets.US_ASCII);
     Files.write(account,accountOriginal);
     Path active=LocalDurableAccountReviewIntentPrimitive.writeActiveReview(account);
     Path witness=LocalFinalSaveNegativeWitness.publishOnce(account,"selected",3L,5L);
     byte[] markerOriginal=Files.readAllBytes(active);
     byte[] witnessOriginal=Files.readAllBytes(witness);
     Path a1=root.resolve("a1"),a2=root.resolve("a2");
Path m1=root.resolve("m1"),m2=root.resolve("m2");
Files.createLink(a1,account);Files.createLink(a2,account);
Files.createLink(m1,active);Files.createLink(m2,active);
req(LocalFinalSaveWitnessForensicAudit.inspect(account,"selected").state==LocalFinalSaveWitnessForensicAudit.State.SOURCE_LINK_COUNT_UNPROVEN_NEGATIVE,
    "nlink3 account not negative before ACTIVE");
Files.delete(a1);Files.delete(a2);
req(LocalFinalSaveWitnessForensicAudit.inspect(account,"selected").state==LocalFinalSaveWitnessForensicAudit.State.ACTIVE_LINK_COUNT_UNPROVEN_NEGATIVE,
    "nlink3 ACTIVE not negative after account alias removal");
Files.delete(m1);Files.delete(m2);
req(LocalFinalSaveWitnessForensicAudit.inspect(account,"selected").state==LocalFinalSaveWitnessForensicAudit.State.MATCHES_UNTRUSTED_NEGATIVE,
    "healthy single-link still negative-untrusted");
     req(Arrays.equals(accountOriginal,Files.readAllBytes(account)) &&
         Arrays.equals(markerOriginal,Files.readAllBytes(active)) &&
         Arrays.equals(witnessOriginal,Files.readAllBytes(witness)),
         "forensic inspection modified source evidence");
     boolean denied=false;
     try{LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);}
     catch(java.io.IOException expected){denied=true;}
     req(denied,"forensic observation promoted login authority");
     System.out.println("G330_FINAL_WITNESS_SOURCE_ALIAS_AUDIT_PASS"+
         " genuineHardlinks=true noGrantOrReplay=true readOnly=true");
   }finally{
     try(Stream<Path> walk=Files.walk(root)){
       for(Path file:walk.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
         Files.deleteIfExists(file);
     }
   }
 }
}

package spk.local;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** G21.324 selected account/ACTIVE drift or removal never validates witness. */
public final class G21324FinalWitnessSelectedSourceMismatchAuditTest {
    private G21324FinalWitnessSelectedSourceMismatchAuditTest(){}
    private static void req(boolean x,String s){
        if(!x)throw new AssertionError("G21.324 "+s);
    }
    public static void main(String[] args)throws Exception{
        req(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21324-source-drift-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] source="selected-source".getBytes(StandardCharsets.US_ASCII);
            Files.write(account,source);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] originalActive=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",1L,15L);
            byte[] originalWitness=Files.readAllBytes(witness);
            Files.write(account,"selected-sourcX".getBytes(
                StandardCharsets.US_ASCII));
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State
                        .SOURCE_MISMATCH_NEGATIVE,
                "account bytes changed but witness classified matching");
            Files.write(account,source);
            try(java.nio.channels.FileChannel file=
                    java.nio.channels.FileChannel.open(active,
                        StandardOpenOption.WRITE)){
                file.position(5L);
                file.write(java.nio.ByteBuffer.wrap(new byte[]{'Z'}));
            }
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State
                        .SOURCE_MISMATCH_NEGATIVE,
                "ACTIVE bytes drift overlooked");
            Files.write(active,originalActive);
            Files.delete(active); // deliberate external marker removal
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State
                        .ACTIVE_MISSING_NEGATIVE,
                "missing ACTIVE incorrectly treated as positive final save");
            req(Arrays.equals(source,Files.readAllBytes(account))&&
                Arrays.equals(originalWitness,Files.readAllBytes(witness)),
                "read-only source comparison rewrote witness or account");
            System.out.println(
                "G21324_FINAL_WITNESS_SELECTED_SOURCE_MISMATCH_PASS"+
                " changedAccountBytesNegative=true"+
                " changedACTIVEBytesNegative=true"+
                " removedACTIVENegative=true"+
                " noAutomaticRecoveryOrClear=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}

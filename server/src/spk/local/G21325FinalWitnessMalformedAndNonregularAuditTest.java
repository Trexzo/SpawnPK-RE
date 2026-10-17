package spk.local;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.325 malformed, directory and oversize final witness are negative. */
public final class G21325FinalWitnessMalformedAndNonregularAuditTest {
    private G21325FinalWitnessMalformedAndNonregularAuditTest(){}
    private static void req(boolean x,String why){
        if(!x)throw new AssertionError("G21.325 "+why);
    }
    public static void main(String[] args)throws Exception{
        req(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21325-negative-shapes-");
        try{
            Path account=root.resolve("selected.properties");
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State.ABSENT_UNPROVEN,
                "missing witness treated as final receipt");
            Files.write(witness,"not-a-record".getBytes(
                StandardCharsets.US_ASCII));
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State
                        .MALFORMED_NEGATIVE,
                "malformed witness treated as healthy");
            Files.write(witness,new byte[4097]);
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State
                        .OVERSIZE_NEGATIVE,
                "oversize witness exceeded bounded-reader promise");
            Files.delete(witness);
            Files.createDirectory(witness);
            req(LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected").state==
                    LocalFinalSaveWitnessForensicAudit.State
                        .NONREGULAR_NEGATIVE,
                "nonregular witness followed or trusted");
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(java.io.IOException expected){veto=true;}
            req(veto,"nonregular retained witness allowed login");
            System.out.println(
                "G21325_FINAL_WITNESS_OPAQUE_NEGATIVE_SHAPES_PASS"+
                " absentIsNotReceipt=true"+
                " malformedNegative=true"+
                " oversizeBounded=true"+
                " nonregularNofollowNegative=true"+
                " admissionRemainsDenied=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}

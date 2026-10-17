package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
public final class G21332ForeignFinalReviewOperatorIsolationTest{
    private static void ok(boolean v){if(!v)throw new AssertionError("G21.332");}
    static void cleanup(Path root)throws Exception{
        try(Stream<Path> walk=Files.walk(root)){
            for(Path p:walk.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new))Files.deleteIfExists(p);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g21332-");
        try{
            Path selected=root.resolve("selected.properties"),foreign=root.resolve("foreign.properties");
            Files.write(selected,"selected".getBytes(StandardCharsets.US_ASCII));
            Files.write(foreign,"foreign".getBytes(StandardCharsets.US_ASCII));
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(foreign);
            LocalFinalSaveNegativeWitness.publishOnce(foreign,"foreign",1L,1L);
            long before;
            try(Stream<Path> ls=Files.list(root)){before=ls.count();}
            LocalAccountForensicEvidence.Evidence a=LocalAccountForensicEvidence.inspect(selected);
            LocalAccountForensicEvidence.Evidence b=LocalAccountForensicEvidence.inspect(foreign);
            ok("ABSENT".equals(a.finalReviewWitnessState));
            ok("ABSENT".equals(a.finalReviewWitnessContent));
            ok("PRESENT_REGULAR_NEGATIVE".equals(b.finalReviewWitnessState));
            ok(b.finalReviewWitnessContent.matches("SHA256:[0-9a-f]{64}"));
            try(Stream<Path> ls=Files.list(root)){ok(ls.count()==before);}
            System.out.println("G21332_FOREIGN_FINAL_WITNESS_OPERATOR_ISOLATION_PASS noCrossAccountBleed=true readOnly=true");
        }finally{cleanup(root);}
    }
}

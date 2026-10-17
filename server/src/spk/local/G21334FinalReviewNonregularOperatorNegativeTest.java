package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
public final class G21334FinalReviewNonregularOperatorNegativeTest{
    private static void ok(boolean v){if(!v)throw new AssertionError("G21.334");}
    static void cleanup(Path root)throws Exception{
        try(Stream<Path> walk=Files.walk(root)){
            for(Path p:walk.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new))Files.deleteIfExists(p);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g21334-");
        try{
            Path account=root.resolve("selected.properties"),foreign=root.resolve("foreign.txt");
            Files.write(account,"selected".getBytes(StandardCharsets.US_ASCII));
            Files.write(foreign,"foreign-private-data".getBytes(StandardCharsets.US_ASCII));
            Path leaf=LocalFinalSaveNegativeWitness.witnessPath(account);
            Files.createSymbolicLink(leaf,foreign);
            LocalAccountForensicEvidence.Evidence linked=LocalAccountForensicEvidence.inspect(account);
            ok("PRESENT_NONREGULAR_NEGATIVE".equals(linked.finalReviewWitnessState));
            ok("NONREGULAR_OPAQUE_NEGATIVE".equals(linked.finalReviewWitnessContent));
            ok(!linked.report().contains("foreign-private-data"));
            Files.delete(leaf);Files.createDirectory(leaf);
            LocalAccountForensicEvidence.Evidence dir=LocalAccountForensicEvidence.inspect(account);
            ok("NONREGULAR_OPAQUE_NEGATIVE".equals(dir.finalReviewWitnessContent));
            ok(dir.observedNegativeNames==0&&dir.negativeSidecars.size()==5);
            boolean denied=false;
            try{LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);}
            catch(IOException e){denied=true;}
            ok(denied);
            System.out.println("G21334_FINAL_REVIEW_NONREGULAR_NOFOLLOW_PASS symlinkOpaque=true directoryNegative=true noPositiveLogin=true");
        }finally{cleanup(root);}
    }
}

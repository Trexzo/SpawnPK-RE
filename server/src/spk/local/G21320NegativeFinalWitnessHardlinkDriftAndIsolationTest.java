package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Real witness hardlink alias after file+directory force, with clean control. */
public final class G21320NegativeFinalWitnessHardlinkDriftAndIsolationTest {
    private G21320NegativeFinalWitnessHardlinkDriftAndIsolationTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.320 "+message);
    }
    private static long links(Path p)throws Exception{
        return ((Number)Files.getAttribute(p,"unix:nlink",
            LinkOption.NOFOLLOW_LINKS)).longValue();
    }
    private static void exercise(boolean mutateSelected)throws Exception{
        Path dir=Files.createTempDirectory(
            "g21320-"+(mutateSelected?"selected":"foreign")+"-");
        try{
            Path account=dir.resolve("selected.properties");
            Path foreign=dir.resolve("foreign.properties");
            byte[] bytes="G21320-account".getBytes(StandardCharsets.US_ASCII);
            Files.write(account,bytes);
            Files.write(foreign,"G21320-foreign".getBytes(
                StandardCharsets.US_ASCII));
            byte[] foreignBytes=Files.readAllBytes(foreign);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] activeBytes=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            Path alias=dir.resolve("extra-hardlink");
            AtomicBoolean injected=new AtomicBoolean();
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                leaf->{
                    check(witness.equals(leaf),"wrong post-force leaf");
                    Path target=mutateSelected?witness:foreign;
                    BasicFileAttributes before=Files.readAttributes(target,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    Files.createLink(alias,target);
                    try{
                        check(links(target)==2L&&
                            before.fileKey().equals(Files.readAttributes(
                                target,BasicFileAttributes.class,
                                LinkOption.NOFOLLOW_LINKS).fileKey()),
                            "real same-file hardlink precondition");
                    }catch(Exception error){
                        throw new IOException("invalid test alias",error);
                    }
                    injected.set(true);
                });
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,20L);
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "G21.320 FINAL_WITNESS_HARDLINK_NEGATIVE");
                if(!mutateSelected)throw expected;
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(injected.get()&&denied==mutateSelected,
                "wrong selected/foreign link decision");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(alias,LinkOption.NOFOLLOW_LINKS)&&
                links(witness)==(mutateSelected?2L:1L)&&
                Arrays.equals(bytes,Files.readAllBytes(account))&&
                Arrays.equals(activeBytes,Files.readAllBytes(active))&&
                Arrays.equals(foreignBytes,Files.readAllBytes(foreign)),
                "fail-closed drift/control damaged source");
            boolean negative=false;
            try{
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(account);
            }catch(IOException expected){negative=true;}
            check(negative,"witness permitted positive admission");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterDirectoryForceForTest(null);
            try(Stream<Path> s=Files.walk(dir)){
                for(Path p:s.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        exercise(true);
        exercise(false);
        System.out.println(
            "G21320_FINAL_WITNESS_HARDLINK_DRIFT_AND_ISOLATION_PASS"+
            " realWitnessAliasRejected=true"+
            " foreignAliasDidNotPoisonSelected=true"+
            " exactNlinkTwoVsOne=true"+
            " ACTIVEIntact=true"+
            " witnessAlwaysNegative=true");
    }
}

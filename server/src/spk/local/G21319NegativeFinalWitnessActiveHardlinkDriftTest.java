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

/** Real selected ACTIVE alias after witness file+directory were forced. */
public final class G21319NegativeFinalWitnessActiveHardlinkDriftTest {
    private G21319NegativeFinalWitnessActiveHardlinkDriftTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.319 "+message);
    }
    private static long links(Path p)throws Exception{
        return ((Number)Files.getAttribute(p,"unix:nlink",
            LinkOption.NOFOLLOW_LINKS)).longValue();
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args unexpected");
        Path dir=Files.createTempDirectory("g21319-active-link-");
        try{
            Path account=dir.resolve("selected.properties");
            byte[] accountBytes="G21319-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,accountBytes);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] markerBytes=Files.readAllBytes(active);
            Path alias=dir.resolve("ACTIVE-extra-hardlink");
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            BasicFileAttributes original=Files.readAttributes(active,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(links(active)==1L,
                "selected ACTIVE initial link count not 1");
            AtomicBoolean injected=new AtomicBoolean();
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                leaf->{
                    check(witness.equals(leaf),"wrong witness seam");
                    Files.createLink(alias,active);
                    try{
                        check(links(active)==2L&&
                            original.fileKey().equals(Files.readAttributes(
                                active,BasicFileAttributes.class,
                                LinkOption.NOFOLLOW_LINKS).fileKey())&&
                            Arrays.equals(markerBytes,Files.readAllBytes(active)),
                            "real same-inode 1->2 ACTIVE alias precondition");
                    }catch(Exception error){
                        throw new IOException("test precondition",error);
                    }
                    injected.set(true);
                });
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,19L);
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "G21.319 FINAL_WITNESS_ACTIVE_HARDLINK_NEGATIVE");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(injected.get()&&denied,
                "extra active hardlink accepted as stable review");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(alias,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(accountBytes,Files.readAllBytes(account))&&
                Arrays.equals(markerBytes,Files.readAllBytes(active))&&
                links(active)==2L,
                "failure cleared alias, marker, witness or account");
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(account);
            }catch(IOException expected){veto=true;}
            check(veto,"negative final-review witness allowed recovery");
            System.out.println(
                "G21319_FINAL_WITNESS_ACTIVE_HARDLINK_DRIFT_PASS"+
                " actualSelectedActiveAlias=true"+
                " sameInodeSameBytes=true"+
                " exactLinkCountTwoDenied=true"+
                " forcedWitnessRemainsNegative=true"+
                " automaticRecovery=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterDirectoryForceForTest(null);
            try(Stream<Path> s=Files.walk(dir)){
                for(Path p:s.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}

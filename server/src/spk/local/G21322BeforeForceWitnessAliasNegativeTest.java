package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.322: witness alias after CREATE_NEW but before file force. */
public final class G21322BeforeForceWitnessAliasNegativeTest {
    private G21322BeforeForceWitnessAliasNegativeTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.322 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path dir=Files.createTempDirectory("g21322-preforce-witness-");
        try{
            Path account=dir.resolve("selected.properties");
            byte[] bytes="pre-force-alias-account".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(account,bytes);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] marker=Files.readAllBytes(active);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            Path alias=dir.resolve("witness-hardlink-before-force");
            AtomicBoolean realAlias=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setAfterWriteBeforeForceForTest(
                selected->{
                    check(witness.equals(selected),
                        "seam referenced incorrect witness");
                    Files.createLink(alias,selected);
                    try{
                        check(((Number)Files.getAttribute(selected,
                            "unix:nlink",LinkOption.NOFOLLOW_LINKS))
                            .longValue()==2L&&
                            Arrays.equals(Files.readAllBytes(selected),
                                Files.readAllBytes(alias)),
                            "pre-force alias differs from exact inode");
                    }catch(Exception e){
                        throw new IOException("invalid pre-force setup",e);
                    }
                    realAlias.set(true);
                });
            boolean refused=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,322L);
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "G21.320 FINAL_WITNESS_HARDLINK_NEGATIVE");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterWriteBeforeForceForTest(null);
            }
            check(realAlias.get()&&refused,
                "pre-force alias was treated as single-link witness");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(alias,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(bytes,Files.readAllBytes(account))&&
                Arrays.equals(marker,Files.readAllBytes(active)),
                "failure cleaned or modified negative review source");
            boolean negative=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);
            }catch(IOException expected){negative=true;}
            check(negative,"partially forced multi-link witness allowed login");
            System.out.println(
                "G21322_BEFORE_FORCE_WITNESS_HARDLINK_NEGATIVE_PASS"+
                " realPreForceAlias=true"+
                " postForceNlinkRecheckDenied=true"+
                " sourceAccountAndACTIVEIntact=true"+
                " negativeReviewLeafNeverCleaned=true"+
                " finalSaveAck=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterWriteBeforeForceForTest(null);
            try(Stream<Path> walk=Files.walk(dir)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}

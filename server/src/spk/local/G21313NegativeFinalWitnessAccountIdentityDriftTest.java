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

/**
 * G21.313: a real same-content replacement account inode or new
 * selected account alias after witness directory force must not be
 * represented as a stable final-review account witness.
 */
public final class G21313NegativeFinalWitnessAccountIdentityDriftTest {
    private G21313NegativeFinalWitnessAccountIdentityDriftTest(){}
    private static void check(boolean valid,String message){
        if(!valid)throw new AssertionError("G21.313 "+message);
    }
    private static void exercise(String variation)throws Exception{
        Path root=Files.createTempDirectory("g21313-"+variation+"-");
        try{
            Path account=root.resolve("selected.properties");
            byte[] bytes=("g21313-"+variation+"-same-bytes")
                .getBytes(StandardCharsets.US_ASCII);
            Files.write(account,bytes);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            byte[] marker=Files.readAllBytes(active);
            Path negative=LocalFinalSaveNegativeWitness
                .witnessPath(account);
            Path extra=root.resolve("preserved-account-alias");
            BasicFileAttributes start=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            AtomicBoolean changed=new AtomicBoolean();
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                leaf->{
                    check(leaf.equals(negative),"wrong exact witness seam");
                    if("replacement".equals(variation)){
                        Files.move(account,extra);
                        Files.write(account,bytes);
                    }else if("hardlink".equals(variation)){
                        Files.createLink(extra,account);
                    }else throw new AssertionError("unknown variation");
                    BasicFileAttributes after=Files.readAttributes(
                        account,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    check(Arrays.equals(bytes,Files.readAllBytes(account)),
                        "account bytes unexpectedly changed");
                    if("replacement".equals(variation))
                        check(!start.fileKey().equals(after.fileKey()),
                            "different-inode precondition missing");
                    else
                        check(start.fileKey().equals(after.fileKey()),
                            "same-inode alias precondition missing");
                    changed.set(true);
                });
            boolean veto=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",1L,1L);
            }catch(IOException error){
                veto=String.valueOf(error.getMessage()).contains(
                    "G21.313 FINAL_WITNESS_ACCOUNT_OBJECT_OR_LINK_DRIFT_NEGATIVE");
            }finally{
                LocalFinalSaveNegativeWitness
                    .setAfterDirectoryForceForTest(null);
            }
            check(changed.get()&&veto,
                variation+" was not rejected after forcing witness");
            check(Files.isRegularFile(negative,
                    LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(extra,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(bytes,Files.readAllBytes(account))&&
                Arrays.equals(marker,Files.readAllBytes(active)),
                "failed publication modified or cleared source evidence");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(account);
            }catch(IOException expected){denied=true;}
            check(denied,"uncertain witness permitted admission");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterDirectoryForceForTest(null);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        exercise("replacement");
        exercise("hardlink");
        System.out.println(
            "G21313_FINAL_WITNESS_ACCOUNT_OBJECT_AND_LINK_DRIFT_PASS"+
            " sameBytesReplacementInodeDenied=true"+
            " sameBytesNewHardlinkDenied=true"+
            " forcedUntrustedWitnessRetained=true"+
            " ACTIVEUnchanged=true"+
            " noAuthenticatedReceipt=true");
    }
}

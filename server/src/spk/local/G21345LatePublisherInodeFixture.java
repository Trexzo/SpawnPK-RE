package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.345–348 real, byte-identical selected inode substitution after
 * complete publication validation; no positive receipt is inferred.
 */
final class G21345LatePublisherInodeFixture {
    enum Scenario { ACCOUNT, ACTIVE, WITNESS, FOREIGN }
    private G21345LatePublisherInodeFixture(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.345–348 "+why);
    }
    static void run(Scenario scenario)throws Exception{
        Path root=Files.createTempDirectory("g21345-late-inode-pub-");
        LocalFinalSaveNegativeWitness
            .setBeforeSuccessfulReturnForTest(null);
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected-identical-bytes".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            Path foreign=root.resolve("foreign.properties");
            Files.write(foreign,"unrelated-identical-bytes".getBytes(
                StandardCharsets.US_ASCII));
            byte[] originalAccount=Files.readAllBytes(account);
            byte[] originalActive=Files.readAllBytes(active);
            byte[] originalForeign=Files.readAllBytes(foreign);
            AtomicBoolean swapped=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setBeforeSuccessfulReturnForTest(
                selectedWitness->{
                    check(witness.equals(selectedWitness),
                        "late cutpoint selected another account");
                    Path target;
                    switch(scenario){
                        case ACCOUNT:target=account;break;
                        case ACTIVE:target=active;break;
                        case WITNESS:target=witness;break;
                        case FOREIGN:target=foreign;break;
                        default:throw new AssertionError("scenario");
                    }
                    BasicFileAttributes old=Files.readAttributes(target,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    byte[] bytes=Files.readAllBytes(target);
                    Path displaced=root.resolve("old-"+scenario.name());
                    Files.move(target,displaced);
                    Files.write(target,bytes,StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                    BasicFileAttributes replacement=Files.readAttributes(
                        target,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    check(!old.fileKey().equals(replacement.fileKey())&&
                        old.size()==replacement.size()&&
                        Arrays.equals(bytes,Files.readAllBytes(target))&&
                        Arrays.equals(bytes,Files.readAllBytes(displaced)),
                        "real same-bytes distinct inode cutpoint");
                    swapped.set(true);
                });
            Path published=null;
            String error=null;
            try{
                published=LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",6L,16L);
            }catch(IOException expected){error=expected.getMessage();}
            finally{
                LocalFinalSaveNegativeWitness
                    .setBeforeSuccessfulReturnForTest(null);
            }
            check(swapped.get(),"late selected-inode swap not performed");
            String expected=scenario==Scenario.WITNESS?
                "G21.343 NEGATIVE_FINAL_WITNESS_LATE_CONTENT_OR_OBJECT_DRIFT":
                "G21.342 NEGATIVE_FINAL_WITNESS_LATE_SOURCE_OBJECT_DRIFT";
            if(scenario==Scenario.FOREIGN)
                check(error==null&&witness.equals(published),
                    "unrelated inode replacement globally poisoned publisher");
            else
                check(error!=null&&error.contains(expected),
                    "selected byte-identical object replacement accepted: "+
                    error);
            check(Files.isRegularFile(witness,
                    LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(originalAccount,Files.readAllBytes(account))&&
                Arrays.equals(originalActive,Files.readAllBytes(active))&&
                Arrays.equals(originalForeign,Files.readAllBytes(foreign)),
                "publication mutated account/ACTIVE/foreign bytes");
            check(Files.isRegularFile(root.resolve(
                    "old-"+scenario.name()),LinkOption.NOFOLLOW_LINKS),
                "displaced original source discarded");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);
            }catch(IOException expectedError){denied=true;}
            check(denied,"negative witness was promoted to admission");
            boolean duplicateRejected=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",6L,17L);
            }catch(IOException expectedError){
                duplicateRejected=String.valueOf(
                    expectedError.getMessage()).contains(
                    "FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");
            }
            check(duplicateRejected,"uncertain leaf overwritten");
            System.out.println(
                "G"+(scenario==Scenario.ACCOUNT?"21345":
                    scenario==Scenario.ACTIVE?"21346":
                    scenario==Scenario.WITNESS?"21347":"21348")+
                "_LATE_PUBLISHER_INODE_"+scenario+"_PASS"+
                " realDistinctInodeIdenticalBytes=true"+
                " lastReturnSelectionProtected=true"+
                " selectedNegativeWitnessPreserved=true"+
                " noAutomaticCleanup=true"+
                " finalSaveAck=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setBeforeSuccessfulReturnForTest(null);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}

package spk.local;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.349–352 real selected/foreign hardlinks at the publisher's late seam. */
final class G21349LateFinalHardlinkFixture {
    enum Scenario { ACCOUNT, ACTIVE, WITNESS, FOREIGN }
    private G21349LateFinalHardlinkFixture(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.349-352 "+why);
    }
    static void run(Scenario scenario)throws Exception{
        Path root=Files.createTempDirectory("g21349-hardlink-late-");
        LocalFinalSaveNegativeWitness.setBeforeSuccessfulReturnForTest(null);
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected-account-bytes".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            Path foreign=root.resolve("unrelated.properties");
            Files.write(foreign,"unrelated-account-bytes".getBytes(
                StandardCharsets.US_ASCII));
            byte[] accountBytes=Files.readAllBytes(account);
            byte[] activeBytes=Files.readAllBytes(active);
            byte[] foreignBytes=Files.readAllBytes(foreign);
            Path alias=root.resolve("added-hardlink-"+scenario);
            AtomicBoolean ran=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setBeforeSuccessfulReturnForTest(
                selected->{
                    check(witness.equals(selected),"wrong final witness");
                    Path target=scenario==Scenario.ACCOUNT?account:
                        scenario==Scenario.ACTIVE?active:
                        scenario==Scenario.WITNESS?witness:foreign;
                    BasicFileAttributes before=Files.readAttributes(
                        target,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    byte[] bytes=Files.readAllBytes(target);
                    Object links=Files.getAttribute(target,"unix:nlink",
                        LinkOption.NOFOLLOW_LINKS);
                    check(before.fileKey()!=null&&links instanceof Number&&
                        ((Number)links).longValue()==1L,
                        "physical single-link precondition missing");
                    Files.createLink(alias,target);
                    Object newLinks=Files.getAttribute(target,"unix:nlink",
                        LinkOption.NOFOLLOW_LINKS);
                    BasicFileAttributes after=Files.readAttributes(
                        target,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    check(newLinks instanceof Number&&
                        ((Number)newLinks).longValue()==2L&&
                        before.fileKey().equals(after.fileKey())&&
                        before.size()==after.size()&&
                        before.lastModifiedTime().equals(after.lastModifiedTime())&&
                        Files.isSameFile(alias,target)&&
                        Arrays.equals(bytes,Files.readAllBytes(alias)),
                        "real hardlink alias must leave original source identical");
                    ran.set(true);
                });
            String error=null;Path returned=null;
            try{
                returned=LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",49L,52L);
            }catch(IOException expected){error=expected.getMessage();}
            finally{
                LocalFinalSaveNegativeWitness
                    .setBeforeSuccessfulReturnForTest(null);
            }
            check(ran.get(),"late seam never injected a hardlink");
            String expected=scenario==Scenario.ACCOUNT?
                "G21.341 NEGATIVE_FINAL_WITNESS_LATE_SOURCE_DRIFT":
                scenario==Scenario.ACTIVE?
                "G21.342 NEGATIVE_FINAL_WITNESS_LATE_ACTIVE_ALIAS":
                "G21.343 NEGATIVE_FINAL_WITNESS_LATE_ALIAS";
            if(scenario==Scenario.FOREIGN)
                check(error==null&&witness.equals(returned),
                    "foreign alias improperly vetoed selected negative publication: "+error);
            else
                check(error!=null&&error.contains(expected),
                    "late selected hardlink passed existing production check: "+error);
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(alias,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(accountBytes,Files.readAllBytes(account))&&
                Arrays.equals(activeBytes,Files.readAllBytes(active))&&
                Arrays.equals(foreignBytes,Files.readAllBytes(foreign)),
                "publication cleaned or altered source during alias injection");
            boolean denied=false,duplicate=false;
            try{LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);}
            catch(IOException yes){denied=true;}
            try{LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",49L,53L);}
            catch(IOException yes){duplicate=String.valueOf(
                yes.getMessage()).contains("FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");}
            check(denied&&duplicate,
                "negative witness admission/overwrite safeguards lost");
            String id=scenario==Scenario.ACCOUNT?"21349":
                scenario==Scenario.ACTIVE?"21350":
                scenario==Scenario.WITNESS?"21351":"21352";
            System.out.println("G"+id+"_LATE_PUBLISHER_HARDLINK_"+scenario+"_PASS"+
                " originalInodeAndBytesPreserved=true"+
                " actualNewLinkName=true"+
                " selectedFailClosed="+(scenario!=Scenario.FOREIGN)+
                " noGlobalForeignVeto="+(scenario==Scenario.FOREIGN)+
                " autoRecovery=false finalSaveAck=false");
        }finally{
            LocalFinalSaveNegativeWitness.setBeforeSuccessfulReturnForTest(null);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path file:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
}

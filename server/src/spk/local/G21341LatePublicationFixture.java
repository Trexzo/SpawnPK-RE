package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Real filesystem production publisher cutpoint tests G21.341-344. */
final class G21341LatePublicationFixture {
    enum Scenario { ACCOUNT, ACTIVE, WITNESS, FOREIGN }
    private G21341LatePublicationFixture(){}
    private static void require(boolean value,String message){
        if(!value)throw new AssertionError("G21.341-344 "+message);
    }
    static void run(Scenario scenario)throws Exception{
        Path root=Files.createTempDirectory("g21341-late-publication-");
        LocalFinalSaveNegativeWitness.setBeforeSuccessfulReturnForTest(null);
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected-source-for-publish".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            Path foreign=root.resolve("foreign.properties");
            Files.write(foreign,"foreign-source-for-publish".getBytes(
                StandardCharsets.US_ASCII));
            byte[] accountBefore=Files.readAllBytes(account);
            byte[] activeBefore=Files.readAllBytes(active);
            byte[] foreignBefore=Files.readAllBytes(foreign);
            AtomicBoolean cut=new AtomicBoolean(false);
            LocalFinalSaveNegativeWitness.setBeforeSuccessfulReturnForTest(
                selectedWitness->{
                    require(selectedWitness.equals(witness),
                        "last-cutpoint path differed from exact selected");
                    Path target;
                    switch(scenario){
                        case ACCOUNT:target=account;break;
                        case ACTIVE:target=active;break;
                        case WITNESS:target=witness;break;
                        case FOREIGN:target=foreign;break;
                        default:throw new AssertionError("invalid scenario");
                    }
                    BasicFileAttributes before=Files.readAttributes(
                        target,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    FileTime recorded=before.lastModifiedTime();
                    try(FileChannel file=FileChannel.open(target,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)){
                        file.position(2L);
                        file.write(ByteBuffer.wrap(new byte[]{'X'}));
                        file.force(true);
                    }
                    Files.setLastModifiedTime(target,recorded);
                    BasicFileAttributes changed=Files.readAttributes(
                        target,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    require(before.fileKey().equals(changed.fileKey())&&
                        before.size()==changed.size()&&
                        recorded.equals(changed.lastModifiedTime()),
                        "same inode, size and restored-mtime precondition");
                    cut.set(true);
                });
            boolean refused=false;
            Path result=null;
            String error=null;
            try{
                result=LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",4L,14L);
            }catch(IOException expected){
                refused=true;
                error=expected.getMessage();
            }finally{
                LocalFinalSaveNegativeWitness
                    .setBeforeSuccessfulReturnForTest(null);
            }
            require(cut.get(),"late production cutpoint not reached");
            if(scenario==Scenario.FOREIGN)
                require(!refused&&witness.equals(result),
                    "foreign mutation contaminated selected publication");
            else{
                String code=scenario==Scenario.WITNESS?
                    "G21.343 NEGATIVE_FINAL_WITNESS_LATE_CONTENT_OR_OBJECT_DRIFT":
                    "G21.341 NEGATIVE_FINAL_WITNESS_LATE_SOURCE_DRIFT";
                require(refused&&error!=null&&error.contains(code),
                    "selected late mutation returned successful witness: "+error);
            }
            require(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS),
                "uncertain witness leaf was removed");
            require(Arrays.equals(accountBefore,Files.readAllBytes(account))==
                    (scenario!=Scenario.ACCOUNT)&&
                Arrays.equals(activeBefore,Files.readAllBytes(active))==
                    (scenario!=Scenario.ACTIVE)&&
                Arrays.equals(foreignBefore,Files.readAllBytes(foreign))==
                    (scenario!=Scenario.FOREIGN),
                "publication touched a foreign or unchanged source");
            boolean negative=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);
            }catch(IOException expected){negative=true;}
            require(negative,"witness was promoted to positive admission");
            boolean duplicate=false;
            try{
                LocalFinalSaveNegativeWitness.publishOnce(
                    account,"selected",4L,15L);
            }catch(IOException expected){
                duplicate=String.valueOf(expected.getMessage()).contains(
                    "FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");
            }
            require(duplicate,"uncertain witness was retried/overwritten");
            System.out.println(
                "G"+(scenario==Scenario.ACCOUNT?"21341":
                    scenario==Scenario.ACTIVE?"21342":
                    scenario==Scenario.WITNESS?"21343":"21344")+
                "_LATE_FINAL_PUBLICATION_"+scenario+"_PASS"+
                " actualPostValidationMutation=true"+
                " sameInodeAndRestoredMetadata=true"+
                " selectedNegativeScope=true"+
                " automaticCleanup=false"+
                " authenticatedFinalSaveAck=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setBeforeSuccessfulReturnForTest(null);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}

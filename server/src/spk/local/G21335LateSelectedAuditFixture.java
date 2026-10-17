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

/** Exact filesystem fixture shared only by G21335–38 focused entrypoints. */
final class G21335LateSelectedAuditFixture {
    enum Scenario { ACCOUNT, ACTIVE, WITNESS, FOREIGN }
    private G21335LateSelectedAuditFixture(){}
    static void require(boolean b,String why){
        if(!b)throw new AssertionError("G21.335-338 "+why);
    }
    static void run(Scenario selected)throws Exception{
        Path root=Files.createTempDirectory("g21335-late-forensic-");
        try{
            Path account=root.resolve("selected.properties");
            Files.write(account,"selected-source-forensic".getBytes(
                StandardCharsets.US_ASCII));
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",3L,9L);
            Path foreign=root.resolve("foreign.properties");
            Files.write(foreign,"unrelated-foreign-source".getBytes(
                StandardCharsets.US_ASCII));
            byte[] sourceBefore=Files.readAllBytes(account);
            byte[] activeBefore=Files.readAllBytes(active);
            byte[] witnessBefore=Files.readAllBytes(witness);
            byte[] foreignBefore=Files.readAllBytes(foreign);
            long count;
            try(Stream<Path> list=Files.list(root)){
                count=list.count();
            }
            AtomicBoolean injected=new AtomicBoolean(false);
            String refusedMessage=null;
            LocalFinalSaveWitnessForensicAudit.Evidence audit=null;
            try{
                audit=LocalFinalSaveWitnessForensicAudit.inspect(
                    account,"selected",ignored->{},selectedWitness->{
                        require(selectedWitness.equals(witness),
                            "late seam selected wrong witness");
                        final Path changed;
                        switch(selected){
                            case ACCOUNT:changed=account;break;
                            case ACTIVE:changed=active;break;
                            case WITNESS:changed=witness;break;
                            case FOREIGN:changed=foreign;break;
                            default:throw new AssertionError("scenario");
                        }
                        BasicFileAttributes original=Files.readAttributes(
                            changed,BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                        FileTime mtime=original.lastModifiedTime();
                        try(FileChannel channel=FileChannel.open(changed,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS)){
                            channel.position(2L);
                            channel.write(ByteBuffer.wrap(new byte[]{'X'}));
                            channel.force(true);
                        }
                        Files.setLastModifiedTime(changed,mtime);
                        BasicFileAttributes mutated=Files.readAttributes(
                            changed,BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                        require(original.fileKey().equals(mutated.fileKey())&&
                                original.size()==mutated.size()&&
                                mtime.equals(mutated.lastModifiedTime()),
                            "real same inode/size/restored mtime precondition");
                        injected.set(true);
                    });
            }catch(IOException expected){
                refusedMessage=expected.getMessage();
            }
            require(injected.get(),"late-stage mutation hook not reached");
            switch(selected){
                case ACCOUNT:
                case ACTIVE:
                    require(refusedMessage!=null&&refusedMessage.contains(
                        "G21.335 FINAL_WITNESS_SELECTED_SOURCES_DRIFTED"),
                        "late selected account/ACTIVE byte change accepted");
                    break;
                case WITNESS:
                    require(refusedMessage!=null&&refusedMessage.contains(
                        "G21.337 FINAL_WITNESS_CHANGED_AFTER_SOURCE_AUDIT"),
                        "late selected witness bytes accepted");
                    break;
                case FOREIGN:
                    require(refusedMessage==null&&audit!=null&&
                        audit.state==LocalFinalSaveWitnessForensicAudit
                            .State.MATCHES_UNTRUSTED_NEGATIVE&&
                        audit.report().contains("finalSaveAck=false")&&
                        audit.report().contains("recoveryAuthorized=false"),
                        "foreign mutation contaminated exact selected audit");
                    break;
            }
            require(Arrays.equals(sourceBefore,Files.readAllBytes(account))==
                    (selected!=Scenario.ACCOUNT)&&
                Arrays.equals(activeBefore,Files.readAllBytes(active))==
                    (selected!=Scenario.ACTIVE)&&
                Arrays.equals(witnessBefore,Files.readAllBytes(witness))==
                    (selected!=Scenario.WITNESS)&&
                Arrays.equals(foreignBefore,Files.readAllBytes(foreign))==
                    (selected!=Scenario.FOREIGN),
                "read-only operator scan edited another exact leaf");
            long countAfter;
            try(Stream<Path> files=Files.list(root)){
                countAfter=files.count();
            }
            require(count==countAfter,"operator scan created sidecars");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    account);
            }catch(IOException expected){denied=true;}
            require(denied,"negative witness became admission authority");
            System.out.println("G"+(selected==Scenario.ACCOUNT?"21335":
                    selected==Scenario.ACTIVE?"21336":
                    selected==Scenario.WITNESS?"21337":"21338")+
                "_LATE_FORENSIC_"+selected+"_PASS"+
                " actualInPlaceFilesystemMutation=true"+
                " selectedScopePreserved=true"+
                " NOFOLLOW_AUDIT=true"+
                " finalSaveAck=false"+
                " automaticRecovery=false");
        }finally{
            try(Stream<Path> tree=Files.walk(root)){
                for(Path item:tree.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
    }
}

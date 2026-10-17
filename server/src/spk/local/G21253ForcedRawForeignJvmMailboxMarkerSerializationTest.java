package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.253 real second-JVM G21.39 marker publication contention
 * against opt-in forced RAW account publication held pre-rename.
 */
public final class G21253ForcedRawForeignJvmMailboxMarkerSerializationTest {
    private static final String OWNER=AccountStore.CANONICAL_USERNAME;
    private static final String READY="G21253_CHILD_MARKER_ATTEMPT_READY";
    private G21253ForcedRawForeignJvmMailboxMarkerSerializationTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.253 "+stage);
    }
    private static void flags(Path root){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        System.setProperty(LocalOptInRawTemp.PROPERTY,"true");
        System.setProperty(LocalOptInRawTemp.FORCE_PROPERTY,"true");
    }
    private static Path review(){
        return new MailboxStrictWriteIntentFence(
            LocalAccountProfiles::accountFile).fencePath(OWNER);
    }

    private static void child(Path root,Path ready)throws Exception{
        flags(root);
        Path file=AccountStore.accountFile();
        check(Files.exists(file,LinkOption.NOFOLLOW_LINKS),
            "original account missing before child");
        Path marker=review();
        Files.writeString(ready,READY,StandardCharsets.US_ASCII);
        MailboxAccountPublicationCoordinator
            .withExclusivePublication(file,()->{
                Files.createFile(marker);
                return null;
            });
        check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
            "child marker not published after publication lock release");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "child leaked publication lease");
        System.out.println("G21253_CHILD_FOREIGN_JVM_MARKER_AFTER_LOCK_PASS");
    }

    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21253-foreign-marker-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] before=new String[keys.length];
        for(int i=0;i<keys.length;i++)before[i]=System.getProperty(keys[i]);
        flags(root);
        Process foreign=null;
        Thread writer=null;
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path account=AccountStore.accountFile();
            Path marker=review();
            check(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "marker already present");
            AtomicReference<Throwable> writeProblem=new AtomicReference<>();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(path->{
                check(account.equals(path),"wrong raw account selected");
                entered.countDown();
                try{
                    if(!release.await(65L,TimeUnit.SECONDS))
                        throw new IOException("G21.253 release timeout");
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException("G21.253 interrupted",interrupted);
                }
            });
            writer=new Thread(()->{
                try{
                    AccountStore.save(
                        player.bank(),player.equipment(),
                        player.movement(),player.petState(),
                        player.playerState());
                }catch(Throwable problem){
                    writeProblem.set(problem);
                }
            },"g21253-parent-forced-raw");
            writer.start();
            check(entered.await(35L,TimeUnit.SECONDS),
                "parent writer never held publication lock");
            Path ready=root.resolve("child-ready");
            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder pb=new ProcessBuilder(
                java.toString(),"-cp",System.getProperty("java.class.path"),
                G21253ForcedRawForeignJvmMailboxMarkerSerializationTest
                    .class.getName(),"--child",
                root.toString(),ready.toString());
            pb.redirectErrorStream(true);
            foreign=pb.start();
            // G21.270 fixture repair: Files.writeString can publish
            // the ready leaf before its payload is complete. An
            // existence-only poll can spuriously fail on a partial
            // string under hosted CI concurrency. Wait for the
            // COMPLETE marker, while retaining a bounded deadline.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45L);
            boolean signaled=false;
            while(foreign.isAlive()&&System.nanoTime()<deadline){
                if(Files.exists(ready,LinkOption.NOFOLLOW_LINKS)&&
                   READY.equals(Files.readString(
                       ready,StandardCharsets.US_ASCII))){
                    signaled=true;
                    break;
                }
                Thread.sleep(25L);
            }
            if(!signaled){
                String childState=foreign.isAlive()
                    ?"FOREIGN_CHILD_ALIVE_NO_COMPLETE_READY"
                    :new String(foreign.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                throw new AssertionError(
                    "G21.270 foreign JVM full READY handshake absent "+
                    childState);
            }
            Thread.sleep(200L);
            check(foreign.isAlive()&&
                !Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "foreign marker slipped past held publication lock");
            release.countDown();
            writer.join(45_000L);
            check(!writer.isAlive()&&writeProblem.get()==null,
                "parent forced writer failed "+writeProblem.get());
            check(foreign.waitFor(45L,TimeUnit.SECONDS),
                "foreign marker writer did not finish");
            String out=new String(
                foreign.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            check(foreign.exitValue()==0&&out.contains(
                "G21253_CHILD_FOREIGN_JVM_MARKER_AFTER_LOCK_PASS"),
                "foreign marker publisher failed "+out);
            check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "marker absent after publication lock release");
            byte[] after=Files.readAllBytes(account);
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            boolean denied=false;
            try{
                AccountStore.save(
                    player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState());
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT");
            }
            check(denied,"new forced writer ignored foreign marker");
            check(Arrays.equals(after,Files.readAllBytes(account)),
                "denied forced writer rewrote account");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leaked");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 publication lease leaked");
            System.out.println(
                "G21253_FORCED_RAW_FOREIGN_JVM_MARKER_SERIALIZATION_PASS"+
                " independentJavaProcesses=true"+
                " childMarkerCannotPublishBeforeParentMove=true"+
                " foreignMarkerPublishedAfterLock=true"+
                " nextForcedRawDenied=true"+
                " noReservationOrPublicationLeaseLeak=true"+
                " durableSessionEpoch=false");
        }finally{
            release.countDown();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            if(foreign!=null&&foreign.isAlive()){
                foreign.destroyForcibly();
                foreign.waitFor(5L,TimeUnit.SECONDS);
            }
            if(writer!=null&&writer.isAlive())writer.join(3_000L);
            for(int i=0;i<keys.length;i++){
                if(before[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],before[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0]))
            child(Path.of(args[1]),Path.of(args[2]));
        else if(args.length==0)parent();
        else throw new IllegalArgumentException("G21.253 unexpected args");
    }
}

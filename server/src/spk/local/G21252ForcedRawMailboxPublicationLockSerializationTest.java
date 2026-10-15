package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.252 genuine two-thread G21.39 account marker/write exclusion. */
public final class G21252ForcedRawMailboxPublicationLockSerializationTest {
    private G21252ForcedRawMailboxPublicationLockSerializationTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.252 "+message);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21252-marker-lock-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] original=new String[keys.length];
        for(int i=0;i<keys.length;i++)original[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch resume=new CountDownLatch(1);
        CountDownLatch contenderStarted=new CountDownLatch(1);
        CountDownLatch contenderFinished=new CountDownLatch(1);
        Thread writer=null,markerWriter=null;
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path account=AccountStore.accountFile();
            Path review=new MailboxStrictWriteIntentFence(
                LocalAccountProfiles::accountFile)
                .fencePath(AccountStore.CANONICAL_USERNAME);
            check(!Files.exists(review,LinkOption.NOFOLLOW_LINKS),
                "unexpected prior review marker");

            AtomicReference<Throwable> writerFailure=new AtomicReference<>();
            AtomicReference<Throwable> markerFailure=new AtomicReference<>();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(exact->{
                check(account.equals(exact),"wrong forced account held");
                entered.countDown();
                try{
                    if(!resume.await(15L,TimeUnit.SECONDS))
                        throw new IOException("G21.252 writer latch timeout");
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new IOException("G21.252 writer interrupted",e);
                }
            });
            writer=new Thread(()->{
                try{
                    AccountStore.save(
                        player.bank(),player.equipment(),
                        player.movement(),player.petState(),
                        player.playerState());
                }catch(Throwable failure){
                    writerFailure.set(failure);
                }
            },"g21252-forced-account-writer");
            writer.start();
            check(entered.await(15L,TimeUnit.SECONDS),
                "forced writer did not reach pre-move hook");

            markerWriter=new Thread(()->{
                contenderStarted.countDown();
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(account,()->{
                            Files.createFile(review);
                            return null;
                        });
                }catch(Throwable failure){
                    markerFailure.set(failure);
                }finally{
                    contenderFinished.countDown();
                }
            },"g21252-review-sidecar-writer");
            markerWriter.start();
            check(contenderStarted.await(5L,TimeUnit.SECONDS),
                "marker publisher not scheduled");
            // The forced writer is deliberately parked inside G21.39.
            // A cooperating marker writer cannot publish a negative
            // sidecar into the last-check-before-rename window.
            Thread.sleep(150L);
            check(contenderFinished.getCount()==1L&&
                !Files.exists(review,LinkOption.NOFOLLOW_LINKS),
                "marker bypassed held account publication lock");
            resume.countDown();
            writer.join(15_000L);
            markerWriter.join(15_000L);
            check(!writer.isAlive()&&!markerWriter.isAlive(),
                "writer or marker publisher timed out");
            check(writerFailure.get()==null,
                "forced publication threw "+writerFailure.get());
            check(markerFailure.get()==null,
                "cooperating marker writer threw "+markerFailure.get());
            check(Files.exists(review,LinkOption.NOFOLLOW_LINKS),
                "marker did not publish after lock release");
            byte[] after=Files.readAllBytes(account);
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            boolean refused=false;
            try{
                AccountStore.save(
                    player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState());
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT");
            }
            check(refused,
                "follow-on forced write ignored newly published marker");
            check(Arrays.equals(after,Files.readAllBytes(account)),
                "follow-on forced write changed reviewed account bytes");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "raw reservation leak");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 JVM lease leak");
            System.out.println(
                "G21252_FORCED_RAW_MARKER_PUBLICATION_SERIALIZATION_PASS"+
                " forcedRawHoldsG2139ThroughMove=true"+
                " concurrentMarkerWaited=true"+
                " markerPublishedAfterForceWrite=true"+
                " subsequentForcedSaveVeto=true"+
                " rawAndPublicationLeasesReleased=true"+
                " uncooperativeExternalSwapUnproven=true"+
                " durableOwnerEpoch=false");
        }finally{
            resume.countDown();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            if(writer!=null&&writer.isAlive())writer.join(3_000L);
            if(markerWriter!=null&&markerWriter.isAlive())
                markerWriter.join(3_000L);
            for(int i=0;i<keys.length;i++){
                if(original[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],original[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}

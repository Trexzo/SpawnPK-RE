package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.220: one-shot G21.188 ACTIVE review remains a NEGATIVE even
 * after a genuine completed World final-save postimage and OS lock
 * release. This is deliberately NOT a reusable healthy login policy.
 */
public final class G21220CoreHealthyFinalOneShotReviewNoRetirementTest {
    private static final String USER="g21220-healthy-review";
    private static final String NS="core_g21220";
    private G21220CoreHealthyFinalOneShotReviewNoRetirementTest(){}
    private static void check(boolean ok,String stage){
        if(!ok)throw new AssertionError("G21.220 healthy "+stage);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21220-no-retire-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalReviewIntentAdmissionVeto.PROPERTY,
            LocalOneShotActiveReviewOnAdmission.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)
            System.setProperty(keys[i],"true");
        World world=null;
        LocalJvmAccountSessionLease.Token original=null;
        LocalJvmAccountSessionLease.Token candidate=null;
        WorldPlayer owner=null;
        long generation=-1L;
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            original=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(original!=null,"original opt-in Token absent");
            LocalReviewIntentAdmissionVeto.requireConfiguration(true,true);
            LocalOneShotActiveReviewOnAdmission.requireConfiguration(
                true,true,true);
            LocalReviewIntentAdmissionVeto.requireAdmissible(
                true,true,original);
            LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration(
                true,true,original);
            Path account=LocalAccountProfiles.accountFile(USER);
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            owner=new WorldPlayer();
            generation=world.registerPlayer(owner,USER);
            original.bindAdmittedWorldOwner(owner,generation);
            world.start();

            final World live=world;
            final WorldPlayer player=owner;
            final long registered=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> receipt=
                new AtomicReference<>();
            world.submitAndWait(player,registered,()->{
                player.snapshotExtensions().replaceNamespace(
                    NS,Collections.singletonMap(
                        "phase","HEALTHY_CONFIRMED_WORLD_FINAL"));
                receipt.set(live.persistence().captureAndSave(
                    USER,player,registered,0,
                    "[G21.220] ","REAL_COMPLETED_FINAL"));
            },5_000L);
            check(receipt.get()!=null,"real final SaveTicket missing");
            receipt.get().completion.get(10L,TimeUnit.SECONDS);
            check(repository.load(USER).isPresent(),
                "healthy final account postimage absent");
            check("HEALTHY_CONFIRMED_WORLD_FINAL".equals(
                    repository.load(USER).get().values().get(
                        "extension.core_g21220.phase")),
                "healthy World final extension not committed");
            check(world.unregisterPlayer(owner,generation),
                "healthy World owner unregister failed");
            original.releaseAfterConfirmedFinalSave();
            original=null;
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "confirmed final release did not clear Token");
            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                "healthy save incorrectly erased one-shot ACTIVE");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);

            // OS session lock is freely reacquirable after healthy
            // final save, but G21.188 is a permanent negative and
            // admission MUST still fail. No implicit review cleanup.
            try(World next=World.isolatedForTest(60_000L,repository)){
                candidate=LocalJvmAccountSessionLease.acquire(
                    next.persistence(),USER);
                check(candidate!=null,"second lock not reacquired");
                boolean denied=false;
                try{
                    LocalReviewIntentAdmissionVeto.requireAdmissible(
                        true,true,candidate);
                }catch(IllegalStateException expected){
                    denied=expected.getMessage()!=null&&
                        expected.getMessage().contains(
                            "REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY");
                }
                check(denied,
                    "healthy confirmed final wrongly authorized review retirement");
                check(next.players().size()==0,
                    "vetoed next World received account member");
                LocalDurableAccountReviewIntentPrimitive
                    .requireReviewMarker(account);
                candidate.releaseAfterInitializationFailure();
                candidate=null;
            }
            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                "second admission changed ACTIVE marker");
            System.out.println(
                "G21220_HEALTHY_FINAL_ONE_SHOT_REVIEW_NOT_RETIRED_PASS"+
                " activeForcedBeforeRegistration=true"+
                " realWorldFinalSaveConfirmed=true"+
                " originalSessionOsLockReleased=true"+
                " oneShotMarkerNeverAutomaticallyCleared=true"+
                " freshOsLockReacquirable=true"+
                " reviewVetoDeniedNewLogin=true"+
                " safePositiveRelogNotImplemented=true"+
                " markerCleanupAuthority=false"+
                " durableOwnerEpoch=false");
        }finally{
            try{
                if(candidate!=null)candidate.releaseAfterInitializationFailure();
                if(world!=null&&owner!=null&&generation>0L)
                    world.unregisterPlayer(owner,generation);
                if(original!=null)original.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(previous[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],previous[i]);
                }
                try(Stream<Path> entries=Files.walk(root)){
                    for(Path path:entries.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }
}

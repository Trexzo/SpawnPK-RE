package spk.local;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.205 TEST-ONLY synthetic negative: a captured World-owned autosave
 * waits behind checkpointLock while its original JVM Token is forcibly
 * released and replaced with a DIFFERENT token for the same path/owner.
 *
 * In a genuine session that release/rebind MUST NOT happen while the
 * World member exists. This deliberately invalid lifecycle fixture
 * isolates the immutable captured-owner check in the async worker.
 *
 * No production code changes, no positive account recovery or release
 * authorization, no cross-JVM/crash/RAW-writer guarantee.
 */
public final class G21205CoreDelayedCheckpointReplacedLeaseNegativeTest {
    private static final String USER="g21205-checkpoint-capture-owner";
    private static final String NAMESPACE="core_g21205";

    private G21205CoreDelayedCheckpointReplacedLeaseNegativeTest(){}

    private static void check(boolean yes,String description){
        if(!yes)throw new AssertionError("G21.205 "+description);
    }

    @FunctionalInterface private interface Condition {
        boolean satisfied()throws Exception;
    }

    private static void await(Condition condition,String stage)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12L);
        while(System.nanoTime()<deadline){
            if(condition.satisfied())return;
            Thread.sleep(10L);
        }
        check(condition.satisfied(),"timeout "+stage);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21205-delayed-checkpoint-");
        String property="spk.local.accountFile";
        String leaseProperty=LocalJvmAccountSessionLease.PROPERTY;
        String lockProperty=LocalCrossJvmAccountSessionLock.PROPERTY;
        String oldAccount=System.getProperty(property);
        String oldLease=System.getProperty(leaseProperty);
        String oldCrossJvm=System.getProperty(lockProperty);
        System.setProperty(property,root.resolve("opensrc.properties").toString());
        System.setProperty(leaseProperty,"true");
        System.setProperty(lockProperty,"false");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token original=null;
        final AtomicReference<LocalJvmAccountSessionLease.Token>
            successor=new AtomicReference<>();
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Files.createDirectories(account.getParent());
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "test account already exists");

            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repository);
            player=new WorldPlayer();

            original=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(original!=null,"opt-in original lease absent");
            generation=world.registerPlayer(player,USER);
            original.bindAdmittedWorldOwner(player,generation);
            world.start();

            Field field=WorldPlayerPersistence.class
                .getDeclaredField("checkpointLock");
            field.setAccessible(true);
            Object checkpointLock=field.get(world.persistence());
            check(checkpointLock!=null,"checkpoint lock absent");

            final World worldRef=world;
            final WorldPlayer owner=player;
            final LocalJvmAccountSessionLease.Token oldToken=original;
            final long capturedGeneration=generation;
            final long capturedBefore=world.persistence()
                .checkpointCapturedCount();
            final long writtenBefore=world.persistence()
                .checkpointWrittenCount();
            final long failedBefore=world.persistence().failedCount();
            final int boundCheckBefore=LocalJvmAccountSessionLease
                .captureBoundWorkerChecksForTest();
            final AtomicReference<LocalJvmAccountSessionLease.SaveCaptureWitness>
                oldWitness=new AtomicReference<>();

            // World action holds the SAME lock used by checkpoint drain
            // and captures a genuine World snapshot before the swap.
            world.submitAndWait(owner,capturedGeneration,()->{
                synchronized(checkpointLock){
                    owner.snapshotExtensions().replaceNamespace(
                        NAMESPACE,
                        Collections.singletonMap(
                            "phase","UNPUBLISHED_OLD_OWNER"));
                    worldRef.persistence().checkpointDue(100L);
                    check(worldRef.persistence().checkpointCapturedCount()==
                            capturedBefore+1L,
                        "real checkpoint not captured");

                    LocalJvmAccountSessionLease.SaveCaptureWitness witness=
                        LocalJvmAccountSessionLease.captureSaveWitness(
                            account,USER,owner,capturedGeneration);
                    oldWitness.set(witness);
                    check(witness!=null,
                        "failed to observe original capture provenance");

                    // TEST-ONLY intentionally invalid lifecycle: release
                    // the STILL-REGISTERED World owner's original token
                    // without a final save, then create a successor for
                    // the same path/player/generation. This reproduces
                    // the precise substitution the worker must refuse.
                    oldToken.releaseAfterInitializationFailure();
                    LocalJvmAccountSessionLease.Token later=
                        LocalJvmAccountSessionLease.acquire(
                            worldRef.persistence(),USER);
                    check(later!=null,"synthetic successor token missing");
                    successor.set(later);
                    later.bindAdmittedWorldOwner(
                        owner,capturedGeneration);

                    // The old G21.201 lookup by CURRENT PATH would accept
                    // this artificially rebound same owner. The new
                    // G21.202 capture-original check MUST refuse it.
                    check(LocalJvmAccountSessionLease
                        .requireCapturedSaveOwnerIfLeased(
                            account,USER,owner,capturedGeneration),
                        "path-only guard did not demonstrate gap");
                    boolean rejected=false;
                    try{
                        LocalJvmAccountSessionLease
                            .requireCapturedWitnessBeforeWrite(
                                witness,account,USER,owner,
                                capturedGeneration);
                    }catch(IllegalStateException expected){
                        rejected=expected.getMessage()!=null&&
                            expected.getMessage().contains(
                                "SAVE_CAPTURE_ORIGINAL_LEASE_LOST_OR_REPLACED");
                    }
                    check(rejected,
                        "original capture accepted later same-path Token");
                }
            },5_000L);

            // The real queued CheckpointDrainTask now runs with its
            // pre-swap PendingCheckpoint. The FilePlayerRepository must
            // not receive the snapshot after the captured token changed.
            await(()->worldRef.persistence().failedCount()>failedBefore,
                "actual queued checkpoint fails owner check");
            check(world.persistence().checkpointWrittenCount()==writtenBefore,
                "invalidated checkpoint was counted as a completed write");
            check(world.persistence().checkpointCapturedCount()==
                    capturedBefore+1L,
                "fixture unexpectedly captured additional checkpoint");
            check(LocalJvmAccountSessionLease
                    .captureBoundWorkerChecksForTest()==boundCheckBefore,
                "rejected captured Token counted as valid worker owner");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "replaced-lease checkpoint published account bytes");
            check(oldWitness.get()!=null,
                "old immutable witness unexpectedly disappeared");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "synthetic successor lease ownership was silently released");

            System.out.println(
                "G21205_DELAYED_CHECKPOINT_REPLACED_LEASE_NEGATIVE_PASS"+
                " actualWorldCheckpointCaptured=true"+
                " workerBlockedUntilTokenReplacement=true"+
                " originalTokenReleasedOnlyInTest=true"+
                " successorSameAccountAndPlayerGeneration=true"+
                " currentPathOnlyGuardWouldAccept=true"+
                " immutableOriginalTokenGuardRejects=true"+
                " actualCheckpointWorkerFailureObserved=true"+
                " checkpointPublished=false"+
                " accountFileCreated=false"+
                " activeReviewRetirement=false"+
                " positiveRecovery=false"+
                " authenticatedDurableEpoch=false"+
                " powerLossProven=false"+
                " rawWriterExclusion=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(successor.get()!=null)
                    successor.get().releaseAfterInitializationFailure();
                if(original!=null)
                    original.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                if(oldAccount==null)System.clearProperty(property);
                else System.setProperty(property,oldAccount);
                if(oldLease==null)System.clearProperty(leaseProperty);
                else System.setProperty(leaseProperty,oldLease);
                if(oldCrossJvm==null)System.clearProperty(lockProperty);
                else System.setProperty(lockProperty,oldCrossJvm);
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path p:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}

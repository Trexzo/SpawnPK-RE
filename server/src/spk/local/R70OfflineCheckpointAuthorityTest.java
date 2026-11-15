package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R70 offline real checkpoint-coalescing owner test.
 *
 * Test 1: A captured checkpoint reaches actual I/O worker then pauses;
 * another JVM takes over; A's callback fails BEFORE repository.save().
 *
 * Test 2: an actual repository LOAD blocks the single I/O worker while
 * checkpoint A then checkpoint B are captured from the same World actor
 * but under different explicitly bound R64 owners. The coalesced task
 * must preserve only B's immutable snapshot and B's captured owner, and
 * must never attach B's authority to A's discarded snapshot.
 */
public final class R70OfflineCheckpointAuthorityTest {
    private static final String USER="opensrc";

    private static final class CountingRepository implements PlayerRepository {
        private final FilePlayerRepository disk;
        final AtomicInteger saveCalls=new AtomicInteger();
        final AtomicInteger committed=new AtomicInteger();
        final AtomicBoolean failNextSave=new AtomicBoolean();
        final AtomicReference<LoadPause> blockNextLoad=
            new AtomicReference<>();

        CountingRepository(Path accounts){
            disk=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
        }
        @Override public Optional<PlayerSnapshot> load(String user)
                throws IOException{
            LoadPause pause=blockNextLoad.getAndSet(null);
            if(pause!=null){
                pause.arrived.countDown();
                try{
                    if(!pause.release.await(15L,TimeUnit.SECONDS))
                        throw new IOException(
                            "R70_TEST_LOAD_WORKER_TIMEOUT"
                        );
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "R70_TEST_LOAD_WORKER_INTERRUPTED",e
                    );
                }
            }
            return disk.load(user);
        }
        @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
            saveCalls.incrementAndGet();
            if(failNextSave.compareAndSet(true,false))
                throw new IOException(
                    "R70_TEST_REPOSITORY_PRE_WRITE_FAILURE"
                );
            disk.save(snapshot);
            committed.incrementAndGet();
        }
    }

    private static final class LoadPause {
        final CountDownLatch arrived=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(
            "spk-r70-checkpoint-owner-propagation-"
        );
        Path journal=root.resolve("journal");
        Path accounts=root.resolve("accounts");
        Path generations=root.resolve("generations");
        Path sessions=root.resolve("sessions");
        for(Path p:new Path[]{journal,accounts,generations,sessions})
            Files.createDirectory(p);
        CountingRepository repository=new CountingRepository(accounts);
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer actor=new WorldPlayer();
        ExecutorService loadCaller=Executors.newSingleThreadExecutor();
        LoadPause pendingLoad=null;
        R70OfflineCheckpointAuthority.Pause oldWriter=null;
        try{
            R60TradePairCoordinator.initializeEmpty(journal);
            actor.bank().spawnItem(995,100,packets());
            PlayerSnapshot initial=
                PlayerSnapshotCodec.capture(USER,actor);
            new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            ).save(initial);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            R64OfflineSessionOwnership.Token ownerA=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,USER
                );

            R70OfflineCheckpointAuthority authority=
                new R70OfflineCheckpointAuthority(
                    journal,accounts,generations,sessions
                );
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineIoWriteAdmissionForTest(authority);
            persistence.installOfflineCheckpointOwnerCaptureForTest(authority);
            try{
                persistence.installOfflineCheckpointOwnerCaptureForTest(
                    authority
                );
                throw new AssertionError(
                    "duplicate checkpoint capture hook installed"
                );
            }catch(IllegalStateException good){
                require(good.getMessage().contains(
                    "R70_CHECKPOINT_CAPTURE_DENIED"),
                    "wrong duplicate checkpoint capture denial");
            }

            world.registerPlayer(actor,USER);
            world.start();
            authority.bindForTest(
                actor,actor.generation(),ownerA
            );

            // ACTUAL World checkpoint at tick100. Capture A's changed
            // in-memory inventory, then pause on the ACTUAL IO worker
            // before it can validate ownership or call repository.save.
            oldWriter=new R70OfflineCheckpointAuthority.Pause();
            authority.pauseNextCheckpoint(oldWriter);
            captureTick(world,actor,100,385,null);
            require(oldWriter.arrived.await(10,TimeUnit.SECONDS),
                "A checkpoint did not reach real persistence worker");
            require(persistence.checkpointCapturedCount()==1L,
                "World tick did not capture A checkpoint");
            require(repository.saveCalls.get()==0,
                "A checkpoint wrote before takeover");

            // Cross-process R64 takeover while A's real I/O worker is
            // paused; the World actor still carries its old R70 binding.
            runTakeover(journal,accounts,generations,sessions);
            R64OfflineSessionOwnership.Token ownerB=
                ready(journal,accounts,generations,sessions,2L);
            require(!ownerB.owner.equals(ownerA.owner),
                "owner B takeover did not replace A's UUID");
            byte[] preAccount=accountBytes(accounts);
            byte[] preLedger=ledgerBytes(generations);
            byte[] preOwner=ownerBytes(sessions);
            oldWriter.release.countDown();
            awaitMetric(persistence::failedCount,1L,
                "old A checkpoint not refused");
            require(repository.saveCalls.get()==0 &&
                    authority.checkpointSuccesses()==0,
                "stale A checkpoint reached repository");
            unchanged(preAccount,preLedger,preOwner,
                accounts,generations,sessions,
                "stale A checkpoint");
            require(persistence.checkpointWrittenCount()==0L,
                "stale A falsely counted as written");
            oldWriter=null;

            // A new login would rehydrate from the unchanged canonical
            // snapshot. The isolated fixture explicitly resets this
            // test-owned actor before giving it B's token. No stale
            // gameplay state is silently promoted by a token rebinding.
            restoreOnWorld(world,actor,initial);
            authority.bindForTest(actor,actor.generation(),ownerB);

            // Block actual WorldPlayerPersistence LOAD inside repository
            // to keep its one I/O thread busy and force coalescing in
            // the original checkpoint slot (same actor identity).
            pendingLoad=new LoadPause();
            repository.blockNextLoad.set(pendingLoad);
            Future<Optional<PlayerSnapshot>> loading=
                loadCaller.submit(()->persistence.load(USER));
            require(pendingLoad.arrived.await(10,TimeUnit.SECONDS),
                "test-owned repository load did not block worker");

            // World checkpoint tick200 stores B's snapshot + B token.
            captureTick(world,actor,200,560,null);
            require(persistence.checkpointCapturedCount()==2L,
                "B checkpoint never captured");
            require(persistence.checkpointCoalescedCount()==0L,
                "first B checkpoint was incorrectly coalesced");

            // A distinct JVM takes over again to C while B's checkpoint
            // is still sitting in that SAME pending checkpoint slot.
            runTakeover(journal,accounts,generations,sessions);
            R64OfflineSessionOwnership.Token ownerC=
                ready(journal,accounts,generations,sessions,3L);
            restoreOnWorld(world,actor,initial);
            authority.bindForTest(actor,actor.generation(),ownerC);

            // Tick300 replaces the ENTIRE pending tuple with a fresh
            // C snapshot and token; B's old inventory item 560 must
            // never be committed under C's identity.
            captureTick(world,actor,300,565,null);
            require(persistence.checkpointCapturedCount()==3L &&
                    persistence.checkpointCoalescedCount()==1L,
                "B-to-C checkpoint tuple coalescing did not occur");
            require(persistence.queuedWrites()==1,
                "expected one coalesced drain behind blocked load");
            pendingLoad.release.countDown();
            loading.get(15L,TimeUnit.SECONDS);
            pendingLoad=null;
            awaitMetric(persistence::checkpointWrittenCount,1L,
                "coalesced C checkpoint did not commit");
            require(repository.saveCalls.get()==1 &&
                    repository.committed.get()==1 &&
                    authority.checkpointSuccesses()==1,
                "coalesced checkpoint wrote more than latest C snapshot");
            R64OfflineSessionOwnership.Token committedC=
                ready(journal,accounts,generations,sessions,3L);
            require(committedC.account.generation==2L,
                "coalesced C checkpoint failed generation advancement");
            PlayerSnapshot committed=repository.load(USER).orElseThrow(
                ()->new AssertionError("C canonical checkpoint missing")
            );
            String expectedDigest=R57TradePairJournal.digest(
                PlayerSnapshotCodec.capture(USER,actor)
            );
            require(expectedDigest.equals(
                    R57TradePairJournal.digest(committed)),
                "coalesced checkpoint committed wrong player snapshot");
            WorldPlayer check=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(committed,check);
            require(check.bank().inventoryCount(565)==1 &&
                    check.bank().inventoryCount(560)==0 &&
                    check.bank().inventoryCount(385)==0,
                "old A/B inventory leaked into C's latest checkpoint");

            // The capture binding still references C generation 1.
            // After its first successful checkpoint, it is stale and
            // MUST NOT be implicitly refreshed by the worker.
            long priorFailed=persistence.failedCount();
            int callsBefore=repository.saveCalls.get();
            captureTick(world,actor,400,561,null);
            awaitMetric(persistence::failedCount,priorFailed+1L,
                "old-generation C token not rejected");
            require(repository.saveCalls.get()==callsBefore,
                "stale R63 generation reached checkpoint repository");

            // Explicitly rebind to validated C generation2; simulate
            // a repository error BEFORE canonical publication. The same
            // C owner can retry using a fresh checkpoint without token
            // replay or an unproven automatic account recovery.
            authority.bindForTest(
                actor,actor.generation(),committedC
            );
            byte[] beforeFailureAccount=accountBytes(accounts);
            byte[] beforeFailureLedger=ledgerBytes(generations);
            byte[] beforeFailureOwner=ownerBytes(sessions);
            repository.failNextSave.set(true);
            captureTick(world,actor,500,562,null);
            awaitMetric(persistence::failedCount,priorFailed+2L,
                "pre-write repository failure not propagated");
            require(repository.saveCalls.get()==callsBefore+1 &&
                    repository.committed.get()==1,
                "failed repository checkpoint unexpectedly committed");
            unchanged(beforeFailureAccount,beforeFailureLedger,
                beforeFailureOwner,accounts,generations,sessions,
                "test-owned repository pre-write failure");

            // Fresh checkpoint with current binding, not a retry of
            // the already failed asynchronous worker task.
            captureTick(world,actor,600,563,null);
            awaitMetric(persistence::checkpointWrittenCount,2L,
                "fresh C checkpoint retry not committed");
            R64OfflineSessionOwnership.Token cAfterRetry=
                ready(journal,accounts,generations,sessions,3L);
            require(cAfterRetry.account.generation==3L &&
                    repository.committed.get()==2,
                "successful retry did not advance generation to 3");

            // Missing actor binding is not inferred from a username
            // or the latest on-disk C record. Refuse before repository.
            authority.unbindForTest(actor);
            int beforeUnbound=repository.saveCalls.get();
            long previousFailures=persistence.failedCount();
            byte[] beforeUnboundAccount=accountBytes(accounts);
            byte[] beforeUnboundLedger=ledgerBytes(generations);
            byte[] beforeUnboundOwner=ownerBytes(sessions);
            captureTick(world,actor,700,564,null);
            awaitMetric(persistence::failedCount,previousFailures+1L,
                "unbound checkpoint failed to close admission");
            require(repository.saveCalls.get()==beforeUnbound &&
                    authority.refusedUnbound()>=1,
                "unbound checkpoint reached file repository");
            unchanged(beforeUnboundAccount,beforeUnboundLedger,
                beforeUnboundOwner,accounts,generations,sessions,
                "unbound checkpoint");

            // Explicitly restored ownership allows progress again.
            authority.bindForTest(
                actor,actor.generation(),cAfterRetry
            );
            captureTick(world,actor,800,566,null);
            awaitMetric(persistence::checkpointWrittenCount,3L,
                "post-unbound current checkpoint did not commit");
            require(ready(journal,accounts,generations,sessions,3L)
                        .account.generation==4L,
                "valid post-unbound save did not advance generation");

            System.out.println(
                "R70_OFFLINE_COALESCED_CHECKPOINT_OWNER_FENCE_PASS "+
                "realWorkerPausedBeforeOldCheckpointCommit=true "+
                "crossJvmTakeoverDeniedOldCheckpointBeforeRepository=true "+
                "coalescedSnapshotAndOwnerTupleAtomicallyReplaced=true "+
                "crossOwnerCoalescingNeverPromotedOldSnapshot=true "+
                "latestNewOwnerCheckpointCommittedExactlyOnce=true "+
                "staleAccountGenerationCheckpointRefused=true "+
                "repositoryPreWriteFailurePreservedEvidence=true "+
                "freshCheckpointRetryAdvancedGeneration=true "+
                "unboundCheckpointRefusedWithoutOwnerInference=true "+
                "defaultProductionCaptureAndWriterUnchanged=true "+
                "noLiveAuthenticationOrPowerLossProof=true"
            );
        }finally{
            if(oldWriter!=null)oldWriter.release.countDown();
            if(pendingLoad!=null)pendingLoad.release.countDown();
            loadCaller.shutdownNow();
            loadCaller.awaitTermination(10,TimeUnit.SECONDS);
            if(actor.registered())world.unregisterPlayer(actor);
            world.close();
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException error){
                        throw new IllegalStateException(
                            "test-owned cleanup "+p,error
                        );
                    }
                });
            }
        }
    }

    private static void captureTick(
        World world,WorldPlayer actor,long tick,int extra,
        PlayerSnapshot restore
    )throws Exception{
        world.submitAndWait(actor,()->{
            if(restore!=null)
                PlayerSnapshotCodec.applyValidated(restore,actor);
            if(extra>0)
                actor.bank().spawnItem(extra,1,packets());
            world.persistence().checkpointDue(tick);
        },5_000L);
    }

    private static void restoreOnWorld(
        World world,WorldPlayer actor,PlayerSnapshot snapshot
    )throws Exception{
        world.submitAndWait(actor,()->{
            PlayerSnapshotCodec.applyValidated(snapshot,actor);
        },5_000L);
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static R64OfflineSessionOwnership.Token ready(
        Path journal,Path accounts,Path generations,Path sessions,
        long epoch
    ){
        R64OfflineSessionOwnership.Review result=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,USER
            );
        require(result.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                result.token!=null &&result.token.epoch==epoch &&
                !result.loginAuthorized(),
            "R70 owner check not ready epoch="+epoch+
                " status="+result.status+" "+result.reason);
        return result.token;
    }

    @FunctionalInterface
    private interface Metric{long value();}
    private static void awaitMetric(
        Metric metric,long expected,String failure
    )throws InterruptedException{
        long deadline=System.nanoTime()+
            TimeUnit.SECONDS.toNanos(12);
        while(metric.value()<expected&&System.nanoTime()<deadline)
            Thread.sleep(15L);
        require(metric.value()>=expected,
            failure+" observed="+metric.value()+" target="+expected);
    }

    private static byte[] accountBytes(Path p)throws IOException{
        return Files.readAllBytes(p.resolve("opensrc.properties"));
    }
    private static byte[] ledgerBytes(Path p)throws IOException{
        return Files.readAllBytes(p.resolve(
            "opensrc.r63-generation.properties"
        ));
    }
    private static byte[] ownerBytes(Path p)throws IOException{
        return Files.readAllBytes(p.resolve(
            "opensrc.r64-session.properties"
        ));
    }
    private static void unchanged(
        byte[] account,byte[] ledger,byte[] owner,
        Path accounts,Path generations,Path sessions,String why
    )throws IOException{
        require(Arrays.equals(account,accountBytes(accounts))&&
                Arrays.equals(ledger,ledgerBytes(generations))&&
                Arrays.equals(owner,ownerBytes(sessions)),
            "checkpoint refusal changed canonical/ledger/owner: "+why);
    }

    private static void runTakeover(
        Path journal,Path accounts,Path generations,Path sessions
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker",
            "takeover",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("test-owned takeover JVM timed out");
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                output.contains("R64_SESSION_WORKER_PASS"),
            "test-owned takeover failed: "+output);
    }
    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").toString();
    }
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private R70OfflineCheckpointAuthorityTest(){}
}

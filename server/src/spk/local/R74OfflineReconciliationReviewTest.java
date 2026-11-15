package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R74 OFFLINE verification of one actual R73->R72->R71 committed final
 * save. Real persistence I/O publishes R63 account and R64 RELEASED,
 * but read-only R74 never retires the durable blocker or permits login.
 *
 * Separate clean JVM reads every tested result; byte snapshots prove
 * the inspector did not change any account/evidence file.
 */
public final class R74OfflineReconciliationReviewTest {
    private static final String USER="opensrc";

    private static final class CountingRepository implements PlayerRepository {
        final FilePlayerRepository disk;
        final boolean blockWrite;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        final AtomicInteger saves=new AtomicInteger();
        final AtomicInteger commits=new AtomicInteger();

        CountingRepository(Path accounts,boolean block){
            disk=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
            blockWrite=block;
            if(!block)release.countDown();
        }
        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            return disk.load(username);
        }
        @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
            saves.incrementAndGet();
            entered.countDown();
            while(release.getCount()!=0L){
                try{
                    release.await(25L,TimeUnit.MILLISECONDS);
                }catch(InterruptedException ignored){
                    // R72's test-owned uninterruptible write simulation.
                }
            }
            disk.save(snapshot);
            commits.incrementAndGet();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Path root,journal,accounts,generations,sessions,evidence;
        final CountingRepository repo;
        final World world;
        final WorldPlayer actor=new WorldPlayer();
        final WorldPlayerPersistence persistence;
        final R71OfflineFinalSaveCheckpointFence finalGate;
        final R72OfflineInFlightCommitEvidence recorder;
        final R73OfflinePendingWriteAdmission admission;
        final R74OfflineReconciliationReview reviewer;
        final R64OfflineSessionOwnership.Token initialOwner;
        UUID id;
        boolean closed;

        Fixture(boolean blocked)throws Exception{
            root=Files.createTempDirectory("spk-r74-reconcile-");
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            evidence=root.resolve("evidence");
            for(Path p:new Path[]{
                journal,accounts,generations,sessions,evidence
            })Files.createDirectory(p);
            repo=new CountingRepository(accounts,blocked);
            world=World.isolatedForTest(60_000L,repo);
            persistence=world.persistence();
            R60TradePairCoordinator.initializeEmpty(journal);
            actor.bank().spawnItem(995,100,packets());
            PlayerSnapshot initial=PlayerSnapshotCodec.capture(USER,actor);
            repo.disk.save(initial);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            initialOwner=R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER
            );
            finalGate=new R71OfflineFinalSaveCheckpointFence(
                journal,accounts,generations,sessions
            );
            recorder=new R72OfflineInFlightCommitEvidence(
                evidence,finalGate
            );
            admission=new R73OfflinePendingWriteAdmission(
                journal,accounts,generations,sessions,evidence
            );
            reviewer=new R74OfflineReconciliationReview(
                journal,accounts,generations,sessions,evidence
            );
            persistence.installOfflineIoWriteAdmissionForTest(recorder);
            persistence.installOfflineCheckpointOwnerCaptureForTest(
                finalGate
            );
            world.registerPlayer(actor,USER);
            world.start();
            finalGate.bindActorForTest(
                actor,actor.generation(),initialOwner
            );
        }

        WorldPlayerPersistence.CapturedSave captureFinal()
                throws Exception{
            AtomicReference<WorldPlayerPersistence.CapturedSave> out=
                new AtomicReference<>();
            world.submitAndWait(actor,()->{
                actor.bank().spawnItem(560,1,packets());
                WorldPlayerPersistence.CapturedSave captured=
                    persistence.captureDeferredFinalSave(
                        USER,actor,actor.generation(),0,
                        "[r74-offline] ","EXACT_RECONCILIATION"
                    );
                try{
                    id=admission.registerFinalBeforeQueueForTest(
                        captured,initialOwner,recorder
                    );
                }catch(IOException error){
                    throw new IllegalStateException(
                        "test-owned final intent refused",error
                    );
                }
                out.set(captured);
            },5_000L);
            require(out.get()!=null&&id!=null,
                "World actor did not register R73/R72 intent");
            return out.get();
        }

        Path ownerPath(){
            return sessions.resolve(
                "opensrc.r64-session.properties"
            );
        }
        Path generationPath(){
            return generations.resolve(
                "opensrc.r63-generation.properties"
            );
        }
        Path r73Path(){
            return evidence.resolve(
                "r73-opensrc.intent.properties"
            );
        }
        Path r72Intent(){
            return evidence.resolve(
                "r72-"+id+"-INTENT.properties"
            );
        }
        Path r72Ack(){
            return evidence.resolve(
                "r72-"+id+"-ACK_OBSERVED.properties"
            );
        }

        void assertStatus(
            R74OfflineReconciliationReview.Status expected
        )throws Exception{
            Map<String,byte[]> before=allBytes(root);
            R74OfflineReconciliationReview.Review review=
                reviewer.inspect(USER);
            require(review.status==expected,
                "wrong R74 inspection expected="+expected+
                " actual="+review.status+" reason="+review.reason);
            require(!review.loginAuthorized()&&
                    !review.replayAuthorized()&&
                    !review.recoveryAuthorized()&&
                    !review.writeIntentRetired()&&
                    !review.quarantineReleased()&&
                    !review.evidenceModified(),
                "R74 returned any forbidden authorization");
            independentInspector(this,expected);
            Map<String,byte[]> after=allBytes(root);
            require(sameBytes(before,after),
                "read-only inspector modified filesystem evidence");
        }

        @Override public void close()throws Exception{
            if(closed)return;
            closed=true;
            repo.release.countDown();
            if(actor.registered())world.unregisterPlayer(actor);
            world.close();
            deleteOwned(root);
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==7&&"inspect".equals(args[0])){
            R74OfflineReconciliationReview check=
                new R74OfflineReconciliationReview(
                    Paths.get(args[1]),Paths.get(args[2]),
                    Paths.get(args[3]),Paths.get(args[4]),
                    Paths.get(args[5])
                );
            R74OfflineReconciliationReview.Review found=
                check.inspect(args[6].split(":",2)[0]);
            String expected=args[6].split(":",2)[1];
            require(expected.equals(found.status.name()),
                "separate JVM review expected="+expected+
                " actual="+found.status+" reason="+found.reason);
            require(!found.loginAuthorized()&&
                    !found.recoveryAuthorized()&&
                    !found.replayAuthorized()&&
                    !found.writeIntentRetired()&&
                    !found.quarantineReleased()&&
                    !found.evidenceModified(),
                "fresh JVM incorrectly granted authority");
            System.out.println(
                "R74_FRESH_JVM_REVIEW_PASS status="+found.status+
                " loginAuthorized=false evidenceModified=false"
            );
            return;
        }
        require(args.length==0,"unrecognized R74 args");
        completeAckAndNegativeEvidence();
        cancelledLateAckRemainsQuarantined();
        System.out.println(
            "R74_OFFLINE_READONLY_RECONCILIATION_PASS "+
            "realR73R72R71R63R64CompleteAcknowledgementCompared=true "+
            "allReviewStatusesRefuseLoginReplayRetirement=true "+
            "freshJvmReadingVerifiedWithoutFileChanges=true "+
            "missingAckAndMissingBlockerRemainDenied=true "+
            "generationAndOwnerMismatchesDetected=true "+
            "malformedAndDuplicateIntentDetected=true "+
            "atomicStageEvidenceRefusedWithoutCleanup=true "+
            "pendingR59TradeCannotBeReconciled=true "+
            "actualCancelledLateWriteAckRequiresManualReview=true "+
            "noLiveIntegrationOrAutomaticRecovery=true"
        );
    }

    private static void completeAckAndNegativeEvidence()
            throws Exception{
        try(Fixture f=new Fixture(false)){
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_MISSING_WRITE_INTENT
            );
            WorldPlayerPersistence.CapturedSave captured=f.captureFinal();
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_UNACKNOWLEDGED_WRITE
            );
            WorldPlayerPersistence.SaveTicket ticket=
                f.persistence.submitCapturedWithBackpressure(
                    captured,5_000L
                );
            ticket.completion.get(15L,TimeUnit.SECONDS);
            require(f.repo.commits.get()==1 &&
                    f.recorder.inspect(f.evidence,f.id).status==
                        R72OfflineInFlightCommitEvidence.ReviewStatus
                            .BLOCK_COMPLETION_NOT_AUTHORIZED,
                "R72 ACK did not follow successful actual worker commit");
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_CONSISTENT_BUT_QUARANTINED
            );
            require(Files.exists(f.r73Path()),
                "reconciler unexpectedly retired R73 blocker");
            R73OfflinePendingWriteAdmission.Review preflight=
                f.admission.inspect(USER);
            require(preflight.status==
                    R73OfflinePendingWriteAdmission.Status
                        .BLOCK_UNRESOLVED_WRITE,
                "non-authorizing R74 review bypassed R73 takeover blocker");

            // Capture original raw evidence, and intentionally modify
            // only disposable TEST-owned fixture files to prove negative
            // outcomes. Always restore before the next scenario.
            byte[] originalAck=Files.readAllBytes(f.r72Ack());
            byte[] originalR73=Files.readAllBytes(f.r73Path());
            byte[] originalR72=Files.readAllBytes(f.r72Intent());
            byte[] originalLedger=Files.readAllBytes(f.generationPath());
            byte[] originalOwner=Files.readAllBytes(f.ownerPath());

            Files.delete(f.r72Ack());
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_UNACKNOWLEDGED_WRITE
            );
            Files.write(f.r72Ack(),originalAck);

            Files.delete(f.r73Path());
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_MISSING_WRITE_INTENT
            );
            Files.write(f.r73Path(),originalR73);

            Properties ledger=load(f.generationPath());
            ledger.setProperty("generation","5");
            AtomicAccountPublisher.publish(
                f.generationPath(),ledger,"R74 test-only wrong generation"
            );
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_EVIDENCE_MISMATCH
            );
            Files.write(f.generationPath(),originalLedger);

            Properties owner=load(f.ownerPath());
            owner.setProperty("phase","ACTIVE");
            AtomicAccountPublisher.publish(
                f.ownerPath(),owner,"R74 test-only active owner"
            );
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_OWNER_NOT_RELEASED
            );
            Files.write(f.ownerPath(),originalOwner);

            owner=load(f.ownerPath());
            owner.setProperty("owner.id",UUID.randomUUID().toString());
            AtomicAccountPublisher.publish(
                f.ownerPath(),owner,"R74 test-only owner mismatch"
            );
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_EVIDENCE_MISMATCH
            );
            Files.write(f.ownerPath(),originalOwner);

            Properties corruptedR72=load(f.r72Intent());
            corruptedR72.setProperty(
                "afterDigest",digestOfZeroes()
            );
            AtomicAccountPublisher.publish(
                f.r72Intent(),corruptedR72,
                "R74 test-only mismatched after hash"
            );
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_AMBIGUOUS_EVIDENCE
            );
            Files.write(f.r72Intent(),originalR72);

            UUID duplicateId=UUID.randomUUID();
            Path duplicate=f.evidence.resolve(
                "r72-"+duplicateId+"-INTENT.properties"
            );
            Properties duplicated=load(f.r72Intent());
            duplicated.setProperty("id",duplicateId.toString());
            AtomicAccountPublisher.publish(
                duplicate,duplicated,"R74 test-only duplicate ID"
            );
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_AMBIGUOUS_EVIDENCE
            );
            Files.delete(duplicate);

            Path stage=f.evidence.resolve(
                ".r73-opensrc.intent.properties.r16-r74-test.stage"
            );
            Files.writeString(stage,"R74-synthetic-staged-evidence");
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_LOCK_OR_PATH_INDETERMINATE
            );
            require(Files.exists(stage),
                "read-only reconciliation deleted ambiguous stage");
            Files.delete(stage);

            // After each negative control, the same unchanged fully
            // completed state still only permits non-authorizing review.
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_CONSISTENT_BUT_QUARANTINED
            );

            // R59 indexed paired trade created AFTER the saved final
            // is independently visible to the account check, which
            // must block even a previously consistent ACK.
            PlayerSnapshot before=f.repo.disk.load(USER).orElseThrow(
                ()->new IOException("missing canonical")
            );
            WorldPlayer changed=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(before,changed);
            changed.bank().spawnItem(565,1,packets());
            PlayerSnapshot after=PlayerSnapshotCodec.capture(
                USER,changed
            );
            PlayerSnapshot peer=sample("peer",385);
            WorldPlayer other=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(peer,other);
            other.bank().spawnItem(560,1,packets());
            PlayerSnapshot peerAfter=PlayerSnapshotCodec.capture(
                "peer",other
            );
            R60TradePairCoordinator.registerPrepared(
                f.journal,1L,Collections.emptyList(),UUID.randomUUID(),
                before,after,peer,peerAfter
            );
            f.assertStatus(
                R74OfflineReconciliationReview.Status
                    .BLOCK_PENDING_PAIR
            );
        }
    }

    private static void cancelledLateAckRemainsQuarantined()
            throws Exception{
        try(Fixture f=new Fixture(true)){
            WorldPlayerPersistence.CapturedSave saved=f.captureFinal();
            WorldPlayerPersistence.SaveTicket ticket=
                f.persistence.submitCapturedWithBackpressure(
                    saved,5_000L
                );
            try{
                require(f.repo.entered.await(10L,TimeUnit.SECONDS),
                    "R74 worker did not block in real repository");
                // The real worker owns R60 while executing the
                // uninterruptible repository write. A read-only
                // reconciler MUST fail closed on lock contention,
                // never bypass the lock to report an optimistic ACK.
                f.assertStatus(
                    R74OfflineReconciliationReview.Status
                        .BLOCK_LOCK_OR_PATH_INDETERMINATE
                );
                f.persistence.close();
                require(ticket.completion.isCompletedExceptionally(),
                    "forced close did not reject in-flight save");
                f.assertStatus(
                    R74OfflineReconciliationReview.Status
                        .BLOCK_LOCK_OR_PATH_INDETERMINATE
                );
                f.repo.release.countDown();
                awaitPhase(
                    f.evidence,f.id,
                    R72OfflineInFlightCommitEvidence.ReviewStatus
                        .BLOCK_LATE_COMMIT_OBSERVED
                );
                require(f.repo.commits.get()==1,
                    "late writer did not publish after forced close");
                f.assertStatus(
                    R74OfflineReconciliationReview.Status
                        .BLOCK_CANCELLED_LATE_ACK_QUARANTINED
                );
                require(f.admission.inspect(USER).status==
                        R73OfflinePendingWriteAdmission.Status
                            .BLOCK_UNRESOLVED_WRITE,
                    "R74 late-ACK review unexpectedly unblocked takeover");
            }finally{
                f.repo.release.countDown();
            }
        }
    }

    private static void independentInspector(
        Fixture f,R74OfflineReconciliationReview.Status status
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R74OfflineReconciliationReviewTest",
            "inspect",f.journal.toString(),f.accounts.toString(),
            f.generations.toString(),f.sessions.toString(),
            f.evidence.toString(),USER+":"+status.name()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("R74 fresh JVM inspection timed out");
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                output.contains("R74_FRESH_JVM_REVIEW_PASS"),
            "fresh JVM reconciliation differs: "+output);
    }

    private static void awaitPhase(
        Path evidence,UUID id,
        R72OfflineInFlightCommitEvidence.ReviewStatus expected
    )throws InterruptedException{
        long deadline=System.nanoTime()+
            TimeUnit.SECONDS.toNanos(15L);
        while(System.nanoTime()<deadline){
            if(R72OfflineInFlightCommitEvidence.inspect(
                    evidence,id).status==expected)return;
            Thread.sleep(20L);
        }
        throw new AssertionError("R74 ACK not observed before deadline");
    }

    private static PlayerSnapshot sample(
        String user,int item
    )throws IOException{
        WorldPlayer p=new WorldPlayer();
        p.bank().spawnItem(item,1,packets());
        return PlayerSnapshotCodec.capture(user,p);
    }

    private static Properties load(Path file)throws IOException{
        Properties props=new Properties();
        try(InputStream in=Files.newInputStream(file)){
            props.load(in);
        }
        return props;
    }

    private static String digestOfZeroes(){
        char[] chars=new char[64];
        Arrays.fill(chars,'0');
        return new String(chars);
    }

    private static Map<String,byte[]> allBytes(Path root)
            throws IOException{
        Map<String,byte[]> values=new HashMap<>();
        try(java.util.stream.Stream<Path> entries=Files.walk(root)){
            entries.filter(Files::isRegularFile).filter(file->{
                // R60's OS lock file is a synchronization primitive,
                // not an account/evidence artifact. Windows correctly
                // refuses reading it while another JVM owns the lock.
                // Compare only the five test-owned durable roots.
                Path relative=root.relativize(file);
                if(relative.getNameCount()<2)return false;
                String part=relative.getName(0).toString();
                return "journal".equals(part)||
                    "accounts".equals(part)||
                    "generations".equals(part)||
                    "sessions".equals(part)||
                    "evidence".equals(part);
            }).forEach(file->{
                try{
                    values.put(root.relativize(file).toString(),
                        Files.readAllBytes(file));
                }catch(IOException err){
                    throw new IllegalStateException(err);
                }
            });
        }
        return values;
    }
    private static boolean sameBytes(
        Map<String,byte[]> before,Map<String,byte[]> after
    ){
        if(!before.keySet().equals(after.keySet()))return false;
        for(String name:before.keySet())
            if(!Arrays.equals(before.get(name),after.get(name)))
                return false;
        return true;
    }
    private static void deleteOwned(Path root)throws Exception{
        try(java.util.stream.Stream<Path> entries=Files.walk(root)){
            entries.sorted(Comparator.reverseOrder()).forEach(path->{
                try{Files.deleteIfExists(path);}
                catch(IOException failure){
                    throw new IllegalStateException(
                        "R74 fixture cleanup "+path,failure
                    );
                }
            });
        }
    }
    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }
    private static String javaExe(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").toString();
    }
    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private R74OfflineReconciliationReviewTest(){}
}

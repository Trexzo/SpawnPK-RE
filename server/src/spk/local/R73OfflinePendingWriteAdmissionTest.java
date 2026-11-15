package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R73: test-only R64 owner transfer vs durable R73/R72 write evidence.
 * Registration and owner replacement serialize under the SAME R60 lock.
 * No live LocalSession, real accounts or production writers are modified.
 */
public final class R73OfflinePendingWriteAdmissionTest {
    private static final String USER="opensrc";

    private static final class BlockedRepository
            implements PlayerRepository {
        final FilePlayerRepository actual;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        final AtomicInteger calls=new AtomicInteger();
        final AtomicInteger committed=new AtomicInteger();
        final AtomicInteger interrupted=new AtomicInteger();
        BlockedRepository(Path root){
            actual=new FilePlayerRepository(
                user->root.resolve(user+".properties")
            );
        }
        @Override public java.util.Optional<PlayerSnapshot> load(String user)
                throws IOException{
            return actual.load(user);
        }
        @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
            calls.incrementAndGet();
            entered.countDown();
            while(release.getCount()!=0){
                try{
                    release.await(20L,TimeUnit.MILLISECONDS);
                }catch(InterruptedException ignored){
                    interrupted.incrementAndGet();
                }
            }
            actual.save(snapshot);
            committed.incrementAndGet();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Path root,journal,accounts,generations,sessions,evidence;
        final World world;
        final WorldPlayer actor=new WorldPlayer();
        final BlockedRepository repository;
        final WorldPlayerPersistence persistence;
        final R71OfflineFinalSaveCheckpointFence finalFence;
        final R72OfflineInFlightCommitEvidence recorder;
        final R73OfflinePendingWriteAdmission admission;
        final R64OfflineSessionOwnership.Token first,peer;
        boolean closed;

        Fixture()throws Exception{
            root=Files.createTempDirectory(
                "spk-r73-write-admission-"
            );
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            evidence=root.resolve("evidence");
            for(Path path:new Path[]{
                journal,accounts,generations,sessions,evidence
            })Files.createDirectory(path);
            repository=new BlockedRepository(accounts);
            world=World.isolatedForTest(60_000L,repository);
            persistence=world.persistence();
            R60TradePairCoordinator.initializeEmpty(journal);
            actor.bank().spawnItem(995,100,packets());
            PlayerSnapshot initial=
                PlayerSnapshotCodec.capture(USER,actor);
            PlayerSnapshot other=sample("peer",385);
            FilePlayerRepository disk=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
            disk.save(initial);
            disk.save(other);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"peer"
            );
            first=R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER
            );
            peer=R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,"peer"
            );
            finalFence=new R71OfflineFinalSaveCheckpointFence(
                journal,accounts,generations,sessions
            );
            recorder=new R72OfflineInFlightCommitEvidence(
                evidence,finalFence
            );
            admission=new R73OfflinePendingWriteAdmission(
                journal,accounts,generations,sessions,evidence
            );
            persistence.installOfflineIoWriteAdmissionForTest(recorder);
            persistence.installOfflineCheckpointOwnerCaptureForTest(
                finalFence
            );
            world.registerPlayer(actor,USER);
            world.start();
            finalFence.bindActorForTest(
                actor,actor.generation(),first
            );
        }

        static final class Registered {
            final UUID id;
            final WorldPlayerPersistence.CapturedSave captured;
            Registered(UUID id,WorldPlayerPersistence.CapturedSave saved){
                this.id=id;captured=saved;
            }
        }

        Registered registerFinal()throws Exception{
            AtomicReference<Registered> result=new AtomicReference<>();
            world.submitAndWait(actor,()->{
                actor.bank().spawnItem(560,1,packets());
                WorldPlayerPersistence.CapturedSave captured=
                    persistence.captureDeferredFinalSave(
                        USER,actor,actor.generation(),0,
                        "[r73-offline] ","FINAL_WRITE_INTENT_FENCE"
                    );
                try{
                    UUID id=admission.registerFinalBeforeQueueForTest(
                        captured,first,recorder
                    );
                    result.set(new Registered(id,captured));
                }catch(IOException error){
                    throw new IllegalStateException(
                        "test-owned intent registration denied",error
                    );
                }
            },5_000L);
            require(result.get()!=null,
                "R73 test-owned final not registered");
            return result.get();
        }

        byte[][] evidenceBytes()throws Exception{
            return new byte[][]{
                Files.readAllBytes(accounts.resolve(
                    "opensrc.properties"
                )),
                Files.readAllBytes(generations.resolve(
                    "opensrc.r63-generation.properties"
                )),
                Files.readAllBytes(sessions.resolve(
                    "opensrc.r64-session.properties"
                ))
            };
        }
        void unchanged(byte[][] before,String why)throws Exception{
            byte[][] current=evidenceBytes();
            for(int i=0;i<before.length;i++)
                require(Arrays.equals(before[i],current[i]),
                    why+" modified account/ledger/owner index="+i);
        }

        @Override public void close()throws Exception{
            if(closed)return;
            closed=true;
            repository.release.countDown();
            if(actor.registered())
                world.unregisterPlayer(actor);
            world.close();
            deleteOwned(root);
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==14&&"attempt".equals(args[0])){
            takeoverWorker(args);
            return;
        }
        if(args.length!=0)
            throw new IllegalArgumentException("R73 test worker arguments");
        guardedRegistrationAndLateCommit();
        hardStoppedLegacyEvidenceAlsoBlocksTakeover();
        System.out.println(
            "R73_OFFLINE_UNRESOLVED_WRITE_TAKEOVER_FENCE_PASS "+
            "writeIntentRegisteredUnderRealR60LockBeforeQueue=true "+
            "independentJvmTransferBlockedBeforeWrite=true "+
            "independentAccountTransferAllowed=true "+
            "activeWorkerAndCancelledFutureNoTakeover=true "+
            "lateAckStillQuarantinedAcrossFreshJvms=true "+
            "hardStoppedR72OnlyLegacyEvidenceBlocks=true "+
            "ambiguousR72AndR73StageFailClosed=true "+
            "noTimeOrProcessLivenessCleanup=true "+
            "noLoginRecoveryOrLiveIntegration=true"
        );
    }

    private static void guardedRegistrationAndLateCommit()
            throws Exception{
        try(Fixture f=new Fixture()){
            require(f.admission.inspect(USER).status==
                    R73OfflinePendingWriteAdmission.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS,
                "new account unexpectedly blocked before first intent");

            Fixture.Registered saved=f.registerFinal();
            Path blocker=f.evidence.resolve(
                "r73-opensrc.intent.properties"
            );
            require(Files.isRegularFile(blocker),
                "durable R73 pre-I/O account blocker was not created");
            require(f.admission.inspect(USER).status==
                    R73OfflinePendingWriteAdmission.Status
                        .BLOCK_UNRESOLVED_WRITE,
                "fresh R73 reader ignored registered account blocker");

            byte[] originalBlocker=Files.readAllBytes(blocker);
            byte[][] before=f.evidenceBytes();
            worker(f, f.first,"R73_UNRESOLVED_WRITE_DENIED");
            f.unchanged(before,"premature takeover");
            require(Arrays.equals(
                    originalBlocker,Files.readAllBytes(blocker)),
                "denied takeover altered immutable blocker");

            // An unrelated account may replace its owner, even when
            // this user's R72 evidence exists in the shared root.
            require(f.admission.inspect("peer").status==
                    R73OfflinePendingWriteAdmission.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS,
                "unrelated account was incorrectly quarantined");
            worker(f,f.peer,"SUCCESS");
            worker(f,f.peer,"R64_SESSION_DENIED stale owner");

            // Re-registering this account MUST NOT rewrite either R73
            // intent or R72 snapshot evidence. A failed duplicate
            // registration remains conservatively blocked.
            try{
                f.admission.registerFinalBeforeQueueForTest(
                    saved.captured,f.first,f.recorder
                );
                throw new AssertionError(
                    "duplicate unresolved account intent was permitted"
                );
            }catch(IOException denied){
                require(denied.getMessage().contains(
                    "R73_UNRESOLVED_WRITE_DENIED"),
                    "wrong duplicate registration refusal: "+denied);
            }
            require(Arrays.equals(
                    originalBlocker,Files.readAllBytes(blocker)),
                "duplicate registration rewrote immutable intent");

            // Actual I/O worker enters an uninterruptible test-owned
            // FilePlayerRepository, retaining the real R60 OS lock.
            WorldPlayerPersistence.SaveTicket ticket=
                f.persistence.submitCapturedWithBackpressure(
                    saved.captured,5_000L
                );
            try{
                require(f.repository.entered.await(10L,TimeUnit.SECONDS),
                    "actual persistence worker did not reach repository");
                require(f.repository.committed.get()==0,
                    "worker published before shutdown race");
                // While the real worker holds R60, a new JVM's
                // transfer is denied at the OS lock itself. A canceled
                // Java SaveTicket cannot release this lock.
                worker(f,f.first,"R60_LOCK_BUSY_OR_OVERLAPPING");
                f.persistence.close();
                require(ticket.completion.isCompletedExceptionally()&&
                        f.repository.committed.get()==0,
                    "forced close did not cancel the in-flight SaveTicket");
                f.unchanged(before,"cancelled before worker resume");
                worker(f,f.first,"R60_LOCK_BUSY_OR_OVERLAPPING");

                f.repository.release.countDown();
                awaitR72(f.evidence,saved.id,
                    R72OfflineInFlightCommitEvidence.ReviewStatus
                        .BLOCK_LATE_COMMIT_OBSERVED
                );
                require(f.repository.committed.get()==1,
                    "uninterruptible late commit did not occur");
                require(f.admission.inspect(USER).status==
                        R73OfflinePendingWriteAdmission.Status
                            .BLOCK_UNRESOLVED_WRITE,
                    "late ACK incorrectly retired durable intent");
                worker(f,f.first,"R73_UNRESOLVED_WRITE_DENIED");
                // Released R64 token + successful account write do
                // NOT authorize retirement of the R73 intent.
                require(Files.isRegularFile(blocker)&&
                        Arrays.equals(
                            originalBlocker,Files.readAllBytes(blocker)),
                    "R73 blocker was changed/retired after late ACK");
            }finally{
                f.repository.release.countDown();
            }

            // A crash-ambiguous R16 staging entry in another account's
            // marker path must fail closed. Never inspect stage contents,
            // guess its age or clean it as part of takeover.
            Path ambiguous=f.evidence.resolve(
                ".r73-peer.intent.properties.r16-crashed.stage"
            );
            Files.writeString(ambiguous,"test-owned-ambiguous-stage");
            R64OfflineSessionOwnership.Review peerReview=
                R64OfflineSessionOwnership.inspect(
                    f.journal,f.accounts,f.generations,f.sessions,"peer"
                );
            require(peerReview.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS,
                "peer was not current before staging negative control");
            worker(f,peerReview.token,
                "R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED");
            require(Files.exists(ambiguous),
                "R73 unexpectedly deleted ambiguous stage");
            // Explicit cleanup of THIS TEST's synthetic negative
            // control only; the production gate never cleans stages.
            Files.delete(ambiguous);
            // A malformed R72-shaped entry with no reliable account
            // cannot be assigned to a user; block transfer globally.
            Path malformed=f.evidence.resolve(
                "r72-NOT-A-VALID-PHASE.properties"
            );
            Files.writeString(malformed,"test-owned-bad-R72-shape");
            worker(f,peerReview.token,
                "R73_UNRESOLVED_WRITE_DENIED ambiguous R72 entry");
            require(Files.exists(malformed),
                "malformed evidence was silently cleaned");
        }
    }

    private static void hardStoppedLegacyEvidenceAlsoBlocksTakeover()
            throws Exception{
        Path root=Files.createTempDirectory(
            "spk-r73-legacy-hard-stop-"
        );
        try{
            Path childRoot=root.resolve("child");
            Files.createDirectory(childRoot);
            Process proc=new ProcessBuilder(
                javaExe(),"-cp",System.getProperty("java.class.path"),
                "spk.local.R72OfflineInFlightCommitEvidenceTest",
                "hardstop",childRoot.toString()
            ).redirectErrorStream(true).start();
            awaitProcess(proc,93,"",30L);
            Path journal=childRoot.resolve("journal");
            Path accounts=childRoot.resolve("accounts");
            Path generations=childRoot.resolve("generations");
            Path sessions=childRoot.resolve("sessions");
            Path evidence=childRoot.resolve("r72-evidence");
            R64OfflineSessionOwnership.Review original=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER
                );
            require(original.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS,
                "hard-stop child did not retain original R64 owner");
            R73OfflinePendingWriteAdmission gate=
                new R73OfflinePendingWriteAdmission(
                    journal,accounts,generations,sessions,evidence
                );
            require(!Files.exists(evidence.resolve(
                    "r73-opensrc.intent.properties"
                )),
                "legacy-only R72 fixture unexpectedly has R73 blocker");
            require(gate.inspect(USER).status==
                    R73OfflinePendingWriteAdmission.Status
                        .BLOCK_UNRESOLVED_WRITE,
                "R72-only hard-stop evidence was not detected");
            worker(journal,accounts,generations,sessions,evidence,
                original.token,"R73_UNRESOLVED_WRITE_DENIED");
        }finally{
            deleteOwned(root);
        }
    }

    private static void worker(
        Fixture f,R64OfflineSessionOwnership.Token owner,String outcome
    )throws Exception{
        worker(f.journal,f.accounts,f.generations,f.sessions,
            f.evidence,owner,outcome);
    }

    private static void worker(
        Path journal,Path accounts,Path generations,Path sessions,
        Path evidence,R64OfflineSessionOwnership.Token owner,
        String outcome
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R73OfflinePendingWriteAdmissionTest",
            "attempt",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString(),
            evidence.toString(),owner.username,owner.owner.toString(),
            Long.toString(owner.epoch),owner.phase.name(),
            Long.toString(owner.account.generation),
            Long.toString(owner.account.journalRevision),
            owner.account.snapshotDigest,outcome
        ).redirectErrorStream(true).start();
        awaitProcess(child,0,"R73_TAKEOVER_WORKER_PASS",20L);
    }

    private static void takeoverWorker(String[] args)
            throws Exception{
        // args includes command, five roots, user, UUID, epoch,
        // phase, account generation, revision, digest and outcome.
        R64OfflineSessionOwnership.Token current=
            new R64OfflineSessionOwnership.Token(
                args[6],UUID.fromString(args[7]),
                Long.parseLong(args[8]),
                R64OfflineSessionOwnership.Phase.valueOf(args[9]),
                new R63AccountSaveAdmission.Token(
                    args[6],Long.parseLong(args[10]),
                    Long.parseLong(args[11]),args[12]
                )
            );
        R73OfflinePendingWriteAdmission gate=
            new R73OfflinePendingWriteAdmission(
                Paths.get(args[1]),Paths.get(args[2]),
                Paths.get(args[3]),Paths.get(args[4]),
                Paths.get(args[5])
            );
        String expected=args[13];
        try{
            R64OfflineSessionOwnership.Token next=
                gate.replaceOwnerIfClear(current);
            require("SUCCESS".equals(expected)&&
                    next.epoch==current.epoch+1L &&
                    !next.owner.equals(current.owner),
                "unexpected successful owner transfer expected="+expected);
        }catch(IOException denied){
            require(!"SUCCESS".equals(expected) &&
                    denied.getMessage().contains(expected),
                "incorrect takeover refusal="+denied+
                " expected="+expected);
        }
        System.out.println(
            "R73_TAKEOVER_WORKER_PASS expected="+expected+
            " loginAuthorized=false"
        );
    }

    private static void awaitR72(
        Path evidence,UUID id,
        R72OfflineInFlightCommitEvidence.ReviewStatus status
    )throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(System.nanoTime()<deadline){
            if(R72OfflineInFlightCommitEvidence.inspect(
                    evidence,id).status==status)return;
            Thread.sleep(20L);
        }
        throw new AssertionError(
            "expected R72 late ACK missing: "+
            R72OfflineInFlightCommitEvidence.inspect(
                evidence,id).status
        );
    }

    private static void awaitProcess(
        Process p,int expectedCode,String marker,long seconds
    )throws Exception{
        if(!p.waitFor(seconds,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("R73 isolated child timed out");
        }
        String output=new String(
            p.getInputStream().readAllBytes(),StandardCharsets.UTF_8
        );
        require(p.exitValue()==expectedCode&&
                output.contains(marker),
            "R73 child failed exit="+p.exitValue()+" output="+output);
    }

    private static PlayerSnapshot sample(
        String user,int item)throws IOException{
        WorldPlayer p=new WorldPlayer();
        p.bank().spawnItem(item,1,packets());
        return PlayerSnapshotCodec.capture(user,p);
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
    private static void deleteOwned(Path root)throws Exception{
        try(java.util.stream.Stream<Path> paths=Files.walk(root)){
            paths.sorted(Comparator.reverseOrder()).forEach(p->{
                try{Files.deleteIfExists(p);}
                catch(IOException failure){
                    throw new IllegalStateException(
                        "test-owned cleanup "+p,failure
                    );
                }
            });
        }
    }
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private R73OfflinePendingWriteAdmissionTest(){}
}

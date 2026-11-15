package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R69: actual WorldPlayerPersistence worker writes one captured snapshot.
 * Pause the I/O thread before commit, transfer R64 account ownership in
 * another real JVM, then resume the old save. It must be rejected before
 * the counting REAL FilePlayerRepository is called.
 *
 * A current owner may save through the same worker; its R63 generation and
 * R64 owner record advance under one cross-process OS lock. Checkpoints
 * and unbound manual tasks are conservatively denied in opt-in test mode.
 */
public final class R69OfflineIoCommitGateTest {
    private static final String USER="opensrc";

    private static final class CountingFileRepository
            implements PlayerRepository {
        private final FilePlayerRepository delegate;
        final AtomicInteger calls=new AtomicInteger();
        final AtomicInteger committed=new AtomicInteger();
        final AtomicBoolean failNext=new AtomicBoolean();

        CountingFileRepository(Path accounts){
            delegate=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
        }

        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            return delegate.load(username);
        }

        @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
            calls.incrementAndGet();
            if(failNext.compareAndSet(true,false))
                throw new IOException(
                    "R69_TEST_REPOSITORY_INJECTED_BEFORE_WRITE"
                );
            delegate.save(snapshot);
            committed.incrementAndGet();
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spk-r69-io-commit-");
        Path journal=root.resolve("journal");
        Path accounts=root.resolve("accounts");
        Path generations=root.resolve("generations");
        Path sessions=root.resolve("sessions");
        Files.createDirectory(journal);
        Files.createDirectory(accounts);
        Files.createDirectory(generations);
        Files.createDirectory(sessions);

        CountingFileRepository repository=
            new CountingFileRepository(accounts);
        // This World uses the real dedicated persistence I/O executor,
        // but its repository is a counting wrapper around a test-owned
        // FilePlayerRepository directory. No live account root is used.
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer sessionA=new WorldPlayer();
        WorldPlayer sessionB=null;
        try{
            R60TradePairCoordinator.initializeEmpty(journal);
            ServerPacketWriter packets=packets();
            sessionA.bank().spawnItem(995,100,packets);
            PlayerSnapshot firstSnapshot=PlayerSnapshotCodec.capture(
                USER,sessionA
            );
            new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            ).save(firstSnapshot);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            R64OfflineSessionOwnership.Token ownerA=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,USER
                );
            R69OfflineIoCommitGate gate=new R69OfflineIoCommitGate(
                journal,accounts,generations,sessions
            );
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineIoWriteAdmissionForTest(gate);
            try{
                persistence.installOfflineIoWriteAdmissionForTest(gate);
                throw new AssertionError(
                    "double commit hook installation not refused"
                );
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "R69_IO_GATE_DENIED"),
                    "incorrect double-hook denial "+expected);
            }

            world.registerPlayer(sessionA,USER);
            world.start();

            // Capture an immutable snapshot while session A is still the
            // World actor owner. The repository is still the old snapshot.
            WorldPlayerPersistence.CapturedSave capturedA=
                capture(world,sessionA,385,"OLD_A_DEFERRED");
            require(capturedA.ticket.snapshot.username().equals(USER)&&
                    !R57TradePairJournal.digest(
                        capturedA.ticket.snapshot
                    ).equals(R57TradePairJournal.digest(firstSnapshot)),
                "queued old account save must differ from canonical");

            gate.bindForTest(capturedA,ownerA);
            R69OfflineIoCommitGate.Pause pause=
                new R69OfflineIoCommitGate.Pause();
            gate.pauseNextCommit(pause);
            WorldPlayerPersistence.SaveTicket oldTicket=
                persistence.submitCapturedWithBackpressure(
                    capturedA,5_000L
                );
            try{
                require(pause.arrived.await(10,TimeUnit.SECONDS),
                    "real persistence worker did not reach commit gate");
                require(!oldTicket.completion.isDone(),
                    "old save completed before owner takeover");

                // An actual second test-owned JVM transfers ownership
                // while the real I/O worker is paused before repository.
                runTakeover(journal,accounts,generations,sessions);
                R64OfflineSessionOwnership.Token ownerB=
                    ready(journal,accounts,generations,sessions,2L);
                require(!ownerA.owner.equals(ownerB.owner),
                    "separate JVM did not transfer ownership");

                byte[] accountAfterTakeover=accountBytes(accounts);
                byte[] ledgerAfterTakeover=ledgerBytes(generations);
                byte[] ownerAfterTakeover=ownerBytes(sessions);
                pause.release.countDown();

                Throwable refused=failure(oldTicket.completion);
                require(refused instanceof IOException &&
                        refused.getMessage().contains(
                            "R64_SESSION_DENIED stale owner"),
                    "old I/O commit did not fail specifically on ownership: "+
                    refused);
                require(repository.calls.get()==0&&
                        repository.committed.get()==0,
                    "old owner reached FilePlayerRepository.save");
                unchanged(accountAfterTakeover,ledgerAfterTakeover,
                    ownerAfterTakeover,accounts,generations,sessions,
                    "old owner I/O callback");
                require(gate.pendingTokens()==0,
                    "old commit retained reusable authorization token");

                // Simulate the World actor reconnecting. Separate from
                // R64 ownership, WorldPlayer's generation also changes.
                world.unregisterPlayer(sessionA);
                sessionB=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    firstSnapshot,sessionB
                );
                world.registerPlayer(sessionB,USER);

                // The correct owner tries a save, but the actual I/O
                // repository throws BEFORE writing; neither account
                // nor generation/owner records may advance.
                WorldPlayerPersistence.CapturedSave firstB=
                    capture(world,sessionB,560,"NEW_B_FAIL_INJECTED");
                gate.bindForTest(firstB,ownerB);
                repository.failNext.set(true);
                WorldPlayerPersistence.SaveTicket failedB=
                    persistence.submitCapturedWithBackpressure(
                        firstB,5_000L
                    );
                Throwable intentional=failure(failedB.completion);
                require(intentional instanceof IOException &&
                        intentional.getMessage().contains(
                            "R69_TEST_REPOSITORY_INJECTED_BEFORE_WRITE"),
                    "injected repository failure not propagated");
                require(repository.calls.get()==1&&
                        repository.committed.get()==0,
                    "injected failure unexpectedly committed a profile");
                unchanged(accountAfterTakeover,ledgerAfterTakeover,
                    ownerAfterTakeover,accounts,generations,sessions,
                    "pre-write injected failure");

                // Fresh ticket, same current B generation: actual worker
                // commits once and advances R63/R64 to generation 2.
                WorldPlayerPersistence.CapturedSave retryB=
                    capture(world,sessionB,0,"NEW_B_RETRY");
                gate.bindForTest(retryB,ownerB);
                WorldPlayerPersistence.SaveTicket savedB=
                    persistence.submitCapturedWithBackpressure(
                        retryB,5_000L
                    );
                savedB.completion.get(15L,TimeUnit.SECONDS);
                require(repository.calls.get()==2&&
                        repository.committed.get()==1&&
                        gate.successful()==1,
                    "legitimate B save did not use the real writer exactly once");
                R64OfflineSessionOwnership.Token advancedB=
                    ready(journal,accounts,generations,sessions,2L);
                require(advancedB.account.generation==2L&&
                        advancedB.account.snapshotDigest.equals(
                            R57TradePairJournal.digest(
                                retryB.ticket.snapshot
                            )),
                    "IO worker did not atomically advance R63/R64 evidence");

                // An unbound queued manual save must fail closed before
                // repository.save. No guess of current session identity.
                int beforeUnboundCalls=repository.calls.get();
                byte[] savedAccount=accountBytes(accounts);
                byte[] savedLedger=ledgerBytes(generations);
                byte[] savedOwner=ownerBytes(sessions);
                WorldPlayerPersistence.CapturedSave unbound=
                    capture(world,sessionB,565,"UNBOUND_MANUAL");
                WorldPlayerPersistence.SaveTicket unboundTicket=
                    persistence.submitCapturedWithBackpressure(
                        unbound,5_000L
                    );
                Throwable unboundError=failure(unboundTicket.completion);
                require(unboundError instanceof IOException &&
                        unboundError.getMessage().contains(
                            "R69_IO_COMMIT_DENIED unbound checkpoint/save"),
                    "unbound manual work was not rejected at commit");
                require(repository.calls.get()==beforeUnboundCalls,
                    "unbound manual work reached repository");
                unchanged(savedAccount,savedLedger,savedOwner,
                    accounts,generations,sessions,"unbound manual save");

                // Coalesced AUTOSAVE_TICK work shares the exact same
                // WorldPlayerPersistence.write() path, but does NOT have
                // a bound R64 token. Fail closed, keep evidence, and
                // count the persistence error. Do not grant it owner B.
                long previousFailure=persistence.failedCount();
                long previousCaptured=persistence.checkpointCapturedCount();
                final WorldPlayer liveB=sessionB;
                world.submitAndWait(
                    liveB,()->persistence.checkpointDue(
                        WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS
                    ),5_000L
                );
                require(persistence.checkpointCapturedCount()>
                        previousCaptured,
                    "test checkpoint was not actually captured");
                awaitFailureCount(persistence,previousFailure+1L);
                require(repository.calls.get()==beforeUnboundCalls,
                    "unbound checkpoint reached canonical repository");
                unchanged(savedAccount,savedLedger,savedOwner,
                    accounts,generations,sessions,"unbound checkpoint");

                // Another real JVM holding R60 prevents a correctly
                // bound current-owner snapshot from entering repository.
                WorldPlayerPersistence.CapturedSave busy=
                    capture(world,sessionB,566,"BUSY_OS_LOCK");
                gate.bindForTest(busy,advancedB);
                holdOtherJvmLock(root,journal,()->{
                    WorldPlayerPersistence.SaveTicket ticket=
                        persistence.submitCapturedWithBackpressure(
                            busy,5_000L
                        );
                    Throwable error=failure(ticket.completion);
                    require(error instanceof IOException &&
                            error.getMessage().contains(
                                "R60_LOCK_BUSY_OR_OVERLAPPING"),
                        "I/O worker bypassed another JVM's OS lease");
                    require(repository.calls.get()==beforeUnboundCalls,
                        "OS-busy I/O work reached repository");
                });
                unchanged(savedAccount,savedLedger,savedOwner,
                    accounts,generations,sessions,"busy OS lease");

                // The current owner can retry by capturing a NEW save
                // ticket after the lock is released, not by resubmitting
                // an already failed ticket.
                WorldPlayerPersistence.CapturedSave afterLock=
                    capture(world,sessionB,0,"NEW_B_AFTER_BUSY");
                gate.bindForTest(afterLock,advancedB);
                persistence.submitCapturedWithBackpressure(
                    afterLock,5_000L
                ).completion.get(15L,TimeUnit.SECONDS);
                require(repository.calls.get()==3&&
                        repository.committed.get()==2 &&
                        ready(journal,accounts,generations,sessions,2L)
                            .account.generation==3L,
                    "valid retry did not progress generation after OS release");
                require(gate.refusedUnbound()>=2,
                    "unbound manual/checkpoint were not both refused");

                System.out.println(
                    "R69_REAL_IO_COMMIT_OWNERSHIP_FENCE_PASS "+
                    "actualWorldPersistenceWorkerPausedBeforeCommit=true "+
                    "freshJvmOwnershipTransferBeforeOldWorkerRelease=true "+
                    "staleWorkerRefusedBeforeRepositorySave=true "+
                    "canonicalGenerationAndOwnerBytesUnchanged=true "+
                    "realRepositoryFailurePreservedGenerations=true "+
                    "currentOwnerActualWorkerCommitAdvancedR63R64=true "+
                    "unboundManualAndCheckpointRefusedAtCommit=true "+
                    "competingJvmOsLockRefusedActualWorker=true "+
                    "freshOwnerRetryAfterLockSucceeded=true "+
                    "defaultProductionWriterUnchanged=true "+
                    "physicalPowerLossAndLiveAuthNotProven=true"
                );
            }finally{
                pause.release.countDown();
            }
        }finally{
            if(sessionA.registered())
                world.unregisterPlayer(sessionA);
            if(sessionB!=null&&sessionB.registered())
                world.unregisterPlayer(sessionB);
            world.close();
            try(java.util.stream.Stream<Path> entries=Files.walk(root)){
                entries.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException problem){
                        throw new IllegalStateException(
                            "test-owned cleanup "+path,problem
                        );
                    }
                });
            }
        }
    }

    private static WorldPlayerPersistence.CapturedSave capture(
        World world,WorldPlayer player,int addItem,String reason
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.CapturedSave> result=
            new AtomicReference<>();
        world.submitAndWait(player,()->{
            if(addItem!=0)
                player.bank().spawnItem(
                    addItem,1,packets()
                );
            result.set(world.persistence().captureDeferredSave(
                USER,player,player.generation(),0,
                "[r69-offline-io] ",reason
            ));
        },5_000L);
        require(result.get()!=null,"world did not capture test save");
        return result.get();
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static R64OfflineSessionOwnership.Token ready(
        Path journal,Path accounts,Path generations,Path sessions,
        long expectedEpoch
    ){
        R64OfflineSessionOwnership.Review review=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,USER
            );
        require(review.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null&&review.token.epoch==expectedEpoch &&
                !review.loginAuthorized(),
            "offline owner not current "+review.status+" "+review.reason);
        return review.token;
    }

    private static byte[] accountBytes(Path root)throws IOException{
        return Files.readAllBytes(root.resolve("opensrc.properties"));
    }
    private static byte[] ledgerBytes(Path root)throws IOException{
        return Files.readAllBytes(root.resolve(
            "opensrc.r63-generation.properties"
        ));
    }
    private static byte[] ownerBytes(Path root)throws IOException{
        return Files.readAllBytes(root.resolve(
            "opensrc.r64-session.properties"
        ));
    }
    private static void unchanged(
        byte[] a,byte[] b,byte[] c,
        Path accounts,Path generations,Path sessions,String reason
    )throws IOException{
        require(Arrays.equals(a,accountBytes(accounts))&&
                Arrays.equals(b,ledgerBytes(generations))&&
                Arrays.equals(c,ownerBytes(sessions)),
            "R69 I/O denial changed canonical/ledger/owner bytes: "+reason);
    }

    private static void awaitFailureCount(
        WorldPlayerPersistence p,long count
    )throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(p.failedCount()<count&&System.nanoTime()<deadline)
            Thread.sleep(20L);
        require(p.failedCount()>=count,
            "checkpoint failure not accounted after writer refusal");
    }

    private static Throwable failure(
        CompletableFuture<Void> completion
    )throws Exception{
        try{
            completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("expected test write failure");
        }catch(ExecutionException failure){
            return failure.getCause();
        }
    }

    private static void runTakeover(
        Path journal,Path accounts,Path generations,Path sessions
    )throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker",
            "takeover",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError(
                "test-only session takeover JVM timed out"
            );
        }
        String output=new String(child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        require(child.exitValue()==0 &&
                output.contains("R64_SESSION_WORKER_PASS"),
            "session takeover child failed: "+output);
    }

    @FunctionalInterface private interface Action{
        void run()throws Exception;
    }

    private static void holdOtherJvmLock(
        Path scratch,Path journal,Action action
    )throws Exception{
        Path ready=scratch.resolve("r69-lock-ready");
        Path release=scratch.resolve("r69-lock-release");
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R60TradePairLockWorker",
            journal.toString(),ready.toString(),
            release.toString(),"hold"
        ).redirectErrorStream(true).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!Files.exists(ready)){
                if(!child.isAlive()||System.nanoTime()>deadline)
                    throw new AssertionError("OS-lock test worker not ready");
                Thread.sleep(20L);
            }
            action.run();
        }finally{
            Files.write(release,new byte[]{1});
            if(!child.waitFor(15,TimeUnit.SECONDS)){
                child.destroyForcibly();
                child.waitFor(5,TimeUnit.SECONDS);
                throw new AssertionError(
                    "test-owned lock worker failed to exit"
                );
            }
            require(child.exitValue()==0,"OS-lock test worker failed");
        }
    }

    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").toString();
    }

    private static void require(boolean truth,String why){
        if(!truth)throw new AssertionError(why);
    }
    private R69OfflineIoCommitGateTest(){}
}

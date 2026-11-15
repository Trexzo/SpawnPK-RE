package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R72 offline proof: a final-save I/O worker which already entered an
 * uninterruptible repository call CAN still commit after close() has
 * exceptionally settled the SaveTicket. Immutable marker evidence lets
 * a new JVM distinguish INFLIGHT / CANCELLED uncertain / LATE ACK.
 * All inspector outcomes deliberately fail closed; no live I/O hook.
 */
public final class R72OfflineInFlightCommitEvidenceTest {
    private static final String USER="opensrc";

    private static final class BlockingRepository implements PlayerRepository {
        final FilePlayerRepository disk;
        final boolean hardStopAtSave;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        final AtomicInteger attempts=new AtomicInteger();
        final AtomicInteger committed=new AtomicInteger();
        final AtomicInteger interrupts=new AtomicInteger();

        BlockingRepository(Path root,boolean hardStopAtSave){
            disk=new FilePlayerRepository(
                user->root.resolve(user+".properties")
            );
            this.hardStopAtSave=hardStopAtSave;
        }
        @Override public Optional<PlayerSnapshot> load(String user)
                throws IOException{
            return disk.load(user);
        }
        @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
            attempts.incrementAndGet();
            entered.countDown();
            if(hardStopAtSave)
                Runtime.getRuntime().halt(93); // isolated CHILD JVM only
            while(release.getCount()!=0L){
                try{
                    release.await(25L,TimeUnit.MILLISECONDS);
                }catch(InterruptedException ignored){
                    interrupts.incrementAndGet();
                }
            }
            disk.save(snapshot);
            committed.incrementAndGet();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Path root,journal,accounts,generations,sessions,evidence;
        final BlockingRepository repository;
        final World world;
        final WorldPlayer actor=new WorldPlayer();
        final WorldPlayerPersistence persistence;
        final R71OfflineFinalSaveCheckpointFence r71;
        final R72OfflineInFlightCommitEvidence r72;
        final R64OfflineSessionOwnership.Token initialOwner;
        final byte[] originalAccount;
        final byte[] originalLedger;
        final byte[] originalOwner;
        boolean disposed;

        Fixture(Path root,boolean haltAtWrite)throws Exception{
            this.root=root;
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            evidence=root.resolve("r72-evidence");
            for(Path path:new Path[]{
                journal,accounts,generations,sessions,evidence
            })Files.createDirectories(path);
            repository=new BlockingRepository(accounts,haltAtWrite);
            world=World.isolatedForTest(60_000L,repository);
            persistence=world.persistence();
            r71=new R71OfflineFinalSaveCheckpointFence(
                journal,accounts,generations,sessions
            );
            r72=new R72OfflineInFlightCommitEvidence(evidence,r71);
            R60TradePairCoordinator.initializeEmpty(journal);
            actor.bank().spawnItem(995,100,packets());
            PlayerSnapshot original=PlayerSnapshotCodec.capture(USER,actor);
            new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            ).save(original);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER
            );
            initialOwner=R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,USER
            );
            originalAccount=accountBytes();
            originalLedger=ledgerBytes();
            originalOwner=ownerBytes();
            persistence.installOfflineIoWriteAdmissionForTest(r72);
            persistence.installOfflineCheckpointOwnerCaptureForTest(r71);
            world.registerPlayer(actor,USER);
            world.start();
            r71.bindActorForTest(
                actor,actor.generation(),initialOwner
            );
        }

        private static final class Registered {
            final UUID id;
            final WorldPlayerPersistence.CapturedSave captured;
            Registered(
                UUID id,WorldPlayerPersistence.CapturedSave captured
            ){
                this.id=id;this.captured=captured;
            }
        }

        Registered captureAndRegisterFinal()throws Exception{
            AtomicReference<Registered> result=new AtomicReference<>();
            world.submitAndWait(actor,()->{
                actor.bank().spawnItem(560,1,packets());
                WorldPlayerPersistence.CapturedSave save=
                    persistence.captureDeferredFinalSave(
                        USER,actor,actor.generation(),0,
                        "[r72-offline] ","FINAL_SAVE_LATE_IO"
                    );
                try{
                    UUID id=r72.registerFinalForTest(
                        save,initialOwner
                    );
                    result.set(new Registered(id,save));
                }catch(IOException failure){
                    throw new IllegalStateException(
                        "test-only R72 registration failed",failure
                    );
                }
            },5_000L);
            require(result.get()!=null,
                "World actor did not capture final save");
            return result.get();
        }

        byte[] accountBytes()throws IOException{
            return Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
        }
        byte[] ledgerBytes()throws IOException{
            return Files.readAllBytes(
                generations.resolve(
                    "opensrc.r63-generation.properties"
                )
            );
        }
        byte[] ownerBytes()throws IOException{
            return Files.readAllBytes(
                sessions.resolve(
                    "opensrc.r64-session.properties"
                )
            );
        }

        void assertOriginal(String why)throws IOException{
            require(Arrays.equals(originalAccount,accountBytes())&&
                    Arrays.equals(originalLedger,ledgerBytes())&&
                    Arrays.equals(originalOwner,ownerBytes()),
                "pre-commit durable evidence changed: "+why);
        }

        @Override public void close()throws Exception{
            if(disposed)return;
            disposed=true;
            repository.release.countDown();
            if(actor.registered())
                world.unregisterPlayer(actor);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"inspect".equals(args[0])){
            Path evidence=Paths.get(args[1]);
            UUID id=UUID.fromString(args[2]);
            R72OfflineInFlightCommitEvidence.Review review=
                R72OfflineInFlightCommitEvidence.inspect(evidence,id);
            if(!args[3].equals(review.status.name())||
               review.loginAuthorized()||
               review.replayAuthorized()||
               review.quarantineReleased())
                throw new AssertionError(
                    "R72_READER_MISMATCH expected="+args[3]+
                    " actual="+review.status+" reason="+review.reason
                );
            System.out.println(
                "R72_FRESH_JVM_INSPECT_PASS status="+review.status+
                " loginAuthorized=false replayAuthorized=false"
            );
            return;
        }
        if(args.length==2&&"hardstop".equals(args[0])){
            runHardStopChild(Paths.get(args[1]));
            throw new AssertionError(
                "hardstop child unexpectedly returned"
            );
        }
        require(args.length==0,"unexpected R72 arguments");
        runLateWriteAfterCancelledFuture();
        runHardStopBeforeCommit();
        System.out.println(
            "R72_OFFLINE_LATE_INFLIGHT_WRITE_EVIDENCE_PASS "+
            "actualWorldPersistenceWorkerEnteredRepository=true "+
            "forcedCloseCancelledTicketWhileRepositoryStillBlocked=true "+
            "intentEnteredCancellationIndividuallyPersisted=true "+
            "freshJvmInspectorFailClosedWhileWriteUncertain=true "+
            "lateRepositoryCommitAfterCloseObserved=true "+
            "accountAndOwnerAdvancedDespiteCancelledSaveTicket=true "+
            "freshJvmInspectorNeverAuthorizesLoginOrReplay=true "+
            "isolatedChildHardStopLeavesUnresolvedIntent=true "+
            "corruptMarkerFailsClosedWithoutDeletingEvidence=true "+
            "noProductionInstallationNoPowerLossProof=true"
        );
    }

    private static void runLateWriteAfterCancelledFuture()
            throws Exception{
        Path scratch=Files.createTempDirectory(
            "spk-r72-late-write-after-close-"
        );
        try(Fixture f=new Fixture(scratch,false)){
            Fixture.Registered registered=f.captureAndRegisterFinal();
            UUID id=registered.id;
            checkFreshJVM(
                f.evidence,id,"BLOCK_REGISTERED_UNCERTAIN"
            );
            WorldPlayerPersistence.SaveTicket ticket=
                f.persistence.submitCapturedWithBackpressure(
                    registered.captured,5_000L
                );
            try{
                require(f.repository.entered.await(
                        10L,TimeUnit.SECONDS),
                    "actual I/O worker never entered repository.save");
                require(f.repository.attempts.get()==1 &&
                        f.repository.committed.get()==0 &&
                        !ticket.completion.isDone(),
                    "test repository was not paused before write");
                f.assertOriginal("worker paused before real filesystem call");
                checkFreshJVM(
                    f.evidence,id,"BLOCK_INFLIGHT_UNCERTAIN"
                );

                // Actual close() waits 5s, interrupts the live worker,
                // waits 1s then rejects its future. The test-owned
                // repository ignores interrupt and stays blocked.
                f.persistence.close();
                Throwable canceled=failure(ticket.completion);
                require(canceled instanceof RejectedExecutionException &&
                        canceled.getMessage().contains(
                            "active save did not terminate"),
                    "in-flight future not terminal after forced close: "+
                    canceled);
                require(f.repository.interrupts.get()>0 &&
                        f.repository.committed.get()==0,
                    "repository unexpectedly stopped/committed at close");
                f.assertOriginal("future canceled before late write");
                checkFreshJVM(
                    f.evidence,id,"BLOCK_CANCELLED_UNCERTAIN"
                );
                require(f.persistence.metrics().contains("completed=0,") &&
                        f.persistence.failedCount()==1L,
                    "future accounting before late return inconsistent");

                // The OS does NOT undo a file write because the Java
                // Future is rejected. Release the still-live worker.
                f.repository.release.countDown();
                awaitStatus(f.evidence,id,
                    R72OfflineInFlightCommitEvidence.ReviewStatus
                        .BLOCK_LATE_COMMIT_OBSERVED);
                require(f.repository.committed.get()==1 &&
                        !Arrays.equals(
                            f.originalAccount,f.accountBytes()
                        ) &&
                        !Arrays.equals(
                            f.originalLedger,f.ledgerBytes()
                        ) &&
                        !Arrays.equals(
                            f.originalOwner,f.ownerBytes()
                        ),
                    "late actual repository write not observed after close");
                R64OfflineSessionOwnership.Review owner=
                    R64OfflineSessionOwnership.inspect(
                        f.journal,f.accounts,f.generations,f.sessions,USER
                    );
                R63AccountSaveAdmission.Check account=
                    R63AccountSaveAdmission.inspect(
                        f.journal,f.accounts,f.generations,USER
                    );
                require(owner.status==
                        R64OfflineSessionOwnership.Status.BLOCK_RELEASED &&
                        account.status==
                            R63AccountSaveAdmission.Status
                                .READY_FOR_OTHER_OFFLINE_CHECKS &&
                        account.token.generation==2L,
                    "late worker did not publish account+RELEASED evidence");
                require(ticket.completion.isCompletedExceptionally()&&
                        f.persistence.metrics().contains("completed=0,") &&
                        f.persistence.failedCount()==1L,
                    "cancelled SaveTicket was resurrected by late completion");
                checkFreshJVM(
                    f.evidence,id,"BLOCK_LATE_COMMIT_OBSERVED"
                );
                checkFreshJVM(
                    f.evidence,UUID.randomUUID(),"BLOCK_UNREGISTERED"
                );

                // Negative control in independent evidence directory:
                // malformed ACK phase must never be parsed as success.
                Path corrupt=scratch.resolve("corrupt-evidence");
                Files.createDirectory(corrupt);
                try(java.nio.file.DirectoryStream<Path> entries=
                        Files.newDirectoryStream(f.evidence)){
                    for(Path file:entries){
                        if(Files.isRegularFile(file))
                            Files.copy(file,corrupt.resolve(
                                file.getFileName()
                            ));
                    }
                }
                Path ack=corrupt.resolve(
                    "r72-"+id+"-ACK_OBSERVED.properties"
                );
                Files.writeString(
                    ack,"INVALID R72 MARKER",StandardCharsets.UTF_8
                );
                checkFreshJVM(
                    corrupt,id,"BLOCK_AMBIGUOUS_EVIDENCE"
                );
            }finally{
                f.repository.release.countDown();
            }
        }finally{
            deleteFixture(scratch);
        }
    }

    private static void runHardStopBeforeCommit()throws Exception{
        Path scratch=Files.createTempDirectory(
            "spk-r72-hard-stop-"
        );
        try{
            Path root=scratch.resolve("child");
            Files.createDirectories(root);
            Process child=new ProcessBuilder(
                javaExecutable(),"-cp",
                System.getProperty("java.class.path"),
                "spk.local.R72OfflineInFlightCommitEvidenceTest",
                "hardstop",root.toString()
            ).redirectErrorStream(true).start();
            if(!child.waitFor(25L,TimeUnit.SECONDS)){
                child.destroyForcibly();
                child.waitFor(5L,TimeUnit.SECONDS);
                throw new AssertionError(
                    "isolated hard-stop child did not exit"
                );
            }
            require(child.exitValue()==93,
                "child did not halt at repository.save: "+
                child.exitValue()+" "+
                new String(child.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)
            );
            UUID id=UUID.fromString(
                Files.readString(root.resolve("r72-child-id.txt")).trim()
            );
            Path evidence=root.resolve("r72-evidence");
            checkFreshJVM(
                evidence,id,"BLOCK_INFLIGHT_UNCERTAIN"
            );
            require(!Files.exists(
                    evidence.resolve(
                        "r72-"+id+"-ACK_OBSERVED.properties"
                    )),
                "hard stop unexpectedly wrote completion acknowledgement");
            require(!Files.exists(
                    evidence.resolve(
                        "r72-"+id+"-CANCEL_OBSERVED.properties"
                    )),
                "abrupt halt incorrectly simulated orderly cancellation");

            R64OfflineSessionOwnership.Review actor=
                R64OfflineSessionOwnership.inspect(
                    root.resolve("journal"),root.resolve("accounts"),
                    root.resolve("generations"),root.resolve("sessions"),
                    USER
                );
            require(actor.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    actor.token.account.generation==1L &&
                    !actor.loginAuthorized(),
                "abrupt halt changed an account before repository call");
        }finally{
            deleteFixture(scratch);
        }
    }

    private static void runHardStopChild(Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        Fixture.Registered save=f.captureAndRegisterFinal();
        Files.writeString(
            root.resolve("r72-child-id.txt"),
            save.id.toString(),StandardCharsets.UTF_8
        );
        f.persistence.submitCapturedWithBackpressure(
            save.captured,5_000L
        );
        // The test-owned persistence thread runs Runtime.halt(93)
        // at actual repository.save BEFORE writing canonical bytes.
        f.repository.entered.await(15L,TimeUnit.SECONDS);
        throw new AssertionError(
            "hard-stop repository returned instead of halting JVM"
        );
    }

    private static void checkFreshJVM(
        Path evidence,UUID id,String status
    )throws Exception{
        Process reader=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R72OfflineInFlightCommitEvidenceTest",
            "inspect",evidence.toString(),id.toString(),status
        ).redirectErrorStream(true).start();
        if(!reader.waitFor(20L,TimeUnit.SECONDS)){
            reader.destroyForcibly();
            reader.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("new JVM evidence inspector timed out");
        }
        String output=new String(reader.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        require(reader.exitValue()==0 &&
                output.contains("R72_FRESH_JVM_INSPECT_PASS"),
            "new JVM evidence inspection failed: "+
            reader.exitValue()+" "+output);
    }

    private static void awaitStatus(
        Path evidence,UUID id,
        R72OfflineInFlightCommitEvidence.ReviewStatus expected
    )throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(System.nanoTime()<deadline){
            R72OfflineInFlightCommitEvidence.Review found=
                R72OfflineInFlightCommitEvidence.inspect(evidence,id);
            if(found.status==expected)return;
            Thread.sleep(20L);
        }
        R72OfflineInFlightCommitEvidence.Review finalStatus=
            R72OfflineInFlightCommitEvidence.inspect(evidence,id);
        throw new AssertionError(
            "late I/O evidence not observed, status="+
            finalStatus.status+" reason="+finalStatus.reason
        );
    }

    private static Throwable failure(
        CompletableFuture<Void> future
    )throws Exception{
        try{
            future.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("expected refused in-flight SaveTicket");
        }catch(ExecutionException expected){
            return expected.getCause();
        }
    }

    private static void deleteFixture(Path root)throws Exception{
        try(java.util.stream.Stream<Path> paths=Files.walk(root)){
            paths.sorted(Comparator.reverseOrder()).forEach(file->{
                try{Files.deleteIfExists(file);}
                catch(IOException failure){
                    throw new IllegalStateException(
                        "test-only cleanup "+file,failure
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

    private static String javaExecutable(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").toString();
    }

    private static void require(boolean check,String message){
        if(!check)throw new AssertionError(message);
    }

    private R72OfflineInFlightCommitEvidenceTest(){}
}

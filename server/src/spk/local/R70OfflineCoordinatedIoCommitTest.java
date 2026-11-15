package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R70: actual WorldPlayerPersistence worker executes test-only coordinated
 * R64 single-account commit, carrying generation/owner tokens forward.
 * Independent tests check a stale owner, a non-mutating duplicate snapshot
 * and an injected crash after ledger but before owner-record publication.
 *
 * No live sessions or production accounts are created or modified.
 */
public final class R70OfflineCoordinatedIoCommitTest {
    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r70-coordinated-");
        try{
            normalAndStale(scratch.resolve("normal"));
            partialCommitQuarantine(scratch.resolve("fault"));
            System.out.println(
                "R70_REAL_IO_COORDINATED_COMMIT_PASS "+
                "ordinarySave=true duplicateSnapshotNoOp=true "+
                "checkpointSave=true deferredFinalSave=true "+
                "generationAndOwnerAdvanceTogether=true "+
                "staleIdenticalAndChangedSaveDenied=true "+
                "crossJvmTakeover=true rawWriterNotInvoked=true "+
                "injectedPartialCommitQuarantined=true "+
                "noAutomaticRecovery=true noProductionHook=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException error){
                        throw new IllegalStateException(
                            "R70 test cleanup "+path,error
                        );
                    }
                });
            }
        }
    }

    private static void normalAndStale(Path root)throws Exception{
        Paths4 paths=new Paths4(root);
        R64OfflineSessionOwnership.Token first=paths.initialize();
        World world=World.isolatedForTest(
            60_000L,rawWriterMustNotRun(paths.files())
        );
        try(world){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            R70OfflineCoordinatedPersistenceAdmission gate=
                new R70OfflineCoordinatedPersistenceAdmission(
                    paths.journal,paths.accounts,paths.generations,
                    paths.sessions,first,false
                );
            world.persistence().installOfflineSaveAdmissionForTest(gate);
            world.start();
            ServerPacketWriter packets=packets();
            AtomicReference<WorldPlayerPersistence.SaveTicket> latest=
                new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,995,packets);
                latest.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r70] ","ORDINARY_SAVE"
                ));
            },10_000L);
            latest.get().completion.get(15L,TimeUnit.SECONDS);
            check(paths,first,2L,latest.get().snapshot);
            require(gate.acceptedWrites()==1,
                "first ordinary save not coordinated");

            // An identical payload must be validated under R60/R64,
            // then no-op without advancing R63 generation.
            world.submitAndWait(player,generation,()->{
                latest.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r70] ","UNCHANGED_SAVE"
                ));
            },10_000L);
            latest.get().completion.get(15L,TimeUnit.SECONDS);
            check(paths,first,2L,latest.get().snapshot);
            require(gate.acceptedWrites()==1&&
                    gate.unchangedWrites()==1,
                "duplicate snapshot incorrectly rewrote ledger");

            world.submitAndWait(player,generation,()->{
                addItem(player,385,packets);
                world.persistence().checkpointDue(100L);
            },10_000L);
            awaitCheckpointWritten(world.persistence(),1L);
            PlayerSnapshot checkpoint=paths.files().load("opensrc")
                .orElseThrow(()->new IOException("checkpoint missing"));
            check(paths,first,3L,checkpoint);
            require(gate.acceptedWrites()==2,
                "checkpoint did not use coordinated writer");

            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,560,packets);
                finalSave.set(
                    world.persistence().captureDeferredFinalSave(
                        "opensrc",player,generation,0,
                        "[r70] ","FINAL_SAVE"
                    )
                );
                world.persistence().checkpointDue(200L);
            },10_000L);
            require(world.persistence().checkpointCapturedCount()==1L,
                "final-save suppression failed");
            WorldPlayerPersistence.CapturedSave captured=finalSave.get();
            require(captured!=null,"final capture missing");
            world.persistence().submitCapturedWithBackpressure(
                captured,5_000L
            ).completion.get(15L,TimeUnit.SECONDS);
            check(paths,first,4L,captured.ticket.snapshot);
            require(gate.acceptedWrites()==3&&
                    gate.unchangedWrites()==1,
                "final save not committed through coordinated API");

            // A genuine different JVM replaces owner A. Even an identical
            // old snapshot must not sneak through as a successful no-op.
            takeover(paths);
            R64OfflineSessionOwnership.Review ownerB=paths.review();
            require(ownerB.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    ownerB.token!=null&&ownerB.token.epoch==2L,
                "cross-JVM takeover was not published");

            byte[] accountBefore=Files.readAllBytes(paths.accountFile());
            byte[] ledgerBefore=Files.readAllBytes(paths.ledgerFile());
            byte[] ownerBefore=Files.readAllBytes(paths.ownerFile());

            world.submitAndWait(player,generation,()->{
                latest.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r70] ","STALE_IDENTICAL_SAVE"
                ));
            },10_000L);
            expectFailure(latest.get(),
                "R64_SESSION_DENIED stale owner");

            world.submitAndWait(player,generation,()->{
                addItem(player,565,packets);
                latest.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r70] ","STALE_CHANGED_SAVE"
                ));
            },10_000L);
            expectFailure(latest.get(),
                "R64_SESSION_DENIED stale owner");
            require(Arrays.equals(accountBefore,Files.readAllBytes(
                        paths.accountFile())) &&
                    Arrays.equals(ledgerBefore,Files.readAllBytes(
                        paths.ledgerFile())) &&
                    Arrays.equals(ownerBefore,Files.readAllBytes(
                        paths.ownerFile())),
                "stale worker modified canonical files");
            require(gate.acceptedWrites()==3&&
                    gate.unchangedWrites()==1 &&
                    world.persistence().failedCount()==2,
                "stale/no-op accounting inconsistent");

            // An explicit successor may still progress through the
            // existing coordinated API. The old hook is NOT rebound.
            R64OfflineSessionOwnership.Token successor=
                R64OfflineSessionOwnership.saveOwned(
                    paths.journal,paths.accounts,paths.generations,
                    paths.sessions,ownerB.token,
                    latest.get().snapshot,false
                );
            require(successor.epoch==2L&&
                    successor.account.generation==5L,
                "successor could not save after stale refusal");
        }
    }

    private static void partialCommitQuarantine(Path root)throws Exception{
        Paths4 paths=new Paths4(root);
        R64OfflineSessionOwnership.Token first=paths.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustNotRun(paths.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            R70OfflineCoordinatedPersistenceAdmission gate=
                new R70OfflineCoordinatedPersistenceAdmission(
                    paths.journal,paths.accounts,paths.generations,
                    paths.sessions,first,true
                );
            world.persistence().installOfflineSaveAdmissionForTest(gate);
            world.start();
            ServerPacketWriter packets=packets();
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,995,packets);
                ticket.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r70-fault] ","INJECTED_LEDGER_OWNER_GAP"
                ));
            },10_000L);
            expectFailure(ticket.get(),
                "R64_INJECTED_AFTER_LEDGER_BEFORE_OWNER_RECORD");
            require(gate.acceptedWrites()==0 &&
                    paths.review().status==
                        R64OfflineSessionOwnership.Status
                            .BLOCK_INDETERMINATE &&
                    !paths.review().loginAuthorized(),
                "injected partial commit not quarantined");

            byte[] account=Files.readAllBytes(paths.accountFile());
            byte[] ledger=Files.readAllBytes(paths.ledgerFile());
            byte[] owner=Files.readAllBytes(paths.ownerFile());
            // A follow-up write cannot repair a partially published R64
            // state without an explicit external recovery protocol.
            world.submitAndWait(player,generation,()->{
                addItem(player,385,packets);
                ticket.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r70-fault] ","RETRY_AFTER_PARTIAL"
                ));
            },10_000L);
            expectFailure(ticket.get(),
                "R64_SESSION_DENIED stale generation");
            require(Arrays.equals(account,Files.readAllBytes(
                        paths.accountFile())) &&
                    Arrays.equals(ledger,Files.readAllBytes(
                        paths.ledgerFile())) &&
                    Arrays.equals(owner,Files.readAllBytes(
                        paths.ownerFile())),
                "partial commit retry modified quarantined files");
        }
    }

    private static void check(
        Paths4 p,R64OfflineSessionOwnership.Token original,
        long expectedGeneration,PlayerSnapshot expected
    )throws IOException{
        R64OfflineSessionOwnership.Review review=p.review();
        require(review.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null &&
                review.token.epoch==original.epoch &&
                review.token.owner.equals(original.owner) &&
                review.token.account.generation==expectedGeneration &&
                R57TradePairJournal.digest(expected).equals(
                    review.token.account.snapshotDigest),
            "R63/R64 metadata did not advance with canonical save");
        PlayerSnapshot stored=p.files().load("opensrc")
            .orElseThrow(()->new IOException("canonical save missing"));
        require(R57TradePairJournal.digest(stored).equals(
                R57TradePairJournal.digest(expected)),
            "stored account differs from snapshot");
    }

    private static void awaitCheckpointWritten(
        WorldPlayerPersistence persistence,long expected
    )throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(persistence.checkpointWrittenCount()<expected){
            if(persistence.failedCount()>0)
                throw new AssertionError(
                    "checkpoint failed before completion: "+
                    persistence.metrics()
                );
            if(System.nanoTime()>until)
                throw new AssertionError(
                    "checkpoint worker timed out: "+
                    persistence.metrics()
                );
            Thread.sleep(10L);
        }
    }

    private static PlayerRepository rawWriterMustNotRun(
        FilePlayerRepository files
    ){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username
            )throws IOException{return files.load(username);}
            @Override public void save(PlayerSnapshot value)
                    throws IOException{
                throw new IOException(
                    "R70_RAW_WRITER_MUST_NOT_RUN"
                );
            }
        };
    }

    private static void expectFailure(
        WorldPlayerPersistence.SaveTicket ticket,String fragment
    )throws Exception{
        require(ticket!=null,"missing save ticket");
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError(
                "unexpectedly allowed save "+fragment
            );
        }catch(ExecutionException failed){
            Throwable cause=failed.getCause();
            require(cause instanceof IOException &&
                    cause.getMessage().contains(fragment),
                "unexpected write failure "+cause+
                ", expected "+fragment);
        }
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static void addItem(
        WorldPlayer player,int item,ServerPacketWriter writer
    )throws IOException{
        String result=player.bank().spawnItem(item,1,writer);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "test bank mutation refused "+result);
    }

    private static void takeover(Paths4 paths)throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker","takeover",
            paths.journal.toString(),paths.accounts.toString(),
            paths.generations.toString(),paths.sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("R70 child takeover timed out");
        }
        String stdout=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                stdout.contains("R64_SESSION_WORKER_PASS"),
            "R70 takeover failed: "+stdout);
    }

    private static String javaExecutable(){
        return Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name")
                .toLowerCase().contains("win")?"java.exe":"java"
        ).toString();
    }

    private static void require(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }

    private static final class Paths4 {
        final Path journal;
        final Path accounts;
        final Path generations;
        final Path sessions;
        Paths4(Path root)throws IOException{
            Files.createDirectory(root);
            journal=Files.createDirectory(root.resolve("journal"));
            accounts=Files.createDirectory(root.resolve("accounts"));
            generations=Files.createDirectory(root.resolve("generations"));
            sessions=Files.createDirectory(root.resolve("sessions"));
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                name->accounts.resolve(name+".properties")
            );
        }
        Path accountFile(){return accounts.resolve("opensrc.properties");}
        Path ledgerFile(){
            return generations.resolve(
                "opensrc.r63-generation.properties"
            );
        }
        Path ownerFile(){
            return sessions.resolve(
                "opensrc.r64-session.properties"
            );
        }
        R64OfflineSessionOwnership.Token initialize()
                throws IOException{
            files().save(PlayerSnapshotCodec.capture(
                "opensrc",new WorldPlayer()
            ));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            return R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,"opensrc"
            );
        }
        R64OfflineSessionOwnership.Review review(){
            return R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        }
    }

    private R70OfflineCoordinatedIoCommitTest(){}
}

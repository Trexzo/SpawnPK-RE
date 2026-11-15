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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated offline acceptance of the REAL WorldPlayerPersistence writer:
 * queued checkpoints and an already-captured final save must not enter the
 * FilePlayerRepository after a separate JVM replaces the R64 account owner.
 * No real socket, authenticated login or production account is involved.
 */
public final class R69OfflinePersistenceWriteFenceTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spk-r69-io-write-");
        try{
            runStaleWriterRace(root);
            runUnguardedBaseline(root);
            System.out.println(
                "R69_REAL_IO_COMMIT_STALE_OWNER_FENCE_PASS "+
                "realDeferredFinalSave=true "+
                "realCheckpointDrain=true "+
                "checkpointCoalescing=true "+
                "separateJvmOwnerTakeover=true "+
                "staleWritesRefusedBeforeRepository=true "+
                "accountLedgerOwnerBytesPreserved=true "+
                "newOwnerCanSaveViaR64=true "+
                "defaultPersistenceUnchanged=true "+
                "productionHooksNotInstalled=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failed){
                        throw new IllegalStateException(
                            "R69 test cleanup failed "+path,failed
                        );
                    }
                });
            }
        }
    }

    private static void runStaleWriterRace(Path root)throws Exception{
        Path journal=Files.createDirectory(root.resolve("journal"));
        Path accounts=Files.createDirectory(root.resolve("accounts"));
        Path generations=Files.createDirectory(root.resolve("generations"));
        Path sessions=Files.createDirectory(root.resolve("sessions"));
        FilePlayerRepository files=repository(accounts);
        files.save(PlayerSnapshotCodec.capture(
            "opensrc",new WorldPlayer()
        ));
        R60TradePairCoordinator.initializeEmpty(journal);
        R63AccountSaveAdmission.initializeExisting(
            journal,accounts,generations,"opensrc"
        );
        R64OfflineSessionOwnership.Token initialOwner=
            R64OfflineSessionOwnership.claimFirst(
                journal,accounts,generations,sessions,"opensrc"
            );

        AtomicInteger repositoryCalls=new AtomicInteger();
        PlayerRepository observedRepository=new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username
            )throws IOException{
                return files.load(username);
            }
            @Override public void save(
                PlayerSnapshot snapshot
            )throws IOException{
                repositoryCalls.incrementAndGet();
                files.save(snapshot);
            }
        };

        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        R69OfflinePersistenceAdmission gate=
            new R69OfflinePersistenceAdmission(
                journal,accounts,generations,sessions,
                initialOwner,entered,release
            );

        try(World world=World.isolatedForTest(
                    60_000L,observedRepository)){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineSaveAdmissionForTest(gate);
            boolean duplicateDenied=false;
            try{
                persistence.installOfflineSaveAdmissionForTest(gate);
            }catch(IllegalStateException expected){
                duplicateDenied=expected.getMessage().contains(
                    "already installed"
                );
            }
            require(duplicateDenied,"second offline writer gate installed");
            world.start();

            ServerPacketWriter packetWriter=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})
            );

            world.submitAndWait(player,generation,()->{
                addItem(player,995,packetWriter);
                persistence.checkpointDue(100L);
            },10_000L);

            require(entered.await(10L,TimeUnit.SECONDS),
                "first real checkpoint never reached IO admission");
            require(gate.attempted()==1&&gate.admitted()==0,
                "checkpoint was not held BEFORE its owner recheck");

            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,385,packetWriter);
                persistence.checkpointDue(200L);
                addItem(player,560,packetWriter);
                persistence.checkpointDue(300L);
                addItem(player,565,packetWriter);
                finalSave.set(persistence.captureDeferredFinalSave(
                    "opensrc",player,generation,0,
                    "[r69] ","LOGOUT_FINAL_SAVE"
                ));
                // Final capture must suppress future autosaves for this
                // World actor generation, but not silently discard the
                // already pending coalesced checkpoint.
                persistence.checkpointDue(400L);
            },10_000L);

            WorldPlayerPersistence.CapturedSave captured=finalSave.get();
            require(captured!=null,
                "real deferred final snapshot was not captured");
            require(persistence.checkpointCapturedCount()==3L,
                "unexpected checkpoint captures");
            require(persistence.checkpointCoalescedCount()==1L,
                "newer pending checkpoint did not replace older");
            require(persistence.checkpointSuppressed(
                    player.id(),generation),
                "final-save capture did not suppress later checkpoint");

            WorldPlayerPersistence.SaveTicket ticket=
                persistence.submitCapturedWithBackpressure(
                    captured,5_000L
                );
            require(ticket==captured.ticket,
                "captured ticket changed at final-save admission");
            require(!ticket.completion.isDone(),
                "final save bypassed the held IO writer");
            require(repositoryCalls.get()==0,
                "repository was called before owner takeover");

            runTakeoverWorker(
                journal,accounts,generations,sessions
            );
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,"opensrc"
                );
            require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    review.token!=null&&
                    review.token.epoch==2L &&
                    !initialOwner.owner.equals(review.token.owner),
                "separate JVM did not establish a new R64 owner");

            byte[] account=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            byte[] generationBytes=Files.readAllBytes(
                generations.resolve("opensrc.r63-generation.properties")
            );
            byte[] sessionBytes=Files.readAllBytes(
                sessions.resolve("opensrc.r64-session.properties")
            );

            release.countDown();
            try{
                ticket.completion.get(15L,TimeUnit.SECONDS);
                throw new AssertionError(
                    "stale captured final save reached repository"
                );
            }catch(ExecutionException expected){
                Throwable cause=expected.getCause();
                require(cause instanceof IOException&&
                        cause.getMessage().contains(
                            "R64_SESSION_DENIED stale owner"),
                    "incorrect final-save rejection: "+cause);
            }

            require(gate.attempted()==3&&gate.admitted()==0,
                "not all two checkpoints and the final save denied");
            require(persistence.checkpointWrittenCount()==0L,
                "stale checkpoint was recorded as written");
            require(persistence.failedCount()==3L,
                "stale write failures were not reflected in metrics");
            require(repositoryCalls.get()==0,
                "stale IO work entered the FilePlayerRepository");
            require(Arrays.equals(account,Files.readAllBytes(
                        accounts.resolve("opensrc.properties")
                    )) &&
                    Arrays.equals(generationBytes,Files.readAllBytes(
                        generations.resolve(
                            "opensrc.r63-generation.properties"
                        )
                    )) &&
                    Arrays.equals(sessionBytes,Files.readAllBytes(
                        sessions.resolve(
                            "opensrc.r64-session.properties"
                        )
                    )),
                "stale IO work modified canonical account or fence bytes");

            // A legitimate successor can still make progress using the
            // already coordinated R64 API; the R69 raw writer is NOT
            // authorized as a new production account commit protocol.
            R64OfflineSessionOwnership.Token advanced=
                R64OfflineSessionOwnership.saveOwned(
                    journal,accounts,generations,sessions,
                    review.token,ticket.snapshot,false
                );
            require(advanced.account.generation==2L,
                "successor could not write after stale-work refusal");
        }finally{
            release.countDown();
        }
    }

    private static void runUnguardedBaseline(Path root)throws Exception{
        Path baseline=Files.createDirectory(root.resolve("baseline"));
        FilePlayerRepository files=repository(baseline);
        try(World world=World.isolatedForTest(60_000L,files)){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            world.start();
            AtomicReference<WorldPlayerPersistence.CapturedSave> pending=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                pending.set(
                    world.persistence().captureDeferredSave(
                        "opensrc",player,generation,0,
                        "[r69-baseline] ","NO_TEST_HOOK"
                    )
                );
            },10_000L);
            WorldPlayerPersistence.CapturedSave captured=pending.get();
            require(captured!=null,
                "default deferred save not captured");
            world.persistence().submitCapturedWithBackpressure(
                captured,5_000L
            ).completion.get(15L,TimeUnit.SECONDS);
            require(Files.isRegularFile(
                baseline.resolve("opensrc.properties")),
                "default unguarded repository write was lost");
        }
    }

    private static void addItem(
        WorldPlayer player,int item,ServerPacketWriter packets
    )throws IOException{
        String result=player.bank().spawnItem(item,1,packets);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "bank setup refused "+item+" "+result);
    }

    private static FilePlayerRepository repository(Path root){
        return new FilePlayerRepository(
            username->root.resolve(username+".properties")
        );
    }

    private static void runTakeoverWorker(
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
            throw new AssertionError(
                "test-owned R64 takeover child timed out"
            );
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                output.contains("R64_SESSION_WORKER_PASS"),
            "test-owned takeover failed "+child.exitValue()+" "+output);
    }

    private static String javaExecutable(){
        return Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name")
                .toLowerCase().contains("win")?"java.exe":"java"
        ).toString();
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private R69OfflinePersistenceWriteFenceTest(){}
}

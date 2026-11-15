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
 * R72 offline real-IO acceptance: a test-installed terminal capture barrier,
 * coordinated account saves plus durable R64-bound capture ordinal recovery.
 * All files are disposable; no production World or LocalSession installs
 * these policies.
 */
public final class R72OfflineTerminalAndDurableOrderTest {
    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r72-durable-");
        try{
            restartWithTerminalFence(scratch.resolve("restart"));
            mismatchAfterInterruptedMarker(scratch.resolve("partial"));
            crossJvmOwnerTransfer(scratch.resolve("owner"));
            System.out.println(
                "R72_OFFLINE_TERMINAL_DURABLE_ORDER_PASS "+
                "postFinalCaptureDenied=true "+
                "duplicateFinalDenied=true "+
                "olderDeferredSubmissionDenied=true "+
                "checkpointSuppressed=true "+
                "finalSaveCommitted=true "+
                "durableOrdinalReplayedAfterRestart=true "+
                "newWorldCaptureAboveDurableFloor=true "+
                "identicalSnapshotDurablyAdvancesOrdinal=true "+
                "staleReplayedOrdinalDenied=true "+
                "interruptedMarkerQuarantined=true "+
                "crossJvmOwnerMismatchDenied=true "+
                "noRawRepositoryWrite=true "+
                "noProductionHooks=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> entries=Files.walk(scratch)){
                entries.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(
                            "R72 temporary cleanup "+path,failure
                        );
                    }
                });
            }
        }
    }

    private static void restartWithTerminalFence(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token first=p.initialize();
        PlayerSnapshot committedAfterWorldOne;

        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryRefusal(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineTerminalCaptureFenceForTest();
            R72OfflineDurableCaptureOrder gate=p.resume(first,false);
            persistence.seedOfflineCaptureOrderForTest(
                gate.resumeCaptureFloor()
            );
            persistence.installOfflineSaveAdmissionForTest(gate);
            world.start();
            ServerPacketWriter packets=packets();

            AtomicReference<WorldPlayerPersistence.CapturedSave> old=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.SaveTicket> saved=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                old.set(persistence.captureDeferredSave(
                    "opensrc",player,generation,0,
                    "[r72] ","EARLY_DEFERRED"
                ));
                addItem(player,995,packets);
                saved.set(persistence.captureAndSave(
                    "opensrc",player,generation,0,
                    "[r72] ","CHANGED"
                ));
            },10_000L);
            require(old.get().ticket.captureOrdinal==1L &&
                    saved.get().captureOrdinal==2L,
                "World capture ordinals did not follow immutable captures");
            saved.get().completion.get(15L,TimeUnit.SECONDS);
            checkCurrent(p,first,2L,2L);

            // A newer byte-identical save must advance the *durable*
            // capture watermark but not the R63 generation.
            world.submitAndWait(player,generation,()->{
                saved.set(persistence.captureAndSave(
                    "opensrc",player,generation,0,
                    "[r72] ","NEWER_IDENTICAL"
                ));
            },10_000L);
            saved.get().completion.get(15L,TimeUnit.SECONDS);
            require(saved.get().captureOrdinal==3L &&
                    gate.identicalCount()==1,
                "newer identical capture not persisted as a watermark");
            checkCurrent(p,first,2L,3L);

            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,385,packets);
                finalSave.set(persistence.captureDeferredFinalSave(
                    "opensrc",player,generation,0,
                    "[r72] ","FINAL_SAVE"
                ));
                persistence.checkpointDue(100L);
            },10_000L);
            require(finalSave.get()!=null &&
                    finalSave.get().ticket.captureOrdinal==4L &&
                    persistence.checkpointCapturedCount()==0L &&
                    persistence.checkpointSuppressed(
                        player.id(),generation),
                "final capture did not install checkpoint suppression");

            expectIllegal(()->world.submitAndWait(
                player,generation,()->{
                    addItem(player,560,packets);
                    persistence.captureAndSave(
                        "opensrc",player,generation,0,
                        "[r72] ","POST_FINAL_ILLEGAL"
                    );
                },10_000L),"R72_TERMINAL_CAPTURE_DENIED");
            expectIllegal(()->world.submitAndWait(
                player,generation,()->persistence.captureDeferredFinalSave(
                    "opensrc",player,generation,0,
                    "[r72] ","DUPLICATE_FINAL"
                ),10_000L),"R72_TERMINAL_CAPTURE_DENIED");

            byte[][] before=p.allBytes();
            expectIllegal(()->persistence.submitCapturedWithBackpressure(
                old.get(),5_000L
            ),"R72_TERMINAL_SUBMISSION_DENIED");
            require(old.get().ticket.completion.isCompletedExceptionally(),
                "rejected deferred ticket left unresolved");
            require(equalsBytes(before,p.allBytes()),
                "late pre-final submission modified account files");

            WorldPlayerPersistence.SaveTicket finalTicket=
                persistence.submitCapturedWithBackpressure(
                    finalSave.get(),5_000L
                );
            finalTicket.completion.get(15L,TimeUnit.SECONDS);
            require(finalTicket.captureOrdinal==4L &&
                    gate.committedCount()==2,
                "real final writer did not commit through R64");
            checkCurrent(p,first,3L,4L);
            committedAfterWorldOne=p.files().load("opensrc")
                .orElseThrow(()->new IOException(
                    "final snapshot not persisted"
                ));
        }

        // Restart: the NEW WorldPlayerPersistence instance would ordinarily
        // reset its local ordinal to zero. Explicitly restore the verified
        // R72 watermark before the new World is started.
        R64OfflineSessionOwnership.Review persisted=p.review();
        require(persisted.token!=null,
            "restarted owner unexpectedly missing");
        R72OfflineDurableCaptureOrder reattached=
            p.resume(persisted.token,false);
        require(reattached.resumeCaptureFloor()==4L,
            "durable ordinal not recovered across World restart");
        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryRefusal(p.files()))){
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                committedAfterWorldOne,player
            );
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineTerminalCaptureFenceForTest();
            persistence.seedOfflineCaptureOrderForTest(
                reattached.resumeCaptureFloor()
            );
            persistence.installOfflineSaveAdmissionForTest(reattached);
            world.start();
            ServerPacketWriter writer=packets();
            AtomicReference<WorldPlayerPersistence.SaveTicket> future=
                new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,560,writer);
                future.set(persistence.captureAndSave(
                    "opensrc",player,generation,0,
                    "[r72-restart] ","FIRST_POST_RESTART_SAVE"
                ));
            },10_000L);
            require(future.get().captureOrdinal==5L,
                "new World did not start above validated durable watermark");
            future.get().completion.get(15L,TimeUnit.SECONDS);
            checkCurrent(p,first,4L,5L);

            byte[][] before=p.allBytes();
            try{
                reattached.withOrderedAdmission(
                    4L,committedAfterWorldOne,
                    ()->{throw new IOException("R72_RAW_WRITER_BYPASS");}
                );
                throw new AssertionError(
                    "pre-restart ordinal was accepted"
                );
            }catch(IOException denied){
                require(denied.getMessage().contains(
                    "R72_ORDER_DENIED stale durable capture ordinal"
                ),"wrong durable replay refusal "+denied);
            }
            require(equalsBytes(before,p.allBytes()) &&
                    reattached.rejectedCount()==1,
                "replayed ordinal changed canonical files");

            expectIllegal(()->persistence.seedOfflineCaptureOrderForTest(5L),
                "R72_CAPTURE_SEED_DENIED");
        }
    }

    private static void mismatchAfterInterruptedMarker(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token first=p.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryRefusal(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            R72OfflineDurableCaptureOrder injecting=
                p.resume(first,true);
            world.persistence().installOfflineSaveAdmissionForTest(injecting);
            world.start();
            ServerPacketWriter writer=packets();
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,995,writer);
                ticket.set(world.persistence().captureAndSave(
                    "opensrc",player,generation,0,
                    "[r72-partial] ","INJECT_AFTER_R64"
                ));
            },10_000L);
            expectFutureFailure(ticket.get(),
                "R72_INJECTED_AFTER_ACCOUNT_BEFORE_MARKER");
            require(injecting.committedCount()==0,
                "partial commit was falsely recorded as completed");

            R64OfflineSessionOwnership.Review updated=p.review();
            require(updated.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    updated.token!=null &&
                    updated.token.account.generation==2L,
                "injected fault did not produce the expected R64 state");
            byte[][] before=p.allBytes();
            expectIOException(()->p.resume(updated.token,false),
                "R72_ORDER_DENIED watermark/account/owner mismatch");
            expectIOException(()->injecting.resumeCaptureFloor(),
                "R64_SESSION_DENIED stale generation");
            require(equalsBytes(before,p.allBytes()),
                "failed marker resume silently rewrote canonical evidence");
        }
    }

    private static void crossJvmOwnerTransfer(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token initial=p.initialize();
        R72OfflineDurableCaptureOrder before=p.resume(initial,false);
        runTakeoverWorker(p);
        R64OfflineSessionOwnership.Review after=p.review();
        require(after.token!=null&&after.token.epoch==2L,
            "R72 separate JVM takeover not recorded");
        byte[][] unchanged=p.allBytes();
        expectIOException(()->before.resumeCaptureFloor(),
            "R64_SESSION_DENIED stale owner");
        expectIOException(()->p.resume(after.token,false),
            "R72_ORDER_DENIED watermark/account/owner mismatch");
        require(equalsBytes(unchanged,p.allBytes()),
            "R72 watermark adopted different owner automatically");
    }

    private static void checkCurrent(
        Fixture p,R64OfflineSessionOwnership.Token original,
        long generation,long ordinal
    )throws Exception{
        R64OfflineSessionOwnership.Review current=p.review();
        require(current.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                current.token!=null &&
                current.token.owner.equals(original.owner) &&
                current.token.epoch==original.epoch &&
                current.token.account.generation==generation,
            "R72 canonical R63/R64 generation not synchronized");
        R72OfflineDurableCaptureOrder guard=
            p.resume(current.token,false);
        require(guard.resumeCaptureFloor()==ordinal,
            "R72 persisted capture ordinal mismatched");
        PlayerSnapshot stored=p.files().load("opensrc")
            .orElseThrow(()->new IOException("canonical missing"));
        require(R57TradePairJournal.digest(stored).equals(
            current.token.account.snapshotDigest),
            "R72 canonical snapshot hash mismatched account metadata");
    }

    private static PlayerRepository rawRepositoryRefusal(
        FilePlayerRepository files
    ){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username
            )throws IOException{
                return files.load(username);
            }
            @Override public void save(PlayerSnapshot snapshot)
                    throws IOException{
                throw new IOException(
                    "R72_RAW_REPOSITORY_WRITE_MUST_NOT_RUN"
                );
            }
        };
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
            "R72 test bank mutation refused "+result);
    }

    @FunctionalInterface private interface Action{
        void run()throws Exception;
    }

    private static void expectIllegal(Action action,String text)
            throws Exception{
        try{
            action.run();
            throw new AssertionError("expected R72 refusal "+text);
        }catch(IllegalStateException refused){
            require(refused.getMessage()!=null &&
                    refused.getMessage().contains(text),
                "wrong terminal refusal "+refused);
        }
    }

    private static void expectIOException(Action action,String text)
            throws Exception{
        try{
            action.run();
            throw new AssertionError("expected R72 IO refusal "+text);
        }catch(IOException refused){
            require(refused.getMessage()!=null &&
                    refused.getMessage().contains(text),
                "wrong durability refusal "+refused);
        }
    }

    private static void expectFutureFailure(
        WorldPlayerPersistence.SaveTicket ticket,String expected
    )throws Exception{
        require(ticket!=null,"save ticket missing");
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("expected interrupted commit refusal");
        }catch(ExecutionException error){
            Throwable cause=error.getCause();
            require(cause instanceof IOException &&
                    cause.getMessage().contains(expected),
                "incorrect save refusal "+cause);
        }
    }

    private static void runTakeoverWorker(Fixture p)throws Exception{
        Process child=new ProcessBuilder(
            javaExecutable(),"-cp",
            System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker","takeover",
            p.journal.toString(),p.accounts.toString(),
            p.generations.toString(),p.sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError("R72 takeover child timeout");
        }
        String stdout=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0 &&
                stdout.contains("R64_SESSION_WORKER_PASS"),
            "R72 takeover JVM failed "+stdout);
    }

    private static String javaExecutable(){
        return Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java"
        ).toString();
    }

    private static boolean equalsBytes(byte[][] a,byte[][] b){
        if(a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)
            if(!Arrays.equals(a[i],b[i]))return false;
        return true;
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private static final class Fixture {
        final Path journal;
        final Path accounts;
        final Path generations;
        final Path sessions;
        final Path markers;
        Fixture(Path root)throws IOException{
            Files.createDirectory(root);
            journal=Files.createDirectory(root.resolve("journal"));
            accounts=Files.createDirectory(root.resolve("accounts"));
            generations=Files.createDirectory(root.resolve("generations"));
            sessions=Files.createDirectory(root.resolve("sessions"));
            markers=Files.createDirectory(root.resolve("watermarks"));
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                name->accounts.resolve(name+".properties")
            );
        }
        R64OfflineSessionOwnership.Token initialize()throws IOException{
            files().save(PlayerSnapshotCodec.capture(
                "opensrc",new WorldPlayer()
            ));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            R64OfflineSessionOwnership.Token initial=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,"opensrc"
                );
            R72OfflineDurableCaptureOrder.initializeFresh(
                journal,accounts,generations,sessions,markers,initial
            );
            return initial;
        }
        R72OfflineDurableCaptureOrder resume(
            R64OfflineSessionOwnership.Token owner,boolean inject
        )throws IOException{
            return R72OfflineDurableCaptureOrder.resume(
                journal,accounts,generations,sessions,markers,
                owner,inject
            );
        }
        R64OfflineSessionOwnership.Review review(){
            return R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        }
        byte[][] allBytes()throws IOException{
            return new byte[][]{
                Files.readAllBytes(
                    accounts.resolve("opensrc.properties")),
                Files.readAllBytes(
                    generations.resolve(
                        "opensrc.r63-generation.properties")),
                Files.readAllBytes(
                    sessions.resolve(
                        "opensrc.r64-session.properties")),
                Files.readAllBytes(
                    markers.resolve(
                        "opensrc.r72-capture-order.properties"))
            };
        }
    }

    private R72OfflineTerminalAndDurableOrderTest(){}
}

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * R73 offline regression using real World actor + IO worker; no live
 * LocalSession, real player account, game socket or production hook.
 */
public final class R73OfflineFinalLogoutDrainTest {
    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r73-logout-");
        try{
            successfulDrainAndWorldUnregister(
                scratch.resolve("success")
            );
            saturatedFinalAdmissionRefusesRelease(
                scratch.resolve("saturated")
            );
            failedFinalCommitRefusesRelease(
                scratch.resolve("failed")
            );
            System.out.println(
                "R73_REAL_LOGOUT_IO_DRAIN_PASS "+
                "checkpointThenFinalSave=true "+
                "workerQueueBarrierCompleted=true "+
                "durableFinalOrdinalVerified=true "+
                "ownerReleasedUnderR60OnWorldActor=true "+
                "worldActorUnregistered=true "+
                "lateGameplayCommandRejected=true "+
                "postReleaseSaveRejected=true "+
                "boundedQueueSaturationRejectsFinal=true "+
                "saturationDoesNotReleaseOwner=true "+
                "failedFinalDoesNotReleaseOwner=true "+
                "uncertainMarkerNotAutomaticallyRepaired=true "+
                "defaultProductionPathUnmodified=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException error){
                        throw new IllegalStateException(
                            "R73 test-owned cleanup "+path,error
                        );
                    }
                });
            }
        }
    }

    private static void successfulDrainAndWorldUnregister(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token original=p.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryMustNeverSave(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            R72OfflineDurableCaptureOrder guard=
                configure(world,p,original);
            world.start();
            ServerPacketWriter writer=packets();
            AtomicReference<WorldPlayerPersistence.CapturedSave> finalCapture=
                new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,995,writer);
                persistence.checkpointDue(100L);
                addItem(player,385,writer);
                finalCapture.set(
                    persistence.captureDeferredFinalSave(
                        "opensrc",player,generation,0,
                        "[r73] ","FINAL_LOGOUT"
                    )
                );
                persistence.checkpointDue(200L);
            },10_000L);
            require(finalCapture.get()!=null &&
                    finalCapture.get().finalSnapshot &&
                    finalCapture.get().ticket.captureOrdinal==2L &&
                    persistence.checkpointCapturedCount()==1L,
                "R73 checkpoint and final were not actually captured");

            R73OfflineFinalLogoutCoordinator.Outcome outcome=
                R73OfflineFinalLogoutCoordinator.finish(
                    world,player,generation,guard,
                    finalCapture.get(),15_000L
                );
            require(outcome.finalCaptureOrdinal==2L &&
                    outcome.released.phase==
                        R64OfflineSessionOwnership.Phase.RELEASED &&
                    outcome.released.account.generation==3L &&
                    !world.players().owns(player,generation) &&
                    persistence.checkpointWrittenCount()==1L &&
                    persistence.failedCount()==0L &&
                    persistence.queuedWrites()==0,
                "R73 final logout did not wait for checkpoint and final");

            R64OfflineSessionOwnership.Review released=p.review();
            require(released.status==
                    R64OfflineSessionOwnership.Status.BLOCK_RELEASED &&
                    !released.loginAuthorized() &&
                    guard.currentToken().phase==
                        R64OfflineSessionOwnership.Phase.RELEASED,
                "R73 owner not durably released after final commit");

            PlayerSnapshot onDisk=p.files().load("opensrc")
                .orElseThrow(()->new IOException("final save missing"));
            require(R57TradePairJournal.digest(onDisk).equals(
                    R57TradePairJournal.digest(
                        finalCapture.get().ticket.snapshot
                    )),
                "R73 canonical profile not equal to captured final state");
            byte[][] proof=p.allBytes();

            // World actor has been unregistered so new command submission
            // cannot run an attack, item mutation or another capture.
            try{
                world.submitAndWait(player,generation,()->{
                    addItem(player,560,writer);
                },5_000L);
                throw new AssertionError(
                    "R73 post-unregister gameplay command executed"
                );
            }catch(Exception cancelled){
                require(!world.players().owns(player,generation),
                    "R73 gameplay command failed for unrelated reason");
            }

            try{
                guard.withOrderedAdmission(
                    outcome.finalCaptureOrdinal+1L,
                    finalCapture.get().ticket.snapshot,
                    ()->{throw new IOException("R73_RAW_WRITER_BYPASS");}
                );
                throw new AssertionError(
                    "R73 committed after R64 release"
                );
            }catch(IOException denied){
                require(denied.getMessage().contains(
                    "R64_SESSION_DENIED"),
                    "R73 wrong post-release write refusal "+denied);
            }
            require(equalBytes(proof,p.allBytes()),
                "R73 post-release action mutated canonical evidence");
        }
    }

    private static void saturatedFinalAdmissionRefusesRelease(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token owner=p.initialize();
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryMustNeverSave(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            R72OfflineDurableCaptureOrder guard=
                configure(world,p,owner);
            world.start();

            try{
                persistence.holdOfflineIoWorkerForTest(entered,release);
                require(entered.await(10L,TimeUnit.SECONDS),
                    "R73 synthetic IO holder was not admitted");
                persistence.fillOfflineIoQueueWithNoopsForTest();

                ServerPacketWriter writer=packets();
                AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                    new AtomicReference<>();
                world.submitAndWait(player,generation,()->{
                    addItem(player,995,writer);
                    finalSave.set(
                        persistence.captureDeferredFinalSave(
                            "opensrc",player,generation,0,
                            "[r73-saturated] ","FINAL_QUEUE_FULL"
                        )
                    );
                },10_000L);

                byte[][] before=p.allBytes();
                expectRefused(()->R73OfflineFinalLogoutCoordinator.finish(
                    world,player,generation,guard,finalSave.get(),75L
                ),"R73_LOGOUT_DENIED final IO rejected");
                require(equalBytes(before,p.allBytes()) &&
                        persistence.failedCount()==1L &&
                        world.players().owns(player,generation) &&
                        p.review().status==
                            R64OfflineSessionOwnership.Status
                                .READY_FOR_OTHER_OFFLINE_CHECKS,
                    "R73 queue saturation released or changed live owner");
            }finally{
                release.countDown();
            }
            persistence.awaitOfflineIoDrainForTest(15_000L);
            require(p.review().status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS,
                "R73 saturation unexpectedly released owner after drain");
        }finally{
            release.countDown();
        }
    }

    private static void failedFinalCommitRefusesRelease(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token original=p.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryMustNeverSave(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineTerminalCaptureFenceForTest();
            R72OfflineDurableCaptureOrder injected=p.resume(
                original,true
            );
            persistence.seedOfflineCaptureOrderForTest(
                injected.resumeCaptureFloor()
            );
            persistence.installOfflineSaveAdmissionForTest(injected);
            world.start();
            ServerPacketWriter writer=packets();
            AtomicReference<WorldPlayerPersistence.CapturedSave> capture=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,995,writer);
                capture.set(persistence.captureDeferredFinalSave(
                    "opensrc",player,generation,0,
                    "[r73-failed] ","FINAL_MARKER_FAULT"
                ));
            },10_000L);

            expectRefused(()->R73OfflineFinalLogoutCoordinator.finish(
                world,player,generation,injected,capture.get(),15_000L
            ),"R73_LOGOUT_DENIED final IO rejected");
            R64OfflineSessionOwnership.Review review=p.review();
            require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    review.token!=null &&
                    review.token.phase==
                        R64OfflineSessionOwnership.Phase.ACTIVE &&
                    review.token.account.generation==2L &&
                    world.players().owns(player,generation) &&
                    persistence.failedCount()==1L,
                "R73 uncertain final commit incorrectly released owner");

            byte[][] evidence=p.allBytes();
            expectRefused(()->p.resume(review.token,false),
                "R72_ORDER_DENIED watermark/account/owner mismatch");
            require(equalBytes(evidence,p.allBytes()),
                "R73 failure path auto-repaired marker evidence");
        }
    }

    private static R72OfflineDurableCaptureOrder configure(
        World world,Fixture p,R64OfflineSessionOwnership.Token owner
    )throws Exception{
        WorldPlayerPersistence persistence=world.persistence();
        persistence.installOfflineTerminalCaptureFenceForTest();
        R72OfflineDurableCaptureOrder gate=p.resume(owner,false);
        persistence.seedOfflineCaptureOrderForTest(
            gate.resumeCaptureFloor()
        );
        persistence.installOfflineSaveAdmissionForTest(gate);
        return gate;
    }

    private static PlayerRepository rawRepositoryMustNeverSave(
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
                    "R73_RAW_REPOSITORY_WRITE_MUST_NOT_RUN"
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
        String output=player.bank().spawnItem(item,1,writer);
        require(output.startsWith("ITEM_SPAWN_OK"),
            "R73 item mutation refused "+output);
    }

    private static void expectRefused(Action action,String fragment)
            throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "R73 unexpected success when expecting "+fragment
            );
        }catch(IOException failure){
            require(failure.getMessage().contains(fragment),
                "R73 wrong refusal "+failure+
                " expected "+fragment);
        }
    }

    @FunctionalInterface private interface Action{
        void run()throws Exception;
    }

    private static boolean equalBytes(byte[][] a,byte[][] b){
        if(a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)
            if(!Arrays.equals(a[i],b[i]))return false;
        return true;
    }

    private static void require(boolean valid,String text){
        if(!valid)throw new AssertionError(text);
    }

    private static final class Fixture {
        final Path journal,accounts,generations,sessions,markers;

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
                username->accounts.resolve(username+".properties")
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
            R64OfflineSessionOwnership.Token first=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,"opensrc"
                );
            R72OfflineDurableCaptureOrder.initializeFresh(
                journal,accounts,generations,sessions,markers,first
            );
            return first;
        }

        R72OfflineDurableCaptureOrder resume(
            R64OfflineSessionOwnership.Token token,boolean inject
        )throws IOException{
            return R72OfflineDurableCaptureOrder.resume(
                journal,accounts,generations,sessions,markers,
                token,inject
            );
        }

        R64OfflineSessionOwnership.Review review(){
            return R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        }

        byte[][] allBytes()throws IOException{
            return new byte[][]{
                Files.readAllBytes(accounts.resolve(
                    "opensrc.properties")),
                Files.readAllBytes(generations.resolve(
                    "opensrc.r63-generation.properties")),
                Files.readAllBytes(sessions.resolve(
                    "opensrc.r64-session.properties")),
                Files.readAllBytes(markers.resolve(
                    "opensrc.r72-capture-order.properties"))
            };
        }
    }

    private R73OfflineFinalLogoutDrainTest(){}
}

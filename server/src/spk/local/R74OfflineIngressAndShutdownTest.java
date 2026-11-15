package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R74 OFFLINE acceptance using the real World command inbox, real
 * WorldPlayerPersistence worker and the R60/R63/R64/R72 file protocol.
 * No live account, authenticated LocalSession or real game socket.
 */
public final class R74OfflineIngressAndShutdownTest {
    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r74-");
        try{
            commandQuiescenceAndVerifiedLogout(
                scratch.resolve("ingress")
            );
            forcedShutdownBlockedBeforeCommit(
                scratch.resolve("forced")
            );
            System.out.println(
                "R74_OFFLINE_INGRESS_FORCED_SHUTDOWN_PASS "+
                "preQuiescenceQueuedCommandRejectedAtDispatch=true "+
                "postQuiescenceProducerRejectedAtEnqueue=true "+
                "ordinaryGameplayMutationNeverRan=true "+
                "trustedFinalReleaseStillRuns=true "+
                "finalSaveAndDrainVerified=true "+
                "R64OwnerReleasedAndWorldUnregistered=true "+
                "forcedShutdownInterruptWitnessFired=true "+
                "uncooperativeBlockedWriterRejectedAfterClose=true "+
                "inFlightFinalFutureFailed=true "+
                "canonicalFilesUnchangedAfterForcedShutdown=true "+
                "ownerNotReleasedOnUncertainFinal=true "+
                "defaultProductionHooksUninstalled=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(scratch)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(
                            "R74 fixture cleanup "+path,failure
                        );
                    }
                });
            }
        }
    }

    private static void commandQuiescenceAndVerifiedLogout(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token owner=p.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawRepositoryForbidden(p.files()))){
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineTerminalCaptureFenceForTest();
            R72OfflineDurableCaptureOrder gate=p.resume(owner);
            persistence.seedOfflineCaptureOrderForTest(
                gate.resumeCaptureFloor()
            );
            persistence.installOfflineSaveAdmissionForTest(gate);

            R74OfflineCommandIngressFence ingress=
                new R74OfflineCommandIngressFence(world);
            ingress.installForTest();
            world.start();
            ServerPacketWriter packet=packets();
            AtomicInteger mutations=new AtomicInteger();
            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            AtomicReference<java.util.concurrent.CompletableFuture<Void>>
                preQueued=new AtomicReference<>();

            world.submitAndWait(player,generation,()->{
                addItem(player,995,packet);
                finalSave.set(
                    persistence.captureDeferredFinalSave(
                        "opensrc",player,generation,0,
                        "[r74] ","QUIESCED_FINAL_SAVE"
                    )
                );
                // This producer was allowed to enqueue while the World
                // handler was running, BEFORE the quiescence flip. It
                // must still be rechecked under the player's mutation
                // lock when the inbox later tries to dispatch it.
                preQueued.set(world.submit(
                    player,generation,()->{
                        mutations.incrementAndGet();
                        addItem(player,560,packet);
                    }
                ));
                ingress.quiesceInsideWorldActor(player,generation);
            },10_000L);
            require(finalSave.get()!=null &&
                    ingress.isQuiesced(player,generation) &&
                    preQueued.get()!=null,
                "R74 source actor did not capture and quiesce");

            expectQuiescenceRejection(preQueued.get(),
                "R74_COMMAND_QUIESCED before handler");

            java.util.concurrent.CompletableFuture<Void> submittedLater=
                world.submit(player,generation,()->{
                    mutations.incrementAndGet();
                    addItem(player,385,packet);
                });
            expectQuiescenceRejection(
                submittedLater,"R74_COMMAND_QUIESCED before enqueue"
            );
            require(mutations.get()==0,
                "post-final gameplay command reached handler");

            R73OfflineFinalLogoutCoordinator.Outcome complete=
                R73OfflineFinalLogoutCoordinator.finish(
                    world,player,generation,gate,
                    finalSave.get(),15_000L,ingress
                );
            require(complete.finalCaptureOrdinal==1L &&
                    complete.released.phase==
                        R64OfflineSessionOwnership.Phase.RELEASED &&
                    !world.players().owns(player,generation) &&
                    p.review().status==
                        R64OfflineSessionOwnership.Status.BLOCK_RELEASED &&
                    persistence.failedCount()==0 &&
                    mutations.get()==0,
                "trusted final release did not complete after quiescence");

            PlayerSnapshot disk=p.files().load("opensrc").orElseThrow(
                ()->new IOException("R74 final account missing")
            );
            require(R57TradePairJournal.digest(disk).equals(
                    R57TradePairJournal.digest(
                        finalSave.get().ticket.snapshot
                    )),"R74 final canonical account mismatch");

            byte[][] evidence=p.allBytes();
            expectQuiescenceRejection(
                world.submit(player,generation,()->mutations.incrementAndGet()),
                "PLAYER");
            require(mutations.get()==0 &&
                    equalBytes(evidence,p.allBytes()),
                "R74 post-release command changed canonical evidence");
        }
    }

    private static void forcedShutdownBlockedBeforeCommit(Path root)
            throws Exception{
        Fixture p=new Fixture(root);
        R64OfflineSessionOwnership.Token owner=p.initialize();
        World world=World.isolatedForTest(
            60_000L,rawRepositoryForbidden(p.files())
        );
        BlockingForcedSaveGate hold=null;
        Thread closer=null;
        AtomicReference<Throwable> closeFailure=new AtomicReference<>();
        try{
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"opensrc");
            WorldPlayerPersistence persistence=world.persistence();
            persistence.installOfflineTerminalCaptureFenceForTest();
            R72OfflineDurableCaptureOrder durable=p.resume(owner);
            persistence.seedOfflineCaptureOrderForTest(
                durable.resumeCaptureFloor()
            );
            hold=new BlockingForcedSaveGate(durable);
            persistence.installOfflineSaveAdmissionForTest(hold);
            world.start();
            ServerPacketWriter packet=packets();
            AtomicReference<WorldPlayerPersistence.CapturedSave> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                addItem(player,995,packet);
                finalSave.set(persistence.captureDeferredFinalSave(
                    "opensrc",player,generation,0,
                    "[r74-shutdown] ","HELD_FINAL_SAVE"
                ));
            },10_000L);
            byte[][] original=p.allBytes();
            WorldPlayerPersistence.SaveTicket ticket=
                persistence.submitCapturedWithBackpressure(
                    finalSave.get(),5_000L
                );
            require(hold.entered.await(10L,TimeUnit.SECONDS),
                "R74 final save did not reach actual worker");

            // Closing the World uses the existing 5-second graceful IO
            // wait, then the actual shutdownNow forced worker path.
            // The writer deliberately IGNORES interruption, waiting
            // until this test explicitly releases it. It must not
            // commit after close returns and its future is rejected.
            closer=new Thread(()->{
                try{
                    world.close();
                }catch(Throwable failed){
                    closeFailure.set(failed);
                }
            },"r74-offline-closer");
            closer.start();
            require(hold.forced.await(15L,TimeUnit.SECONDS),
                "R74 shutdown did not signal forced IO witness");
            require(ticket.completion.isCompletedExceptionally()||
                    !ticket.completion.isDone(),
                "R74 forced shutdown falsely reported successful save");
            hold.release.countDown();
            require(hold.exited.await(10L,TimeUnit.SECONDS),
                "R74 uncooperative writer did not exit after forced stop");
            closer.join(15_000L);
            require(!closer.isAlive() &&
                    closeFailure.get()==null &&
                    hold.abortObserved.get() &&
                    ticket.completion.isCompletedExceptionally(),
                "R74 forced shutdown failed to abort in-flight save");
            try{
                ticket.completion.get(1L,TimeUnit.SECONDS);
                throw new AssertionError(
                    "R74 forced final save falsely succeeded"
                );
            }catch(ExecutionException|CancellationException expected){
                // Either the real worker's rejected future or the
                // offline writer's explicit abort may win this race.
            }

            require(p.review().status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    equalBytes(original,p.allBytes()) &&
                    durable.currentToken().phase==
                        R64OfflineSessionOwnership.Phase.ACTIVE,
                "R74 forced shutdown published or released uncertain final");
        }finally{
            if(hold!=null)
                hold.release.countDown();
            if(closer!=null){
                closer.join(15_000L);
                require(!closer.isAlive(),"R74 cleanup closer still running");
            }
            if(!world.closed())
                world.close();
        }
    }

    private static final class BlockingForcedSaveGate
            implements WorldPlayerPersistence.OfflineOrderedSaveAdmission,
                       WorldPlayerPersistence.OfflineForcedShutdownWitness {
        final R72OfflineDurableCaptureOrder delegate;
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        final CountDownLatch forced=new CountDownLatch(1);
        final CountDownLatch exited=new CountDownLatch(1);
        final AtomicBoolean abortObserved=new AtomicBoolean();
        private final AtomicBoolean shutdownRequested=new AtomicBoolean();

        BlockingForcedSaveGate(R72OfflineDurableCaptureOrder delegate){
            this.delegate=delegate;
        }

        @Override public void onOfflineForcedShutdownForTest(){
            shutdownRequested.set(true);
            forced.countDown();
        }

        @Override public void withAdmission(
            PlayerSnapshot snapshot,
            WorldPlayerPersistence.CheckedSaveWriter writer
        )throws IOException{
            throw new IOException(
                "R74_FORCED_SAVE_DENIED missing capture ordinal"
            );
        }

        @Override public void withOrderedAdmission(
            long ordinal,PlayerSnapshot snapshot,
            WorldPlayerPersistence.CheckedSaveWriter writer
        )throws IOException{
            entered.countDown();
            try{
                // Simulates a non-cooperative pre-commit IO block.
                // Do NOT hold the R60 file lock during this wait.
                for(;;){
                    try{
                        if(release.await(20L,TimeUnit.SECONDS))
                            break;
                        throw new IOException(
                            "R74_FORCED_SAVE_DENIED test hold timeout"
                        );
                    }catch(InterruptedException ignored){
                        // A deliberately uncooperative blocking writer
                        // ignores thread interruption. The separate
                        // abort witness is what must prevent late commit.
                    }
                }
                if(shutdownRequested.get()){
                    abortObserved.set(true);
                    throw new IOException(
                        "R74_FORCED_SAVE_DENIED writer aborted before R60"
                    );
                }
                delegate.withOrderedAdmission(
                    ordinal,snapshot,writer
                );
            }finally{
                exited.countDown();
            }
        }
    }

    private static PlayerRepository rawRepositoryForbidden(
        FilePlayerRepository file
    ){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username
            )throws IOException{
                return file.load(username);
            }
            @Override public void save(PlayerSnapshot snapshot)
                    throws IOException{
                throw new IOException(
                    "R74_RAW_REPOSITORY_WRITE_MUST_NOT_RUN"
                );
            }
        };
    }

    private static void expectQuiescenceRejection(
        java.util.concurrent.CompletableFuture<Void> future,
        String marker
    )throws Exception{
        require(future!=null,"R74 expected queued command missing");
        try{
            future.get(10L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R74 ordinary command ran after quiescence"
            );
        }catch(ExecutionException|CancellationException refused){
            Throwable cause=refused instanceof ExecutionException
                ?refused.getCause():refused;
            require(cause!=null &&
                    cause.getMessage()!=null &&
                    cause.getMessage().contains(marker),
                "R74 command refused for wrong reason "+cause);
        }
    }

    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }

    private static void addItem(
        WorldPlayer player,int item,ServerPacketWriter packet
    )throws IOException{
        String result=player.bank().spawnItem(item,1,packet);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R74 bank mutation rejected "+result);
    }

    private static boolean equalBytes(byte[][] a,byte[][] b){
        if(a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)
            if(!Arrays.equals(a[i],b[i]))return false;
        return true;
    }

    private static void require(boolean value,String why){
        if(!value)throw new AssertionError(why);
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
            R64OfflineSessionOwnership.Token owner
        )throws IOException{
            return R72OfflineDurableCaptureOrder.resume(
                journal,accounts,generations,sessions,markers,owner,false
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

    private R74OfflineIngressAndShutdownTest(){}
}

package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.24: opt-in PREPARED_NO_GRANT persistence FIFO isolation.
 *
 * Old checkpoint in-flight -> strict snapshot queued on same worker ->
 * old deferred capture submitted after it: old capture is rejected, never
 * written over the strict file. New captures can still write normally.
 * This is NOT an inventory credit or a durable World-level claim commit.
 */
public final class G2124OrderedPreparedAccountSaveIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean oldWorkerAheadOfBarrier=false;
        boolean preparedStrictWrittenAfterOld=false;
        boolean oldDeferredCaptureRejected=false;
        boolean laterCheckpointStillWrites=false;
        boolean savedPreparedAndUnclaimed=false;
        boolean noItemCredit=false;
        boolean ownerGenerationFence=false;
        boolean failedStrictNoReceipt=false;
        boolean terminalAdmissionDenied=false;
        boolean boundedWorkerQueue=false;
        boolean noSettlementAuthority=false;
        boolean foreignAccountUnaffected=false;

        Path dir=Files.createTempDirectory("g2124-ordered-");
        BlockingRepository repository=new BlockingRepository(
            username->dir.resolve(username+".properties")
        );
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer alice=new WorldPlayer();
        long generation=world.registerPlayer(
            alice,LocalAccountProfiles.PRIMARY
        );

        try{
            world.start();
            world.submitAndWait(
                alice,generation,
                ()->{
                    alice.mailbox().deliver(new RewardDeliveryMessage(
                        "g2124:gift","Gift","Prepared-only",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,50)
                        ),
                        "CUSTOM_LOCALLAB_G2124_FIXTURE"
                    ));
                    alice.movement().setRunEnergy(12);
                    world.persistence().checkpointDue(
                        WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS
                    );
                },
                5_000L
            );
            if(!repository.firstStarted.await(
                    5,TimeUnit.SECONDS))
                throw new AssertionError(
                    "first checkpoint did not enter repository"
                );

            AtomicReference<WorldPlayerPersistence.CapturedSave>
                delayed=new AtomicReference<>();
            world.submitAndWait(
                alice,generation,
                ()->{
                    delayed.set(world.persistence().captureDeferredSave(
                        LocalAccountProfiles.PRIMARY,
                        alice,generation,0,
                        "[g2124] ","DEFERRED_BEFORE_BARRIER"
                    ));
                },
                5_000L
            );

            AtomicReference<PlayerSnapshot> prepared=
                new AtomicReference<>();
            world.submitAndWait(
                alice,generation,
                ()->{
                    MailboxPreparedClaimJournal.Intent intent=
                        MailboxPreparedClaimJournal.prepare(
                            alice,alice.mailbox().get("g2124:gift")
                        );
                    MailboxPreparedClaimJournal.stageOnly(alice,intent);
                    alice.movement().setRunEnergy(40);
                    prepared.set(PlayerSnapshotCodec.capture(
                        LocalAccountProfiles.PRIMARY,alice
                    ));
                },
                5_000L
            );

            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    username->dir.resolve(username+".properties")
                );
            CompletableFuture<
                StrictDurablePlayerSnapshotWriter.Receipt
            > strictFuture=world.persistence()
                .submitPreparedStrictBarrier(
                    alice,generation,prepared.get(),strict
                );
            WorldPlayerPersistence.SaveTicket staleTicket=
                world.persistence().submitCapturedWithBackpressure(
                    delayed.get(),2_000L
                );

            oldWorkerAheadOfBarrier=
                repository.calls.get()==1&&
                !strictFuture.isDone()&&
                !staleTicket.completion.isDone()&&
                world.persistence().queuedWrites()>=2;

            repository.releaseFirst.countDown();
            strictFuture.get(8,TimeUnit.SECONDS);
            boolean staleRejected=false;
            try{
                staleTicket.completion.get(8,TimeUnit.SECONDS);
            }catch(ExecutionException expected){
                staleRejected=expected.getCause() instanceof
                    java.util.concurrent.RejectedExecutionException;
            }
            oldDeferredCaptureRejected=
                staleRejected&&repository.calls.get()==1;

            FilePlayerRepository reader=
                new FilePlayerRepository(
                    username->dir.resolve(username+".properties")
                );
            PlayerSnapshot onDisk=reader.load(
                LocalAccountProfiles.PRIMARY
            ).get();
            String intentKey="extension."+
                MailboxPreparedClaimJournal.NAMESPACE+".key";
            preparedStrictWrittenAfterOld=
                prepared.get().values().equals(onDisk.values())&&
                onDisk.value(intentKey)!=null&&
                repository.calls.get()==1;

            // Captures after the barrier may write normally; this
            // fixture's owner state still includes the PREPARED intent.
            world.submitAndWait(
                alice,generation,
                ()->{
                    alice.movement().setRunEnergy(41);
                    world.persistence().checkpointDue(
                        WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS*2L
                    );
                },
                5_000L
            );
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(6);
            while(world.persistence().checkpointWrittenCount()<2L&&
                  System.nanoTime()<deadline)
                Thread.sleep(15L);

            PlayerSnapshot latest=reader.load(
                LocalAccountProfiles.PRIMARY
            ).get();
            laterCheckpointStillWrites=
                world.persistence().checkpointWrittenCount()>=2L&&
                repository.calls.get()==2&&
                onDisk.value(intentKey).equals(
                    latest.value(intentKey)
                );

            WorldPlayer loaded=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(latest,loaded);
            savedPreparedAndUnclaimed=
                MailboxPreparedClaimJournal.inspectPrepared(loaded)!=
                    null&&
                loaded.mailbox().get("g2124:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            noItemCredit=
                loaded.bank().inventorySlots()==0&&
                alice.bank().inventorySlots()==0&&
                alice.mailbox().get("g2124:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            WorldPlayer stranger=new WorldPlayer();
            foreignAccountUnaffected=
                stranger.mailbox().size()==0&&
                stranger.bank().inventorySlots()==0&&
                !Files.exists(dir.resolve("g2124-stranger.properties"));

            // An injected pre-rename strict failure completes exceptionally
            // and cannot return a false durability receipt.
            StrictDurablePlayerSnapshotWriter failing=
                new StrictDurablePlayerSnapshotWriter(
                    username->dir.resolve(username+".properties"),
                    phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            throw new IOException(
                                "injected pre-replace failure"
                            );
                    }
                );
            CompletableFuture<
                StrictDurablePlayerSnapshotWriter.Receipt
            > failed=world.persistence().submitPreparedStrictBarrier(
                alice,generation,prepared.get(),failing
            );
            boolean rejected=false;
            try{
                failed.get(8,TimeUnit.SECONDS);
            }catch(ExecutionException expected){
                rejected=expected.getCause() instanceof IOException;
            }
            failedStrictNoReceipt=
                rejected&&reader.load(LocalAccountProfiles.PRIMARY)
                    .get().values().equals(latest.values());

            boolean unregistered=world.unregisterPlayer(
                alice,generation
            );
            boolean oldDenied=false;
            try{
                world.persistence().submitPreparedStrictBarrier(
                    alice,generation,prepared.get(),strict
                );
            }catch(IllegalStateException expected){
                oldDenied=true;
            }
            ownerGenerationFence=unregistered&&oldDenied;

            boundedWorkerQueue=
                WorldPlayerPersistence.MAX_PENDING_WRITES==64&&
                world.persistence().queuedWrites()==0;

            world.persistence().close();
            boolean afterClose=false;
            try{
                world.persistence().submitPreparedStrictBarrier(
                    alice,generation,prepared.get(),strict
                );
            }catch(IllegalStateException |
                   java.util.concurrent.RejectedExecutionException expected){
                afterClose=true;
            }
            terminalAdmissionDenied=afterClose;

            noSettlementAuthority=
                StrictDurablePlayerSnapshotWriter.AUTHORITY
                    .contains("STRICT_FILE_BOUNDARY_ONLY")&&
                MailboxPreparedClaimJournal.STATE.equals(
                    "PREPARED_NO_GRANT"
                )&&
                "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY".equals(
                    MailboxInventoryClaimPreflight.AUTHORITY
                );
        }finally{
            repository.releaseFirst.countDown();
            if(alice.registered())
                world.unregisterPlayer(alice);
            world.close();
            try(Stream<Path> entries=Files.walk(dir)){
                for(Path file:entries.sorted(
                        java.util.Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }

        System.out.println(
            "G2124_ORDERED_PREPARED_SAVE_DIAGNOSTICS"+
            " oldWorkerAheadOfBarrier="+oldWorkerAheadOfBarrier+
            " preparedStrictWrittenAfterOld="+
                preparedStrictWrittenAfterOld+
            " oldDeferredCaptureRejected="+
                oldDeferredCaptureRejected+
            " laterCheckpointStillWrites="+laterCheckpointStillWrites+
            " savedPreparedAndUnclaimed="+savedPreparedAndUnclaimed+
            " noItemCredit="+noItemCredit+
            " ownerGenerationFence="+ownerGenerationFence+
            " failedStrictNoReceipt="+failedStrictNoReceipt+
            " terminalAdmissionDenied="+terminalAdmissionDenied+
            " boundedWorkerQueue="+boundedWorkerQueue+
            " noSettlementAuthority="+noSettlementAuthority+
            " foreignAccountUnaffected="+foreignAccountUnaffected
        );
        if(!(oldWorkerAheadOfBarrier&&preparedStrictWrittenAfterOld&&
             oldDeferredCaptureRejected&&laterCheckpointStillWrites&&
             savedPreparedAndUnclaimed&&noItemCredit&&
             ownerGenerationFence&&failedStrictNoReceipt&&
             terminalAdmissionDenied&&boundedWorkerQueue&&
             noSettlementAuthority&&foreignAccountUnaffected))
            throw new AssertionError(
                "G21.24 ordered strict-prepared save acceptance"
            );
        System.out.println(
            "G2124_ORDERED_PREPARED_SAVE_PASS"+
            " oneWorldPersistenceWorker=true"+
            " oldInFlightBeforeBarrier=true"+
            " deferredOldCaptureRejected=true"+
            " preparedStrictSnapshotRoundTrip=true"+
            " laterCheckpointAllowed=true"+
            " noClaimOrItemGrant=true"+
            " staleGenerationDenied=true"+
            " durabilityBeforeClaimNotYetProven=true"
        );
    }

    private static final class BlockingRepository
        implements PlayerRepository{
        final FilePlayerRepository delegate;
        final CountDownLatch firstStarted=new CountDownLatch(1);
        final CountDownLatch releaseFirst=new CountDownLatch(1);
        final AtomicInteger calls=new AtomicInteger();

        BlockingRepository(
            FilePlayerRepository.PathResolver paths
        ){
            delegate=new FilePlayerRepository(paths);
        }

        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            return delegate.load(username);
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            if(calls.incrementAndGet()==1){
                firstStarted.countDown();
                try{
                    if(!releaseFirst.await(8,TimeUnit.SECONDS))
                        throw new IOException(
                            "blocking repository not released"
                        );
                }catch(InterruptedException unexpected){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "blocked repository interrupted",unexpected
                    );
                }
            }
            delegate.save(snapshot);
        }
    }

    private G2124OrderedPreparedAccountSaveIntegrationTest(){}
}

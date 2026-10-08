package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.28: same-worker FIFO drain is a point-in-time read observation.
 * A held reservation prevents newer conflicting writes and cannot
 * be released while the drain is queued/running. No reward is granted.
 */
public final class G2128MailboxReservedFifoDrainIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean inFlightNotPrematurelyQuiescent=false;
        boolean pendingDrainCannotRelease=false;
        boolean duplicatePendingDrainRejected=false;
        boolean newerCapturedSaveBlocked=false;
        boolean autosaveSkippedWhileHeld=false;
        boolean exactPreparedAfterWorkerDrain=false;
        boolean exactReadNotDurabilityReceipt=false;
        boolean exactUnclaimedCancellation=false;
        boolean hypotheticalObservedNeverGrant=false;
        boolean hypotheticalCannotRelease=false;
        boolean divergentObservedFailClosed=false;
        boolean missingAccountFailClosed=false;
        boolean repositoryReadExceptionFailClosed=false;
        boolean repairObservationAllowsSafeRelease=false;
        boolean staleGenerationRejected=false;
        boolean shutdownAdmissionRejected=false;
        boolean noRewardCreditOrClaim=false;

        Path folder=Files.createTempDirectory(
            "g2128-reserved-fifo-"
        );
        BlockingRepository repository=new BlockingRepository(
            name->folder.resolve(name+".properties")
        );
        World world=World.isolatedForTest(60000L,repository);
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(
            owner,LocalAccountProfiles.PRIMARY
        );
        try{
            world.start();
            AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                proposalRef=new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                older=new AtomicReference<>();
            world.submitAndWait(owner,generation,()->{
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    "g2128:reward","Reward","No item credit",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"CUSTOM_LOCALLAB_G2128_FIXTURE"
                ));
                MailboxRewardDeliveryService.Snapshot row=
                    owner.mailbox().get("g2128:reward");
                MailboxPreparedClaimJournal.stageOnly(
                    owner,MailboxPreparedClaimJournal.prepare(
                        owner,row
                    )
                );
                proposalRef.set(MailboxSettlementPostimagePlanner.plan(
                    owner,generation,row
                ));
                older.set(world.persistence().captureDeferredSave(
                    LocalAccountProfiles.PRIMARY,owner,generation,
                    0,"[g2128] ","BEFORE_RESERVATION"
                ));
            },5000L);
            MailboxSettlementPostimagePlanner.Proposal proposal=
                proposalRef.get();

            WorldPlayerPersistence.SaveTicket first=
                world.persistence().submitCapturedWithBackpressure(
                    older.get(),2000L
                );
            if(!repository.firstSaveStarted.await(
                    5,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.28 in-flight repository save never started"
                );

            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            CompletableFuture<
                WorldPlayerPersistence.PreparedDrainObservation
            > drain=world.persistence().drainReservedPreparedAccount(
                token,proposal
            );
            inFlightNotPrematurelyQuiescent=
                token.isActive()&&
                repository.writes.get()==1&&
                !first.completion.isDone()&&
                !drain.isDone();
            pendingDrainCannotRelease=
                rejects(()->token.cancelIfStillUnclaimed())&&
                token.isActive();
            duplicatePendingDrainRejected=rejects(()->{
                world.persistence().drainReservedPreparedAccount(
                    token,proposal
                );
            });

            AtomicReference<WorldPlayerPersistence.CapturedSave>
                capturedAfter=new AtomicReference<>();
            world.submitAndWait(owner,generation,()->{
                capturedAfter.set(
                    world.persistence().captureDeferredSave(
                        LocalAccountProfiles.PRIMARY,owner,generation,
                        0,"[g2128] ","NEWER_CAPTURE_DURING_LOCK"
                    )
                );
            },5000L);
            WorldPlayerPersistence.SaveTicket refused=
                world.persistence().submitCapturedWithBackpressure(
                    capturedAfter.get(),2000L
                );
            newerCapturedSaveBlocked=failedAs(
                refused.completion,
                java.util.concurrent.RejectedExecutionException.class
            )&&repository.writes.get()==1;

            long capturedBefore=
                world.persistence().checkpointCapturedCount();
            world.submitAndWait(owner,generation,()->{
                world.persistence().checkpointDue(
                    WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS
                );
            },5000L);
            autosaveSkippedWhileHeld=
                world.persistence().checkpointCapturedCount()==
                    capturedBefore;

            repository.releaseFirstSave.countDown();
            first.completion.get(8,TimeUnit.SECONDS);
            WorldPlayerPersistence.PreparedDrainObservation observed=
                drain.get(8,TimeUnit.SECONDS);
            exactPreparedAfterWorkerDrain=
                observed.workerQuiescentAtRead&&
                observed.state==
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.EXACT_PREPARED&&
                observed.account.equals(proposal.account)&&
                observed.intentKey.equals(proposal.idempotencyKey)&&
                observed.generation==generation&&
                repository.reads.get()==1;

            exactReadNotDurabilityReceipt=
                !observed.durabilityReceipt&&
                !observed.grantAuthorized;
            exactUnclaimedCancellation=
                token.cancelIfStillUnclaimed()&&
                !token.isActive()&&
                !token.cancelIfStillUnclaimed();

            FilePlayerRepository direct=
                new FilePlayerRepository(
                    name->folder.resolve(name+".properties")
                );
            // Test-only disk fixtures simulate exact hypothetical
            // bytes after an uncertain post-rename force failure.
            WorldPlayerPersistence.PreparedAccountReservation
                hypothetical=world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            direct.save(proposal.hypotheticalPostimage);
            WorldPlayerPersistence.PreparedDrainObservation seen=
                world.persistence().drainReservedPreparedAccount(
                    hypothetical,proposal
                ).get(8,TimeUnit.SECONDS);
            hypotheticalObservedNeverGrant=
                seen.state==
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.EXACT_HYPOTHETICAL&&
                !seen.durabilityReceipt&&!seen.grantAuthorized&&
                owner.bank().inventorySlots()==0&&
                owner.mailbox().get("g2128:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            hypotheticalCannotRelease=
                rejects(()->hypothetical.cancelIfStillUnclaimed())&&
                hypothetical.isActive();
            direct.save(proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedDrainObservation fixed=
                world.persistence().drainReservedPreparedAccount(
                    hypothetical,proposal
                ).get(8,TimeUnit.SECONDS);
            repairObservationAllowsSafeRelease=
                fixed.state==
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.EXACT_PREPARED&&
                hypothetical.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation
                divergent=world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            TreeMap<String,String> changed=
                new TreeMap<>(proposal.preparedPreimage.values());
            changed.put("extension.g2128.unrelated","unexpected");
            direct.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                proposal.account,changed
            ));
            WorldPlayerPersistence.PreparedDrainObservation bad=
                world.persistence().drainReservedPreparedAccount(
                    divergent,proposal
                ).get(8,TimeUnit.SECONDS);
            divergentObservedFailClosed=
                bad.state==
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.DIVERGENT&&
                !bad.grantAuthorized&&
                rejects(()->divergent.cancelIfStillUnclaimed());
            direct.save(proposal.preparedPreimage);
            world.persistence().drainReservedPreparedAccount(
                divergent,proposal
            ).get(8,TimeUnit.SECONDS);
            divergent.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation
                missing=world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            Files.delete(folder.resolve(
                proposal.account+".properties"
            ));
            WorldPlayerPersistence.PreparedDrainObservation gone=
                world.persistence().drainReservedPreparedAccount(
                    missing,proposal
                ).get(8,TimeUnit.SECONDS);
            missingAccountFailClosed=
                gone.state==
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.MISSING_ACCOUNT&&
                rejects(()->missing.cancelIfStillUnclaimed());
            direct.save(proposal.preparedPreimage);
            world.persistence().drainReservedPreparedAccount(
                missing,proposal
            ).get(8,TimeUnit.SECONDS);
            missing.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation
                failed=world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            repository.failNextRead=true;
            CompletableFuture<
                WorldPlayerPersistence.PreparedDrainObservation
            > ioFailure=world.persistence().drainReservedPreparedAccount(
                failed,proposal
            );
            repositoryReadExceptionFailClosed=
                failedAs(ioFailure,IOException.class)&&
                rejects(()->failed.cancelIfStillUnclaimed())&&
                failed.isActive();
            world.persistence().drainReservedPreparedAccount(
                failed,proposal
            ).get(8,TimeUnit.SECONDS);
            failed.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation
                retired=world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            boolean goneOwner=world.unregisterPlayer(
                owner,generation
            );
            staleGenerationRejected=
                goneOwner&&rejects(()->{
                    world.persistence().drainReservedPreparedAccount(
                        retired,proposal
                    );
                });
            long second=world.registerPlayer(
                owner,LocalAccountProfiles.PRIMARY
            );
            staleGenerationRejected &=
                second!=generation&&rejects(()->{
                    world.persistence().drainReservedPreparedAccount(
                        retired,proposal
                    );
                })&&retired.isActive();

            world.persistence().close();
            shutdownAdmissionRejected=rejects(()->{
                world.persistence().drainReservedPreparedAccount(
                    retired,proposal
                );
            });
            noRewardCreditOrClaim=
                owner.bank().inventorySlots()==0&&
                owner.mailbox().get("g2128:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY".equals(
                    MailboxInventoryClaimPreflight.AUTHORITY
                );
        }finally{
            repository.releaseFirstSave.countDown();
            world.close();
            try(Stream<Path> paths=Files.walk(folder)){
                for(Path path:paths.sorted(
                        java.util.Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
        System.out.println(
            "G2128_RESERVED_FIFO_DRAIN_DIAGNOSTICS"+
            " inFlightNotPrematurelyQuiescent="+
                inFlightNotPrematurelyQuiescent+
            " pendingDrainCannotRelease="+pendingDrainCannotRelease+
            " duplicatePendingDrainRejected="+
                duplicatePendingDrainRejected+
            " newerCapturedSaveBlocked="+newerCapturedSaveBlocked+
            " autosaveSkippedWhileHeld="+autosaveSkippedWhileHeld+
            " exactPreparedAfterWorkerDrain="+
                exactPreparedAfterWorkerDrain+
            " exactReadNotDurabilityReceipt="+
                exactReadNotDurabilityReceipt+
            " exactUnclaimedCancellation="+
                exactUnclaimedCancellation+
            " hypotheticalObservedNeverGrant="+
                hypotheticalObservedNeverGrant+
            " hypotheticalCannotRelease="+hypotheticalCannotRelease+
            " divergentObservedFailClosed="+divergentObservedFailClosed+
            " missingAccountFailClosed="+missingAccountFailClosed+
            " repositoryReadExceptionFailClosed="+
                repositoryReadExceptionFailClosed+
            " repairObservationAllowsSafeRelease="+
                repairObservationAllowsSafeRelease+
            " staleGenerationRejected="+staleGenerationRejected+
            " shutdownAdmissionRejected="+shutdownAdmissionRejected+
            " noRewardCreditOrClaim="+noRewardCreditOrClaim
        );
        require(
            inFlightNotPrematurelyQuiescent&&
            pendingDrainCannotRelease&&duplicatePendingDrainRejected&&
            newerCapturedSaveBlocked&&autosaveSkippedWhileHeld&&
            exactPreparedAfterWorkerDrain&&
            exactReadNotDurabilityReceipt&&
            exactUnclaimedCancellation&&
            hypotheticalObservedNeverGrant&&
            hypotheticalCannotRelease&&divergentObservedFailClosed&&
            missingAccountFailClosed&&repositoryReadExceptionFailClosed&&
            repairObservationAllowsSafeRelease&&
            staleGenerationRejected&&shutdownAdmissionRejected&&
            noRewardCreditOrClaim,
            "G21.28 reserved FIFO drain acceptance"
        );
        System.out.println(
            "G2128_RESERVED_FIFO_DRAIN_PASS"+
            " sameExistingWorker=true"+
            " earlierInFlightSaveDrained=true"+
            " reservationPinnedDuringRead=true"+
            " exactDiskClassification=true"+
            " ambiguousHypotheticalNeverReceipt=true"+
            " failedOrMissingReadCannotRelease=true"+
            " ownerGenerationFenced=true"+
            " liveCredit=false"+
            " durableSettlementReceipt=false"
        );
    }

    private static final class BlockingRepository
        implements PlayerRepository{
        private final FilePlayerRepository disk;
        final CountDownLatch firstSaveStarted=new CountDownLatch(1);
        final CountDownLatch releaseFirstSave=new CountDownLatch(1);
        final AtomicInteger writes=new AtomicInteger();
        final AtomicInteger reads=new AtomicInteger();
        volatile boolean failNextRead;

        BlockingRepository(FilePlayerRepository.PathResolver resolver){
            disk=new FilePlayerRepository(resolver);
        }

        @Override public Optional<PlayerSnapshot> load(
            String account
        )throws IOException{
            reads.incrementAndGet();
            if(failNextRead){
                failNextRead=false;
                throw new IOException(
                    "G21.28 injected read failure"
                );
            }
            return disk.load(account);
        }

        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            if(writes.incrementAndGet()==1){
                firstSaveStarted.countDown();
                try{
                    if(!releaseFirstSave.await(8,TimeUnit.SECONDS))
                        throw new IOException(
                            "G21.28 blocked first save timed out"
                        );
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21.28 save interrupted",interrupted
                    );
                }
            }
            disk.save(snapshot);
        }
    }

    private interface Action{void run()throws Exception;}
    private static boolean rejects(Action action){
        try{
            action.run();
            return false;
        }catch(IllegalArgumentException|
                IllegalStateException|
                java.util.concurrent.RejectedExecutionException expected){
            return true;
        }catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }
    private static boolean failedAs(
        CompletableFuture<?> future,Class<? extends Throwable> type
    )throws Exception{
        try{
            future.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException expected){
            return type.isInstance(expected.getCause());
        }
    }
    private static void require(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
    }
    private G2128MailboxReservedFifoDrainIntegrationTest(){}
}

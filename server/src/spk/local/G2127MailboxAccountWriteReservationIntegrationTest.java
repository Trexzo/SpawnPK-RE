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
 * G21.27: account-local PREPARED-only exclusive write reservation.
 * Existing in-flight repository.save may finish; there is no claim/grant.
 * The test never uses this reservation as a durable settlement receipt.
 */
public final class G2127MailboxAccountWriteReservationIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean inFlightNotMisrepresented=false;
        boolean duplicateReservationRejected=false;
        boolean olderDeferredSaveRejected=false;
        boolean newerDeferredSaveRejected=false;
        boolean newerAutosaveNotCaptured=false;
        boolean finalSaveReservationRejected=false;
        boolean strictPreparedWriteRejected=false;
        boolean nonOwnerAccountStillWrites=false;
        boolean explicitUnclaimedRelease=false;
        boolean legitimateCheckpointAfterRelease=false;
        boolean changedPreimageCannotRelease=false;
        boolean recoveredExactPreimageCanRelease=false;
        boolean staleGenerationCannotRelease=false;
        boolean postRetirementAccountStillFenced=false;
        boolean zeroCreditAndClaim=false;
        boolean noAutomaticRelease=false;
        boolean noLiveNativeClaimHandler=false;

        Path dir=Files.createTempDirectory("g2127-write-lock-");
        BlockingRepository repository=new BlockingRepository(
            account->dir.resolve(account+".properties")
        );
        World world=World.isolatedForTest(60000L,repository);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,LocalAccountProfiles.PRIMARY
        );
        try{
            world.start();
            AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                proposalRef=new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                older=new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                player.mailbox().deliver(new RewardDeliveryMessage(
                    "g2127:gift","Prepared gift","No grant",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"CUSTOM_LOCALLAB_G2127_FIXTURE"
                ));
                MailboxRewardDeliveryService.Snapshot selected=
                    player.mailbox().get("g2127:gift");
                MailboxPreparedClaimJournal.Intent intent=
                    MailboxPreparedClaimJournal.prepare(
                        player,selected
                    );
                MailboxPreparedClaimJournal.stageOnly(player,intent);
                proposalRef.set(MailboxSettlementPostimagePlanner.plan(
                    player,generation,selected
                ));
                older.set(world.persistence().captureDeferredSave(
                    LocalAccountProfiles.PRIMARY,player,generation,
                    0,"[g2127] ","BEFORE_EXCLUSIVE_RESERVATION"
                ));
            },5000L);
            MailboxSettlementPostimagePlanner.Proposal proposal=
                proposalRef.get();

            WorldPlayerPersistence.SaveTicket inFlight=
                world.persistence().submitCapturedWithBackpressure(
                    older.get(),2000L
                );
            if(!repository.firstStarted.await(5,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.27 first save never entered file backend"
                );

            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal
                );
            inFlightNotMisrepresented=
                token.isActive()&&
                repository.writes.get()==1&&
                !inFlight.completion.isDone();

            duplicateReservationRejected=rejects(()->{
                world.persistence().reservePreparedAccount(
                    player,generation,proposal
                );
            });

            // A capture created before reservation, admitted afterwards,
            // must never enter the account file writer.
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                newer=new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                newer.set(world.persistence().captureDeferredSave(
                    LocalAccountProfiles.PRIMARY,player,generation,
                    0,"[g2127] ","AFTER_EXCLUSIVE_RESERVATION"
                ));
            },5000L);
            WorldPlayerPersistence.SaveTicket oldTicket=
                world.persistence().submitCapturedWithBackpressure(
                    newer.get(),2000L
                );
            olderDeferredSaveRejected=
                rejectsFuture(oldTicket.completion)&&
                repository.writes.get()==1;

            // A second attempt is also refused even though its
            // immutable snapshot was captured at a newer sequence.
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                later=new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                later.set(world.persistence().captureDeferredSave(
                    LocalAccountProfiles.PRIMARY,player,generation,
                    0,"[g2127] ","NEW_SEQUENCE_STILL_BLOCKED"
                ));
            },5000L);
            WorldPlayerPersistence.SaveTicket lateTicket=
                world.persistence().submitCapturedWithBackpressure(
                    later.get(),2000L
                );
            newerDeferredSaveRejected=
                rejectsFuture(lateTicket.completion)&&
                repository.writes.get()==1;

            long capturedBefore=
                world.persistence().checkpointCapturedCount();
            world.submitAndWait(player,generation,()->{
                world.persistence().checkpointDue(
                    WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS
                );
            },5000L);
            newerAutosaveNotCaptured=
                world.persistence().checkpointCapturedCount()==
                    capturedBefore;

            finalSaveReservationRejected=rejects(()->{
                world.persistence().reserveFinalSaveWithBackpressure(
                    player,generation,0L
                );
            });
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    a->dir.resolve(a+".properties")
                );
            strictPreparedWriteRejected=rejects(()->{
                world.persistence().submitPreparedStrictBarrier(
                    player,generation,proposal.preparedPreimage,strict
                );
            });

            // Another account in the same World/queue is not fenced.
            WorldPlayer other=new WorldPlayer();
            long otherGeneration=world.registerPlayer(
                other,"g2127-independent"
            );
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                otherCapture=new AtomicReference<>();
            world.submitAndWait(other,otherGeneration,()->{
                otherCapture.set(
                    world.persistence().captureDeferredSave(
                        "g2127-independent",other,otherGeneration,
                        0,"[g2127] ","UNRELATED_ACCOUNT"
                    )
                );
            },5000L);
            WorldPlayerPersistence.SaveTicket unrelated=
                world.persistence().submitCapturedWithBackpressure(
                    otherCapture.get(),2000L
                );

            // Existing in-flight file write is not cancelable; its
            // completion precedes the unrelated queued account writer.
            repository.releaseFirst.countDown();
            inFlight.completion.get(8,TimeUnit.SECONDS);
            unrelated.completion.get(8,TimeUnit.SECONDS);
            nonOwnerAccountStillWrites=
                repository.writes.get()==2&&
                Files.exists(
                    dir.resolve("g2127-independent.properties")
                )&&
                token.isActive();

            explicitUnclaimedRelease=
                token.cancelIfStillUnclaimed()&&
                !token.isActive()&&
                !token.cancelIfStillUnclaimed();

            // Normal game saves are not disabled by default.
            world.submitAndWait(player,generation,()->{
                world.persistence().checkpointDue(
                    WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS*2
                );
            },5000L);
            long end=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(7L);
            while(world.persistence().checkpointWrittenCount()<1L&&
                  System.nanoTime()<end)
                Thread.sleep(10L);
            legitimateCheckpointAfterRelease=
                world.persistence().checkpointWrittenCount()>=1L&&
                repository.writes.get()>=3&&
                Files.exists(dir.resolve(
                    LocalAccountProfiles.PRIMARY+".properties"
                ));

            // A changed live inventory cannot silently release an
            // active account reservation, even with the same message ID.
            WorldPlayerPersistence.PreparedAccountReservation next=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal
                );
            int[] ids=new int[BankState.INVENTORY_CAPACITY];
            int[] qty=new int[ids.length];
            java.util.Arrays.fill(ids,-1);
            ids[0]=995;qty[0]=1;
            player.bank().replaceInventorySemantic(ids,qty);
            changedPreimageCannotRelease=
                rejects(()->{next.cancelIfStillUnclaimed();})&&
                next.isActive()&&
                player.mailbox().get("g2127:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;

            java.util.Arrays.fill(ids,-1);
            java.util.Arrays.fill(qty,0);
            player.bank().replaceInventorySemantic(ids,qty);
            recoveredExactPreimageCanRelease=
                next.cancelIfStillUnclaimed()&&!next.isActive();

            WorldPlayerPersistence.PreparedAccountReservation retired=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal
                );
            boolean unregistered=world.unregisterPlayer(
                player,generation
            );
            staleGenerationCannotRelease=
                unregistered&&rejects(()->{
                    retired.cancelIfStillUnclaimed();
                })&&retired.isActive();

            long newGeneration=world.registerPlayer(
                player,LocalAccountProfiles.PRIMARY
            );
            postRetirementAccountStillFenced=
                newGeneration!=generation&&
                rejects(()->{
                    world.persistence().reservePreparedAccount(
                        player,newGeneration,proposal
                    );
                })&&retired.isActive();

            noAutomaticRelease=retired.isActive();
            zeroCreditAndClaim=
                player.bank().inventorySlots()==0&&
                player.mailbox().get("g2127:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            noLiveNativeClaimHandler=
                "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY".equals(
                    MailboxInventoryClaimPreflight.AUTHORITY
                )&&
                WorldPlayerPersistence.MAX_PREPARED_ACCOUNT_RESERVATIONS==
                    32;
        }finally{
            repository.releaseFirst.countDown();
            world.close();
            try(Stream<Path> contents=Files.walk(dir)){
                for(Path path:contents.sorted(
                        java.util.Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2127_MAILBOX_WRITE_RESERVATION_DIAGNOSTICS"+
            " inFlightNotMisrepresented="+inFlightNotMisrepresented+
            " duplicateReservationRejected="+
                duplicateReservationRejected+
            " olderDeferredSaveRejected="+olderDeferredSaveRejected+
            " newerDeferredSaveRejected="+newerDeferredSaveRejected+
            " newerAutosaveNotCaptured="+newerAutosaveNotCaptured+
            " finalSaveReservationRejected="+
                finalSaveReservationRejected+
            " strictPreparedWriteRejected="+strictPreparedWriteRejected+
            " nonOwnerAccountStillWrites="+nonOwnerAccountStillWrites+
            " explicitUnclaimedRelease="+explicitUnclaimedRelease+
            " legitimateCheckpointAfterRelease="+
                legitimateCheckpointAfterRelease+
            " changedPreimageCannotRelease="+
                changedPreimageCannotRelease+
            " recoveredExactPreimageCanRelease="+
                recoveredExactPreimageCanRelease+
            " staleGenerationCannotRelease="+staleGenerationCannotRelease+
            " postRetirementAccountStillFenced="+
                postRetirementAccountStillFenced+
            " zeroCreditAndClaim="+zeroCreditAndClaim+
            " noAutomaticRelease="+noAutomaticRelease+
            " noLiveNativeClaimHandler="+noLiveNativeClaimHandler
        );
        require(
            inFlightNotMisrepresented&&duplicateReservationRejected&&
            olderDeferredSaveRejected&&newerDeferredSaveRejected&&
            newerAutosaveNotCaptured&&finalSaveReservationRejected&&
            strictPreparedWriteRejected&&nonOwnerAccountStillWrites&&
            explicitUnclaimedRelease&&legitimateCheckpointAfterRelease&&
            changedPreimageCannotRelease&&
            recoveredExactPreimageCanRelease&&
            staleGenerationCannotRelease&&
            postRetirementAccountStillFenced&&zeroCreditAndClaim&&
            noAutomaticRelease&&noLiveNativeClaimHandler,
            "G21.27 write reservation gate"
        );
        System.out.println(
            "G2127_MAILBOX_WRITE_RESERVATION_PASS"+
            " optInPerAccountAndGeneration=true"+
            " olderAndNewerOrdinarySavesBlocked=true"+
            " checkpointCaptureSkipped=true"+
            " finalSaveAndStrictWriteBlocked=true"+
            " existingInFlightNotCalledQuiescent=true"+
            " unchangedPreparedExplicitReleaseOnly=true"+
            " generationRetirementFailClosed=true"+
            " liveRewardCredit=false"+
            " durableSettlementReceipt=false"
        );
    }

    private static final class BlockingRepository
        implements PlayerRepository {
        final FilePlayerRepository disk;
        final CountDownLatch firstStarted=new CountDownLatch(1);
        final CountDownLatch releaseFirst=new CountDownLatch(1);
        final AtomicInteger writes=new AtomicInteger();

        BlockingRepository(FilePlayerRepository.PathResolver paths){
            disk=new FilePlayerRepository(paths);
        }

        @Override public Optional<PlayerSnapshot> load(
            String account
        )throws IOException{
            return disk.load(account);
        }

        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            if(writes.incrementAndGet()==1){
                firstStarted.countDown();
                try{
                    if(!releaseFirst.await(8,TimeUnit.SECONDS))
                        throw new IOException(
                            "G21.27 first account writer blocked"
                        );
                }catch(InterruptedException error){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21.27 interrupted writer",error
                    );
                }
            }
            disk.save(snapshot);
        }
    }

    private static boolean rejectsFuture(
        CompletableFuture<Void> future
    )throws Exception{
        try{
            future.get(5,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException expected){
            return expected.getCause() instanceof
                java.util.concurrent.RejectedExecutionException;
        }
    }

    private interface Action { void run()throws Exception; }
    private static boolean rejects(Action action){
        try{action.run();return false;}
        catch(IllegalArgumentException|
              IllegalStateException|
              java.util.concurrent.RejectedExecutionException expected){
            return true;
        }
        catch(Exception unexpected){
            throw new IllegalStateException(unexpected);
        }
    }

    private static void require(boolean condition,String name){
        if(!condition)throw new AssertionError(name);
    }

    private G2127MailboxAccountWriteReservationIntegrationTest(){}
}

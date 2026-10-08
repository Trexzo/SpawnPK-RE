package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

/**
 * G21.36: the actual World persistence save worker, including snapshots
 * captured BEFORE an independent review fence was armed, cannot publish
 * old account bytes once the G21.32 sidecar is visible before replacement.
 *
 * The deterministic before-replace hook only exists in the test fixture;
 * it cannot skip the actual fence guard. No native claim is enabled.
 */
public final class G2136MailboxFencedWorldSaveIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean originalPreparedSaved=false;
        boolean writeEnteredBeforeMarker=false;
        boolean reviewArmedDuringSerialization=false;
        boolean workerWriteVetoed=false;
        boolean noAccountReplacementAfterFence=false;
        boolean rejectedWriteTempCleaned=false;
        boolean earlierCapturedSnapshotDenied=false;
        boolean newWorldSaveDenied=false;
        boolean checkpointRejectedNotWritten=false;
        boolean finalDisconnectSaveDenied=false;
        boolean unrelatedAccountWrites=true;
        boolean unfencedInvalidPreparedRejected=false;
        boolean directForensicFixtureStillPossible=false;
        boolean originalLiveStateNoReward=false;
        boolean noAutomaticGrantOrRelease=false;
        boolean freshWorldLoginRejected=false;

        Path dir=Files.createTempDirectory("g2136-file-world-save-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        CountDownLatch beforeReplace=new CountDownLatch(1);
        CountDownLatch continueReplace=new CountDownLatch(1);
        FilePlayerRepository file=new FilePlayerRepository(
            paths,account->{
                if(!"g2136-alice".equals(account))
                    return;
                beforeReplace.countDown();
                try{
                    if(!continueReplace.await(8,TimeUnit.SECONDS))
                        throw new IOException(
                            "G21.36 held writer not released"
                        );
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21.36 interrupted writer",interrupted
                    );
                }
            }
        );
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(paths);
        final String alice="g2136-alice";
        final String bob="g2136-bob";

        try{
            try(World world=World.isolatedForTest(60000L,file)){
                world.start();
                WorldPlayer player=new WorldPlayer();
                long generation=world.registerPlayer(player,alice);
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    proposalRef=new AtomicReference<>();
                AtomicReference<WorldPlayerPersistence.CapturedSave>
                    earlyRef=new AtomicReference<>();
                world.submitAndWait(player,generation,()->{
                    player.mailbox().deliver(new RewardDeliveryMessage(
                        "g2136:gift","Guarded save","NO_GRANT",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,25)
                        ),"CUSTOM_LOCALLAB_G2136_FIXTURE"
                    ));
                    MailboxRewardDeliveryService.Snapshot selected=
                        player.mailbox().get("g2136:gift");
                    MailboxPreparedClaimJournal.stageOnly(
                        player,MailboxPreparedClaimJournal.prepare(
                            player,selected
                        )
                    );
                    proposalRef.set(
                        MailboxSettlementPostimagePlanner.plan(
                            player,generation,selected
                        )
                    );
                    earlyRef.set(
                        world.persistence().captureDeferredSave(
                            alice,player,generation,0,
                            "[g2136] ","EARLY_PRE_FENCE_CAPTURE"
                        )
                    );
                },5000L);
                MailboxSettlementPostimagePlanner.Proposal proposal=
                    proposalRef.get();

                // An exact PREPARED account already exists; the incoming
                // normal write must never replace it after review is armed.
                file.save(proposal.preparedPreimage);
                byte[] initial=Files.readAllBytes(paths.resolve(alice));
                originalPreparedSaved=
                    MailboxPreparedRestartAdmission.inspect(
                        file.load(alice).get()
                    ).admissionAllowed;

                // This *actual* worker SaveTask passes its first fence
                // check and serializes a temp file before being paused.
                WorldPlayerPersistence.SaveTicket incoming=
                    world.persistence().submitCapturedWithBackpressure(
                        earlyRef.get(),2000L
                    );
                writeEnteredBeforeMarker=
                    beforeReplace.await(8,TimeUnit.SECONDS)&&
                    !incoming.completion.isDone();
                reviewArmedDuringSerialization=
                    fence.arm(proposal).record.matches(proposal);
                continueReplace.countDown();
                workerWriteVetoed=failedWith(
                    incoming,"G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                );
                noAccountReplacementAfterFence=
                    Arrays.equals(initial,
                        Files.readAllBytes(paths.resolve(alice)))&&
                    file.load(alice).get().values().equals(
                        proposal.preparedPreimage.values()
                    );
                rejectedWriteTempCleaned=
                    !Files.exists(dir.resolve(alice+".properties.tmp"));

                // Repeat for a captured account state submitted AFTER
                // durable review is visible; early veto occurs before any
                // serialization, independent of a later owner generation.
                AtomicReference<WorldPlayerPersistence.CapturedSave>
                    delayed=new AtomicReference<>();
                world.submitAndWait(player,generation,()->{
                    delayed.set(
                        world.persistence().captureDeferredSave(
                            alice,player,generation,0,
                            "[g2136] ","DEFERRED_AFTER_FENCE"
                        )
                    );
                },5000L);
                WorldPlayerPersistence.SaveTicket deferred=
                    world.persistence().submitCapturedWithBackpressure(
                        delayed.get(),2000L
                    );
                earlierCapturedSnapshotDenied=failedWith(
                    deferred,"G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                )&&Arrays.equals(initial,
                    Files.readAllBytes(paths.resolve(alice)));

                // A direct normal captureAndSave shares the same guarded
                // WorldPlayerPersistence.write implementation.
                WorldPlayerPersistence.SaveTicket immediate=
                    world.persistence().captureAndSave(
                        alice,player,generation,0,
                        "[g2136] ","FENCED_IMMEDIATE_SAVE"
                    );
                newWorldSaveDenied=failedWith(
                    immediate,"G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                );

                long failedBefore=world.persistence().failedCount();
                long writtenBefore=
                    world.persistence().checkpointWrittenCount();
                world.submitAndWait(player,generation,()->{
                    world.persistence().checkpointDue(
                        WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS
                    );
                },5000L);
                long timeout=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(6);
                while(world.persistence().failedCount()<=failedBefore&&
                      System.nanoTime()<timeout)
                    Thread.sleep(10L);
                checkpointRejectedNotWritten=
                    world.persistence().failedCount()>failedBefore&&
                    world.persistence().checkpointWrittenCount()==
                        writtenBefore&&
                    Arrays.equals(initial,
                        Files.readAllBytes(paths.resolve(alice)));

                WorldPlayerPersistence.FinalSaveReservation reservation=
                    world.persistence().reserveFinalSaveWithBackpressure(
                        player,generation,2000L
                    );
                AtomicReference<WorldPlayerPersistence.CapturedSave>
                    finalCaptured=new AtomicReference<>();
                world.submitAndWait(player,generation,()->{
                    finalCaptured.set(
                        world.persistence().captureDeferredFinalSave(
                            alice,player,generation,0,
                            "[g2136] ","FENCED_FINAL_DISCONNECT"
                        )
                    );
                },5000L);
                WorldPlayerPersistence.SaveTicket finalTicket=
                    reservation.publish(finalCaptured.get());
                finalDisconnectSaveDenied=failedWith(
                    finalTicket,"G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"
                )&&fence.present(alice)&&
                    Arrays.equals(initial,
                        Files.readAllBytes(paths.resolve(alice)));

                // Another account has no review fence: it still uses
                // normal account writes and gets a successful ticket.
                WorldPlayer independent=new WorldPlayer();
                long independentGeneration=
                    world.registerPlayer(independent,bob);
                WorldPlayerPersistence.SaveTicket otherTicket=
                    world.persistence().captureAndSave(
                        bob,independent,independentGeneration,0,
                        "[g2136] ","UNFENCED_ACCOUNT"
                    );
                otherTicket.completion.get(8,TimeUnit.SECONDS);
                unrelatedAccountWrites=
                    Files.exists(paths.resolve(bob))&&
                    file.load(bob).isPresent()&&!fence.present(bob);

                originalLiveStateNoReward=
                    player.bank().inventorySlots()==0&&
                    player.mailbox().get("g2136:gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
                noAutomaticGrantOrRelease=
                    fence.present(alice)&&
                    !fence.inspect(alice).grantAuthorized&&
                    !fence.inspect(alice).replayAuthorized&&
                    !fence.inspect(alice).releaseAuthorized;

                // The ordinary direct Repository.save remains an explicit
                // untrusted fixture/forensic route, not an admitted World
                // writer. It never overrides the review marker.
                file.save(proposal.hypotheticalPostimage);
                directForensicFixtureStillPossible=
                    file.load(alice).get().values().equals(
                        proposal.hypotheticalPostimage.values()
                    )&&fence.present(alice);
                // Restore the exact preimage only through explicit direct
                // forensic test setup. Never "recover" via game logic.
                file.save(proposal.preparedPreimage);
            }

            // A different unfenced account's corrupted PREPARED journal
            // must be rejected by a World-save even without a review fence.
            try(World fresh=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                fresh.start();
                freshWorldLoginRejected=
                    fencedLoadRefused(fresh,alice)&&
                    fresh.persistence().load(bob).isPresent();
            }

            // G21.36 protects direct guarded World writes from a
            // hypothetical credited+CLAIMED postimage even if no marker
            // was armed for that account (negative journal admission).
            try(World isolated=World.isolatedForTest(
                    60000L,new FilePlayerRepository(paths))){
                isolated.start();
                WorldPlayer state=new WorldPlayer();
                long generation=
                    isolated.registerPlayer(state,"g2136-journal");
                AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                    proposal=new AtomicReference<>();
                isolated.submitAndWait(state,generation,()->{
                    state.mailbox().deliver(new RewardDeliveryMessage(
                        "g2136:journal","Unsafe save","NO_GRANT",
                        Collections.singletonList(
                            new RewardDeliveryMessage.Attachment(995,2)
                        ),"CUSTOM_LOCALLAB_G2136_FIXTURE"
                    ));
                    MailboxRewardDeliveryService.Snapshot row=
                        state.mailbox().get("g2136:journal");
                    MailboxPreparedClaimJournal.stageOnly(
                        state,MailboxPreparedClaimJournal.prepare(
                            state,row
                        )
                    );
                    proposal.set(MailboxSettlementPostimagePlanner.plan(
                        state,generation,row
                    ));
                },5000L);
                FilePlayerRepository unsafe=
                    new FilePlayerRepository(paths);
                boolean rejected=false;
                try{
                    unsafe.saveForWorld(
                        proposal.get().hypotheticalPostimage
                    );
                }catch(IOException expected){
                    rejected=expected.getMessage().contains(
                        "G21.36 MAILBOX_WORLD_SAVE_QUARANTINE"
                    );
                }
                unfencedInvalidPreparedRejected=
                    rejected&&!Files.exists(
                        paths.resolve("g2136-journal"));
            }
        }finally{
            continueReplace.countDown();
            try(Stream<Path> files=Files.walk(dir)){
                for(Path path:files.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2136_MAILBOX_WORLD_SAVE_FENCE_DIAGNOSTICS"+
            " preparedPreimageExists="+originalPreparedSaved+
            " workerReachedPreReplace="+writeEnteredBeforeMarker+
            " markerArmedDuringSave="+reviewArmedDuringSerialization+
            " workerTicketRejected="+workerWriteVetoed+
            " noPreimageOverwrite="+noAccountReplacementAfterFence+
            " noTempArtifacts="+rejectedWriteTempCleaned+
            " deferredCaptureRejected="+earlierCapturedSnapshotDenied+
            " laterImmediateSaveRejected="+newWorldSaveDenied+
            " checkpointDenied="+checkpointRejectedNotWritten+
            " finalDisconnectSaveDenied="+finalDisconnectSaveDenied+
            " unrelatedSavePass="+unrelatedAccountWrites+
            " journalPostimageRejected="+unfencedInvalidPreparedRejected+
            " directForensicFixturePreserved="+
                directForensicFixtureStillPossible+
            " noLiveRewardOrClaim="+originalLiveStateNoReward+
            " noGrantOrRelease="+noAutomaticGrantOrRelease+
            " restartedLoginFenced="+freshWorldLoginRejected
        );
        require(
            originalPreparedSaved&&writeEnteredBeforeMarker&&
            reviewArmedDuringSerialization&&workerWriteVetoed&&
            noAccountReplacementAfterFence&&rejectedWriteTempCleaned&&
            earlierCapturedSnapshotDenied&&newWorldSaveDenied&&
            checkpointRejectedNotWritten&&finalDisconnectSaveDenied&&
            unrelatedAccountWrites&&unfencedInvalidPreparedRejected&&
            directForensicFixtureStillPossible&&
            originalLiveStateNoReward&&noAutomaticGrantOrRelease&&
            freshWorldLoginRejected,
            "G21.36 fenced World save acceptance"
        );
        System.out.println(
            "G2136_MAILBOX_FENCED_WORLD_SAVE_PASS"+
            " actualWorldWorkerGuarded=true"+
            " markerArmedDuringSerializationVeto=true"+
            " autosaveAndFinalSaveFailClosed=true"+
            " existingAccountPreserved=true"+
            " unrelatedAccountUnaffected=true"+
            " noRewardGrantOrReplay=true"
        );
    }

    private static boolean failedWith(
        WorldPlayerPersistence.SaveTicket ticket,String marker
    )throws Exception{
        try{
            ticket.completion.get(8,TimeUnit.SECONDS);
            return false;
        }catch(ExecutionException expected){
            return expected.getCause() instanceof IOException&&
                expected.getCause().getMessage()!=null&&
                expected.getCause().getMessage().contains(marker);
        }
    }

    private static boolean fencedLoadRefused(
        World world,String account
    )throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){
            return expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                );
        }
    }

    private static void require(boolean yes,String name){
        if(!yes)throw new AssertionError(name);
    }

    private G2136MailboxFencedWorldSaveIntegrationTest(){}
}

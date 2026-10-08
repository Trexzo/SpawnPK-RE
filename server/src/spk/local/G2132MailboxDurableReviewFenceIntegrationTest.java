package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.32: file-backed negative fence across actual session-loading worker
 * and a fresh World process. No reward or automatic journal repair.
 */
public final class G2132MailboxDurableReviewFenceIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean originalValidPreparedLoad=false;
        boolean noFenceNormalAccountLoads=false;
        boolean strictMarkerReceipt=false;
        boolean parsedRecordMatchesProposal=false;
        boolean preparedAccountNowRejected=false;
        boolean realSessionHydrationRejected=false;
        boolean rawForensicReadUnchanged=false;
        boolean strippedJournalStillRejected=false;
        boolean missingAccountStillRejected=false;
        boolean secondAccountUnaffected=false;
        boolean restartedWorldRejectsAccount=false;
        boolean malformedMarkerStillRejected=false;
        boolean malformedDiagnosticNotTrusted=false;
        boolean preRenameFailureNoMarker=false;
        boolean postRenameFailureMarkerBlocks=false;
        boolean duplicateFenceWriteDenied=false;
        boolean noOrphanTempFiles=false;
        boolean noLiveInventoryOrClaim=false;
        boolean explicitNoGrantAuthority=false;

        Path dir=Files.createTempDirectory(
            "g2132-durable-review-fence-"
        );
        FilePlayerRepository.PathResolver resolver=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        MailboxDurableReviewFence fence=
            new MailboxDurableReviewFence(resolver);
        final String alice="g2132-alice";
        final String bob="g2132-bob";
        final String alt="g2132-alt";
        final String ambiguous="g2132-ambiguous";

        try{
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                MailboxSettlementPostimagePlanner.Proposal proposal=
                    prepare(world,alice,"g2132:reward");
                MailboxSettlementPostimagePlanner.Proposal alternative=
                    prepare(world,alt,"g2132:alt");
                MailboxSettlementPostimagePlanner.Proposal afterFailure=
                    prepare(world,ambiguous,"g2132:ambiguous");

                repository.save(proposal.preparedPreimage);
                originalValidPreparedLoad=
                    world.persistence().load(alice).get()
                        .values().equals(
                            proposal.preparedPreimage.values()
                        );

                WorldPlayer other=new WorldPlayer();
                other.markRegistered(bob);
                PlayerSnapshot otherSnapshot=
                    PlayerSnapshotCodec.capture(bob,other);
                repository.save(otherSnapshot);
                noFenceNormalAccountLoads=
                    world.persistence().load(bob).get()
                        .values().equals(otherSnapshot.values())&&
                    !fence.present(bob);

                MailboxDurableReviewFence.Receipt receipt=
                    fence.arm(proposal);
                strictMarkerReceipt=
                    receipt.fenceFile.equals(fence.fencePath(alice))&&
                    receipt.authority.equals(
                        MailboxDurableReviewFence.AUTHORITY
                    )&&
                    !receipt.grantAuthorized&&
                    !receipt.replayAuthorized&&
                    fence.present(alice);
                MailboxDurableReviewFence.Record parsed=
                    fence.inspect(alice);
                parsedRecordMatchesProposal=
                    parsed.matches(proposal)&&
                    parsed.state.equals(MailboxDurableReviewFence.STATE)&&
                    !parsed.grantAuthorized&&!parsed.replayAuthorized&&
                    !parsed.releaseAuthorized;

                preparedAccountNowRejected=denied(
                    world,alice
                );
                WorldPlayer fresh=new WorldPlayer();
                LocalAccountLifecycle.LoadResult rejected=
                    LocalAccountLifecycle.load(
                        new LocalAccountLifecycle.Selection(alice,true),
                        fresh,world.persistence(),item->true,
                        "[g2132-session] "
                    );
                realSessionHydrationRejected=
                    rejected.failed&&
                    fresh.bank().inventorySlots()==0&&
                    fresh.mailbox().size()==0;

                rawForensicReadUnchanged=
                    world.persistence()
                        .observeUntrustedMailboxAccount(alice)
                        .get().values().equals(
                            proposal.preparedPreimage.values()
                        );
                // Even losing the G21.22 journal must not resurrect a
                // claim: the independent fence is still present.
                repository.save(new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,alice,
                    Collections.emptyMap()
                ));
                strippedJournalStillRejected=denied(world,alice);
                Files.delete(resolver.resolve(alice));
                missingAccountStillRejected=denied(world,alice)&&
                    !repository.load(alice).isPresent();
                secondAccountUnaffected=
                    world.persistence().load(bob).get()
                        .values().equals(otherSnapshot.values());

                MailboxDurableReviewFence beforeMove=
                    new MailboxDurableReviewFence(
                        resolver,phase->{
                            if(phase==MailboxDurableReviewFence.Phase
                                    .BEFORE_ATOMIC_REPLACE)
                                throw new IOException(
                                    "G21.32 injected pre-rename failure"
                                );
                        }
                    );
                boolean beforeFailed=false;
                try{
                    beforeMove.arm(alternative);
                }catch(IOException expected){
                    beforeFailed=true;
                }
                preRenameFailureNoMarker=
                    beforeFailed&&!fence.present(alt)&&
                    !Files.exists(fence.fencePath(alt));

                MailboxDurableReviewFence afterMove=
                    new MailboxDurableReviewFence(
                        resolver,phase->{
                            if(phase==MailboxDurableReviewFence.Phase
                                    .BEFORE_DIRECTORY_FORCE)
                                throw new IOException(
                                    "G21.32 injected ambiguous outcome"
                                );
                        }
                    );
                boolean unconfirmed=false;
                try{
                    afterMove.arm(afterFailure);
                }catch(MailboxDurableReviewFence
                        .UnconfirmedFenceException expected){
                    unconfirmed=true;
                }
                postRenameFailureMarkerBlocks=
                    unconfirmed&&fence.present(ambiguous)&&
                    denied(world,ambiguous)&&
                    fence.inspect(ambiguous).matches(afterFailure);

                boolean refusedDuplicate=false;
                try{
                    fence.arm(proposal);
                }catch(IOException expected){
                    refusedDuplicate=true;
                }
                duplicateFenceWriteDenied=
                    refusedDuplicate&&fence.present(alice);

                try(Stream<Path> entries=Files.list(dir)){
                    noOrphanTempFiles=
                        entries.noneMatch(path->path.getFileName()
                            .toString().contains(".g2132-")&&
                            path.getFileName().toString().endsWith(".tmp"));
                }

                noLiveInventoryOrClaim=true;
                for(WorldPlayer player:world.players().all()){
                    if(player.username().startsWith("g2132-")){
                        noLiveInventoryOrClaim &=
                            player.bank().inventorySlots()==0;
                        for(MailboxRewardDeliveryService.Snapshot mail:
                                player.mailbox().all()){
                            noLiveInventoryOrClaim &=
                                mail.claimState==
                                    MailboxRewardDeliveryService
                                        .ClaimState.UNCLAIMED;
                        }
                    }
                }
                explicitNoGrantAuthority=
                    !receipt.grantAuthorized&&
                    !receipt.replayAuthorized&&
                    !parsed.grantAuthorized&&
                    !parsed.replayAuthorized;
            }

            // Fresh World and worker, no old G21.27 in-memory token.
            try(World restarted=World.isolatedForTest(
                    60000L,new FilePlayerRepository(resolver))){
                restarted.start();
                restartedWorldRejectsAccount=
                    denied(restarted,alice)&&
                    denied(restarted,ambiguous)&&
                    restarted.persistence().load(bob).isPresent();
                Files.write(fence.fencePath(alice),
                    "broken-marker".getBytes(StandardCharsets.US_ASCII));
                malformedMarkerStillRejected=
                    fence.present(alice)&&denied(restarted,alice);
                boolean diagnosticRefused=false;
                try{
                    fence.inspect(alice);
                }catch(IOException expected){
                    diagnosticRefused=true;
                }
                malformedDiagnosticNotTrusted=diagnosticRefused;
            }
        }finally{
            try(Stream<Path> entries=Files.walk(dir)){
                for(Path path:entries.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2132_MAILBOX_DURABLE_REVIEW_DIAGNOSTICS"+
            " priorPreparedLoads="+originalValidPreparedLoad+
            " normalAccountLoads="+noFenceNormalAccountLoads+
            " strictFenceReceipt="+strictMarkerReceipt+
            " markerBoundToProposal="+parsedRecordMatchesProposal+
            " preparedNowRejected="+preparedAccountNowRejected+
            " sessionHydrationBlocked="+realSessionHydrationRejected+
            " rawForensicReadPreserved="+rawForensicReadUnchanged+
            " missingJournalStillDenied="+strippedJournalStillRejected+
            " missingAccountStillDenied="+missingAccountStillRejected+
            " secondAccountUnaffected="+secondAccountUnaffected+
            " restartRejects="+restartedWorldRejectsAccount+
            " malformedMarkerStillDenied="+malformedMarkerStillRejected+
            " malformedDiagnosticDenied="+malformedDiagnosticNotTrusted+
            " preRenameNoMarker="+preRenameFailureNoMarker+
            " uncertainPostRenameStillFenced="+
                postRenameFailureMarkerBlocks+
            " duplicateArmDenied="+duplicateFenceWriteDenied+
            " noOrphanTemps="+noOrphanTempFiles+
            " liveStateUnchanged="+noLiveInventoryOrClaim+
            " noGrantAuthority="+explicitNoGrantAuthority
        );
        require(
            originalValidPreparedLoad&&noFenceNormalAccountLoads&&
            strictMarkerReceipt&&parsedRecordMatchesProposal&&
            preparedAccountNowRejected&&realSessionHydrationRejected&&
            rawForensicReadUnchanged&&strippedJournalStillRejected&&
            missingAccountStillRejected&&secondAccountUnaffected&&
            restartedWorldRejectsAccount&&malformedMarkerStillRejected&&
            malformedDiagnosticNotTrusted&&preRenameFailureNoMarker&&
            postRenameFailureMarkerBlocks&&duplicateFenceWriteDenied&&
            noOrphanTempFiles&&noLiveInventoryOrClaim&&
            explicitNoGrantAuthority,
            "G21.32 durable negative review fence"
        );
        System.out.println(
            "G2132_MAILBOX_DURABLE_NEGATIVE_FENCE_PASS"+
            " independentMarkerRestartSurvives=true"+
            " missingJournalOrAccountCannotBypass=true"+
            " priorForensicObservationUnaffected=true"+
            " noNonAtomicFallback=true liveClaim=false"+
            " autoRelease=false replay=false"
        );
    }

    private static MailboxSettlementPostimagePlanner.Proposal prepare(
        World world,String username,String messageId
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,username);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal> out=
            new AtomicReference<>();
        world.submitAndWait(player,generation,()->{
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"Durable-fence test","No grant",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)
                ),"CUSTOM_LOCALLAB_G2132_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot row=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(
                player,MailboxPreparedClaimJournal.prepare(player,row)
            );
            out.set(MailboxSettlementPostimagePlanner.plan(
                player,generation,row
            ));
        },5000L);
        return out.get();
    }

    private static boolean denied(
        World world,String account
    )throws Exception{
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException veto){
            return veto.getMessage()!=null&&
                veto.getMessage().contains(
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"
                );
        }
    }

    private static void require(boolean yes,String name){
        if(!yes)throw new AssertionError(name);
    }

    private G2132MailboxDurableReviewFenceIntegrationTest(){}
}

package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.68: actual World-tick single-use candidate from G21.67 evidence. */
public final class G2168MailboxWorldTerminalCandidateIntegrationTest {
    private static final class Seed {
        final WorldPlayer live;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(
            WorldPlayer live,long generation,
            MailboxSettlementPostimagePlanner.Proposal proposal
        ){
            this.live=live;
            this.generation=generation;
            this.proposal=proposal;
            this.terminal=MailboxAtomicTerminalSnapshot.compose(proposal);
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2168-world-candidate-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean detachedProjectionExact=false;
        boolean strictTerminalReceiptBound=false;
        boolean issuedOnRealWorldTick=false;
        boolean liveOwnerUnchanged=false;
        boolean replayCandidateRejected=false;
        boolean candidateCannotRelease=false;
        boolean noDiskMutationAfterCandidate=false;
        boolean restartQuarantineRetained=false;
        boolean noReceiptCannotStage=false;
        boolean fakeObservationRejected=false;
        boolean foreignObservationRejected=false;
        boolean negativeObservationInvalidates=false;
        boolean staleLivePreimageRejected=false;
        boolean staleGenerationRejected=false;
        boolean secondAccountIndependent=false;
        boolean markerAndTempClean=false;

        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();

            Seed first=seed(world,"g2168-first");
            writer.saveStrict(first.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation firstToken=
                world.persistence().reservePreparedAccount(
                    first.live,first.generation,first.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence receipt=
                world.persistence().publishReservedTerminalStrictly(
                    firstToken,first.proposal,first.terminal,writer
                ).get(8,TimeUnit.SECONDS);
            byte[] before=Files.readAllBytes(
                paths.resolve(first.proposal.account));

            // Neither a null/missing observation nor a fabricated
            // package-scoped copy can authorize candidate issuance:
            // G21.67 must have actually produced this exact object.
            noReceiptCannotStage=rejects(()->world.persistence()
                .stageReservedTerminalWorldCandidate(
                    firstToken,first.proposal,first.terminal,null));
            WorldPlayerPersistence.TerminalReconciliationEvidence fake=
                new WorldPlayerPersistence.TerminalReconciliationEvidence(
                    WorldPlayerPersistence.TerminalReconciliationEvidence
                        .State.EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT,
                    firstToken
                );
            fakeObservationRejected=rejects(()->world.persistence()
                .stageReservedTerminalWorldCandidate(
                    firstToken,first.proposal,first.terminal,fake));

            WorldPlayerPersistence.TerminalReconciliationEvidence valid=
                reconcile(world,first,firstToken,receipt);
            strictTerminalReceiptBound=valid.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT;

            // An independently staged candidate has exact inventory,
            // Mailbox CLAIMED state and terminal extension namespace,
            // but can NEVER touch the original registered WorldPlayer.
            MailboxTerminalLiveTransitionCandidate detached=
                MailboxTerminalLiveTransitionCandidate.stageDetached(
                    first.proposal,first.terminal);
            detachedProjectionExact=
                detached.projectedTerminal.values().equals(
                    first.terminal.values())&&
                detached.projectedClaimed&&
                detached.projectedInventoryMatchesIntent&&
                detached.terminalSnapshotSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(first.terminal))&&
                !detached.liveApplied&&!detached.grantAuthorized;

            MailboxTerminalLiveTransitionCandidate issued=
                world.persistence().stageReservedTerminalWorldCandidate(
                    firstToken,first.proposal,first.terminal,valid)
                    .get(8,TimeUnit.SECONDS);
            issuedOnRealWorldTick=
                issued!=null&&issued.account.equals(first.proposal.account)&&
                issued.intentKey.equals(first.proposal.idempotencyKey)&&
                issued.generation==first.generation&&
                issued.projectedTerminal.values().equals(
                    first.terminal.values())&&
                !issued.liveApplied&&!issued.grantAuthorized&&
                !issued.replayAuthorized&&!issued.releaseAuthorized&&
                !issued.rollbackAuthorized&&!issued.clientAckAuthorized;

            liveOwnerUnchanged=
                PlayerSnapshotCodec.capture(first.proposal.account,
                    first.live).values().equals(
                    first.proposal.preparedPreimage.values())&&
                first.live.bank().inventorySlots()==0&&
                first.live.mailbox().get(first.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            replayCandidateRejected=rejects(()->world.persistence()
                .stageReservedTerminalWorldCandidate(
                    firstToken,first.proposal,first.terminal,valid));
            candidateCannotRelease=firstToken.isActive()&&
                rejects(()->firstToken.cancelIfStillUnclaimed());
            noDiskMutationAfterCandidate=Arrays.equals(before,
                Files.readAllBytes(paths.resolve(first.proposal.account)));
            try{
                world.persistence().load(first.proposal.account);
            }catch(IOException refusal){
                restartQuarantineRetained=refusal.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }

            Seed second=seed(world,"g2168-second");
            writer.saveStrict(second.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation secondToken=
                world.persistence().reservePreparedAccount(
                    second.live,second.generation,second.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence secondReceipt=
                world.persistence().publishReservedTerminalStrictly(
                    secondToken,second.proposal,second.terminal,writer)
                    .get(8,TimeUnit.SECONDS);
            WorldPlayerPersistence.TerminalReconciliationEvidence secondProof=
                reconcile(world,second,secondToken,secondReceipt);
            foreignObservationRejected=rejects(()->world.persistence()
                .stageReservedTerminalWorldCandidate(
                    secondToken,second.proposal,second.terminal,valid))&&
                rejects(()->world.persistence()
                    .stageReservedTerminalWorldCandidate(
                        firstToken,first.proposal,first.terminal,secondProof));

            // A later negative file observation invalidates the prior
            // matching receipt evidence while leaving the token held.
            final Path secondFile=paths.resolve(second.proposal.account);
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(secondFile,()->{
                    new MailboxStrictUncertainFence(paths)
                        .armInsidePublicationLock(
                            second.proposal.account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    second.proposal.preparedPreimage));
                    return null;
                });
            WorldPlayerPersistence.TerminalReconciliationEvidence invalidated=
                reconcile(world,second,secondToken,secondReceipt);
            negativeObservationInvalidates=invalidated.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.NEGATIVE_FENCE_QUARANTINE&&
                rejects(()->world.persistence()
                    .stageReservedTerminalWorldCandidate(
                        secondToken,second.proposal,second.terminal,
                        secondProof))&&secondToken.isActive();

            Seed moved=seed(world,"g2168-movement");
            writer.saveStrict(moved.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation movedToken=
                world.persistence().reservePreparedAccount(
                    moved.live,moved.generation,moved.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence movedReceipt=
                world.persistence().publishReservedTerminalStrictly(
                    movedToken,moved.proposal,moved.terminal,writer)
                    .get(8,TimeUnit.SECONDS);
            WorldPlayerPersistence.TerminalReconciliationEvidence movedProof=
                reconcile(world,moved,movedToken,movedReceipt);
            world.submitAndWait(moved.live,moved.generation,()->{
                moved.live.movement().setRunEnergy(37);
            },5000L);
            staleLivePreimageRejected=rejects(()->world.persistence()
                .stageReservedTerminalWorldCandidate(
                    movedToken,moved.proposal,moved.terminal,movedProof))&&
                movedToken.isActive()&&moved.live.bank().inventorySlots()==0;

            Seed retired=seed(world,"g2168-retired");
            writer.saveStrict(retired.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation retiredToken=
                world.persistence().reservePreparedAccount(
                    retired.live,retired.generation,retired.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence retiredReceipt=
                world.persistence().publishReservedTerminalStrictly(
                    retiredToken,retired.proposal,retired.terminal,writer)
                    .get(8,TimeUnit.SECONDS);
            WorldPlayerPersistence.TerminalReconciliationEvidence retiredProof=
                reconcile(world,retired,retiredToken,retiredReceipt);
            boolean unregistered=world.unregisterPlayer(
                retired.live,retired.generation);
            staleGenerationRejected=unregistered&&
                rejects(()->world.persistence()
                    .stageReservedTerminalWorldCandidate(
                        retiredToken,retired.proposal,
                        retired.terminal,retiredProof));

            // No per-account terminal fence may freeze an ordinary
            // independent account.
            WorldPlayer independent=new WorldPlayer();
            long independentGeneration=world.registerPlayer(
                independent,"g2168-normal");
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                captured=new AtomicReference<>();
            world.submitAndWait(
                independent,independentGeneration,()->{
                    captured.set(world.persistence().captureDeferredSave(
                        "g2168-normal",independent,independentGeneration,
                        0,"[g2168] ","INDEPENDENT_WORLD_SAVE"));
                },5000L);
            world.persistence().submitCapturedWithBackpressure(
                captured.get(),2000L).completion.get(8,TimeUnit.SECONDS);
            secondAccountIndependent=
                repo.load("g2168-normal").isPresent()&&
                firstToken.isActive()&&secondToken.isActive()&&
                movedToken.isActive()&&retiredToken.isActive()&&
                second.live.bank().inventorySlots()==0;
            try(Stream<Path> files=Files.walk(dir)){
                markerAndTempClean=files.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0&&
                    !new MailboxStrictWriteIntentFence(paths)
                        .present(first.proposal.account)&&
                    !new MailboxStrictUncertainFence(paths)
                        .present(first.proposal.account);
            }
        }finally{
            try(Stream<Path> files=Files.walk(dir)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }

        System.out.println("G2168_WORLD_TERMINAL_CANDIDATE_DIAGNOSTICS"+
            " detachedProjectionExact="+detachedProjectionExact+
            " strictTerminalReceiptBound="+strictTerminalReceiptBound+
            " issuedOnRealWorldTick="+issuedOnRealWorldTick+
            " liveOwnerUnchanged="+liveOwnerUnchanged+
            " replayCandidateRejected="+replayCandidateRejected+
            " candidateCannotRelease="+candidateCannotRelease+
            " noDiskMutationAfterCandidate="+noDiskMutationAfterCandidate+
            " restartQuarantineRetained="+restartQuarantineRetained+
            " noReceiptCannotStage="+noReceiptCannotStage+
            " fakeObservationRejected="+fakeObservationRejected+
            " foreignObservationRejected="+foreignObservationRejected+
            " negativeObservationInvalidates="+negativeObservationInvalidates+
            " staleLivePreimageRejected="+staleLivePreimageRejected+
            " staleGenerationRejected="+staleGenerationRejected+
            " secondAccountIndependent="+secondAccountIndependent+
            " markerAndTempClean="+markerAndTempClean);
        if(!(detachedProjectionExact&&strictTerminalReceiptBound&&
             issuedOnRealWorldTick&&liveOwnerUnchanged&&
             replayCandidateRejected&&candidateCannotRelease&&
             noDiskMutationAfterCandidate&&restartQuarantineRetained&&
             noReceiptCannotStage&&fakeObservationRejected&&
             foreignObservationRejected&&negativeObservationInvalidates&&
             staleLivePreimageRejected&&staleGenerationRejected&&
             secondAccountIndependent&&markerAndTempClean))
            throw new AssertionError(
                "G21.68 single-use NO_GRANT World candidate");
        System.out.println("G2168_WORLD_TERMINAL_CANDIDATE_PASS"+
            " worldTick=true singleUse=true"+
            " grant=false apply=false replay=false release=false");
    }

    private static WorldPlayerPersistence.TerminalReconciliationEvidence
        reconcile(
            World world,Seed seed,
            WorldPlayerPersistence.PreparedAccountReservation token,
            WorldPlayerPersistence.TerminalPublicationEvidence receipt
        )throws Exception{
        return world.persistence().reconcileReservedTerminalReadOnly(
            token,seed.proposal,seed.terminal,receipt)
            .get(8,TimeUnit.SECONDS);
    }

    private static Seed seed(World world,String account)throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal> p=
            new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            String message=account+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"World-owned transition candidate","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2168_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            p.set(MailboxSettlementPostimagePlanner.plan(
                owner,generation,row));
        },5000L);
        return new Seed(owner,generation,p.get());
    }

    private static boolean rejects(Runnable action){
        try{action.run();return false;}
        catch(IllegalArgumentException|IllegalStateException|
              NullPointerException expected){
            return true;
        }
    }
    private G2168MailboxWorldTerminalCandidateIntegrationTest(){}
}

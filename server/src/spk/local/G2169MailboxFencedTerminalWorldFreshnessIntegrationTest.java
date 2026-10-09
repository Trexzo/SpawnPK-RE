package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.69: one-use real-world-tick + exact terminal-file attestation
 * INSIDE the G21.39 cooperating publication critical section.
 * No claim, inventory mutation, replay, release or session admission.
 */
public final class G2169MailboxFencedTerminalWorldFreshnessIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        final WorldPlayerPersistence.PreparedAccountReservation token;
        final MailboxTerminalLiveTransitionCandidate candidate;

        Seed(WorldPlayer owner,long generation,
             MailboxSettlementPostimagePlanner.Proposal proposal,
             PlayerSnapshot terminal,
             WorldPlayerPersistence.PreparedAccountReservation token,
             MailboxTerminalLiveTransitionCandidate candidate){
            this.owner=owner;this.generation=generation;
            this.proposal=proposal;this.terminal=terminal;
            this.token=token;this.candidate=candidate;
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2169-fenced-world-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        boolean lockBlocksFifoObservation=false;
        boolean duplicatePendingRejected=false;
        boolean exactWorldFreshnessAttested=false;
        boolean neverAuthorizesGrant=false;
        boolean singleUseAfterSuccess=false;
        boolean bytesUntouched=false;
        boolean livePreparedUnchanged=false;
        boolean restartRemainsQuarantined=false;
        boolean candidateReleaseStillDenied=false;
        boolean independentAccountUnaffected=false;
        boolean forgedCandidateDenied=false;
        boolean negativeMarkerDenied=false;
        boolean oneShotFailureRemainsClosed=false;
        boolean wrongDiskDenied=false;
        boolean staleLivePlayerDenied=false;
        boolean oldObservationInvalidated=false;
        boolean retiredOwnerDenied=false;
        boolean noTempOrLeaseLeaks=false;

        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();

            Seed positive=seed(world,writer,"g2169-positive");
            byte[] before=Files.readAllBytes(
                paths.resolve(positive.proposal.account));
            CountDownLatch acquired=new CountDownLatch(1);
            CountDownLatch release=new CountDownLatch(1);
            AtomicReference<Throwable> holderError=new AtomicReference<>();
            Path positivePath=paths.resolve(positive.proposal.account);
            Thread holder=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(positivePath,()->{
                            acquired.countDown();
                            try{
                                if(!release.await(7L,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.69 holder fixture timeout");
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.69 holder interrupted",
                                    interrupted);
                            }
                            return null;
                        });
                }catch(Throwable problem){holderError.set(problem);}
            },"g2169-cooperating-lock-holder");
            holder.start();
            try{
                if(!acquired.await(5L,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.69 fixture failed to acquire file lock");
                CompletableFuture<
                    WorldPlayerPersistence.TerminalWorldFreshnessEvidence
                > pending=world.persistence()
                    .attestReservedTerminalWorldFreshness(
                        positive.token,positive.proposal,
                        positive.candidate);
                lockBlocksFifoObservation=
                    !pending.isDone()&&positive.token.isActive();
                duplicatePendingRejected=rejects(()->world.persistence()
                    .attestReservedTerminalWorldFreshness(
                        positive.token,positive.proposal,
                        positive.candidate))&&!pending.isDone();

                // The account LOCAL publication lock never becomes a
                // process-global mutex. An independent account can save
                // even while the fenced task waits on the other lock.
                WorldPlayer independent=new WorldPlayer();
                long independentGen=world.registerPlayer(
                    independent,"g2169-independent");
                PlayerSnapshot independentSnapshot=
                    PlayerSnapshotCodec.capture(
                        "g2169-independent",independent);
                repo.saveForWorld(independentSnapshot);
                independentAccountUnaffected=
                    repo.load("g2169-independent").isPresent()&&
                    independent.accepts(independentGen);

                release.countDown();
                WorldPlayerPersistence.TerminalWorldFreshnessEvidence proof=
                    pending.get(8L,TimeUnit.SECONDS);
                exactWorldFreshnessAttested=
                    proof.exactDiskBeforeAndAfterWorldTick&&
                    proof.ownerPreparedOnWorldTick&&
                    proof.publicationLockReleasedOnReturn&&
                    proof.account.equals(positive.proposal.account)&&
                    proof.intentKey.equals(
                        positive.proposal.idempotencyKey)&&
                    proof.ownerGeneration==positive.generation&&
                    proof.terminalSha256.equals(
                        positive.candidate.terminalSnapshotSha256);
                neverAuthorizesGrant=
                    !proof.durabilityConfirmed&&!proof.liveApplied&&
                    !proof.transactionCommitted&&
                    !proof.grantAuthorized&&!proof.replayAuthorized&&
                    !proof.rollbackAuthorized&&!proof.releaseAuthorized&&
                    !proof.clientAckAuthorized;
            }finally{
                release.countDown();
                holder.join(7000L);
            }
            if(holder.isAlive()||holderError.get()!=null)
                throw new AssertionError(
                    "G21.69 publication lock fixture unresolved",
                    holderError.get());
            singleUseAfterSuccess=rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    positive.token,positive.proposal,
                    positive.candidate));
            candidateReleaseStillDenied=positive.token.isActive()&&
                rejects(()->positive.token.cancelIfStillUnclaimed());
            bytesUntouched=Arrays.equals(before,Files.readAllBytes(
                paths.resolve(positive.proposal.account)));
            livePreparedUnchanged=PlayerSnapshotCodec.capture(
                positive.proposal.account,positive.owner).values()
                    .equals(positive.proposal.preparedPreimage.values())&&
                positive.owner.bank().inventorySlots()==0&&
                positive.owner.mailbox().get(
                    positive.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try{
                world.persistence().load(positive.proposal.account);
            }catch(IOException veto){
                restartRemainsQuarantined=veto.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }

            Seed forged=seed(world,writer,"g2169-forged");
            MailboxTerminalLiveTransitionCandidate duplicate=
                MailboxTerminalLiveTransitionCandidate.stageDetached(
                    forged.proposal,forged.terminal);
            forgedCandidateDenied=rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    forged.token,forged.proposal,duplicate));

            Seed negative=seed(world,writer,"g2169-marker");
            Path markerPath=paths.resolve(negative.proposal.account);
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(markerPath,()->{
                    new MailboxStrictUncertainFence(paths)
                        .armInsidePublicationLock(
                            negative.proposal.account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    negative.proposal.preparedPreimage));
                    return null;
                });
            boolean negativeFailure=failed(world.persistence()
                .attestReservedTerminalWorldFreshness(
                    negative.token,negative.proposal,
                    negative.candidate));
            negativeMarkerDenied=
                negativeFailure&&negative.token.isActive()&&
                new MailboxStrictUncertainFence(paths)
                    .present(negative.proposal.account);
            oneShotFailureRemainsClosed=rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    negative.token,negative.proposal,
                    negative.candidate));

            Seed diskChanged=seed(world,writer,"g2169-diskchanged");
            repo.save(diskChanged.proposal.preparedPreimage);
            wrongDiskDenied=failed(world.persistence()
                .attestReservedTerminalWorldFreshness(
                    diskChanged.token,diskChanged.proposal,
                    diskChanged.candidate))!=null&&
                diskChanged.token.isActive()&&
                diskChanged.owner.bank().inventorySlots()==0;

            Seed movement=seed(world,writer,"g2169-movement");
            world.submitAndWait(movement.owner,movement.generation,()->{
                movement.owner.movement().setRunEnergy(37);
            },5000L);
            staleLivePlayerDenied=rejects(()->world.persistence()
                .attestReservedTerminalWorldFreshness(
                    movement.token,movement.proposal,movement.candidate))&&
                movement.token.isActive();

            Seed observedAgain=seed(world,writer,"g2169-superseded");
            // A later independent G21.67 worker observation, even when
            // exact, invalidates the old candidate's receipt identity.
            PlayerSnapshot latest=observedAgain.terminal;
            WorldPlayerPersistence.TerminalReconciliationEvidence refreshed=
                world.persistence().reconcileReservedTerminalReadOnly(
                    observedAgain.token,observedAgain.proposal,
                    latest,null).get(8L,TimeUnit.SECONDS);
            oldObservationInvalidated=refreshed.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence.State
                    .EXACT_TERMINAL_NO_RECEIPT&&
                rejects(()->world.persistence()
                    .attestReservedTerminalWorldFreshness(
                        observedAgain.token,observedAgain.proposal,
                        observedAgain.candidate));

            Seed retired=seed(world,writer,"g2169-retired");
            boolean unregistered=world.unregisterPlayer(
                retired.owner,retired.generation);
            retiredOwnerDenied=unregistered&&
                rejects(()->world.persistence()
                    .attestReservedTerminalWorldFreshness(
                        retired.token,retired.proposal,retired.candidate))&&
                retired.token.isActive();

            try(Stream<Path> files=Files.walk(root)){
                noTempOrLeaseLeaks=files.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> entries=Files.walk(root)){
                for(Path path:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println("G2169_FENCED_WORLD_FRESHNESS_DIAGNOSTICS"+
            " lockBlocksFifoObservation="+lockBlocksFifoObservation+
            " duplicatePendingRejected="+duplicatePendingRejected+
            " exactWorldFreshnessAttested="+exactWorldFreshnessAttested+
            " neverAuthorizesGrant="+neverAuthorizesGrant+
            " singleUseAfterSuccess="+singleUseAfterSuccess+
            " bytesUntouched="+bytesUntouched+
            " livePreparedUnchanged="+livePreparedUnchanged+
            " restartRemainsQuarantined="+restartRemainsQuarantined+
            " candidateReleaseStillDenied="+candidateReleaseStillDenied+
            " independentAccountUnaffected="+independentAccountUnaffected+
            " forgedCandidateDenied="+forgedCandidateDenied+
            " negativeMarkerDenied="+negativeMarkerDenied+
            " oneShotFailureRemainsClosed="+oneShotFailureRemainsClosed+
            " wrongDiskDenied="+wrongDiskDenied+
            " staleLivePlayerDenied="+staleLivePlayerDenied+
            " oldObservationInvalidated="+oldObservationInvalidated+
            " retiredOwnerDenied="+retiredOwnerDenied+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(lockBlocksFifoObservation&&duplicatePendingRejected&&
             exactWorldFreshnessAttested&&neverAuthorizesGrant&&
             singleUseAfterSuccess&&bytesUntouched&&
             livePreparedUnchanged&&
             restartRemainsQuarantined&&candidateReleaseStillDenied&&
             independentAccountUnaffected&&forgedCandidateDenied&&
             negativeMarkerDenied&&oneShotFailureRemainsClosed&&
             wrongDiskDenied&&staleLivePlayerDenied&&
             oldObservationInvalidated&&retiredOwnerDenied&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.69 fenced World terminal freshness NO_GRANT");
        System.out.println("G2169_FENCED_WORLD_FRESHNESS_PASS"+
            " lockAcrossWorldTick=true oneShot=true"+
            " grant=false apply=false replay=false release=false");
    }

    private static Seed seed(World world,
        StrictDurablePlayerSnapshotWriter writer,
        String account)throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            plan=new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            String id=account+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                id,"Fenced World terminal observation","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2169_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            plan.set(MailboxSettlementPostimagePlanner.plan(
                owner,generation,row));
        },5000L);
        PlayerSnapshot terminal=
            MailboxAtomicTerminalSnapshot.compose(plan.get());
        writer.saveStrict(plan.get().preparedPreimage);
        WorldPlayerPersistence.PreparedAccountReservation token=
            world.persistence().reservePreparedAccount(
                owner,generation,plan.get());
        WorldPlayerPersistence.TerminalPublicationEvidence publication=
            world.persistence().publishReservedTerminalStrictly(
                token,plan.get(),terminal,writer)
            .get(8L,TimeUnit.SECONDS);
        WorldPlayerPersistence.TerminalReconciliationEvidence observed=
            world.persistence().reconcileReservedTerminalReadOnly(
                token,plan.get(),terminal,publication)
            .get(8L,TimeUnit.SECONDS);
        if(observed.state!=
                WorldPlayerPersistence.TerminalReconciliationEvidence.State
                    .EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT)
            throw new AssertionError(
                "G21.69 seed missing strict receipt observation");
        MailboxTerminalLiveTransitionCandidate candidate=
            world.persistence().stageReservedTerminalWorldCandidate(
                token,plan.get(),terminal,observed)
            .get(8L,TimeUnit.SECONDS);
        return new Seed(
            owner,generation,plan.get(),terminal,token,candidate);
    }

    private static boolean failed(CompletableFuture<?> future){
        try{future.get(8L,TimeUnit.SECONDS);return false;}
        catch(ExecutionException expected){return true;}
        catch(Exception unexpected){throw new AssertionError(
            "G21.69 unexpected future timeout",unexpected);}
    }

    private static boolean rejects(Runnable op){
        try{op.run();return false;}
        catch(IllegalStateException|IllegalArgumentException expected){
            return true;
        }
    }
    private G2169MailboxFencedTerminalWorldFreshnessIntegrationTest(){}
}

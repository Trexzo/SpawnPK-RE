package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.66: actual guarded strict terminal file publication on the SAME
 * World persistence FIFO, while reward grant and restart hydration
 * remain forbidden. Deterministic pre/post-rename fault boundaries.
 */
public final class G2166MailboxGuardedTerminalPublicationIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer player,long gen,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.owner=player;
            this.generation=gen;
            this.proposal=proposal;
            this.terminal=MailboxAtomicTerminalSnapshot.compose(proposal);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2166-guarded-terminal-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(paths);
        MailboxStrictUncertainFence uncertain=
            new MailboxStrictUncertainFence(paths);
        boolean fifoPreflightExact=false;
        boolean strictReceiptExact=false;
        boolean terminalDiskExact=false;
        boolean restartTerminalRefused=false;
        boolean confirmedMarkerCleared=false;
        boolean reservationStillBlocksSaves=false;
        boolean repeatPublicationRejected=false;
        boolean originalLiveOwnerUnclaimed=false;
        boolean activeTerminalMarkerNotSelfKick=false;
        boolean confirmedTerminalMarkerCleared=false;
        boolean permanentMarkerStillVetoes=false;
        boolean staleAdmissionRejected=false;
        boolean lateOwnerChangedRejected=false;
        boolean lateOwnerDiskPrepared=false;
        boolean divergentDiskRejected=false;
        boolean divergentDiskNotOverwritten=false;
        boolean permanentMarkerVeto=false;
        boolean uncertainPostMoveRejected=false;
        boolean uncertainMarkerPersisted=false;
        boolean uncertainDiskTerminal=false;
        boolean uncertainRestartRefused=false;
        boolean independentAccountSaved=false;
        boolean noTempOrLeaseLeaks=false;
        boolean noRewardGranted=true;

        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();

            Seed clean=seed(world,"g2166-clean");
            writer.saveStrict(clean.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation cleanToken=
                world.persistence().reservePreparedAccount(
                    clean.owner,clean.generation,clean.proposal);
            WorldPlayerPersistence.TerminalPrepublicationEvidence preview=
                world.persistence().preflightReservedTerminalPublication(
                    cleanToken,clean.proposal,clean.terminal)
                    .get(8,TimeUnit.SECONDS);
            fifoPreflightExact=preview.state==
                WorldPlayerPersistence.TerminalPrepublicationEvidence
                    .State.EXACT_PREPARED_NO_GRANT&&
                !preview.publicationAuthorized&&!preview.grantAuthorized;
            WorldPlayerPersistence.TerminalPublicationEvidence completed=
                world.persistence().publishReservedTerminalStrictly(
                    cleanToken,clean.proposal,clean.terminal,writer)
                    .get(8,TimeUnit.SECONDS);
            strictReceiptExact=completed.fileOperationConfirmed&&
                completed.strictReceipt.matchesSnapshot(clean.terminal)&&
                clean.proposal.account.equals(completed.account)&&
                clean.proposal.idempotencyKey.equals(completed.intentKey)&&
                completed.generation==clean.generation&&
                !completed.grantAuthorized&&!completed.replayAuthorized&&
                !completed.rollbackAuthorized&&!completed.releaseAuthorized&&
                !completed.clientAckAuthorized;
            terminalDiskExact=repo.load(clean.proposal.account).get()
                .values().equals(clean.terminal.values());
            confirmedMarkerCleared=
                !intent.present(clean.proposal.account)&&
                !uncertain.present(clean.proposal.account);
            try{
                world.persistence().load(clean.proposal.account);
            }catch(IOException refused){
                restartTerminalRefused=refused.getMessage().contains(
                    "QUARANTINE_TERMINAL_NO_GRANT");
            }
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                competing=new AtomicReference<>();
            world.submitAndWait(clean.owner,clean.generation,()->{
                competing.set(world.persistence().captureDeferredSave(
                    clean.proposal.account,clean.owner,clean.generation,
                    0,"[g2166] ","AFTER_TERMINAL_PUBLICATION"));
            },5000L);
            reservationStillBlocksSaves=cleanToken.isActive()&&
                failed(world.persistence().submitCapturedWithBackpressure(
                    competing.get(),2000L).completion)!=null&&
                rejects(()->cleanToken.cancelIfStillUnclaimed());
            repeatPublicationRejected=failed(world.persistence()
                .publishReservedTerminalStrictly(
                    cleanToken,clean.proposal,clean.terminal,writer))!=null&&
                repo.load(clean.proposal.account).get().values()
                    .equals(clean.terminal.values());
            originalLiveOwnerUnclaimed=
                clean.owner.bank().inventorySlots()==0&&
                clean.owner.mailbox().get(clean.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;


            // A live socket's marker-only review gate must not treat
            // THIS same-account, still-running strict terminal writer's
            // temporary G21.48 intent as a permanent review marker.
            Seed selfMarker=seed(world,"g2166-selfmarker");
            writer.saveStrict(selfMarker.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation markerToken=
                world.persistence().reservePreparedAccount(
                    selfMarker.owner,selfMarker.generation,
                    selfMarker.proposal);
            java.util.concurrent.CountDownLatch markerArmed=
                new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch markerRelease=
                new java.util.concurrent.CountDownLatch(1);
            StrictDurablePlayerSnapshotWriter markerWriter=
                new StrictDurablePlayerSnapshotWriter(paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .AFTER_WRITE_AHEAD_INTENT){
                        markerArmed.countDown();
                        try{
                            if(!markerRelease.await(8,TimeUnit.SECONDS))
                                throw new IOException(
                                    "G21.66 marker fixture timeout");
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new IOException(
                                "G21.66 marker fixture interrupted",
                                interrupted
                            );
                        }
                    }
                });
            CompletableFuture<
                WorldPlayerPersistence.TerminalPublicationEvidence
            > markerTask=world.persistence()
                .publishReservedTerminalStrictly(
                    markerToken,selfMarker.proposal,
                    selfMarker.terminal,markerWriter);
            try{
                if(!markerArmed.await(5,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.66 terminal write-ahead marker not armed");
                activeTerminalMarkerNotSelfKick=
                    intent.present(selfMarker.proposal.account)&&
                    !world.persistence().hasDurableMailboxReviewFence(
                        selfMarker.proposal.account);
            }finally{
                markerRelease.countDown();
            }
            markerTask.get(8,TimeUnit.SECONDS);
            confirmedTerminalMarkerCleared=
                !intent.present(selfMarker.proposal.account)&&
                !world.persistence().hasDurableMailboxReviewFence(
                    selfMarker.proposal.account);

            Seed stale=seed(world,"g2166-stale");
            writer.saveStrict(stale.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation staleToken=
                world.persistence().reservePreparedAccount(
                    stale.owner,stale.generation,stale.proposal);
            world.submitAndWait(stale.owner,stale.generation,()->{
                stale.owner.movement().setRunEnergy(37);
            },5000L);
            staleAdmissionRejected=rejects(()->world.persistence()
                .publishReservedTerminalStrictly(
                    staleToken,stale.proposal,stale.terminal,writer))&&
                repo.load(stale.proposal.account).get().values()
                    .equals(stale.proposal.preparedPreimage.values());

            Seed late=seed(world,"g2166-late");
            writer.saveStrict(late.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation lateToken=
                world.persistence().reservePreparedAccount(
                    late.owner,late.generation,late.proposal);
            AtomicBoolean faultTriggered=new AtomicBoolean();
            StrictDurablePlayerSnapshotWriter lateWriter=
                new StrictDurablePlayerSnapshotWriter(paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE){
                        faultTriggered.set(true);
                        try{
                            world.submitAndWait(
                                late.owner,late.generation,()->{
                                    late.owner.movement().setRunEnergy(37);
                                },5000L
                            );
                        }catch(Exception injectionFailure){
                            throw new IOException(
                                "G21.66 late-owner fixture failed",
                                injectionFailure
                            );
                        }
                    }
                });
            Throwable lateFailure=failed(world.persistence()
                .publishReservedTerminalStrictly(
                    lateToken,late.proposal,late.terminal,lateWriter));
            lateOwnerChangedRejected=lateFailure!=null&&
                faultTriggered.get()&&lateToken.isActive();
            lateOwnerDiskPrepared=repo.load(late.proposal.account).get()
                .values().equals(late.proposal.preparedPreimage.values())&&
                !intent.present(late.proposal.account);

            Seed divergent=seed(world,"g2166-divergent");
            writer.saveStrict(divergent.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation divergentToken=
                world.persistence().reservePreparedAccount(
                    divergent.owner,divergent.generation,
                    divergent.proposal);
            // Raw test-only writer refuses cooperation; the guarded
            // terminal callback must re-read exact PREPARED disk bytes.
            repo.save(divergent.proposal.hypotheticalPostimage);
            divergentDiskRejected=failed(world.persistence()
                .publishReservedTerminalStrictly(
                    divergentToken,divergent.proposal,divergent.terminal,
                    writer))!=null;
            divergentDiskNotOverwritten=repo.load(divergent.proposal.account)
                .get().values().equals(
                    divergent.proposal.hypotheticalPostimage.values())&&
                !intent.present(divergent.proposal.account);

            Seed fenced=seed(world,"g2166-fenced");
            writer.saveStrict(fenced.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation fencedToken=
                world.persistence().reservePreparedAccount(
                    fenced.owner,fenced.generation,fenced.proposal);
            new MailboxDurableReviewFence(paths).arm(fenced.proposal);
            permanentMarkerVeto=failed(world.persistence()
                .publishReservedTerminalStrictly(
                    fencedToken,fenced.proposal,fenced.terminal,writer))!=null&&
                repo.load(fenced.proposal.account).get().values().equals(
                    fenced.proposal.preparedPreimage.values());

            Seed afterMove=seed(world,"g2166-uncertain");
            writer.saveStrict(afterMove.proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation postToken=
                world.persistence().reservePreparedAccount(
                    afterMove.owner,afterMove.generation,
                    afterMove.proposal);
            StrictDurablePlayerSnapshotWriter postMoveWriter=
                new StrictDurablePlayerSnapshotWriter(paths,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_DIRECTORY_FORCE)
                        throw new IOException("G21.66 post-move force fault");
                });
            Throwable postFailure=failed(world.persistence()
                .publishReservedTerminalStrictly(
                    postToken,afterMove.proposal,afterMove.terminal,
                    postMoveWriter));
            uncertainPostMoveRejected=postFailure instanceof
                StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException&&postToken.isActive();
            uncertainMarkerPersisted=
                uncertain.present(afterMove.proposal.account)&&
                intent.present(afterMove.proposal.account);
            permanentMarkerStillVetoes=
                world.persistence().hasDurableMailboxReviewFence(
                    afterMove.proposal.account);
            uncertainDiskTerminal=repo.load(afterMove.proposal.account)
                .get().values().equals(afterMove.terminal.values());
            try{
                world.persistence().load(afterMove.proposal.account);
            }catch(IOException refusal){
                uncertainRestartRefused=refusal.getMessage().contains(
                    "MAILBOX_DURABLE_REVIEW_FENCE");
            }

            WorldPlayer other=new WorldPlayer();
            long otherGen=world.registerPlayer(
                other,"g2166-independent");
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                otherCapture=new AtomicReference<>();
            world.submitAndWait(other,otherGen,()->{
                otherCapture.set(world.persistence().captureDeferredSave(
                    "g2166-independent",other,otherGen,0,
                    "[g2166] ","UNRELATED_ACCOUNT"));
            },5000L);
            world.persistence().submitCapturedWithBackpressure(
                otherCapture.get(),2000L).completion.get(
                8,TimeUnit.SECONDS);
            independentAccountSaved=repo.load(
                "g2166-independent").isPresent();
            noRewardGranted=clean.owner.bank().inventorySlots()==0&&
                afterMove.owner.bank().inventorySlots()==0&&
                afterMove.owner.mailbox().get(afterMove.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> pathsOnDisk=Files.walk(root)){
                noTempOrLeaseLeaks=pathsOnDisk.noneMatch(path->
                    path.getFileName().toString().endsWith(".tmp"))&&
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
        System.out.println("G2166_GUARDED_TERMINAL_DIAGNOSTICS"+
            " fifoPreflightExact="+fifoPreflightExact+
            " strictReceiptExact="+strictReceiptExact+
            " terminalDiskExact="+terminalDiskExact+
            " restartTerminalRefused="+restartTerminalRefused+
            " confirmedMarkerCleared="+confirmedMarkerCleared+
            " reservationStillBlocksSaves="+reservationStillBlocksSaves+
            " repeatPublicationRejected="+repeatPublicationRejected+
            " originalLiveOwnerUnclaimed="+originalLiveOwnerUnclaimed+
            " activeTerminalMarkerNotSelfKick="+
                activeTerminalMarkerNotSelfKick+
            " confirmedTerminalMarkerCleared="+
                confirmedTerminalMarkerCleared+
            " permanentMarkerStillVetoes="+
                permanentMarkerStillVetoes+
            " staleAdmissionRejected="+staleAdmissionRejected+
            " lateOwnerChangedRejected="+lateOwnerChangedRejected+
            " lateOwnerDiskPrepared="+lateOwnerDiskPrepared+
            " divergentDiskRejected="+divergentDiskRejected+
            " divergentDiskNotOverwritten="+divergentDiskNotOverwritten+
            " permanentMarkerVeto="+permanentMarkerVeto+
            " uncertainPostMoveRejected="+uncertainPostMoveRejected+
            " uncertainMarkerPersisted="+uncertainMarkerPersisted+
            " uncertainDiskTerminal="+uncertainDiskTerminal+
            " uncertainRestartRefused="+uncertainRestartRefused+
            " independentAccountSaved="+independentAccountSaved+
            " noRewardGranted="+noRewardGranted+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(fifoPreflightExact&&strictReceiptExact&&
             terminalDiskExact&&restartTerminalRefused&&
             confirmedMarkerCleared&&reservationStillBlocksSaves&&
             repeatPublicationRejected&&originalLiveOwnerUnclaimed&&
             activeTerminalMarkerNotSelfKick&&
             confirmedTerminalMarkerCleared&&
             permanentMarkerStillVetoes&&
             staleAdmissionRejected&&lateOwnerChangedRejected&&
             lateOwnerDiskPrepared&&divergentDiskRejected&&
             divergentDiskNotOverwritten&&permanentMarkerVeto&&
             uncertainPostMoveRejected&&uncertainMarkerPersisted&&
             uncertainDiskTerminal&&uncertainRestartRefused&&
             independentAccountSaved&&noRewardGranted&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.66 guarded terminal World publication");
        System.out.println("G2166_GUARDED_TERMINAL_PASS"+
            " publicationFileOnly=true grant=false replay=false release=false");
    }

    private static Seed seed(World world,String account)throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            ref=new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            String message=account+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"Reserved strict terminal","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2166_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            ref.set(MailboxSettlementPostimagePlanner.plan(
                owner,generation,row));
        },5000L);
        return new Seed(owner,generation,ref.get());
    }
    private static Throwable failed(CompletableFuture<?> task){
        try{task.get(8,TimeUnit.SECONDS);return null;}
        catch(ExecutionException expected){return expected.getCause();}
        catch(Exception other){throw new AssertionError(
            "G21.66 unexpected future timeout/interruption",other);}
    }
    private static boolean rejects(Runnable action){
        try{action.run();return false;}
        catch(IllegalArgumentException|IllegalStateException expected){
            return true;
        }
    }
    private G2166MailboxGuardedTerminalPublicationIntegrationTest(){}
}

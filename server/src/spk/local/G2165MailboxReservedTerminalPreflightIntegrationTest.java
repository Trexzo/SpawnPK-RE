package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.65: file-backed FIFO reservation with terminal NO_GRANT gate. */
public final class G2165MailboxReservedTerminalPreflightIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2165-reserved-terminal-");
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        AtomicBoolean first=new AtomicBoolean(true);
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(
            paths,account->{
                if(first.compareAndSet(true,false)){
                    entered.countDown();
                    try{
                        if(!release.await(8,TimeUnit.SECONDS))
                            throw new IOException("G21.65 blocked writer timeout");
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "G21.65 writer interrupted",interrupted);
                    }
                }
            });
        boolean existingWriteDrainedFirst=false;
        boolean reservationPinsGate=false;
        boolean duplicateGateRejected=false;
        boolean newerSaveDenied=false;
        boolean autosaveSuppressed=false;
        boolean exactPreparedObserved=false;
        boolean noPublicationPermit=false;
        boolean unchangedDiskAfterGate=false;
        boolean terminalForgeryDenied=false;
        boolean liveDriftDenied=false;
        boolean divergentDiskDetected=false;
        boolean divergenceHoldsReservation=false;
        boolean unrelatedAccountWrites=false;
        boolean noInventoryGrant=true;
        boolean noTempOrLeaseLeaks=false;

        try(World world=World.isolatedForTest(60000L,repository)){
            world.start();
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,"g2165-alice");
            AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                ref=new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                older=new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                player.mailbox().deliver(new RewardDeliveryMessage(
                    "g2165:gift","Gate fixture","No credit",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)),
                    "CUSTOM_LOCALLAB_G2165_FIXTURE"));
                MailboxRewardDeliveryService.Snapshot row=
                    player.mailbox().get("g2165:gift");
                MailboxPreparedClaimJournal.stageOnly(player,
                    MailboxPreparedClaimJournal.prepare(player,row));
                ref.set(MailboxSettlementPostimagePlanner.plan(
                    player,generation,row));
                older.set(world.persistence().captureDeferredSave(
                    "g2165-alice",player,generation,0,
                    "[g2165] ","BEFORE_RESERVATION"));
            },5000L);
            MailboxSettlementPostimagePlanner.Proposal proposal=ref.get();
            PlayerSnapshot terminal=
                MailboxAtomicTerminalSnapshot.compose(proposal);
            WorldPlayerPersistence.SaveTicket prior=
                world.persistence().submitCapturedWithBackpressure(
                    older.get(),2000L);
            if(!entered.await(5,TimeUnit.SECONDS))
                throw new AssertionError("G21.65 first save never started");

            WorldPlayerPersistence.PreparedAccountReservation reservation=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal);
            CompletableFuture<
                WorldPlayerPersistence.TerminalPrepublicationEvidence
            > pending=world.persistence()
                .preflightReservedTerminalPublication(
                    reservation,proposal,terminal);
            existingWriteDrainedFirst=
                !prior.completion.isDone()&&!pending.isDone();
            reservationPinsGate=reservation.isActive()&&
                rejects(()->reservation.cancelIfStillUnclaimed());
            duplicateGateRejected=rejects(()->world.persistence()
                .preflightReservedTerminalPublication(
                    reservation,proposal,terminal));

            AtomicReference<WorldPlayerPersistence.CapturedSave>
                captured=new AtomicReference<>();
            world.submitAndWait(player,generation,()->captured.set(
                world.persistence().captureDeferredSave(
                    "g2165-alice",player,generation,0,
                    "[g2165] ","AFTER_RESERVATION")
            ),5000L);
            WorldPlayerPersistence.SaveTicket refused=
                world.persistence().submitCapturedWithBackpressure(
                    captured.get(),2000L);
            newerSaveDenied=failed(refused.completion);
            long checkpointCount=
                world.persistence().checkpointCapturedCount();
            world.submitAndWait(player,generation,()->world.persistence()
                .checkpointDue(
                    WorldPlayerPersistence.AUTOSAVE_INTERVAL_TICKS),
                5000L);
            autosaveSuppressed=world.persistence()
                .checkpointCapturedCount()==checkpointCount;

            release.countDown();
            prior.completion.get(8,TimeUnit.SECONDS);
            WorldPlayerPersistence.TerminalPrepublicationEvidence observed=
                pending.get(8,TimeUnit.SECONDS);
            exactPreparedObserved=
                observed.workerFifoObservation&&
                observed.state==
                    WorldPlayerPersistence.TerminalPrepublicationEvidence
                        .State.EXACT_PREPARED_NO_GRANT;
            noPublicationPermit=
                !observed.durabilityReceipt&&!observed.publicationAuthorized&&
                !observed.grantAuthorized&&!observed.replayAuthorized&&
                !observed.releaseAuthorized&&!observed.clientAckAuthorized&&
                reservation.isActive();
            unchangedDiskAfterGate=
                repository.load(proposal.account).get().values().equals(
                    proposal.preparedPreimage.values());

            TreeMap<String,String> forged=new TreeMap<>(terminal.values());
            forged.put("extension.mailbox-terminal-snapshot.key",
                "0".repeat(64));
            terminalForgeryDenied=rejects(()->world.persistence()
                .preflightReservedTerminalPublication(
                    reservation,proposal,new PlayerSnapshot(
                        PlayerSnapshot.CURRENT_VERSION,
                        proposal.account,forged)));

            world.submitAndWait(player,generation,()->player
                .movement().setRunEnergy(37),5000L);
            liveDriftDenied=rejects(()->world.persistence()
                .preflightReservedTerminalPublication(
                    reservation,proposal,terminal));
            world.submitAndWait(player,generation,()->player
                .movement().setRunEnergy(100),5000L);

            // Deliberately uncooperative raw disk mutation after FIFO
            // gate; the next check must not approve stale PREPARED.
            repository.save(proposal.hypotheticalPostimage);
            WorldPlayerPersistence.TerminalPrepublicationEvidence drift=
                world.persistence().preflightReservedTerminalPublication(
                    reservation,proposal,terminal).get(8,TimeUnit.SECONDS);
            divergentDiskDetected=drift.state==
                WorldPlayerPersistence.TerminalPrepublicationEvidence
                    .State.DIVERGENT_ACCOUNT;
            divergenceHoldsReservation=reservation.isActive()&&
                rejects(()->reservation.cancelIfStillUnclaimed());

            WorldPlayer independent=new WorldPlayer();
            long otherGen=world.registerPlayer(
                independent,"g2165-independent");
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                other=new AtomicReference<>();
            world.submitAndWait(independent,otherGen,()->other.set(
                world.persistence().captureDeferredSave(
                    "g2165-independent",independent,otherGen,0,
                    "[g2165] ","INDEPENDENT")
            ),5000L);
            world.persistence().submitCapturedWithBackpressure(
                other.get(),2000L).completion.get(8,TimeUnit.SECONDS);
            unrelatedAccountWrites=repository.load(
                "g2165-independent").isPresent()&&reservation.isActive();
            noInventoryGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get("g2165:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> entries=Files.walk(dir)){
                noTempOrLeaseLeaks=entries.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            release.countDown();
            try(Stream<Path> entries=Files.walk(dir)){
                for(Path path:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println("G2165_TERMINAL_PREFLIGHT_DIAGNOSTICS"+
            " existingWriteDrainedFirst="+existingWriteDrainedFirst+
            " reservationPinsGate="+reservationPinsGate+
            " duplicateGateRejected="+duplicateGateRejected+
            " newerSaveDenied="+newerSaveDenied+
            " autosaveSuppressed="+autosaveSuppressed+
            " exactPreparedObserved="+exactPreparedObserved+
            " noPublicationPermit="+noPublicationPermit+
            " unchangedDiskAfterGate="+unchangedDiskAfterGate+
            " terminalForgeryDenied="+terminalForgeryDenied+
            " liveDriftDenied="+liveDriftDenied+
            " divergentDiskDetected="+divergentDiskDetected+
            " divergenceHoldsReservation="+divergenceHoldsReservation+
            " unrelatedAccountWrites="+unrelatedAccountWrites+
            " noInventoryGrant="+noInventoryGrant+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(existingWriteDrainedFirst&&reservationPinsGate&&
             duplicateGateRejected&&newerSaveDenied&&
             autosaveSuppressed&&exactPreparedObserved&&
             noPublicationPermit&&unchangedDiskAfterGate&&
             terminalForgeryDenied&&liveDriftDenied&&
             divergentDiskDetected&&divergenceHoldsReservation&&
             unrelatedAccountWrites&&noInventoryGrant&&
             noTempOrLeaseLeaks))
            throw new AssertionError("G21.65 terminal FIFO preflight");
        System.out.println("G2165_TERMINAL_PREFLIGHT_PASS"+
            " grant=false publish=false replay=false release=false");
    }
    private static boolean failed(CompletableFuture<?> f){
        try{f.get(5,TimeUnit.SECONDS);return false;}
        catch(ExecutionException expected){return true;}
        catch(Exception unexpected){return false;}
    }
    private static boolean rejects(Runnable operation){
        try{operation.run();return false;}
        catch(IllegalArgumentException|IllegalStateException expected){
            return true;
        }
    }
    private G2165MailboxReservedTerminalPreflightIntegrationTest(){}
}

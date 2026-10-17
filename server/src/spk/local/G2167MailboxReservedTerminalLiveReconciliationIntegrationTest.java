package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.67: compare exact persisted terminal, original live PREPARED owner
 * and G21.66 file-operation receipt on the SAME reserved World FIFO.
 * Never apply inventory, replay, grant, release or send packets.
 */
public final class G2167MailboxReservedTerminalLiveReconciliationIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal plan;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long generation,
             MailboxSettlementPostimagePlanner.Proposal plan){
            this.owner=p;
            this.generation=generation;
            this.plan=plan;
            this.terminal=MailboxAtomicTerminalSnapshot.compose(plan);
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2167-reconcile-");
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository repo=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean exactTerminalMatched=false,repeatWithoutCredit=false;
        boolean noReceiptNotSuccess=false,foreignReceiptRejected=false;
        boolean negativeMarkerDominates=false;
        boolean preparedOnlyRecognized=false,divergentRecognized=false;
        boolean missingRecognized=false,staleOwnerRejected=false;
        boolean forgedTerminalRejected=false,duplicatePendingRejected=false;
        boolean reservationCannotRelease=false;
        boolean rawBytesUntouched=false,restartStillQuarantined=false;
        boolean independentAccountUntouched=false,liveUnclaimed=true;
        boolean noTemporaryOrLeaseLeaks=false;

        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();
            Seed clean=seed(world,"g2167-clean");
            writer.saveStrict(clean.plan.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation cleanToken=
                world.persistence().reservePreparedAccount(
                    clean.owner,clean.generation,clean.plan);
            WorldPlayerPersistence.TerminalPublicationEvidence receipt=
                world.persistence().publishReservedTerminalStrictly(
                    cleanToken,clean.plan,clean.terminal,writer
                ).get(8,TimeUnit.SECONDS);
            Path file=paths.resolve(clean.plan.account);
            byte[] bytesBefore=Files.readAllBytes(file);

            // Block this account's publication lock to prove that a
            // pending reconciliation cannot admit another same-token
            // FIFO read. A different account remains independent.
            CountDownLatch acquired=new CountDownLatch(1);
            CountDownLatch release=new CountDownLatch(1);
            AtomicReference<Throwable> lockFailure=new AtomicReference<>();
            Thread holder=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(file,()->{
                            acquired.countDown();
                            try{
                                if(!release.await(8,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.67 lock fixture timed out");
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.67 lock fixture interrupted",
                                    interrupted);
                            }
                            return null;
                        });
                }catch(Throwable error){lockFailure.set(error);}
            },"g2167-test-lock-holder");
            holder.start();
            try{
                if(!acquired.await(5,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "G21.67 publication lock not acquired");
                CompletableFuture<
                    WorldPlayerPersistence.TerminalReconciliationEvidence
                > pending=world.persistence()
                    .reconcileReservedTerminalReadOnly(
                        cleanToken,clean.plan,clean.terminal,receipt);
                duplicatePendingRejected=rejects(()->world.persistence()
                    .reconcileReservedTerminalReadOnly(
                        cleanToken,clean.plan,clean.terminal,receipt))&&
                    cleanToken.isActive()&&!pending.isDone();
                release.countDown();
                WorldPlayerPersistence.TerminalReconciliationEvidence exact=
                    pending.get(8,TimeUnit.SECONDS);
                exactTerminalMatched=exact.state==
                    WorldPlayerPersistence.TerminalReconciliationEvidence
                        .State.EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT&&
                    exact.account.equals(clean.plan.account)&&
                    exact.intentKey.equals(clean.plan.idempotencyKey)&&
                    exact.generation==clean.generation&&
                    exact.worldFifoReadOnly&&exact.liveOwnerExactlyPrepared&&
                    !exact.durabilityConfirmed&&!exact.settlementCommitted&&
                    !exact.grantAuthorized&&!exact.replayAuthorized&&
                    !exact.rollbackAuthorized&&!exact.releaseAuthorized&&
                    !exact.clientAckAuthorized;
            }finally{
                release.countDown();
                holder.join(5000L);
            }
            if(lockFailure.get()!=null)
                throw new AssertionError(
                    "G21.67 lock holder unexpectedly failed",
                    lockFailure.get());

            WorldPlayerPersistence.TerminalReconciliationEvidence repeated=
                observe(world,clean,cleanToken,receipt);
            repeatWithoutCredit=repeated.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT&&
                clean.owner.bank().inventorySlots()==0&&
                clean.owner.mailbox().get(clean.plan.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            WorldPlayerPersistence.TerminalReconciliationEvidence absent=
                observe(world,clean,cleanToken,null);
            noReceiptNotSuccess=absent.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.EXACT_TERMINAL_NO_RECEIPT&&
                !absent.grantAuthorized;

            Seed unrelated=seed(world,"g2167-foreign");
            writer.saveStrict(unrelated.plan.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation otherToken=
                world.persistence().reservePreparedAccount(
                    unrelated.owner,unrelated.generation,unrelated.plan);
            WorldPlayerPersistence.TerminalPublicationEvidence otherReceipt=
                world.persistence().publishReservedTerminalStrictly(
                    otherToken,unrelated.plan,unrelated.terminal,writer
                ).get(8,TimeUnit.SECONDS);
            WorldPlayerPersistence.TerminalReconciliationEvidence wrong=
                observe(world,clean,cleanToken,otherReceipt);
            foreignReceiptRejected=wrong.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.EXACT_TERMINAL_RECEIPT_MISMATCH&&
                !wrong.grantAuthorized;
            rawBytesUntouched=Arrays.equals(
                bytesBefore,Files.readAllBytes(file));

            // A negative permanent marker always dominates even when
            // the complete terminal file and receipt remain present.
            MailboxAccountPublicationCoordinator
                .withExclusivePublication(file,()->{
                    new MailboxStrictUncertainFence(paths)
                        .armInsidePublicationLock(
                            clean.plan.account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    clean.plan.preparedPreimage));
                    return null;
                });
            WorldPlayerPersistence.TerminalReconciliationEvidence fenced=
                observe(world,clean,cleanToken,receipt);
            negativeMarkerDominates=fenced.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.NEGATIVE_FENCE_QUARANTINE;
            try{
                world.persistence().load(clean.plan.account);
            }catch(IOException denied){
                restartStillQuarantined=
                    denied.getMessage().contains(
                        "MAILBOX_DURABLE_REVIEW_FENCE");
            }

            Seed unsettled=seed(world,"g2167-prepared");
            writer.saveStrict(unsettled.plan.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation preToken=
                world.persistence().reservePreparedAccount(
                    unsettled.owner,unsettled.generation,unsettled.plan);
            WorldPlayerPersistence.TerminalReconciliationEvidence pre=
                observe(world,unsettled,preToken,null);
            preparedOnlyRecognized=pre.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.EXACT_PREPARED_NO_GRANT;
            repo.save(unsettled.plan.hypotheticalPostimage);
            WorldPlayerPersistence.TerminalReconciliationEvidence changed=
                observe(world,unsettled,preToken,null);
            divergentRecognized=changed.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.DIVERGENT_ACCOUNT_QUARANTINE&&
                !changed.grantAuthorized;
            Files.delete(paths.resolve(unsettled.plan.account));
            WorldPlayerPersistence.TerminalReconciliationEvidence missing=
                observe(world,unsettled,preToken,null);
            missingRecognized=missing.state==
                WorldPlayerPersistence.TerminalReconciliationEvidence
                    .State.MISSING_ACCOUNT_QUARANTINE;

            Seed changedOwner=seed(world,"g2167-changed");
            writer.saveStrict(changedOwner.plan.preparedPreimage);
            WorldPlayerPersistence.PreparedAccountReservation driftToken=
                world.persistence().reservePreparedAccount(
                    changedOwner.owner,changedOwner.generation,
                    changedOwner.plan);
            world.submitAndWait(
                changedOwner.owner,changedOwner.generation,
                ()->changedOwner.owner.movement().setRunEnergy(37),
                5000L
            );
            staleOwnerRejected=rejects(()->world.persistence()
                .reconcileReservedTerminalReadOnly(
                    driftToken,changedOwner.plan,changedOwner.terminal,
                    null));
            TreeMap<String,String> altered=new TreeMap<>(
                changedOwner.terminal.values());
            altered.put("extension.mailbox-terminal-snapshot.key",
                "0".repeat(64));
            forgedTerminalRejected=rejects(()->world.persistence()
                .reconcileReservedTerminalReadOnly(
                    driftToken,changedOwner.plan,
                    new PlayerSnapshot(PlayerSnapshot.CURRENT_VERSION,
                        changedOwner.plan.account,altered),null));
            reservationCannotRelease=cleanToken.isActive()&&
                preToken.isActive()&&
                otherToken.isActive()&&
                driftToken.isActive()&&
                rejects(()->cleanToken.cancelIfStillUnclaimed());

            WorldPlayer normal=new WorldPlayer();
            long normalGen=world.registerPlayer(normal,"g2167-normal");
            AtomicReference<WorldPlayerPersistence.CapturedSave>
                current=new AtomicReference<>();
            world.submitAndWait(normal,normalGen,()->current.set(
                world.persistence().captureDeferredSave(
                    "g2167-normal",normal,normalGen,0,
                    "[g2167] ","INDEPENDENT_SAVE")),5000L);
            world.persistence().submitCapturedWithBackpressure(
                current.get(),2000L).completion.get(8,TimeUnit.SECONDS);
            independentAccountUntouched=repo.load(
                "g2167-normal").isPresent()&&
                cleanToken.isActive()&&
                preToken.isActive();

            liveUnclaimed=clean.owner.bank().inventorySlots()==0&&
                unrelated.owner.bank().inventorySlots()==0&&
                unsettled.owner.bank().inventorySlots()==0&&
                clean.owner.mailbox().get(clean.plan.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            try(Stream<Path> pathsOnDisk=Files.walk(dir)){
                noTemporaryOrLeaseLeaks=pathsOnDisk.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> entries=Files.walk(dir)){
                for(Path p:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2167_TERMINAL_RECONCILIATION_DIAGNOSTICS"+
            " exactTerminalMatched="+exactTerminalMatched+
            " repeatWithoutCredit="+repeatWithoutCredit+
            " noReceiptNotSuccess="+noReceiptNotSuccess+
            " foreignReceiptRejected="+foreignReceiptRejected+
            " negativeMarkerDominates="+negativeMarkerDominates+
            " preparedOnlyRecognized="+preparedOnlyRecognized+
            " divergentRecognized="+divergentRecognized+
            " missingRecognized="+missingRecognized+
            " staleOwnerRejected="+staleOwnerRejected+
            " forgedTerminalRejected="+forgedTerminalRejected+
            " duplicatePendingRejected="+duplicatePendingRejected+
            " reservationCannotRelease="+reservationCannotRelease+
            " rawBytesUntouched="+rawBytesUntouched+
            " restartStillQuarantined="+restartStillQuarantined+
            " independentAccountUntouched="+independentAccountUntouched+
            " liveUnclaimed="+liveUnclaimed+
            " noTemporaryOrLeaseLeaks="+noTemporaryOrLeaseLeaks);
        if(!(exactTerminalMatched&&repeatWithoutCredit&&
             noReceiptNotSuccess&&foreignReceiptRejected&&
             negativeMarkerDominates&&preparedOnlyRecognized&&
             divergentRecognized&&missingRecognized&&staleOwnerRejected&&
             forgedTerminalRejected&&duplicatePendingRejected&&
             reservationCannotRelease&&rawBytesUntouched&&
             restartStillQuarantined&&independentAccountUntouched&&
             liveUnclaimed&&noTemporaryOrLeaseLeaks))
            throw new AssertionError(
                "G21.67 no-grant reserved terminal reconciliation");
        System.out.println("G2167_TERMINAL_RECONCILIATION_PASS"+
            " worldFifo=true grant=false replay=false release=false");
    }

    private static WorldPlayerPersistence.TerminalReconciliationEvidence
        observe(
            World world,Seed seed,
            WorldPlayerPersistence.PreparedAccountReservation token,
            WorldPlayerPersistence.TerminalPublicationEvidence receipt
        )throws Exception{
        return world.persistence().reconcileReservedTerminalReadOnly(
            token,seed.plan,seed.terminal,receipt).get(
            8,TimeUnit.SECONDS);
    }

    private static Seed seed(World world,String account)throws Exception{
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
            result=new AtomicReference<>();
        world.submitAndWait(owner,generation,()->{
            String message=account+":gift";
            owner.mailbox().deliver(new RewardDeliveryMessage(
                message,"Reconciliation test","No item grant",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2167_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot selected=
                owner.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,selected));
            result.set(MailboxSettlementPostimagePlanner.plan(
                owner,generation,selected));
        },5000L);
        return new Seed(owner,generation,result.get());
    }

    private static boolean rejects(Runnable op){
        try{op.run();return false;}
        catch(IllegalArgumentException|IllegalStateException expected){
            return true;
        }
    }
    private G2167MailboxReservedTerminalLiveReconciliationIntegrationTest(){}
}

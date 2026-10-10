package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.92 real FIFO persistence reservation, no recovery admission. */
public final class G2192MailboxRecoveryWriteFenceIntegrationTest {
    private static void check(boolean b,String reason){
        if(!b)throw new AssertionError("G21.92 "+reason);
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2192-fifo-");
        FilePlayerRepository.PathResolver paths=
            name->root.resolve(name+".properties");
        CountDownLatch readEntered=new CountDownLatch(1);
        CountDownLatch releaseRead=new CountDownLatch(1);
        FilePlayerRepository repository=new FilePlayerRepository(
            paths,a->{},a->{},a->{
                if(!a.equals("g2192-blocker"))return;
                readEntered.countDown();
                try{
                    if(!releaseRead.await(8,TimeUnit.SECONDS))
                        throw new java.io.IOException(
                            "G21.92 injected load not unblocked");
                }catch(InterruptedException failure){
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException(
                        "G21.92 injected load interrupted",failure);
                }
            });
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord disk=
            new MailboxGuardedDiskCommitRecord(paths);
        String account="g2192-committed";
        boolean fifoWaited=false,duplicateDenied=false;
        boolean ordinarySaveDenied=false,stableAudit=false;
        boolean earlyCancelDenied=false,releaseWorked=false;
        boolean staleReleaseDenied=false,dirtyReleaseDenied=false;
        boolean unchangedDisk=false,sourceUnclaimed=false;
        boolean noLeaks=false,noReservationLeaks=false;
        AtomicReference<Throwable> blockerFailure=new AtomicReference<>();
        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(10L,repository)){
            WorldPlayer source=new WorldPlayer();
            long sourceGeneration=fixture.registerPlayer(source,account);
            String message=account+":gift";
            source.mailbox().deliver(new RewardDeliveryMessage(
                message,"No Replay","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2192_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot attachment=
                source.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(source,
                MailboxPreparedClaimJournal.prepare(source,attachment));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    source,sourceGeneration,attachment);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(
                proposal);
            writer.saveStrict(proposal.preparedPreimage);
            journal.publishPreparedIntent(proposal);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                writer.saveStrictTerminalForWorld(
                    terminal,paths.resolve(account),
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(proposal.preparedPreimage),
                    ()->{},()->{});
            check(receipt.matchesSnapshot(terminal),"strict receipt");
            disk.recordConfirmedDiskTerminal(proposal,receipt);

            WorldPlayer blocker=new WorldPlayer();
            blocker.markRegistered("g2192-blocker");
            writer.saveStrict(PlayerSnapshotCodec.capture(
                "g2192-blocker",blocker));

            byte[] accountBytes=Files.readAllBytes(paths.resolve(account));
            byte[] journalBytes=Files.readAllBytes(journal.journalPath(account));
            byte[] commitBytes=Files.readAllBytes(disk.recordPath(account));

            WorldPlayer receiver=new WorldPlayer();
            long generation=restart.registerPlayer(receiver,account);
            restart.start();
            Thread blockingLoad=new Thread(()->{
                try{
                    restart.persistence().load("g2192-blocker");
                }catch(Throwable failure){
                    blockerFailure.set(failure);
                }
            },"g2192-fixture-blocked-load");
            blockingLoad.start();
            check(readEntered.await(5,TimeUnit.SECONDS),
                "load hook entered");
            WorldPlayerPersistence.CommittedRecoveryReservation token=
                restart.persistence().reserveCommittedRecovery(
                    receiver,generation,account);
            fifoWaited=!token.drained().isDone()&&token.isActive()&&
                restart.persistence().committedRecoveryReservationsForTest()==1;
            earlyCancelDenied=!token.cancelIfStillFresh();

            try{
                restart.persistence().reserveCommittedRecovery(
                    receiver,generation,account);
            }catch(IllegalStateException expected){
                duplicateDenied=true;
            }
            releaseRead.countDown();
            blockingLoad.join(7000);
            check(!blockingLoad.isAlive()&&blockerFailure.get()==null,
                "blocked load completed");
            token.drained().get(5,TimeUnit.SECONDS);
            MailboxCommittedRestartHandoffAudit.Result audit=
                token.inspectReadOnlyAfterDrain(
                    new MailboxCommittedDetachedRestartRecovery(paths));
            stableAudit=audit.state==
                MailboxCommittedRestartHandoffAudit.State
                    .STABLE_CANDIDATE_NO_ADMISSION&&
                !audit.grantAuthorized&&!audit.liveApplied&&
                !audit.restartAdmissionAuthorized;

            AtomicReference<WorldPlayerPersistence.SaveTicket> save=
                new AtomicReference<>();
            restart.commands().submit(receiver,generation,()->{
                save.set(restart.persistence().captureAndSave(
                    account,receiver,generation,0,
                    "[g2192] ","RECOVERY_FENCE_TEST"));
            }).get(5,TimeUnit.SECONDS);
            check(save.get()!=null,"world save dispatched");
            try{
                save.get().completion.get(5,TimeUnit.SECONDS);
            }catch(java.util.concurrent.ExecutionException expected){
                Throwable cause=expected.getCause();
                ordinarySaveDenied=cause!=null&&
                    String.valueOf(cause.getMessage()).contains(
                        "G21.92 COMMITTED_RECOVERY_WRITE_FENCED");
            }
            sourceUnclaimed=source.bank().inventorySlots()==0&&
                source.mailbox().get(message).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            unchangedDisk=Arrays.equals(accountBytes,
                    Files.readAllBytes(paths.resolve(account)))&&
                Arrays.equals(journalBytes,
                    Files.readAllBytes(journal.journalPath(account)))&&
                Arrays.equals(commitBytes,
                    Files.readAllBytes(disk.recordPath(account)));
            releaseWorked=token.cancelIfStillFresh()&&!token.isActive()&&
                restart.persistence().committedRecoveryReservationsForTest()==0;

            WorldPlayerPersistence.CommittedRecoveryReservation stale=
                restart.persistence().reserveCommittedRecovery(
                    receiver,generation,account);
            stale.drained().get(5,TimeUnit.SECONDS);
            restart.unregisterPlayer(receiver,generation);
            staleReleaseDenied=!stale.cancelIfStillFresh()&&stale.isActive();

            // A separate receiver becomes nonfresh *after* reservation.
            // Its token must stay fenced even though its generation is
            // still current. No disk claim or reward is performed here.
            WorldPlayer dirty=new WorldPlayer();
            long dirtyGeneration=restart.registerPlayer(
                dirty,"g2192-dirty");
            WorldPlayerPersistence.CommittedRecoveryReservation dirtyToken=
                restart.persistence().reserveCommittedRecovery(
                    dirty,dirtyGeneration,"g2192-dirty");
            dirtyToken.drained().get(5,TimeUnit.SECONDS);
            dirty.mailbox().deliver(new RewardDeliveryMessage(
                "g2192-dirty-marker","Marker","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,1)),
                "CUSTOM_LOCALLAB_G2192_FIXTURE"));
            dirtyReleaseDenied=!dirtyToken.cancelIfStillFresh()&&
                dirtyToken.isActive();
            restart.unregisterPlayer(dirty,dirtyGeneration);

            noReservationLeaks=restart.persistence()
                .committedRecoveryReservationsForTest()==2;
        }finally{
            releaseRead.countDown();
            noLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2192_COMMITTED_RECOVERY_FENCE_DIAGNOSTICS"+
            " fifoWaited="+fifoWaited+
            " duplicateDenied="+duplicateDenied+
            " earlyCancelDenied="+earlyCancelDenied+
            " ordinarySaveDenied="+ordinarySaveDenied+
            " stableNoGrantAudit="+stableAudit+
            " safeCancel="+releaseWorked+
            " staleGenerationCannotCancel="+staleReleaseDenied+
            " dirtyReceiverCannotCancel="+dirtyReleaseDenied+
            " staleAndDirtyFencesRetained="+noReservationLeaks+
            " sourceUnclaimed="+sourceUnclaimed+
            " accountJournalCommitUnchanged="+unchangedDisk+
            " noJvmLeases="+noLeaks);
        if(!(fifoWaited&&duplicateDenied&&earlyCancelDenied&&
             ordinarySaveDenied&&stableAudit&&releaseWorked&&
             staleReleaseDenied&&dirtyReleaseDenied&&noReservationLeaks&&
             sourceUnclaimed&&
             unchangedDisk&&noLeaks))
            throw new AssertionError("G21.92 recovery write fence failed");
        System.out.println("G2192_RECOVERY_WRITE_FENCE_PASS"+
            " worldGenerationBound=true fifoBarrier=true"+
            " writeQuarantine=true session=false liveApply=false"+
            " grant=false replay=false release=false ack=false");
    }
}

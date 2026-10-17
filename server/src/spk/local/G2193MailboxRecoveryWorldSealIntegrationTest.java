package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class G2193MailboxRecoveryWorldSealIntegrationTest {
    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("G21.93 "+reason);
    }
    private static WorldPlayer makeCommitted(
        World original,String account,
        FilePlayerRepository.PathResolver paths,
        StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord record
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=original.registerPlayer(player,account);
        player.mailbox().deliver(new RewardDeliveryMessage(
            account+":gift","No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2193_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            player.mailbox().get(account+":gift");
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,row));
        MailboxSettlementPostimagePlanner.Proposal p=
            MailboxSettlementPostimagePlanner.plan(
                player,generation,row);
        writer.saveStrict(p.preparedPreimage);
        journal.publishPreparedIntent(p);
        PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot.compose(p);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            writer.saveStrictTerminalForWorld(terminal,
                paths.resolve(account),
                StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                    p.preparedPreimage),()->{},()->{});
        check(receipt.matchesSnapshot(terminal),"fixture receipt");
        record.recordConfirmedDiskTerminal(p,receipt);
        return player;
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2193-seal-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord record=
            new MailboxGuardedDiskCommitRecord(paths);
        MailboxCommittedDetachedRestartRecovery restorer=
            new MailboxCommittedDetachedRestartRecovery(paths);
        String[] accounts={
            "g2193-seal","g2193-replay",
            "g2193-stale","g2193-dirty"
        };
        boolean seal=false,doubleSeal=false,noCancel=false;
        boolean foreignEvidence=false,replayVeto=false;
        boolean retiredVeto=false,dirtyVeto=false;
        boolean writeVeto=false,allFalse=false;
        boolean unchanged=false,unclaimed=false;
        boolean retained=false,leasesZero=false;
        Map<String,byte[][]> before=new HashMap<>();
        try(World original=World.isolatedForTest(60000L);
            World live=World.isolatedForTest(60000L,
                new FilePlayerRepository(paths))){
            Map<String,WorldPlayer> owners=new HashMap<>();
            for(String account:accounts){
                owners.put(account,makeCommitted(original,account,paths,
                    writer,journal,record));
                before.put(account,new byte[][]{
                    Files.readAllBytes(paths.resolve(account)),
                    Files.readAllBytes(journal.journalPath(account)),
                    Files.readAllBytes(record.recordPath(account))
                });
            }
            WorldPlayer fresh=new WorldPlayer();
            long gen=live.registerPlayer(fresh,"g2193-seal");
            WorldPlayerPersistence.CommittedRecoveryReservation token=
                live.persistence().reserveCommittedRecovery(
                    fresh,gen,"g2193-seal");
            token.drained().get(5,TimeUnit.SECONDS);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence evidence=
                token.inspectOneShotHandoff(restorer);
            allFalse=!evidence.transactionCommitted&&
                !evidence.liveApplied&&!evidence.grantAuthorized&&
                !evidence.replayAuthorized&&
                !evidence.restartAdmissionAuthorized&&
                !evidence.releaseAuthorized&&!evidence.clientAckAuthorized;

            WorldPlayer r=new WorldPlayer();
            long rgen=live.registerPlayer(r,"g2193-replay");
            WorldPlayerPersistence.CommittedRecoveryReservation old=
                live.persistence().reserveCommittedRecovery(
                    r,rgen,"g2193-replay");
            old.drained().get(5,TimeUnit.SECONDS);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence oldProof=
                old.inspectOneShotHandoff(restorer);
            check(old.cancelIfStillFresh(),
                "unsealed owner can cancel while unchanged");
            WorldPlayerPersistence.CommittedRecoveryReservation freshToken=
                live.persistence().reserveCommittedRecovery(
                    r,rgen,"g2193-replay");
            freshToken.drained().get(5,TimeUnit.SECONDS);
            replayVeto=freshToken.sealNoAdmission(oldProof)==
                WorldPlayerPersistence.RecoverySealDecision
                    .REJECT_EVIDENCE;
            foreignEvidence=token.sealNoAdmission(oldProof)==
                WorldPlayerPersistence.RecoverySealDecision
                    .REJECT_EVIDENCE;
            seal=token.sealNoAdmission(evidence)==
                WorldPlayerPersistence.RecoverySealDecision
                    .SEALED_QUARANTINE_NO_ADMISSION&&
                token.sealedNoAdmission();
            doubleSeal=token.sealNoAdmission(evidence)==
                WorldPlayerPersistence.RecoverySealDecision
                    .REJECT_ALREADY_SEALED;
            noCancel=!token.cancelIfStillFresh()&&token.isActive();
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence newProof=
                freshToken.inspectOneShotHandoff(restorer);
            check(freshToken.sealNoAdmission(newProof)==
                WorldPlayerPersistence.RecoverySealDecision
                    .SEALED_QUARANTINE_NO_ADMISSION,
                "new token accepts only its own evidence");

            live.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            live.commands().submit(fresh,gen,()->{
                ticket.set(live.persistence().captureAndSave(
                    "g2193-seal",fresh,gen,0,
                    "[g2193] ","SEALED_ACCOUNT_TEST"));
            }).get(5,TimeUnit.SECONDS);
            check(ticket.get()!=null,"ordinary World save created");
            try{
                ticket.get().completion.get(5,TimeUnit.SECONDS);
            }catch(java.util.concurrent.ExecutionException expected){
                writeVeto=String.valueOf(
                    expected.getCause().getMessage()).contains(
                        "G21.92 COMMITTED_RECOVERY_WRITE_FENCED");
            }

            WorldPlayer stale=new WorldPlayer();
            long sg=live.registerPlayer(stale,"g2193-stale");
            WorldPlayerPersistence.CommittedRecoveryReservation st=
                live.persistence().reserveCommittedRecovery(
                    stale,sg,"g2193-stale");
            st.drained().get(5,TimeUnit.SECONDS);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence sp=
                st.inspectOneShotHandoff(restorer);
            live.unregisterPlayer(stale,sg);
            retiredVeto=st.sealNoAdmission(sp)==
                WorldPlayerPersistence.RecoverySealDecision
                    .REJECT_NOT_OWNED&&
                !st.cancelIfStillFresh()&&st.isActive();

            WorldPlayer dirty=new WorldPlayer();
            long dg=live.registerPlayer(dirty,"g2193-dirty");
            WorldPlayerPersistence.CommittedRecoveryReservation dt=
                live.persistence().reserveCommittedRecovery(
                    dirty,dg,"g2193-dirty");
            dt.drained().get(5,TimeUnit.SECONDS);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence dp=
                dt.inspectOneShotHandoff(restorer);
            dirty.mailbox().deliver(new RewardDeliveryMessage(
                "g2193-dirty-marker","Marker","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,1)),
                "CUSTOM_LOCALLAB_G2193_FIXTURE"));
            dirtyVeto=dt.sealNoAdmission(dp)==
                WorldPlayerPersistence.RecoverySealDecision
                    .REJECT_NONFRESH&&
                !dt.cancelIfStillFresh()&&dt.isActive();

            unchanged=true;
            unclaimed=true;
            for(String account:accounts){
                byte[][] bytes=before.get(account);
                unchanged&=Arrays.equals(bytes[0],
                    Files.readAllBytes(paths.resolve(account)))&&
                    Arrays.equals(bytes[1],
                    Files.readAllBytes(journal.journalPath(account)))&&
                    Arrays.equals(bytes[2],
                    Files.readAllBytes(record.recordPath(account)));
                WorldPlayer p=owners.get(account);
                unclaimed&=p.bank().inventorySlots()==0&&
                    p.mailbox().get(account+":gift").claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            }
            retained=live.persistence()
                .committedRecoveryReservationsForTest()==4;
        }finally{
            leasesZero=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2193_WORLD_SEAL_DIAGNOSTICS"+
            " sealed="+seal+
            " duplicateSealBlocked="+doubleSeal+
            " sealedCancelBlocked="+noCancel+
            " foreignEvidenceBlocked="+foreignEvidence+
            " crossTokenReplayBlocked="+replayVeto+
            " retiredGenerationBlocked="+retiredVeto+
            " dirtyReceiverBlocked="+dirtyVeto+
            " ordinaryWorldSaveBlocked="+writeVeto+
            " allAuthorityFalse="+allFalse+
            " diskBytesUnchanged="+unchanged+
            " ownersUnclaimed="+unclaimed+
            " quarantinesRetained="+retained+
            " noJvmLeases="+leasesZero);
        check(seal&&doubleSeal&&noCancel&&foreignEvidence&&
            replayVeto&&retiredVeto&&dirtyVeto&&writeVeto&&
            allFalse&&unchanged&&unclaimed&&retained&&leasesZero,
            "single-use World/persistence recovery seal regression");
        System.out.println("G2193_WORLD_PERSISTENCE_SEAL_PASS"+
            " oneShot=true WorldOwnership=true FIFO=true"+
            " noDiskIoUnderWorldLock=true liveApply=false"+
            " session=false grant=false replay=false release=false ack=false");
    }
}

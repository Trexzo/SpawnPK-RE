package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Real G21.66 terminal receipt -> write-once G21.86 disk record. */
public final class G2186MailboxGuardedDiskCommitIntegrationTest {
    private static final MailboxGuardedDiskCommitRecord.Phase[] CUTS={
        MailboxGuardedDiskCommitRecord.Phase.BEFORE_TEMP_CREATE,
        MailboxGuardedDiskCommitRecord.Phase.BEFORE_LINK,
        MailboxGuardedDiskCommitRecord.Phase.AFTER_LINK,
        MailboxGuardedDiskCommitRecord.Phase.BEFORE_DIRECTORY_FORCE,
        MailboxGuardedDiskCommitRecord.Phase.AFTER_DIRECTORY_FORCE
    };

    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer player,long gen,
             MailboxSettlementPostimagePlanner.Proposal p){
            this.player=player;generation=gen;proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2186-disk-commit-");
        FilePlayerRepository.PathResolver paths=
            a->root.resolve(a+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal preparedJournal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(paths);
        boolean prelinkAbsent=true,postlinkUnconfirmed=true;
        boolean worldStrictReceiptReal=false;
        boolean diskCommitFoundAcrossRestart=false;
        boolean duplicateNoClobber=false;
        boolean missingReceiptRefused=false;
        boolean foreignReceiptRefused=false;
        boolean missingJournalRefused=false;
        boolean divergedAccountRefused=false;
        boolean removedJournalQuarantine=false;
        boolean preimageRollbackNotCommitted=false;
        boolean tamperedRecordQuarantine=false;
        boolean oversizedRecordQuarantine=false;
        boolean symlinkRecordQuarantine=false;
        boolean negativeMarkerWins=false;
        boolean noLiveGrant=true,worldAdmissionRemainsDenied=false;
        boolean noTempOrLeaseLeaks=false,allFlagsNonGrant=true;
        int beforeLink=0,afterLink=0;

        try(World world=World.isolatedForTest(60000L,repository);
            World restarted=World.isolatedForTest(60000L,repository)){
            world.start();
            restarted.start();

            // Exercise the actual World FIFO reserved terminal strict
            // publication for one genuinely confirmed strict Receipt.
            Seed success=seed(world,"g2186-world");
            writer.saveStrict(success.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(success.proposal);
            WorldPlayerPersistence.PreparedAccountReservation reserved=
                world.persistence().reservePreparedAccount(
                    success.player,success.generation,success.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence completed=
                world.persistence().publishReservedTerminalStrictly(
                    reserved,success.proposal,success.terminal,writer)
                    .get(8,TimeUnit.SECONDS);
            worldStrictReceiptReal=completed.fileOperationConfirmed&&
                completed.strictReceipt.matchesSnapshot(success.terminal)&&
                !completed.grantAuthorized&&!completed.replayAuthorized;
            commit.recordConfirmedDiskTerminal(
                success.proposal,completed.strictReceipt);
            byte[] original=Files.readAllBytes(
                commit.recordPath(success.proposal.account));
            MailboxGuardedDiskCommitRecord.Observation persisted=
                new MailboxGuardedDiskCommitRecord(paths)
                    .inspect(success.proposal.account);
            MailboxGuardedDiskCommitRecord.Observation independently=
                new MailboxGuardedDiskCommitRecord(paths)
                    .inspect(success.proposal.account);
            diskCommitFoundAcrossRestart=
                persisted.status==MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                independently.status==persisted.status&&
                persisted.recordValidated&&
                persisted.diskCommitRecordMatched&&
                persisted.intentKey.equals(success.proposal.idempotencyKey)&&
                persisted.messageId.equals(success.proposal.messageId)&&
                persisted.terminalSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(success.terminal))&&
                noAuthority(persisted)&&noAuthority(independently);
            boolean duplicateRefused=false;
            try{
                commit.recordConfirmedDiskTerminal(
                    success.proposal,completed.strictReceipt);
            }catch(IOException deny){
                duplicateRefused=deny.getMessage().contains(
                    "COMMIT_RECORD_ALREADY_EXISTS");
            }
            duplicateNoClobber=duplicateRefused&&Arrays.equals(
                original,Files.readAllBytes(
                    commit.recordPath(success.proposal.account)));
            worldAdmissionRemainsDenied=
                rejectsSession(restarted,success.proposal.account);
            allFlagsNonGrant&=noAuthority(persisted)&&
                noAuthority(independently);

            for(int i=0;i<=CUTS.length;i++){
                String account="g2186-phase-"+i;
                Seed s=seed(world,account);
                writer.saveStrict(s.proposal.preparedPreimage);
                preparedJournal.publishPreparedIntent(s.proposal);
                StrictDurablePlayerSnapshotWriter.Receipt receipt=
                    confirmedTerminal(writer,paths,s);
                MailboxGuardedDiskCommitRecord.Phase cut=
                    i==CUTS.length?null:CUTS[i];
                AtomicInteger fired=new AtomicInteger();
                MailboxGuardedDiskCommitRecord injected=
                    new MailboxGuardedDiskCommitRecord(paths,p->{
                        if(p==cut){
                            fired.incrementAndGet();
                            throw new IOException("G21.86 injected "+p);
                        }
                    });
                boolean early=false,uncertain=false;
                try{
                    injected.recordConfirmedDiskTerminal(s.proposal,receipt);
                }catch(MailboxGuardedDiskCommitRecord
                        .UnconfirmedRecordException e){
                    uncertain=true;
                }catch(IOException rejected){
                    early=true;
                }
                if(cut!=null&&fired.get()!=1)
                    throw new AssertionError(
                        "G21.86 fault point not reached "+cut);
                boolean fileExists=Files.exists(
                    commit.recordPath(account),LinkOption.NOFOLLOW_LINKS);
                MailboxGuardedDiskCommitRecord.Observation seen=
                    new MailboxGuardedDiskCommitRecord(paths)
                        .inspect(account);
                allFlagsNonGrant&=noAuthority(seen);
                if(i<2){
                    beforeLink++;
                    prelinkAbsent&=early&&!uncertain&&!fileExists&&
                        seen.status==MailboxGuardedDiskCommitRecord.Status
                            .ABSENT;
                }else if(i<CUTS.length){
                    afterLink++;
                    postlinkUnconfirmed&=uncertain&&!early&&fileExists&&
                        seen.status==MailboxGuardedDiskCommitRecord.Status
                            .DISK_COMMIT_MATCH_NO_LIVE_APPLY;
                }else{
                    postlinkUnconfirmed&=!uncertain&&!early&&fileExists&&
                        seen.status==MailboxGuardedDiskCommitRecord.Status
                            .DISK_COMMIT_MATCH_NO_LIVE_APPLY;
                }
                noLiveGrant&=s.player.bank().inventorySlots()==0&&
                    s.player.mailbox().get(s.proposal.messageId)
                        .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            }

            Seed noReceipt=seed(world,"g2186-no-receipt");
            writer.saveStrict(noReceipt.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(noReceipt.proposal);
            confirmedTerminal(writer,paths,noReceipt);
            try{
                commit.recordConfirmedDiskTerminal(noReceipt.proposal,null);
            }catch(IOException refused){
                missingReceiptRefused=refused.getMessage().contains(
                    "MISSING_STRICT_RECEIPT")&&
                    !Files.exists(commit.recordPath(
                        noReceipt.proposal.account));
            }
            Seed foreign=seed(world,"g2186-foreign");
            writer.saveStrict(foreign.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(foreign.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt foreignReceipt=
                confirmedTerminal(writer,paths,foreign);
            try{
                commit.recordConfirmedDiskTerminal(
                    noReceipt.proposal,foreignReceipt);
            }catch(IOException refused){
                foreignReceiptRefused=refused.getMessage().contains(
                    "RECEIPT_TERMINAL_OR_PATH_MISMATCH");
            }

            Seed noJournal=seed(world,"g2186-no-journal");
            writer.saveStrict(noJournal.proposal.preparedPreimage);
            StrictDurablePlayerSnapshotWriter.Receipt journalLessReceipt=
                confirmedTerminal(writer,paths,noJournal);
            try{
                commit.recordConfirmedDiskTerminal(
                    noJournal.proposal,journalLessReceipt);
            }catch(IOException denied){
                missingJournalRefused=denied.getMessage().contains(
                    "PREPARED_INTENT_MISMATCH")&&
                    !Files.exists(commit.recordPath(
                        noJournal.proposal.account));
            }

            Seed diverged=seed(world,"g2186-diverged");
            writer.saveStrict(diverged.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(diverged.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt stale=
                confirmedTerminal(writer,paths,diverged);
            // Raw test-only writer simulates foreign tampering after
            // confirmed file operation: never trust old receipt alone.
            writer.saveStrict(diverged.proposal.preparedPreimage);
            try{
                commit.recordConfirmedDiskTerminal(diverged.proposal,stale);
            }catch(IOException rejected){
                divergedAccountRefused=rejected.getMessage().contains(
                    "PREPARED_INTENT_MISMATCH")||
                    rejected.getMessage().contains(
                        "DISK_NOT_EXACT_TERMINAL");
            }

            Seed withoutIntent=seed(world,"g2186-remove-intent");
            writer.saveStrict(withoutIntent.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(withoutIntent.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt removedReceipt=
                confirmedTerminal(writer,paths,withoutIntent);
            commit.recordConfirmedDiskTerminal(
                withoutIntent.proposal,removedReceipt);
            Files.delete(preparedJournal.journalPath(
                withoutIntent.proposal.account));
            MailboxGuardedDiskCommitRecord.Observation missingJournal=
                commit.inspect(withoutIntent.proposal.account);
            removedJournalQuarantine=
                missingJournal.status==MailboxGuardedDiskCommitRecord.Status
                    .INTENT_CONFLICT_QUARANTINE&&noAuthority(missingJournal);

            Seed rolledBack=seed(world,"g2186-rollback");
            writer.saveStrict(rolledBack.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(rolledBack.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt prior=
                confirmedTerminal(writer,paths,rolledBack);
            commit.recordConfirmedDiskTerminal(rolledBack.proposal,prior);
            writer.saveStrict(rolledBack.proposal.preparedPreimage);
            MailboxGuardedDiskCommitRecord.Observation rollback=
                commit.inspect(rolledBack.proposal.account);
            preimageRollbackNotCommitted=
                rollback.status==MailboxGuardedDiskCommitRecord.Status
                    .PREPARED_NOT_COMMITTED&&noAuthority(rollback);

            Seed altered=seed(world,"g2186-corrupt");
            writer.saveStrict(altered.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(altered.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt alteredReceipt=
                confirmedTerminal(writer,paths,altered);
            commit.recordConfirmedDiskTerminal(
                altered.proposal,alteredReceipt);
            Path bad=commit.recordPath(altered.proposal.account);
            byte[] wrong=Files.readAllBytes(bad);
            wrong[0]^=1;
            Files.write(bad,wrong);
            MailboxGuardedDiskCommitRecord.Observation invalid=
                commit.inspect(altered.proposal.account);
            tamperedRecordQuarantine=
                invalid.status==MailboxGuardedDiskCommitRecord.Status
                    .INVALID_RECORD_QUARANTINE&&noAuthority(invalid);

            Seed large=seed(world,"g2186-oversize");
            writer.saveStrict(large.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(large.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt largeReceipt=
                confirmedTerminal(writer,paths,large);
            commit.recordConfirmedDiskTerminal(large.proposal,largeReceipt);
            Files.write(commit.recordPath(large.proposal.account),
                new byte[1025]);
            MailboxGuardedDiskCommitRecord.Observation oversized=
                commit.inspect(large.proposal.account);
            oversizedRecordQuarantine=
                oversized.status==MailboxGuardedDiskCommitRecord.Status
                    .INVALID_RECORD_QUARANTINE&&noAuthority(oversized);

            Seed marked=seed(world,"g2186-negative");
            writer.saveStrict(marked.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(marked.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt markedReceipt=
                confirmedTerminal(writer,paths,marked);
            commit.recordConfirmedDiskTerminal(marked.proposal,markedReceipt);
            Path hold=paths.resolve(marked.proposal.account).resolveSibling(
                marked.proposal.account+".properties"+
                MailboxStrictUncertainFence.SUFFIX);
            byte[] holdBytes="G2186_NEGATIVE_HOLD".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(hold,holdBytes);
            MailboxGuardedDiskCommitRecord.Observation blocked=
                commit.inspect(marked.proposal.account);
            negativeMarkerWins=
                blocked.status==MailboxGuardedDiskCommitRecord.Status
                    .NEGATIVE_MARKER_MANUAL_HOLD&&noAuthority(blocked)&&
                Arrays.equals(holdBytes,Files.readAllBytes(hold))&&
                rejectsSession(restarted,marked.proposal.account);

            Seed linked=seed(world,"g2186-symlink");
            writer.saveStrict(linked.proposal.preparedPreimage);
            preparedJournal.publishPreparedIntent(linked.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt linkReceipt=
                confirmedTerminal(writer,paths,linked);
            commit.recordConfirmedDiskTerminal(linked.proposal,linkReceipt);
            Path link=commit.recordPath(linked.proposal.account);
            try{
                Files.delete(link);
                Files.createSymbolicLink(link,commit.recordPath(
                    success.proposal.account));
                MailboxGuardedDiskCommitRecord.Observation symlink=
                    commit.inspect(linked.proposal.account);
                symlinkRecordQuarantine=
                    symlink.status==MailboxGuardedDiskCommitRecord.Status
                        .INVALID_RECORD_QUARANTINE&&noAuthority(symlink);
            }catch(java.nio.file.FileSystemException|
                    UnsupportedOperationException unsupported){
                symlinkRecordQuarantine=!Files.isSymbolicLink(link);
            }

            noLiveGrant&=
                success.player.bank().inventorySlots()==0&&
                success.player.mailbox().get(success.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                marked.player.bank().inventorySlots()==0&&
                noReceipt.player.bank().inventorySlots()==0;
            try(Stream<Path> stream=Files.walk(root)){
                noTempOrLeaseLeaks=stream.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }

        System.out.println("G2186_DISK_COMMIT_DIAGNOSTICS"+
            " worldStrictReceiptReal="+worldStrictReceiptReal+
            " diskCommitFoundAcrossRestart="+diskCommitFoundAcrossRestart+
            " beforeLink="+beforeLink+" afterLink="+afterLink+
            " prelinkAbsent="+prelinkAbsent+
            " postlinkUnconfirmed="+postlinkUnconfirmed+
            " duplicateNoClobber="+duplicateNoClobber+
            " missingReceiptRefused="+missingReceiptRefused+
            " foreignReceiptRefused="+foreignReceiptRefused+
            " missingJournalRefused="+missingJournalRefused+
            " divergedAccountRefused="+divergedAccountRefused+
            " removedJournalQuarantine="+removedJournalQuarantine+
            " preimageRollbackNotCommitted="+preimageRollbackNotCommitted+
            " tamperedRecordQuarantine="+tamperedRecordQuarantine+
            " oversizedRecordQuarantine="+oversizedRecordQuarantine+
            " symlinkRecordQuarantine="+symlinkRecordQuarantine+
            " negativeMarkerWins="+negativeMarkerWins+
            " worldAdmissionRemainsDenied="+worldAdmissionRemainsDenied+
            " noLiveGrant="+noLiveGrant+
            " allFlagsNonGrant="+allFlagsNonGrant+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(worldStrictReceiptReal&&diskCommitFoundAcrossRestart&&
             beforeLink==2&&afterLink==3&&prelinkAbsent&&
             postlinkUnconfirmed&&duplicateNoClobber&&
             missingReceiptRefused&&foreignReceiptRefused&&
             missingJournalRefused&&divergedAccountRefused&&
             removedJournalQuarantine&&preimageRollbackNotCommitted&&
             tamperedRecordQuarantine&&oversizedRecordQuarantine&&
             symlinkRecordQuarantine&&negativeMarkerWins&&
             worldAdmissionRemainsDenied&&noLiveGrant&&
             allFlagsNonGrant&&noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.86 receipt-guarded disk COMMIT record regression failed");
        System.out.println("G2186_DISK_COMMIT_PASS"+
            " recordWriteOnce=true strictFileReceipt=true"+
            " intentBound=true restartReadable=true"+
            " settlementCommit=false replay=false liveGrant=false"+
            " admission=false release=false ack=false");
    }

    private static StrictDurablePlayerSnapshotWriter.Receipt
        confirmedTerminal(
            StrictDurablePlayerSnapshotWriter writer,
            FilePlayerRepository.PathResolver paths,Seed seed
        )throws IOException{
        return writer.saveStrictTerminalForWorld(
            seed.terminal,paths.resolve(seed.proposal.account),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                seed.proposal.preparedPreimage),
            ()->{},()->{}
        );
    }

    private static boolean rejectsSession(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){
            return true;
        }
    }
    private static boolean noAuthority(
        MailboxGuardedDiskCommitRecord.Observation value
    ){
        return !value.transactionCommitted&&!value.liveApplied&&
            !value.grantAuthorized&&!value.replayAuthorized&&
            !value.rollbackAuthorized&&
            !value.restartAdmissionAuthorized&&
            !value.releaseAuthorized&&!value.clientAckAuthorized;
    }

    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String messageId=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"Disk commit fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2186_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            player.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,row));
        return new Seed(player,generation,
            MailboxSettlementPostimagePlanner.plan(
                player,generation,row));
    }
}

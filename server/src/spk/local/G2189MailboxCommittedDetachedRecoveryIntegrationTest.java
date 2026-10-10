package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.89 genuine disk COMMIT -> independent detached restart replay-proof read. */
public final class G2189MailboxCommittedDetachedRecoveryIntegrationTest {
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,MailboxSettlementPostimagePlanner.Proposal proposal){
            owner=p;
            this.proposal=proposal;
            terminal=MailboxAtomicTerminalSnapshot.compose(proposal);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2189-detached-restart-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord disk=
            new MailboxGuardedDiskCommitRecord(paths);
        MailboxCommittedDetachedRestartRecovery restorer=
            new MailboxCommittedDetachedRestartRecovery(paths);
        boolean repeatedDetachedExact=false;
        boolean crossInstanceExact=false;
        boolean inventoryNotDuplicated=false;
        boolean originalWorldOwnerUnchanged=false;
        boolean worldAdmissionStillBlocked=false;
        boolean missingCommitRejected=false;
        boolean preparedRollbackRejected=false;
        boolean missingIntentRejected=false;
        boolean negativeMarkerRejected=false;
        boolean corruptCommitRejected=false;
        boolean oversizedCommitRejected=false;
        boolean symlinkCommitRejected=false;
        boolean foreignAccountRejected=false;
        boolean missingAccountRejected=false;
        boolean fileBytesUnchanged=false;
        boolean noPositiveAuthority=true;
        boolean noTempOrLeaseLeaks=false;
        int restores=0;

        try(World fixture=World.isolatedForTest(60000L);
            World newWorld=World.isolatedForTest(60000L,repository)){
            newWorld.start();
            Seed committed=seed(fixture,"g2189-committed");
            persistCommit(writer,journal,disk,paths,committed);
            byte[] before=Files.readAllBytes(
                paths.resolve(committed.proposal.account));
            byte[] beforeRecord=Files.readAllBytes(
                disk.recordPath(committed.proposal.account));
            byte[] beforeIntent=Files.readAllBytes(
                journal.journalPath(committed.proposal.account));

            String firstFingerprint=null;
            int firstOccupied=-1;
            for(int i=0;i<5;i++){
                MailboxCommittedDetachedRestartRecovery.Result read=
                    new MailboxCommittedDetachedRestartRecovery(paths)
                        .recoverDetached(committed.proposal.account);
                restores++;
                String fingerprint=StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(read.exactRestoredSnapshot);
                noPositiveAuthority&=noAuthority(read);
                if(i==0){
                    firstFingerprint=fingerprint;
                    firstOccupied=read.occupiedInventorySlots;
                }
                repeatedDetachedExact=
                    (i==0||repeatedDetachedExact)&&
                    fingerprint.equals(firstFingerprint)&&
                    read.exactRestoredSnapshot.values().equals(
                        committed.terminal.values())&&
                    read.terminalSha256.equals(firstFingerprint)&&
                    read.messageId.equals(committed.proposal.messageId)&&
                    read.idempotencyKey.equals(
                        committed.proposal.idempotencyKey)&&
                    read.claimedBeforeAnyReplay&&read.detachedRoundTrip&&
                    read.occupiedInventorySlots==firstOccupied;

                // Hydrate onto DIFFERENT fresh detached objects five
                // times: persisted inventory is assigned, not awarded
                // again on top of an existing balance.
                WorldPlayer projected=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    read.exactRestoredSnapshot,projected);
                int totalCoins=0;
                for(int slot=0;slot<BankState.INVENTORY_CAPACITY;slot++){
                    BankState.InventorySlotSnapshot item=
                        projected.bank().inventorySlotSnapshot(slot);
                    if(item.occupied&&item.itemId==995)
                        totalCoins+=item.quantity;
                }
                boolean exactClaim=projected.mailbox().get(
                    committed.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.CLAIMED;
                inventoryNotDuplicated=
                    (i==0||inventoryNotDuplicated)&&
                    totalCoins==25&&exactClaim;
            }
            MailboxCommittedDetachedRestartRecovery.Result independent=
                restorer.recoverDetached(committed.proposal.account);
            crossInstanceExact=
                independent.exactRestoredSnapshot.values().equals(
                    committed.terminal.values())&&
                independent.occupiedInventorySlots==firstOccupied&&
                noAuthority(independent);
            noPositiveAuthority&=noAuthority(independent);
            fileBytesUnchanged=Arrays.equals(before,
                Files.readAllBytes(paths.resolve(
                    committed.proposal.account)))&&
                Arrays.equals(beforeRecord,Files.readAllBytes(
                    disk.recordPath(committed.proposal.account)))&&
                Arrays.equals(beforeIntent,Files.readAllBytes(
                    journal.journalPath(committed.proposal.account)));
            worldAdmissionStillBlocked=deniesSession(
                newWorld,committed.proposal.account);

            Seed withoutCommit=seed(fixture,"g2189-no-record");
            writer.saveStrict(withoutCommit.proposal.preparedPreimage);
            journal.publishPreparedIntent(withoutCommit.proposal);
            writer.saveStrictTerminalForWorld(
                withoutCommit.terminal,
                paths.resolve(withoutCommit.proposal.account),
                StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                    withoutCommit.proposal.preparedPreimage),
                ()->{},()->{});
            missingCommitRejected=rejects(restorer,
                withoutCommit.proposal.account);

            Seed rollback=seed(fixture,"g2189-rollback");
            persistCommit(writer,journal,disk,paths,rollback);
            repository.save(rollback.proposal.preparedPreimage);
            preparedRollbackRejected=rejects(restorer,
                rollback.proposal.account);

            Seed lostJournal=seed(fixture,"g2189-lost-intent");
            persistCommit(writer,journal,disk,paths,lostJournal);
            Files.delete(journal.journalPath(
                lostJournal.proposal.account));
            missingIntentRejected=rejects(restorer,
                lostJournal.proposal.account);

            Seed reviewed=seed(fixture,"g2189-reviewed");
            persistCommit(writer,journal,disk,paths,reviewed);
            Path negative=paths.resolve(reviewed.proposal.account)
                .resolveSibling(reviewed.proposal.account+
                    ".properties"+MailboxStrictUncertainFence.SUFFIX);
            byte[] reviewBytes="G2189_NEGATIVE_MANUAL".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(negative,reviewBytes);
            negativeMarkerRejected=rejects(restorer,
                reviewed.proposal.account)&&
                Arrays.equals(reviewBytes,Files.readAllBytes(negative));

            Seed corrupted=seed(fixture,"g2189-corrupt");
            persistCommit(writer,journal,disk,paths,corrupted);
            Path corrupt=disk.recordPath(corrupted.proposal.account);
            byte[] content=Files.readAllBytes(corrupt);
            content[0]^=1;
            Files.write(corrupt,content);
            corruptCommitRejected=rejects(restorer,
                corrupted.proposal.account);

            Seed over=seed(fixture,"g2189-oversized");
            persistCommit(writer,journal,disk,paths,over);
            Files.write(disk.recordPath(over.proposal.account),
                new byte[1025]);
            oversizedCommitRejected=rejects(restorer,
                over.proposal.account);

            Seed linked=seed(fixture,"g2189-symlink");
            persistCommit(writer,journal,disk,paths,linked);
            Path link=disk.recordPath(linked.proposal.account);
            try{
                Files.delete(link);
                Files.createSymbolicLink(link,disk.recordPath(
                    committed.proposal.account));
                symlinkCommitRejected=rejects(restorer,
                    linked.proposal.account);
            }catch(java.nio.file.FileSystemException|
                    UnsupportedOperationException unavailable){
                symlinkCommitRejected=!Files.isSymbolicLink(link);
            }
            try{
                restorer.recoverDetached("G2189_Invalid_Name");
            }catch(IllegalArgumentException invalid){
                foreignAccountRejected=true;
            }
            missingAccountRejected=rejects(restorer,
                "g2189-unknown");

            originalWorldOwnerUnchanged=
                committed.owner.bank().inventorySlots()==0&&
                committed.owner.mailbox().get(
                    committed.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                rollback.owner.bank().inventorySlots()==0&&
                reviewed.owner.bank().inventorySlots()==0;
            try(Stream<Path> all=Files.walk(root)){
                noTempOrLeaseLeaks=all.noneMatch(p->
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

        System.out.println("G2189_COMMITTED_RECOVERY_DIAGNOSTICS"+
            " restores="+restores+
            " repeatedDetachedExact="+repeatedDetachedExact+
            " crossInstanceExact="+crossInstanceExact+
            " inventoryNotDuplicated="+inventoryNotDuplicated+
            " originalWorldOwnerUnchanged="+
                originalWorldOwnerUnchanged+
            " worldAdmissionStillBlocked="+worldAdmissionStillBlocked+
            " missingCommitRejected="+missingCommitRejected+
            " preparedRollbackRejected="+preparedRollbackRejected+
            " missingIntentRejected="+missingIntentRejected+
            " negativeMarkerRejected="+negativeMarkerRejected+
            " corruptCommitRejected="+corruptCommitRejected+
            " oversizedCommitRejected="+oversizedCommitRejected+
            " symlinkCommitRejected="+symlinkCommitRejected+
            " foreignAccountRejected="+foreignAccountRejected+
            " missingAccountRejected="+missingAccountRejected+
            " fileBytesUnchanged="+fileBytesUnchanged+
            " noPositiveAuthority="+noPositiveAuthority+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(restores==5&&repeatedDetachedExact&&crossInstanceExact&&
             inventoryNotDuplicated&&originalWorldOwnerUnchanged&&
             worldAdmissionStillBlocked&&missingCommitRejected&&
             preparedRollbackRejected&&missingIntentRejected&&
             negativeMarkerRejected&&corruptCommitRejected&&
             oversizedCommitRejected&&symlinkCommitRejected&&
             foreignAccountRejected&&missingAccountRejected&&
             fileBytesUnchanged&&noPositiveAuthority&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.89 disk COMMIT detached recovery regression failed");
        System.out.println("G2189_DETACHED_COMMIT_RECOVERY_PASS"+
            " repeatedHydrationStable=true noDuplicateItems=true"+
            " diskCommitBound=true liveApply=false"+
            " grant=false replay=false release=false ack=false");
    }

    private static void persistCommit(
        StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord disk,
        FilePlayerRepository.PathResolver paths,Seed s
    )throws IOException{
        writer.saveStrict(s.proposal.preparedPreimage);
        journal.publishPreparedIntent(s.proposal);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            writer.saveStrictTerminalForWorld(
                s.terminal,paths.resolve(s.proposal.account),
                StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                    s.proposal.preparedPreimage),
                ()->{},()->{});
        if(!receipt.matchesSnapshot(s.terminal))
            throw new AssertionError(
                "G21.89 fixture receipt not confirmed");
        disk.recordConfirmedDiskTerminal(s.proposal,receipt);
    }

    private static boolean rejects(
        MailboxCommittedDetachedRestartRecovery restorer,
        String account
    ){
        try{
            restorer.recoverDetached(account);
            return false;
        }catch(IOException expected){
            return expected.getMessage().contains("G21.89 ");
        }
    }

    private static boolean deniesSession(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException expected){
            return true;
        }
    }

    private static boolean noAuthority(
        MailboxCommittedDetachedRestartRecovery.Result read
    ){
        return !read.transactionCommitted&&!read.liveApplied&&
            !read.grantAuthorized&&!read.replayAuthorized&&
            !read.rollbackAuthorized&&
            !read.restartAdmissionAuthorized&&
            !read.releaseAuthorized&&!read.clientAckAuthorized;
    }

    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String message=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            message,"No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2189_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot item=
            player.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,item));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,item));
    }
}

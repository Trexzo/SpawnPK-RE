package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/** Real-file cross-domain restart handoff negative-continuity tests. */
public final class G2191MailboxRestartHandoffAuditIntegrationTest {
    private static final class Seed {
        final WorldPlayer source;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer source,
             MailboxSettlementPostimagePlanner.Proposal proposal){
            this.source=source;
            this.proposal=proposal;
            terminal=MailboxAtomicTerminalSnapshot.compose(proposal);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2191-continuity-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(paths);
        MailboxCommittedDetachedRestartRecovery restorer=
            new MailboxCommittedDetachedRestartRecovery(paths);
        boolean stable=false,accountInodeSwapDenied=false;
        boolean journalInodeSwapDenied=false,commitInodeSwapDenied=false;
        boolean staleGenerationDenied=false,dirtyReceiverDenied=false;
        boolean crossWorldDenied=false,wrongAccountDenied=false;
        boolean tamperedCommitDenied=false,missingRecordDenied=false;
        boolean stateAndFilesUnchanged=false,worldOwnerNoGrant=false;
        boolean noAuthority=false,noLeases=false;
        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L);
            World foreignWorld=World.isolatedForTest(60000L)){
            Seed healthy=seed(fixture,"g2191-stable");
            persist(paths,writer,journal,commit,healthy);
            WorldPlayer fresh=new WorldPlayer();
            long generation=restart.registerPlayer(fresh,"g2191-stable");
            byte[] beforeAccount=Files.readAllBytes(
                paths.resolve("g2191-stable"));
            byte[] beforeJournal=Files.readAllBytes(
                journal.journalPath("g2191-stable"));
            byte[] beforeCommit=Files.readAllBytes(
                commit.recordPath("g2191-stable"));
            PlayerSnapshot freshImage=PlayerSnapshotCodec.capture(
                "g2191-stable",fresh);
            MailboxCommittedRestartHandoffAudit.Result good=
                MailboxCommittedRestartHandoffAudit.inspect(
                    restart,fresh,generation,"g2191-stable",restorer);
            stable=good.state==MailboxCommittedRestartHandoffAudit.State
                    .STABLE_CANDIDATE_NO_ADMISSION&&
                good.worldFirst==MailboxCommittedWorldGenerationCandidate
                    .Decision.CANDIDATE_ONLY_NO_ADMISSION&&
                good.worldLast==MailboxCommittedWorldGenerationCandidate
                    .Decision.CANDIDATE_ONLY_NO_ADMISSION;
            noAuthority=!good.transactionCommitted&&!good.liveApplied&&
                !good.grantAuthorized&&!good.replayAuthorized&&
                !good.restartAdmissionAuthorized&&
                !good.releaseAuthorized&&!good.clientAckAuthorized;
            crossWorldDenied=MailboxCommittedRestartHandoffAudit.inspect(
                foreignWorld,fresh,generation,"g2191-stable",restorer).state==
                    MailboxCommittedRestartHandoffAudit.State
                        .REJECT_FIRST_WORLD;
            stateAndFilesUnchanged=Arrays.equals(beforeAccount,
                Files.readAllBytes(paths.resolve("g2191-stable")))&&
                Arrays.equals(beforeJournal,Files.readAllBytes(
                    journal.journalPath("g2191-stable")))&&
                Arrays.equals(beforeCommit,Files.readAllBytes(
                    commit.recordPath("g2191-stable")))&&
                freshImage.values().equals(
                    PlayerSnapshotCodec.capture("g2191-stable",fresh).values());
            restart.unregisterPlayer(fresh,generation);

            Seed accountChanged=seed(fixture,"g2191-account-swap");
            persist(paths,writer,journal,commit,accountChanged);
            accountInodeSwapDenied=swappedFileDenied(
                restart,restorer,"g2191-account-swap",
                paths.resolve("g2191-account-swap"));
            Seed journalChanged=seed(fixture,"g2191-journal-swap");
            persist(paths,writer,journal,commit,journalChanged);
            journalInodeSwapDenied=swappedFileDenied(
                restart,restorer,"g2191-journal-swap",
                journal.journalPath("g2191-journal-swap"));
            Seed commitChanged=seed(fixture,"g2191-commit-swap");
            persist(paths,writer,journal,commit,commitChanged);
            commitInodeSwapDenied=swappedFileDenied(
                restart,restorer,"g2191-commit-swap",
                commit.recordPath("g2191-commit-swap"));

            Seed stale=seed(fixture,"g2191-stale");
            persist(paths,writer,journal,commit,stale);
            WorldPlayer retired=new WorldPlayer();
            long retiredGeneration=restart.registerPlayer(
                retired,"g2191-stale");
            MailboxCommittedRestartHandoffAudit.Result staleAudit=
                MailboxCommittedRestartHandoffAudit.inspect(
                    restart,retired,retiredGeneration,"g2191-stale",
                    restorer,()->{},()->{
                        restart.unregisterPlayer(retired,retiredGeneration);
                    });
            staleGenerationDenied=staleAudit.state==
                MailboxCommittedRestartHandoffAudit.State.REJECT_FINAL_WORLD&&
                staleAudit.worldLast==MailboxCommittedWorldGenerationCandidate
                    .Decision.REJECT_NOT_OWNED;

            Seed dirty=seed(fixture,"g2191-dirty");
            persist(paths,writer,journal,commit,dirty);
            WorldPlayer used=new WorldPlayer();
            long usedGeneration=restart.registerPlayer(used,"g2191-dirty");
            MailboxCommittedRestartHandoffAudit.Result dirtyAudit=
                MailboxCommittedRestartHandoffAudit.inspect(
                    restart,used,usedGeneration,"g2191-dirty",restorer,
                    ()->{},()->{
                        used.mailbox().deliver(new RewardDeliveryMessage(
                            "g2191-mark-dirty","Marker","NO_GRANT",
                            Collections.singletonList(
                                new RewardDeliveryMessage.Attachment(995,1)),
                            "CUSTOM_LOCALLAB_G2191_FIXTURE"));
                    });
            dirtyReceiverDenied=dirtyAudit.state==
                MailboxCommittedRestartHandoffAudit.State.REJECT_FINAL_WORLD&&
                dirtyAudit.worldLast==MailboxCommittedWorldGenerationCandidate
                    .Decision.REJECT_NONFRESH_RECEIVER;
            restart.unregisterPlayer(used,usedGeneration);

            WorldPlayer incorrect=new WorldPlayer();
            long wrongGen=restart.registerPlayer(incorrect,
                "g2191-wrong-owner");
            wrongAccountDenied=MailboxCommittedRestartHandoffAudit.inspect(
                restart,incorrect,wrongGen,"g2191-stable",restorer).state==
                    MailboxCommittedRestartHandoffAudit.State
                        .REJECT_FIRST_WORLD;
            restart.unregisterPlayer(incorrect,wrongGen);

            Seed corrupt=seed(fixture,"g2191-corrupt");
            persist(paths,writer,journal,commit,corrupt);
            WorldPlayer corruptReceiver=new WorldPlayer();
            long corruptGeneration=restart.registerPlayer(
                corruptReceiver,"g2191-corrupt");
            try{
                MailboxCommittedRestartHandoffAudit.inspect(
                    restart,corruptReceiver,corruptGeneration,
                    "g2191-corrupt",restorer,()->{
                        Files.write(commit.recordPath("g2191-corrupt"),
                            new byte[]{(byte)0x31});
                    },()->{});
            }catch(IOException expected){
                tamperedCommitDenied=true;
            }
            restart.unregisterPlayer(corruptReceiver,corruptGeneration);

            Seed missing=seed(fixture,"g2191-no-commit");
            persist(paths,writer,journal,commit,missing);
            Files.delete(commit.recordPath("g2191-no-commit"));
            WorldPlayer absentReceiver=new WorldPlayer();
            long absentGeneration=restart.registerPlayer(
                absentReceiver,"g2191-no-commit");
            try{
                MailboxCommittedRestartHandoffAudit.inspect(
                    restart,absentReceiver,absentGeneration,
                    "g2191-no-commit",restorer);
            }catch(IOException expected){
                missingRecordDenied=true;
            }
            restart.unregisterPlayer(absentReceiver,absentGeneration);
            worldOwnerNoGrant=healthy.source.bank().inventorySlots()==0&&
                healthy.source.mailbox().get("g2191-stable:gift")
                    .claimState==MailboxRewardDeliveryService.ClaimState
                        .UNCLAIMED&&
                accountChanged.source.bank().inventorySlots()==0&&
                stale.source.bank().inventorySlots()==0;
        }finally{
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path path:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("G2191_RESTART_HANDOFF_DIAGNOSTICS"+
            " stable="+stable+
            " accountSwapBlocked="+accountInodeSwapDenied+
            " journalSwapBlocked="+journalInodeSwapDenied+
            " commitSwapBlocked="+commitInodeSwapDenied+
            " staleWorldGenerationBlocked="+staleGenerationDenied+
            " dirtyReceiverBlocked="+dirtyReceiverDenied+
            " crossWorldBlocked="+crossWorldDenied+
            " wrongAccountBlocked="+wrongAccountDenied+
            " corruptCommitBlocked="+tamperedCommitDenied+
            " missingRecordBlocked="+missingRecordDenied+
            " healthyFilesAndReceiverUnchanged="+stateAndFilesUnchanged+
            " originalWorldOwnerNoGrant="+worldOwnerNoGrant+
            " noAuthority="+noAuthority+
            " noJvmLeases="+noLeases);
        if(!(stable&&accountInodeSwapDenied&&journalInodeSwapDenied&&
             commitInodeSwapDenied&&staleGenerationDenied&&
             dirtyReceiverDenied&&crossWorldDenied&&wrongAccountDenied&&
             tamperedCommitDenied&&missingRecordDenied&&
             stateAndFilesUnchanged&&worldOwnerNoGrant&&noAuthority&&
             noLeases))
            throw new AssertionError(
                "G21.91 restart handoff continuity regression failed");
        System.out.println("G2191_HANDOFF_CONTINUITY_PASS"+
            " twoDiskReads=true twoWorldChecks=true threeFileKeys=true"+
            " grant=false liveApply=false session=false"+
            " replay=false release=false ack=false");
    }

    private static boolean swappedFileDenied(
        World world,MailboxCommittedDetachedRestartRecovery restorer,
        String account,Path selected
    )throws Exception{
        WorldPlayer receiver=new WorldPlayer();
        long generation=world.registerPlayer(receiver,account);
        try{
            MailboxCommittedRestartHandoffAudit.Result result=
                MailboxCommittedRestartHandoffAudit.inspect(
                    world,receiver,generation,account,restorer,()->{
                        // Same bytes, NEW inode: checksum-only continuity
                        // would miss this between independent reads.
                        Path temp=selected.resolveSibling(
                            selected.getFileName()+".g2191-replacement");
                        Files.copy(selected,temp);
                        Files.move(temp,selected,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    },()->{});
            return result.state==MailboxCommittedRestartHandoffAudit.State
                .REJECT_DISK_OBJECT_CHANGED;
        }finally{
            world.unregisterPlayer(receiver,generation);
        }
    }

    private static Seed seed(World fixture,String account){
        WorldPlayer source=new WorldPlayer();
        long generation=fixture.registerPlayer(source,account);
        String message=account+":gift";
        source.mailbox().deliver(new RewardDeliveryMessage(
            message,"No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2191_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot item=
            source.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(source,
            MailboxPreparedClaimJournal.prepare(source,item));
        return new Seed(source,MailboxSettlementPostimagePlanner.plan(
            source,generation,item));
    }

    private static void persist(
        FilePlayerRepository.PathResolver paths,
        StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord commit,
        Seed seed
    )throws IOException{
        writer.saveStrict(seed.proposal.preparedPreimage);
        journal.publishPreparedIntent(seed.proposal);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            writer.saveStrictTerminalForWorld(
                seed.terminal,paths.resolve(seed.proposal.account),
                StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                    seed.proposal.preparedPreimage),()->{},()->{});
        if(!receipt.matchesSnapshot(seed.terminal))
            throw new AssertionError(
                "G21.91 fixture strict receipt absent");
        commit.recordConfirmedDiskTerminal(seed.proposal,receipt);
    }
}

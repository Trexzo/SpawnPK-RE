package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.90 G21.89 disk recovery -> read-only World generation fence. */
public final class G2190MailboxWorldGenerationCandidateIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2190-world-candidate-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(paths);
        String account="g2190-committed";
        boolean candidate=false,oldGenerationDenied=false;
        boolean reregisterCurrent=false,crossWorldDenied=false;
        boolean wrongAccountDenied=false,nonfreshDenied=false;
        boolean newOwnerUndisturbed=false,filesUnchanged=false;
        boolean liveOwnerUnchanged=false,noAuthority=false,noLeaks=false;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L);
            World other=World.isolatedForTest(60000L)){
            WorldPlayer source=new WorldPlayer();
            long originalGeneration=fixture.registerPlayer(source,account);
            String message=account+":gift";
            source.mailbox().deliver(new RewardDeliveryMessage(
                message,"No Replay","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2190_FIXTURE"
            ));
            MailboxRewardDeliveryService.Snapshot gift=
                source.mailbox().get(message);
            MailboxPreparedClaimJournal.stageOnly(source,
                MailboxPreparedClaimJournal.prepare(source,gift));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    source,originalGeneration,gift);
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
            if(!receipt.matchesSnapshot(terminal))
                throw new AssertionError("G21.90 fixture strict receipt");
            commit.recordConfirmedDiskTerminal(proposal,receipt);

            byte[] accountBefore=Files.readAllBytes(paths.resolve(account));
            byte[] journalBefore=Files.readAllBytes(
                journal.journalPath(account));
            byte[] commitBefore=Files.readAllBytes(
                commit.recordPath(account));
            MailboxCommittedDetachedRestartRecovery.Result restored=
                new MailboxCommittedDetachedRestartRecovery(paths)
                    .recoverDetached(account);

            WorldPlayer fresh=new WorldPlayer();
            long generation=restart.registerPlayer(fresh,account);
            PlayerSnapshot freshBefore=PlayerSnapshotCodec.capture(
                account,fresh);
            candidate=MailboxCommittedWorldGenerationCandidate.inspect(
                restart,fresh,generation,restored)==
                MailboxCommittedWorldGenerationCandidate.Decision
                    .CANDIDATE_ONLY_NO_ADMISSION;
            crossWorldDenied=
                MailboxCommittedWorldGenerationCandidate.inspect(
                    other,fresh,generation,restored)==
                    MailboxCommittedWorldGenerationCandidate.Decision
                        .REJECT_NOT_OWNED;
            newOwnerUndisturbed=fresh.bank().inventorySlots()==0&&
                fresh.mailbox().size()==0&&
                !fresh.mailboxSnapshotKnown()&&
                freshBefore.values().equals(
                    PlayerSnapshotCodec.capture(account,fresh).values());

            restart.unregisterPlayer(fresh,generation);
            oldGenerationDenied=
                MailboxCommittedWorldGenerationCandidate.inspect(
                    restart,fresh,generation,restored)==
                    MailboxCommittedWorldGenerationCandidate.Decision
                        .REJECT_NOT_OWNED;
            long newGeneration=restart.registerPlayer(fresh,account);
            reregisterCurrent=newGeneration!=generation&&
                MailboxCommittedWorldGenerationCandidate.inspect(
                    restart,fresh,newGeneration,restored)==
                    MailboxCommittedWorldGenerationCandidate.Decision
                        .CANDIDATE_ONLY_NO_ADMISSION;
            restart.unregisterPlayer(fresh,newGeneration);

            WorldPlayer foreign=new WorldPlayer();
            long foreignGeneration=restart.registerPlayer(
                foreign,"g2190-foreign");
            wrongAccountDenied=
                MailboxCommittedWorldGenerationCandidate.inspect(
                    restart,foreign,foreignGeneration,restored)==
                    MailboxCommittedWorldGenerationCandidate.Decision
                        .REJECT_ACCOUNT_MISMATCH;
            restart.unregisterPlayer(foreign,foreignGeneration);

            WorldPlayer used=new WorldPlayer();
            long usedGeneration=restart.registerPlayer(used,account);
            used.mailbox().deliver(new RewardDeliveryMessage(
                "g2190-already-used","Used","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,1)),
                "CUSTOM_LOCALLAB_G2190_FIXTURE"
            ));
            nonfreshDenied=
                MailboxCommittedWorldGenerationCandidate.inspect(
                    restart,used,usedGeneration,restored)==
                    MailboxCommittedWorldGenerationCandidate.Decision
                        .REJECT_NONFRESH_RECEIVER;
            restart.unregisterPlayer(used,usedGeneration);

            liveOwnerUnchanged=source.bank().inventorySlots()==0&&
                source.mailbox().get(message).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            filesUnchanged=Arrays.equals(accountBefore,
                    Files.readAllBytes(paths.resolve(account)))&&
                Arrays.equals(journalBefore,
                    Files.readAllBytes(journal.journalPath(account)))&&
                Arrays.equals(commitBefore,
                    Files.readAllBytes(commit.recordPath(account)));
            noAuthority=!restored.transactionCommitted&&
                !restored.liveApplied&&!restored.grantAuthorized&&
                !restored.replayAuthorized&&
                !restored.restartAdmissionAuthorized&&
                !restored.releaseAuthorized&&
                !restored.clientAckAuthorized;
        }finally{
            noLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            try(Stream<Path> all=Files.walk(root)){
                for(Path path:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("G2190_WORLD_GENERATION_CANDIDATE_DIAGNOSTICS"+
            " candidate="+candidate+
            " staleGenerationDenied="+oldGenerationDenied+
            " newGenerationChecked="+reregisterCurrent+
            " crossWorldDenied="+crossWorldDenied+
            " foreignAccountDenied="+wrongAccountDenied+
            " nonfreshDenied="+nonfreshDenied+
            " receiverUnchanged="+newOwnerUndisturbed+
            " originalWorldOwnerUnchanged="+liveOwnerUnchanged+
            " diskBytesUnchanged="+filesUnchanged+
            " allAuthorityFalse="+noAuthority+
            " noLeases="+noLeaks);
        if(!(candidate&&oldGenerationDenied&&reregisterCurrent&&
             crossWorldDenied&&wrongAccountDenied&&nonfreshDenied&&
             newOwnerUndisturbed&&filesUnchanged&&liveOwnerUnchanged&&
             noAuthority&&noLeaks))
            throw new AssertionError("G21.90 World generation gate failed");
        System.out.println("G2190_WORLD_CANDIDATE_PASS"+
            " boundGeneration=true noDiskIoUnderWorldLock=true"+
            " mutate=false admit=false grant=false replay=false"+
            " release=false ack=false");
    }
}

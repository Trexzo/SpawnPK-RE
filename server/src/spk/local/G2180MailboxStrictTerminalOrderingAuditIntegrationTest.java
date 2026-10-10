package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.80: actual terminal strict writer, negative WAL, and post-move
 * uncertainty exercised on disk. No World live settlement or grants.
 */
public final class G2180MailboxStrictTerminalOrderingAuditIntegrationTest {
    private static final StrictDurablePlayerSnapshotWriter.Phase[] CUTS={
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_TEMP_CREATE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_TEMP_CREATE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_SERIALIZE,
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_FILE_FORCE,
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_ATOMIC_REPLACE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_WRITE_AHEAD_INTENT,
        StrictDurablePlayerSnapshotWriter.Phase.BEFORE_DIRECTORY_FORCE,
        StrictDurablePlayerSnapshotWriter.Phase.AFTER_DIRECTORY_FORCE
    };

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2180-strict-ordering-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        int early=0,stranded=0,uncertain=0,confirmed=0;
        int observations=0;
        boolean allStatesNoGrant=true;
        boolean strictReceiptsOnlyOnSuccess=true;
        boolean terminalWithoutReceiptQuarantined=false;
        boolean foreignReceiptRejected=false;
        boolean worldLoadVeto=true;
        boolean originalLiveNeverCredited=true;
        boolean witnessDoesNotWrite=true;
        boolean noTemporaryOrLeaseLeaks=false;
        EnumSet<MailboxStrictTerminalOrderingAudit.State> states=
            EnumSet.noneOf(
                MailboxStrictTerminalOrderingAudit.State.class);
        StrictDurablePlayerSnapshotWriter.Receipt matchingReceipt=null;
        MailboxSettlementPostimagePlanner.Proposal preparedForMismatch=null;
        PlayerSnapshot terminalForMismatch=null;
        FilePlayerRepository.RestartRecoveryEvidence
            preparedObservationForMismatch=null;
        Path preparedPathForMismatch=null;

        try(World fixture=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,repository)){
            restart.start();
            for(int i=0;i<=CUTS.length;i++){
                String account="g2180-order-"+i;
                WorldPlayer owner=new WorldPlayer();
                long generation=fixture.registerPlayer(owner,account);
                String messageId=account+":gift";
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    messageId,"Strict ordering test","NO_GRANT",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)),
                    "CUSTOM_LOCALLAB_G2180_FIXTURE"));
                MailboxRewardDeliveryService.Snapshot selected=
                    owner.mailbox().get(messageId);
                MailboxPreparedClaimJournal.stageOnly(owner,
                    MailboxPreparedClaimJournal.prepare(owner,selected));
                MailboxSettlementPostimagePlanner.Proposal proposal=
                    MailboxSettlementPostimagePlanner.plan(
                        owner,generation,selected);
                PlayerSnapshot terminal=
                    MailboxAtomicTerminalSnapshot.compose(proposal);
                Path accountPath=paths.resolve(account);
                writer.saveStrict(proposal.preparedPreimage);
                boolean clean=i==CUTS.length;
                final StrictDurablePlayerSnapshotWriter.Phase injected=
                    clean?null:CUTS[i];
                AtomicInteger faultReached=new AtomicInteger();
                StrictDurablePlayerSnapshotWriter operation=
                    new StrictDurablePlayerSnapshotWriter(paths,phase->{
                        if(phase==injected){
                            faultReached.incrementAndGet();
                            throw new IOException(
                                "G21.80 FAULT_INJECTED "+phase);
                        }
                    });
                StrictDurablePlayerSnapshotWriter.Receipt receipt=null;
                boolean returnedFailure=false,postmoveUnconfirmed=false;
                try{
                    receipt=operation.saveStrictTerminalForWorld(
                        terminal,accountPath,
                        StrictDurablePlayerSnapshotWriter
                            .canonicalSnapshotSha256(proposal.preparedPreimage),
                        ()->{},()->{});
                }catch(
                    StrictDurablePlayerSnapshotWriter
                        .UnconfirmedCommitException expected
                ){
                    postmoveUnconfirmed=true;
                }catch(IOException expected){
                    returnedFailure=true;
                }
                if(!clean&&faultReached.get()!=1)
                    throw new AssertionError(
                        "G21.80 injection not reached "+injected);
                if(clean&&receipt==null)
                    throw new AssertionError(
                        "G21.80 success missing strict receipt");

                FilePlayerRepository.RestartRecoveryEvidence observed=
                    repository.inspectRestartRecoveryReadOnly(account);
                byte[] beforeAudit=Files.readAllBytes(accountPath);
                MailboxStrictTerminalOrderingAudit.Result audited=
                    MailboxStrictTerminalOrderingAudit.inspect(
                        proposal,terminal,accountPath,receipt,observed);
                observations++;
                states.add(audited.state);
                allStatesNoGrant&=noAuthority(audited);
                witnessDoesNotWrite&=Arrays.equals(
                    beforeAudit,Files.readAllBytes(accountPath));
                originalLiveNeverCredited&=
                    owner.bank().inventorySlots()==0&&
                    owner.bank().inventoryCount(995)==0&&
                    owner.mailbox().get(messageId).claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                    PlayerSnapshotCodec.capture(account,owner)
                        .values().equals(proposal.preparedPreimage.values());

                if(i<5){
                    early+=(returnedFailure&&!postmoveUnconfirmed&&
                        receipt==null&&
                        observed.state==
                            FilePlayerRepository.RestartRecoveryEvidence
                                .State.PREPARED_UNCLAIMED_NO_REPLAY&&
                        audited.state==
                            MailboxStrictTerminalOrderingAudit.State
                                .PREPARED_BEFORE_REPLACE_NO_GRANT)?1:0;
                    if(i==0){
                        preparedForMismatch=proposal;
                        terminalForMismatch=terminal;
                        preparedObservationForMismatch=observed;
                        preparedPathForMismatch=accountPath;
                    }
                    worldLoadVeto&=restart.persistence()
                        .load(account).isPresent();
                }else if(i==5){
                    stranded+=(returnedFailure&&!postmoveUnconfirmed&&
                        receipt==null&&
                        observed.state==
                            FilePlayerRepository.RestartRecoveryEvidence.State
                                .STRANDED_WRITE_INTENT_MARKER&&
                        audited.state==
                            MailboxStrictTerminalOrderingAudit.State
                                .NEGATIVE_MARKER_MANUAL_REVIEW_NO_GRANT)?1:0;
                    worldLoadVeto&=rejectsAdmission(restart,account);
                }else if(i==6||i==7){
                    uncertain+=(postmoveUnconfirmed&&!returnedFailure&&
                        receipt==null&&
                        observed.state==
                            FilePlayerRepository.RestartRecoveryEvidence.State
                                .UNCERTAIN_COMMIT_MARKER&&
                        audited.state==
                            MailboxStrictTerminalOrderingAudit.State
                                .NEGATIVE_MARKER_MANUAL_REVIEW_NO_GRANT)?1:0;
                    worldLoadVeto&=rejectsAdmission(restart,account);
                }else{
                    confirmed+=(receipt!=null&&!postmoveUnconfirmed&&
                        !returnedFailure&&
                        observed.state==
                            FilePlayerRepository.RestartRecoveryEvidence.State
                                .COHERENT_TERMINAL_QUARANTINE&&
                        audited.strictOperationReceiptMatched&&
                        audited.state==
                            MailboxStrictTerminalOrderingAudit.State
                                .STRICT_FILE_OPERATION_ONLY_NO_COMMIT)?1:0;
                    matchingReceipt=receipt;
                    strictReceiptsOnlyOnSuccess&=
                        receipt.matchesSnapshot(terminal);
                    worldLoadVeto&=rejectsAdmission(restart,account);
                    MailboxStrictTerminalOrderingAudit.Result noReceipt=
                        MailboxStrictTerminalOrderingAudit.inspect(
                            proposal,terminal,accountPath,null,observed);
                    terminalWithoutReceiptQuarantined=
                        noReceipt.state==
                            MailboxStrictTerminalOrderingAudit.State
                                .TERMINAL_VISIBLE_NO_RECEIPT_QUARANTINE&&
                        noAuthority(noReceipt);
                    states.add(noReceipt.state);
                }
                strictReceiptsOnlyOnSuccess&=clean==(receipt!=null);
            }

            try{
                MailboxStrictTerminalOrderingAudit.inspect(
                    preparedForMismatch,terminalForMismatch,
                    preparedPathForMismatch,matchingReceipt,
                    preparedObservationForMismatch);
            }catch(IllegalArgumentException refused){
                foreignReceiptRejected=refused.getMessage().contains(
                    "foreign or stale strict receipt");
            }

            try(Stream<Path> all=Files.walk(root)){
                noTemporaryOrLeaseLeaks=all.noneMatch(p->{
                    String name=p.getFileName().toString();
                    return name.endsWith(".tmp")||
                        name.contains(".g2123-");
                })&&MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path file:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
        System.out.println("G2180_ORDERING_DIAGNOSTICS"+
            " early="+early+" stranded="+stranded+
            " uncertain="+uncertain+" confirmed="+confirmed+
            " observations="+observations+
            " strictReceiptsOnlyOnSuccess="+strictReceiptsOnlyOnSuccess+
            " terminalWithoutReceiptQuarantined="+
                terminalWithoutReceiptQuarantined+
            " foreignReceiptRejected="+foreignReceiptRejected+
            " worldLoadVeto="+worldLoadVeto+
            " originalLiveNeverCredited="+originalLiveNeverCredited+
            " witnessDoesNotWrite="+witnessDoesNotWrite+
            " noTemporaryOrLeaseLeaks="+noTemporaryOrLeaseLeaks+
            " allStatesNoGrant="+allStatesNoGrant);
        if(!(early==5&&stranded==1&&uncertain==2&&confirmed==1&&
             observations==9&&strictReceiptsOnlyOnSuccess&&
             terminalWithoutReceiptQuarantined&&foreignReceiptRejected&&
             worldLoadVeto&&originalLiveNeverCredited&&
             witnessDoesNotWrite&&noTemporaryOrLeaseLeaks&&
             allStatesNoGrant))
            throw new AssertionError(
                "G21.80 strict terminal ordering audit regression failed");
        System.out.println("G2180_STRICT_ORDERING_PASS"+
            " preMove=5 walStranded=1 postMoveUnconfirmed=2"+
            " strictFileConfirmed=1"+
            " durableTransactionCommit=false liveApply=false"+
            " grant=false replay=false admission=false ack=false");
    }

    private static boolean rejectsAdmission(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException rejected){
            return true;
        }
    }

    private static boolean noAuthority(
        MailboxStrictTerminalOrderingAudit.Result a
    ){
        return a.missingPositiveProofs.size()==4&&
            !a.transactionCommitted&&!a.durabilityConfirmed&&
            !a.liveApplied&&!a.grantAuthorized&&!a.replayAuthorized&&
            !a.rollbackAuthorized&&!a.restartAdmissionAuthorized&&
            !a.releaseAuthorized&&!a.clientAckAuthorized;
    }
}

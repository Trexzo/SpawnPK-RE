package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.30: the exact G21.25 full postimage must match the successful
 * G21.23 strict receipt fingerprint. Even that does not grant items,
 * commit a World-level transaction or authorize restart replay.
 */
public final class G2130MailboxBoundStrictReceiptIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean correctPreparedSnapshotBound=false;
        boolean hypotheticalNotMatchedByPreparedReceipt=false;
        boolean canonicalDeterminism=false;
        boolean changedFullRecordRejected=false;
        boolean alternateVersionRejected=false;
        boolean exactPreparedNoGrant=false;
        boolean preAtomicFailureNoReceipt=false;
        boolean preAtomicFailureOldIntact=false;
        boolean ambiguousAfterReplaceNoReceipt=false;
        boolean hypotheticalWithoutReceiptVeto=false;
        boolean genuineWrongSnapshotReceiptVeto=false;
        boolean hypotheticalBoundReceiptRecognized=false;
        boolean forgedObservationVeto=false;
        boolean foreignReceiptVeto=false;
        boolean explicitRepairRelease=false;
        boolean restartCannotRecoverReceipt=false;
        boolean staleOwnerVeto=false;
        boolean noLiveInventoryOrClaim=false;

        Path dir=Files.createTempDirectory(
            "g2130-snapshot-bound-"
        );
        FilePlayerRepository.PathResolver paths=
            account->dir.resolve(account+".properties");
        FilePlayerRepository disk=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        try(World world=World.isolatedForTest(60000L,disk)){
            WorldPlayer owner=new WorldPlayer();
            long generation=world.registerPlayer(
                owner,LocalAccountProfiles.PRIMARY
            );
            world.start();
            AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                proposalRef=new AtomicReference<>();
            world.submitAndWait(owner,generation,()->{
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    "g2130:reward","Snapshot-bound gift","No grant",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"CUSTOM_LOCALLAB_G2130_FIXTURE"
                ));
                MailboxRewardDeliveryService.Snapshot selected=
                    owner.mailbox().get("g2130:reward");
                MailboxPreparedClaimJournal.stageOnly(
                    owner,MailboxPreparedClaimJournal.prepare(
                        owner,selected
                    )
                );
                proposalRef.set(MailboxSettlementPostimagePlanner.plan(
                    owner,generation,selected
                ));
            },5000L);
            MailboxSettlementPostimagePlanner.Proposal proposal=
                proposalRef.get();
            StrictDurablePlayerSnapshotWriter.Receipt preparedReceipt=
                strict.saveStrict(proposal.preparedPreimage);
            correctPreparedSnapshotBound=
                preparedReceipt.matchesSnapshot(proposal.preparedPreimage)&&
                preparedReceipt.snapshotVersion==
                    PlayerSnapshot.CURRENT_VERSION&&
                preparedReceipt.snapshotSha256.matches("[0-9a-f]{64}");
            hypotheticalNotMatchedByPreparedReceipt=
                !preparedReceipt.matchesSnapshot(
                    proposal.hypotheticalPostimage
                )&&
                !preparedReceipt.snapshotSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            proposal.hypotheticalPostimage
                        )
                );

            TreeMap<String,String> reversed=new TreeMap<>(
                Comparator.reverseOrder()
            );
            reversed.putAll(proposal.preparedPreimage.values());
            PlayerSnapshot reordered=new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                proposal.account,reversed
            );
            canonicalDeterminism=
                preparedReceipt.matchesSnapshot(reordered)&&
                preparedReceipt.snapshotSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(reordered)
                );
            TreeMap<String,String> changed=new TreeMap<>(
                proposal.preparedPreimage.values()
            );
            changed.put("extension.g2130.other","changed");
            changedFullRecordRejected=
                !preparedReceipt.matchesSnapshot(
                    new PlayerSnapshot(PlayerSnapshot.CURRENT_VERSION,
                        proposal.account,changed)
                );
            alternateVersionRejected=
                !preparedReceipt.matchesSnapshot(
                    new PlayerSnapshot(3,proposal.account,
                        proposal.preparedPreimage.values())
                )&&
                !preparedReceipt.matchesSnapshot(
                    new PlayerSnapshot(PlayerSnapshot.CURRENT_VERSION,
                        "g2130-foreign",
                        proposal.preparedPreimage.values())
                );

            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            WorldPlayerPersistence.PreparedDrainObservation preDisk=
                world.persistence().drainReservedPreparedAccount(
                    token,proposal
                ).get(8,TimeUnit.SECONDS);
            MailboxBoundStrictReceiptEvidence.Evidence prepared=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,preDisk,preparedReceipt
                );
            exactPreparedNoGrant=
                prepared.state==
                    MailboxBoundStrictReceiptEvidence.State
                        .EXACT_PREPARED_NO_GRANT&&
                !prepared.strictOperationSnapshotBound&&
                allVeto(prepared);

            // Test-only direct strict saves are isolated on a temp account.
            // Do not use these calls as a live World settlement algorithm.
            StrictDurablePlayerSnapshotWriter beforeReplace=
                new StrictDurablePlayerSnapshotWriter(
                    paths,phase->{
                        if(phase==
                            StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE)
                            throw new IOException(
                                "G21.30 injected pre-rename failure"
                            );
                    }
                );
            try{
                beforeReplace.saveStrict(
                    proposal.hypotheticalPostimage
                );
            }catch(IOException expected){
                preAtomicFailureNoReceipt=true;
            }
            preAtomicFailureOldIntact=
                disk.load(proposal.account).get()
                    .values().equals(
                        proposal.preparedPreimage.values()
                    );

            StrictDurablePlayerSnapshotWriter afterReplace=
                new StrictDurablePlayerSnapshotWriter(
                    paths,phase->{
                        if(phase==
                            StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_DIRECTORY_FORCE)
                            throw new IOException(
                                "G21.30 injected ambiguous outcome"
                            );
                    }
                );
            try{
                afterReplace.saveStrict(
                    proposal.hypotheticalPostimage
                );
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                ambiguousAfterReplaceNoReceipt=true;
            }
            WorldPlayerPersistence.PreparedDrainObservation ambiguous=
                world.persistence().drainReservedPreparedAccount(
                    token,proposal
                ).get(8,TimeUnit.SECONDS);
            MailboxBoundStrictReceiptEvidence.Evidence missingReceipt=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,ambiguous,null
                );
            hypotheticalWithoutReceiptVeto=
                missingReceipt.state==
                    MailboxBoundStrictReceiptEvidence.State
                        .HYPOTHETICAL_MISSING_RECEIPT&&
                !missingReceipt.strictOperationSnapshotBound&&
                allVeto(missingReceipt);
            MailboxBoundStrictReceiptEvidence.Evidence wrongReceipt=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,ambiguous,preparedReceipt
                );
            genuineWrongSnapshotReceiptVeto=
                wrongReceipt.state==
                    MailboxBoundStrictReceiptEvidence.State
                        .HYPOTHETICAL_RECEIPT_FOR_DIFFERENT_SNAPSHOT&&
                allVeto(wrongReceipt);

            StrictDurablePlayerSnapshotWriter.Receipt actualHypothetical=
                strict.saveStrict(proposal.hypotheticalPostimage);
            WorldPlayerPersistence.PreparedDrainObservation exactHypothetical=
                world.persistence().drainReservedPreparedAccount(
                    token,proposal
                ).get(8,TimeUnit.SECONDS);
            MailboxBoundStrictReceiptEvidence.Evidence bound=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,exactHypothetical,actualHypothetical
                );
            hypotheticalBoundReceiptRecognized=
                actualHypothetical.matchesSnapshot(
                    proposal.hypotheticalPostimage
                )&&
                bound.state==
                    MailboxBoundStrictReceiptEvidence.State
                        .BOUND_HYPOTHETICAL_FILE_OPERATION&&
                bound.strictOperationSnapshotBound&&
                allVeto(bound);

            WorldPlayerPersistence.PreparedDrainObservation forged=
                new WorldPlayerPersistence.PreparedDrainObservation(
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.EXACT_HYPOTHETICAL,
                    "g2130-different",proposal.idempotencyKey,generation
                );
            forgedObservationVeto=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,forged,actualHypothetical
                ).state==
                    MailboxBoundStrictReceiptEvidence.State
                        .REJECTED_OR_UNTRUSTED_OBSERVATION;

            WorldPlayer foreign=new WorldPlayer();
            foreign.markRegistered("g2130-foreign");
            StrictDurablePlayerSnapshotWriter.Receipt otherReceipt=
                strict.saveStrict(
                    PlayerSnapshotCodec.capture("g2130-foreign",foreign)
                );
            foreignReceiptVeto=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,exactHypothetical,otherReceipt
                ).state==
                    MailboxBoundStrictReceiptEvidence.State
                        .REJECTED_OR_UNTRUSTED_OBSERVATION;

            restartCannotRecoverReceipt=
                MailboxBoundStrictReceiptEvidence.inspect(
                    null,proposal,exactHypothetical,actualHypothetical
                ).state==
                    MailboxBoundStrictReceiptEvidence.State
                        .RESTART_QUARANTINE;

            // A matching receipt is NOT permission to release a
            // hypothetical live transaction. Repair test fixture to
            // complete PREPARED, then independently drain and cancel.
            disk.save(proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedDrainObservation repaired=
                world.persistence().drainReservedPreparedAccount(
                    token,proposal
                ).get(8,TimeUnit.SECONDS);
            explicitRepairRelease=
                MailboxBoundStrictReceiptEvidence.inspect(
                    token,proposal,repaired,null
                ).state==
                    MailboxBoundStrictReceiptEvidence.State
                        .EXACT_PREPARED_NO_GRANT&&
                token.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation oldToken=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            WorldPlayerPersistence.PreparedDrainObservation staleDisk=
                world.persistence().drainReservedPreparedAccount(
                    oldToken,proposal
                ).get(8,TimeUnit.SECONDS);
            boolean unregistered=world.unregisterPlayer(owner,generation);
            staleOwnerVeto=
                unregistered&&
                MailboxBoundStrictReceiptEvidence.inspect(
                    oldToken,proposal,staleDisk,preparedReceipt
                ).state==
                    MailboxBoundStrictReceiptEvidence.State
                        .REJECTED_OR_UNTRUSTED_OBSERVATION;
            long newer=world.registerPlayer(
                owner,LocalAccountProfiles.PRIMARY
            );
            staleOwnerVeto &=
                newer!=generation&&
                MailboxBoundStrictReceiptEvidence.inspect(
                    oldToken,proposal,staleDisk,preparedReceipt
                ).state==
                    MailboxBoundStrictReceiptEvidence.State
                        .REJECTED_OR_UNTRUSTED_OBSERVATION;

            noLiveInventoryOrClaim=
                owner.bank().inventorySlots()==0&&
                owner.mailbox().get("g2130:reward").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY".equals(
                    MailboxInventoryClaimPreflight.AUTHORITY
                );
        }finally{
            try(Stream<Path> entries=Files.walk(dir)){
                for(Path path:entries.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2130_MAILBOX_BOUND_RECEIPT_DIAGNOSTICS"+
            " preparedSnapshotBound="+correctPreparedSnapshotBound+
            " preparedCannotMatchHypothetical="+
                hypotheticalNotMatchedByPreparedReceipt+
            " stableCanonicalHash="+canonicalDeterminism+
            " unrelatedStateHashVeto="+changedFullRecordRejected+
            " wrongVersionOrAccountVeto="+alternateVersionRejected+
            " exactPreparedNoGrant="+exactPreparedNoGrant+
            " preAtomicFailureNoReceipt="+preAtomicFailureNoReceipt+
            " preAtomicOldIntact="+preAtomicFailureOldIntact+
            " postRenameNoReceipt="+ambiguousAfterReplaceNoReceipt+
            " hypotheticalNoReceiptVeto="+hypotheticalWithoutReceiptVeto+
            " genuineWrongSnapshotReceiptVeto="+
                genuineWrongSnapshotReceiptVeto+
            " boundFileOperationOnly="+hypotheticalBoundReceiptRecognized+
            " forgedObservationVeto="+forgedObservationVeto+
            " foreignReceiptVeto="+foreignReceiptVeto+
            " restartQuarantine="+restartCannotRecoverReceipt+
            " repairedPreimageRelease="+explicitRepairRelease+
            " staleGenerationVeto="+staleOwnerVeto+
            " liveNoGrant="+noLiveInventoryOrClaim
        );
        require(
            correctPreparedSnapshotBound&&
            hypotheticalNotMatchedByPreparedReceipt&&
            canonicalDeterminism&&changedFullRecordRejected&&
            alternateVersionRejected&&exactPreparedNoGrant&&
            preAtomicFailureNoReceipt&&preAtomicFailureOldIntact&&
            ambiguousAfterReplaceNoReceipt&&
            hypotheticalWithoutReceiptVeto&&
            genuineWrongSnapshotReceiptVeto&&
            hypotheticalBoundReceiptRecognized&&
            forgedObservationVeto&&foreignReceiptVeto&&
            explicitRepairRelease&&restartCannotRecoverReceipt&&
            staleOwnerVeto&&noLiveInventoryOrClaim,
            "G21.30 canonical receipt acceptance"
        );
        System.out.println(
            "G2130_MAILBOX_SNAPSHOT_BOUND_STRICT_RECEIPT_PASS"+
            " canonicalDigest=true strictReceiptAfterForceOnly=true"+
            " wrongSnapshotVeto=true restartSafeGrant=false"+
            " liveInventoryCredit=false liveMailboxClaim=false"
        );
    }

    private static boolean allVeto(
        MailboxBoundStrictReceiptEvidence.Evidence evidence
    ){
        return !evidence.liveGrantAuthorized&&
            !evidence.replayAuthorized&&
            !evidence.liveReconciliationAuthorized&&
            !evidence.restartRecoveryAuthorized&&
            !evidence.clientSuccessAuthorized;
    }

    private static void require(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
    }

    private G2130MailboxBoundStrictReceiptIntegrationTest(){}
}

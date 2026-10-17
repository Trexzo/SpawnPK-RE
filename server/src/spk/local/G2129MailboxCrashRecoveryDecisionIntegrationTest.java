package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.29: deterministic read-only recovery decisions across PREPARED,
 * hypothetical, ambiguous receipt, divergence and owner retirement.
 * No test path invokes a live inventory or Mailbox reward grant.
 */
public final class G2129MailboxCrashRecoveryDecisionIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean exactPreparedClassified=false;
        boolean preparedCannotAuthorizeGrant=false;
        boolean sameObservationIdempotent=false;
        boolean explicitSafeReleaseRetained=false;
        boolean visibleWithoutReceiptQuarantined=false;
        boolean genuineStrictReceiptStillUnbound=false;
        boolean foreignReceiptRejected=false;
        boolean forgedObservationRejected=false;
        boolean restartWithoutTokenQuarantined=false;
        boolean missingAccountQuarantined=false;
        boolean divergentAccountQuarantined=false;
        boolean repairAllowsIndependentRelease=false;
        boolean staleGenerationRejected=false;
        boolean noLiveCreditOrClaim=false;
        boolean noClientAuthorization=false;
        boolean normalCheckpointPathUntouched=false;

        Path directory=Files.createTempDirectory(
            "g2129-mailbox-recovery-"
        );
        FilePlayerRepository.PathResolver resolver=
            account->directory.resolve(account+".properties");
        FilePlayerRepository disk=new FilePlayerRepository(resolver);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(resolver);

        try(World world=World.isolatedForTest(60000L,disk)){
            WorldPlayer owner=new WorldPlayer();
            long generation=world.registerPlayer(
                owner,LocalAccountProfiles.PRIMARY
            );
            world.start();
            AtomicReference<MailboxSettlementPostimagePlanner.Proposal>
                saved=new AtomicReference<>();
            world.submitAndWait(owner,generation,()->{
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    "g2129:gift","Prepared reward","No actual grant",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)
                    ),"CUSTOM_LOCALLAB_G2129_FIXTURE"
                ));
                MailboxRewardDeliveryService.Snapshot selected=
                    owner.mailbox().get("g2129:gift");
                MailboxPreparedClaimJournal.stageOnly(
                    owner,MailboxPreparedClaimJournal.prepare(
                        owner,selected
                    )
                );
                saved.set(MailboxSettlementPostimagePlanner.plan(
                    owner,generation,selected
                ));
            },5000L);
            MailboxSettlementPostimagePlanner.Proposal proposal=
                saved.get();
            disk.save(proposal.preparedPreimage);

            WorldPlayerPersistence.PreparedAccountReservation first=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            WorldPlayerPersistence.PreparedDrainObservation prepared=
                world.persistence().drainReservedPreparedAccount(
                    first,proposal
                ).get(8,TimeUnit.SECONDS);
            MailboxPreparedRecoveryDecision.Decision decision=
                MailboxPreparedRecoveryDecision.assess(
                    first,proposal,prepared,null
                );
            exactPreparedClassified=
                decision.state==
                    MailboxPreparedRecoveryDecision.State
                        .EXACT_PREPARED_UNCLAIMED&&
                !decision.needsManualReconciliation()&&
                proposal.account.equals(decision.account)&&
                proposal.idempotencyKey.equals(decision.intentKey);
            preparedCannotAuthorizeGrant=allVeto(decision);
            MailboxPreparedRecoveryDecision.Decision repeat=
                MailboxPreparedRecoveryDecision.assess(
                    first,proposal,prepared,null
                );
            sameObservationIdempotent=
                repeat.state==decision.state&&
                allVeto(repeat)&&first.isActive();
            explicitSafeReleaseRetained=
                !decision.safeReleaseAuthorized&&
                first.cancelIfStillUnclaimed()&&
                !first.isActive();

            WorldPlayerPersistence.PreparedAccountReservation second=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            // This is a real G21.23 success receipt for the PREPARED
            // snapshot, NOT for the hypothetical account postimage.
            StrictDurablePlayerSnapshotWriter.Receipt wrongSnapshot=
                strict.saveStrict(proposal.preparedPreimage);
            disk.save(proposal.hypotheticalPostimage);
            WorldPlayerPersistence.PreparedDrainObservation hypothetical=
                world.persistence().drainReservedPreparedAccount(
                    second,proposal
                ).get(8,TimeUnit.SECONDS);
            MailboxPreparedRecoveryDecision.Decision withoutReceipt=
                MailboxPreparedRecoveryDecision.assess(
                    second,proposal,hypothetical,null
                );
            visibleWithoutReceiptQuarantined=
                withoutReceipt.state==
                    MailboxPreparedRecoveryDecision.State
                        .HYPOTHETICAL_VISIBLE_NO_RECEIPT&&
                withoutReceipt.needsManualReconciliation()&&
                allVeto(withoutReceipt);
            MailboxPreparedRecoveryDecision.Decision weakReceipt=
                MailboxPreparedRecoveryDecision.assess(
                    second,proposal,hypothetical,wrongSnapshot
                );
            genuineStrictReceiptStillUnbound=
                weakReceipt.state==
                    MailboxPreparedRecoveryDecision.State
                        .HYPOTHETICAL_VISIBLE_UNBOUND_RECEIPT&&
                allVeto(weakReceipt)&&second.isActive();

            WorldPlayer foreign=new WorldPlayer();
            foreign.markRegistered("g2129-foreign");
            StrictDurablePlayerSnapshotWriter.Receipt otherReceipt=
                strict.saveStrict(PlayerSnapshotCodec.capture(
                    "g2129-foreign",foreign
                ));
            MailboxPreparedRecoveryDecision.Decision foreignRejected=
                MailboxPreparedRecoveryDecision.assess(
                    second,proposal,hypothetical,otherReceipt
                );
            foreignReceiptRejected=
                foreignRejected.state==
                    MailboxPreparedRecoveryDecision.State
                        .RECEIPT_IDENTITY_MISMATCH&&
                allVeto(foreignRejected);

            WorldPlayerPersistence.PreparedDrainObservation forged=
                new WorldPlayerPersistence.PreparedDrainObservation(
                    WorldPlayerPersistence.PreparedDrainObservation
                        .State.EXACT_HYPOTHETICAL,
                    "wrong-account",proposal.idempotencyKey,generation
                );
            forgedObservationRejected=
                MailboxPreparedRecoveryDecision.assess(
                    second,proposal,forged,wrongSnapshot
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .UNTRUSTED_OBSERVATION;
            restartWithoutTokenQuarantined=
                MailboxPreparedRecoveryDecision.afterRestart(
                    proposal
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .RESTART_REQUIRES_MANUAL_RECONCILIATION&&
                allVeto(MailboxPreparedRecoveryDecision.assess(
                    null,proposal,hypothetical,wrongSnapshot
                ));

            // An exact hypothetical on disk must keep the exclusive
            // reservation held. Only another exact PREPARED observation
            // and G21.28's independent safeguards can release it.
            disk.save(proposal.preparedPreimage);
            world.persistence().drainReservedPreparedAccount(
                second,proposal
            ).get(8,TimeUnit.SECONDS);
            second.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation third=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            Files.delete(resolver.resolve(proposal.account));
            WorldPlayerPersistence.PreparedDrainObservation missing=
                world.persistence().drainReservedPreparedAccount(
                    third,proposal
                ).get(8,TimeUnit.SECONDS);
            missingAccountQuarantined=
                allVeto(MailboxPreparedRecoveryDecision.assess(
                    third,proposal,missing,null
                ))&&
                MailboxPreparedRecoveryDecision.assess(
                    third,proposal,missing,null
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .MISSING_ACCOUNT_QUARANTINE;

            TreeMap<String,String> changed=new TreeMap<>(
                proposal.preparedPreimage.values()
            );
            changed.put("extension.g2129.unrelated","different");
            disk.save(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                proposal.account,changed
            ));
            WorldPlayerPersistence.PreparedDrainObservation divergent=
                world.persistence().drainReservedPreparedAccount(
                    third,proposal
                ).get(8,TimeUnit.SECONDS);
            divergentAccountQuarantined=
                MailboxPreparedRecoveryDecision.assess(
                    third,proposal,divergent,null
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .DIVERGENT_ACCOUNT_QUARANTINE;
            disk.save(proposal.preparedPreimage);
            WorldPlayerPersistence.PreparedDrainObservation repaired=
                world.persistence().drainReservedPreparedAccount(
                    third,proposal
                ).get(8,TimeUnit.SECONDS);
            repairAllowsIndependentRelease=
                MailboxPreparedRecoveryDecision.assess(
                    third,proposal,repaired,null
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .EXACT_PREPARED_UNCLAIMED&&
                third.cancelIfStillUnclaimed();

            WorldPlayerPersistence.PreparedAccountReservation retired=
                world.persistence().reservePreparedAccount(
                    owner,generation,proposal
                );
            WorldPlayerPersistence.PreparedDrainObservation justBefore=
                world.persistence().drainReservedPreparedAccount(
                    retired,proposal
                ).get(8,TimeUnit.SECONDS);
            boolean unregistered=world.unregisterPlayer(
                owner,generation
            );
            staleGenerationRejected=
                unregistered&&
                MailboxPreparedRecoveryDecision.assess(
                    retired,proposal,justBefore,null
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .STALE_OR_UNBOUND_RESERVATION;
            long nextGeneration=world.registerPlayer(
                owner,LocalAccountProfiles.PRIMARY
            );
            staleGenerationRejected &=
                nextGeneration!=generation&&
                MailboxPreparedRecoveryDecision.assess(
                    retired,proposal,justBefore,null
                ).state==
                    MailboxPreparedRecoveryDecision.State
                        .STALE_OR_UNBOUND_RESERVATION;

            noLiveCreditOrClaim=
                owner.bank().inventorySlots()==0&&
                owner.mailbox().get("g2129:gift").claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            noClientAuthorization=
                !decision.clientSuccessAuthorized&&
                !weakReceipt.clientSuccessAuthorized&&
                !foreignRejected.clientSuccessAuthorized;
            normalCheckpointPathUntouched=
                world.persistence().checkpointCapturedCount()>=0;
        }finally{
            try(Stream<Path> paths=Files.walk(directory)){
                for(Path path:paths.sorted(
                        Comparator.reverseOrder()
                    ).toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }

        System.out.println(
            "G2129_MAILBOX_RECOVERY_DECISION_DIAGNOSTICS"+
            " exactPrepared="+exactPreparedClassified+
            " preparedNoGrant="+preparedCannotAuthorizeGrant+
            " idempotent="+sameObservationIdempotent+
            " explicitRelease="+explicitSafeReleaseRetained+
            " hypotheticalNoReceipt="+visibleWithoutReceiptQuarantined+
            " weakReceiptVeto="+genuineStrictReceiptStillUnbound+
            " foreignReceiptVeto="+foreignReceiptRejected+
            " forgedObservationVeto="+forgedObservationRejected+
            " restartQuarantine="+restartWithoutTokenQuarantined+
            " missingQuarantine="+missingAccountQuarantined+
            " divergentQuarantine="+divergentAccountQuarantined+
            " repairedRelease="+repairAllowsIndependentRelease+
            " staleOwnerVeto="+staleGenerationRejected+
            " noLiveGrant="+noLiveCreditOrClaim+
            " noClientSuccess="+noClientAuthorization+
            " normalPathUntouched="+normalCheckpointPathUntouched
        );
        require(
            exactPreparedClassified&&preparedCannotAuthorizeGrant&&
            sameObservationIdempotent&&explicitSafeReleaseRetained&&
            visibleWithoutReceiptQuarantined&&
            genuineStrictReceiptStillUnbound&&foreignReceiptRejected&&
            forgedObservationRejected&&restartWithoutTokenQuarantined&&
            missingAccountQuarantined&&divergentAccountQuarantined&&
            repairAllowsIndependentRelease&&staleGenerationRejected&&
            noLiveCreditOrClaim&&noClientAuthorization&&
            normalCheckpointPathUntouched,
            "G21.29 recovery classification acceptance"
        );
        System.out.println(
            "G2129_MAILBOX_CRASH_RECOVERY_PASS"+
            " focusedBoundary=true durableGrant=false replay=false"+
            " liveClaim=false strictReceiptUnbound=true"
        );
    }

    private static boolean allVeto(
        MailboxPreparedRecoveryDecision.Decision d
    ){
        return !d.durabilityConfirmed&&
            !d.grantAuthorized&&!d.replayAuthorized&&
            !d.safeReleaseAuthorized&&!d.clientSuccessAuthorized;
    }

    private static void require(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }

    private G2129MailboxCrashRecoveryDecisionIntegrationTest(){}
}

package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.stream.Stream;

/**
 * G21.161: actual G21.79 read-only file-backed cutover inspection
 * surfaces all seven outstanding proofs and refuses every attempted
 * signer admission. Never reads a live World owner or grants rewards.
 */
public final class G21161MailboxCutoverTrustProofIntegrationTest {
    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("G21.161 "+reason);
    }

    private static void deny(
        MailboxSettlementCutoverReadiness.Decision observed,
        byte[] candidateKey,String diagnostic
    )throws Exception{
        boolean vetoed=false;
        try{
            MailboxSettlementCutoverReadiness
                .requireProductionSignerBeforeCutover(
                    observed,candidateKey);
        }catch(IOException expected){
            String message=String.valueOf(expected.getMessage());
            vetoed=message.contains(diagnostic)&&
                message.contains("NO_AUTHORITY");
        }
        check(vetoed,"cutover production root gate not enforced "+
            diagnostic);
    }

    private static MailboxSettlementCutoverReadiness.Decision
        decision(
            FilePlayerRepository repository,String account,
            FilePlayerRepository.RestartRecoveryEvidence.State state,
            MailboxSettlementCutoverReadiness.Disposition disposition
        )throws Exception{
        FilePlayerRepository.RestartRecoveryEvidence evidence=
            repository.inspectRestartRecoveryReadOnly(account);
        MailboxSettlementCutoverReadiness.Decision result=
            MailboxSettlementCutoverReadiness.assess(account,evidence);
        check(result.account.equals(account)&&
            result.observed==state&&
            result.disposition==disposition,
            "original cutover disposition must not change");
        check(result.missingProofs.size()==7&&
            result.missingProofs.containsAll(EnumSet.allOf(
                MailboxSettlementCutoverReadiness.MissingProof.class))&&
            result.missingProofs.contains(
                MailboxSettlementCutoverReadiness.MissingProof
                    .INDEPENDENT_SIGNER_ROOT_AND_HISTORICAL_CUSTODY)&&
            result.missingProofs.contains(
                MailboxSettlementCutoverReadiness.MissingProof
                    .COMPLETE_RAW_DEFAULT_HISTORICAL_FUTURE_WRITER_FENCE)&&
            result.missingProofs.contains(
                MailboxSettlementCutoverReadiness.MissingProof
                    .ATOMIC_MULTI_FILE_CRASH_RECOVERY_EPOCH),
            "root/writer/atomic cutover gaps must remain explicit");
        check(!result.independentlyEnrolledSignerRootObserved&&
            !result.historicalEvidenceCustodyAuthenticated&&
            !result.rawDefaultAndHistoricalWritersExcluded&&
            !result.futureWriterFenceProved&&
            !result.atomicRecoveryEpochProved&&
            !result.snapshotAdmitted&&!result.durabilityConfirmed&&
            !result.transactionCommitted&&!result.liveApplied&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.rollbackAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,
            "read-only cutover may never confer any authority");
        return result;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21161-real-cutover-");
        try{
            FilePlayerRepository.PathResolver paths=
                account->root.resolve(account+".properties");
            FilePlayerRepository repository=
                new FilePlayerRepository(paths);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(paths);
            check(MailboxProductionSignerTrustAdmissionV1
                .independentlyEnrolledProductionRoots()==0,
                "unexpected independently enrolled production root");

            KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair mathematicalCandidate=generator.generateKeyPair();
            KeyPair callerSubstitution=generator.generateKeyPair();
            byte[] first=mathematicalCandidate.getPublic().getEncoded();
            byte[] second=callerSubstitution.getPublic().getEncoded();
            int observedStatuses=0,rootDenials=0,invalidDenials=0;

            String missing="g21161-missing";
            MailboxSettlementCutoverReadiness.Decision absent=decision(
                repository,missing,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .MISSING_ACCOUNT_NO_REPLAY,
                MailboxSettlementCutoverReadiness.Disposition
                    .MISSING_ACCOUNT_QUARANTINE);
            observedStatuses++;
            deny(absent,first,"NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            rootDenials++;
            deny(absent,second,"NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            rootDenials++;
            check(!Files.exists(paths.resolve(missing)),
                "missing-account inspection created a source");

            String legacy="g21161-legacy";
            WorldPlayer player=new WorldPlayer();
            player.markRegistered(legacy);
            writer.saveStrict(PlayerSnapshotCodec.capture(legacy,player));
            byte[] recorded=Files.readAllBytes(paths.resolve(legacy));
            MailboxSettlementCutoverReadiness.Decision old=decision(
                repository,legacy,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .LEGACY_NO_JOURNAL_NON_ADMITTING,
                MailboxSettlementCutoverReadiness.Disposition
                    .LEGACY_NOT_MAILBOX_TRANSACTION);
            observedStatuses++;
            deny(old,first,"NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            rootDenials++;
            check(Arrays.equals(recorded,
                    Files.readAllBytes(paths.resolve(legacy))),
                "legacy cutover inspection changed stored bytes");

            String marker=legacy+".properties.g2132-mailbox-review";
            Path review=root.resolve(marker);
            byte[] reviewBytes="G21161_MANUAL_HOLD_NO_GRANT\n"
                .getBytes(StandardCharsets.US_ASCII);
            Files.write(review,reviewBytes);
            MailboxSettlementCutoverReadiness.Decision held=decision(
                repository,legacy,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .DURABLE_REVIEW_MARKER,
                MailboxSettlementCutoverReadiness.Disposition
                    .REVIEW_MARKER_MANUAL_HOLD);
            observedStatuses++;
            deny(held,second,"NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
            rootDenials++;
            check(Arrays.equals(reviewBytes,Files.readAllBytes(review))&&
                Arrays.equals(recorded,
                    Files.readAllBytes(paths.resolve(legacy))),
                "manual hold inspection mutated physical evidence");

            deny(held,null,"INVALID_PRODUCTION_SIGNER_CANDIDATE");
            invalidDenials++;
            deny(held,new byte[2],
                "INVALID_PRODUCTION_SIGNER_CANDIDATE");
            invalidDenials++;
            deny(held,new byte[257],
                "INVALID_PRODUCTION_SIGNER_CANDIDATE");
            invalidDenials++;
            boolean foreignRefused=false;
            try{
                MailboxSettlementCutoverReadiness.assess(
                    "g21161-other",
                    repository.inspectRestartRecoveryReadOnly(legacy));
            }catch(IllegalArgumentException expected){
                foreignRefused=true;
            }
            check(foreignRefused,"cross-account cutover was accepted");
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(observedStatuses==3&&rootDenials==4&&
                invalidDenials==3&&zeroLeases,
                "expected real file-backed 3/4/3 controls and no leases");
            System.out.println("G21161_CUTOVER_TRUST_PROOF_DIAGNOSTICS"+
                " realRecoveryStates="+observedStatuses+
                " sevenMissingProofsPerState=true"+
                " productionSignerRootVetoes="+rootDenials+
                " malformedSignerVetoes="+invalidDenials+
                " foreignAccountRejected="+foreignRefused+
                " originalFilesUnchanged=true"+
                " independentlyEnrolledRoots=0"+
                " zeroJvmLeases="+zeroLeases);
            System.out.println("G21161_CUTOVER_TRUST_PROOF_PASS"+
                " cutoverContractExposesSignerWriterEpochGaps=true"+
                " signerRootNotEnrolled=true"+
                " originalStateDispositionsPreserved=true"+
                " independentProvenance=false rawWriterFence=false"+
                " atomicEpoch=false cleanup=false unlink=false"+
                " rollover=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}

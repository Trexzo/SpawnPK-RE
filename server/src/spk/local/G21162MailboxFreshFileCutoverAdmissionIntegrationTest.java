package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.stream.Stream;

/**
 * G21.162: real G21.71 account/marker disk changes force a FRESH
 * cutover review, never admission of a previously retained Decision.
 * The only coherent terminal case still fails G21.159 signer-root
 * admission. All source bytes and World reward counts stay intact.
 */
public final class G21162MailboxFreshFileCutoverAdmissionIntegrationTest {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.162 "+reason);
    }

    @FunctionalInterface
    private interface Checked {
        void run()throws Exception;
    }

    private static void veto(
        Checked call,String diagnostic
    )throws Exception{
        boolean rejected=false;
        try{
            call.run();
        }catch(IOException expected){
            String why=String.valueOf(expected.getMessage());
            rejected=why.contains("NO_AUTHORITY")&&
                why.contains(diagnostic);
        }
        check(rejected,"missing checked read-only cutover veto "+diagnostic);
    }

    private static MailboxFreshFileCutoverAdmissionV1.Review fresh(
        FilePlayerRepository repo,String account,
        MailboxSettlementCutoverReadiness.Disposition disposition,
        FilePlayerRepository.RestartRecoveryEvidence.State state
    )throws Exception{
        MailboxFreshFileCutoverAdmissionV1.Review r=
            MailboxFreshFileCutoverAdmissionV1
                .inspectFreshReadOnly(repo,account);
        check(r.status==
            MailboxFreshFileCutoverAdmissionV1.Status
                .FRESH_FILE_FORENSIC_REVIEW_NO_AUTHORITY&&
            r.account.equals(account)&&r.disposition==disposition&&
            r.state==state&&r.missingProofs.size()==7&&
            r.missingProofs.containsAll(EnumSet.allOf(
                MailboxSettlementCutoverReadiness.MissingProof.class))&&
            !r.historicalEvidenceAuthenticated&&
            !r.independentlyEnrolledSignerRoot&&
            !r.rawDefaultOrFutureWritersFenced&&
            !r.atomicRecoveryEpochProved&&
            !r.snapshotAdmitted&&!r.durabilityConfirmed&&
            !r.transactionCommitted&&!r.cleanupAuthorized&&
            !r.rolloverAuthorized&&!r.pruningAuthorized&&
            !r.unlinkAuthorized&&!r.grantAuthorized&&
            !r.replayAuthorized&&!r.sessionAuthorized&&
            !r.releaseAuthorized&&!r.clientAckAuthorized,
            "fresh file review was not seven-proof NO AUTHORITY");
        return r;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21162-fresh-disk-");
        try{
            FilePlayerRepository.PathResolver resolver=
                account->root.resolve(account+".properties");
            FilePlayerRepository repository=
                new FilePlayerRepository(resolver);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);

            KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair signer=generator.generateKeyPair();
            byte[] candidate=signer.getPublic().getEncoded();
            check(MailboxProductionSignerTrustAdmissionV1
                .independentlyEnrolledProductionRoots()==0,
                "unexpected production signer enrolled");

            int freshReviews=0,staleTransitions=0,stateVetoes=0,
                signerRootVetoes=0,otherVetoes=0;
            final String absent="g21162-missing";
            MailboxFreshFileCutoverAdmissionV1.Review missing=fresh(
                repository,absent,
                MailboxSettlementCutoverReadiness.Disposition
                    .MISSING_ACCOUNT_QUARANTINE,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .MISSING_ACCOUNT_NO_REPLAY);
            freshReviews++;
            check(!missing.originalObjectAndBytesObserved,
                "missing file cannot have exact bytes");
            veto(()->MailboxFreshFileCutoverAdmissionV1
                .requireFreshProductionCutover(repository,absent,candidate),
                "NO_POSITIVE_CUTOVER_STATE_MISSING_ACCOUNT_QUARANTINE");
            stateVetoes++;
            check(!Files.exists(resolver.resolve(absent)),
                "inspection created a missing account");

            final String account="g21162-legacy";
            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered(account);
            writer.saveStrict(PlayerSnapshotCodec.capture(account,legacy));
            byte[] original=Files.readAllBytes(resolver.resolve(account));
            MailboxFreshFileCutoverAdmissionV1.Review previous=fresh(
                repository,account,
                MailboxSettlementCutoverReadiness.Disposition
                    .LEGACY_NOT_MAILBOX_TRANSACTION,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .LEGACY_NO_JOURNAL_NON_ADMITTING);
            freshReviews++;
            check(previous.originalObjectAndBytesObserved,
                "existing legacy file was not observed exactly");
            veto(()->MailboxFreshFileCutoverAdmissionV1
                .requireFreshProductionCutover(repository,account,candidate),
                "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION");
            stateVetoes++;

            // A previously retained LEGACY review becomes stale
            // immediately after a real negative marker appears.
            Path marker=root.resolve(
                account+".properties.g2132-mailbox-review");
            byte[] markerBytes="G21162_NO_GRANT_MARKER\n".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(marker,markerBytes);
            MailboxFreshFileCutoverAdmissionV1.Review after=fresh(
                repository,account,
                MailboxSettlementCutoverReadiness.Disposition
                    .REVIEW_MARKER_MANUAL_HOLD,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .DURABLE_REVIEW_MARKER);
            freshReviews++;
            check(previous.disposition!=after.disposition,
                "stale retained review was reused after marker creation");
            staleTransitions++;
            veto(()->MailboxFreshFileCutoverAdmissionV1
                .requireFreshProductionCutover(repository,account,candidate),
                "NO_POSITIVE_CUTOVER_STATE_REVIEW_MARKER_MANUAL_HOLD");
            stateVetoes++;
            check(Arrays.equals(markerBytes,Files.readAllBytes(marker))&&
                Arrays.equals(original,
                    Files.readAllBytes(resolver.resolve(account))),
                "read-only cutover altered account or marker");

            // Marker deletion is NOT by the admission code. It
            // happens explicitly in this isolated fixture, after
            // which the NEXT call must observe the legacy state.
            Files.delete(marker);
            MailboxFreshFileCutoverAdmissionV1.Review returned=fresh(
                repository,account,
                MailboxSettlementCutoverReadiness.Disposition
                    .LEGACY_NOT_MAILBOX_TRANSACTION,
                FilePlayerRepository.RestartRecoveryEvidence.State
                    .LEGACY_NO_JOURNAL_NON_ADMITTING);
            freshReviews++;
            check(after.disposition!=returned.disposition,
                "stale manual-hold result reused after marker removal");
            staleTransitions++;
            veto(()->MailboxFreshFileCutoverAdmissionV1
                .requireFreshProductionCutover(repository,account,candidate),
                "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION");
            stateVetoes++;
            check(Arrays.equals(original,
                Files.readAllBytes(resolver.resolve(account))),
                "legacy file modified by cutover");

            // Real COHERENT terminal: derive the account image from
            // the original (non-granting) G21.79 test pattern.
            try(World world=World.isolatedForTest(60000L)){
                final String terminal="g21162-terminal";
                WorldPlayer owner=new WorldPlayer();
                long generation=world.registerPlayer(owner,terminal);
                String messageId=terminal+":gift";
                owner.mailbox().deliver(new RewardDeliveryMessage(
                    messageId,"G21.162 test Mailbox","NO_GRANT",
                    Collections.singletonList(
                        new RewardDeliveryMessage.Attachment(995,25)),
                    "CUSTOM_LOCALLAB_G21162_TEST_FIXTURE"));
                MailboxRewardDeliveryService.Snapshot row=
                    owner.mailbox().get(messageId);
                MailboxPreparedClaimJournal.stageOnly(owner,
                    MailboxPreparedClaimJournal.prepare(owner,row));
                MailboxSettlementPostimagePlanner.Proposal plan=
                    MailboxSettlementPostimagePlanner.plan(
                        owner,generation,row);
                writer.saveStrict(
                    MailboxAtomicTerminalSnapshot.compose(plan));
                byte[] terminalBytes=Files.readAllBytes(
                    resolver.resolve(terminal));
                MailboxFreshFileCutoverAdmissionV1.Review terminalReview=fresh(
                    repository,terminal,
                    MailboxSettlementCutoverReadiness.Disposition
                        .COHERENT_TERMINAL_NOT_COMMITTED,
                    FilePlayerRepository.RestartRecoveryEvidence.State
                        .COHERENT_TERMINAL_QUARANTINE);
                freshReviews++;
                check(terminalReview.originalObjectAndBytesObserved,
                    "terminal must have exact physical read");
                veto(()->MailboxFreshFileCutoverAdmissionV1
                    .requireFreshProductionCutover(
                        repository,terminal,candidate),
                    "NO_INDEPENDENTLY_ENROLLED_PRODUCTION_ROOT");
                signerRootVetoes++;
                veto(()->MailboxFreshFileCutoverAdmissionV1
                    .requireFreshProductionCutover(
                        repository,terminal,new byte[2]),
                    "INVALID_PRODUCTION_SIGNER_CANDIDATE");
                otherVetoes++;
                check(Arrays.equals(terminalBytes,
                    Files.readAllBytes(resolver.resolve(terminal)))&&
                    owner.bank().inventorySlots()==0&&
                    owner.mailbox().get(messageId).claimState==
                      MailboxRewardDeliveryService.ClaimState.UNCLAIMED,
                    "cutover admitted terminal or paid live reward");
            }

            veto(()->MailboxFreshFileCutoverAdmissionV1
                .requireFreshProductionCutover(
                    repository,"INVALID ACCOUNT!",candidate),
                "INVALID_FRESH_CUTOVER_ACCOUNT");
            otherVetoes++;
            boolean noPositiveMethodAcceptsRetainedDecision=true;
            for(java.lang.reflect.Method m:
                    MailboxFreshFileCutoverAdmissionV1.class
                        .getDeclaredMethods()){
                if(!m.getName().equals("requireFreshProductionCutover"))
                    continue;
                for(Class<?> type:m.getParameterTypes())
                    if(type==MailboxSettlementCutoverReadiness.Decision.class||
                       type==MailboxFreshFileCutoverAdmissionV1.Review.class)
                        noPositiveMethodAcceptsRetainedDecision=false;
            }
            check(noPositiveMethodAcceptsRetainedDecision,
                "cutover API takes stale decision as authority");
            boolean noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(freshReviews==5&&staleTransitions==2&&
                stateVetoes==4&&signerRootVetoes==1&&
                otherVetoes==2&&noLeases,
                "G21.162 counts or lease leak");
            System.out.println("G21162_FRESH_FILE_CUTOVER_DIAGNOSTICS"+
                " realFileReviews="+freshReviews+
                " markerTransitionRefreshes="+staleTransitions+
                " negativeStateVetoes="+stateVetoes+
                " unprovisionedRootVetoes="+signerRootVetoes+
                " otherCheckedVetoes="+otherVetoes+
                " retainedDecisionNotAcceptedAsAdmission=true"+
                " accountAndMarkerBytesUnchanged=true"+
                " zeroJvmLeases="+noLeases);
            System.out.println("G21162_FRESH_FILE_CUTOVER_PASS"+
                " G2171OnDiskRestartInspectorUsed=true"+
                " realTerminalRemainQuarantined=true"+
                " independentlyAuthenticatedSigner=false"+
                " rawWriterFence=false atomicEpoch=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}

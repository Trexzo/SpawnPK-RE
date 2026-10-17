package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.119: a G21.118 diagnostic v2 pair is NOT a real writer or
 * disk publication. Typed real-writer labels alone also cannot
 * substitute for an independently classified G21.86 disk COMMIT.
 *
 * All classifications are forensic and confer zero grant authority.
 */
public final class G21119MailboxDiagnosticRealWriterSemanticBoundaryIntegrationTest {
    private static final String REAL="g21119-real";
    private static final String ONLY="g21119-diag";
    private static final String FORGED="g21119-labels";
    private static final String MISSING="g21119-three";

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.119 "+message);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long g,MailboxSettlementPostimagePlanner.Proposal q){
            player=p;generation=g;proposal=q;
            terminal=MailboxAtomicTerminalSnapshot.compose(q);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.119 semantic writer boundary","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21119_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,gen,
            MailboxSettlementPostimagePlanner.plan(
                player,gen,selected));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path path)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static String shaOrAbsent(Path file)throws Exception{
        return Files.exists(file,LinkOption.NOFOLLOW_LINKS)
            ?sha(file):"ABSENT";
    }
    @FunctionalInterface
    private interface Action {void run()throws IOException;}
    private static boolean denied(Action f,String text)throws IOException{
        try{f.run();return false;}
        catch(IOException ex){
            return String.valueOf(ex.getMessage()).contains(text);
        }
    }
    private static final class Pins {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String account,prepared,commit,typed;
        Pins(FilePlayerRepository.PathResolver r,String a)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,a);
            typed=MailboxWriterKindEvidenceV2.capture(r,a).sha256;
            account=sha(r.resolve(a));
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord record=
                new MailboxGuardedDiskCommitRecord(r);
            prepared=shaOrAbsent(journal.journalPath(a));
            commit=shaOrAbsent(record.recordPath(a));
        }
    }
    private static MailboxWriterArchiveLiveReconciliationV1.Report old5(
        FilePlayerRepository.PathResolver r,String a,Pins pins
    )throws IOException{
        return MailboxWriterArchiveLiveReconciliationV1
            .comparePinnedTypedRecoveryLeaves(
                r,a,pins.v1.portable,pins.v1.sha256,
                pins.account,pins.prepared,pins.commit,pins.typed);
    }
    private static MailboxRealWriterSemanticBoundaryV1.Report semantic(
        FilePlayerRepository.PathResolver r,String a,Pins pins
    )throws IOException{
        MailboxRealWriterSemanticBoundaryV1.Report x=
            MailboxRealWriterSemanticBoundaryV1.inspectPinned(
                r,a,pins.v1.portable,pins.v1.sha256,pins.account,
                pins.prepared,pins.commit,pins.typed);
        check(!x.sourceWriterKindAuthenticityProved&&
            !x.labelUuidBoundToDiskCommitProved&&
            !x.historicalWriterParticipationProved&&
            !x.allFutureWritersFenced&&
            !x.epochOrTransactionAtomicityProved&&
            !x.businessOperationOrLiveApplyProved&&
            !x.archiveRolloverAuthorized&&
            !x.recordPruningAuthorized&&!x.cleanupAuthorized&&
            !x.unlinkAuthorized&&!x.grantAuthorized&&
            !x.replayAuthorized&&!x.sessionAuthorized&&
            !x.releaseAuthorized&&!x.clientAckAuthorized&&
            !x.fivePlane.allWriterVersionsFenced&&
            !x.fivePlane.grantAuthorized,
            "semantic record promoted observational evidence");
        return x;
    }
    private static MailboxRealWriterSemanticBoundaryV1.Report strict(
        FilePlayerRepository.PathResolver r,String a,Pins pins
    )throws IOException{
        return MailboxRealWriterSemanticBoundaryV1
            .requirePinnedRealWriterCandidate(
                r,a,pins.v1.portable,pins.v1.sha256,pins.account,
                pins.prepared,pins.commit,pins.typed);
    }
    private static void fakeKind(
        FilePlayerRepository.PathResolver r,String account,
        MailboxWriterKindEvidenceV2.Kind kind
    )throws IOException{
        Path selected=r.resolve(account);
        MailboxPublicationWriterLifecycle.withWriter(selected,()->
            MailboxWriterAttemptEvidenceV1.runRecordedInsideWriter(
                selected,account,kind,()->Boolean.TRUE));
    }
    private static boolean archiveUnchanged(
        FilePlayerRepository.PathResolver r,String a,Pins pins
    )throws IOException{
        MailboxWriterEvidencePortableArchiveV1.Verified restored=
            MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                pins.v1.portable,pins.v1.sha256);
        return restored.account.equals(a)&&
            restored.records==
                MailboxWriterAttemptEvidenceV1.inspect(r,a)
                    .recordCount&&!restored.cleanupAuthorized&&
            !restored.rolloverAuthorized;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21119-semantic-witness-");
        boolean realCandidate=false,realDiskStillNegative=false;
        boolean diagnosticOnlyRejected=false,diagMixedRejected=false;
        boolean fakeFourKindsNoCommitRejected=false;
        boolean missingKindRejected=false,oldWitnessCompatible=false;
        boolean oldArchivesCompatible=false,noRewards=false,zeroLeases=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=paths(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);

            Seed real=seed(world,REAL);
            writer.saveStrictWithWriterEvidence(
                real.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(real.proposal);
            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    real.player,real.generation,real.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence receipt=
                world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        token,real.proposal,real.terminal,writer)
                    .get(15L,TimeUnit.SECONDS);
            commit.recordConfirmedDiskTerminalWithWriterEvidence(
                real.proposal,receipt.strictReceipt);
            Pins realPins=new Pins(r,REAL);
            MailboxRealWriterSemanticBoundaryV1.Report good=
                strict(r,REAL,realPins);
            realCandidate=good.status==
                MailboxRealWriterSemanticBoundaryV1.Status
                    .FOUR_REAL_KIND_LABELS_AND_DISK_MATCH_NO_GRANT&&
                good.allFourRealLabelsObserved&&
                good.realWriterKindPairs==4&&
                good.diagnosticPairs==0&&
                good.diskCommit.recordValidated&&
                good.diskCommit.diskCommitRecordMatched&&noGrant(real);
            realDiskStillNegative=
                !good.diskCommit.transactionCommitted&&
                !good.diskCommit.liveApplied&&
                !good.diskCommit.grantAuthorized&&
                !good.diskCommit.replayAuthorized&&
                good.fivePlane.pinnedTypedWriterKindBytesMatched&&
                good.fivePlane.pinnedRecoverySidecarBytesMatched;

            // A fully typed, fully paired DIAGNOSTIC-ONLY account
            // satisfies the original five-plane content witness.
            // It must NEVER be promoted to a business publication.
            Seed only=seed(world,ONLY);
            writer.saveStrict(only.proposal.preparedPreimage);
            MailboxCrossJvmDurableWriterDiagnosticV1.runRecorded(
                r,ONLY,()->Boolean.TRUE);
            Pins diagPins=new Pins(r,ONLY);
            oldWitnessCompatible=old5(r,ONLY,diagPins)
                .pinnedTypedWriterKindBytesMatched;
            MailboxRealWriterSemanticBoundaryV1.Report d=
                semantic(r,ONLY,diagPins);
            diagnosticOnlyRejected=d.status==
                MailboxRealWriterSemanticBoundaryV1.Status
                    .DIAGNOSTIC_ONLY_NOT_BUSINESS_EVIDENCE&&
                d.realWriterKindPairs==0&&d.diagnosticPairs==1&&
                d.diskCommit==null&&denied(
                    ()->strict(r,ONLY,diagPins),
                    "G21.119 REAL_WRITER_SEMANTIC_GATE_NO_AUTHORITY");

            // Add one diagnostic AFTER the real COMMIT: old pinned
            // v1/v2/sidecars still agree, and the COMMIT remains a
            // matched disk observation. Strict real-only gate vetoes.
            MailboxCrossJvmDurableWriterDiagnosticV1.runRecorded(
                r,REAL,()->Boolean.TRUE);
            Pins mixedPins=new Pins(r,REAL);
            oldWitnessCompatible&=old5(r,REAL,mixedPins)
                .pinnedTypedWriterKindBytesMatched;
            MailboxRealWriterSemanticBoundaryV1.Report mixed=
                semantic(r,REAL,mixedPins);
            diagMixedRejected=mixed.status==
                MailboxRealWriterSemanticBoundaryV1.Status
                    .DIAGNOSTIC_MIXED_WITH_REAL_NOT_BUSINESS_ONLY&&
                mixed.allFourRealLabelsObserved&&
                mixed.realWriterKindPairs==4&&
                mixed.diagnosticPairs==1&&
                mixed.diskCommit==null&&
                commit.inspect(REAL).diskCommitRecordMatched&&denied(
                    ()->strict(r,REAL,mixedPins),
                    "G21.119 REAL_WRITER_SEMANTIC_GATE_NO_AUTHORITY");

            // Four caller-supplied typed labels are not authentication
            // of four REAL operations. No journal or COMMIT is written.
            Seed forged=seed(world,FORGED);
            writer.saveStrict(forged.proposal.preparedPreimage);
            fakeKind(r,FORGED,MailboxWriterKindEvidenceV2.Kind
                .STRICT_PREPARED);
            fakeKind(r,FORGED,MailboxWriterKindEvidenceV2.Kind
                .PREPARED_JOURNAL);
            fakeKind(r,FORGED,MailboxWriterKindEvidenceV2.Kind
                .WORLD_TERMINAL);
            fakeKind(r,FORGED,MailboxWriterKindEvidenceV2.Kind
                .DISK_COMMIT);
            Pins forgedPins=new Pins(r,FORGED);
            oldWitnessCompatible&=old5(r,FORGED,forgedPins)
                .pinnedTypedWriterKindBytesMatched;
            MailboxRealWriterSemanticBoundaryV1.Report fake=
                semantic(r,FORGED,forgedPins);
            fakeFourKindsNoCommitRejected=fake.status==
                MailboxRealWriterSemanticBoundaryV1.Status
                    .REAL_KIND_LABELS_WITHOUT_DISK_COMMIT&&
                fake.allFourRealLabelsObserved&&fake.diagnosticPairs==0&&
                fake.diskCommit.status==
                    MailboxGuardedDiskCommitRecord.Status.ABSENT&&
                denied(()->strict(r,FORGED,forgedPins),
                    "G21.119 REAL_WRITER_SEMANTIC_GATE_NO_AUTHORITY");

            Seed missing=seed(world,MISSING);
            writer.saveStrict(missing.proposal.preparedPreimage);
            fakeKind(r,MISSING,MailboxWriterKindEvidenceV2.Kind
                .STRICT_PREPARED);
            fakeKind(r,MISSING,MailboxWriterKindEvidenceV2.Kind
                .PREPARED_JOURNAL);
            fakeKind(r,MISSING,MailboxWriterKindEvidenceV2.Kind
                .WORLD_TERMINAL);
            Pins missingPins=new Pins(r,MISSING);
            oldWitnessCompatible&=old5(r,MISSING,missingPins)
                .pinnedTypedWriterKindBytesMatched;
            MailboxRealWriterSemanticBoundaryV1.Report notFour=
                semantic(r,MISSING,missingPins);
            missingKindRejected=notFour.status==
                MailboxRealWriterSemanticBoundaryV1.Status
                    .MISSING_REAL_WRITER_KINDS_NO_AUTHORITY&&
                notFour.realWriterKindPairs==3&&
                !notFour.allFourRealLabelsObserved&&
                denied(()->strict(r,MISSING,missingPins),
                    "G21.119 REAL_WRITER_SEMANTIC_GATE_NO_AUTHORITY");

            oldArchivesCompatible=
                archiveUnchanged(r,REAL,mixedPins)&&
                archiveUnchanged(r,ONLY,diagPins)&&
                archiveUnchanged(r,FORGED,forgedPins)&&
                archiveUnchanged(r,MISSING,missingPins);
            noRewards=noGrant(real)&&noGrant(only)&&
                noGrant(forged)&&noGrant(missing);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21119_SEMANTIC_BOUNDARY_DIAGNOSTICS"+
                " actualFourKindsAndDiskCandidate="+realCandidate+
                " matchedDiskStillNoLiveGrant="+realDiskStillNegative+
                " completedDiagnosticOnlyDenied="+diagnosticOnlyRejected+
                " fourRealPlusDiagnosticDenied="+diagMixedRejected+
                " syntheticFourLabelsWithoutCommitDenied="+
                    fakeFourKindsNoCommitRejected+
                " missingRealKindDenied="+missingKindRejected+
                " preexistingFivePlaneBackwardCompatible="+
                    oldWitnessCompatible+
                " originalV1ArchivesUnchanged="+oldArchivesCompatible+
                " noLiveRewards="+noRewards+
                " zeroJvmLeases="+zeroLeases);
            check(realCandidate&&realDiskStillNegative&&
                diagnosticOnlyRejected&&diagMixedRejected&&
                fakeFourKindsNoCommitRejected&&missingKindRejected&&
                oldWitnessCompatible&&oldArchivesCompatible&&
                noRewards&&zeroLeases,
                "diagnostic/real semantic separation acceptance");
            System.out.println("G21119_REAL_WRITER_SEMANTIC_BOUNDARY_PASS"+
                " diagnosticNotBusiness=true"+
                " realLabelsRequireDiskCommit=true"+
                " kindsNotAuthenticated=true"+
                " fivePlanesUnchanged=true"+
                " existingDefaultsUnchanged=true"+
                " writerGenerationFence=false epoch=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}

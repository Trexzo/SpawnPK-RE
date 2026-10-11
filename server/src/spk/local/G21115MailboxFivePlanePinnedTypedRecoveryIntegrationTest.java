package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.115: FIVE separately pinned read-only content planes, two locked
 * passes separated by an unlocked interval. No archive or settlement
 * authorization, historical writer census or writer-generation fence.
 */
public final class G21115MailboxFivePlanePinnedTypedRecoveryIntegrationTest {
    private static final String FOUR="g21115-four";
    private static final String MIXED="g21115-mixed";

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.115 "+message);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return account->dir.resolve(account+".properties");
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
        long generation=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.115 five-plane fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21115_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,generation,
            MailboxSettlementPostimagePlanner.plan(
                player,generation,selected));
    }
    private static String sha(Path file)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder hex=new StringBuilder(64);
        for(byte x:digest)
            hex.append(String.format(Locale.ROOT,"%02x",x&255));
        return hex.toString();
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    @FunctionalInterface
    private interface Op {void call()throws Exception;}
    private static boolean denied(Op op,String marker)throws Exception{
        try{op.call();return false;}
        catch(IOException x){
            return String.valueOf(x.getMessage()).contains(marker);
        }
    }
    private static Path typedComplete(Path root,String account)
        throws IOException{
        try(Stream<Path> items=Files.list(root)){
            return items.filter(x->x.getFileName().toString().startsWith(
                    account+".properties.g21114-writer-kind-v2-"))
                .filter(x->x.getFileName().toString().endsWith(".complete"))
                .sorted(Comparator.comparing(Path::toString))
                .findFirst().orElseThrow(()->new IOException(
                    "G21.115 missing typed fixture complete"));
        }
    }
    private static MailboxWriterArchiveLiveReconciliationV1.Report check5(
        FilePlayerRepository.PathResolver r,String account,
        MailboxWriterEvidencePortableArchiveV1.Snapshot v1,
        String accountSha,String journalSha,String commitSha,String kindSha,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        return MailboxWriterArchiveLiveReconciliationV1
            .comparePinnedTypedRecoveryLeaves(
                r,account,v1.portable,v1.sha256,accountSha,
                journalSha,commitSha,kindSha,between);
    }
    private static void noAuthority(
        MailboxWriterArchiveLiveReconciliationV1.Report x
    ){
        check(!x.authoritativeAccountContentProved&&
            !x.allWriterVersionsFenced&&
            !x.futureLegacyWritersExcluded&&
            !x.ledgerLifetimeArchived&&
            !x.sourceFutureImmutabilityProved&&
            !x.rolloverAuthorized&&!x.recordPruningAuthorized&&
            !x.cleanupAuthorized&&!x.unlinkAuthorized&&
            !x.grantAuthorized&&!x.replayAuthorized&&
            !x.sessionAdmissionAuthorized&&!x.releaseAuthorized&&
            !x.clientAckAuthorized,
            "reconciliation manufactured authority");
        if(x.typedWriterKindReport!=null){
            MailboxWriterKindEvidenceV2.Report t=x.typedWriterKindReport;
            check(!t.sourceAuthenticityVerified&&
                !t.completeHistoricalWriterPopulation&&
                !t.defaultWritersEnrolled&&
                !t.allFutureWritersExcluded&&
                !t.cleanupAuthorized&&!t.rolloverAuthorized&&
                !t.grantAuthorized&&!t.sessionAuthorized,
                "typed manifest manufactured authority");
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21115-five-plane-");
        boolean matched=false,legacyApisCompatible=false;
        boolean wrongKindPinDenied=false,missingKindPinDenied=false;
        boolean mixedV1Denied=false,typedPayloadTamperDenied=false;
        boolean typedLeafMissingDenied=false,interpassKindDriftDenied=false;
        boolean interpassLegacyDriftDenied=false;
        boolean preparedDriftDenied=false,commitDriftDenied=false;
        boolean noGrants=false,zeroLeases=false;
        try(World world=World.isolatedForTest(
            60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);

            Seed full=seed(world,FOUR);
            strict.saveStrictWithWriterEvidence(
                full.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(
                full.proposal);
            WorldPlayerPersistence.PreparedAccountReservation reservation=
                world.persistence().reservePreparedAccount(
                    full.player,full.generation,full.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence published=
                world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        reservation,full.proposal,full.terminal,strict)
                    .get(15,TimeUnit.SECONDS);
            commit.recordConfirmedDiskTerminalWithWriterEvidence(
                full.proposal,published.strictReceipt);

            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=
                MailboxWriterEvidencePortableArchiveV1.capture(r,FOUR);
            MailboxWriterKindEvidenceV2.Snapshot v2=
                MailboxWriterKindEvidenceV2.capture(r,FOUR);
            String accountSha=sha(r.resolve(FOUR));
            String preparedSha=sha(journal.journalPath(FOUR));
            String commitSha=sha(commit.recordPath(FOUR));
            MailboxWriterArchiveLiveReconciliationV1.Report good=
                check5(r,FOUR,v1,accountSha,preparedSha,commitSha,
                    v2.sha256,()->{});
            noAuthority(good);
            matched=good.pinnedCanonicalBytesMatched&&
                good.pinnedRecoverySidecarBytesMatched&&
                good.pinnedTypedWriterKindBytesMatched&&
                good.matchedRecords==8&&
                good.typedWriterKindReport.status==
                    MailboxWriterKindEvidenceV2.Status
                        .OBSERVED_TYPED_PAIRED_NO_AUTHORITY&&
                good.typedWriterKindReport.pairedByKind.size()==4&&
                good.typedWriterKindReport.typedBegins==4&&
                good.typedWriterKindReport.typedCompletes==4&&
                noGrant(full);
            MailboxWriterArchiveLiveReconciliationV1.Report old=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedRecoveryLeaves(
                        r,FOUR,v1.portable,v1.sha256,accountSha,
                        preparedSha,commitSha);
            noAuthority(old);
            legacyApisCompatible=old.pinnedCanonicalBytesMatched&&
                old.pinnedRecoverySidecarBytesMatched&&
                !old.pinnedTypedWriterKindBytesMatched&&
                old.typedWriterKindReport==null;

            String bad="0".repeat(64);
            wrongKindPinDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,bad,()->{}),
                "G21.115 TYPED_WITNESS_MISMATCH_NO_AUTHORITY");
            missingKindPinDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,
                "not-a-digest",()->{}),
                "G21.115 TYPED_WITNESS_MISMATCH_NO_AUTHORITY");

            Seed mixed=seed(world,MIXED);
            strict.saveStrictWithWriterEvidence(
                mixed.proposal.preparedPreimage);
            MailboxWriterAttemptEvidenceV1.runRecorded(
                r,MIXED,()->Boolean.TRUE);
            MailboxWriterEvidencePortableArchiveV1.Snapshot mV1=
                MailboxWriterEvidencePortableArchiveV1.capture(r,MIXED);
            MailboxWriterKindEvidenceV2.Snapshot mV2=
                MailboxWriterKindEvidenceV2.capture(r,MIXED);
            mixedV1Denied=denied(()->check5(
                r,MIXED,mV1,sha(r.resolve(MIXED)),
                "ABSENT","ABSENT",mV2.sha256,()->{}),
                "G21.115 TYPED_WITNESS_MISMATCH_NO_AUTHORITY")&&
                mV2.report.status==
                    MailboxWriterKindEvidenceV2.Status
                        .MIXED_TYPED_AND_UNTYPED_UNKNOWN;

            Path leaf=typedComplete(root,FOUR);
            byte[] originalKind=Files.readAllBytes(leaf);
            interpassKindDriftDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,
                v2.sha256,()->Files.write(leaf,
                    "G21115_CHANGED_BETWEEN_PASSES".getBytes(
                        StandardCharsets.US_ASCII))),
                "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP");
            Files.write(leaf,originalKind);
            byte[] changed=originalKind.clone();
            changed[0]^=1;
            Files.write(leaf,changed);
            typedPayloadTamperDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,
                v2.sha256,()->{}),
                "G21.114 TYPED_WRITER_EVIDENCE_UNSAFE_NO_CLEANUP");
            Files.write(leaf,originalKind);

            Path prepared=journal.journalPath(FOUR);
            byte[] preparedBytes=Files.readAllBytes(prepared);
            Files.write(prepared,
                "G21115_MUTATED_PREPARED".getBytes(
                    StandardCharsets.US_ASCII));
            preparedDriftDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,
                v2.sha256,()->{}),
                "G21.108 PINNED_SIDECAR_CONTENT_MISMATCH_NO_AUTHORITY");
            Files.write(prepared,preparedBytes);
            Path commitRecord=commit.recordPath(FOUR);
            byte[] commitBytes=Files.readAllBytes(commitRecord);
            Files.write(commitRecord,
                "G21115_MUTATED_COMMIT".getBytes(
                    StandardCharsets.US_ASCII));
            commitDriftDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,
                v2.sha256,()->{}),
                "G21.108 PINNED_SIDECAR_CONTENT_MISMATCH_NO_AUTHORITY");
            Files.write(commitRecord,commitBytes);

            // The default writer still bypasses v1 AND v2 even when
            // all four prior typed attempts match independently
            // pinned bytes. This raw/legacy interval veto is essential.
            interpassLegacyDriftDenied=denied(()->check5(
                r,FOUR,v1,accountSha,preparedSha,commitSha,
                v2.sha256,
                ()->strict.saveStrict(full.proposal.preparedPreimage)),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH_NO_AUTHORITY");

            // A missing typed leaf after all prior content checks is
            // incomplete/unknown, never evidence of writer coverage.
            Files.delete(leaf);
            typedLeafMissingDenied=denied(()->check5(
                r,FOUR,v1,sha(r.resolve(FOUR)),preparedSha,
                commitSha,v2.sha256,()->{}),
                "G21.115 TYPED_WITNESS_MISMATCH_NO_AUTHORITY");

            noGrants=noGrant(full)&&noGrant(mixed);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G21115_FIVE_PLANE_DIAGNOSTICS"+
                " pinnedFivePlanesMatched="+matched+
                " previousG21108ApiUnchanged="+legacyApisCompatible+
                " wrongTypedShaDenied="+wrongKindPinDenied+
                " malformedTypedShaDenied="+missingKindPinDenied+
                " mixedLegacyV1Denied="+mixedV1Denied+
                " tamperedTypedContentDenied="+typedPayloadTamperDenied+
                " missingTypedCompanionDenied="+typedLeafMissingDenied+
                " interpassTypedDriftDenied="+interpassKindDriftDenied+
                " interpassLegacyAccountDriftDenied="+
                    interpassLegacyDriftDenied+
                " preparedPinDriftDenied="+preparedDriftDenied+
                " commitPinDriftDenied="+commitDriftDenied+
                " noLiveGrant="+noGrants+
                " zeroJvmLeases="+zeroLeases);
            check(matched&&legacyApisCompatible&&wrongKindPinDenied&&
                missingKindPinDenied&&mixedV1Denied&&
                typedPayloadTamperDenied&&typedLeafMissingDenied&&
                interpassKindDriftDenied&&interpassLegacyDriftDenied&&
                preparedDriftDenied&&commitDriftDenied&&
                noGrants&&zeroLeases,
                "fail-closed five-plane typed recovery contract");
            System.out.println("G21115_PINNED_TYPED_RECOVERY_PASS"+
                " pinnedTypedV2=true pinnedV1=true"+
                " pinnedCanonical=true pinnedRecoverySidecars=true"+
                " defaultWritersStillBypass=true"+
                " historicalWriterCoverage=false"+
                " epochSealed=false rollover=false pruning=false"+
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
        check(args.length==0,"unexpected arguments");
        run();
    }
}

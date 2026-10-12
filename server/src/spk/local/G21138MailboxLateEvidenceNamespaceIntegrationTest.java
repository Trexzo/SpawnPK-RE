package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.138: regression-only last-observer newly introduced v1/v2/v3
 * evidence namespace leaves; exact pins must never permit acceptance.
 * Existing G21.136 opt-in final scan is the gate. No grant/epoch.
 */
public final class G21138MailboxLateEvidenceNamespaceIntegrationTest {
    private static final String ACCOUNT="g21138-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.138 "+message);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return a->dir.resolve(a+".properties");
    }
    private static String sha(Path file)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder text=new StringBuilder(64);
        for(byte b:bytes)
            text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r)throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,ACCOUNT);
            v2=MailboxWriterKindEvidenceV2.capture(r,ACCOUNT);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,ACCOUNT);
            account=sha(r.resolve(ACCOUNT));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(ACCOUNT));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(ACCOUNT));
        }
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        physical(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(r,ACCOUNT,p.v3.sha256,
                p.v1.portable,p.v1.sha256,p.account,p.prepared,
                p.commit,p.v2.sha256,LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneImmutableUuidCohortV1.Report
        strict(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneImmutableUuidCohortV1
            .comparePinnedImmutableUuidCohort(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static boolean denied(
        FilePlayerRepository.PathResolver r,Pins p,String expected
    ){
        try{strict(r,p);return false;}
        catch(IOException refused){
            return String.valueOf(refused.getMessage())
                .contains(expected);
        }
    }
    private static void negative(
        MailboxSevenPlaneImmutableUuidCohortV1.Report result
    ){
        check(result.exactFourV1V2V3UuidCohortObserved&&
            result.physical.sevenPhysicalPinsMatched&&
            result.physical.threePhysicalIdentitiesStableAcrossCaptures&&
            result.physical.evidenceLeafMetadataStableAcrossCaptures&&
            !result.v3ResultPayloadAuthorAuthenticated&&
            !result.realCallbackExecutionProved&&
            !result.rawOrDefaultWritersExcluded&&
            !result.historicalAndFutureWritersFenced&&
            !result.independentPinAuthenticationProved&&
            !result.atomicRecoveryEpochProved&&
            !result.richSemanticCohortWithinLeaseProved&&
            !result.rolloverAuthorized&&!result.pruningAuthorized&&
            !result.cleanupAuthorized&&!result.unlinkAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.sessionAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,"physical cohort promoted grant");
    }
    private static MailboxSevenPlaneV3PayloadCohortV1.Report payload(
        FilePlayerRepository.PathResolver r,Pins p
    )throws IOException{
        return MailboxSevenPlaneV3PayloadCohortV1
            .comparePinnedFourPayloads(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneContinuousSemanticCohortV1.Report
        continuous(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        return MailboxSevenPlaneContinuousSemanticCohortV1
            .comparePinnedContinuousSemantic(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,()->{});
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        withObserver(
            FilePlayerRepository.PathResolver r,Pins p,
            MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.InsideLeaseObserver
                visitor
        )throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,between,visitor);
    }
    private static void healthy(FilePlayerRepository.PathResolver r,Pins p)
        throws IOException{
        check(physical(r,p).sevenPhysicalPinsMatched,
            "original physical witness did not recover");
        check(continuous(r,p).originalFourSemanticInspectorsInsideSameLease,
            "original semantic witness did not recover");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "G21.100 lock retained after healthy re-entry");
    }
    /**
     * Insert only a NEW test-specific evidence filename after the
     * SECOND observer and the G21.129 before/after scans, but before
     * the optional third physical scan exits its G21.100 lease.
     * The scanned source namespace must fail closed, then the exact
     * injected leaf is deleted; all real leaves remain untouched.
     */
    private static boolean injectedLateLeafDenied(
        FilePlayerRepository.PathResolver resolver,
        Pins pinned,Path path,byte[] data,String expectedVeto
    )throws Exception{
        check(!Files.exists(path,LinkOption.NOFOLLOW_LINKS),
            "test injection path already exists");
        int[] observed={0};
        int[] between={0};
        boolean denied=false;
        try{
            try{
                withObserver(resolver,pinned,()->between[0]++,
                    (selectedAccount,account,snapshot)->{
                        if(++observed[0]==2){
                            Files.write(path,data,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE);
                        }
                    });
            }catch(IOException refused){
                denied=String.valueOf(refused.getMessage())
                    .contains(expectedVeto);
            }
            check(observed[0]==2&&between[0]==1,
                "must fail only AFTER both observed physical captures");
            check(Files.exists(path,LinkOption.NOFOLLOW_LINKS),
                "test injection not created at last observation");
            return denied;
        }finally{
            Files.deleteIfExists(path);
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21138-cohort-");
        boolean genuine=false,lateNamespaceRejected=false;
        boolean validButUnpairedV1Rejected=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEachInjection=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.138 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21138_TEST_ONLY"));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(id);
            MailboxPreparedClaimJournal.stageOnly(player,
                MailboxPreparedClaimJournal.prepare(player,selected));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    player,generation,selected);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot
                .compose(proposal);
            StrictDurablePlayerSnapshotWriter strictWriter=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);
            strictWriter.saveStrictWithResultBinding(
                proposal.preparedPreimage);
            journal.publishPreparedIntentWithResultBinding(proposal);
            WorldPlayerPersistence.PreparedAccountReservation ticket=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence saved=
                world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        ticket,proposal,terminal,strictWriter)
                    .get(15L,TimeUnit.SECONDS);
            check(saved.strictReceipt.matchesSnapshot(terminal),
                "real World strict Receipt");
            disk.recordConfirmedDiskTerminalWithResultBinding(
                proposal,saved.strictReceipt);
            Pins original=new Pins(r);
            negative(strict(r,original));
            check(payload(r,original).canonicalFourV3PayloadsObserved,
                "original G21.133 canonical payloads");
            MailboxSevenPlaneContinuousSemanticCohortV1.Report real=
                continuous(r,original);
            check(real.originalFourSemanticInspectorsInsideSameLease&&
                real.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                real.semanticHashesMatchV3Payloads&&
                !real.externallyAuthenticatedWriterProvenance&&
                !real.rawOrDefaultWriterExclusionProved&&
                !real.historicalAndFutureWritersFenced&&
                !real.independentPinAuthenticationProved&&
                !real.atomicCrossFileEpochProved&&
                !real.hardwarePowerLossProved&&
                !real.cleanupAuthorized&&!real.unlinkAuthorized&&
                !real.pruningAuthorized&&!real.rolloverAuthorized&&
                !real.grantAuthorized&&!real.replayAuthorized&&
                !real.sessionAuthorized&&!real.releaseAuthorized&&
                !real.clientAckAuthorized,
                "semantic witness improperly promoted authority");
            genuine=true;

            Path account=r.resolve(ACCOUNT);
            String basename=account.getFileName().toString();
            String prefix1=".g21103-writer-attempt-";
            String prefix2=".g21114-writer-kind-";
            String unknownV3=".g21129-unrecognized-result-v3";
            String injected="G21138-TEST-ONLY-UNKNOWN-EVIDENCE\n";
            Path[] unknown={
                root.resolve(basename+prefix1+"v9-unknown.begin"),
                root.resolve(basename+prefix2+"v9-unknown.begin"),
                root.resolve(basename+unknownV3+".result")
            };
            String[] exactReason={
                "UNKNOWN_WRITER_EVIDENCE_VERSION_NO_UNLINK",
                "UNKNOWN_KIND_VERSION",
                "UNKNOWN_G2112_RESULT_LEAF"
            };
            int rejected=0;
            for(int n=0;n<unknown.length;n++){
                check(injectedLateLeafDenied(r,original,unknown[n],
                    injected.getBytes(StandardCharsets.US_ASCII),
                    exactReason[n]),
                    "late evidence namespace was accepted kind="+n);
                rejected++;
                healthy(r,original);
            }
            lateNamespaceRejected=rejected==3;
            String foreignUuid="11111111-2222-4333-8444-555555555555";
            Path syntacticV1=root.resolve(
                basename+prefix1+"v1-"+foreignUuid+".begin");
            String canonical=MailboxWriterAttemptEvidenceV1.PROTOCOL+
                "\nACCOUNT="+ACCOUNT+"\nATTEMPT="+foreignUuid+
                "\nSTAGE=BEGIN\n";
            validButUnpairedV1Rejected=injectedLateLeafDenied(
                r,original,syntacticV1,
                canonical.getBytes(StandardCharsets.US_ASCII),
                "IMMUTABLE_EVIDENCE_PIN_DIVERGED");
            check(validButUnpairedV1Rejected,
                "new canonical unpaired V1 leaf changed SHA pins");
            healthy(r,original);
            healthyRetryAfterEachInjection=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            int[] observed={0};
            withObserver(r,original,()->{},
                (pathSelected,accountName,snapshot)->observed[0]++);
            check(observed[0]==2,
                "original opt-in observer count changed");
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21138_LATE_NAMESPACE_DIAGNOSTICS"+
                " genuineFourBoundWriterBaseline="+genuine+
                " unknownV1V2V3LateLeafVetos="+rejected+
                " canonicalExtraV1BeginRejected="+
                    validButUnpairedV1Rejected+
                " legacyTwoCaptureApiPreserved="+
                    originalLegacyApiPreserved+
                " immediateHealthyRetryAfterEach="+
                    healthyRetryAfterEachInjection+
                " optInVisitorCount="+observed[0]+
                " noLiveRewards="+noGrant+
                " zeroJvmLeases="+zeroLeases);
            check(genuine&&lateNamespaceRejected&&
                validButUnpairedV1Rejected&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEachInjection&&
                noGrant&&zeroLeases,
                "late evidence namespace containment");
            System.out.println("G21138_LATE_EVIDENCE_NAMESPACE_PASS"+
                " finalInLeaseV1V2V3NamespaceRechecked=true"+
                " canonicalUnpairedLateV1Rejected=true"+
                " originalArchiveWritersUnmodified=true"+
                " afterFinalScanWriterExclusion=false"+
                " signedProvenance=false atomicEpoch=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false"+
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

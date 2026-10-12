package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * G21.143 negative-control: a RAW same-byte new inode replacement
 * performed AFTER an OS lease ended can be INVISIBLE to fresh
 * pinned semantic/physical observations with no independently
 * authenticated historical inode pin. NEVER promote authority.
 */
public final class G21143MailboxPostLeaseSameBytesNewInodeIntegrationTest {
    private static final String ACCOUNT="g21143-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.143 "+message);
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
    private static Path evidence(Path account,String glob)
        throws IOException{
        try(DirectoryStream<Path> found=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+glob)){
            for(Path p:found)return p;
        }
        throw new IOException("G21.143 missing original "+glob);
    }
    private static Object physicalKey(Path file)throws IOException{
        BasicFileAttributes a=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&!a.isSymbolicLink()&&a.fileKey()!=null,
            "expected nofollow regular fileKey on source");
        return a.fileKey();
    }
    /**
     * Contrast G21.137: that swap occurred BETWEEN two captures under
     * one held lease and was vetoed. This swap occurs OUTSIDE every
     * lease, before new scans begin, so all hashes remain unchanged
     * and the newly introduced inode is stable across both captures.
     */
    private static boolean preScanInodeSwapBlindSpot(
        FilePlayerRepository.PathResolver r,Pins pinned,
        Path target,Path shadow
    )throws Exception{
        check(!Files.exists(shadow,LinkOption.NOFOLLOW_LINKS),
            "neutral shadow unexpectedly exists");
        check(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS),
            "expected original regular inode");
        byte[] initial=Files.readAllBytes(target);
        FileTime originalTime=Files.getLastModifiedTime(target);
        Object originalKey=physicalKey(target);
        MailboxSevenPlaneContinuousSemanticCohortV1.Report prior=
            continuous(r,pinned);
        check(prior.originalFourSemanticInspectorsInsideSameLease&&
            prior.physical.sevenPhysicalPinsMatched&&
            MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "original witness must finish and release OS lease");
        boolean changedKey=false;
        boolean sameBytes=false;
        boolean sameMtime=false;
        boolean freshSemanticsAccepted=false;
        boolean noAuthority=false;
        try{
            Files.move(target,shadow);
            Files.copy(shadow,target,
                StandardCopyOption.COPY_ATTRIBUTES);
            Files.setLastModifiedTime(target,originalTime);
            changedKey=!Objects.equals(originalKey,physicalKey(target));
            sameBytes=Arrays.equals(initial,Files.readAllBytes(target));
            sameMtime=originalTime.equals(
                Files.getLastModifiedTime(target));
            check(changedKey&&sameBytes&&sameMtime,
                "fixture must preserve bytes/mtime but change inode");
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
                afterPhysical=physical(r,pinned);
            MailboxSevenPlaneContinuousSemanticCohortV1.Report
                afterSemantic=continuous(r,pinned);
            freshSemanticsAccepted=
                afterPhysical.sevenPhysicalPinsMatched&&
                afterPhysical.threePhysicalIdentitiesStableAcrossCaptures&&
                afterPhysical.evidenceLeafMetadataStableAcrossCaptures&&
                afterSemantic.originalFourSemanticInspectorsInsideSameLease&&
                afterSemantic.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                afterSemantic.semanticHashesMatchV3Payloads;
            noAuthority=
                !prior.rawOrDefaultWriterExclusionProved&&
                !prior.independentPinAuthenticationProved&&
                !prior.atomicCrossFileEpochProved&&
                !prior.grantAuthorized&&
                !afterPhysical.rawWriterExcluded&&
                !afterPhysical.historicalAndFutureWritersExcluded&&
                !afterPhysical.independentPinAuthenticationProved&&
                !afterPhysical.atomicRecoveryEpochProved&&
                !afterPhysical.hardwarePowerLossProved&&
                !afterPhysical.cleanupAuthorized&&
                !afterPhysical.unlinkAuthorized&&
                !afterPhysical.pruningAuthorized&&
                !afterPhysical.rolloverAuthorized&&
                !afterPhysical.grantAuthorized&&
                !afterPhysical.replayAuthorized&&
                !afterPhysical.sessionAuthorized&&
                !afterPhysical.releaseAuthorized&&
                !afterPhysical.clientAckAuthorized&&
                !afterSemantic.externallyAuthenticatedWriterProvenance&&
                !afterSemantic.rawOrDefaultWriterExclusionProved&&
                !afterSemantic.historicalAndFutureWritersFenced&&
                !afterSemantic.independentPinAuthenticationProved&&
                !afterSemantic.atomicCrossFileEpochProved&&
                !afterSemantic.hardwarePowerLossProved&&
                !afterSemantic.cleanupAuthorized&&
                !afterSemantic.unlinkAuthorized&&
                !afterSemantic.pruningAuthorized&&
                !afterSemantic.rolloverAuthorized&&
                !afterSemantic.grantAuthorized&&
                !afterSemantic.replayAuthorized&&
                !afterSemantic.sessionAuthorized&&
                !afterSemantic.releaseAuthorized&&
                !afterSemantic.clientAckAuthorized;
            check(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
                "read-only scans must release OS lease");
        }finally{
            if(Files.exists(shadow,LinkOption.NOFOLLOW_LINKS)){
                Files.deleteIfExists(target);
                Files.move(shadow,target);
            }
            check(Arrays.equals(initial,Files.readAllBytes(target))&&
                originalTime.equals(Files.getLastModifiedTime(target))&&
                Objects.equals(originalKey,physicalKey(target)),
                "must restore exact original inode/mtime/bytes");
        }
        return changedKey&&sameBytes&&sameMtime&&
            freshSemanticsAccepted&&noAuthority;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21143-cohort-");
        boolean genuine=false,allSixUndetectableReplacements=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEverySwap=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.143 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21143_TEST_ONLY"));
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
            Path[] targets={
                account,
                new MailboxDurableIdempotencyIntentJournal(r)
                    .journalPath(ACCOUNT),
                new MailboxGuardedDiskCommitRecord(r)
                    .recordPath(ACCOUNT),
                evidence(account,".g21103-writer-attempt-v1-*.begin"),
                evidence(account,".g21114-writer-kind-v2-*.begin"),
                evidence(account,".g21120-commit-result-v3-*.result")
            };
            String[] names={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT","V1","V2","V3"
            };
            int invisibleCount=0;
            for(int i=0;i<targets.length;i++){
                Path backup=root.resolve(
                    "g21143-original-inode-shadow-"+i+".bak");
                check(preScanInodeSwapBlindSpot(
                    r,original,targets[i],backup),
                    "post-lease equal-byte replacement not measured "+
                    names[i]);
                invisibleCount++;
                // After recovery of ORIGINAL fileKey, prove both
                // observational readers work again without grants.
                healthy(r,original);
            }
            allSixUndetectableReplacements=invisibleCount==6;
            healthyRetryAfterEverySwap=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21143_POST_LEASE_INODE_BLIND_SPOT_DIAGNOSTICS"+
                " realFourBoundBaseline="+genuine+
                " equalByteEqualMtimeNewInodeAccepted="+invisibleCount+
                " originalExactInodesRestored=true"+
                " legacyApiPreserved="+originalLegacyApiPreserved+
                " healthyRetryAfterEveryRestore="+
                    healthyRetryAfterEverySwap+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixUndetectableReplacements&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEverySwap&&noGrant&&zeroLeases,
                "post-lease different inode observation blind spot");
            System.out.println("G21143_POST_LEASE_INODE_NEGATIVE_CONTROL_PASS"+
                " sixStableReplacedInodesAccepted=true"+
                " byteHashesAndMtimeUnchanged=true"+
                " historicalOriginalFileKeyNotPinned=true"+
                " thisIsNotTamperDetection=true"+
                " rawWriterExclusion=false signedProvenance=false"+
                " atomicEpoch=false rollover=false pruning=false"+
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

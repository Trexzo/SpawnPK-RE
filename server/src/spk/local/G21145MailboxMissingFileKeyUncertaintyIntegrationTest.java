package spk.local;

import java.io.IOException;
import java.lang.reflect.Field;
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
 * G21.145: cross-report historical-to-current NO-AUTHORITY
 * fileKey identity comparison catches same-byte new inodes that
 * individual fresh pinned seven-plane readers may accept.
 * Historical snapshots are untrusted, memory-only observations.
 */
public final class G21145MailboxMissingFileKeyUncertaintyIntegrationTest {
    private static final String ACCOUNT="g21145-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.145 "+message);
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
        throw new IOException("G21.145 missing original "+glob);
    }
    private static Object physicalKey(Path file)throws IOException{
        BasicFileAttributes a=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&!a.isSymbolicLink()&&a.fileKey()!=null,
            "expected nofollow regular fileKey on source");
        return a.fileKey();
    }
    /**
     * Two individually pinned valid observations can have
     * DIFFERENT file identities. Unlike G21.143's negative control,
     * explicitly RETAIN the historical report and compare it with
     * fresh separately leased physical snapshots in memory only.
     */
    private static boolean historicalDeltaDetects(
        FilePlayerRepository.PathResolver r,Pins pinned,
        Path target,Path shadow,String expectedLabel
    )throws Exception{
        check(!Files.exists(shadow,LinkOption.NOFOLLOW_LINKS),
            "neutral shadow must not exist");
        byte[] oldBytes=Files.readAllBytes(target);
        FileTime oldTime=Files.getLastModifiedTime(target);
        Object oldKey=physicalKey(target);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report prior=
            physical(r,pinned);
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report baseline=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                prior,physical(r,pinned));
        check(baseline.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
            baseline.changedSources.isEmpty(),
            "unchanged baseline cannot claim physical drift");
        check(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "previous witness must have released G21.100 lease");
        boolean distinct=false,byteIdentical=false,timeIdentical=false;
        boolean freshAccepted=false,historicalDeltaDetected=false;
        boolean authorityDenied=false;
        try{
            Files.move(target,shadow);
            Files.copy(shadow,target,StandardCopyOption.COPY_ATTRIBUTES);
            Files.setLastModifiedTime(target,oldTime);
            distinct=!Objects.equals(oldKey,physicalKey(target));
            byteIdentical=Arrays.equals(oldBytes,
                Files.readAllBytes(target));
            timeIdentical=oldTime.equals(
                Files.getLastModifiedTime(target));
            check(distinct&&byteIdentical&&timeIdentical,
                "fixture must provide new fileKey, same bytes/mtime");

            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report now=
                physical(r,pinned);
            MailboxSevenPlaneContinuousSemanticCohortV1.Report semantic=
                continuous(r,pinned);
            freshAccepted=now.sevenPhysicalPinsMatched&&
                semantic.originalFourSemanticInspectorsInsideSameLease&&
                semantic.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                semantic.semanticHashesMatchV3Payloads;

            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    prior,now);
            historicalDeltaDetected=
                delta.status==MailboxSevenPlaneHistoricalIdentityDeltaV1
                    .Status.CHANGED_OBSERVED_METADATA_NO_AUTHORITY&&
                delta.changedSources.size()==1&&
                delta.changedSources.contains(expectedLabel);
            authorityDenied=
                !delta.historicalReportAuthenticated&&
                !delta.independentPinAuthenticationProved&&
                !delta.rawOrDefaultWriterExclusionProved&&
                !delta.historicalAndFutureWritersFenced&&
                !delta.atomicCrossFileEpochProved&&
                !delta.hardwarePowerLossProved&&
                !delta.cleanupAuthorized&&!delta.unlinkAuthorized&&
                !delta.pruningAuthorized&&!delta.rolloverAuthorized&&
                !delta.grantAuthorized&&!delta.replayAuthorized&&
                !delta.sessionAuthorized&&!delta.releaseAuthorized&&
                !delta.clientAckAuthorized&&
                !semantic.externallyAuthenticatedWriterProvenance&&
                !semantic.rawOrDefaultWriterExclusionProved&&
                !semantic.atomicCrossFileEpochProved&&
                !semantic.grantAuthorized&&!semantic.cleanupAuthorized;
        }finally{
            if(Files.exists(shadow,LinkOption.NOFOLLOW_LINKS)){
                Files.deleteIfExists(target);
                Files.move(shadow,target);
            }
            check(Arrays.equals(oldBytes,Files.readAllBytes(target))&&
                oldTime.equals(Files.getLastModifiedTime(target))&&
                Objects.equals(oldKey,physicalKey(target)),
                "real original inode and attributes not restored");
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report recovered=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                prior,physical(r,pinned));
        check(recovered.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
            recovered.changedSources.isEmpty(),
            "restored original should match retained past report");
        return distinct&&byteIdentical&&timeIdentical&&freshAccepted&&
            historicalDeltaDetected&&authorityDenied;
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity selectIdentity(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation obs,
            int n,String label
        ){
        if(n==0)return obs.accountIdentity;
        if(n==1)return obs.journalIdentity;
        if(n==2)return obs.commitIdentity;
        return obs.evidenceLeafIdentities.get(label.substring(5));
    }

    /**
     * A missing provider fileKey cannot be constructed on the hosted
     * Linux inode filesystem, so use isolated TEST-ONLY reflection to
     * simulate the JDK BasicFileAttributes.fileKey()==null result
     * in one already-captured immutable read-only report.
     * No actual files are touched; all private final fields are
     * restored before the next comparison.
     */
    private static boolean nullKeyUncertainty(
        FilePlayerRepository.PathResolver r,Pins pinned,
        int plane,String label,int mode
    )throws Exception{
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
            physical(r,pinned);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
            physical(r,pinned);
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report initial=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(initial.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                initial.uncertainSources.isEmpty()&&
                initial.changedSources.isEmpty(),
            "real hosted NOFOLLOW keys should match before simulation");

        MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity[] ids={
                selectIdentity(historical.after,plane,label),
                selectIdentity(current.before,plane,label),
                selectIdentity(current.after,plane,label)
            };
        Field keyField=MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity.class.getDeclaredField("fileKey");
        keyField.setAccessible(true);
        Object[] originalKeys=new Object[ids.length];
        for(int i=0;i<ids.length;i++){
            check(ids[i]!=null,"missing physical identity sample");
            originalKeys[i]=keyField.get(ids[i]);
            check(originalKeys[i]!=null,
                "expected real fileKey before simulated provider absence");
        }
        int[] chosen=mode==0?new int[]{0}:
            mode==1?new int[]{1}:
            mode==2?new int[]{2}:new int[]{0,1,2};
        boolean uncertain=false,immutable=false,authorityDenied=false;
        try{
            for(int index:chosen){
                keyField.set(ids[index],null);
                check(keyField.get(ids[index])==null,
                    "synthetic missing physical key was not installed");
            }
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            uncertain=delta.status==
                    MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                        .UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY&&
                delta.changedSources.isEmpty()&&
                delta.uncertainSources.size()==1&&
                delta.uncertainSources.contains(label);
            try{
                delta.uncertainSources.add("FORGED");
            }catch(UnsupportedOperationException expected){
                immutable=true;
            }
            authorityDenied=!delta.historicalReportAuthenticated&&
                !delta.independentPinAuthenticationProved&&
                !delta.rawOrDefaultWriterExclusionProved&&
                !delta.historicalAndFutureWritersFenced&&
                !delta.atomicCrossFileEpochProved&&
                !delta.hardwarePowerLossProved&&
                !delta.cleanupAuthorized&&!delta.unlinkAuthorized&&
                !delta.pruningAuthorized&&!delta.rolloverAuthorized&&
                !delta.grantAuthorized&&!delta.replayAuthorized&&
                !delta.sessionAuthorized&&!delta.releaseAuthorized&&
                !delta.clientAckAuthorized;
        }finally{
            for(int i=0;i<ids.length;i++)
                keyField.set(ids[i],originalKeys[i]);
        }
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report restored=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                historical,current);
        check(restored.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                restored.changedSources.isEmpty()&&
                restored.uncertainSources.isEmpty(),
            "simulated provider null must be fully restored");
        return uncertain&&immutable&&authorityDenied;
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21145-cohort-");
        boolean genuine=false,allSixHistoricalIdentityDeltas=false;
        boolean allSixMissingFileKeysUncertain=false;
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
                id,"G21.145 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21145_TEST_ONLY"));
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
            String[] labels={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT",
                "LEAF:"+targets[3].getFileName(),
                "LEAF:"+targets[4].getFileName(),
                "LEAF:"+targets[5].getFileName()
            };
            int identityDeltas=0;
            for(int i=0;i<targets.length;i++){
                Path shadow=root.resolve(
                    "g21145-original-inode-shadow-"+i+".bak");
                check(historicalDeltaDetects(
                    r,original,targets[i],shadow,labels[i]),
                    "retained observation failed to detect identity "+
                        labels[i]);
                identityDeltas++;
                healthy(r,original);
            }
            allSixHistoricalIdentityDeltas=identityDeltas==6;
            // For EACH of six original physical categories simulate:
            // only historical.after missing, only current.before
            // missing, only current.after missing, and ALL missing.
            // It must never be a positive historical identity match.
            int simulatedUnknowns=0;
            for(int i=0;i<labels.length;i++){
                for(int mode=0;mode<4;mode++){
                    check(nullKeyUncertainty(
                        r,original,i,labels[i],mode),
                        "missing NOFOLLOW fileKey wrongly matched "+
                            labels[i]+" mode="+mode);
                    simulatedUnknowns++;
                }
            }
            allSixMissingFileKeysUncertain=simulatedUnknowns==24;
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
            System.out.println("G21145_MISSING_FILEKEY_UNCERTAINTY_DIAGNOSTICS"+
                " genuineFourBoundWorldBaseline="+genuine+
                " inheritedSixRealInodeDriftsDetected="+identityDeltas+
                " syntheticMissingFileKeyPlacements="+simulatedUnknowns+
                " originalInodesAndMetadataRestored=true"+
                " sameAccountHealthyRetries="+
                    healthyRetryAfterEverySwap+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixHistoricalIdentityDeltas&&
                allSixMissingFileKeysUncertain&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEverySwap&&noGrant&&zeroLeases,
                "G21.145 fileKey uncertainty contract");
            System.out.println("G21145_MISSING_FILEKEY_UNCERTAINTY_PASS"+
                " missingHistoricalOrCurrentKeysNeverMatched=true"+
                " accountJournalCommitV1V2V3Covered=true"+
                " 24MissingKeyCasesExplicitlyUncertain=true"+
                " inheritedSixKnownDifferentInodesStillChanged=true"+
                " immutableUncertainLabels=true"+
                " independentProvenance=false rawWriterFence=false"+
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

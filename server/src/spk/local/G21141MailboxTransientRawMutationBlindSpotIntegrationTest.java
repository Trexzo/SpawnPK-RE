package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
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
 * G21.141: NEGATIVE CONTROL, not a new tamper veto. Test-only
 * RAW in-place byte mutation fully restored between scans can be
 * invisible despite one advisory lifecycle OS lock; success
 * NEVER implies independent writer exclusion or grant authority.
 */
public final class G21141MailboxTransientRawMutationBlindSpotIntegrationTest {
    private static final String ACCOUNT="g21141-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.141 "+message);
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
    private static Path firstLeaf(Path account,String suffix)
        throws IOException{
        try(DirectoryStream<Path> files=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+suffix)){
            for(Path p:files)return p;
        }
        throw new IOException("G21.141 missing real leaf "+suffix);
    }
    private static Object nofollowKey(Path path)throws IOException{
        BasicFileAttributes attr=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(attr.isRegularFile()&&!attr.isSymbolicLink()&&
            attr.fileKey()!=null,"missing original regular file key");
        return attr.fileKey();
    }
    /**
     * This callback INTENTIONALLY demonstrates an observational blind
     * spot, it is not an actual writer/recovery implementation.
     * A RAW in-place mutation and full content+mtime restoration
     * is completed while G21.100 OS advisory lock is held, entirely
     * between first and second physical/semantic snapshots.
     */
    private static boolean transientBlindSpot(
        FilePlayerRepository.PathResolver r,Pins pins,Path target
    )throws Exception{
        check(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS),
            "baseline test source must be NOFOLLOW regular");
        byte[] original=Files.readAllBytes(target);
        check(original.length>0,"source file cannot be empty");
        java.nio.file.attribute.FileTime oldMtime=
            Files.getLastModifiedTime(target);
        Object originalKey=nofollowKey(target);
        final boolean[] mutatedObserved={false};
        final boolean[] originalRestored={false};
        final int[] callbackRuns={0};
        boolean accepted=false;
        try{
            MailboxSevenPlaneContinuousSemanticCohortV1.Report report=
                MailboxSevenPlaneContinuousSemanticCohortV1
                    .comparePinnedContinuousSemantic(
                        r,ACCOUNT,pins.v3.sha256,pins.v1.portable,
                        pins.v1.sha256,pins.account,pins.prepared,
                        pins.commit,pins.v2.sha256,LEASE_MS,()->{
                            callbackRuns[0]++;
                            byte[] changed=original.clone();
                            // Keep file size and inode unchanged.
                            changed[0]=(byte)(changed[0]^0x01);
                            check(!java.util.Arrays.equals(
                                original,changed),
                                "RAW bytes must genuinely diverge");
                            try{
                                Files.write(target,changed,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                                mutatedObserved[0]=java.util.Arrays.equals(
                                    changed,Files.readAllBytes(target))&&
                                    !java.util.Arrays.equals(
                                        original,Files.readAllBytes(target));
                            }finally{
                                Files.write(target,original,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                                Files.setLastModifiedTime(target,oldMtime);
                            }
                            originalRestored[0]=
                                java.util.Arrays.equals(original,
                                    Files.readAllBytes(target))&&
                                oldMtime.equals(
                                    Files.getLastModifiedTime(target))&&
                                java.util.Objects.equals(
                                    originalKey,nofollowKey(target));
                            check(mutatedObserved[0]&&originalRestored[0],
                                "must prove real mutation AND restoration");
                        });
            // NEGATIVE CONTROL: observational success is allowed.
            // All fields with authority/provenance claims MUST be false.
            accepted=report.originalFourSemanticInspectorsInsideSameLease&&
                report.strictUuidAndPayloadChecksOnCapturedSnapshots&&
                report.semanticHashesMatchV3Payloads&&
                !report.rawOrDefaultWriterExclusionProved&&
                !report.historicalAndFutureWritersFenced&&
                !report.independentPinAuthenticationProved&&
                !report.atomicCrossFileEpochProved&&
                !report.hardwarePowerLossProved&&
                !report.externallyAuthenticatedWriterProvenance&&
                !report.cleanupAuthorized&&!report.unlinkAuthorized&&
                !report.pruningAuthorized&&!report.rolloverAuthorized&&
                !report.grantAuthorized&&!report.replayAuthorized&&
                !report.sessionAuthorized&&!report.releaseAuthorized&&
                !report.clientAckAuthorized;
            check(report.physical.sevenPhysicalPinsMatched&&
                report.physical.threePhysicalIdentitiesStableAcrossCaptures&&
                report.physical.evidenceLeafMetadataStableAcrossCaptures&&
                !report.physical.rawWriterExcluded&&
                !report.physical.atomicRecoveryEpochProved,
                "physical flags cannot claim RAW or epoch exclusion");
        }finally{
            // This is an isolated test fixture, but always restore
            // even if an inside-lease reader were to fail unexpectedly.
            Files.write(target,original,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
            Files.setLastModifiedTime(target,oldMtime);
            check(java.util.Arrays.equals(original,
                    Files.readAllBytes(target))&&
                oldMtime.equals(Files.getLastModifiedTime(target))&&
                java.util.Objects.equals(originalKey,nofollowKey(target)),
                "exact original bytes/mtime/inode not restored");
        }
        return accepted&&mutatedObserved[0]&&originalRestored[0]&&
            callbackRuns[0]==1;
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21141-cohort-");
        boolean genuine=false,allSixTransientBlindSpotsMeasured=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEveryRestoration=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.141 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21141_TEST_ONLY"));
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
                firstLeaf(account,".g21103-writer-attempt-v1-*.begin"),
                firstLeaf(account,".g21114-writer-kind-v2-*.begin"),
                firstLeaf(account,".g21120-commit-result-v3-*.result")
            };
            String[] names={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT","V1","V2","V3"
            };
            int observedTransientPasses=0;
            for(int i=0;i<targets.length;i++){
                check(transientBlindSpot(r,original,targets[i]),
                    "restored RAW mutation negative-control failure "+
                        names[i]);
                observedTransientPasses++;
                healthy(r,original);
            }
            allSixTransientBlindSpotsMeasured=
                observedTransientPasses==6;
            healthyRetryAfterEveryRestoration=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21141_TRANSIENT_RAW_BLIND_SPOT_DIAGNOSTICS"+
                " genuineFourBoundWriterBaseline="+genuine+
                " experimentallyInvisibleTransients="+
                    observedTransientPasses+
                " originalInodesMtimesBytesRestored=true"+
                " legacyTwoCaptureApiPreserved="+
                    originalLegacyApiPreserved+
                " healthyRetryAfterEach="+
                    healthyRetryAfterEveryRestoration+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixTransientBlindSpotsMeasured&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEveryRestoration&&noGrant&&zeroLeases,
                "RAW transient negative-control contract");
            System.out.println("G21141_TRANSIENT_RAW_NEGATIVE_CONTROL_PASS"+
                " sixRawTransientMutationsObservedThenRestored=true"+
                " physicalSemanticWitnessCanAcceptRestoredState=true"+
                " rawWriterExclusionProved=false"+
                " completeWriteHistoryProved=false"+
                " independentPinsAuthenticated=false"+
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

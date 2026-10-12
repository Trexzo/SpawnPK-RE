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
 * G21.139: original six-plane source file disappearance AFTER the
 * second in-lease observer must fail closed in G21.136 final scan.
 * Restore same original inode, no grant/epoch.

 */
public final class G21139MailboxOriginalLeafDisappearanceIntegrationTest {
    private static final String ACCOUNT="g21139-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.139 "+message);
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
        throw new IOException("G21.139 missing real leaf "+suffix);
    }
    private static Object nofollowKey(Path path)throws IOException{
        BasicFileAttributes attr=Files.readAttributes(path,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(attr.isRegularFile()&&!attr.isSymbolicLink()&&
            attr.fileKey()!=null,"missing original regular file key");
        return attr.fileKey();
    }
    /**
     * Move original inode OUT of every watched namespace at the
     * second visitor, while SAME G21.100 advisory lease is held.
     * No deletion or mutation of real file data; test shadow is
     * always outside the captured account/attempt/result prefixes.
     */
    private static boolean missingOriginalDenied(
        FilePlayerRepository.PathResolver r,Pins pins,
        Path target,Path backup
    )throws Exception{
        check(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS),
            "expected original evidence source");
        check(!Files.exists(backup,LinkOption.NOFOLLOW_LINKS),
            "shadow path already exists");
        byte[] bytes=Files.readAllBytes(target);
        java.nio.file.attribute.FileTime originalMtime=
            Files.getLastModifiedTime(target);
        Object originalKey=nofollowKey(target);
        int[] visits={0},callbacks={0};
        boolean rejected=false;
        try{
            try{
                withObserver(r,pins,()->callbacks[0]++,
                    (observedPath,account,snapshot)->{
                        if(++visits[0]==2){
                            Files.move(target,backup);
                            check(!Files.exists(target,
                                LinkOption.NOFOLLOW_LINKS),
                                "original path must be absent");
                        }
                    });
            }catch(IOException failure){
                rejected=true;
            }
            check(visits[0]==2&&callbacks[0]==1,
                "late removal must happen after two snapshots");
            check(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&
                Files.exists(backup,LinkOption.NOFOLLOW_LINKS),
                "fixture must keep source absent until veto");
            return rejected;
        }finally{
            if(Files.exists(backup,LinkOption.NOFOLLOW_LINKS)){
                check(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),
                    "unexpected replacement at missing original path");
                Files.move(backup,target);
            }
            check(java.util.Arrays.equals(bytes,
                    Files.readAllBytes(target))&&
                java.util.Objects.equals(originalKey,
                    nofollowKey(target))&&
                originalMtime.equals(Files.getLastModifiedTime(target)),
                "restored original exact inode/bytes/mtime");
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21139-cohort-");
        boolean genuine=false,allSixMissingOriginalsRejected=false;
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
                id,"G21.139 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21139_TEST_ONLY"));
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
            int rejected=0;
            for(int i=0;i<targets.length;i++){
                Path neutral=root.resolve(
                    "g21139-original-shadow-"+i+".bak");
                check(missingOriginalDenied(
                    r,original,targets[i],neutral),
                    "missing original source accepted plane="+names[i]);
                rejected++;
                // The same originally captured inode is back; no
                // new hashes, repins or artifact writers are needed.
                healthy(r,original);
            }
            allSixMissingOriginalsRejected=rejected==6;
            healthyRetryAfterEveryRestoration=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            int[] visits={0};
            withObserver(r,original,()->{},
                (observedPath,accountName,sample)->visits[0]++);
            check(visits[0]==2,"opt-in visitor count changed");
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21139_LATE_ORIGINAL_ABSENCE_DIAGNOSTICS"+
                " genuineFourBoundWriterBaseline="+genuine+
                " accountJournalCommitV1V2V3MissingVetoes="+rejected+
                " exactOriginalInodesRestored=true"+
                " oldTwoCaptureSignatureStillPasses="+
                    originalLegacyApiPreserved+
                " healthyRetryAfterEveryRestore="+
                    healthyRetryAfterEveryRestoration+
                " optInObserverVisits="+visits[0]+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixMissingOriginalsRejected&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEveryRestoration&&noGrant&&zeroLeases,
                "late original source disappearance not contained");
            System.out.println("G21139_LATE_ORIGINAL_ABSENCE_PASS"+
                " sixOriginalAccountEvidencePlanesChecked=true"+
                " missingAfterSecondVisitorDenied=true"+
                " exactOriginalInodesRestored=true"+
                " futureRawWriterExclusion=false"+
                " independentProvenance=false atomicEpoch=false"+
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

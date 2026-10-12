package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.136: opt-in G21.134 physical observer tail-rescan must reject
 * observable post-second-observer drift on all six account/evidence
 * classes; old two-scan witness remains unchanged. No grant/epoch.
 */
public final class G21136MailboxPostObserverPhysicalRescanIntegrationTest {
    private static final String ACCOUNT="g21136-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.136 "+message);
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
        throw new IOException("G21.136 missing original "+glob);
    }
    /**
     * G21.136: uncooperative same-byte overwrite + observable
     * physical mtime drift AFTER final semantic observer, and BEFORE
     * its held-lease physical tail-rescan. Never touches live paths.
     */
    private static boolean tailVeto(
        FilePlayerRepository.PathResolver r,Pins pinned,
        Path target
    )throws Exception{
        byte[] bytes=Files.readAllBytes(target);
        FileTime old=Files.getLastModifiedTime(target);
        int[] seen={0};
        int[] between={0};
        boolean vetoed=false;
        try{
            try{
                withObserver(r,pinned,()->between[0]++,
                    (selected,name,sample)->{
                        if(++seen[0]==2){
                            Files.write(target,bytes);
                            Files.setLastModifiedTime(target,
                                FileTime.fromMillis(
                                    old.toMillis()+180000L));
                        }
                    });
            }catch(IOException expected){
                vetoed=String.valueOf(expected.getMessage()).contains(
                    "AFTER_FINAL_OBSERVER_METADATA_CHANGED");
            }
            check(seen[0]==2&&between[0]==1,
                "both observer visits/callback not executed");
            return vetoed;
        }finally{
            Files.write(target,bytes);
            Files.setLastModifiedTime(target,old);
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21136-cohort-");
        boolean genuine=false,allSixPhysicalTailVetos=false;
        boolean originalLegacyApiPreserved=false;
        boolean healthyRetryAfterEveryMutation=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.136 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21136_TEST_ONLY"));
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
            String[] plane={
                "ACCOUNT","PREPARED_JOURNAL","COMMIT","V1","V2","V3"
            };
            int vetoCount=0;
            for(int i=0;i<targets.length;i++){
                check(tailVeto(r,original,targets[i]),
                    "second observer drift was not vetoed plane="+plane[i]);
                vetoCount++;
                // Same-account lease must be reusable immediately;
                // stored hashes/metadata were restored in finally.
                healthy(r,original);
            }
            allSixPhysicalTailVetos=vetoCount==6;
            healthyRetryAfterEveryMutation=true;
            originalLegacyApiPreserved=
                physical(r,original).sevenPhysicalPinsMatched;
            int[] visited={0};
            withObserver(r,original,()->{},
                (file,who,sample)->visited[0]++);
            check(visited[0]==2,
                "opt-in two semantic callbacks changed count");
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21136_POST_OBSERVER_TAIL_DIAGNOSTICS"+
                " genuineFourBoundWorldAccount="+genuine+
                " accountJournalCommitV1V2V3MtimeVetos="+vetoCount+
                " legacyTwoCaptureApiPreserved="+
                    originalLegacyApiPreserved+
                " healthyRetryAfterEveryPlane="+
                    healthyRetryAfterEveryMutation+
                " optInObserverVisits="+visited[0]+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&allSixPhysicalTailVetos&&
                originalLegacyApiPreserved&&
                healthyRetryAfterEveryMutation&&noGrant&&zeroLeases,
                "post observer tail regression");
            System.out.println("G21136_POST_OBSERVER_TAIL_RESCAN_PASS"+
                " sixPhysicalPlanesRecheckedWithinHeldLease=true"+
                " sameBytesObservableMetadataDriftDenied=true"+
                " legacyG21129TwoCaptureSignaturePreserved=true"+
                " rawWriterExcluded=false timestampRestorationExcluded=false"+
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

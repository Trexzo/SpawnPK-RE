package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.135: seven physical planes can be fully repinned to include a
 * synthetic v3 UUID rename; only the separately opt-in structural
 * v1/v2/v3 UUID cohort must reject that untrusted observation.
 */
public final class G21135MailboxInsideLeaseFailureContainmentIntegrationTest {
    private static final String ACCOUNT="g21135-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.135 "+message);
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
    private static Path leaf(Path account,String namespace)
        throws IOException{
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+namespace+"*.result")){
            for(Path file:entries)return file;
        }
        throw new IOException("G21.135 missing result leaf "+namespace);
    }
    private static String field(String content,String key)
        throws IOException{
        String marker=key+"=";
        int start=content.indexOf(marker);
        if(start<0||content.indexOf(marker,start+1)>=0)
            throw new IOException("G21.135 field count "+key);
        int end=content.indexOf('\n',start);
        if(end<0)throw new IOException("G21.135 field EOL "+key);
        return content.substring(start+marker.length(),end);
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21135-cohort-");
        boolean genuine=false,firstFailure=false,secondFailure=false;
        boolean uncheckedFailure=false,betweenFailure=false;
        boolean healthyRetryAfterEach=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.135 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21135_TEST_ONLY"));
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

            int[] seen={0};
            int[] callbacks={0};
            try{
                withObserver(r,original,()->{callbacks[0]++;},
                    (file,who,sample)->{
                        seen[0]++;
                        throw new IOException("G21135_EXPECTED_FIRST_OBSERVER");
                    });
            }catch(IOException expected){
                firstFailure="G21135_EXPECTED_FIRST_OBSERVER"
                    .equals(expected.getMessage())&&
                    seen[0]==1&&callbacks[0]==0;
            }
            check(firstFailure,"first observer did not short-circuit");
            healthy(r,original);

            seen[0]=0;callbacks[0]=0;
            try{
                withObserver(r,original,()->{callbacks[0]++;},
                    (file,who,sample)->{
                        if(++seen[0]==2)
                            throw new IOException(
                                "G21135_EXPECTED_SECOND_OBSERVER");
                    });
            }catch(IOException expected){
                secondFailure="G21135_EXPECTED_SECOND_OBSERVER"
                    .equals(expected.getMessage())&&
                    seen[0]==2&&callbacks[0]==1;
            }
            check(secondFailure,"second observer did not fail closed");
            healthy(r,original);

            seen[0]=0;callbacks[0]=0;
            try{
                withObserver(r,original,()->{callbacks[0]++;},
                    (file,who,sample)->{
                        seen[0]++;
                        throw new IllegalStateException(
                            "G21135_EXPECTED_UNCHECKED_OBSERVER");
                    });
            }catch(IllegalStateException expected){
                uncheckedFailure="G21135_EXPECTED_UNCHECKED_OBSERVER"
                    .equals(expected.getMessage())&&
                    seen[0]==1&&callbacks[0]==0;
            }
            check(uncheckedFailure,
                "unchecked observer leaked success or callback");
            healthy(r,original);

            seen[0]=0;callbacks[0]=0;
            try{
                withObserver(r,original,()->{
                    callbacks[0]++;
                    throw new IOException("G21135_EXPECTED_BETWEEN_FAILURE");
                },(file,who,sample)->seen[0]++);
            }catch(IOException expected){
                betweenFailure="G21135_EXPECTED_BETWEEN_FAILURE"
                    .equals(expected.getMessage())&&
                    seen[0]==1&&callbacks[0]==1;
            }
            check(betweenFailure,
                "between-capture exception unexpectedly published success");
            healthy(r,original);
            healthyRetryAfterEach=true;

            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21135_INSIDE_LEASE_FAILURE_DIAGNOSTICS"+
                " genuineSemanticBaseline="+genuine+
                " firstObserverVetoBeforeCallback="+firstFailure+
                " secondObserverVetoAfterOneCallback="+secondFailure+
                " uncheckedObserverContained="+uncheckedFailure+
                " callbackErrorNoSecondObserver="+betweenFailure+
                " healthyRetryAfterEveryException="+
                    healthyRetryAfterEach+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&firstFailure&&secondFailure&&
                uncheckedFailure&&betweenFailure&&healthyRetryAfterEach&&
                noGrant&&zeroLeases,"inside lease failure containment");
            System.out.println("G21135_INSIDE_LEASE_FAILURE_CONTAINMENT_PASS"+
                " observerFailuresFailClosed=true"+
                " singleLeaseReleasedAfterAllFailures=true"+
                " healthyRetryRestored=true"+
                " rawOrDefaultWriterExclusion=false"+
                " authenticatedProvenance=false atomicEpoch=false"+
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

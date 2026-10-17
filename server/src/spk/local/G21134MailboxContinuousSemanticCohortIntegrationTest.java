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
 * G21.134: seven physical planes can be fully repinned to include a
 * synthetic v3 UUID rename; only the separately opt-in structural
 * v1/v2/v3 UUID cohort must reject that untrusted observation.
 */
public final class G21134MailboxContinuousSemanticCohortIntegrationTest {
    private static final String ACCOUNT="g21134-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.134 "+message);
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
    private static Path leaf(Path account,String namespace)
        throws IOException{
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+namespace+"*.result")){
            for(Path file:entries)return file;
        }
        throw new IOException("G21.134 missing result leaf "+namespace);
    }
    private static String field(String content,String key)
        throws IOException{
        String marker=key+"=";
        int start=content.indexOf(marker);
        if(start<0||content.indexOf(marker,start+1)>=0)
            throw new IOException("G21.134 field count "+key);
        int end=content.indexOf('\n',start);
        if(end<0)throw new IOException("G21.134 field EOL "+key);
        return content.substring(start+marker.length(),end);
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21134-cohort-");
        boolean genuine=false,threeForgedTerminalHashesConsistent=false;
        boolean originalPayloadVerifierAcceptedForged=false;
        boolean heldLeaseSemanticInspectorRejected=false;
        boolean restoredPass=false,noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.134 semantic cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21134_TEST_ONLY"));
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

            Path canonical=r.resolve(ACCOUNT);
            String[] namespace={
                ".g21120-commit-result-v3-",
                ".g21121-prepared-result-v3-",
                ".g21123-world-terminal-result-v3-"
            };
            Path[] targets=new Path[namespace.length];
            byte[][] leafBackups=new byte[namespace.length][];
            String zero="00000000000000000000000000000000"+
                "00000000000000000000000000000000";
            try{
                for(int i=0;i<namespace.length;i++){
                    targets[i]=leaf(canonical,namespace[i]);
                    leafBackups[i]=Files.readAllBytes(targets[i]);
                    String body=new String(leafBackups[i],
                        StandardCharsets.US_ASCII);
                    String trueTerminal=field(body,"TERMINAL_SHA256");
                    check(!zero.equals(trueTerminal),
                        "forged hash must differ from original");
                    String modified=body.replace(
                        "TERMINAL_SHA256="+trueTerminal+"\n",
                        "TERMINAL_SHA256="+zero+"\n");
                    check(!modified.equals(body),
                        "must change terminal record bytes");
                    Files.write(targets[i],
                        modified.getBytes(StandardCharsets.US_ASCII));
                }
                Pins repinned=new Pins(r);
                threeForgedTerminalHashesConsistent=
                    !repinned.v3.sha256.equals(original.v3.sha256);
                negative(strict(r,repinned));
                // G21.133 checks cross-payload consistency, but does
                // NOT verify canonical hashes against the real
                // G21.120-123 business semantic readers.
                originalPayloadVerifierAcceptedForged=
                    payload(r,repinned).canonicalFourV3PayloadsObserved;
                try{
                    continuous(r,repinned);
                }catch(IOException expected){
                    // Original semantic inspector rejects before
                    // authorizing any live settlement operation.
                    heldLeaseSemanticInspectorRejected=
                        String.valueOf(expected.getMessage())
                            .contains("G21.12");
                }
            }finally{
                for(int i=0;i<targets.length;i++)
                    if(targets[i]!=null&&leafBackups[i]!=null)
                        Files.write(targets[i],leafBackups[i]);
            }
            continuous(r,new Pins(r));
            restoredPass=true;
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21134_CONTINUOUS_SEMANTIC_DIAGNOSTICS"+
                " genuineFourRealSemanticsInsideSingleLease="+genuine+
                " tripleForgedTerminalRecordsIndependentlyRepinned="+
                    threeForgedTerminalHashesConsistent+
                " previousPayloadGateAcceptedConsistentForgery="+
                    originalPayloadVerifierAcceptedForged+
                " insideLeaseRealSemanticInspectorDeniedForgery="+
                    heldLeaseSemanticInspectorRejected+
                " restoredRealSemanticsPass="+restoredPass+
                " noLiveRewards="+noGrant+" zeroJvmLeases="+zeroLeases);
            check(genuine&&threeForgedTerminalHashesConsistent&&
                originalPayloadVerifierAcceptedForged&&
                heldLeaseSemanticInspectorRejected&&restoredPass&&
                noGrant&&zeroLeases,"continuous semantic gate");
            System.out.println("G21134_CONTINUOUS_SEMANTIC_COHORT_PASS"+
                " fourRealReadersInsideSameG21100Lease=true"+
                " exactSamePhysicalSamplesUuidPayloadChecked=true"+
                " forgedV3CanonicalTerminalDenied=true"+
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

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
 * G21.133: seven physical planes can be fully repinned to include a
 * synthetic v3 UUID rename; only the separately opt-in structural
 * v1/v2/v3 UUID cohort must reject that untrusted observation.
 */
public final class G21133MailboxV3PayloadBindingIntegrationTest {
    private static final String ACCOUNT="g21133-cohort";
    private static final long LEASE_MS=300L;
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.133 "+message);
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
    private static Path leaf(Path account,String namespace)
        throws IOException{
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(
                account.getParent(),
                account.getFileName().toString()+namespace+"*.result")){
            for(Path file:entries)return file;
        }
        throw new IOException("G21.133 missing result leaf "+namespace);
    }
    private static String field(String content,String key)
        throws IOException{
        String marker=key+"=";
        int start=content.indexOf(marker);
        if(start<0||content.indexOf(marker,start+1)>=0)
            throw new IOException("G21.133 field count "+key);
        int end=content.indexOf('\n',start);
        if(end<0)throw new IOException("G21.133 field EOL "+key);
        return content.substring(start+marker.length(),end);
    }
    private static boolean tamper(
        FilePlayerRepository.PathResolver r,Path canonical,
        String namespace,String key,String newValue
    )throws Exception{
        Path target=leaf(canonical,namespace);
        byte[] saved=Files.readAllBytes(target);
        String raw=new String(saved,StandardCharsets.US_ASCII);
        String existing=field(raw,key);
        if(existing.equals(newValue))
            throw new IOException("G21.133 identical test mutation");
        String mutated=raw.replace(key+"="+existing+"\n",
            key+"="+newValue+"\n");
        if(mutated.equals(raw))
            throw new IOException("G21.133 no test mutation");
        try{
            Files.write(target,mutated.getBytes(StandardCharsets.US_ASCII));
            Pins forged=new Pins(r);
            // G21.132 checks UUIDs in the leaf NAMES only;
            // independently repinned forged contents are accepted.
            negative(strict(r,forged));
            try{
                payload(r,forged);
                return false;
            }catch(IOException expected){
                return String.valueOf(expected.getMessage()).contains(
                    "G21.133 V3_PAYLOAD_COHORT_UNSAFE_NO_GRANT");
            }
        }finally{
            Files.write(target,saved);
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21133-cohort-");
        boolean genuine=false,wrongUuid=false,wrongKind=false;
        boolean wrongCommitSha=false,wrongPreparedSha=false;
        boolean wrongTerminalAccountSha=false,wrongVersion=false;
        boolean restoredPass=false,noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String id=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                id,"G21.133 uuid cohort","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21133_TEST_ONLY"));
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
            genuine=true;

            Path canonical=r.resolve(ACCOUNT);
            check(payload(r,original).canonicalFourV3PayloadsObserved,
                "original payload cohort should pass");
            final String c=".g21120-commit-result-v3-";
            final String j=".g21121-prepared-result-v3-";
            final String s=".g21122-strict-prepared-result-v3-";
            final String w=".g21123-world-terminal-result-v3-";
            wrongUuid=tamper(r,canonical,c,"ATTEMPT",
                "11111111-2222-4333-8444-555555555555");
            wrongKind=tamper(r,canonical,c,"KIND","WORLD_TERMINAL");
            String zero=
                "00000000000000000000000000000000"+
                "00000000000000000000000000000000";
            wrongCommitSha=tamper(
                r,canonical,c,"COMMIT_BYTES_SHA256",zero);
            wrongPreparedSha=tamper(
                r,canonical,j,"PREPARED_SHA256",zero);
            wrongTerminalAccountSha=tamper(
                r,canonical,w,"TERMINAL_FILE_BYTES_SHA256",zero);
            wrongVersion=tamper(
                r,canonical,s,"SNAPSHOT_VERSION","999");
            payload(r,new Pins(r));
            restoredPass=true;
            noGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(id).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21133_V3_PAYLOAD_DIAGNOSTICS"+
                " fourRealBoundPayloads="+genuine+
                " wrongPayloadUuidDenied="+wrongUuid+
                " wrongPayloadKindDenied="+wrongKind+
                " wrongCommitShaDenied="+wrongCommitSha+
                " wrongPreparedShaDenied="+wrongPreparedSha+
                " wrongTerminalAccountShaDenied="+wrongTerminalAccountSha+
                " wrongVersionDenied="+wrongVersion+
                " originalRestored="+restoredPass+
                " noLiveRewards="+noGrant+
                " zeroJvmLeases="+zeroLeases);
            check(genuine&&wrongUuid&&wrongKind&&wrongCommitSha&&
                wrongPreparedSha&&wrongTerminalAccountSha&&
                wrongVersion&&restoredPass&&noGrant&&zeroLeases,
                "canonical payload+hash cohort acceptance");
            System.out.println("G21133_V3_PAYLOAD_COHORT_PASS"+
                " fourResultPayloadFormatsValidated=true"+
                " accountJournalCommitHashesMatched=true"+
                " strictPreparedHistoricalRawNotCurrentAccount=true"+
                " signedSourceProvenance=false atomicEpoch=false"+
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

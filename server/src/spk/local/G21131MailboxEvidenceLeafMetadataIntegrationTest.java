package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.131: immutable v1/v2/v3 evidence-leaf hashes can still
 * conceal a raw same-byte rewrite. Negative-only metadata veto,
 * without claiming a raw-writer fence or atomic recovery epoch.
 */
public final class G21131MailboxEvidenceLeafMetadataIntegrationTest {
    private static final String ACCOUNT="g21131-identity";
    private static final long LEASE_MS=250L;
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.131 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return who->dir.resolve(who+".properties");
    }
    private static String sha(Path path)throws Exception{
        byte[] value=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));
        StringBuilder text=new StringBuilder(64);
        for(byte b:value)
            text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String account,prepared,commit;
        final Path[] physical;
        private static Path leaf(Path account,String glob)
            throws IOException{
            try(java.nio.file.DirectoryStream<Path> leaves=
                Files.newDirectoryStream(account.getParent(),
                    account.getFileName().toString()+glob)){
                for(Path candidate:leaves)
                    return candidate;
            }
            throw new IOException("G21.131 missing representative "+glob);
        }
        Pins(FilePlayerRepository.PathResolver r)throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,ACCOUNT);
            v2=MailboxWriterKindEvidenceV2.capture(r,ACCOUNT);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,ACCOUNT);
            Path canonical=r.resolve(ACCOUNT);
            account=sha(canonical);
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(ACCOUNT));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(ACCOUNT));
            physical=new Path[]{
                leaf(canonical,".g21103-writer-attempt-v1-*"),
                leaf(canonical,".g21114-writer-kind-v2-*"),
                leaf(canonical,".g2112*")
            };
        }
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        compare(FilePlayerRepository.PathResolver r,Pins p,
            MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback)
        throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,
                LEASE_MS,callback);
    }
    private static boolean denied(
        FilePlayerRepository.PathResolver r,Pins p,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback,
        String marker){
        try{compare(r,p,callback);return false;}
        catch(IOException e){
            return String.valueOf(e.getMessage()).contains(marker);
        }
    }
    private static void assertNegative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report result){
        check(result.sevenPhysicalPinsMatched&&
            result.threePhysicalIdentitiesStableAcrossCaptures&&
            result.evidenceLeafMetadataStableAcrossCaptures&&
            result.cooperatingCrossJvmWritesExcludedDuringLease&&
            !result.rawWriterExcluded&&
            !result.richFourWriterSemanticCohortProved&&
            !result.independentPinAuthenticationProved&&
            !result.historicalAndFutureWritersExcluded&&
            !result.atomicRecoveryEpochProved&&
            !result.hardwarePowerLossProved&&
            !result.cleanupAuthorized&&!result.unlinkAuthorized&&
            !result.pruningAuthorized&&!result.rolloverAuthorized&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.sessionAuthorized&&!result.releaseAuthorized&&
            !result.clientAckAuthorized,
            "metadata witness promoted to authority");
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21131-physical-");
        boolean base=false,v1Leaf=false,v2Leaf=false,v3Leaf=false;
        boolean restoredMtimeGap=false,noLiveGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String messageId=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"G21.131 evidence metadata","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21131_TEST_ONLY"));
            MailboxRewardDeliveryService.Snapshot selected=
                player.mailbox().get(messageId);
            MailboxPreparedClaimJournal.stageOnly(player,
                MailboxPreparedClaimJournal.prepare(player,selected));
            MailboxSettlementPostimagePlanner.Proposal proposal=
                MailboxSettlementPostimagePlanner.plan(
                    player,generation,selected);
            PlayerSnapshot terminal=MailboxAtomicTerminalSnapshot
                .compose(proposal);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal prepared=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);
            strict.saveStrictWithResultBinding(proposal.preparedPreimage);
            prepared.publishPreparedIntentWithResultBinding(proposal);
            WorldPlayerPersistence.PreparedAccountReservation reservation=
                world.persistence().reservePreparedAccount(
                    player,generation,proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence saved=
                world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        reservation,proposal,terminal,strict)
                    .get(15L,TimeUnit.SECONDS);
            check(saved.strictReceipt.matchesSnapshot(terminal),
                "terminal Receipt");
            disk.recordConfirmedDiskTerminalWithResultBinding(
                proposal,saved.strictReceipt);
            Pins pins=new Pins(r);
            assertNegative(compare(r,pins,()->{}));
            base=true;

            boolean[] refusals=new boolean[3];
            for(int i=0;i<pins.physical.length;i++){
                Path file=pins.physical[i];
                byte[] original=Files.readAllBytes(file);
                String originalSha=sha(file);
                FileTime timestamp=Files.getLastModifiedTime(
                    file,LinkOption.NOFOLLOW_LINKS);
                try{
                    refusals[i]=denied(r,pins,()->{
                        // RAW bypasses the advisory lifecycle lease.
                        // Identical bytes keep all seven digest pins
                        // matching; only the observed mtime is changed.
                        Files.write(file,original);
                        Files.setLastModifiedTime(file,
                            FileTime.fromMillis(
                                timestamp.toMillis()+120000L));
                    },"BETWEEN_CAPTURE_EVIDENCE_LEAF_METADATA_CHANGED");
                }finally{
                    Files.write(file,original);
                    Files.setLastModifiedTime(file,timestamp);
                }
                check(sha(file).equals(originalSha),
                    "original leaf content restored");
                assertNegative(compare(r,pins,()->{}));
            }
            v1Leaf=refusals[0];
            v2Leaf=refusals[1];
            v3Leaf=refusals[2];

            Path file=pins.physical[0];
            byte[] content=Files.readAllBytes(file);
            FileTime originalTime=Files.getLastModifiedTime(
                file,LinkOption.NOFOLLOW_LINKS);
            // Explicit counterexample: a raw writer restores the
            // exact original timestamp and original bytes in-place.
            // The metadata witness is NOT a complete writer census.
            assertNegative(compare(r,pins,()->{
                Files.write(file,content);
                Files.setLastModifiedTime(file,originalTime);
            }));
            restoredMtimeGap=true;

            noLiveGrant=player.bank().inventorySlots()==0&&
                player.mailbox().get(messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21131_PHYSICAL_METADATA_DIAGNOSTICS"+
                " genuineFourBoundWriters="+base+
                " identicalBytesV1LeafMtimeVeto="+v1Leaf+
                " identicalBytesV2LeafMtimeVeto="+v2Leaf+
                " identicalBytesV3LeafMtimeVeto="+v3Leaf+
                " restoredTimestampBypassAcknowledged="+restoredMtimeGap+
                " existingSevenPlanePinsUnchanged=true"+
                " noLiveRewards="+noLiveGrant+
                " zeroJvmLeases="+zeroLeases);
            check(base&&v1Leaf&&v2Leaf&&v3Leaf&&
                restoredMtimeGap&&noLiveGrant&&zeroLeases,
                "physical metadata negative regression");
            System.out.println("G21131_PHYSICAL_METADATA_PASS"+
                " evidenceLeafFileKeyMtimeChecked=true"+
                " originalContinuousOsLeasePreserved=true"+
                " rawWriterCensus=false epoch=false"+
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

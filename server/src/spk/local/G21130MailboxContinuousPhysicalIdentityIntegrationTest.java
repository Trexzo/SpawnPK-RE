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
 * G21.130: a matching content SHA-256 can still conceal an observed
 * raw same-byte rewrite. Negative-only file identity/mtime veto,
 * without claiming a raw-writer fence or atomic recovery epoch.
 */
public final class G21130MailboxContinuousPhysicalIdentityIntegrationTest {
    private static final String ACCOUNT="g21130-identity";
    private static final long LEASE_MS=250L;
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.130 "+reason);
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
        Pins(FilePlayerRepository.PathResolver r)throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,ACCOUNT);
            v2=MailboxWriterKindEvidenceV2.capture(r,ACCOUNT);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,ACCOUNT);
            physical=new Path[]{
                r.resolve(ACCOUNT),
                new MailboxDurableIdempotencyIntentJournal(r)
                    .journalPath(ACCOUNT),
                new MailboxGuardedDiskCommitRecord(r)
                    .recordPath(ACCOUNT)
            };
            account=sha(physical[0]);
            prepared=sha(physical[1]);
            commit=sha(physical[2]);
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
        Path root=Files.createTempDirectory("g21130-physical-");
        boolean base=false,account=false,journal=false,commit=false;
        boolean restoredMtimeGap=false,noLiveGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(60000L,
                new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            WorldPlayer player=new WorldPlayer();
            long generation=world.registerPlayer(player,ACCOUNT);
            String messageId=ACCOUNT+":gift";
            player.mailbox().deliver(new RewardDeliveryMessage(
                messageId,"G21.130 physical metadata","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G21130_TEST_ONLY"));
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
                    },"BETWEEN_CAPTURE_FILE_METADATA_CHANGED");
                }finally{
                    Files.write(file,original);
                    Files.setLastModifiedTime(file,timestamp);
                }
                check(sha(file).equals(
                    i==0?pins.account:i==1?pins.prepared:pins.commit),
                    "original content restored");
                assertNegative(compare(r,pins,()->{}));
            }
            account=refusals[0];
            journal=refusals[1];
            commit=refusals[2];

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
            System.out.println("G21130_PHYSICAL_METADATA_DIAGNOSTICS"+
                " genuineFourBoundWriters="+base+
                " identicalBytesAccountMtimeVeto="+account+
                " identicalBytesPreparedMtimeVeto="+journal+
                " identicalBytesCommitMtimeVeto="+commit+
                " restoredTimestampBypassAcknowledged="+restoredMtimeGap+
                " existingSevenPlanePinsUnchanged=true"+
                " noLiveRewards="+noLiveGrant+
                " zeroJvmLeases="+zeroLeases);
            check(base&&account&&journal&&commit&&
                restoredMtimeGap&&noLiveGrant&&zeroLeases,
                "physical metadata negative regression");
            System.out.println("G21130_PHYSICAL_METADATA_PASS"+
                " crossCaptureFileKeyMtimeChecked=true"+
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

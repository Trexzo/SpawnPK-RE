package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.125: read-only separately pinned four-v3-result inventory,
 * plus existing G21.124 exact four writer cohort. All pins are
 * test-fixture supplied: they are NOT independently authenticated.
 */
public final class G21125MailboxPinnedFourV3ResultLeavesIntegrationTest {
    private static final String FOUR="g21125-four";
    private static final String OLD="g21125-old";

    private static void check(boolean x,String message){
        if(!x)throw new AssertionError("G21.125 "+message);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed{
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long g,MailboxSettlementPostimagePlanner.Proposal q){
            player=p;generation=g;proposal=q;
            terminal=MailboxAtomicTerminalSnapshot.compose(q);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer p=new WorldPlayer();
        long generation=world.registerPlayer(p,account);
        String id=account+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.125 result leaf witness","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21125_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot m=p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,m));
        return new Seed(p,generation,
            MailboxSettlementPostimagePlanner.plan(p,generation,m));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path file)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder out=new StringBuilder(64);
        for(byte b:digest)
            out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String v2,account,journal,commit;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot results;
        Pins(FilePlayerRepository.PathResolver r,String a)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,a);
            v2=MailboxWriterKindEvidenceV2.capture(r,a).sha256;
            account=sha(r.resolve(a));
            journal=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(a));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(a));
            results=MailboxFourBoundResultLeavesArchiveV1.capture(r,a);
        }
    }
    private static MailboxFourBoundResultLeavesArchiveV1.Comparison
        audit(FilePlayerRepository.PathResolver r,String account,
        Pins pins,String boundHash,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        return MailboxFourBoundResultLeavesArchiveV1
            .comparePinnedFourResultLeaves(
                r,account,boundHash,pins.v1.portable,
                pins.v1.sha256,pins.account,pins.journal,
                pins.commit,pins.v2,between);
    }
    @FunctionalInterface private interface Attempt{
        void run()throws Exception;
    }
    private static boolean denied(Attempt a,String marker){
        try{a.run();return false;}
        catch(Exception error){
            for(Throwable t=error;t!=null;t=t.getCause())
                if(String.valueOf(t.getMessage()).contains(marker))
                    return true;
            return false;
        }
    }
    private static Path fourTerminalLeaf(Path root)throws IOException{
        try(Stream<Path> listed=Files.list(root)){
            return listed.filter(p->p.getFileName().toString()
                .startsWith(FOUR+
                    ".properties.g21123-world-terminal-result-v3-"))
                .findFirst().orElseThrow(()->new IOException(
                    "G21.125 no terminal v3 fixture"));
        }
    }
    private static void completed(
        World world,Seed seed,
        StrictDurablePlayerSnapshotWriter strict,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord commit,
        boolean bindWorld
    )throws Exception{
        strict.saveStrictWithResultBinding(
            seed.proposal.preparedPreimage);
        journal.publishPreparedIntentWithResultBinding(
            seed.proposal);
        WorldPlayerPersistence.PreparedAccountReservation reservation=
            world.persistence().reservePreparedAccount(
                seed.player,seed.generation,seed.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence result=
            (bindWorld
                ?world.persistence()
                    .publishReservedTerminalStrictlyWithResultBinding(
                        reservation,seed.proposal,seed.terminal,strict)
                :world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        reservation,seed.proposal,seed.terminal,strict))
                .get(15L,TimeUnit.SECONDS);
        check(result.strictReceipt.matchesSnapshot(seed.terminal),
            "invalid real strict receipt");
        commit.recordConfirmedDiskTerminalWithResultBinding(
            seed.proposal,result.strictReceipt);
    }
    private static void negative(
        MailboxFourBoundResultLeavesArchiveV1.Snapshot a
    ){
        check(a.resultLeaves==4&&
            !a.independentProvenanceAuthenticated&&
            !a.historicalWriterCensusProved&&
            !a.rawAndDefaultWritersExcluded&&
            !a.futureWriterFenceProved&&
            !a.atomicMultiFileEpochProved&&
            !a.hardwarePowerLossProved&&
            !a.archiveRolloverAuthorized&&
            !a.recordPruningAuthorized&&
            !a.cleanupAuthorized&&!a.unlinkAuthorized&&
            !a.grantAuthorized&&!a.replayAuthorized&&
            !a.sessionAuthorized&&!a.releaseAuthorized&&
            !a.clientAckAuthorized,
            "snapshot promoted into positive authority");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21125-result-inventory-");
        boolean fourClean=false,wrongPinDenied=false;
        boolean malformedPinDenied=false,missingLeafDenied=false;
        boolean extraLeafDenied=false,unknownVersionDenied=false;
        boolean changedPayloadDenied=false,interpassTamperDenied=false;
        boolean legacyMissingDenied=false,originalOldApiPass=false;
        boolean v1PortableIntact=false,noGrants=false,zeroLeases=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);
            Seed full=seed(world,FOUR);
            completed(world,full,strict,journal,commit,true);
            Pins pins=new Pins(r,FOUR);
            negative(pins.results);
            MailboxFourBoundResultLeavesArchiveV1.Comparison yes=
                audit(r,FOUR,pins,pins.results.sha256,()->{});
            negative(yes.before);
            negative(yes.after);
            fourClean=yes.samePinnedResultLeavesObserved&&
                yes.before.portable.equals(pins.results.portable)&&
                yes.after.sha256.equals(pins.results.sha256)&&
                yes.fourWriterCohort.observedAgreement&&
                !yes.allIntervalsFenced&&
                !yes.safeArchiveEpochProved&&
                !yes.grantAuthorized&&noGrant(full);

            wrongPinDenied=denied(()->audit(
                r,FOUR,pins,"0".repeat(64),()->{}),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");
            malformedPinDenied=denied(()->audit(
                r,FOUR,pins,"not-a-sha",()->{}),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");

            Path terminalLeaf=fourTerminalLeaf(root);
            byte[] original=Files.readAllBytes(terminalLeaf);
            Files.delete(terminalLeaf);
            missingLeafDenied=denied(()->
                MailboxFourBoundResultLeavesArchiveV1.capture(r,FOUR),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");
            Files.write(terminalLeaf,original);

            Path extra=root.resolve(FOUR+
                ".properties.g21123-world-terminal-result-v3-"+
                "00000000-0000-0000-0000-000000000000.result");
            Files.write(extra,original);
            extraLeafDenied=denied(()->
                MailboxFourBoundResultLeavesArchiveV1.capture(r,FOUR),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");
            Files.delete(extra);

            Path unknown=root.resolve(FOUR+
                ".properties.g21122-strict-prepared-result-v4-unknown");
            Files.write(unknown,new byte[]{1});
            unknownVersionDenied=denied(()->
                MailboxFourBoundResultLeavesArchiveV1.capture(r,FOUR),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");
            Files.delete(unknown);

            byte[] tampered=original.clone();
            tampered[0]^=1;
            Files.write(terminalLeaf,tampered);
            changedPayloadDenied=denied(()->audit(
                r,FOUR,pins,pins.results.sha256,()->{}),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");
            Files.write(terminalLeaf,original);

            interpassTamperDenied=denied(
                ()->audit(r,FOUR,pins,pins.results.sha256,
                    ()->Files.write(terminalLeaf,tampered)),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");
            Files.write(terminalLeaf,original);

            // Original G21.124 remains compatible and unchanged.
            originalOldApiPass=MailboxFourBoundWriterCohortAuditV1
                .comparePinnedFourBoundResults(
                    r,FOUR,pins.v1.portable,pins.v1.sha256,
                    pins.account,pins.journal,pins.commit,pins.v2,
                    ()->{}).observedAgreement;

            Seed legacy=seed(world,OLD);
            completed(world,legacy,strict,journal,commit,false);
            legacyMissingDenied=denied(()->
                MailboxFourBoundResultLeavesArchiveV1.capture(r,OLD),
                "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY");

            MailboxWriterEvidencePortableArchiveV1.Verified archive=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    pins.v1.portable,pins.v1.sha256);
            v1PortableIntact=archive.records==8&&
                archive.started==4&&archive.completed==4&&
                !archive.cleanupAuthorized&&!archive.rolloverAuthorized;
            noGrants=noGrant(full)&&noGrant(legacy);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21125_PINNED_FOUR_RESULT_DIAGNOSTICS"+
                " realFourV3ResultLeafDigestMatched="+fourClean+
                " wrongIndependentDigestDenied="+wrongPinDenied+
                " malformedIndependentDigestDenied="+malformedPinDenied+
                " missingV3ResultDenied="+missingLeafDenied+
                " duplicateV3ResultDenied="+extraLeafDenied+
                " unknownV3VersionDenied="+unknownVersionDenied+
                " alteredV3SourceDenied="+changedPayloadDenied+
                " interpassV3DriftDenied="+interpassTamperDenied+
                " oldG21113UnboundDenied="+legacyMissingDenied+
                " oldG21124ApiUnchanged="+originalOldApiPass+
                " portableV1Intact="+v1PortableIntact+
                " noLiveGrants="+noGrants+
                " zeroJvmLeases="+zeroLeases);
            check(fourClean&&wrongPinDenied&&malformedPinDenied&&
                missingLeafDenied&&extraLeafDenied&&
                unknownVersionDenied&&changedPayloadDenied&&
                interpassTamperDenied&&legacyMissingDenied&&
                originalOldApiPass&&v1PortableIntact&&noGrants&&
                zeroLeases,
                "pinned four-v3 result inventory regression");
            System.out.println("G21125_PINNED_FOUR_RESULT_LEAVES_PASS"+
                " fourExactResultNamesAndBytes=true"+
                " resultSha256Pinned=true"+
                " existingFourResultCohort=true"+
                " existingFivePlanesPinned=true"+
                " externallyAuthenticatedPin=false"+
                " allWritersFenced=false atomicEpoch=false"+
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
        check(args.length==0,"unexpected test arguments");
        run();
    }
}

package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.124: strict opt-in forensic cohort of four real v3 results.
 * Tests the gaps deliberately: no cross-JVM atomicity, complete
 * historical writer participation, cleanup or live reward grant.
 */
public final class G21124MailboxFourBoundWriterCohortIntegrationTest {
    private static final String FULL="g21124-full";
    private static final String LEGACY="g21124-legacy";
    private static final String EXTRA="g21124-extra";

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.124 "+why);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed{
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long g,MailboxSettlementPostimagePlanner.Proposal s){
            player=p;generation=g;proposal=s;
            terminal=MailboxAtomicTerminalSnapshot.compose(s);
        }
    }
    private static Seed seed(World world,String a){
        WorldPlayer p=new WorldPlayer();
        long g=world.registerPlayer(p,a);
        String id=a+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.124 four-bound cohort","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21124_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot s=p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,s));
        return new Seed(p,g,MailboxSettlementPostimagePlanner
            .plan(p,g,s));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path file)throws Exception{
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder s=new StringBuilder(64);
        for(byte b:bytes)
            s.append(String.format(Locale.ROOT,"%02x",b&255));
        return s.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String a)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,a);
            v2=MailboxWriterKindEvidenceV2.capture(r,a).sha256;
            account=sha(r.resolve(a));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(a));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(a));
        }
    }
    private static MailboxFourBoundWriterCohortAuditV1.TwoObservations
        audit(FilePlayerRepository.PathResolver r,String a,Pins p,
            MailboxWriterArchiveLiveReconciliationV1.BetweenPasses b
        )throws IOException{
        return MailboxFourBoundWriterCohortAuditV1
            .comparePinnedFourBoundResults(r,a,p.v1.portable,
                p.v1.sha256,p.account,p.prepared,p.commit,p.v2,b);
    }
    @FunctionalInterface private interface Attempt{
        void run()throws Exception;
    }
    private static boolean denies(Attempt action,String label)
        throws Exception{
        try{action.run();return false;}
        catch(IOException ex){
            for(Throwable e=ex;e!=null;e=e.getCause())
                if(String.valueOf(e.getMessage()).contains(label))
                    return true;
            return false;
        }
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt terminal(
        World world,Seed s,StrictDurablePlayerSnapshotWriter writer,
        boolean bind
    )throws Exception{
        WorldPlayerPersistence.PreparedAccountReservation token=
            world.persistence().reservePreparedAccount(
                s.player,s.generation,s.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence output=
            (bind?world.persistence()
                .publishReservedTerminalStrictlyWithResultBinding(
                    token,s.proposal,s.terminal,writer)
                :world.persistence()
                .publishReservedTerminalStrictlyWithWriterEvidence(
                    token,s.proposal,s.terminal,writer))
                .get(15L,TimeUnit.SECONDS);
        check(output.strictReceipt.matchesSnapshot(s.terminal),
            "invalid actual terminal Receipt");
        return output.strictReceipt;
    }
    private static void completed(
        World world,Seed s,StrictDurablePlayerSnapshotWriter writer,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord commit,boolean boundWorld
    )throws Exception{
        writer.saveStrictWithResultBinding(s.proposal.preparedPreimage);
        journal.publishPreparedIntentWithResultBinding(s.proposal);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            terminal(world,s,writer,boundWorld);
        commit.recordConfirmedDiskTerminalWithResultBinding(
            s.proposal,receipt);
    }
    private static Path v3(Path dir,String name)throws IOException{
        try(Stream<Path> s=Files.list(dir)){
            return s.filter(p->p.getFileName().toString()
                .startsWith(name+
                    ".properties.g21123-world-terminal-result-v3-"))
                .findFirst().orElseThrow(()->new IOException(
                    "G21.124 missing terminal v3 test fixture"));
        }
    }
    private static void negative(
        MailboxFourBoundWriterCohortAuditV1.Report r
    ){
        check(r.fourIndependentBoundResultsObserved&&
            r.exactlyOneObservedPairPerRealKind&&
            !r.historicalWriterCensusProved&&
            !r.allDefaultRawFutureWritersExcluded&&
            !r.independentWriterProvenanceProved&&
            !r.multiFileAtomicEpochProved&&
            !r.hardwarePowerLossProved&&
            !r.archiveRolloverAuthorized&&
            !r.recordPruningAuthorized&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.sessionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "cohort promoted into grant/archival authorization");
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21124-cohort-");
        boolean clean=false,legacyUnboundDenied=false;
        boolean extraRealLabelDenied=false,oldGateAcceptedExtra=false;
        boolean interpassV3DriftDenied=false;
        boolean interpassLegacyAccountDriftDenied=false;
        boolean portableIntact=false,noRewards=false,zeroLeases=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);
            Seed full=seed(world,FULL);
            completed(world,full,writer,journal,commit,true);
            Pins pinned=new Pins(r,FULL);
            MailboxFourBoundWriterCohortAuditV1.TwoObservations good=
                audit(r,FULL,pinned,()->{});
            negative(good.before);
            negative(good.after);
            clean=good.observedAgreement&&
                !good.entireBetweenIntervalFenced&&
                !good.atomicMultiFileSnapshotProved&&
                !good.cleanupAuthorized&&!good.grantAuthorized&&
                good.before.preparedSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(
                            full.proposal.preparedPreimage))&&
                good.before.terminalSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(full.terminal))&&
                noGrant(full);

            Seed legacy=seed(world,LEGACY);
            completed(world,legacy,writer,journal,commit,false);
            Pins oldPins=new Pins(r,LEGACY);
            legacyUnboundDenied=denies(
                ()->audit(r,LEGACY,oldPins,()->{}),
                "G21.124 FOUR_BOUND_COHORT_UNSAFE_NO_AUTHORITY");

            // Same fully pinned v1/v2 real kinds + genuine COMMIT,
            // with an EXTRA caller-labeled no-op STRICT_PREPARED.
            // G21.119 allows a generic real-label candidate; G21.124
            // requires one and ONLY one bound attempt of each kind.
            Seed extra=seed(world,EXTRA);
            completed(world,extra,writer,journal,commit,true);
            Path selected=r.resolve(EXTRA);
            MailboxPublicationWriterLifecycle.withWriter(selected,()->
                MailboxWriterAttemptEvidenceV1.runRecordedInsideWriter(
                    selected,EXTRA,
                    MailboxWriterKindEvidenceV2.Kind.STRICT_PREPARED,
                    ()->Boolean.TRUE));
            Pins extraPins=new Pins(r,EXTRA);
            oldGateAcceptedExtra=
                MailboxRealWriterSemanticBoundaryV1
                    .requirePinnedRealWriterCandidate(
                        r,EXTRA,extraPins.v1.portable,
                        extraPins.v1.sha256,extraPins.account,
                        extraPins.prepared,extraPins.commit,
                        extraPins.v2).realWriterKindPairs==5;
            extraRealLabelDenied=denies(
                ()->audit(r,EXTRA,extraPins,()->{}),
                "G21.124 FOUR_BOUND_COHORT_UNSAFE_NO_AUTHORITY");

            Path result=v3(root,FULL);
            byte[] original=Files.readAllBytes(result);
            interpassV3DriftDenied=denies(
                ()->audit(r,FULL,pinned,()->{
                    byte[] changed=original.clone();changed[0]^=1;
                    Files.write(result,changed);
                }),
                "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY");
            Files.write(result,original);

            // An ordinary default strict writer remains UNRECORDED.
            // Its later canonical bytes change, so pinned G21.115
            // should reject the second pass, but did not stop the write.
            interpassLegacyAccountDriftDenied=denies(
                ()->audit(r,FULL,pinned,()->
                    writer.saveStrict(full.proposal.preparedPreimage)),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH_NO_AUTHORITY");

            MailboxWriterEvidencePortableArchiveV1.Verified old=
                MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                    pinned.v1.portable,pinned.v1.sha256);
            portableIntact=old.records==8&&old.started==4&&
                old.completed==4&&!old.cleanupAuthorized&&
                !old.rolloverAuthorized;
            noRewards=noGrant(full)&&noGrant(legacy)&&noGrant(extra);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21124_FOUR_RESULT_COHORT_DIAGNOSTICS"+
                " fourBoundReportsSamePreparedTerminal="+clean+
                " oldTypedOnlyTerminalDenied="+legacyUnboundDenied+
                " originalRealOnlyGateAllowsExtra="+oldGateAcceptedExtra+
                " extraTypedRealNoopDenied="+extraRealLabelDenied+
                " interpassV3TamperDenied="+interpassV3DriftDenied+
                " interpassUnrecordedCanonicalDriftDenied="+
                    interpassLegacyAccountDriftDenied+
                " v1PortableUnchanged="+portableIntact+
                " noLiveRewards="+noRewards+
                " zeroJvmLeases="+zeroLeases);
            check(clean&&legacyUnboundDenied&&oldGateAcceptedExtra&&
                extraRealLabelDenied&&interpassV3DriftDenied&&
                interpassLegacyAccountDriftDenied&&portableIntact&&
                noRewards&&zeroLeases,
                "four independently bound results fail-closed audit");
            System.out.println("G21124_FOUR_BOUND_COHORT_PASS"+
                " exactlyFourTypedRealPairs=true"+
                " allFourResultHashesAgree=true"+
                " sameG21100LifecycleObservation=true"+
                " persistentDriftRejected=true"+
                " completeHistoricalWriterCensus=false"+
                " atomicEpoch=false hardwarePowerLossProved=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected test args");
        run();
    }
}

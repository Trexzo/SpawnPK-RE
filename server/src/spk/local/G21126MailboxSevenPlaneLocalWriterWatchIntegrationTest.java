package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.126: one full REAL four-bound-result cohort, with an opt-in
 * SAME-JVM attempt watch spanning all independently pinned seven
 * content planes. This is deliberately NOT cross-process coverage.
 */
public final class G21126MailboxSevenPlaneLocalWriterWatchIntegrationTest {
    private static final String ACCOUNT="g21126-four";
    private static final String OTHER="g21126-other";

    private static void check(boolean value,String why){
        if(!value)throw new AssertionError("G21.126 "+why);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
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
            id,"G21.126 local watch fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21126_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,selected));
        return new Seed(p,generation,
            MailboxSettlementPostimagePlanner.plan(p,generation,selected));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path p)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(p));
        StringBuilder out=new StringBuilder(64);
        for(byte b:bytes)
            out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static void publish(
        World world,Seed seed,
        StrictDurablePlayerSnapshotWriter strict,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord disk
    )throws Exception{
        strict.saveStrictWithResultBinding(seed.proposal.preparedPreimage);
        journal.publishPreparedIntentWithResultBinding(seed.proposal);
        WorldPlayerPersistence.PreparedAccountReservation reservation=
            world.persistence().reservePreparedAccount(
                seed.player,seed.generation,seed.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence publication=
            world.persistence()
                .publishReservedTerminalStrictlyWithResultBinding(
                    reservation,seed.proposal,seed.terminal,strict)
                .get(15L,TimeUnit.SECONDS);
        check(publication.strictReceipt.matchesSnapshot(seed.terminal),
            "genuine terminal strict receipt missing");
        disk.recordConfirmedDiskTerminalWithResultBinding(
            seed.proposal,publication.strictReceipt);
    }
    private static final class Pins {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot leaves;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String name)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,name);
            leaves=MailboxFourBoundResultLeavesArchiveV1.capture(r,name);
            v2=MailboxWriterKindEvidenceV2.capture(r,name).sha256;
            account=sha(r.resolve(name));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(name));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(name));
        }
    }
    private static MailboxFourBoundResultLeavesArchiveV1.Comparison
        old(FilePlayerRepository.PathResolver r,Pins pins,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        return MailboxFourBoundResultLeavesArchiveV1
            .comparePinnedFourResultLeaves(r,ACCOUNT,
                pins.leaves.sha256,pins.v1.portable,pins.v1.sha256,
                pins.account,pins.prepared,pins.commit,pins.v2,between);
    }
    private static MailboxFourBoundResultLeavesArchiveV1.Comparison
        watched(FilePlayerRepository.PathResolver r,Pins pins,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        return MailboxSevenPlaneLocalWriterWatchV1
            .comparePinnedWithLocalAttemptWatch(r,ACCOUNT,
                pins.leaves.sha256,pins.v1.portable,pins.v1.sha256,
                pins.account,pins.prepared,pins.commit,pins.v2,between);
    }
    @FunctionalInterface private interface Action{void run()throws Exception;}
    private static boolean denied(Action act,String marker){
        try{act.run();return false;}
        catch(Exception error){
            for(Throwable cause=error;cause!=null;cause=cause.getCause())
                if(String.valueOf(cause.getMessage()).contains(marker))
                    return true;
            return false;
        }
    }
    private static void noAuthority(
        MailboxFourBoundResultLeavesArchiveV1.Comparison c
    ){
        check(c.samePinnedResultLeavesObserved&&
            c.fourWriterCohort.observedAgreement&&
            !c.allIntervalsFenced&&
            !c.independentSourceAuthenticationProved&&
            !c.safeArchiveEpochProved&&
            !c.cleanupAuthorized&&!c.rolloverAuthorized&&
            !c.grantAuthorized&&
            !c.fourWriterCohort.before.historicalWriterCensusProved&&
            !c.fourWriterCohort.before.allDefaultRawFutureWritersExcluded&&
            !c.fourWriterCohort.before.grantAuthorized,
            "local watch made positive archive/World authority");
    }
    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path dir)throws Exception{
        MailboxPublicationWriterLifecycle.withWriter(
            resolver(dir).resolve(ACCOUNT),()->Boolean.TRUE);
        check(MailboxLocalWriterAttemptWatchV1
                .activeLocalWatchCount()==0,
            "foreign JVM sees parent watch registration");
        System.out.println("G21126_FOREIGN_COOPERATING_NOOP_COMPLETED");
    }
    private static void foreign(Path root)throws IOException{
        Path log=root.resolve("g21126-child.log");
        try{
            Process p=new ProcessBuilder(javaBinary(),"-cp",
                System.getProperty("java.class.path"),
                G21126MailboxSevenPlaneLocalWriterWatchIntegrationTest
                    .class.getName(),"--child",root.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
            try{
                if(!p.waitFor(20L,TimeUnit.SECONDS)||
                   p.exitValue()!=0||
                   !new String(Files.readAllBytes(log),
                       StandardCharsets.UTF_8)
                        .contains("G21126_FOREIGN_COOPERATING_NOOP_COMPLETED"))
                    throw new IOException(
                        "G21126 foreign no-op child did not complete");
            }finally{
                if(p.isAlive()){
                    p.destroyForcibly();
                    p.waitFor(5L,TimeUnit.SECONDS);
                }
            }
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException("G21126 FOREIGN_CHILD_INTERRUPTED",
                interrupted);
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21126-local-watch-");
        boolean cleanMatched=false,oldApiSeesNoopAsClean=false;
        boolean sameJVMNoopDenied=false,failedJVMWriterDenied=false;
        boolean unrelatedAccountIgnored=false,callbackErrorDisarmed=false;
        boolean overlappingWatchDenied=false,realStrictAttemptObserved=false;
        boolean foreignJvmOutsideWatch=false,rawWriterOutsideWatch=false;
        boolean originalArchivesUnchanged=false;
        boolean noGrants=false,zeroLeases=false;
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
            Seed full=seed(world,ACCOUNT);
            publish(world,full,strict,journal,commit);
            Pins pins=new Pins(r,ACCOUNT);
            Path selected=r.resolve(ACCOUNT);

            MailboxFourBoundResultLeavesArchiveV1.Comparison clean=
                watched(r,pins,()->{});
            noAuthority(clean);
            cleanMatched=true;
            MailboxFourBoundResultLeavesArchiveV1.Comparison oldNoop=
                old(r,pins,()->
                    MailboxPublicationWriterLifecycle.withWriter(
                        selected,()->Boolean.TRUE));
            noAuthority(oldNoop);
            oldApiSeesNoopAsClean=true;
            sameJVMNoopDenied=denied(
                ()->watched(r,pins,()->
                    MailboxPublicationWriterLifecycle.withWriter(
                        selected,()->Boolean.TRUE)),
                "G21.126 SEVEN_PLANE_LOCAL_ATTEMPT_UNSAFE_NO_AUTHORITY");

            failedJVMWriterDenied=denied(
                ()->watched(r,pins,()->{
                    try{
                        MailboxPublicationWriterLifecycle.withWriter(
                            selected,()->{
                                throw new IOException(
                                    "G21126_SYNTHETIC_FAILURE");
                            });
                    }catch(IOException injected){
                        if(!String.valueOf(injected.getMessage())
                            .contains("G21126_SYNTHETIC_FAILURE"))
                            throw injected;
                    }
                }),
                "G21.126 SEVEN_PLANE_LOCAL_ATTEMPT_UNSAFE_NO_AUTHORITY");

            Seed other=seed(world,OTHER);
            MailboxFourBoundResultLeavesArchiveV1.Comparison separate=
                watched(r,pins,()->
                    strict.saveStrict(other.proposal.preparedPreimage));
            noAuthority(separate);
            unrelatedAccountIgnored=true;

            callbackErrorDisarmed=denied(
                ()->watched(r,pins,()->{
                    throw new IOException("G21126_CALLBACK_ABORT");
                }),"G21126_CALLBACK_ABORT")&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;

            try(MailboxLocalWriterAttemptWatchV1.Scope existing=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                overlappingWatchDenied=denied(
                    ()->watched(r,pins,()->{}),
                    "G21.116 CONCURRENT_LOCAL_WATCH_NO_AUTHORITY");
            }

            // A real ordinary strict default write is UNENROLLED in
            // v1/v2/v3 but still reports local G21.100 lock entry.
            int oldLeaves=MailboxWriterAttemptEvidenceV1
                .inspect(r,ACCOUNT).recordCount;
            String v3Pin=pins.leaves.sha256;
            try(MailboxLocalWriterAttemptWatchV1.Scope watch=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                strict.saveStrict(full.terminal);
                realStrictAttemptObserved=watch.sawAttempt();
            }
            realStrictAttemptObserved&=
                MailboxWriterAttemptEvidenceV1.inspect(r,ACCOUNT)
                    .recordCount==oldLeaves&&
                MailboxFourBoundResultLeavesArchiveV1
                    .capture(r,ACCOUNT).sha256.equals(v3Pin);

            // Negative limitations: no parent registry event when
            // another JVM enters G21.100; or when raw file bytes are
            // written without the cooperating lifecycle API.
            try(MailboxLocalWriterAttemptWatchV1.Scope watch=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                foreign(root);
                foreignJvmOutsideWatch=!watch.sawAttempt();
            }
            try(MailboxLocalWriterAttemptWatchV1.Scope watch=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                byte[] oldBytes=Files.readAllBytes(selected);
                Files.write(selected,oldBytes);
                rawWriterOutsideWatch=!watch.sawAttempt();
            }
            originalArchivesUnchanged=
                MailboxWriterEvidencePortableArchiveV1
                    .verifyPortable(pins.v1.portable,pins.v1.sha256)
                    .records==8;
            noGrants=noGrant(full)&&noGrant(other);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21126_SEVEN_PLANE_LOCAL_WATCH_DIAGNOSTICS"+
                " cleanSevenPlanePass="+cleanMatched+
                " originalG21125AllowsNoop="+oldApiSeesNoopAsClean+
                " unchangedBytesAttemptDenied="+sameJVMNoopDenied+
                " failedWriterAttemptDenied="+failedJVMWriterDenied+
                " otherAccountNotContaminated="+unrelatedAccountIgnored+
                " callbackFailureDisarmed="+callbackErrorDisarmed+
                " overlappingWatchDenied="+overlappingWatchDenied+
                " ordinaryStrictWriteObserved="+realStrictAttemptObserved+
                " foreignJvmNoopInvisible="+foreignJvmOutsideWatch+
                " rawWriterInvisible="+rawWriterOutsideWatch+
                " v1ArchiveUnchanged="+originalArchivesUnchanged+
                " noLiveGrant="+noGrants+
                " zeroJvmLeaseWatchLeaks="+zeroLeases);
            check(cleanMatched&&oldApiSeesNoopAsClean&&
                sameJVMNoopDenied&&failedJVMWriterDenied&&
                unrelatedAccountIgnored&&callbackErrorDisarmed&&
                overlappingWatchDenied&&realStrictAttemptObserved&&
                foreignJvmOutsideWatch&&rawWriterOutsideWatch&&
                originalArchivesUnchanged&&noGrants&&zeroLeases,
                "G21.126 fail-closed local seven-plane watch acceptance");
            System.out.println("G21126_SEVEN_PLANE_LOCAL_WATCH_PASS"+
                " sevenPinnedPlanesPreserved=true"+
                " sameJvmCooperatingAttemptVeto=true"+
                " processLocalOnly=true"+
                " foreignJvmHistoryFenced=false"+
                " rawDefaultHistoryFenced=false"+
                " atomicEpoch=false signedProvenance=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path f:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(f);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize());
            return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}

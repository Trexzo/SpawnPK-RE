package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.116: opt-in JVM-local cooperating attempt detection.
 * Demonstrates actual absent cross-JVM/raw writer participation,
 * rather than claiming a safe generation or archive epoch.
 */
public final class G21116MailboxLocalWriterActivityWatchIntegrationTest {
    private static final String ACCOUNT="g21116-main";
    private static final String OTHER="g21116-other";

    private static void check(boolean pass,String reason){
        if(!pass)throw new AssertionError("G21.116 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return a->dir.resolve(a+".properties");
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
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.116 local attempt watch","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21116_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,selected));
        return new Seed(player,generation,
            MailboxSettlementPostimagePlanner.plan(
                player,generation,selected));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path file)throws Exception{
        byte[] digest=java.security.MessageDigest
            .getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    @FunctionalInterface
    private interface Action {void run()throws Exception;}
    private static boolean rejects(Action action,String marker)
        throws Exception{
        try{action.run();return false;}
        catch(IOException e){
            return String.valueOf(e.getMessage()).contains(marker);
        }
    }
    private static final class Pins {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final String account,prepared,commit,typed;
        Pins(FilePlayerRepository.PathResolver r,String a,
             MailboxDurableIdempotencyIntentJournal journal,
             MailboxGuardedDiskCommitRecord commitRecord)
             throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,a);
            typed=MailboxWriterKindEvidenceV2.capture(r,a).sha256;
            account=sha(r.resolve(a));
            prepared=sha(journal.journalPath(a));
            commit=sha(commitRecord.recordPath(a));
        }
    }
    private static MailboxWriterArchiveLiveReconciliationV1.Report
        checked(FilePlayerRepository.PathResolver r,Pins pins,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        return MailboxLocalWriterAttemptWatchV1
            .comparePinnedTypedRecoveryLeavesWatchLocalAttempts(
                r,ACCOUNT,pins.v1.portable,pins.v1.sha256,
                pins.account,pins.prepared,pins.commit,pins.typed,between);
    }
    private static void negative(
        MailboxWriterArchiveLiveReconciliationV1.Report r
    ){
        check(r.pinnedTypedWriterKindBytesMatched&&
            r.pinnedCanonicalBytesMatched&&
            r.pinnedRecoverySidecarBytesMatched&&
            !r.allWriterVersionsFenced&&
            !r.futureLegacyWritersExcluded&&
            !r.ledgerLifetimeArchived&&
            !r.rolloverAuthorized&&!r.recordPruningAuthorized&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.sessionAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "clean local watcher manufactured authority");
    }
    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void fork(Path root)throws Exception{
        Path log=root.resolve("g21116-foreign-jvm.log");
        Process p=new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            G21116MailboxLocalWriterActivityWatchIntegrationTest.class
                .getName(),"--child",root.toString())
            .redirectErrorStream(true).redirectOutput(log.toFile())
            .start();
        try{
            check(p.waitFor(25L,TimeUnit.SECONDS),"foreign JVM timeout");
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5L,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            java.nio.charset.StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains(
            "G21116_OTHER_JVM_COOPERATING_WRITE_ENTRY"),
            "foreign JVM could not enter lifecycle "+output);
    }
    private static void child(Path root)throws Exception{
        Path file=resolver(root).resolve(ACCOUNT);
        MailboxPublicationWriterLifecycle.withWriter(file,()->{
            System.out.println(
                "G21116_OTHER_JVM_COOPERATING_WRITE_ENTRY");
            return Boolean.TRUE;
        });
        check(MailboxLocalWriterAttemptWatchV1
            .activeLocalWatchCount()==0,
            "cross-JVM watch unexpectedly shared");
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21116-local-watch-");
        boolean cleanMatched=false,zeroContentAttemptDenied=false;
        boolean realLegacySaveCounted=false,otherAccountIgnored=false;
        boolean exceptionCounted=false,watchReleasedOnFailure=false;
        boolean duplicateWatchDenied=false,rawNotTracked=false;
        boolean foreignJvmNotTracked=false,priorApiUnchanged=false;
        boolean allNoGrant=false,noLeaseLeaks=false;
        try(World world=World.isolatedForTest(
            60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=resolver(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(r);
            Seed full=seed(world,ACCOUNT);
            strict.saveStrictWithWriterEvidence(
                full.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(
                full.proposal);
            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    full.player,full.generation,full.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence terminal=
                world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        token,full.proposal,full.terminal,strict)
                    .get(15L,TimeUnit.SECONDS);
            disk.recordConfirmedDiskTerminalWithWriterEvidence(
                full.proposal,terminal.strictReceipt);
            Pins pins=new Pins(r,ACCOUNT,journal,disk);
            Path selected=r.resolve(ACCOUNT);

            MailboxWriterArchiveLiveReconciliationV1.Report clean=
                checked(r,pins,()->{});
            negative(clean);
            cleanMatched=true;
            check(MailboxLocalWriterAttemptWatchV1
                .activeLocalWatchCount()==0,"clean observation leak");

            // Existing five-plane witness sees identical inputs while
            // this local cooperating no-op acquires G21.100. The NEW
            // opt-in monitor detects its attempt without content drift.
            MailboxWriterArchiveLiveReconciliationV1.Report old=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedTypedRecoveryLeaves(
                        r,ACCOUNT,pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,pins.typed,
                        ()->MailboxPublicationWriterLifecycle.withWriter(
                            selected,()->Boolean.TRUE));
            negative(old);
            priorApiUnchanged=!old.allWriterVersionsFenced;
            zeroContentAttemptDenied=rejects(
                ()->checked(r,pins,
                    ()->MailboxPublicationWriterLifecycle.withWriter(
                        selected,()->Boolean.TRUE)),
                "G21.116 LOCAL_WRITER_ATTEMPT_SEEN_NO_AUTHORITY");

            // Different account's cooperating writes do not contaminate
            // the watched account's negative-only observations.
            Seed other=seed(world,OTHER);
            MailboxWriterArchiveLiveReconciliationV1.Report unaffected=
                checked(r,pins,
                    ()->strict.saveStrict(
                        other.proposal.preparedPreimage));
            negative(unaffected);
            otherAccountIgnored=true;

            // Real default strict writer is still UNRECORDED in v1/v2
            // but its local G21.100 entry is visible to the scope.
            String v1Pinned=pins.v1.portable;
            String v2Pinned=pins.typed;
            try(MailboxLocalWriterAttemptWatchV1.Scope scope=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                strict.saveStrict(full.terminal);
                realLegacySaveCounted=scope.sawAttempt();
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1Again=
                MailboxWriterEvidencePortableArchiveV1.capture(r,ACCOUNT);
            MailboxWriterKindEvidenceV2.Snapshot v2Again=
                MailboxWriterKindEvidenceV2.capture(r,ACCOUNT);
            realLegacySaveCounted&=v1Pinned.equals(v1Again.portable)&&
                v2Pinned.equals(v2Again.sha256);
            // Re-pin account after ordinary default file replacement.
            Pins repinned=new Pins(r,ACCOUNT,journal,disk);
            negative(checked(r,repinned,()->{}));

            // An attempted writer throwing BEFORE any file operation
            // is conservatively classified as activity.
            try(MailboxLocalWriterAttemptWatchV1.Scope scope=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                boolean injected=rejects(
                    ()->MailboxPublicationWriterLifecycle.withWriter(
                        selected,()->{
                            throw new IOException(
                                "G21116_SYNTHETIC_WRITER_FAILURE");
                        }),"G21116_SYNTHETIC_WRITER_FAILURE");
                exceptionCounted=injected&&scope.sawAttempt();
            }

            // Duplicate watches on the SAME account are refused.
            try(MailboxLocalWriterAttemptWatchV1.Scope first=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                duplicateWatchDenied=rejects(
                    ()->{
                        try(MailboxLocalWriterAttemptWatchV1.Scope ignored=
                                MailboxLocalWriterAttemptWatchV1
                                    .arm(selected)){}
                    },"G21.116 CONCURRENT_LOCAL_WATCH_NO_AUTHORITY");
            }

            // Caller exception must disarm via finally / AutoCloseable.
            watchReleasedOnFailure=rejects(
                ()->checked(r,repinned,()->{
                    throw new IOException("G21116_CALLBACK_ABORT");
                }),"G21116_CALLBACK_ABORT")&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;

            // Deliberate NEGATIVE gap proofs: direct raw mutation is
            // NOT a G21.100 writer entry and another process has its
            // own JVM-local registry. These cannot authorize epochs.
            try(MailboxLocalWriterAttemptWatchV1.Scope scope=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                byte[] sameBytes=Files.readAllBytes(selected);
                Files.write(selected,sameBytes);
                rawNotTracked=!scope.sawAttempt();
            }
            try(MailboxLocalWriterAttemptWatchV1.Scope scope=
                    MailboxLocalWriterAttemptWatchV1.arm(selected)){
                fork(root);
                foreignJvmNotTracked=!scope.sawAttempt();
            }

            allNoGrant=noGrant(full)&&noGrant(other);
            noLeaseLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21116_LOCAL_WRITER_WATCH_DIAGNOSTICS"+
                " cleanFivePlaneWitness="+cleanMatched+
                " identicalBytesCooperatingAttemptVeto="+
                    zeroContentAttemptDenied+
                " ordinaryStrictWriterAttemptObserved="+
                    realLegacySaveCounted+
                " unrelatedAccountExcluded="+otherAccountIgnored+
                " failedWriterAttemptObserved="+exceptionCounted+
                " callbackFailureDisarmed="+watchReleasedOnFailure+
                " duplicateWatchRefused="+duplicateWatchDenied+
                " rawWriterOutsideRegistry="+rawNotTracked+
                " separateJvmOutsideRegistry="+foreignJvmNotTracked+
                " oldFivePlaneApiUnchanged="+priorApiUnchanged+
                " noRewardGrant="+allNoGrant+
                " zeroRegistryAndLockLeaks="+noLeaseLeaks);
            check(cleanMatched&&zeroContentAttemptDenied&&
                realLegacySaveCounted&&otherAccountIgnored&&
                exceptionCounted&&watchReleasedOnFailure&&
                duplicateWatchDenied&&rawNotTracked&&
                foreignJvmNotTracked&&priorApiUnchanged&&allNoGrant&&
                noLeaseLeaks,
                "scoped local writer activity regression");
            System.out.println("G21116_COOPERATING_WRITER_ACTIVITY_PASS"+
                " sameJvmOnly=true cooperatingOnly=true"+
                " detectsUnchangedAttempts=true priorApisIntact=true"+
                " rawWriterCoverage=false crossJvmCoverage=false"+
                " historicalWriterCoverage=false"+
                " generationFence=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize());
            return;
        }
        check(args.length==0,"unexpected test args");
        run();
    }
}

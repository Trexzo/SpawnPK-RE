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
 * G21.127: independent REAL Java processes acting against an actual
 * four-result-bound Mailbox account. Seven-plane pins and the
 * G21.126 local attempt watch CANNOT attest absent foreign history.
 *
 * No default writer changes, no writer generation fence, no grant,
 * cleanup, replay, session, rollover, pruning, unlink or client ACK.
 */
public final class G21127MailboxCrossJvmSevenPlaneDiagnosticIntegrationTest {
    private static final String MAIN="g21127-main";
    private static final String CRASH="g21127-crash";
    private static final long PROBE_MS=130L;

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.127 "+why);
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
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String message=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            message,"G21.127 independent JVM gap","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21127_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot chosen=
            player.mailbox().get(message);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,chosen));
        return new Seed(player,generation,
            MailboxSettlementPostimagePlanner.plan(
                player,generation,chosen));
    }
    private static boolean noGrant(Seed seed){
        return seed.player.bank().inventorySlots()==0&&
            seed.player.mailbox().get(seed.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
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
        WorldPlayerPersistence.TerminalPublicationEvidence terminal=
            world.persistence()
                .publishReservedTerminalStrictlyWithResultBinding(
                    reservation,seed.proposal,seed.terminal,strict)
                .get(15L,TimeUnit.SECONDS);
        check(terminal.strictReceipt.matchesSnapshot(seed.terminal),
            "missing true strict World terminal Receipt");
        disk.recordConfirmedDiskTerminalWithResultBinding(
            seed.proposal,terminal.strictReceipt);
    }
    private static String hash(Path file)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder result=new StringBuilder(64);
        for(byte b:digest)
            result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    private static final class Pins {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver resolver,String account)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1
                .capture(resolver,account);
            v3=MailboxFourBoundResultLeavesArchiveV1
                .capture(resolver,account);
            v2=MailboxWriterKindEvidenceV2
                .capture(resolver,account).sha256;
            this.account=hash(resolver.resolve(account));
            prepared=hash(
                new MailboxDurableIdempotencyIntentJournal(resolver)
                    .journalPath(account));
            commit=hash(new MailboxGuardedDiskCommitRecord(resolver)
                .recordPath(account));
        }
    }
    private static MailboxFourBoundResultLeavesArchiveV1.Comparison
        seven(FilePlayerRepository.PathResolver r,String account,Pins p,
              MailboxWriterArchiveLiveReconciliationV1.BetweenPasses gap)
        throws IOException{
        return MailboxSevenPlaneLocalWriterWatchV1
            .comparePinnedWithLocalAttemptWatch(
                r,account,p.v3.sha256,
                p.v1.portable,p.v1.sha256,p.account,
                p.prepared,p.commit,p.v2,gap);
    }
    @FunctionalInterface interface Operation {void run()throws Exception;}
    private static boolean rejected(Operation op){
        try{op.run();return false;}
        catch(IOException expected){return true;}
        catch(Exception unexpected){
            for(Throwable x=unexpected;x!=null;x=x.getCause())
                if(x instanceof IOException)return true;
            return false;
        }
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path root,String account,String kind)
        throws Exception{
        FilePlayerRepository.PathResolver r=resolver(root);
        Path file=r.resolve(account);
        if("ordinary".equals(kind)){
            MailboxPublicationWriterLifecycle.withWriter(
                file,()->Boolean.TRUE);
            System.out.println("G21127_FOREIGN_UNRECORDED_NOOP_PASS");
            return;
        }
        if("paired".equals(kind)){
            MailboxCrossJvmDurableWriterDiagnosticV1.runRecorded(
                r,account,()->Boolean.TRUE);
            System.out.println("G21127_FOREIGN_DURABLE_PAIR_PASS");
            return;
        }
        if("crash".equals(kind)){
            MailboxCrossJvmDurableWriterDiagnosticV1.runRecorded(
                r,account,()->{
                    System.out.println(
                        "G21127_FOREIGN_FORCED_BEGINS_HARD_CRASH");
                    System.out.flush();
                    Runtime.getRuntime().halt(139);
                    throw new AssertionError("halt returned");
                });
            throw new AssertionError("crash callback returned");
        }
        if("fresh-veto".equals(kind)){
            MailboxWriterAttemptEvidenceV1.Report v1=
                MailboxWriterAttemptEvidenceV1.inspect(r,account);
            MailboxWriterKindEvidenceV2.Report v2=
                MailboxWriterKindEvidenceV2.inspect(r,account);
            boolean[] attempted={false};
            boolean refused=false;
            try{
                MailboxCrossJvmDurableWriterDiagnosticV1.runRecorded(
                    r,account,()->{
                        attempted[0]=true;
                        return Boolean.TRUE;
                    });
            }catch(IOException error){
                refused=String.valueOf(error.getMessage()).contains(
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            }
            check(v1.recordCount==9&&v1.started==5&&
                v1.completed==4&&v2.typedBegins==5&&
                v2.typedCompletes==4&&refused&&!attempted[0],
                "fresh independent JVM accepted incomplete diagnostic");
            MailboxPublicationWriterLifecycle.withWriter(
                file,()->Boolean.TRUE);
            check(MailboxWriterAttemptEvidenceV1.inspect(
                r,account).recordCount==9,
                "ordinary writer unexpectedly enrolled after crash");
            System.out.println("G21127_FRESH_JVM_INCOMPLETE_VETO_PASS");
            return;
        }
        throw new IllegalArgumentException("unknown child kind");
    }
    private static void fork(
        Path root,String account,String kind,int code,String marker
    )throws IOException{
        Path log=root.resolve("g21127-"+account+"-"+kind+".log");
        Process process=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21127MailboxCrossJvmSevenPlaneDiagnosticIntegrationTest
                .class.getName(),"--child",root.toString(),account,kind)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        try{
            if(!process.waitFor(25L,TimeUnit.SECONDS))
                throw new IOException("G21.127 child timeout "+kind);
            String body=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            if(process.exitValue()!=code||!body.contains(marker))
                throw new IOException(
                    "G21.127 child failed "+kind+
                    " exit="+process.exitValue()+" log="+body);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IOException("G21.127 child interrupted",e);
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                try{process.waitFor(5L,TimeUnit.SECONDS);}
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21127-foreign-audit-");
        boolean cleanSeven=false,twoQuietMissForeign=false;
        boolean originalPinsUnchanged=false,watchMissesForeign=false;
        boolean durablePairSeen=false,stalePinsVeto=false;
        boolean repinnedMixedKindVeto=false;
        boolean crashedBeginPersisted=false;
        boolean freshJVMRefused=false,ordinaryAfterCrashUnfenced=false;
        boolean noRewards=false,zeroLeases=false;
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
            Seed main=seed(world,MAIN);
            Seed crash=seed(world,CRASH);
            publish(world,main,strict,journal,disk);
            publish(world,crash,strict,journal,disk);
            Pins pins=new Pins(r,MAIN),crashPins=new Pins(r,CRASH);
            MailboxFourBoundResultLeavesArchiveV1.Comparison accepted=
                seven(r,MAIN,pins,()->{});
            cleanSeven=accepted.samePinnedResultLeavesObserved&&
                accepted.fourWriterCohort.observedAgreement&&
                !accepted.safeArchiveEpochProved&&!accepted.grantAuthorized;
            MailboxCrossJvmWriterLeaseProbeV1.State quiet=
                MailboxCrossJvmWriterLeaseProbeV1.State
                    .QUIET_AT_SAMPLE_ONLY;
            final boolean[] quietGap={false};
            MailboxFourBoundResultLeavesArchiveV1.Comparison gap=
                seven(r,MAIN,pins,()->{
                    MailboxCrossJvmWriterLeaseProbeV1.TwoSamples samples=
                        MailboxCrossJvmWriterLeaseProbeV1.sampleAround(
                            r,MAIN,PROBE_MS,()->fork(
                                root,MAIN,"ordinary",0,
                                "G21127_FOREIGN_UNRECORDED_NOOP_PASS"));
                    quietGap[0]=samples.before.state==quiet&&
                        samples.after.state==quiet&&
                        !samples.observedBusy&&
                        !samples.entireIntervalQuiescentProved&&
                        !samples.allWriterGenerationsFenced;
                });
            twoQuietMissForeign=quietGap[0]&&
                gap.samePinnedResultLeavesObserved;
            watchMissesForeign=gap.fourWriterCohort.observedAgreement&&
                !gap.safeArchiveEpochProved;
            originalPinsUnchanged=
                MailboxWriterEvidencePortableArchiveV1
                    .capture(r,MAIN).sha256.equals(pins.v1.sha256)&&
                MailboxWriterKindEvidenceV2
                    .capture(r,MAIN).sha256.equals(pins.v2)&&
                MailboxFourBoundResultLeavesArchiveV1
                    .capture(r,MAIN).sha256.equals(pins.v3.sha256)&&
                pins.account.equals(hash(r.resolve(MAIN)));

            fork(root,MAIN,"paired",0,
                "G21127_FOREIGN_DURABLE_PAIR_PASS");
            MailboxWriterAttemptEvidenceV1.Report recorded=
                MailboxWriterAttemptEvidenceV1.inspect(r,MAIN);
            MailboxWriterKindEvidenceV2.Report typed=
                MailboxWriterKindEvidenceV2.inspect(r,MAIN);
            durablePairSeen=recorded.recordCount==10&&
                recorded.started==5&&recorded.completed==5&&
                typed.typedBegins==5&&typed.typedCompletes==5&&
                typed.pairedByKind.getOrDefault(
                    MailboxWriterKindEvidenceV2.Kind
                        .COOPERATING_DIAGNOSTIC,0)==1&&
                MailboxFourBoundResultLeavesArchiveV1
                    .capture(r,MAIN).sha256.equals(pins.v3.sha256)&&
                pins.account.equals(hash(r.resolve(MAIN)));
            stalePinsVeto=rejected(()->seven(r,MAIN,pins,()->{}));
            Pins repinned=new Pins(r,MAIN);
            repinnedMixedKindVeto=rejected(()->seven(
                r,MAIN,repinned,()->{}));

            fork(root,CRASH,"crash",139,
                "G21127_FOREIGN_FORCED_BEGINS_HARD_CRASH");
            MailboxWriterAttemptEvidenceV1.Report begin=
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH);
            MailboxWriterKindEvidenceV2.Report beginTyped=
                MailboxWriterKindEvidenceV2.inspect(r,CRASH);
            crashedBeginPersisted=begin.recordCount==9&&
                begin.started==5&&begin.completed==4&&
                beginTyped.typedBegins==5&&
                beginTyped.typedCompletes==4&&
                MailboxCrossJvmWriterLeaseProbeV1
                    .sample(r,CRASH,PROBE_MS).state==quiet&&
                MailboxFourBoundResultLeavesArchiveV1
                    .capture(r,CRASH).sha256.equals(crashPins.v3.sha256);
            fork(root,CRASH,"fresh-veto",0,
                "G21127_FRESH_JVM_INCOMPLETE_VETO_PASS");
            freshJVMRefused=rejected(()->seven(
                r,CRASH,crashPins,()->{}))&&
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH)
                    .recordCount==9;
            ordinaryAfterCrashUnfenced=
                MailboxWriterKindEvidenceV2.inspect(r,CRASH)
                    .typedBegins==5&&
                MailboxFourBoundResultLeavesArchiveV1.capture(r,CRASH)
                    .sha256.equals(crashPins.v3.sha256);
            noRewards=noGrant(main)&&noGrant(crash);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21127_FOREIGN_SEVEN_PLANE_DIAGNOSTICS"+
                " cleanFourBoundSevenPlanes="+cleanSeven+
                " twoQuietLeaseSamplesMissForeignNoop="+
                    twoQuietMissForeign+
                " localWatchMissesForeignNoop="+watchMissesForeign+
                " allOriginalPinsStillMatch="+originalPinsUnchanged+
                " foreignOptinDurableDiagnosticPaired="+durablePairSeen+
                " originalPinsDetectNewEvidence="+stalePinsVeto+
                " repinnedDiagnosticStillNotBusiness="+
                    repinnedMixedKindVeto+
                " foreignHardCrashBeginPersisted="+
                    crashedBeginPersisted+
                " freshJvmRejectsIncomplete="+freshJVMRefused+
                " defaultPostCrashStillUnfenced="+
                    ordinaryAfterCrashUnfenced+
                " noLiveGrants="+noRewards+
                " zeroJvmLeases="+zeroLeases);
            check(cleanSeven&&twoQuietMissForeign&&
                watchMissesForeign&&originalPinsUnchanged&&
                durablePairSeen&&stalePinsVeto&&repinnedMixedKindVeto&&
                crashedBeginPersisted&&freshJVMRefused&&
                ordinaryAfterCrashUnfenced&&noRewards&&zeroLeases,
                "real foreign-JVM seven-plane negative audit");
            System.out.println("G21127_FOREIGN_DURABLE_DIAGNOSTIC_PASS"+
                " realIndependentJvms=true"+
                " optinDiagnosticDurableAcrossExit=true"+
                " foreignCompletedDefaultAttemptInvisible=true"+
                " twoQuietSamplesNoEpoch=true"+
                " diagnosticCannotReplaceRealWriter=true"+
                " incompleteCrashVetoFreshJvm=true"+
                " historicalRawDefaultWriterFence=false"+
                " crossJvmAtomicEpoch=false signedProvenance=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2],args[3]);
            return;
        }
        check(args.length==0,"unexpected test arguments");
        run();
    }
}

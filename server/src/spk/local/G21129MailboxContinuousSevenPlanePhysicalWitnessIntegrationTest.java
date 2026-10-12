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
 * G21.129: one continuously held G21.100 cross-process OS lease for
 * physical seven-plane observations, not for independently nested
 * rich G21.119/124 semantics or any historic/future/raw writer epoch.
 */
public final class G21129MailboxContinuousSevenPlanePhysicalWitnessIntegrationTest {
    private static final String ACCOUNT="g21129-main";
    private static final String OTHER="g21129-other";
    private static final long WAIT=190L;

    private static void check(boolean b,String why){
        if(!b)throw new AssertionError("G21.129 "+why);
    }
    private static FilePlayerRepository.PathResolver resolver(Path dir){
        return who->dir.resolve(who+".properties");
    }
    private static final class Seed{
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long gen,
            MailboxSettlementPostimagePlanner.Proposal q){
            player=p;generation=gen;proposal=q;
            terminal=MailboxAtomicTerminalSnapshot.compose(q);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer p=new WorldPlayer();
        long generation=world.registerPlayer(p,account);
        String id=account+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.129 continuous seven plane","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21129_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot s=p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,s));
        return new Seed(p,generation,
            MailboxSettlementPostimagePlanner.plan(p,generation,s));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static void publish(
        World world,Seed s,StrictDurablePlayerSnapshotWriter strict,
        MailboxDurableIdempotencyIntentJournal journal,
        MailboxGuardedDiskCommitRecord disk
    )throws Exception{
        strict.saveStrictWithResultBinding(s.proposal.preparedPreimage);
        journal.publishPreparedIntentWithResultBinding(s.proposal);
        WorldPlayerPersistence.PreparedAccountReservation ticket=
            world.persistence().reservePreparedAccount(
                s.player,s.generation,s.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence saved=
            world.persistence()
                .publishReservedTerminalStrictlyWithResultBinding(
                    ticket,s.proposal,s.terminal,strict)
                .get(15L,TimeUnit.SECONDS);
        check(saved.strictReceipt.matchesSnapshot(s.terminal),
            "real World terminal Receipt missing");
        disk.recordConfirmedDiskTerminalWithResultBinding(
            s.proposal,saved.strictReceipt);
    }
    private static String digest(Path file)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file));
        StringBuilder out=new StringBuilder(64);
        for(byte b:bytes)
            out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String name)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,name);
            v2=MailboxWriterKindEvidenceV2.capture(r,name);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,name);
            account=digest(r.resolve(name));
            prepared=digest(new
                MailboxDurableIdempotencyIntentJournal(r)
                    .journalPath(name));
            commit=digest(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(name));
        }
    }
    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        compare(FilePlayerRepository.PathResolver r,Pins p,
          MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback
        )throws IOException{
        return MailboxSevenPlaneContinuousPhysicalWitnessV1
            .compareWithContinuousLease(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2.sha256,WAIT,callback);
    }
    private static void negative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report r
    ){
        check(r.sevenPhysicalPinsMatched&&
            r.cooperatingCrossJvmWritesExcludedDuringLease&&
            !r.richFourWriterSemanticCohortProved&&
            !r.independentPinAuthenticationProved&&
            !r.rawWriterExcluded&&
            !r.historicalAndFutureWritersExcluded&&
            !r.atomicRecoveryEpochProved&&
            !r.hardwarePowerLossProved&&
            !r.cleanupAuthorized&&!r.unlinkAuthorized&&
            !r.pruningAuthorized&&!r.rolloverAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.sessionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,
            "observation manufactured extra authority");
    }
    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static Process spawn(
        Path root,String account,String mode,Path log
    )throws IOException{
        return new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            G21128MailboxGuardedCrossJvmSevenPlaneGapIntegrationTest
                .class.getName(),
            "--child",root.toString(),account,mode)
            .redirectErrorStream(true).redirectOutput(log.toFile())
            .start();
    }
    private static void child(
        Path root,String account,String mode)throws IOException{
        Path log=root.resolve("g21129-"+mode+"-"+account+".log");
        Process child=spawn(root,account,mode,log);
        try{
            if(!child.waitFor(24L,TimeUnit.SECONDS))
                throw new IOException("foreign JVM timeout "+mode);
            String result=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            String expected="G21128_CHILD_"+
                mode.toUpperCase(Locale.ROOT);
            if(child.exitValue()!=0||!result.contains(expected))
                throw new IOException("foreign JVM rejected "+mode+
                    " exit="+child.exitValue()+" log="+result);
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException("foreign JVM interrupted",interrupted);
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                try{child.waitFor(3L,TimeUnit.SECONDS);}
                catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    private static boolean denied(
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses call,
        String marker
    ){
        try{call.run();return false;}
        catch(IOException failure){
            return String.valueOf(failure.getMessage())
                .contains(marker);
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21129-continuous-");
        boolean clean=false,foreignBusyDuringFullWindow=false;
        boolean unrelatedQuiet=false,busyHolderDenied=false;
        boolean callbackNotRun=false,callbackFailureReleased=false;
        boolean rawIdenticalUnfenced=false,rawChangedDetected=false;
        boolean malformedPinDenied=false,timeoutDenied=false;
        boolean oldG21128StillPasses=false,noRewards=false;
        boolean zeroLeases=false;
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
            Seed owner=seed(world,ACCOUNT);
            publish(world,owner,strict,journal,commit);
            Pins pins=new Pins(r,ACCOUNT);

            negative(compare(r,pins,()->{}));
            clean=true;
            final boolean[] probes={false,false};
            negative(compare(r,pins,()->{
                child(root,ACCOUNT,"probe-busy");
                probes[0]=true;
                child(root,OTHER,"probe-quiet");
                probes[1]=true;
            }));
            foreignBusyDuringFullWindow=probes[0];
            unrelatedQuiet=probes[1];

            // A normal cooperating writer in a separate JVM cannot
            // enter WHILE our one continuous lifecycle lease is held.
            // The same OS lease cannot be acquired even for a no-op.
            Path ready=root.resolve("g21128-ready");
            Path release=root.resolve("g21128-release");
            Path log=root.resolve("g21129-holder.log");
            Process holder=spawn(root,ACCOUNT,"hold",log);
            try{
                long until=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(9L);
                while(!Files.exists(ready)&&
                      System.nanoTime()<until&&holder.isAlive())
                    Thread.sleep(12L);
                check(Files.exists(ready)&&holder.isAlive(),
                    "independent JVM never acquired its lease");
                final boolean[] executed={false};
                busyHolderDenied=denied(
                    ()->compare(r,pins,()->{executed[0]=true;}),
                    "G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT");
                callbackNotRun=!executed[0];
                Files.write(release,new byte[]{1});
                check(holder.waitFor(12L,TimeUnit.SECONDS)&&
                    holder.exitValue()==0,
                    "foreign holder did not release cleanly");
            }finally{
                if(holder.isAlive()){
                    holder.destroyForcibly();
                    holder.waitFor(3L,TimeUnit.SECONDS);
                }
            }
            negative(compare(r,pins,()->{}));

            callbackFailureReleased=denied(
                ()->compare(r,pins,()->{
                    throw new IOException("G21129_CALLBACK_ABORT");
                }),"G21129_CALLBACK_ABORT");
            child(root,ACCOUNT,"probe-quiet");

            // RAW/uncooperative writes bypass OS advisory file locks.
            // G21.130 now detects observable metadata drift even if
            // bytes are identical. This legacy negative test restores
            // the timestamp to demonstrate a genuine remaining gap.
            Path account=r.resolve(ACCOUNT);
            java.nio.file.attribute.FileTime originalModified=
                Files.getLastModifiedTime(account);
            negative(compare(r,pins,()->{
                child(root,ACCOUNT,"raw");
                Files.setLastModifiedTime(account,originalModified);
            }));
            rawIdenticalUnfenced=true;

            byte[] original=Files.readAllBytes(account);
            try{
                rawChangedDetected=denied(
                    ()->compare(r,pins,()->Files.write(
                        account,"G21129_ALTERED_CANONICAL"
                            .getBytes(StandardCharsets.US_ASCII))),
                    "G21.129 CONTINUOUS_PHYSICAL_SEVEN_PLANE_UNSAFE_NO_AUTHORITY");
            }finally{
                Files.write(account,original);
            }
            negative(compare(r,pins,()->{}));

            malformedPinDenied=denied(
                ()->MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .compareWithContinuousLease(
                        r,ACCOUNT,"not-a-digest",
                        pins.v1.portable,pins.v1.sha256,
                        pins.account,pins.prepared,pins.commit,
                        pins.v2.sha256,WAIT,()->{}),
                "G21.129 CONTINUOUS_PHYSICAL_SEVEN_PLANE_UNSAFE_NO_AUTHORITY");
            try{
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .compareWithContinuousLease(
                        r,ACCOUNT,pins.v3.sha256,pins.v1.portable,
                        pins.v1.sha256,pins.account,pins.prepared,
                        pins.commit,pins.v2.sha256,0L,()->{});
            }catch(IllegalArgumentException denied){
                timeoutDenied=true;
            }

            MailboxFourBoundResultLeavesArchiveV1.Comparison old=
                MailboxSevenPlaneCrossJvmGapGuardV1
                    .comparePinnedWithGuardedInterpass(
                        r,ACCOUNT,pins.v3.sha256,pins.v1.portable,
                        pins.v1.sha256,pins.account,pins.prepared,
                        pins.commit,pins.v2.sha256,WAIT,()->{});
            oldG21128StillPasses=old.samePinnedResultLeavesObserved&&
                old.fourWriterCohort.observedAgreement&&
                !old.allIntervalsFenced;
            noRewards=noGrant(owner);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21129_CONTINUOUS_PHYSICAL_DIAGNOSTICS"+
                " sevenPhysicalPlanesMatch="+clean+
                " foreignJvmBusyThroughoutHeldWindow="+
                    foreignBusyDuringFullWindow+
                " unrelatedAccountQuiet="+unrelatedQuiet+
                " foreignHeldLeaseRefused="+busyHolderDenied+
                " refusedCallbackNotRun="+callbackNotRun+
                " callbackErrorReleasedLease="+callbackFailureReleased+
                " rawSameBytesBypassesLease="+rawIdenticalUnfenced+
                " rawDifferentBytesRejected="+rawChangedDetected+
                " malformedIndependentPinDenied="+malformedPinDenied+
                " invalidTimeoutDenied="+timeoutDenied+
                " oldG21128ApiPreserved="+oldG21128StillPasses+
                " noLiveRewards="+noRewards+
                " zeroJvmLeases="+zeroLeases);
            check(clean&&foreignBusyDuringFullWindow&&
                unrelatedQuiet&&busyHolderDenied&&callbackNotRun&&
                callbackFailureReleased&&rawIdenticalUnfenced&&
                rawChangedDetected&&malformedPinDenied&&
                timeoutDenied&&oldG21128StillPasses&&noRewards&&
                zeroLeases,"continuous lease seven-plane acceptance");
            System.out.println("G21129_CONTINUOUS_PHYSICAL_SEVEN_PLANE_PASS"+
                " originalV1V2V3InsideCaptures=true"+
                " originalAccountPreparedCommitPinned=true"+
                " oneCrossJvmLifecycleLeaseHeld=true"+
                " richSemanticCohortWithinLease=false"+
                " rawWriterExclusion=false"+
                " signedProvenance=false historicalWriterCensus=false"+
                " atomicEpoch=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}

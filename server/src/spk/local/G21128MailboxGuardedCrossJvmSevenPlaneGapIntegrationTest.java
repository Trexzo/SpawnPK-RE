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
 * G21.128: REAL foreign JVM, G21.100 protected seven-plane
 * between-pass CALLBACK only. No all-interval fence or epoch.
 */
public final class G21128MailboxGuardedCrossJvmSevenPlaneGapIntegrationTest {
    private static final String ACCOUNT="g21128-main";
    private static final String OTHER="g21128-other";
    private static final long WAIT_MS=130L;

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.128 "+why);
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
    private static Seed seed(World w,String account){
        WorldPlayer player=new WorldPlayer();
        long g=w.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.128 foreign gap lease","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21128_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot m=player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,m));
        return new Seed(player,g,MailboxSettlementPostimagePlanner.plan(
            player,g,m));
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
        WorldPlayerPersistence.PreparedAccountReservation reservation=
            world.persistence().reservePreparedAccount(
                s.player,s.generation,s.proposal);
        WorldPlayerPersistence.TerminalPublicationEvidence terminal=
            world.persistence()
                .publishReservedTerminalStrictlyWithResultBinding(
                    reservation,s.proposal,s.terminal,strict)
                .get(15L,TimeUnit.SECONDS);
        check(terminal.strictReceipt.matchesSnapshot(s.terminal),
            "real terminal Receipt missing");
        disk.recordConfirmedDiskTerminalWithResultBinding(
            s.proposal,terminal.strictReceipt);
    }
    private static String sha(Path p)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(p));
        StringBuilder out=new StringBuilder(64);
        for(byte b:hash)
            out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static final class Pins{
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String v2,account,prepared,commit;
        Pins(FilePlayerRepository.PathResolver r,String name)
            throws Exception{
            v1=MailboxWriterEvidencePortableArchiveV1.capture(r,name);
            v3=MailboxFourBoundResultLeavesArchiveV1.capture(r,name);
            v2=MailboxWriterKindEvidenceV2.capture(r,name).sha256;
            account=sha(r.resolve(name));
            prepared=sha(new MailboxDurableIdempotencyIntentJournal(r)
                .journalPath(name));
            commit=sha(new MailboxGuardedDiskCommitRecord(r)
                .recordPath(name));
        }
    }
    private static MailboxFourBoundResultLeavesArchiveV1.Comparison
        guarded(FilePlayerRepository.PathResolver r,Pins p,
            MailboxWriterArchiveLiveReconciliationV1.BetweenPasses gap
        )throws IOException{
        return MailboxSevenPlaneCrossJvmGapGuardV1
            .comparePinnedWithGuardedInterpass(
                r,ACCOUNT,p.v3.sha256,p.v1.portable,p.v1.sha256,
                p.account,p.prepared,p.commit,p.v2,WAIT_MS,gap);
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
            !c.grantAuthorized,
            "protected callback manufactured archive/World authority");
    }
    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static Path marker(Path dir,String suffix){
        return dir.resolve("g21128-"+suffix);
    }
    private static void child(Path dir,String account,String mode)
        throws Exception{
        FilePlayerRepository.PathResolver r=resolver(dir);
        if("probe-busy".equals(mode)||"probe-quiet".equals(mode)){
            MailboxCrossJvmWriterLeaseProbeV1.State got=
                MailboxCrossJvmWriterLeaseProbeV1
                    .sample(r,account,WAIT_MS).state;
            MailboxCrossJvmWriterLeaseProbeV1.State expected=
                "probe-busy".equals(mode)
                    ?MailboxCrossJvmWriterLeaseProbeV1.State
                        .COOPERATING_LEASE_BUSY_UNKNOWN
                    :MailboxCrossJvmWriterLeaseProbeV1.State
                        .QUIET_AT_SAMPLE_ONLY;
            check(got==expected,"cross-JVM lock observation "+got);
            System.out.println("G21128_CHILD_"+mode.toUpperCase(Locale.ROOT));
            return;
        }
        if("ordinary".equals(mode)){
            MailboxPublicationWriterLifecycle.withWriter(
                r.resolve(account),()->Boolean.TRUE);
            System.out.println("G21128_CHILD_ORDINARY_UNENROLLED");
            return;
        }
        if("raw".equals(mode)){
            Path file=r.resolve(account);
            Files.write(file,Files.readAllBytes(file));
            System.out.println("G21128_CHILD_RAW_WRITE_UNFENCED");
            return;
        }
        if("hold".equals(mode)){
            MailboxPublicationWriterLifecycle.withWriter(
                r.resolve(account),()->{
                    Files.write(marker(dir,"ready"),new byte[]{1});
                    long deadline=System.nanoTime()+
                        TimeUnit.SECONDS.toNanos(15L);
                    while(!Files.exists(marker(dir,"release"))){
                        if(System.nanoTime()>deadline)
                            throw new IOException("G21128_HOLD_TIMEOUT");
                        try{Thread.sleep(12L);}
                        catch(InterruptedException e){
                            Thread.currentThread().interrupt();
                            throw new IOException("held lease interrupted",e);
                        }
                    }
                    System.out.println("G21128_CHILD_HELD_LEASE_RELEASED");
                    return Boolean.TRUE;
                });
            return;
        }
        throw new IllegalArgumentException("unknown child mode "+mode);
    }
    private static String fork(
        Path root,String account,String mode
    )throws IOException{
        Path log=root.resolve("g21128-"+mode+"-"+account+".log");
        Process proc=new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            G21128MailboxGuardedCrossJvmSevenPlaneGapIntegrationTest
                .class.getName(),"--child",root.toString(),account,mode)
            .redirectErrorStream(true).redirectOutput(log.toFile())
            .start();
        try{
            if(!proc.waitFor(24L,TimeUnit.SECONDS))
                throw new IOException("G21128 child timeout "+mode);
            String message=new String(Files.readAllBytes(log),
                StandardCharsets.UTF_8);
            String tag="G21128_CHILD_"+mode.toUpperCase(Locale.ROOT);
            if(proc.exitValue()!=0||!message.contains(tag))
                throw new IOException("G21128 child failed "+mode+
                    " exit="+proc.exitValue()+" log="+message);
            return message;
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IOException("child interrupted",e);
        }finally{
            if(proc.isAlive()){
                proc.destroyForcibly();
                try{proc.waitFor(5L,TimeUnit.SECONDS);}
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    private static boolean denied(
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses action,
        String marker
    ){
        try{action.run();return false;}
        catch(IOException err){
            return String.valueOf(err.getMessage()).contains(marker);
        }
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21128-guarded-gap-");
        boolean clean=false,busyInGap=false,otherAccountQuiet=false;
        boolean foreignAfterGapUnrecorded=false,busyGuardRefused=false;
        boolean callbackNotRunOnBusy=false,callbackFailureReleased=false;
        boolean foreignRawBypasses=false,rawBytesSame=false;
        boolean invalidTimeoutDenied=false,noRewards=false,zeroLeases=false;
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
            Seed real=seed(world,ACCOUNT);
            publish(world,real,strict,journal,disk);
            Pins pins=new Pins(r,ACCOUNT);
            noAuthority(guarded(r,pins,()->{}));
            clean=true;
            final boolean[] inside={false,false};
            noAuthority(guarded(r,pins,()->{
                fork(root,ACCOUNT,"probe-busy");
                inside[0]=true;
                fork(root,OTHER,"probe-quiet");
                inside[1]=true;
            }));
            busyInGap=inside[0];
            otherAccountQuiet=inside[1];
            // After release, a foreign cooperative DEFAULT writer can
            // enter without v1/v2/v3 evidence: NOT future protection.
            fork(root,ACCOUNT,"ordinary");
            noAuthority(guarded(r,pins,()->{}));
            foreignAfterGapUnrecorded=
                MailboxWriterAttemptEvidenceV1.inspect(r,ACCOUNT)
                    .recordCount==8&&
                MailboxFourBoundResultLeavesArchiveV1
                    .capture(r,ACCOUNT).sha256.equals(pins.v3.sha256)&&
                MailboxWriterKindEvidenceV2.capture(
                    r,ACCOUNT).sha256.equals(pins.v2);

            // Start foreign-held lease on same account. Test the
            // SAME entry point as the gap callback, independent of
            // G21.125's earlier separately locked capture passes.
            Path log=root.resolve("g21128-hold.log");
            Process holding=new ProcessBuilder(javaBinary(),"-cp",
                System.getProperty("java.class.path"),
                G21128MailboxGuardedCrossJvmSevenPlaneGapIntegrationTest
                    .class.getName(),"--child",root.toString(),
                ACCOUNT,"hold")
                .redirectErrorStream(true).redirectOutput(log.toFile())
                .start();
            try{
                long deadline=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(9L);
                while(!Files.exists(marker(root,"ready"))&&
                      System.nanoTime()<deadline&&holding.isAlive()){
                    Thread.sleep(15L);
                }
                check(Files.exists(marker(root,"ready"))&&
                    holding.isAlive(),"foreign holder not ready");
                final boolean[] called={false};
                busyGuardRefused=denied(
                    ()->MailboxSevenPlaneCrossJvmGapGuardV1
                        .withGuardedCallback(
                            r.resolve(ACCOUNT),WAIT_MS,()->{
                                called[0]=true;
                                return Boolean.TRUE;
                            }),
                    "G21.73 RECOVERY_PUBLICATION_BUSY_NO_GRANT");
                callbackNotRunOnBusy=!called[0];
            }finally{
                Files.write(marker(root,"release"),new byte[]{1});
                if(!holding.waitFor(12L,TimeUnit.SECONDS)){
                    holding.destroyForcibly();
                    holding.waitFor(5L,TimeUnit.SECONDS);
                }
                check(holding.exitValue()==0,"foreign holder did not release");
            }
            noAuthority(guarded(r,pins,()->{}));

            callbackFailureReleased=denied(
                ()->guarded(r,pins,()->{
                    throw new IOException("G21128_CALLBACK_ABORT");
                }),"G21128_CALLBACK_ABORT")&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;

            invalidTimeoutDenied=false;
            try{
                MailboxSevenPlaneCrossJvmGapGuardV1
                    .withGuardedCallback(
                        r.resolve(ACCOUNT),0L,()->Boolean.TRUE);
            }catch(IllegalArgumentException expected){
                invalidTimeoutDenied=true;
            }

            // Advisory leases cannot stop raw/uncooperative file I/O.
            // Same bytes keep independent content pins unchanged.
            String prior=sha(r.resolve(ACCOUNT));
            foreignRawBypasses=
                MailboxSevenPlaneCrossJvmGapGuardV1.withGuardedCallback(
                    r.resolve(ACCOUNT),WAIT_MS,()->{
                        fork(root,ACCOUNT,"raw");
                        return Boolean.TRUE;
                    });
            rawBytesSame=prior.equals(sha(r.resolve(ACCOUNT)));
            noAuthority(guarded(r,pins,()->{}));

            noRewards=noGrant(real);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21128_GUARDED_GAP_DIAGNOSTICS"+
                " cleanSevenPlaneMatch="+clean+
                " foreignJvmSeesBusyDuringGap="+busyInGap+
                " otherAccountQuiet="+otherAccountQuiet+
                " defaultForeignWriterAfterGapUnenrolled="+
                    foreignAfterGapUnrecorded+
                " foreignHeldLeaseRefused="+busyGuardRefused+
                " refusedCallbackNeverExecuted="+callbackNotRunOnBusy+
                " failedCallbackReleasesGuardAndWatch="+
                    callbackFailureReleased+
                " foreignRawWriterNotBlocked="+foreignRawBypasses+
                " rawIdenticalBytesStillPinned="+rawBytesSame+
                " invalidBoundedTimeoutDenied="+invalidTimeoutDenied+
                " noLiveRewards="+noRewards+
                " zeroJvmLeaseWatchLeaks="+zeroLeases);
            check(clean&&busyInGap&&otherAccountQuiet&&
                foreignAfterGapUnrecorded&&busyGuardRefused&&
                callbackNotRunOnBusy&&callbackFailureReleased&&
                foreignRawBypasses&&rawBytesSame&&
                invalidTimeoutDenied&&noRewards&&zeroLeases,
                "cross-JVM guarded callback negative-only contract");
            System.out.println("G21128_CROSS_JVM_INTERPASS_GUARD_PASS"+
                " guardedCallbackOnly=true"+
                " foreignCooperatingContentionVisible=true"+
                " unrelatedAccountIndependent=true"+
                " allObservationIntervalsFenced=false"+
                " priorFutureWritersFenced=false"+
                " rawWriterFenced=false signedProvenance=false"+
                " atomicEpoch=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
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
        if(args.length==4&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2],args[3]);return;
        }
        check(args.length==0,"unexpected arguments");
        run();
    }
}

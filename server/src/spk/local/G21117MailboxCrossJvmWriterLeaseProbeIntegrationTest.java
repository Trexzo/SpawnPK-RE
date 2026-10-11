package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.117: genuine OTHER Java process using the shared G21.100
 * OS advisory lease. Active contention is observable; prior writer
 * attempts, crashed epochs, raw writes and historical populations
 * remain entirely unproven.
 */
public final class G21117MailboxCrossJvmWriterLeaseProbeIntegrationTest {
    private static final String ACCOUNT="g21117-main";
    private static final String OTHER="g21117-other";
    private static final String UNSAFE="g21117-unsafe";
    private static final long PROBE_MS=140L;

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.117 "+reason);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long gen,
             MailboxSettlementPostimagePlanner.Proposal plan){
            player=p;generation=gen;proposal=plan;
            terminal=MailboxAtomicTerminalSnapshot.compose(plan);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.117 cross-JVM lease proof","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21117_TEST_ONLY"));
        MailboxRewardDeliveryService.Snapshot s=player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,s));
        return new Seed(player,gen,
            MailboxSettlementPostimagePlanner.plan(player,gen,s));
    }
    private static boolean noGrant(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static String sha(Path p)throws Exception{
        byte[] data=java.security.MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(p));
        StringBuilder out=new StringBuilder(64);
        for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static void noAuthority(
        MailboxCrossJvmWriterLeaseProbeV1.Sample s
    ){
        check(!s.localAndCrossJvmHistoryKnown&&
            !s.historicalWritersCovered&&!s.futureWritersExcluded&&
            !s.uncooperativeWritersExcluded&&
            !s.inactivePriorWriterProved&&
            !s.generationBoundaryEstablished&&
            !s.crashDurabilityProved&&
            !s.archiveRolloverAuthorized&&
            !s.recordPruningAuthorized&&
            !s.cleanupAuthorized&&!s.unlinkAuthorized&&
            !s.grantAuthorized&&!s.replayAuthorized&&
            !s.sessionAuthorized&&!s.releaseAuthorized&&
            !s.clientAckAuthorized,
            "sample became archive or reward authority");
    }
    private static MailboxCrossJvmWriterLeaseProbeV1.Sample sample(
        FilePlayerRepository.PathResolver paths,String account
    )throws IOException{
        MailboxCrossJvmWriterLeaseProbeV1.Sample s=
            MailboxCrossJvmWriterLeaseProbeV1
                .sample(paths,account,PROBE_MS);
        noAuthority(s);
        return s;
    }
    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static Path signal(Path root,String tag,String suffix){
        return root.resolve("g21117-"+tag+"."+suffix);
    }
    private static final class Fork implements AutoCloseable {
        final Process process;
        final Path root;
        final String tag,mode;
        private Fork(Process p,Path d,String t,String m){
            process=p;root=d;tag=t;mode=m;
        }
        void awaitReady()throws Exception{
            Path ready=signal(root,tag,"ready");
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(9L);
            while(!Files.exists(ready)&&System.nanoTime()<deadline){
                if(!process.isAlive())break;
                Thread.sleep(15L);
            }
            check(Files.exists(ready)&&process.isAlive(),
                "child did not acquire held OS writer lease mode="+mode+
                " exit="+(process.isAlive()?"active":process.exitValue()));
        }
        void release(int expected)throws Exception{
            Files.write(signal(root,tag,"release"),new byte[]{1});
            check(process.waitFor(12L,TimeUnit.SECONDS),
                "held child failed to exit mode="+mode);
            check(process.exitValue()==expected,
                "held child unexpected exit "+process.exitValue()+
                " mode="+mode);
        }
        @Override public void close(){
            if(process.isAlive()){
                process.destroyForcibly();
                try{process.waitFor(3L,TimeUnit.SECONDS);}
                catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    private static Fork spawn(
        Path root,String account,String mode,String tag
    )throws Exception{
        Process child=new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            G21117MailboxCrossJvmWriterLeaseProbeIntegrationTest
                .class.getName(),
            "--child",root.toString(),account,mode,tag)
            .redirectErrorStream(true)
            .redirectOutput(signal(root,tag,"log").toFile())
            .start();
        return new Fork(child,root,tag,mode);
    }
    private static void child(
        Path root,String account,String mode,String tag
    )throws Exception{
        FilePlayerRepository.PathResolver paths=resolver(root);
        Path selected=paths.resolve(account);
        MailboxPublicationWriterLifecycle.withWriter(selected,()->{
            if("noop".equals(mode)){
                System.out.println(
                    "G21117_FOREIGN_COMPLETED_NOOP_LEASE");
                System.out.flush();
                return Boolean.TRUE;
            }
            Files.write(signal(root,tag,"ready"),new byte[]{1});
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(12L);
            while(!Files.exists(signal(root,tag,"release"))){
                if(System.nanoTime()>deadline)
                    throw new IOException(
                        "G21117_CHILD_LEASE_RELEASE_TIMEOUT");
                try{Thread.sleep(15L);}
                catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException(
                        "G21117_CHILD_LEASE_INTERRUPTED",interrupted);
                }
            }
            if("crash".equals(mode)){
                System.out.println(
                    "G21117_FOREIGN_JVM_CRASH_WITH_HELD_LEASE");
                System.out.flush();
                Runtime.getRuntime().halt(137);
                throw new AssertionError("JVM halt unexpectedly returned");
            }
            if(!"hold".equals(mode))
                throw new IOException("G21117_INVALID_CHILD_MODE");
            System.out.println(
                "G21117_FOREIGN_JVM_HOLD_RELEASED_CLEAN");
            System.out.flush();
            return Boolean.TRUE;
        });
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21117-cross-jvm-");
        boolean cleanQuiet=false,activeForeignBusy=false;
        boolean otherAccountIsolated=false,releaseQuiet=false;
        boolean completeAttemptInvisible=false,localWatchInvisible=false;
        boolean crashedHeldLeaseWasBusy=false,postCrashQuiet=false;
        boolean maliciousSymlinkRejected=false,invalidTimeoutRejected=false;
        boolean fivePlaneUnchanged=false,zeroLeaks=false,noLiveGrant=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver(root)))){
            world.start();
            FilePlayerRepository.PathResolver paths=resolver(root);
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(paths);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(paths);
            MailboxGuardedDiskCommitRecord disk=
                new MailboxGuardedDiskCommitRecord(paths);
            Seed s=seed(world,ACCOUNT);
            strict.saveStrictWithWriterEvidence(
                s.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(s.proposal);
            WorldPlayerPersistence.PreparedAccountReservation token=
                world.persistence().reservePreparedAccount(
                    s.player,s.generation,s.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence published=
                world.persistence()
                    .publishReservedTerminalStrictlyWithWriterEvidence(
                        token,s.proposal,s.terminal,strict)
                    .get(15L,TimeUnit.SECONDS);
            disk.recordConfirmedDiskTerminalWithWriterEvidence(
                s.proposal,published.strictReceipt);

            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=
                MailboxWriterEvidencePortableArchiveV1
                    .capture(paths,ACCOUNT);
            MailboxWriterKindEvidenceV2.Snapshot v2=
                MailboxWriterKindEvidenceV2.capture(paths,ACCOUNT);
            String accountPin=sha(paths.resolve(ACCOUNT));
            String journalPin=sha(journal.journalPath(ACCOUNT));
            String commitPin=sha(disk.recordPath(ACCOUNT));
            MailboxCrossJvmWriterLeaseProbeV1.State quiet=
                MailboxCrossJvmWriterLeaseProbeV1.State
                    .QUIET_AT_SAMPLE_ONLY;
            MailboxCrossJvmWriterLeaseProbeV1.State busy=
                MailboxCrossJvmWriterLeaseProbeV1.State
                    .COOPERATING_LEASE_BUSY_UNKNOWN;
            cleanQuiet=sample(paths,ACCOUNT).state==quiet;
            // The foreign JVM owns the OS file lease, NOT the local
            // watch registry. The bounded probe detects the live lease.
            try(Fork child=spawn(root,ACCOUNT,"hold","hold")){
                child.awaitReady();
                try(MailboxLocalWriterAttemptWatchV1.Scope scope=
                        MailboxLocalWriterAttemptWatchV1.arm(
                            paths.resolve(ACCOUNT))){
                    activeForeignBusy=sample(paths,ACCOUNT).state==busy&&
                        !scope.sawAttempt();
                    otherAccountIsolated=sample(paths,OTHER).state==quiet;
                }
                child.release(0);
            }
            releaseQuiet=sample(paths,ACCOUNT).state==quiet;

            // A completely finished foreign writer acquires/releases
            // the lease DURING the unlocked gap. Both instantaneous
            // samples are quiet, even with same-process watch armed.
            try(MailboxLocalWriterAttemptWatchV1.Scope scope=
                    MailboxLocalWriterAttemptWatchV1.arm(
                        paths.resolve(ACCOUNT))){
                MailboxCrossJvmWriterLeaseProbeV1.TwoSamples pair=
                    MailboxCrossJvmWriterLeaseProbeV1.sampleAround(
                        paths,ACCOUNT,PROBE_MS,()->{
                            try(Fork f=spawn(
                                    root,ACCOUNT,"noop","noop")){
                                try{
                                    check(f.process.waitFor(
                                        10L,TimeUnit.SECONDS),
                                        "foreign noop timeout");
                                    check(f.process.exitValue()==0,
                                        "foreign no-op JVM failed");
                                }catch(InterruptedException e){
                                    Thread.currentThread().interrupt();
                                    throw new IOException(
                                        "foreign no-op interrupted",e);
                                }catch(Exception e){
                                    throw new IOException(
                                        "foreign no-op process failed",e);
                                }
                            }catch(IOException e){throw e;}
                            catch(Exception e){
                                throw new IOException(
                                    "could not launch no-op JVM",e);
                            }
                        });
                completeAttemptInvisible=pair.before.state==quiet&&
                    pair.after.state==quiet&&!pair.observedBusy&&
                    !pair.entireIntervalQuiescentProved&&
                    !pair.allWriterGenerationsFenced;
                localWatchInvisible=!scope.sawAttempt();
            }
            // Old five-plane content remains byte-identical because
            // foreign writer acquired/released the lease without I/O.
            MailboxWriterArchiveLiveReconciliationV1.Report witness=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedTypedRecoveryLeaves(
                        paths,ACCOUNT,v1.portable,v1.sha256,
                        accountPin,journalPin,commitPin,v2.sha256);
            fivePlaneUnchanged=witness.pinnedCanonicalBytesMatched&&
                witness.pinnedTypedWriterKindBytesMatched&&
                witness.pinnedRecoverySidecarBytesMatched&&
                !witness.allWriterVersionsFenced;

            // Real hard JVM termination while owning the lifecycle
            // lease releases it; v1/v2 writer history is NOT updated.
            try(Fork child=spawn(root,ACCOUNT,"crash","crash")){
                child.awaitReady();
                crashedHeldLeaseWasBusy=sample(paths,ACCOUNT).state==busy;
                child.release(137);
            }
            postCrashQuiet=sample(paths,ACCOUNT).state==quiet&&
                MailboxWriterAttemptEvidenceV1.inspect(paths,ACCOUNT)
                    .recordCount==8&&
                MailboxWriterKindEvidenceV2.inspect(paths,ACCOUNT)
                    .typedBegins==4;

            invalidTimeoutRejected=false;
            try{
                MailboxCrossJvmWriterLeaseProbeV1.sample(
                    paths,ACCOUNT,0L);
            }catch(IllegalArgumentException expected){
                invalidTimeoutRejected=true;
            }
            try{
                MailboxCrossJvmWriterLeaseProbeV1.sample(
                    paths,ACCOUNT,1501L);
                invalidTimeoutRejected=false;
            }catch(IllegalArgumentException expected){}

            // A redirected OS lock leaf is a forensic safety error,
            // NOT a valid BUSY/QUIET lifecycle sample.
            Path unsafeLock=MailboxAccountPublicationCoordinator.lockPath(
                MailboxPublicationWriterLifecycle.domain(
                    paths.resolve(UNSAFE)));
            try{
                Files.createSymbolicLink(
                    unsafeLock,paths.resolve(ACCOUNT));
                try{
                    sample(paths,UNSAFE);
                }catch(IOException failure){
                    maliciousSymlinkRejected=String.valueOf(
                        failure.getMessage()).contains(
                            "G21.77 RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT");
                }
            }catch(UnsupportedOperationException unsupported){
                // On filesystems without symbolic links the test only
                // verifies the existing NOFOLLOW ancestry code.
                maliciousSymlinkRejected=true;
            }catch(java.nio.file.FileSystemException deniedLink){
                // Windows can forbid unprivileged link creation.
                maliciousSymlinkRejected=true;
            }
            noLiveGrant=noGrant(s);
            zeroLeaks=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            System.out.println("G21117_CROSS_JVM_LEASE_DIAGNOSTICS"+
                " quietBeforeForeign="+cleanQuiet+
                " heldForeignLeaseDetected="+activeForeignBusy+
                " unrelatedAccountIsolated="+otherAccountIsolated+
                " releasedForeignLeaseQuiet="+releaseQuiet+
                " completedForeignNoopMissed="+completeAttemptInvisible+
                " localWatchDoesNotSeeForeign="+localWatchInvisible+
                " heldCrashLeaseBusyBeforeHalt="+
                    crashedHeldLeaseWasBusy+
                " crashedLeaseQuietAfterHalt="+postCrashQuiet+
                " unchangedFivePlaneAfterForeignNoop="+
                    fivePlaneUnchanged+
                " unsafeSymlinkLockDenied="+maliciousSymlinkRejected+
                " invalidBoundedTimeoutDenied="+invalidTimeoutRejected+
                " noLiveGrant="+noLiveGrant+
                " zeroJvmLeaseLeaks="+zeroLeaks);
            check(cleanQuiet&&activeForeignBusy&&
                otherAccountIsolated&&releaseQuiet&&
                completeAttemptInvisible&&localWatchInvisible&&
                crashedHeldLeaseWasBusy&&postCrashQuiet&&
                fivePlaneUnchanged&&maliciousSymlinkRejected&&
                invalidTimeoutRejected&&noLiveGrant&&zeroLeaks,
                "cross-JVM active lease visibility vs history gaps");
            System.out.println("G21117_CROSS_JVM_ACTIVE_LEASE_PASS"+
                " liveCooperatingCrossJvmVisible=true"+
                " finishedAttemptHistory=false"+
                " crashedWriterAttemptHistory=false"+
                " rawWriterVisibility=false"+
                " crossJvmEpochFence=false writerVersionCensus=false"+
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
        if(args.length==5&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2],args[3],args[4]);
            return;
        }
        check(args.length==0,"unexpected test arguments");
        run();
    }
}

package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.113 — opt-in real G21.66 World FIFO terminal strict publication
 * with v1 forced evidence; default world save and grants remain OFF.
 */
public final class G21113MailboxWorldTerminalRecordedWriterIntegrationTest {
    private static final String NORMAL="g21113-normal";
    private static final String OPTIN="g21113-optin";
    private static final String FULL="g21113-full";
    private static final String FAIL="g21113-fail";
    private static final String CRASH="g21113-crash";
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.113 "+reason);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final long generation;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,long g,
             MailboxSettlementPostimagePlanner.Proposal plan){
            player=p;generation=g;proposal=plan;
            terminal=MailboxAtomicTerminalSnapshot.compose(plan);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String id=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.113 World FIFO evidence fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21113_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            owner.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,selected));
        return new Seed(owner,generation,MailboxSettlementPostimagePlanner
            .plan(owner,generation,selected));
    }
    private static WorldPlayerPersistence.PreparedAccountReservation reserve(
        World world,Seed seed
    ){
        return world.persistence().reservePreparedAccount(
            seed.player,seed.generation,seed.proposal);
    }
    private static WorldPlayerPersistence.TerminalPublicationEvidence
        terminal(
            World world,Seed seed,
            StrictDurablePlayerSnapshotWriter writer,boolean recorded
        )throws Exception{
        WorldPlayerPersistence.PreparedAccountReservation token=
            reserve(world,seed);
        if(recorded)
            return world.persistence()
                .publishReservedTerminalStrictlyWithWriterEvidence(
                    token,seed.proposal,seed.terminal,writer)
                .get(15,TimeUnit.SECONDS);
        return world.persistence().publishReservedTerminalStrictly(
            token,seed.proposal,seed.terminal,writer)
            .get(15,TimeUnit.SECONDS);
    }
    private static Path[] records(Path root,String account)
        throws IOException{
        try(Stream<Path> all=Files.list(root)){
            String prefix=account+
                ".properties.g21103-writer-attempt-";
            return all.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(Path root,String account)
        throws IOException{
        Path[] leaves=records(root,account);
        String[] list=new String[leaves.length];
        for(int i=0;i<leaves.length;i++)
            list[i]=leaves[i].getFileName()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(leaves[i]));
        return list;
    }
    private static MailboxWriterAttemptEvidenceV1.Report report(
        FilePlayerRepository.PathResolver paths,String account
    )throws IOException{
        MailboxWriterAttemptEvidenceV1.Report r=
            MailboxWriterAttemptEvidenceV1.inspect(paths,account);
        check(!r.historicalEvidencePruningAllowed&&
            !r.completeWriterPopulationKnown&&
            !r.everyHistoricalGenerationParticipates&&
            !r.crashEpochSealed&&!r.uncooperativeWritersExcluded&&
            !r.futureWritersExcluded&&!r.unlinkAuthorized&&
            !r.cleanupAuthorized&&!r.accountMutationAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.restartAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,"writer ledger conferred authority");
        return r;
    }
    private static boolean noReward(Seed s){
        return s.player.bank().inventorySlots()==0&&
            s.player.mailbox().get(s.proposal.messageId).claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
    }
    private static void completedWithoutAuthority(
        WorldPlayerPersistence.TerminalPublicationEvidence e,Seed s
    ){
        check(e.fileOperationConfirmed&&
            e.strictReceipt.matchesSnapshot(s.terminal)&&
            !e.grantAuthorized&&!e.replayAuthorized&&
            !e.rollbackAuthorized&&!e.releaseAuthorized&&
            !e.clientAckAuthorized,"terminal evidence authorized reward");
    }
    private static boolean exceptionHas(Throwable t,String code){
        for(Throwable x=t;x!=null;x=x.getCause())
            if(String.valueOf(x.getMessage()).contains(code))return true;
        return false;
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path root,String mode)throws Exception{
        FilePlayerRepository.PathResolver r=paths(root);
        if("observe".equals(mode)){
            Path account=r.resolve(CRASH);
            byte[] before=Files.readAllBytes(account);
            String[] leaves=fingerprint(root,CRASH);
            MailboxWriterAttemptEvidenceV1.Report v=report(r,CRASH);
            boolean invoked[]={false};
            boolean denied=false;
            try{
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,CRASH,()->{invoked[0]=true;return Boolean.TRUE;});
            }catch(IOException error){
                denied=exceptionHas(error,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            }
            Optional<PlayerSnapshot> disk=
                new FilePlayerRepository(r).load(CRASH);
            boolean terminalImage=disk.isPresent()&&
                MailboxAtomicTerminalSnapshot.inspect(disk.get()).state==
                    MailboxAtomicTerminalSnapshot.State
                        .COHERENT_TERMINAL_NO_GRANT;
            check(terminalImage&&denied&&!invoked[0]&&
                v.started==1&&v.completed==0&&v.recordCount==1&&
                v.status==MailboxWriterAttemptEvidenceV1.Status
                    .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                leaves.length==1&&leaves[0].contains(".begin|")&&
                Arrays.equals(before,Files.readAllBytes(account))&&
                Arrays.equals(leaves,fingerprint(root,CRASH))&&
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0,
                "new JVM failed to quarantine interrupted World file");
            System.out.println(
                "G21113_FRESH_JVM_TERMINAL_VETO_PASS"+
                " exactTerminalOnDisk=true beginOnly=true"+
                " completed=false replay=false cleanup=false");
            return;
        }
        if("halt".equals(mode)){
            try(World world=World.isolatedForTest(
                    60000L,new FilePlayerRepository(r))){
                world.start();
                Seed seed=seed(world,CRASH);
                StrictDurablePlayerSnapshotWriter writer=
                    new StrictDurablePlayerSnapshotWriter(r,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .AFTER_DIRECTORY_FORCE){
                            System.out.println(
                                "G21113_REAL_WORLD_TERMINAL_FORCE_BEFORE_HALT"+
                                " exit=132 durableBegin=true");
                            System.out.flush();
                            Runtime.getRuntime().halt(132);
                            throw new AssertionError("halt returned");
                        }
                    });
                new StrictDurablePlayerSnapshotWriter(r)
                    .saveStrict(seed.proposal.preparedPreimage);
                new MailboxDurableIdempotencyIntentJournal(r)
                    .publishPreparedIntent(seed.proposal);
                terminal(world,seed,writer,true);
                throw new AssertionError("expected true JVM halt");
            }
        }
        throw new AssertionError("unknown child mode");
    }
    private static void fork(
        Path root,String mode,int expected,String marker
    )throws Exception{
        Path log=root.resolve("g21113-"+mode+".log");
        Process p=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21113MailboxWorldTerminalRecordedWriterIntegrationTest
                .class.getName(),"--child",root.toString(),mode)
            .redirectErrorStream(true).redirectOutput(log.toFile())
            .start();
        try{
            check(p.waitFor(30,TimeUnit.SECONDS),
                "child timed out "+mode);
        }finally{
            if(p.isAlive()){
                p.destroyForcibly();
                p.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==expected&&output.contains(marker),
            "fork "+mode+" exit="+p.exitValue()+" "+output);
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21113-world-fifo-");
        boolean defaultUnenrolled=false,recordedTerminal=false;
        boolean fourPaired=false,allFourSemantic=false;
        boolean fullBlockedBeforeMove=false,failureBeginOnly=false;
        boolean hardHaltPreservesUnmatched=false;
        boolean noGrant=false,zeroLeases=false;
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths(root)))){
            world.start();
            FilePlayerRepository.PathResolver r=paths(root);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(r);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(r);
            MailboxGuardedDiskCommitRecord commit=
                new MailboxGuardedDiskCommitRecord(r);

            Seed baseline=seed(world,NORMAL);
            writer.saveStrict(baseline.proposal.preparedPreimage);
            journal.publishPreparedIntent(baseline.proposal);
            WorldPlayerPersistence.TerminalPublicationEvidence ordinary=
                terminal(world,baseline,writer,false);
            completedWithoutAuthority(ordinary,baseline);
            defaultUnenrolled=report(r,NORMAL).recordCount==0&&
                noReward(baseline);

            Seed optin=seed(world,OPTIN);
            writer.saveStrictWithWriterEvidence(
                optin.proposal.preparedPreimage);
            journal.publishPreparedIntentWithWriterEvidence(
                optin.proposal);
            int beforeWorldTerminal=report(r,OPTIN).recordCount;
            WorldPlayerPersistence.TerminalPublicationEvidence saved=
                terminal(world,optin,writer,true);
            completedWithoutAuthority(saved,optin);
            int afterWorldTerminal=report(r,OPTIN).recordCount;
            commit.recordConfirmedDiskTerminalWithWriterEvidence(
                optin.proposal,saved.strictReceipt);
            MailboxGuardedDiskCommitRecord.Observation observed=
                commit.inspect(OPTIN);
            MailboxWriterAttemptEvidenceV1.Report paired=report(r,OPTIN);
            fourPaired=beforeWorldTerminal==4&&
                afterWorldTerminal==6&&paired.recordCount==8&&
                paired.started==4&&paired.completed==4&&
                paired.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP;
            allFourSemantic=observed.status==
                MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                observed.recordValidated&&
                saved.strictReceipt.matchesSnapshot(optin.terminal)&&
                noReward(optin)&&
                !observed.grantAuthorized&&!observed.replayAuthorized&&
                !observed.clientAckAuthorized;
            recordedTerminal=afterWorldTerminal-beforeWorldTerminal==2;

            // Exhaust all 32 versioned leaves BEFORE issuing actual
            // reserved World terminal publication. No file may move.
            Seed full=seed(world,FULL);
            writer.saveStrict(full.proposal.preparedPreimage);
            journal.publishPreparedIntent(full.proposal);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            byte[] beforeFull=Files.readAllBytes(r.resolve(FULL));
            String[] beforeFullV1=fingerprint(root,FULL);
            boolean limitDenied=false;
            try{
                terminal(world,full,writer,true);
            }catch(ExecutionException failed){
                limitDenied=exceptionHas(failed,
                    "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL");
            }
            fullBlockedBeforeMove=limitDenied&&
                Arrays.equals(beforeFull,Files.readAllBytes(
                    r.resolve(FULL)))&&
                Arrays.equals(beforeFullV1,fingerprint(root,FULL))&&
                !Files.exists(commit.recordPath(FULL),
                    LinkOption.NOFOLLOW_LINKS)&&noReward(full);

            // Real forced failure before move: v1 BEGIN is durable,
            // COMPLETE absent; future opt-in attempts must fail closed.
            Seed early=seed(world,FAIL);
            writer.saveStrict(early.proposal.preparedPreimage);
            journal.publishPreparedIntent(early.proposal);
            byte[] prepared=Files.readAllBytes(r.resolve(FAIL));
            StrictDurablePlayerSnapshotWriter cut=
                new StrictDurablePlayerSnapshotWriter(r,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE)
                        throw new IOException("G21113_INJECTED_BEFORE_MOVE");
                });
            boolean faultDenied=false;
            try{
                terminal(world,early,cut,true);
            }catch(ExecutionException failed){
                faultDenied=exceptionHas(failed,
                    "G21113_INJECTED_BEFORE_MOVE");
            }
            MailboxWriterAttemptEvidenceV1.Report incomplete=
                report(r,FAIL);
            boolean crossKindDenied=false;
            try{
                commit.recordConfirmedDiskTerminalWithWriterEvidence(
                    early.proposal,saved.strictReceipt);
            }catch(IOException failed){
                // Foreign strict receipt is rejected even earlier.
                crossKindDenied=exceptionHas(failed,
                    "G21.86 RECEIPT_TERMINAL_OR_PATH_MISMATCH_NO_GRANT");
            }
            failureBeginOnly=faultDenied&&crossKindDenied&&
                incomplete.started==1&&incomplete.completed==0&&
                incomplete.recordCount==1&&
                Arrays.equals(prepared,Files.readAllBytes(r.resolve(FAIL)));
            boolean v1Blocked=false;
            try{
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FAIL,()->Boolean.TRUE);
            }catch(IOException denied){
                v1Blocked=exceptionHas(denied,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
            }
            failureBeginOnly&=v1Blocked;

            fork(root,"halt",132,
                "G21113_REAL_WORLD_TERMINAL_FORCE_BEFORE_HALT");
            byte[] afterCrash=Files.readAllBytes(r.resolve(CRASH));
            String[] crashRecords=fingerprint(root,CRASH);
            fork(root,"observe",0,
                "G21113_FRESH_JVM_TERMINAL_VETO_PASS");
            hardHaltPreservesUnmatched=
                afterCrash.length>0&&crashRecords.length==1&&
                crashRecords[0].contains(".begin|")&&
                Arrays.equals(afterCrash,Files.readAllBytes(
                    r.resolve(CRASH)))&&
                Arrays.equals(crashRecords,fingerprint(root,CRASH));
            noGrant=noReward(baseline)&&noReward(optin)&&
                noReward(full)&&noReward(early);
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            check(defaultUnenrolled&&recordedTerminal&&fourPaired&&
                allFourSemantic&&fullBlockedBeforeMove&&
                failureBeginOnly&&hardHaltPreservesUnmatched&&
                noGrant&&zeroLeases,
                "real World FIFO terminal v1 evidence regression");
            System.out.println("G21113_WORLD_FIFO_TERMINAL_DIAGNOSTICS"+
                " defaultWorldUnenrolled="+defaultUnenrolled+
                " actualWorldTerminalPaired="+recordedTerminal+
                " fourOptInWritesEightLeaves="+fourPaired+
                " matchingDiskCommitNoLiveGrant="+allFourSemantic+
                " fullLedgerBlockedBeforeMove="+fullBlockedBeforeMove+
                " preMoveFaultLeavesBeginOnly="+failureBeginOnly+
                " hardJVMCrashKeepsUnmatchedBegin="+
                    hardHaltPreservesUnmatched+
                " independentJVMQuarantine="+hardHaltPreservesUnmatched+
                " liveOwnerNoGrant="+noGrant+" zeroJvmLeases="+zeroLeases);
            System.out.println("G21113_OPTIN_WORLD_TERMINAL_WRITER_PASS"+
                " realWorldFifo=true reservationKept=true"+
                " beforeAfterOwnerChecksKept=true walFenceKept=true"+
                " defaultHotpathUnenrolled=true"+
                " historicalWritersFenced=false rollover=false"+
                " pruning=false cleanup=false unlink=false"+
                " grant=false replay=false release=false session=false"+
                " ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            child(Paths.get(args[1]).toAbsolutePath().normalize(),
                args[2]);return;
        }
        check(args.length==0,"unexpected args");
        run();
    }
}

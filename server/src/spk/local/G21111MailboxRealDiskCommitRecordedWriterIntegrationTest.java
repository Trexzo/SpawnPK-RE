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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.111: real G21.86 COMMIT record opt-in v1 writer attempt,
 * not a live Mailbox settlement, cleanup or epoch credential.
 */
public final class G21111MailboxRealDiskCommitRecordedWriterIntegrationTest {
    private static final String DEFAULT="g21111-default";
    private static final String PAIRED="g21111-paired";
    private static final String FAILURE="g21111-failure";
    private static final String FULL="g21111-full";
    private static final String CRASH="g21111-crash";

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.111 "+reason);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return a->root.resolve(a+".properties");
    }
    private static final class Seed {
        final WorldPlayer player;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer w,MailboxSettlementPostimagePlanner.Proposal p){
            player=w;proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }
    private static Seed seed(World world,String account){
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,account);
        String id=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            id,"G21.111 disk evidence fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G21111_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot s=
            player.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,s));
        return new Seed(player,MailboxSettlementPostimagePlanner.plan(
            player,generation,s));
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt setup(
        Seed s,FilePlayerRepository.PathResolver r
    )throws IOException{
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(r);
        writer.saveStrict(s.proposal.preparedPreimage);
        new MailboxDurableIdempotencyIntentJournal(r)
            .publishPreparedIntent(s.proposal);
        return writer.saveStrictTerminalForWorld(
            s.terminal,r.resolve(s.proposal.account),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                s.proposal.preparedPreimage),
            ()->{},()->{});
    }
    private static Path[] records(Path root,String account)
        throws IOException{
        try(Stream<Path> stream=Files.list(root)){
            String prefix=account+
                ".properties.g21103-writer-attempt-v1-";
            return stream.filter(p->p.getFileName().toString()
                .startsWith(prefix))
                .sorted(Comparator.comparing(Path::toString))
                .toArray(Path[]::new);
        }
    }
    private static String[] fingerprint(Path root,String account)
        throws IOException{
        Path[] ps=records(root,account);
        String[] out=new String[ps.length];
        for(int i=0;i<ps.length;i++)
            out[i]=ps[i].getFileName()+"|"+
                java.util.Base64.getEncoder().encodeToString(
                    Files.readAllBytes(ps[i]));
        return out;
    }
    private static void noAuthority(MailboxWriterAttemptEvidenceV1.Report r){
        check(!r.historicalEvidencePruningAllowed&&
            !r.completeWriterPopulationKnown&&
            !r.everyHistoricalGenerationParticipates&&
            !r.crashEpochSealed&&!r.uncooperativeWritersExcluded&&
            !r.futureWritersExcluded&&!r.unlinkAuthorized&&
            !r.cleanupAuthorized&&!r.accountMutationAuthorized&&
            !r.grantAuthorized&&!r.replayAuthorized&&
            !r.restartAdmissionAuthorized&&!r.releaseAuthorized&&
            !r.clientAckAuthorized,"v1 attempt enabled authority");
    }
    private static void noAuthority(MailboxGuardedDiskCommitRecord.Observation x){
        check(!x.transactionCommitted&&!x.liveApplied&&
            !x.grantAuthorized&&!x.replayAuthorized&&
            !x.rollbackAuthorized&&!x.restartAdmissionAuthorized&&
            !x.releaseAuthorized&&!x.clientAckAuthorized,
            "disk COMMIT observation enabled live grant");
    }
    private static boolean denied(
        MailboxGuardedDiskCommitRecord writer,Seed s,
        StrictDurablePlayerSnapshotWriter.Receipt receipt,String code
    )throws IOException{
        try{
            writer.recordConfirmedDiskTerminalWithWriterEvidence(
                s.proposal,receipt);
            return false;
        }catch(IOException veto){
            return String.valueOf(veto.getMessage()).contains(code);
        }
    }
    private static String javaBin(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }
    private static void child(Path root,String mode)throws Exception{
        FilePlayerRepository.PathResolver r=paths(root);
        try(World world=World.isolatedForTest(60000L)){
            Seed s=seed(world,CRASH);
            MailboxGuardedDiskCommitRecord normal=
                new MailboxGuardedDiskCommitRecord(r);
            if("halt".equals(mode)){
                StrictDurablePlayerSnapshotWriter.Receipt receipt=
                    setup(s,r);
                MailboxGuardedDiskCommitRecord crash=
                    new MailboxGuardedDiskCommitRecord(r,stage->{
                        if(stage==MailboxGuardedDiskCommitRecord
                                .Phase.AFTER_DIRECTORY_FORCE){
                            System.out.println(
                                "G21111_REAL_COMMIT_DIRFORCE_BEFORE_HALT"+
                                " durableBegin=true exit=131");
                            System.out.flush();
                            Runtime.getRuntime().halt(131);
                            throw new AssertionError("halt returned");
                        }
                    });
                crash.recordConfirmedDiskTerminalWithWriterEvidence(
                    s.proposal,receipt);
                throw new AssertionError("expected halt did not fire");
            }
            if("retry".equals(mode)){
                Path account=r.resolve(CRASH);
                Path diskCommit=normal.recordPath(CRASH);
                byte[] accountBefore=Files.readAllBytes(account);
                byte[] commitBefore=Files.readAllBytes(diskCommit);
                String[] attempts=fingerprint(root,CRASH);
                // No valid strict Receipt can be reconstituted without
                // another file write, and G21.88 correctly forbids
                // a NEW guarded terminal write after disk COMMIT.
                // Exercise the SAME G21.103/104 opt-in preflight
                // without fabricating a receipt or mutating the file.
                boolean[] actionCalled={false};
                boolean blocked=false;
                try{
                    MailboxWriterAttemptEvidenceV1.runRecorded(
                        r,CRASH,()->{
                            actionCalled[0]=true;
                            return Boolean.TRUE;
                        });
                }catch(IOException expected){
                    blocked=String.valueOf(expected.getMessage())
                        .contains(
                            "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");
                }
                MailboxWriterAttemptEvidenceV1.Report v1=
                    MailboxWriterAttemptEvidenceV1.inspect(r,CRASH);
                MailboxGuardedDiskCommitRecord.Observation persisted=
                    normal.inspect(CRASH);
                noAuthority(v1);
                noAuthority(persisted);
                boolean guardedStrictDenied=false;
                try{
                    new StrictDurablePlayerSnapshotWriter(r)
                        .saveStrictTerminalForWorld(
                            s.terminal,account,
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    s.proposal.preparedPreimage),
                            ()->{},()->{});
                }catch(IOException expected){
                    guardedStrictDenied=String.valueOf(
                        expected.getMessage()).contains(
                            "G21.88 DISK_COMMIT_STRICT_SAVE_VETO");
                }
                check(blocked&&!actionCalled[0]&&guardedStrictDenied&&
                    v1.started==1&&v1.completed==0&&
                    v1.status==MailboxWriterAttemptEvidenceV1.Status
                        .INCOMPLETE_ATTEMPT_NO_CLEANUP&&
                    persisted.status==MailboxGuardedDiskCommitRecord.Status
                        .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                    Arrays.equals(accountBefore,Files.readAllBytes(account))&&
                    Arrays.equals(commitBefore,Files.readAllBytes(diskCommit))&&
                    Arrays.equals(attempts,fingerprint(root,CRASH))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0,
                    "independent JVM accepted uncertain COMMIT or altered it");
                System.out.println(
                    "G21111_FRESH_JVM_COMMIT_RETRY_DENIED_PASS"+
                    " v1BeginUnpaired=true strictG2188Veto=true"+
                    " commitPreserved=true cleanup=false grant=false");
                return;
            }
            throw new AssertionError("unknown child mode");
        }
    }
    private static void fork(
        Path root,String mode,int code,String marker
    )throws Exception{
        Path log=root.resolve("g21111-"+mode+".log");
        Process child=new ProcessBuilder(
            javaBin(),"-cp",System.getProperty("java.class.path"),
            G21111MailboxRealDiskCommitRecordedWriterIntegrationTest
                .class.getName(),"--child",root.toString(),mode)
            .redirectErrorStream(true).redirectOutput(log.toFile())
            .start();
        try{
            check(child.waitFor(25,TimeUnit.SECONDS),
                "child timeout "+mode);
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String message=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(child.exitValue()==code&&message.contains(marker),
            "child mode="+mode+" code="+child.exitValue()+" "+message);
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21111-real-commit-");
        boolean defaultUnrecorded=false,paired=false,semanticMatched=false;
        boolean prelinkLeavesBegin=false,fullDeniesBeforeCommit=false;
        boolean accountPreserved=false,crashBeginOnly=false;
        boolean independentRetryDenied=false,zeroLeases=false;
        boolean liveRewardNotApplied=false;
        try(World world=World.isolatedForTest(60000L)){
            FilePlayerRepository.PathResolver r=paths(root);
            MailboxGuardedDiskCommitRecord writer=
                new MailboxGuardedDiskCommitRecord(r);
            Seed defaultCase=seed(world,DEFAULT);
            StrictDurablePlayerSnapshotWriter.Receipt defaultReceipt=
                setup(defaultCase,r);
            writer.recordConfirmedDiskTerminal(
                defaultCase.proposal,defaultReceipt);
            MailboxWriterAttemptEvidenceV1.Report unrecorded=
                MailboxWriterAttemptEvidenceV1.inspect(r,DEFAULT);
            noAuthority(unrecorded);
            defaultUnrecorded=unrecorded.recordCount==0&&
                unrecorded.status==MailboxWriterAttemptEvidenceV1.Status
                    .NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN;

            Seed success=seed(world,PAIRED);
            StrictDurablePlayerSnapshotWriter.Receipt received=
                setup(success,r);
            writer.recordConfirmedDiskTerminalWithWriterEvidence(
                success.proposal,received);
            MailboxWriterAttemptEvidenceV1.Report record=
                MailboxWriterAttemptEvidenceV1.inspect(r,PAIRED);
            MailboxGuardedDiskCommitRecord.Observation observed=
                writer.inspect(PAIRED);
            noAuthority(record);
            noAuthority(observed);
            paired=record.started==1&&record.completed==1&&
                record.recordCount==2&&
                record.status==MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP;
            semanticMatched=observed.status==
                MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                observed.recordValidated&&observed.diskCommitRecordMatched&&
                observed.messageId.equals(success.proposal.messageId)&&
                observed.intentKey.equals(success.proposal.idempotencyKey);

            Seed failure=seed(world,FAILURE);
            StrictDurablePlayerSnapshotWriter.Receipt failedReceipt=
                setup(failure,r);
            byte[] beforeFail=Files.readAllBytes(r.resolve(FAILURE));
            MailboxGuardedDiskCommitRecord faulted=
                new MailboxGuardedDiskCommitRecord(r,stage->{
                    if(stage==MailboxGuardedDiskCommitRecord
                            .Phase.BEFORE_LINK)
                        throw new IOException("G21111_INJECTED_BEFORE_LINK");
                });
            boolean injected=false;
            try{
                faulted.recordConfirmedDiskTerminalWithWriterEvidence(
                    failure.proposal,failedReceipt);
            }catch(IOException expected){
                injected=String.valueOf(expected.getMessage())
                    .contains("G21111_INJECTED_BEFORE_LINK");
            }
            MailboxWriterAttemptEvidenceV1.Report partial=
                MailboxWriterAttemptEvidenceV1.inspect(r,FAILURE);
            noAuthority(partial);
            prelinkLeavesBegin=injected&&partial.started==1&&
                partial.completed==0&&partial.recordCount==1&&
                !Files.exists(writer.recordPath(FAILURE),
                    LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(beforeFail,Files.readAllBytes(
                    r.resolve(FAILURE)))&&
                denied(writer,failure,failedReceipt,
                    "G21.104 PRIOR_INCOMPLETE_WRITER_ATTEMPT_NO_ENROLL");

            Seed full=seed(world,FULL);
            StrictDurablePlayerSnapshotWriter.Receipt fullReceipt=
                setup(full,r);
            for(int i=0;i<16;i++)
                MailboxWriterAttemptEvidenceV1.runRecorded(
                    r,FULL,()->Boolean.TRUE);
            byte[] beforeFull=Files.readAllBytes(r.resolve(FULL));
            String[] leaves=fingerprint(root,FULL);
            fullDeniesBeforeCommit=denied(writer,full,fullReceipt,
                "G21.104 WRITER_EVIDENCE_RETENTION_FULL_NO_ENROLL");
            accountPreserved=Arrays.equals(beforeFull,Files.readAllBytes(
                    r.resolve(FULL)))&&
                Arrays.equals(leaves,fingerprint(root,FULL))&&
                !Files.exists(writer.recordPath(FULL),
                    LinkOption.NOFOLLOW_LINKS);

            fork(root,"halt",131,
                "G21111_REAL_COMMIT_DIRFORCE_BEFORE_HALT");
            MailboxWriterAttemptEvidenceV1.Report crashed=
                MailboxWriterAttemptEvidenceV1.inspect(r,CRASH);
            noAuthority(crashed);
            byte[] originalCommit=Files.readAllBytes(
                writer.recordPath(CRASH));
            byte[] originalAccount=Files.readAllBytes(r.resolve(CRASH));
            String[] crashLeaves=fingerprint(root,CRASH);
            fork(root,"retry",0,
                "G21111_FRESH_JVM_COMMIT_RETRY_DENIED_PASS");
            crashBeginOnly=crashed.started==1&&crashed.completed==0&&
                crashed.recordCount==1&&crashLeaves.length==1&&
                crashLeaves[0].contains(".begin|")&&
                Arrays.equals(originalCommit,Files.readAllBytes(
                    writer.recordPath(CRASH)))&&
                Arrays.equals(originalAccount,Files.readAllBytes(
                    r.resolve(CRASH)))&&
                Arrays.equals(crashLeaves,fingerprint(root,CRASH));
            independentRetryDenied=crashBeginOnly;
            liveRewardNotApplied=success.player.bank().inventorySlots()==0&&
                success.player.mailbox().get(success.proposal.messageId)
                    .claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                defaultCase.player.bank().inventorySlots()==0&&
                failure.player.bank().inventorySlots()==0&&
                full.player.bank().inventorySlots()==0;
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            check(defaultUnrecorded&&paired&&semanticMatched&&
                prelinkLeavesBegin&&fullDeniesBeforeCommit&&
                accountPreserved&&crashBeginOnly&&independentRetryDenied&&
                liveRewardNotApplied&&zeroLeases,
                "real disk COMMIT opt-in participation contract");
            System.out.println("G21111_REAL_DISK_COMMIT_DIAGNOSTICS"+
                " defaultCommitUnenrolled="+defaultUnrecorded+
                " optInCommitHasPairedV1="+paired+
                " guardedDiskCommitSemanticsMatch="+semanticMatched+
                " prelinkFailureLeavesBeginOnly="+prelinkLeavesBegin+
                " fullLedgerDeniesBeforeCommit="+fullDeniesBeforeCommit+
                " sourceUnchangedOnVeto="+accountPreserved+
                " hardJvmAfterCommitForceLeavesBegin="+crashBeginOnly+
                " freshJvmRecoveryDenied="+independentRetryDenied+
                " liveRewardUnchanged="+liveRewardNotApplied+
                " noJvmLeases="+zeroLeases);
            System.out.println("G21111_OPTIN_REAL_COMMIT_WRITER_PASS"+
                " writer=G2186 diskOnly=true genuineStrictReceipt=true"+
                " defaultHotpathsUnchanged=true"+
                " historicalWritersCovered=false rollover=false"+
                " pruning=false cleanup=false unlink=false"+
                " grant=false replay=false session=false ack=false");
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
        check(args.length==0,"unexpected arguments");
        run();
    }
}

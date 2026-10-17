package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * G21.97: independent JVM hard termination inside REAL publication fault
 * points. Never treats a filesystem observation as an item grant.
 */
public final class G2197MailboxInterruptedPublicationJvmIntegrationTest {
    private enum Cut {
        JOURNAL_AFTER_LINK(97,true,false),
        TERMINAL_BEFORE_DIRECTORY_FORCE(98,false,false),
        COMMIT_BEFORE_LINK(99,false,false),
        COMMIT_AFTER_LINK(100,false,true),
        COMMIT_AFTER_DIRECTORY_FORCE(101,false,true);

        final int exitCode;
        final boolean preparedOnly;
        final boolean commitLeaf;
        Cut(int code,boolean prepared,boolean commit){
            exitCode=code;
            preparedOnly=prepared;
            commitLeaf=commit;
        }
        String account(){
            return "g2197-"+name().toLowerCase(Locale.ROOT)
                .replace('_','-');
        }
    }

    private static void require(boolean good,String detail){
        if(!good)throw new AssertionError("G21.97 "+detail);
    }
    private static FilePlayerRepository.PathResolver resolver(Path root){
        return account->root.resolve(account+".properties");
    }

    private static byte[][] leaves(Path root,String account)
        throws IOException{
        FilePlayerRepository.PathResolver paths=resolver(root);
        Path file=paths.resolve(account);
        Path intent=new MailboxDurableIdempotencyIntentJournal(paths)
            .journalPath(account);
        Path record=new MailboxGuardedDiskCommitRecord(paths)
            .recordPath(account);
        return new byte[][]{
            Files.exists(file)?Files.readAllBytes(file):null,
            Files.exists(intent)?Files.readAllBytes(intent):null,
            Files.exists(record)?Files.readAllBytes(record):null
        };
    }

    private static boolean equalLeaves(byte[][] a,byte[][] b){
        for(int i=0;i<3;i++)
            if(!Arrays.equals(a[i],b[i]))return false;
        return true;
    }

    private static void stopAt(Cut cut,String checkpoint){
        System.out.println("G2197_CHILD_CUT_REACHED"+
            " cut="+cut.name()+
            " checkpoint="+checkpoint+
            " grant=false replay=false session=false ack=false");
        System.out.flush();
        Runtime.getRuntime().halt(cut.exitCode);
        throw new AssertionError("G21.97 hard halt unexpectedly returned");
    }

    private static void crashWriter(Path root,Cut cut)
        throws Exception{
        String account=cut.account();
        FilePlayerRepository.PathResolver paths=resolver(root);
        try(World source=World.isolatedForTest(60000L)){
            WorldPlayer owner=new WorldPlayer();
            long generation=source.registerPlayer(owner,account);
            owner.mailbox().deliver(new RewardDeliveryMessage(
                account+":gift","No Replay","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2197_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot message=
                owner.mailbox().get(account+":gift");
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,message));
            MailboxSettlementPostimagePlanner.Proposal plan=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,message);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(paths);
            writer.saveStrict(plan.preparedPreimage);
            require(owner.bank().inventorySlots()==0&&
                message.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED,
                "seeded owner already credited");

            if(cut==Cut.JOURNAL_AFTER_LINK){
                new MailboxDurableIdempotencyIntentJournal(paths,
                    phase->{
                        if(phase==
                           MailboxDurableIdempotencyIntentJournal.Phase
                               .AFTER_LINK)
                            stopAt(cut,"journalHardLinkBeforeDirectoryForce");
                    }).publishPreparedIntent(plan);
                throw new AssertionError("journal publication did not halt");
            }
            new MailboxDurableIdempotencyIntentJournal(paths)
                .publishPreparedIntent(plan);

            PlayerSnapshot terminal=
                MailboxAtomicTerminalSnapshot.compose(plan);
            String preparedSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(plan.preparedPreimage);
            if(cut==Cut.TERMINAL_BEFORE_DIRECTORY_FORCE){
                new StrictDurablePlayerSnapshotWriter(paths,
                    phase->{
                        if(phase==
                           StrictDurablePlayerSnapshotWriter.Phase
                               .BEFORE_DIRECTORY_FORCE)
                            stopAt(cut,
                                "terminalAtomicMoveBeforeDirectoryForce");
                    }).saveStrictTerminalForWorld(
                        terminal,paths.resolve(account),preparedSha,
                        ()->{},()->{});
                throw new AssertionError("terminal replacement did not halt");
            }

            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                writer.saveStrictTerminalForWorld(
                    terminal,paths.resolve(account),preparedSha,
                    ()->{},()->{});
            require(receipt.matchesSnapshot(terminal),
                "strict terminal receipt mismatch");
            MailboxGuardedDiskCommitRecord.Phase target=
                cut==Cut.COMMIT_BEFORE_LINK
                    ?MailboxGuardedDiskCommitRecord.Phase.BEFORE_LINK
                    :cut==Cut.COMMIT_AFTER_LINK
                        ?MailboxGuardedDiskCommitRecord.Phase.AFTER_LINK
                        :MailboxGuardedDiskCommitRecord.Phase
                            .AFTER_DIRECTORY_FORCE;
            new MailboxGuardedDiskCommitRecord(paths,phase->{
                if(phase==target)
                    stopAt(cut,"commit"+target.name());
            }).recordConfirmedDiskTerminal(plan,receipt);
            throw new AssertionError("COMMIT publication did not halt");
        }
    }

    private static void verifyFreshProcess(Path root,Cut cut)
        throws Exception{
        String account=cut.account();
        FilePlayerRepository.PathResolver paths=resolver(root);
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        byte[][] before=leaves(root,account);
        require(before[0]!=null&&before[1]!=null,
            "account or PREPARED journal absent unexpectedly");

        MailboxGuardedDiskCommitRecord.Observation record=
            new MailboxGuardedDiskCommitRecord(paths).inspect(account);
        MailboxGuardedDiskCommitRecord.Status expectedStatus=
            cut.commitLeaf
                ?MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY
                :MailboxGuardedDiskCommitRecord.Status.ABSENT;
        require(record.status==expectedStatus,
            "unexpected forensic disk COMMIT state "+
            cut.name()+" actual="+record.status);
        require((before[2]!=null)==cut.commitLeaf,
            "COMMIT leaf presence differs from hard-kill stage");
        require(!record.transactionCommitted&&
                !record.liveApplied&&!record.grantAuthorized&&
                !record.replayAuthorized&&
                !record.restartAdmissionAuthorized&&
                !record.releaseAuthorized&&!record.clientAckAuthorized,
            "record inspection minted forbidden positive authority");

        MailboxDurableIdempotencyIntentJournal.Observation journal=
            new MailboxDurableIdempotencyIntentJournal(paths)
                .inspect(account);
        require(!journal.grantAuthorized&&
                !journal.replayAuthorized&&
                !journal.restartAdmissionAuthorized&&
                !journal.clientAckAuthorized,
            "journal inspection minted forbidden positive authority");

        boolean sessionRefused=false,workerRefused=false;
        boolean saveRefused=false,detachedRefused=false;
        boolean preparedUnclaimed=false,detachedOnly=false;

        if(cut.preparedOnly){
            require(journal.status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .PREPARED_MATCH_NO_REPLAY,
                "valid PREPARED journal not independently observable");
            PlayerSnapshot prepared=repository
                .loadForWorldSession(account).orElseThrow(
                    ()->new AssertionError("PREPARED account missing"));
            require(MailboxPreparedRestartAdmission.inspect(prepared).state==
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED,
                "PREPARED policy unexpectedly changed");
            WorldPlayer detached=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(prepared,detached);
            MailboxRewardDeliveryService.Snapshot message=
                detached.mailbox().get(account+":gift");
            preparedUnclaimed=
                detached.bank().inventorySlots()==0&&
                message!=null&&
                message.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            // Established G21.31 design allows this intact PREPARED
            // preimage; it is NOT a settlement or automatically logged-in
            // session. Do not falsely claim it is forbidden.
            require(preparedUnclaimed,"PREPARED restart minted credit");
        }else{
            try{
                repository.loadForWorldSession(account);
            }catch(IOException expected){
                sessionRefused=true;
            }
            require(sessionRefused,
                "interrupted terminal/COMMIT admitted by guarded reader");
            try(World restarted=World.isolatedForTest(60000L,repository)){
                try{
                    restarted.persistence().load(account);
                }catch(IOException expected){
                    workerRefused=true;
                }
                require(restarted.players().snapshot().isEmpty(),
                    "fresh World automatically registered recovered owner");
            }
            require(workerRefused,
                "interrupted terminal/COMMIT admitted by persistence worker");
            try{
                repository.saveForWorld(
                    PlayerSnapshotCodec.capture(
                        account,new WorldPlayer()));
            }catch(IOException expected){
                saveRefused=true;
            }
            require(saveRefused,
                "interrupted terminal/COMMIT permitted ordinary World save");
        }

        try{
            MailboxCommittedDetachedRestartRecovery.Result detached=
                new MailboxCommittedDetachedRestartRecovery(paths)
                    .recoverDetached(account);
            require(cut.commitLeaf&&detached.detachedRoundTrip&&
                    detached.claimedBeforeAnyReplay&&
                    !detached.transactionCommitted&&
                    !detached.liveApplied&&!detached.grantAuthorized&&
                    !detached.replayAuthorized&&
                    !detached.restartAdmissionAuthorized&&
                    !detached.releaseAuthorized&&
                    !detached.clientAckAuthorized,
                "detached COMMIT recovery unexpectedly authorized grant");
            detachedOnly=true;
        }catch(IOException absent){
            detachedRefused=true;
            require(!cut.commitLeaf,
                "COMMIT-backed forensic decode incorrectly refused");
        }
        require(cut.commitLeaf?detachedOnly:detachedRefused,
            "detached classification disagreed with COMMIT presence");

        require(equalLeaves(before,leaves(root,account)),
            "restart inspection changed account/journal/COMMIT bytes");
        require(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "publication lease leaked from restart observer");
        System.out.println("G2197_RESTART_CHILD_PASS"+
            " cut="+cut.name()+
            " commitState="+record.status+
            " journalState="+journal.status+
            " preparedOnlyUnclaimed="+preparedUnclaimed+
            " terminalOrCommitSessionDenied="+sessionRefused+
            " persistenceSessionDenied="+workerRefused+
            " ordinarySaveDenied="+saveRefused+
            " detachedDeniedOrNonGrant=true"+
            " diskBytesUnchanged=true"+
            " publicationLeasesZero=true"+
            " grant=false replay=false ack=false");
    }

    private static String javaBinary(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win")?"java.exe":"java").toString();
    }

    private static void fork(Path root,Cut cut,boolean writer)
        throws Exception{
        String mode=writer?"write":"restart";
        Path log=root.resolve("g2197-"+cut.name()+"-"+mode+".log");
        ProcessBuilder command=new ProcessBuilder(
            javaBinary(),"-cp",System.getProperty("java.class.path"),
            G2197MailboxInterruptedPublicationJvmIntegrationTest
                .class.getName(),
            "--child",mode,root.toString(),cut.name());
        command.redirectErrorStream(true);
        command.redirectOutput(log.toFile());
        Process process=command.start();
        try{
            require(process.waitFor(25,TimeUnit.SECONDS),
                "child timeout "+cut+" mode="+mode);
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(5,TimeUnit.SECONDS);
            }
        }
        int expected=writer?cut.exitCode:0;
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        require(process.exitValue()==expected,
            "child "+mode+" cut="+cut+
            " code="+process.exitValue()+
            " expected="+expected+" output="+output);
        require(output.contains(
                writer?"G2197_CHILD_CUT_REACHED":"G2197_RESTART_CHILD_PASS")&&
                output.contains("cut="+cut.name()),
            "child stage proof missing "+cut+" output="+output);
    }

    private static void runParent()throws Exception{
        Path root=Files.createTempDirectory("g2197-crash-cuts-");
        int exactHalts=0,independentRestarts=0;
        boolean allLeavesUnchanged=true;
        boolean parentLeasesZero=false;
        try{
            for(Cut cut:Cut.values()){
                fork(root,cut,true);
                exactHalts++;
                byte[][] immediatelyAfterCrash=leaves(root,cut.account());
                fork(root,cut,false);
                independentRestarts++;
                allLeavesUnchanged&=equalLeaves(
                    immediatelyAfterCrash,leaves(root,cut.account()));
            }
            parentLeasesZero=
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            System.out.println("G2197_INTERRUPTED_PUBLICATION_DIAGNOSTICS"+
                " hardJvmHalts="+exactHalts+
                " independentJvmRestarts="+independentRestarts+
                " observedDiskLeavesUnmodified="+allLeavesUnchanged+
                " parentPublicationLeasesZero="+parentLeasesZero+
                " noPositiveAuthority=true");
            require(exactHalts==Cut.values().length&&
                    independentRestarts==Cut.values().length&&
                    allLeavesUnchanged&&parentLeasesZero,
                "JVM interruption/restart matrix failed");
            System.out.println("G2197_INTERRUPTED_PUBLICATION_RESTART_PASS"+
                " cutPoints=5 independentProcesses=10"+
                " noJvmCleanup=true stablePreparedUnclaimed=true"+
                " terminalAndCommitQuarantined=true"+
                " liveApply=false grant=false replay=false"+
                " release=false ack=false");
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
            Path root=Paths.get(args[2]).toAbsolutePath().normalize();
            Cut cut=Cut.valueOf(args[3]);
            if("write".equals(args[1])){
                crashWriter(root,cut);
                throw new AssertionError("writer mode returned");
            }
            require("restart".equals(args[1]),
                "invalid child mode");
            verifyFreshProcess(root,cut);
            return;
        }
        require(args.length==0,"invalid top-level arguments");
        runParent();
    }
}

package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.95: real independent JVM death and fresh-process restart.
 * Intentionally uses Runtime.halt in child modes (no finally/shutdown).
 * Ordinary World login, save and reward authority remain disabled.
 */
public final class G2195MailboxRealJvmCrashRestartIntegrationTest {
    private static final int EXIT_SEALED_CRASH=95;
    private static final int EXIT_LOCKED_CRASH=96;
    private static final String SEALED="g2195-sealed";
    private static final String LOCKED="g2195-locked";

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.95 "+reason);
    }

    private static FilePlayerRepository.PathResolver paths(Path root){
        return account->root.resolve(account+".properties");
    }

    private static void makeDurableCommittedFixture(
        World source,String account,
        FilePlayerRepository.PathResolver resolver
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        long generation=source.registerPlayer(player,account);
        String messageId=account+":gift";
        player.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"No Replay","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2195_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=
            player.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(player,
            MailboxPreparedClaimJournal.prepare(player,row));
        MailboxSettlementPostimagePlanner.Proposal proposal=
            MailboxSettlementPostimagePlanner.plan(
                player,generation,row);

        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(resolver);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(resolver);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(resolver);
        writer.saveStrict(proposal.preparedPreimage);
        journal.publishPreparedIntent(proposal);
        PlayerSnapshot terminal=
            MailboxAtomicTerminalSnapshot.compose(proposal);
        StrictDurablePlayerSnapshotWriter.Receipt receipt=
            writer.saveStrictTerminalForWorld(
                terminal,resolver.resolve(account),
                StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(proposal.preparedPreimage),
                ()->{},()->{});
        require(receipt.matchesSnapshot(terminal),
            "fixture strict terminal receipt");
        commit.recordConfirmedDiskTerminal(proposal,receipt);
        require(player.bank().inventorySlots()==0&&
            row.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED,
            "original live owner never granted or claimed");
    }

    private static byte[][] bytes(Path root,String account)
        throws IOException{
        FilePlayerRepository.PathResolver resolver=paths(root);
        return new byte[][]{
            Files.readAllBytes(resolver.resolve(account)),
            Files.readAllBytes(
                new MailboxDurableIdempotencyIntentJournal(resolver)
                    .journalPath(account)),
            Files.readAllBytes(
                new MailboxGuardedDiskCommitRecord(resolver)
                    .recordPath(account))
        };
    }

    private static boolean sameBytes(
        byte[][] before,byte[][] after
    ){
        return Arrays.equals(before[0],after[0])&&
            Arrays.equals(before[1],after[1])&&
            Arrays.equals(before[2],after[2]);
    }

    private static void hardCrashAfterSeal(
        Path root,String account
    )throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver))){
            WorldPlayer receiver=new WorldPlayer();
            long generation=world.registerPlayer(receiver,account);
            WorldPlayerPersistence.CommittedRecoveryReservation token=
                world.persistence().reserveCommittedRecovery(
                    receiver,generation,account);
            token.drained().get(5,TimeUnit.SECONDS);
            MailboxCommittedDetachedRestartRecovery recovery=
                new MailboxCommittedDetachedRestartRecovery(resolver);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence evidence=
                token.inspectOneShotHandoff(recovery);
            require(token.sealWithPinnedPublication(recovery,evidence)==
                WorldPlayerPersistence.RecoverySealDecision
                    .SEALED_QUARANTINE_NO_ADMISSION,
                "child sealed negative-only under publication lock");
            require(token.sealedNoAdmission()&&
                token.publicationPinVerified()&&
                !token.cancelIfStillFresh(),
                "child retains live negative-only write quarantine");
            require(!evidence.grantAuthorized&&
                !evidence.liveApplied&&!evidence.replayAuthorized&&
                !evidence.restartAdmissionAuthorized&&
                !evidence.clientAckAuthorized,
                "child cannot grant/admit/replay/ACK");
            System.out.println("G2195_CHILD_SEALED_BEFORE_HALT"+
                " negativeSeal=true noGrant=true noAdmission=true");
            System.out.flush();
            // No normal close(), shutdown hook, or finally from here.
            Runtime.getRuntime().halt(EXIT_SEALED_CRASH);
            throw new AssertionError("G21.95 hard JVM halt returned");
        }
    }

    private static void hardCrashInsidePublicationLock(
        Path root,String account
    )throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        try(World world=World.isolatedForTest(
                60000L,new FilePlayerRepository(resolver))){
            WorldPlayer receiver=new WorldPlayer();
            long generation=world.registerPlayer(receiver,account);
            WorldPlayerPersistence.CommittedRecoveryReservation token=
                world.persistence().reserveCommittedRecovery(
                    receiver,generation,account);
            token.drained().get(5,TimeUnit.SECONDS);
            MailboxCommittedDetachedRestartRecovery recovery=
                new MailboxCommittedDetachedRestartRecovery(resolver);
            WorldPlayerPersistence.ReadOnlyRecoveryEvidence evidence=
                token.inspectOneShotHandoff(recovery);
            // The real cooperating OS advisory lock is held while we
            // recheck the actual three-file recovery witness and World
            // quarantine seal. Halt BEFORE final disk recheck/unlock.
            recovery.withPinnedPublication(account,inside->{
                require(evidence.audit.matchesPinnedDisk(inside),
                    "held-lock disk witness");
                require(token.sealNoAdmission(evidence)==
                    WorldPlayerPersistence.RecoverySealDecision
                        .SEALED_QUARANTINE_NO_ADMISSION,
                    "held-lock World/persistence negative seal");
                require(token.sealedNoAdmission(),
                    "held-lock quarantine set");
                System.out.println("G2195_CHILD_INSIDE_FILE_LOCK_BEFORE_HALT"+
                    " negativeSeal=true lockHeld=true noGrant=true");
                System.out.flush();
                Runtime.getRuntime().halt(EXIT_LOCKED_CRASH);
                throw new AssertionError("G21.95 hard lock halt returned");
            });
            throw new AssertionError(
                "G21.95 publication critical section unexpectedly returned");
        }
    }

    private static void verifyIndependentRestart(
        Path root,String account
    )throws Exception{
        FilePlayerRepository.PathResolver resolver=paths(root);
        FilePlayerRepository repository=new FilePlayerRepository(resolver);
        MailboxGuardedDiskCommitRecord commit=
            new MailboxGuardedDiskCommitRecord(resolver);

        // Abruptly killed JVMs cannot leave a process-global advisory
        // account lock hanging; this is a different OS process.
        boolean lockReacquired=
            MailboxAccountPublicationCoordinator
                .withExclusivePublicationBounded(
                    resolver.resolve(account),1500L,()->true);
        require(lockReacquired,"restarted JVM reacquires advisory file lock");
        MailboxGuardedDiskCommitRecord.Observation observation=
            commit.inspect(account);
        require(observation.status==
                MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY&&
                observation.recordValidated&&
                observation.diskCommitRecordMatched&&
                !observation.grantAuthorized&&
                !observation.restartAdmissionAuthorized,
            "disk COMMIT retained as negative-only restart evidence");

        boolean normalLoadDenied=false;
        try{
            repository.loadForWorldSession(account);
        }catch(IOException blocked){
            normalLoadDenied=String.valueOf(blocked.getMessage())
                .contains("G21.87 DISK_COMMIT_RESTART_QUARANTINE");
        }
        require(normalLoadDenied,
            "new JVM normal World session read vetoed");

        boolean ordinarySaveDenied=false;
        try{
            repository.saveForWorld(PlayerSnapshotCodec.capture(
                account,new WorldPlayer()));
        }catch(IOException blocked){
            ordinarySaveDenied=String.valueOf(blocked.getMessage())
                .contains("G21.88 DISK_COMMIT_WORLD_SAVE_VETO");
        }
        require(ordinarySaveDenied,
            "new JVM ordinary World save vetoed");

        boolean persistenceSessionDenied=false;
        try(World world=World.isolatedForTest(60000L,repository)){
            try{
                world.persistence().load(account);
            }catch(IOException blocked){
                persistenceSessionDenied=String.valueOf(
                    blocked.getMessage()).contains(
                        "G21.87 DISK_COMMIT_RESTART_QUARANTINE");
            }
            require(world.players().snapshot().isEmpty(),
                "restart creates no live player automatically");
        }
        require(persistenceSessionDenied,
            "real persistence worker also rejects session load");

        MailboxCommittedDetachedRestartRecovery.Result forensic=
            new MailboxCommittedDetachedRestartRecovery(resolver)
                .recoverDetached(account);
        require(forensic.detachedRoundTrip&&
                forensic.claimedBeforeAnyReplay&&
                forensic.occupiedInventorySlots>0&&
                !forensic.transactionCommitted&&
                !forensic.liveApplied&&
                !forensic.grantAuthorized&&
                !forensic.replayAuthorized&&
                !forensic.restartAdmissionAuthorized&&
                !forensic.releaseAuthorized&&
                !forensic.clientAckAuthorized,
            "detached forensic decode never grants or admits");
        require(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "fresh JVM released all publication leases");
        System.out.println("G2195_RESTART_CHILD_PASS"+
            " account="+account+
            " lockReacquired=true"+
            " normalWorldSessionDenied=true"+
            " ordinaryWorldSaveDenied=true"+
            " persistenceSessionDenied=true"+
            " diskCommitMatched=true"+
            " detachedOnly=true"+
            " noGrant=true noReplay=true noAck=true");
    }

    private static int fork(
        Path root,String mode,String account,int expectedExit,
        String expectedMarker
    )throws Exception{
        String javaBin=System.getProperty("os.name")
            .toLowerCase(java.util.Locale.ROOT).contains("win")
            ?"java.exe":"java";
        Path java=Paths.get(System.getProperty("java.home"),
            "bin",javaBin);
        require(Files.isRegularFile(java),
            "Java child executable unavailable");
        String classPath=System.getProperty("java.class.path");
        require(classPath!=null&&!classPath.isEmpty(),
            "child test classpath unavailable");
        Path log=root.resolve(
            "g2195-"+mode+"-"+account+".log");
        ProcessBuilder pb=new ProcessBuilder(
            java.toString(),"-cp",classPath,
            G2195MailboxRealJvmCrashRestartIntegrationTest.class.getName(),
            "--child",mode,root.toString(),account);
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        Process child=pb.start();
        final boolean terminated;
        try{
            terminated=child.waitFor(20,TimeUnit.SECONDS);
            if(!terminated)
                throw new AssertionError(
                    "G21.95 child process timed out mode="+mode);
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        int code=child.exitValue();
        require(code==expectedExit,
            "child exit unexpected mode="+mode+" exit="+code+
            " output="+output);
        require(output.contains(expectedMarker),
            "child proof marker missing mode="+mode+
            " output="+output);
        return code;
    }

    private static void runParent()throws Exception{
        Path root=Files.createTempDirectory("g2195-real-jvm-");
        boolean sealedDeath=false,heldLockDeath=false;
        boolean sealedRestart=false,lockedRestart=false;
        boolean allOriginalDiskBytesUnchanged=false;
        boolean publicationLeasesZero=false;
        try{
            byte[][] sealedBefore,lockedBefore;
            try(World source=World.isolatedForTest(60000L)){
                makeDurableCommittedFixture(source,SEALED,paths(root));
                makeDurableCommittedFixture(source,LOCKED,paths(root));
                sealedBefore=bytes(root,SEALED);
                lockedBefore=bytes(root,LOCKED);
            }
            sealedDeath=fork(root,"sealed-crash",SEALED,
                EXIT_SEALED_CRASH,
                "G2195_CHILD_SEALED_BEFORE_HALT")==EXIT_SEALED_CRASH;
            sealedRestart=fork(root,"restart",SEALED,0,
                "G2195_RESTART_CHILD_PASS")==0;
            heldLockDeath=fork(root,"locked-crash",LOCKED,
                EXIT_LOCKED_CRASH,
                "G2195_CHILD_INSIDE_FILE_LOCK_BEFORE_HALT")==
                    EXIT_LOCKED_CRASH;
            lockedRestart=fork(root,"restart",LOCKED,0,
                "G2195_RESTART_CHILD_PASS")==0;
            allOriginalDiskBytesUnchanged=
                sameBytes(sealedBefore,bytes(root,SEALED))&&
                sameBytes(lockedBefore,bytes(root,LOCKED));
            publicationLeasesZero=
                MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            System.out.println("G2195_REAL_JVM_RESTART_DIAGNOSTICS"+
                " hardHaltAfterSeal="+sealedDeath+
                " independentRestartAfterSeal="+sealedRestart+
                " hardHaltInsideFileLock="+heldLockDeath+
                " independentRestartAfterHeldLockDeath="+lockedRestart+
                " accountIntentCommitBytesUnchanged="+
                    allOriginalDiskBytesUnchanged+
                " parentJvmLeasesZero="+publicationLeasesZero);
            require(sealedDeath&&sealedRestart&&heldLockDeath&&
                lockedRestart&&allOriginalDiskBytesUnchanged&&
                publicationLeasesZero,
                "multi-process abrupt death and restart proof");
            System.out.println("G2195_REAL_JVM_CRASH_RESTART_PASS"+
                " independentProcesses=4"+
                " abruptHalt=true noShutdownHooks=true"+
                " lockReleasedOnDeath=true"+
                " diskCommitQuarantinePersists=true"+
                " worldSession=false ordinarySave=false"+
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
        if(args.length>0){
            require(args.length==4&&"--child".equals(args[0]),
                "child arguments invalid");
            Path root=Paths.get(args[2]).toAbsolutePath().normalize();
            String account=args[3];
            switch(args[1]){
                case "sealed-crash":
                    hardCrashAfterSeal(root,account);
                    break;
                case "locked-crash":
                    hardCrashInsidePublicationLock(root,account);
                    break;
                case "restart":
                    verifyIndependentRestart(root,account);
                    return;
                default:
                    throw new AssertionError("G21.95 unknown child mode");
            }
            throw new AssertionError(
                "G21.95 child hard-halt mode returned");
        }
        runParent();
    }
}

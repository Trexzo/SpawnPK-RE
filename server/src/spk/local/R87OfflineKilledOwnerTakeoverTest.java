package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R87 OFFLINE ONLY. Forcibly terminates genuine separate JVMs while the
 * R86 fixture holds the actual OS file lock on each side of ATOMIC_MOVE.
 *
 * These tests simulate PROCESS death, not host power loss or disk flush
 * failure. No real World, account, session or game process is launched.
 */
public final class R87OfflineKilledOwnerTakeoverTest {
    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    private static final String BEFORE="STAGED_BEFORE_MOVE";
    private static final String AFTER="REPLACED_BEFORE_ACK";
    private static final String CANONICAL="r86-offline-owner-record.txt";

    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args.length!=5||!"killpoint".equals(args[0]))
                throw new IllegalArgumentException(
                    "R87 unexpected child invocation");
            enterChildKillpoint(Paths.get(args[1]),
                R86OfflineOwnerEpochRecord.Token.parse(args[2]),
                args[3],Paths.get(args[4]));
            return;
        }

        Path scratch=Files.createTempDirectory("spk-r87-killed-takeover-");
        try{
            killDuringStagedFileBeforeMove(
                scratch.resolve("kill-before-move"));
            killAfterCanonicalMoveBeforeAck(
                scratch.resolve("kill-after-move"));
            System.out.println(
                "R87_OFFLINE_KILLED_TAKEOVER_PASS "+
                "realChildJvmDestroyForcibly=true "+
                "fileLockHeldAtBothKillpoints=true "+
                "childExitAndLockReleaseObserved=true "+
                "preMoveCanonicalUnchanged=true "+
                "preMoveStageRemainsQuarantined=true "+
                "orphanStageNotAutomaticallyDeletedOrReplayed=true "+
                "postMoveOwnerEpochAdvanced=true "+
                "postMovePriorOwnerRejected=true "+
                "eventIdentityAndCheckpointPreserved=true "+
                "noCredentialRecoveredFromInspection=true "+
                "noLiveWorldOrAccountFiles=true "+
                "noPowerLossDurabilityClaim=true"
            );
        }finally{
            // End-of-test disposal only. Do not interpret this cleanup
            // as permission to delete ambiguous real persistence evidence.
            try(Stream<Path> walk=Files.walk(scratch)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException error){throw new IllegalStateException(
                        "R87 disposable fixture cleanup failed "+p,error);
                    }
                });
            }
        }
    }

    private static void killDuringStagedFileBeforeMove(Path root)
        throws Exception{
        R86OfflineOwnerEpochRecord.Session original=
            makeCommittedFixture(root,71L);
        R86OfflineOwnerEpochRecord.Token old=original.token();
        R86OfflineOwnerEpochRecord.State before=original.snapshot();
        byte[] canonicalBefore=bytes(root);

        killAt(root,old,BEFORE);

        require(Arrays.equals(canonicalBefore,bytes(root)),
            "R87 pre-move killed JVM changed canonical owner record");
        require(stageCount(root)==1L,
            "R87 pre-move kill did not strand one staged record");

        // R64 already quarantines ambiguous staged publications. R87
        // applies the same refusal principle to its V2 fixture:
        // no silent stage deletion, login, writer reopening or replay.
        expectFailure(()->R86OfflineOwnerEpochRecord.inspectForTest(root),
            "R87_STAGE_QUARANTINED");
        expectFailure(()->R86OfflineOwnerEpochRecord.reopen(root,old),
            "R87_STAGE_QUARANTINED");
        expectFailure(()->original.consumer(
            R86OfflineOwnerEpochRecord.Consumer.MATCH,
            R86OfflineOwnerEpochRecord.Fault.NONE),
            "R87_STAGE_QUARANTINED");

        // Validate the surviving canonical bytes WITHOUT treating them
        // as authorization to reconstruct an owner or replay the event.
        R86OfflineOwnerEpochRecord.State raw=
            R86OfflineOwnerEpochRecord.State.parse(
                Files.readString(root.resolve(CANONICAL),
                    StandardCharsets.UTF_8));
        assertSourceAndObjective(before,raw);
        require(raw.ownerEpoch==old.epoch &&
                raw.owner.equals(old.owner) &&
                raw.revision==old.revision,
            "R87 pre-move source owner changed");
        // Leave orphan stage in place until the disposable fixture is
        // destroyed at end of test, preserving ambiguity evidence.
    }

    private static void killAfterCanonicalMoveBeforeAck(Path root)
        throws Exception{
        R86OfflineOwnerEpochRecord.Session original=
            makeCommittedFixture(root,72L);
        R86OfflineOwnerEpochRecord.Token old=original.token();
        R86OfflineOwnerEpochRecord.State before=original.snapshot();
        byte[] canonicalBefore=bytes(root);

        killAt(root,old,AFTER);

        require(!Arrays.equals(canonicalBefore,bytes(root)),
            "R87 post-move killed JVM did not publish new canonical bytes");
        require(stageCount(root)==0L,
            "R87 post-move takeover left an unexpected staged file");
        R86OfflineOwnerEpochRecord.State after=
            R86OfflineOwnerEpochRecord.inspectForTest(root);
        assertSourceAndObjective(before,after);
        require(after.ownerEpoch==old.epoch+1L &&
                after.revision==old.revision+1L &&
                !after.owner.equals(old.owner) &&
                after.stream.equals(old.stream),
            "R87 post-move takeover lost monotonic owner identity");
        expectFailure(()->R86OfflineOwnerEpochRecord.reopen(root,old),
            "R86_STALE_OWNER");
        expectFailure(()->original.consumer(
            R86OfflineOwnerEpochRecord.Consumer.MATCH,
            R86OfflineOwnerEpochRecord.Fault.NONE),
            "R86_STALE_OWNER");

        // R86 inspectForTest is read-only; it must not authorize the
        // caller to claim a successor token. No post-crash automatic
        // projection retry or credential recovery occurs here.
        require(after.objectiveCp&&!after.matchCp&&!after.collectionCp,
            "R87 post-move inspection unexpectedly dispatched consumers");
    }

    private static R86OfflineOwnerEpochRecord.Session makeCommittedFixture(
        Path root,long epoch)throws Exception{
        R86OfflineOwnerEpochRecord.Session state=
            R86OfflineOwnerEpochRecord.create(root,epoch);
        state.lethal(R86OfflineOwnerEpochRecord.Fault.NONE);
        state.consumer(R86OfflineOwnerEpochRecord.Consumer.OBJECTIVE,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        require(state.snapshot().sequence==1L &&
                state.snapshot().objectiveCp &&
                state.snapshot().objective==1L,
            "R87 pre-kill committed source fixture invalid");
        return state;
    }

    private static void killAt(
        Path root,R86OfflineOwnerEpochRecord.Token prior,
        String expectedStage)throws Exception{
        Path ready=root.resolve("r87-ready-"+expectedStage);
        Path childLog=root.resolve("r87-child-"+expectedStage+".log");
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Process child=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R87OfflineKilledOwnerTakeoverTest.class.getName(),
            "killpoint",root.toString(),prior.wire(),
            expectedStage,ready.toString()
        ).redirectErrorStream(true).redirectOutput(childLog.toFile()).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.isRegularFile(ready) &&
                  child.isAlive() &&
                  System.nanoTime()<deadline)
                Thread.sleep(15L);
            require(Files.isRegularFile(ready)&&child.isAlive(),
                "R87 child did not reach "+expectedStage+": "+
                (Files.exists(childLog)?Files.readString(childLog):"no log"));
            require(expectedStage.equals(Files.readString(
                ready,StandardCharsets.UTF_8)),
                "R87 wrong child crash cutpoint");
            // The child is paused INSIDE the OS-locked takeover. The
            // parent cannot enter the same lock while the child lives.
            expectFailure(()->R86OfflineOwnerEpochRecord.inspectForTest(root),
                "R86_LOCK_DENIED");
            child.destroyForcibly();
            require(child.waitFor(15L,TimeUnit.SECONDS),
                "R87 force-killed JVM did not terminate");
            require(!child.isAlive(),
                "R87 child still alive after force termination");
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(15L,TimeUnit.SECONDS);
            }
        }
        // Acquiring the fixture's lock in the assertions below proves
        // the operating system released it after the JVM was killed.
        // A pre-move orphan stage still blocks canonical access.
    }

    private static void enterChildKillpoint(
        Path root,R86OfflineOwnerEpochRecord.Token predecessor,
        String desired,Path ready)throws Exception{
        require(BEFORE.equals(desired)||AFTER.equals(desired),
            "R87 unrecognized requested kill phase");
        R86OfflineOwnerEpochRecord.installOfflineTakeoverCutpointForTest(
            actual->{
                if(!desired.equals(actual))
                    return;
                Files.writeString(ready,actual,StandardCharsets.UTF_8);
                // Parent must forcibly terminate this process; a bounded
                // timeout fails the test instead of silently committing.
                long deadline=System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(30);
                while(System.nanoTime()<deadline){
                    try{Thread.sleep(100L);}
                    catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException(
                            "R87 child cutpoint interrupted",interrupted);
                    }
                }
                throw new IOException(
                    "R87 child was not force-terminated at cutpoint");
            });
        try{
            R86OfflineOwnerEpochRecord.takeover(root,predecessor,
                R86OfflineOwnerEpochRecord.Fault.NONE);
            throw new AssertionError(
                "R87 killed-JVM takeover returned unexpectedly");
        }finally{
            R86OfflineOwnerEpochRecord.clearOfflineTakeoverCutpointForTest();
        }
    }

    private static void assertSourceAndObjective(
        R86OfflineOwnerEpochRecord.State before,
        R86OfflineOwnerEpochRecord.State after){
        require(before.killId.equals(after.killId) &&
                before.deathId.equals(after.deathId) &&
                before.stream.equals(after.stream) &&
                before.originEpoch==after.originEpoch &&
                after.hp==0 && after.sequence==1L &&
                after.objectiveCp && after.objective==1L &&
                after.match==0L && after.collection==0L &&
                !after.matchCp && !after.collectionCp,
            "R87 killed-JVM takeover altered event identity or checkpoint");
    }

    private static long stageCount(Path root)throws IOException{
        try(java.nio.file.DirectoryStream<Path> stages=
                Files.newDirectoryStream(root,"r86-stage-*.tmp")){
            long count=0L;
            for(Path ignored:stages)count++;
            return count;
        }
    }

    private static byte[] bytes(Path root)throws IOException{
        return Files.readAllBytes(root.resolve(CANONICAL));
    }

    private static void expectFailure(Checked action,String fragment)
        throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "R87 expected refusal missing: "+fragment);
        }catch(IOException|IllegalStateException error){
            require(error.getMessage().contains(fragment),
                "R87 wrong refusal: "+error);
        }
    }

    private static void require(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }

    private R87OfflineKilledOwnerTakeoverTest(){}
}

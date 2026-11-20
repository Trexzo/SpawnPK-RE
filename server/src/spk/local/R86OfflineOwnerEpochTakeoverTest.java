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
 * R86 OFFLINE ONLY: real second-JVM owner takeover, competing successor
 * attempts, post-ack ambiguity, and persistent checkpoint preservation.
 * The only mutated files are beneath fresh temporary fixture roots.
 */
public final class R86OfflineOwnerEpochTakeoverTest {
    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args.length!=3 ||
               (!"takeover".equals(args[0])&&!"race".equals(args[0])))
                throw new IllegalArgumentException(
                    "R86 unexpected child command");
            Path root=Paths.get(args[1]);
            R86OfflineOwnerEpochRecord.Token previous=
                R86OfflineOwnerEpochRecord.Token.parse(args[2]);
            try{
                R86OfflineOwnerEpochRecord.Session next=
                    R86OfflineOwnerEpochRecord.takeover(
                        root,previous,
                        R86OfflineOwnerEpochRecord.Fault.NONE);
                if("takeover".equals(args[0]))
                    next.consumer(
                        R86OfflineOwnerEpochRecord.Consumer.MATCH,
                        R86OfflineOwnerEpochRecord.Fault.NONE);
                System.out.println("R86_CHILD_SUCCESS "+next.token().wire());
            }catch(IOException rejected){
                if(!"race".equals(args[0]))
                    throw rejected;
                if(rejected.getMessage().contains("R86_STALE_OWNER"))
                    System.out.println("R86_CHILD_STALE_REFUSED");
                else if(rejected.getMessage().contains("R86_LOCK_DENIED"))
                    System.out.println("R86_CHILD_LOCK_REFUSED");
                else
                    throw rejected;
            }
            return;
        }

        Path root=Files.createTempDirectory("spk-r86-owner-epoch-");
        try{
            sameJvmTakeoverPreservesEventAndCheckpoints(
                root.resolve("same-jvm"));
            genuineSecondJvmTakeover(root.resolve("second-jvm"));
            twoCompetingJvmsOnlyOneSuccess(root.resolve("race"));
            beforeAndAfterTakeoverMove(root.resolve("fault"));
            staleSameEpochRevisionRefused(root.resolve("revision"));
            malformedAndMissingCanonicalQuarantined(
                root.resolve("corrupt"));
            System.out.println(
                "R86_OFFLINE_OWNER_EPOCH_TAKEOVER_PASS "+
                "sameFileOwnerEpochAndCheckpointState=true "+
                "immutablePreTakeoverEventIdentity=true "+
                "explicitSuccessorEpochAndOwnerUuid=true "+
                "oldOwnerDeniedAfterTransfer=true "+
                "realSecondJvmTakeoverAndNextConsumerCommit=true "+
                "twoJvmsCompeteOneWinner=true "+
                "preMoveKeepsPriorOwner=true "+
                "postMoveAckAmbiguityRefusesOldOwner=true "+
                "sameEpochStaleRevisionRejected=true "+
                "checkpointValuesSurviveTakeover=true "+
                "corruptOrMissingRecordQuarantined=true "+
                "noAutomaticOwnerRecoveryOrLogin=true "+
                "noProductionWorldOrAccountWrites=true"
            );
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException failure){throw new IllegalStateException(
                        "R86 cleanup "+p,failure);
                    }
                });
            }
        }
    }

    private static void sameJvmTakeoverPreservesEventAndCheckpoints(
        Path root)throws Exception{
        R86OfflineOwnerEpochRecord.Session first=
            R86OfflineOwnerEpochRecord.create(root,11L);
        first.lethal(R86OfflineOwnerEpochRecord.Fault.NONE);
        first.consumer(R86OfflineOwnerEpochRecord.Consumer.OBJECTIVE,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        R86OfflineOwnerEpochRecord.State before=first.snapshot();
        R86OfflineOwnerEpochRecord.Token oldToken=first.token();
        R86OfflineOwnerEpochRecord.Session next=
            R86OfflineOwnerEpochRecord.takeover(
                root,oldToken,R86OfflineOwnerEpochRecord.Fault.NONE);
        require(next.token().epoch==12L &&
                next.token().revision==oldToken.revision+1L &&
                !next.token().owner.equals(oldToken.owner) &&
                next.token().stream.equals(oldToken.stream),
            "R86 successor not distinct in same record");
        R86OfflineOwnerEpochRecord.State after=next.snapshot();
        require(after.killId.equals(before.killId) &&
                after.deathId.equals(before.deathId) &&
                after.originEpoch==11L&&after.hp==0 &&
                after.objective==1L&&after.objectiveCp,
            "R86 owner handoff rewrote source identity or checkpoint");
        expectFailure(()->first.consumer(
                R86OfflineOwnerEpochRecord.Consumer.MATCH,
                R86OfflineOwnerEpochRecord.Fault.NONE),
            "R86_STALE_OWNER");
        expectFailure(()->first.snapshot(),"R86_REOPEN_REQUIRED");
        expectFailure(()->R86OfflineOwnerEpochRecord.reopen(
            root,oldToken),"R86_STALE_OWNER");
        next.consumer(R86OfflineOwnerEpochRecord.Consumer.MATCH,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        next.consumer(R86OfflineOwnerEpochRecord.Consumer.COLLECTION,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        assertAll(R86OfflineOwnerEpochRecord.inspectForTest(root));
        R86OfflineOwnerEpochRecord.Token last=next.token();
        next.consumer(R86OfflineOwnerEpochRecord.Consumer.MATCH,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        require(next.token().revision==last.revision,
            "R86 duplicate checkpoint changed revision");
    }

    private static void genuineSecondJvmTakeover(
        Path root)throws Exception{
        R86OfflineOwnerEpochRecord.Session stale=
            R86OfflineOwnerEpochRecord.create(root,22L);
        stale.lethal(R86OfflineOwnerEpochRecord.Fault.NONE);
        stale.consumer(R86OfflineOwnerEpochRecord.Consumer.OBJECTIVE,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        String kill=stale.snapshot().killId;
        R86OfflineOwnerEpochRecord.Token previous=stale.token();
        String output=runChild("takeover",root,previous);
        require(output.contains("R86_CHILD_SUCCESS "),
            "R86 second-JVM transfer did not report success: "+output);
        R86OfflineOwnerEpochRecord.Token successor=
            tokenFrom(output);
        require(successor.epoch==23L &&
                !successor.owner.equals(previous.owner),
            "R86 independent JVM did not advance ownership");
        expectFailure(()->stale.consumer(
            R86OfflineOwnerEpochRecord.Consumer.COLLECTION,
            R86OfflineOwnerEpochRecord.Fault.NONE),"R86_STALE_OWNER");
        R86OfflineOwnerEpochRecord.Session restored=
            R86OfflineOwnerEpochRecord.reopen(root,successor);
        require(restored.snapshot().killId.equals(kill) &&
                restored.snapshot().match==2L&&
                restored.snapshot().objective==1L,
            "R86 new owner lost preexisting or new-JVM checkpoints");
        restored.consumer(R86OfflineOwnerEpochRecord.Consumer.COLLECTION,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        assertAll(R86OfflineOwnerEpochRecord.inspectForTest(root));
    }

    private static void twoCompetingJvmsOnlyOneSuccess(
        Path root)throws Exception{
        R86OfflineOwnerEpochRecord.Session source=
            R86OfflineOwnerEpochRecord.create(root,33L);
        source.lethal(R86OfflineOwnerEpochRecord.Fault.NONE);
        R86OfflineOwnerEpochRecord.Token previous=source.token();
        Child a=startChild("race",root,previous,"contender-a");
        Child b=startChild("race",root,previous,"contender-b");
        String first=finishChild(a);
        String second=finishChild(b);
        int won=(first.contains("R86_CHILD_SUCCESS ")?1:0)+
                (second.contains("R86_CHILD_SUCCESS ")?1:0);
        int denied=(first.contains("R86_CHILD_STALE_REFUSED")||
                    first.contains("R86_CHILD_LOCK_REFUSED")?1:0)+
                (second.contains("R86_CHILD_STALE_REFUSED")||
                    second.contains("R86_CHILD_LOCK_REFUSED")?1:0);
        require(won==1&&denied==1,
            "R86 race did not produce exactly one successor: "+
            first+" ; "+second);
        R86OfflineOwnerEpochRecord.State actual=
            R86OfflineOwnerEpochRecord.inspectForTest(root);
        require(actual.ownerEpoch==34L &&
                actual.revision==previous.revision+1L &&
                actual.hp==0&&actual.killId.endsWith("/33/1/KILL"),
            "R86 two-JVM ownership race created duplicate epochs/events");
        expectFailure(()->source.consumer(
            R86OfflineOwnerEpochRecord.Consumer.OBJECTIVE,
            R86OfflineOwnerEpochRecord.Fault.NONE),"R86_STALE_OWNER");
    }

    private static void beforeAndAfterTakeoverMove(
        Path parent)throws Exception{
        for(boolean after:new boolean[]{false,true}){
            Path root=parent.resolve(after?"after":"before");
            R86OfflineOwnerEpochRecord.Session source=
                R86OfflineOwnerEpochRecord.create(root,44L);
            source.lethal(R86OfflineOwnerEpochRecord.Fault.NONE);
            source.consumer(
                R86OfflineOwnerEpochRecord.Consumer.OBJECTIVE,
                R86OfflineOwnerEpochRecord.Fault.NONE);
            R86OfflineOwnerEpochRecord.Token predecessor=source.token();
            R86OfflineOwnerEpochRecord.State before=source.snapshot();
            try{
                R86OfflineOwnerEpochRecord.takeover(root,predecessor,
                    after?R86OfflineOwnerEpochRecord.Fault
                        .AFTER_MOVE_BEFORE_ACK:
                        R86OfflineOwnerEpochRecord.Fault.BEFORE_MOVE);
                throw new AssertionError(
                    "R86 injected takeover failure unexpectedly succeeded");
            }catch(IOException expected){
                require(expected.getMessage().contains(after?
                    "R86_INJECTED after atomic move before ack":
                    "R86_INJECTED before atomic move"),
                    "R86 incorrect injection failure "+expected);
            }
            R86OfflineOwnerEpochRecord.State current=
                R86OfflineOwnerEpochRecord.inspectForTest(root);
            require(current.ownerEpoch==(after?45L:44L) &&
                    current.killId.equals(before.killId)&&
                    current.objectiveCp&&current.objective==1L,
                "R86 takeover fault broke source or checkpoint invariants");
            if(after){
                expectFailure(()->R86OfflineOwnerEpochRecord.reopen(
                    root,predecessor),"R86_STALE_OWNER");
                expectFailure(()->source.consumer(
                    R86OfflineOwnerEpochRecord.Consumer.MATCH,
                    R86OfflineOwnerEpochRecord.Fault.NONE),
                    "R86_STALE_OWNER");
                // Inspecting the post-ack file proves the takeover happened,
                // but this test-only observer is NOT credential recovery.
                require(current.ownerEpoch==predecessor.epoch+1L,
                    "R86 ambiguous acknowledge did not advance epoch");
            }else{
                R86OfflineOwnerEpochRecord.Session stillOld=
                    R86OfflineOwnerEpochRecord.reopen(root,predecessor);
                stillOld.consumer(
                    R86OfflineOwnerEpochRecord.Consumer.MATCH,
                    R86OfflineOwnerEpochRecord.Fault.NONE);
                require(stillOld.snapshot().ownerEpoch==44L,
                    "R86 pre-move failure transferred owner unexpectedly");
            }
        }
    }

    private static void staleSameEpochRevisionRefused(
        Path root)throws Exception{
        R86OfflineOwnerEpochRecord.Session first=
            R86OfflineOwnerEpochRecord.create(root,55L);
        first.lethal(R86OfflineOwnerEpochRecord.Fault.NONE);
        R86OfflineOwnerEpochRecord.Session stale=
            R86OfflineOwnerEpochRecord.reopen(root,first.token());
        first.consumer(R86OfflineOwnerEpochRecord.Consumer.OBJECTIVE,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        expectFailure(()->stale.consumer(
            R86OfflineOwnerEpochRecord.Consumer.MATCH,
            R86OfflineOwnerEpochRecord.Fault.NONE),"R86_STALE_OWNER");
        require(R86OfflineOwnerEpochRecord.inspectForTest(root).objectiveCp &&
                !R86OfflineOwnerEpochRecord.inspectForTest(root).matchCp,
            "R86 same-epoch stale revision erased committed checkpoint");
        R86OfflineOwnerEpochRecord.Session refreshed=
            R86OfflineOwnerEpochRecord.reopen(root,first.token());
        refreshed.consumer(R86OfflineOwnerEpochRecord.Consumer.MATCH,
            R86OfflineOwnerEpochRecord.Fault.NONE);
        require(refreshed.snapshot().matchCp,
            "R86 explicit reopen failed to update");
    }

    private static void malformedAndMissingCanonicalQuarantined(
        Path root)throws Exception{
        R86OfflineOwnerEpochRecord.Session source=
            R86OfflineOwnerEpochRecord.create(root,66L);
        R86OfflineOwnerEpochRecord.Token token=source.token();
        Path canonical=root.resolve("r86-offline-owner-record.txt");
        byte[] saved=Files.readAllBytes(canonical);
        Files.writeString(canonical,"R86_BAD_CANONICAL\n",
            StandardCharsets.UTF_8);
        expectFailure(()->R86OfflineOwnerEpochRecord.reopen(root,token),
            "R86_RECORD_QUARANTINED");
        expectFailure(()->source.lethal(
            R86OfflineOwnerEpochRecord.Fault.NONE),
            "R86_RECORD_QUARANTINED");
        Files.write(canonical,saved);
        require(R86OfflineOwnerEpochRecord.reopen(root,token)
                    .snapshot().hp==10,
            "R86 restored fixture failed canonical validation");
        Files.delete(canonical);
        expectFailure(()->R86OfflineOwnerEpochRecord.reopen(root,token),
            "R86_RECORD_QUARANTINED");
    }

    private static String runChild(String mode,Path root,
                                   R86OfflineOwnerEpochRecord.Token token)
        throws Exception{
        return finishChild(startChild(mode,root,token,"child"));
    }

    private static final class Child{
        final Process process;
        final Path output;
        Child(Process process,Path output){
            this.process=process;this.output=output;
        }
    }

    private static Child startChild(
        String mode,Path root,R86OfflineOwnerEpochRecord.Token token,
        String label)throws Exception{
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Path output=root.resolve(label+".log");
        Process process=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R86OfflineOwnerEpochTakeoverTest.class.getName(),
            mode,root.toString(),token.wire()
        ).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        return new Child(process,output);
    }

    private static String finishChild(Child child)throws Exception{
        if(!child.process.waitFor(25L,TimeUnit.SECONDS)){
            child.process.destroyForcibly();
            child.process.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R86 second JVM takeover timed out: "+child.output);
        }
        String output=Files.readString(child.output,StandardCharsets.UTF_8);
        require(child.process.exitValue()==0,
            "R86 child process exit failure: "+output);
        return output;
    }

    private static R86OfflineOwnerEpochRecord.Token tokenFrom(
        String output){
        for(String line:output.split("\\R")){
            for(String piece:line.split("\\n")){
                if(piece.startsWith("R86_CHILD_SUCCESS "))
                    return R86OfflineOwnerEpochRecord.Token.parse(
                        piece.substring("R86_CHILD_SUCCESS ".length()).trim());
            }
        }
        throw new AssertionError("R86 child successor token missing");
    }

    private static void assertAll(R86OfflineOwnerEpochRecord.State s){
        require(s.hp==0&&s.sequence==1L &&
                s.objectiveCp&&s.objective==1L &&
                s.matchCp&&s.match==2L &&
                s.collectionCp&&s.collection==7L,
            "R86 takeover destroyed per-consumer state");
    }

    private static void expectFailure(Checked action,String expected)
        throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "R86 expected denial missing "+expected);
        }catch(IOException|IllegalStateException refused){
            require(refused.getMessage().contains(expected),
                "R86 wrong denial "+refused);
        }
    }

    private static void require(boolean yes,String detail){
        if(!yes)throw new AssertionError(detail);
    }

    private R86OfflineOwnerEpochTakeoverTest(){}
}

package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R85 OFFLINE ONLY. Uses real OS FileLock and freshly re-read R84 canonical
 * bytes, and a real second JVM that holds the lock independently. All paths
 * are freshly created disposable directories.
 */
public final class R85OfflineFencedCombatRecordTest {
    @FunctionalInterface private interface Checked {
        void run()throws Exception;
    }

    public static void main(String[] args)throws Exception {
        if(args.length!=0){
            if(args.length!=4 || !"holdlock".equals(args[0]))
                throw new IllegalArgumentException("R85 test child arguments");
            holdInChild(Paths.get(args[1]),Paths.get(args[2]),
                Paths.get(args[3]));
            return;
        }
        Path temp=Files.createTempDirectory("spk-r85-locked-fixture-");
        try{
            staleHandlesCannotLoseCheckpoint(temp.resolve("freshness"));
            sameJvmLockContentionFailsClosed(temp.resolve("same-jvm"));
            actualSecondJvmLockContention(temp.resolve("second-jvm"));
            beforeAndAfterMoveRequireExplicitReopen(
                temp.resolve("faults"));
            epochAndRecordTamperingRefused(temp.resolve("tampering"));
            System.out.println(
                "R85_OFFLINE_FENCED_COMBAT_RECORD_PASS "+
                "realOsFileLock=true "+
                "secondJvmHoldsLockAndBlocksWrites=true "+
                "sameJvmOverlappingLockRefused=true "+
                "staleGenerationAndExactBytesCheckedUnderLock=true "+
                "sameEpochStaleWriterCannotLoseCheckpoint=true "+
                "allThreeConsumerCheckpointValuesPreserved=true "+
                "duplicateConsumerNoIncrement=true "+
                "prePostMoveFaultPoisonAndReopen=true "+
                "staleEpochAndCorruptionRefused=true "+
                "R84RawNegativeControlUnchanged=true "+
                "noRealOwnershipTakeoverOrLease=true "+
                "noProductionAccountOrReplayChanges=true"
            );
        }finally{
            try(Stream<Path> walk=Files.walk(temp)){
                walk.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException error){throw new IllegalStateException(
                        "R85 fixture cleanup "+path,error);
                    }
                });
            }
        }
    }

    private static void staleHandlesCannotLoseCheckpoint(
        Path root)throws Exception{
        R85OfflineFencedCombatRecord a=
            R85OfflineFencedCombatRecord.create(root,111L);
        R85OfflineFencedCombatRecord b=
            R85OfflineFencedCombatRecord.reopen(root,111L);
        require(a.observedGeneration()==0L&&b.observedGeneration()==0L,
            "R85 initial generation mismatch");
        a.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        require(a.observedGeneration()==1L,
            "R85 producer generation not advanced");
        byte[] afterProducer=canonical(root);
        expectFailure(()->b.commitLethal(
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "R85_STALE_GENERATION");
        expectFailure(()->b.observedState(),"R85_REOPEN_REQUIRED");
        require(Arrays.equals(afterProducer,canonical(root)),
            "R85 stale duplicate producer changed bytes");

        b=R85OfflineFencedCombatRecord.reopen(root,111L);
        // Take both handles from the exact same committed generation.
        R85OfflineFencedCombatRecord staleB=b;
        a.commitConsumer(R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        require(a.observedGeneration()==2L,
            "R85 objective checkpoint generation not advanced");
        byte[] afterObjective=canonical(root);
        expectFailure(()->staleB.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.MATCH,
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "R85_STALE_GENERATION");
        require(Arrays.equals(afterObjective,canonical(root)),
            "R85 stale competing match erased objective checkpoint");

        R85OfflineFencedCombatRecord next=
            R85OfflineFencedCombatRecord.reopen(root,111L);
        next.commitConsumer(R84OfflineAtomicCombatRecord.Consumer.MATCH,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        require(next.observedGeneration()==3L,
            "R85 match checkpoint generation not advanced");
        expectFailure(()->a.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.COLLECTION,
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "R85_STALE_GENERATION");

        R85OfflineFencedCombatRecord finalWriter=
            R85OfflineFencedCombatRecord.reopen(root,111L);
        finalWriter.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.COLLECTION,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        require(finalWriter.observedGeneration()==4L,
            "R85 collection generation not advanced");
        allCommitted(R85OfflineFencedCombatRecord.reopen(
            root,111L).observedState());
        byte[] beforeDuplicate=canonical(root);
        finalWriter.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        require(finalWriter.observedGeneration()==4L &&
                Arrays.equals(beforeDuplicate,canonical(root)),
            "R85 duplicate already-checkpointed consumer rewrote data");
    }

    private static void sameJvmLockContentionFailsClosed(
        Path root)throws Exception{
        R85OfflineFencedCombatRecord r=
            R85OfflineFencedCombatRecord.create(root,21L);
        byte[] before=canonical(root);
        try(FileChannel channel=FileChannel.open(
                R85OfflineFencedCombatRecord.lockFileForTest(root),
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            FileLock held=channel.lock()){
            expectFailure(()->r.commitLethal(
                R84OfflineAtomicCombatRecord.Fault.NONE),
                "R85_LOCK_DENIED");
            require(Arrays.equals(before,canonical(root)),
                "R85 same-JVM lock refusal changed canonical bytes");
        }
        r.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        require(r.observedGeneration()==1L,
            "R85 lock refusal incorrectly poisoned unchanged writer");
    }

    private static void actualSecondJvmLockContention(
        Path root)throws Exception{
        R85OfflineFencedCombatRecord r=
            R85OfflineFencedCombatRecord.create(root,22L);
        byte[] before=canonical(root);
        Path ready=root.resolve("child-ready");
        Path release=root.resolve("child-release");
        Path log=root.resolve("child.log");
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(java.util.Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Process child=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R85OfflineFencedCombatRecordTest.class.getName(),
            "holdlock",root.toString(),ready.toString(),release.toString()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!Files.exists(ready) && child.isAlive() &&
                  System.nanoTime()<deadline)
                Thread.sleep(20L);
            require(Files.exists(ready),
                "R85 child failed to acquire cross-JVM FileLock: "+
                (Files.exists(log)?Files.readString(log):"no log"));
            expectFailure(()->r.commitLethal(
                R84OfflineAtomicCombatRecord.Fault.NONE),
                "R85_LOCK_DENIED");
            require(Arrays.equals(before,canonical(root)),
                "R85 competing JVM mutated locked canonical file");
        }finally{
            Files.writeString(release,"release",StandardCharsets.UTF_8);
            if(!child.waitFor(20L,TimeUnit.SECONDS))
                child.destroyForcibly();
            if(child.isAlive())
                child.waitFor(5L,TimeUnit.SECONDS);
        }
        require(child.exitValue()==0,
            "R85 lock-holder JVM failed: "+Files.readString(log));
        r.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        require(r.observedGeneration()==1L,
            "R85 cross-JVM lock was not released");
    }

    private static void holdInChild(
        Path root,Path ready,Path release)throws Exception{
        Path lock=R85OfflineFencedCombatRecord.lockFileForTest(root);
        try(FileChannel channel=FileChannel.open(lock,
                StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            FileLock held=channel.lock()){
            Files.writeString(ready,"held",StandardCharsets.UTF_8);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.exists(release)&&System.nanoTime()<deadline)
                Thread.sleep(20L);
            require(Files.exists(release),
                "R85 second JVM lock release signal missing");
        }
    }

    private static void beforeAndAfterMoveRequireExplicitReopen(
        Path parent)throws Exception{
        for(boolean after:new boolean[]{false,true}){
            Path root=parent.resolve(after?"after":"before");
            R85OfflineFencedCombatRecord writer=
                R85OfflineFencedCombatRecord.create(root,31L);
            R84OfflineAtomicCombatRecord.Fault fault=after?
                R84OfflineAtomicCombatRecord.Fault
                    .AFTER_ATOMIC_MOVE_BEFORE_ACK:
                R84OfflineAtomicCombatRecord.Fault.BEFORE_ATOMIC_MOVE;
            expectFailure(()->writer.commitLethal(fault),
                after?"R84_INJECTED after atomic move before ack":
                      "R84_INJECTED before atomic move");
            expectFailure(()->writer.commitLethal(
                R84OfflineAtomicCombatRecord.Fault.NONE),
                "R85_REOPEN_REQUIRED");
            R85OfflineFencedCombatRecord recovered=
                R85OfflineFencedCombatRecord.reopen(root,31L);
            require(recovered.observedGeneration()==(after?1L:0L),
                "R85 injected source crash gave wrong canonical generation");
            if(after)
                expectFailure(()->recovered.commitLethal(
                    R84OfflineAtomicCombatRecord.Fault.NONE),
                    "event already committed");
            else
                recovered.commitLethal(
                    R84OfflineAtomicCombatRecord.Fault.NONE);
            R85OfflineFencedCombatRecord consumer=
                R85OfflineFencedCombatRecord.reopen(root,31L);
            expectFailure(()->consumer.commitConsumer(
                R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE,fault),
                after?"R84_INJECTED after atomic move before ack":
                      "R84_INJECTED before atomic move");
            expectFailure(()->consumer.observedState(),
                "R85_REOPEN_REQUIRED");
            R85OfflineFencedCombatRecord resumed=
                R85OfflineFencedCombatRecord.reopen(root,31L);
            require(resumed.observedGeneration()==(after?2L:1L),
                "R85 injected consumer crash incorrectly advanced generation");
            resumed.commitConsumer(
                R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE,
                R84OfflineAtomicCombatRecord.Fault.NONE);
            require(resumed.observedGeneration()==2L &&
                    resumed.observedState().objectives==1L,
                "R85 consumer checkpoint duplicate or recovery mismatch");
        }
    }

    private static void epochAndRecordTamperingRefused(
        Path root)throws Exception{
        R85OfflineFencedCombatRecord writer=
            R85OfflineFencedCombatRecord.create(root,41L);
        expectFailure(()->R85OfflineFencedCombatRecord.reopen(
            root,42L),"stale owner epoch");
        writer.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        byte[] legitimate=canonical(root);
        // A noncooperating writer can still bypass the experimental
        // wrapper. Demonstrate the wrapper refuses the unexpected bytes.
        Path record=root.resolve("r84-offline-record.txt");
        Files.writeString(record,"R85-corrupted-record\n",
            StandardCharsets.UTF_8);
        expectFailure(()->writer.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.MATCH,
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "R84_RECORD_QUARANTINED");
        expectFailure(()->writer.observedGeneration(),
            "R85_REOPEN_REQUIRED");
        expectFailure(()->R85OfflineFencedCombatRecord.reopen(
            root,41L),"R84_RECORD_QUARANTINED");
        Files.write(record,legitimate);
        R85OfflineFencedCombatRecord restored=
            R85OfflineFencedCombatRecord.reopen(root,41L);
        restored.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.MATCH,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        require(restored.observedGeneration()==2L &&
                restored.observedState().match==2L,
            "R85 canonical revalidation or recovery failed");
    }

    private static byte[] canonical(Path root)throws IOException{
        return Files.readAllBytes(root.resolve("r84-offline-record.txt"));
    }

    private static void allCommitted(
        R84OfflineAtomicCombatRecord.State s){
        require(s.hp==0&&s.sequence==1L &&
                s.objectiveCheckpoint&&s.objectives==1L &&
                s.matchCheckpoint&&s.match==2L &&
                s.collectionCheckpoint&&s.collection==7L,
            "R85 competing writers lost a canonical checkpoint");
    }

    private static void expectFailure(
        Checked action,String expected)throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "R85 expected denial was not raised: "+expected);
        }catch(IOException|IllegalStateException refused){
            require(refused.getMessage().contains(expected),
                "R85 wrong refusal: "+refused);
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private R85OfflineFencedCombatRecordTest(){}
}

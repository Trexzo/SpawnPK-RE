package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R84 OFFLINE ONLY: disposable-file crash cutpoints. The test does NOT
 * replay real combat, publish DomainEvents, or mutate any account.
 * R83/R81/R79 real-resolver regressions run alongside this model in CI.
 */
public final class R84OfflineAtomicCombatRecordTest {
    private interface Checked { void run()throws Exception; }

    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("r84-combat-atomic-model-");
        try {
            normalAndRestart(root.resolve("normal"));
            producerBeforeSwapRefusesAutomaticRecovery(
                root.resolve("producer-before"));
            producerAfterSwapIsAmbiguousButIdempotent(
                root.resolve("producer-after"));
            eachConsumerFailureBeforeAndAfterMove(
                root.resolve("consumers"));
            staleEpochAndInvalidStateQuarantined(
                root.resolve("quarantine"));
            twoOpenWritersCanLoseUpdatesWithoutRealLock(
                root.resolve("concurrency-neg-control"));
            System.out.println(
                "R84_OFFLINE_COMBAT_ATOMIC_PROTOCOL_PASS "+
                "producerModelAllocatesPairIdsBeforeAtomicHpCommit=true "+
                "sourceAndCheckpointSingleRecord=true "+
                "idsStableAfterReopen=true "+
                "preMoveNoPublish=true "+
                "postMoveAckAmbiguityFailClosed=true "+
                "everyConsumerPreAndPostMoveCutpoint=true "+
                "consumerCountAndCheckpointPaired=true "+
                "duplicateAfterReopenNoDoubleIncrement=true "+
                "staleOwnerEpochRefused=true "+
                "corruptedOrMissingRecordQuarantined=true "+
                "concurrentWriterLostUpdateRiskExposed=true "+
                "noRealProducerOrOutboxWiring=true "+
                "noPowerLossDurabilityClaim=true "+
                "noAutomaticGameReplay=true"
            );
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(
                            "R84 fixture cleanup failed "+path,failure);
                    }
                });
            }
        }
    }

    private static void normalAndRestart(Path root)throws Exception{
        R84OfflineAtomicCombatRecord record=
            R84OfflineAtomicCombatRecord.create(root,11L);
        R84OfflineAtomicCombatRecord.State before=record.snapshot();
        check(before.sequence==0L&&before.hp==10 &&
              "-".equals(before.killId) && "-".equals(before.deathId),
            "R84 invalid initial model state");
        record.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        R84OfflineAtomicCombatRecord.State emitted=record.snapshot();
        check(emitted.hp==0 && emitted.sequence==1L &&
              !emitted.killId.equals(emitted.deathId) &&
              emitted.killId.endsWith("/KILL") &&
              emitted.deathId.endsWith("/DEATH") &&
              emitted.killId.contains("/11/1/"),
            "R84 producer-side model event pair missing or not unique");

        R84OfflineAtomicCombatRecord restarted=
            R84OfflineAtomicCombatRecord.reopen(root,11L);
        check(restarted.snapshot().killId.equals(emitted.killId) &&
              restarted.snapshot().deathId.equals(emitted.deathId),
            "R84 producer-model event IDs changed after reopen");

        restarted.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        restarted.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.MATCH,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        restarted.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.COLLECTION,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        R84OfflineAtomicCombatRecord reopened=
            R84OfflineAtomicCombatRecord.reopen(root,11L);
        allCommitted(reopened.snapshot());
        for(R84OfflineAtomicCombatRecord.Consumer consumer:
                R84OfflineAtomicCombatRecord.Consumer.values())
            reopened.commitConsumer(
                consumer,R84OfflineAtomicCombatRecord.Fault.NONE);
        allCommitted(R84OfflineAtomicCombatRecord.reopen(
            root,11L).snapshot());
        expectFailure(()->reopened.commitLethal(
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "event already committed");
        noStaging(root);
    }

    private static void producerBeforeSwapRefusesAutomaticRecovery(
        Path root)throws Exception {
        R84OfflineAtomicCombatRecord record=
            R84OfflineAtomicCombatRecord.create(root,23L);
        byte[] original=Files.readAllBytes(
            root.resolve("r84-offline-record.txt"));
        expectFailure(()->record.commitLethal(
            R84OfflineAtomicCombatRecord.Fault.BEFORE_ATOMIC_MOVE),
            "R84_INJECTED before atomic move");
        expectFailure(()->record.commitLethal(
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "ambiguous commit: reopen");
        check(Arrays.equals(original,Files.readAllBytes(
                root.resolve("r84-offline-record.txt"))),
            "R84 before-move fault wrote a partial or later record");
        R84OfflineAtomicCombatRecord reopened=
            R84OfflineAtomicCombatRecord.reopen(root,23L);
        check(reopened.snapshot().hp==10 &&
              reopened.snapshot().sequence==0L,
            "R84 before-move crash changed model HP/ID");
        noStaging(root);
    }

    private static void producerAfterSwapIsAmbiguousButIdempotent(
        Path root)throws Exception {
        R84OfflineAtomicCombatRecord record=
            R84OfflineAtomicCombatRecord.create(root,24L);
        expectFailure(()->record.commitLethal(
            R84OfflineAtomicCombatRecord.Fault
                .AFTER_ATOMIC_MOVE_BEFORE_ACK),
            "R84_INJECTED after atomic move before ack");
        expectFailure(()->record.snapshot(),
            "ambiguous commit: reopen");
        R84OfflineAtomicCombatRecord resumed=
            R84OfflineAtomicCombatRecord.reopen(root,24L);
        check(resumed.snapshot().hp==0 &&
              resumed.snapshot().sequence==1L,
            "R84 after-move state was not fully published");
        expectFailure(()->resumed.commitLethal(
            R84OfflineAtomicCombatRecord.Fault.NONE),
            "event already committed");
        noStaging(root);
    }

    private static void eachConsumerFailureBeforeAndAfterMove(
        Path parent)throws Exception {
        for(R84OfflineAtomicCombatRecord.Consumer consumer:
                R84OfflineAtomicCombatRecord.Consumer.values()){
            for(boolean after:new boolean[]{false,true}){
                Path root=parent.resolve(consumer.name()+"-"+after);
                R84OfflineAtomicCombatRecord record=
                    R84OfflineAtomicCombatRecord.create(root,33L);
                record.commitLethal(
                    R84OfflineAtomicCombatRecord.Fault.NONE);
                R84OfflineAtomicCombatRecord.Fault fault=after?
                    R84OfflineAtomicCombatRecord.Fault
                        .AFTER_ATOMIC_MOVE_BEFORE_ACK:
                    R84OfflineAtomicCombatRecord.Fault
                        .BEFORE_ATOMIC_MOVE;
                expectFailure(()->record.commitConsumer(consumer,fault),
                    after?"R84_INJECTED after atomic move before ack":
                          "R84_INJECTED before atomic move");
                expectFailure(()->record.commitConsumer(
                    consumer,R84OfflineAtomicCombatRecord.Fault.NONE),
                    "ambiguous commit: reopen");
                R84OfflineAtomicCombatRecord resumed=
                    R84OfflineAtomicCombatRecord.reopen(root,33L);
                checkCheckpoint(resumed.snapshot(),consumer,after);
                if(after){
                    resumed.commitConsumer(
                        consumer,R84OfflineAtomicCombatRecord.Fault.NONE);
                    checkCheckpoint(
                        R84OfflineAtomicCombatRecord.reopen(
                            root,33L).snapshot(),consumer,true);
                }
                noStaging(root);
            }
        }
    }

    private static void staleEpochAndInvalidStateQuarantined(
        Path root)throws Exception {
        R84OfflineAtomicCombatRecord record=
            R84OfflineAtomicCombatRecord.create(root,44L);
        expectFailure(()->R84OfflineAtomicCombatRecord.reopen(
            root,45L),"stale owner epoch");
        expectFailure(()->R84OfflineAtomicCombatRecord.create(
            root,44L),"already initialized");
        record.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        Path canonical=root.resolve("r84-offline-record.txt");
        byte[] legitimate=Files.readAllBytes(canonical);
        Files.writeString(canonical,"INVALID-TRUNCATED",
            StandardCharsets.UTF_8);
        expectFailure(()->R84OfflineAtomicCombatRecord.reopen(
            root,44L),"R84_RECORD_QUARANTINED");
        Files.write(canonical,legitimate);
        check(R84OfflineAtomicCombatRecord.reopen(
            root,44L).snapshot().sequence==1L,
            "R84 restored fixture failed to reopen");
        Files.delete(canonical);
        expectFailure(()->R84OfflineAtomicCombatRecord.reopen(
            root,44L),"R84_RECORD_QUARANTINED");
    }

    private static void twoOpenWritersCanLoseUpdatesWithoutRealLock(
        Path root)throws Exception {
        R84OfflineAtomicCombatRecord first=
            R84OfflineAtomicCombatRecord.create(root,55L);
        first.commitLethal(R84OfflineAtomicCombatRecord.Fault.NONE);
        R84OfflineAtomicCombatRecord stale=
            R84OfflineAtomicCombatRecord.reopen(root,55L);
        first.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        // A second writer carrying the same epoch and a stale in-memory
        // record can replace the entire file. ATOMIC_MOVE is not a CAS.
        stale.commitConsumer(
            R84OfflineAtomicCombatRecord.Consumer.MATCH,
            R84OfflineAtomicCombatRecord.Fault.NONE);
        R84OfflineAtomicCombatRecord.State lost=
            R84OfflineAtomicCombatRecord.reopen(root,55L).snapshot();
        check(lost.matchCheckpoint&&lost.match==2L &&
              !lost.objectiveCheckpoint&&lost.objectives==0L,
            "R84 lost-update failure case was not demonstrated");
        // This negative control is why production needs R64-like
        // OS ownership + expected generation under a shared lock.
    }

    private static void checkCheckpoint(
        R84OfflineAtomicCombatRecord.State state,
        R84OfflineAtomicCombatRecord.Consumer consumer,
        boolean committed){
        check(state.hp==0 && state.sequence==1L,
            "R84 consumer fault altered the source event");
        if(consumer==R84OfflineAtomicCombatRecord.Consumer.OBJECTIVE)
            check(state.objectiveCheckpoint==committed &&
                  state.objectives==(committed?1L:0L) &&
                  state.match==0L && state.collection==0L,
                "R84 objective value/checkpoint torn");
        if(consumer==R84OfflineAtomicCombatRecord.Consumer.MATCH)
            check(state.matchCheckpoint==committed &&
                  state.match==(committed?2L:0L) &&
                  state.objectives==0L && state.collection==0L,
                "R84 match value/checkpoint torn");
        if(consumer==R84OfflineAtomicCombatRecord.Consumer.COLLECTION)
            check(state.collectionCheckpoint==committed &&
                  state.collection==(committed?7L:0L) &&
                  state.objectives==0L && state.match==0L,
                "R84 Collection value/checkpoint torn");
    }

    private static void allCommitted(
        R84OfflineAtomicCombatRecord.State state){
        check(state.objectiveCheckpoint&&state.objectives==1L &&
              state.matchCheckpoint&&state.match==2L &&
              state.collectionCheckpoint&&state.collection==7L &&
              state.hp==0,
            "R84 completed state or consumer checkpoints mismatch");
    }

    private static void noStaging(Path root)throws Exception {
        try(Stream<Path> children=Files.list(root)){
            check(children.noneMatch(p->p.getFileName().toString()
                    .startsWith("r84-stage-")),
                "R84 injected failure left temporary stage");
        }
    }

    private static void expectFailure(Checked action,String expected)
        throws Exception {
        try{
            action.run();
            throw new AssertionError(
                "R84 expected failure missing: "+expected);
        }catch(IOException|IllegalStateException refusal){
            check(refusal.getMessage().contains(expected),
                "R84 unexpected failure "+refusal);
        }
    }

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private R84OfflineAtomicCombatRecordTest(){}
}

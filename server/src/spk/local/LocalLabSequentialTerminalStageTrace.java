package spk.local;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * World-process-local forensic observation of the DEFAULT sequential
 * post-settlement G4/G14/G15/G16/G18 progression callback.
 *
 * A recorded RETURNED is NOT evidence that a reward was credited (a
 * consumer may have no active task). THREW may mean partial mutation.
 * NOT_REACHED means the sequential callback stopped before that stage.
 *
 * No persistence, grant, replay, reconnect, "ACK", recovery or original
 * SpawnPK server-side kill-credit authority is implemented here.
 */
final class LocalLabSequentialTerminalStageTrace {
    static final int MAX_REPORTS=128;

    enum Stage { G4, G14, G15, G16, G18 }
    enum Outcome {
        RETURNED,
        THREW,
        NOT_REACHED,
        SKIPPED_NO_CURRENT_RECIPIENT
    }

    static final class Report {
        final EntityId npcId;
        final long deathTick;
        final String recipientRef;
        final long spawnRecipientGeneration;
        // Observation ONLY. A false match means the current same-name
        // WorldPlayer is not the immutable NPC spawn-time player/epoch.
        // This does not authorize blocking or replaying any grant.
        final boolean spawnOriginMatchedAtDispatch;
        final long observedCurrentGeneration;
        final Map<Stage,Outcome> outcomes;
        final Map<Stage,String> failureTypes;

        private Report(Capture capture){
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                capture.terminal;
            npcId=terminal.finalization.npcId;
            deathTick=terminal.finalization.deathTick;
            recipientRef=terminal.finalization.recipientRef;
            spawnRecipientGeneration=
                terminal.recipientOrigin==null
                    ?-1L
                    :terminal.recipientOrigin.generation;
            spawnOriginMatchedAtDispatch=
                capture.spawnOriginMatchedAtDispatch;
            observedCurrentGeneration=capture.observedCurrentGeneration;
            outcomes=Collections.unmodifiableMap(
                new EnumMap<Stage,Outcome>(capture.outcomes)
            );
            failureTypes=Collections.unmodifiableMap(
                new EnumMap<Stage,String>(capture.failureTypes)
            );
        }

        Outcome outcome(Stage stage){
            return outcomes.get(
                Objects.requireNonNull(stage,"stage")
            );
        }

        String failureType(Stage stage){
            return failureTypes.get(
                Objects.requireNonNull(stage,"stage")
            );
        }
    }

    static final class Snapshot {
        final long observed;
        final long evicted;
        final List<Report> recent;

        private Snapshot(
            long observed,
            long evicted,
            ArrayDeque<Report> reports
        ){
            this.observed=observed;
            this.evicted=evicted;
            this.recent=Collections.unmodifiableList(
                new ArrayList<Report>(reports)
            );
        }
    }

    /**
     * An invocation-local scratchpad. Captures which callbacks returned
     * and which were NOT invoked when fail-fast propagation stopped the
     * sequential chain; this is never a reward/credit dedupe key.
     */
    static final class Capture {
        final MonsterSpawnerPvmRuntime.FinalizeResult terminal;
        final boolean spawnOriginMatchedAtDispatch;
        final long observedCurrentGeneration;
        final EnumMap<Stage,Outcome> outcomes=
            new EnumMap<Stage,Outcome>(Stage.class);
        final EnumMap<Stage,String> failureTypes=
            new EnumMap<Stage,String>(Stage.class);
        private boolean recorded;

        private Capture(
            MonsterSpawnerPvmRuntime.FinalizeResult terminal,
            World world
        ){
            this.terminal=terminal;
            MonsterSpawnerPvmRuntime.RecipientOrigin origin=
                terminal.recipientOrigin;
            String recipientRef=terminal.finalization.recipientRef;
            WorldPlayer observed=world.players().byName(recipientRef);
            observedCurrentGeneration=
                observed==null?-1L:observed.generation();
            spawnOriginMatchedAtDispatch=
                origin!=null&&observed!=null&&
                origin.player==observed&&
                origin.generation==observedCurrentGeneration&&
                recipientRef.equals(origin.recipientRef)&&
                origin.stillCurrentIn(world);
            for(Stage stage:Stage.values())
                outcomes.put(stage,Outcome.NOT_REACHED);
        }

        void run(
            Stage stage,
            Runnable callback
        ){
            Stage checked=Objects.requireNonNull(stage,"stage");
            Runnable action=Objects.requireNonNull(
                callback,"callback"
            );
            if(recorded||outcomes.get(checked)!=Outcome.NOT_REACHED)
                throw new IllegalStateException(
                    "sequential stage attempt already completed stage="+
                    checked
                );
            try{
                action.run();
                outcomes.put(checked,Outcome.RETURNED);
            }catch(RuntimeException|Error failure){
                outcomes.put(checked,Outcome.THREW);
                // Only retain type name, not throwable, payload,
                // user state or stack trace.
                failureTypes.put(
                    checked,failure.getClass().getName()
                );
                throw failure;
            }
        }

        void skipNoCurrentRecipient(){
            if(recorded)
                throw new IllegalStateException(
                    "sequential stage capture already recorded"
                );
            for(Stage stage:Stage.values()){
                if(outcomes.get(stage)!=Outcome.NOT_REACHED)
                    throw new IllegalStateException(
                        "cannot mark started stage skipped"
                    );
                outcomes.put(
                    stage,Outcome.SKIPPED_NO_CURRENT_RECIPIENT
                );
            }
        }
    }

    private final World world;
    private final ArrayDeque<Report> reports=new ArrayDeque<>();
    private long observed;
    private long evicted;

    LocalLabSequentialTerminalStageTrace(World world){
        this.world=Objects.requireNonNull(world,"world");
    }

    Capture begin(
        MonsterSpawnerPvmRuntime.FinalizeResult terminal
    ){
        MonsterSpawnerPvmRuntime.FinalizeResult event=
            Objects.requireNonNull(terminal,"terminal");
        if(event.sourceWorld!=world)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH"
            );
        if(event.status!=MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED||
           event.finalization==null||event.settlement==null)
            throw new IllegalArgumentException(
                "sequential stages require a settled FINALIZED event"
            );
        return new Capture(event,world);
    }

    synchronized void record(Capture capture){
        Capture checked=Objects.requireNonNull(
            capture,"capture"
        );
        if(checked.terminal.sourceWorld!=world)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_SOURCE_WORLD_MISMATCH"
            );
        if(checked.recorded)
            throw new IllegalStateException(
                "SEQUENTIAL_TERMINAL_STAGE_CAPTURE_ALREADY_RECORDED"
            );
        checked.recorded=true;

        if(observed<Long.MAX_VALUE)
            observed++;
        if(reports.size()==MAX_REPORTS){
            reports.removeFirst();
            if(evicted<Long.MAX_VALUE)
                evicted++;
        }
        reports.addLast(new Report(checked));
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(observed,evicted,reports);
    }
}

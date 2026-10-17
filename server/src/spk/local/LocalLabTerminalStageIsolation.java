package spk.local;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Opt-in, World-local diagnostics and exception containment for the five
 * LocalLab progression consumers AFTER canonical G1 record/respawn and drop
 * settlement. This is NOT a transaction log, ACK, deduplication service,
 * replay queue, original SpawnPK kill authority, or durable outbox.
 *
 * A stage returning normally is recorded as RETURNED, not "credited":
 * individual runtimes may legitimately no-op with no active objective.
 * A stage throwing may already have mutated state before the exception,
 * so THREW is deliberately not evidence of an unapplied stage.
 *
 * The bounded journal is in-memory and observational only. Do not use it
 * to grant/replay rewards or remove account review markers.
 */
final class LocalLabTerminalStageIsolation {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_TERMINAL_STAGE_ISOLATION_EXPERIMENT";
    static final int MAX_REPORTS=128;

    enum Stage { G4, G14, G15, G16, G18 }
    enum Outcome {
        RETURNED,
        THREW,
        SKIPPED_NO_CURRENT_RECIPIENT
    }

    static final class Report {
        final EntityId npcId;
        final long deathTick;
        final String recipientRef;
        final long observedGeneration;
        final boolean spawnOriginMatchedAtDispatch;
        final Map<Stage,Outcome> outcomes;

        private Report(
            EntityId npcId,
            long deathTick,
            String recipientRef,
            long observedGeneration,
            boolean spawnOriginMatchedAtDispatch,
            EnumMap<Stage,Outcome> outcomes
        ){
            this.npcId=Objects.requireNonNull(npcId,"npcId");
            this.deathTick=deathTick;
            this.recipientRef=Objects.requireNonNull(
                recipientRef,"recipientRef");
            this.observedGeneration=observedGeneration;
            this.spawnOriginMatchedAtDispatch=
                spawnOriginMatchedAtDispatch;
            this.outcomes=Collections.unmodifiableMap(
                new EnumMap<Stage,Outcome>(outcomes));
        }

        Outcome outcome(Stage stage){
            return outcomes.get(Objects.requireNonNull(stage,"stage"));
        }
    }

    /**
     * Read-only, detached operator diagnostics for a single World-local
     * opt-in dispatcher. This is NOT a ledger of applied rewards; a
     * RETURNED stage can have no active objective, and THREW may
     * follow partial mutation. Never replay from this snapshot.
     */
    /**
     * Observational control-flow totals only, NOT applied rewards or
     * completed durable consumer ACKs. These values deliberately
     * survive eviction from the bounded recent-report ring.
     */
    static final class OutcomeTotals {
        final long returned;
        final long threw;
        final long skipped;

        private OutcomeTotals(long[] values){
            this.returned=values[0];
            this.threw=values[1];
            this.skipped=values[2];
        }
    }

    static final class DiagnosticSnapshot {
        final long observedCount;
        final long evictedCount;
        final List<Report> recentReports;
        final Map<Stage,OutcomeTotals> allTimeStageOutcomes;

        private DiagnosticSnapshot(
            long observedCount,
            long evictedCount,
            List<Report> recentReports,
            EnumMap<Stage,OutcomeTotals> stageTotals
        ){
            this.observedCount=observedCount;
            this.evictedCount=evictedCount;
            this.recentReports=Collections.unmodifiableList(
                new ArrayList<Report>(
                    Objects.requireNonNull(recentReports,"recentReports")
                )
            );
            this.allTimeStageOutcomes=Collections.unmodifiableMap(
                new EnumMap<Stage,OutcomeTotals>(
                    Objects.requireNonNull(stageTotals,"stageTotals")
                )
            );
        }

        OutcomeTotals stage(Stage checkedStage){
            return allTimeStageOutcomes.get(
                Objects.requireNonNull(checkedStage,"stage")
            );
        }
    }

    /*
     * An injected dispatcher must never become a bridge between distinct
     * Worlds. The production factory creates one per World; the package-
     * local injection seam used by tests needs the same identity rule.
     * Identity is not a persistent World ID, replay key or owner epoch.
     */
    private World boundWorld;
    private final ArrayDeque<Report> reports=new ArrayDeque<>();
    private final EnumMap<Stage,long[]> stageOutcomes=
        new EnumMap<Stage,long[]>(Stage.class);
    private long totalObserved;
    private long totalEvicted;

    LocalLabTerminalStageIsolation(){
        for(Stage stage:Stage.values())
            stageOutcomes.put(stage,new long[3]);
    }

    synchronized void bindTo(World world){
        World checked=Objects.requireNonNull(world,"world");
        // A package-local injected dispatcher must not be installed
        // into an already closed World. No lifecycle/reward callbacks
        // or observer ownership can legitimately begin at this point.
        if(checked.closed())
            throw new IllegalStateException(
                "TERMINAL_DIAGNOSTICS_WORLD_CLOSED");
        if(boundWorld==null){
            boundWorld=checked;
            return;
        }
        if(boundWorld!=checked)
            throw new IllegalStateException(
                "TERMINAL_DIAGNOSTICS_WORLD_MISMATCH");
    }

    private synchronized void requireBoundWorld(World checked){
        if(boundWorld!=checked)
            throw new IllegalStateException(
                "TERMINAL_DIAGNOSTICS_WORLD_MISMATCH");
    }

    synchronized DiagnosticSnapshot diagnosticSnapshot(){
        EnumMap<Stage,OutcomeTotals> copied=
            new EnumMap<Stage,OutcomeTotals>(Stage.class);
        for(Stage stage:Stage.values())
            copied.put(stage,new OutcomeTotals(stageOutcomes.get(stage)));
        return new DiagnosticSnapshot(
            totalObserved,totalEvicted,
            new ArrayList<Report>(reports),copied
        );
    }

    synchronized List<Report> history(){
        return diagnosticSnapshot().recentReports;
    }

    /**
     * Called exactly once by the installed observer after G1 is invoked.
     * Never re-invokes G1, settlement, or finalization. The exact current
     * player is captured once and re-checked before EACH stage to prevent
     * accidental forwarding after a player-generation transition between
     * callbacks. This is NOT atomic with each name-based consumer's update.
     */
    Report deliver(
        World world,
        MonsterSpawnerPvmRuntime.FinalizeResult terminal
    ){
        World checked=Objects.requireNonNull(world,"world");
        // Fail before any callback or diagnostics mutation if a package-
        // local caller supplies a terminal from the wrong World.
        requireBoundWorld(checked);
        // Fail closed BEFORE claiming this event and BEFORE any
        // downstream callbacks or diagnostic report, rather than
        // emitting misleading stage results after World teardown.
        // This is an entry check, not a durable in-flight close fence.
        if(checked.closed())
            throw new IllegalStateException(
                "TERMINAL_DIAGNOSTICS_WORLD_CLOSED");
        MonsterSpawnerPvmRuntime.FinalizeResult event=
            Objects.requireNonNull(terminal,"terminal");
        // Binding the DISPATCHER is not enough: a correctly bound
        // dispatcher must also reject finalized evidence produced by
        // another World, even if the usernames and EntityIds coincide.
        // Preserve the old same-World terminal-time recipient policy.
        if(event.sourceWorld!=checked)
            throw new IllegalStateException(
                "TERMINAL_EVENT_SOURCE_WORLD_MISMATCH");
        if(event.status!=MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED||
           event.finalization==null||event.settlement==null)
            throw new IllegalArgumentException(
                "Terminal isolation requires settled FINALIZED event");
        // A terminal may have already committed G1 and ground before
        // reaching this observer. Reusing the same finalized Java
        // result must NEVER replay downstream name-keyed consumers.
        // Fail closed even after the 128-report ring evicts its detail.
        if(!event.claimExperimentalStageDeliveryOnce())
            throw new IllegalStateException(
                "TERMINAL_STAGE_EVENT_ALREADY_DELIVERED_NO_REPLAY");

        MonsterSpawnerNpcDeathFinalizationService.Result death=
            event.finalization;
        String recipientRef=PartyService.requireRef(death.recipientRef);
        WorldPlayer recipient=checked.players().byName(recipientRef);
        long generation=recipient==null?-1L:recipient.generation();
        MonsterSpawnerPvmRuntime.RecipientOrigin origin=
            event.recipientOrigin;
        boolean matched=recipient!=null&&origin!=null&&
            origin.player==recipient&&origin.generation==generation&&
            recipientRef.equals(origin.recipientRef);

        EnumMap<Stage,Outcome> outcomes=
            new EnumMap<Stage,Outcome>(Stage.class);

        for(Stage stage:Stage.values()){
            if(recipient==null||
               checked.players().byName(recipientRef)!=recipient||
               !checked.players().owns(recipient,generation)){
                outcomes.put(stage,Outcome.SKIPPED_NO_CURRENT_RECIPIENT);
                continue;
            }

            try{
                switch(stage){
                    case G4:
                        checked.localLabSlayer()
                            .recordMonsterSpawnerFinalization(
                                recipientRef,death.definitionId,
                                death.deathTile,death.deathTick);
                        break;
                    case G14:
                        checked.localLabDailyChallenges()
                            .recordMonsterSpawnerFinalization(
                                recipientRef,death.definitionId,
                                death.deathTick);
                        break;
                    case G15:
                        checked.localLabTaskScrolls()
                            .recordMonsterSpawnerFinalization(
                                recipientRef,death.definitionId,
                                death.deathTick);
                        break;
                    case G16:
                        checked.localLabAdventures()
                            .recordMonsterSpawnerFinalization(
                                recipientRef,death.definitionId,
                                death.deathTick);
                        break;
                    case G18:
                        checked.localLabDailyMoneyMaking()
                            .recordMonsterSpawnerFinalization(
                                recipientRef,death.definitionId,
                                death.deathTick);
                        break;
                    default:
                        throw new AssertionError("Unknown terminal stage");
                }
                outcomes.put(stage,Outcome.RETURNED);
            }catch(RuntimeException failure){
                outcomes.put(stage,Outcome.THREW);
                System.err.println(
                    "[world] LOCALLAB_TERMINAL_STAGE_FAILED"+
                    " stage="+stage+
                    " npc="+death.npcId+
                    " deathTick="+death.deathTick+
                    " recipient="+recipientRef+
                    " generation="+generation+
                    " error="+failure);
            }
        }

        Report report=new Report(
            death.npcId,death.deathTick,recipientRef,
            generation,matched,outcomes);
        synchronized(this){
            // Saturate observational counters instead of overflowing.
            // Neither counter is used for reward or replay authority.
            if(totalObserved<Long.MAX_VALUE)
                totalObserved++;
            if(reports.size()==MAX_REPORTS){
                reports.removeFirst();
                if(totalEvicted<Long.MAX_VALUE)
                    totalEvicted++;
            }
            reports.addLast(report);
            for(Stage stage:Stage.values()){
                Outcome outcome=Objects.requireNonNull(
                    report.outcome(stage),"terminal stage outcome");
                int slot;
                switch(outcome){
                    case RETURNED: slot=0; break;
                    case THREW: slot=1; break;
                    case SKIPPED_NO_CURRENT_RECIPIENT: slot=2; break;
                    default: throw new AssertionError("Unknown stage outcome");
                }
                long[] counts=stageOutcomes.get(stage);
                if(counts[slot]<Long.MAX_VALUE)
                    counts[slot]++;
            }
        }
        return report;
    }
}

package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EXPERIMENTAL / OBSERVATION ONLY: bounded, process-local reservation of
 * which consumer callbacks MAY have been attempted for a canonical settled
 * NPC death. No callbacks are executed here. No grant, ACK, replay, disk
 * publication, owner epoch or original SpawnPK authority is provided.
 *
 * Deliberately FAILS CLOSED when full. Unlike an evicting diagnostic ring,
 * this fence must never forget earlier in-process reservations and then
 * misrepresent an old event as a fresh unattempted event.
 */
final class LocalLabExperimentalTerminalAttemptFence {
    static final int MAX_ATTEMPTS=256;

    enum Observation {
        RESERVED_UNCERTAIN,
        RETURNED_UNACKNOWLEDGED,
        THREW_MUTATION_UNCERTAIN
    }

    enum Refusal {
        DUPLICATE_CONSUMER,
        CONFLICTING_DEATH_EVIDENCE,
        CAPACITY_FAIL_CLOSED,
        FOREIGN_FENCE,
        ALREADY_OBSERVED,
        UNKNOWN_RESERVATION
    }

    private static final class EventKey {
        final EntityId npcId;
        final long deathTick;
        final String recipientRef;

        EventKey(LocalLabSettledDeathEnvelope envelope){
            this(envelope.npcId,envelope.deathTick,envelope.recipientRef);
        }

        EventKey(EntityId npcId,long deathTick,String recipientRef){
            this.npcId=Objects.requireNonNull(npcId,"npcId");
            this.deathTick=deathTick;
            this.recipientRef=Objects.requireNonNull(recipientRef,"recipientRef");
        }

        @Override public boolean equals(Object candidate){
            if(!(candidate instanceof EventKey))return false;
            EventKey other=(EventKey)candidate;
            return Objects.equals(npcId,other.npcId)&&
                deathTick==other.deathTick&&
                Objects.equals(recipientRef,other.recipientRef);
        }

        @Override public int hashCode(){
            return Objects.hash(npcId,deathTick,recipientRef);
        }
    }

    static final class Attempt {
        final EntityId npcId;
        final int definitionId;
        final Tile deathTile;
        final long spawnRecipientGeneration;
        final long deathTick;
        final String recipientRef;
        final LocalLabSettledDeathEnvelope.OriginStatus originStatus;
        final LocalLabTerminalDeliveryAssessment.Consumer consumer;
        private final LocalLabExperimentalTerminalAttemptFence owner;
        private Observation observation=Observation.RESERVED_UNCERTAIN;
        private String failureType;

        private Attempt(
            LocalLabExperimentalTerminalAttemptFence owner,
            LocalLabSettledDeathEnvelope envelope,
            LocalLabTerminalDeliveryAssessment.Consumer consumer
        ){
            this.owner=owner;
            npcId=envelope.npcId;
            definitionId=envelope.definitionId;
            deathTile=envelope.deathTile;
            spawnRecipientGeneration=envelope.spawnRecipientGeneration;
            deathTick=envelope.deathTick;
            recipientRef=envelope.recipientRef;
            originStatus=envelope.originStatus;
            this.consumer=consumer;
        }
    }

    static final class Entry {
        final EntityId npcId;
        final long deathTick;
        final String recipientRef;
        final LocalLabSettledDeathEnvelope.OriginStatus originStatus;
        final LocalLabTerminalDeliveryAssessment.Consumer consumer;
        final Observation observation;
        final String failureType;

        private Entry(Attempt token){
            npcId=token.npcId;
            deathTick=token.deathTick;
            recipientRef=token.recipientRef;
            originStatus=token.originStatus;
            consumer=token.consumer;
            observation=token.observation;
            failureType=token.failureType;
        }
    }

    static final class Snapshot {
        final int reserved;
        final List<Entry> entries;
        final boolean capacityExhausted;
        final Map<Refusal,Long> refusals;
        final Map<Observation,Long> observations;

        long refusalCount(Refusal refusal){
            return refusals.get(Objects.requireNonNull(refusal,"refusal"));
        }

        long observationCount(Observation observation){
            return observations.get(Objects.requireNonNull(observation,"observation"));
        }

        private Snapshot(List<Entry> entries,
                         EnumMap<Refusal,Long> refusals){
            this.entries=Collections.unmodifiableList(
                new ArrayList<Entry>(entries));
            reserved=entries.size();
            capacityExhausted=reserved==MAX_ATTEMPTS;
            this.refusals=Collections.unmodifiableMap(
                new EnumMap<Refusal,Long>(refusals));
            EnumMap<Observation,Long> counts=
                new EnumMap<Observation,Long>(Observation.class);
            for(Observation observation:Observation.values())
                counts.put(observation,0L);
            for(Entry entry:entries){
                long previous=counts.get(entry.observation);
                if(previous<Long.MAX_VALUE)
                    counts.put(entry.observation,previous+1L);
            }
            observations=Collections.unmodifiableMap(counts);
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }

    private final World world;
    private final Map<EventKey,Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt>>
        attempted=new LinkedHashMap<EventKey,Map<
            LocalLabTerminalDeliveryAssessment.Consumer,Attempt>>();
    private int count;
    private final EnumMap<Refusal,Long> refusals=
        new EnumMap<Refusal,Long>(Refusal.class);

    private void refused(Refusal kind){
        long previous=refusals.get(kind);
        if(previous<Long.MAX_VALUE)refusals.put(kind,previous+1L);
    }

    LocalLabExperimentalTerminalAttemptFence(World world){
        this.world=Objects.requireNonNull(world,"world");
        for(Refusal refusal:Refusal.values())refusals.put(refusal,0L);
    }

    synchronized Attempt reserve(
        MonsterSpawnerPvmRuntime.FinalizeResult terminal,
        LocalLabTerminalDeliveryAssessment.Consumer consumer
    ){
        LocalLabTerminalDeliveryAssessment.Consumer checked=
            Objects.requireNonNull(consumer,"consumer");
        LocalLabSettledDeathEnvelope death=
            LocalLabSettledDeathEnvelope.observe(world,terminal);
        EventKey key=new EventKey(death);
        Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt> existing=
            attempted.get(key);
        if(existing!=null){
            for(Attempt token:existing.values()){
                // Same id/tick/name with different finalized metadata must
                // fail before treating the second record as valid evidence.
                if(token.originStatus!=death.originStatus||
                   token.definitionId!=death.definitionId||
                   !Objects.equals(token.deathTile,death.deathTile)||
                   token.spawnRecipientGeneration!=death.spawnRecipientGeneration){
                    refused(Refusal.CONFLICTING_DEATH_EVIDENCE);
                    throw new IllegalStateException(
                        "TERMINAL_ATTEMPT_CONFLICTING_DEATH_EVIDENCE");
                }
            }
            if(existing.containsKey(checked)){
                refused(Refusal.DUPLICATE_CONSUMER);
                throw new IllegalStateException(
                    "TERMINAL_ATTEMPT_DUPLICATE_CONSUMER");
            }
        }
        if(count>=MAX_ATTEMPTS){
            refused(Refusal.CAPACITY_FAIL_CLOSED);
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_CAPACITY_FAIL_CLOSED");
        }
        Attempt token=new Attempt(this,death,checked);
        if(existing==null){
            existing=new LinkedHashMap<
                LocalLabTerminalDeliveryAssessment.Consumer,Attempt>();
            attempted.put(key,existing);
        }
        existing.put(checked,token);
        count++;
        return token;
    }

    synchronized void returned(Attempt token){
        complete(token,Observation.RETURNED_UNACKNOWLEDGED,null);
    }

    synchronized void threw(Attempt token,Throwable failure){
        complete(
            token,
            Observation.THREW_MUTATION_UNCERTAIN,
            Objects.requireNonNull(failure,"failure").getClass().getName()
        );
    }

    private void complete(
        Attempt token,Observation outcome,String failureType
    ){
        Attempt checked=Objects.requireNonNull(token,"attempt");
        if(checked.owner!=this){
            refused(Refusal.FOREIGN_FENCE);
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_FOREIGN_FENCE");
        }
        if(checked.observation!=Observation.RESERVED_UNCERTAIN){
            refused(Refusal.ALREADY_OBSERVED);
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_ALREADY_OBSERVED");
        }
        Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt> row=
            attempted.get(new EventKey(checked.npcId,checked.deathTick,checked.recipientRef));
        if(row==null||row.get(checked.consumer)!=checked){
            refused(Refusal.UNKNOWN_RESERVATION);
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_UNKNOWN_RESERVATION");
        }
        checked.observation=outcome;
        checked.failureType=failureType;
    }

    synchronized Snapshot snapshot(){
        List<Entry> copies=new ArrayList<Entry>(count);
        for(Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt> row:
                attempted.values())
            for(Attempt token:row.values())
                copies.add(new Entry(token));
        return new Snapshot(copies,refusals);
    }
}

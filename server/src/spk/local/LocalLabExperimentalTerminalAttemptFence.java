package spk.local;

import java.util.ArrayList;
import java.util.Collections;
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

        private Snapshot(List<Entry> entries){
            this.entries=Collections.unmodifiableList(
                new ArrayList<Entry>(entries));
            reserved=entries.size();
            capacityExhausted=reserved==MAX_ATTEMPTS;
        }

        boolean hasDurableGrantAcknowledgement(){return false;}
        boolean safeToAutomaticallyReplay(){return false;}
    }

    private final World world;
    private final Map<EventKey,Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt>>
        attempted=new LinkedHashMap<EventKey,Map<
            LocalLabTerminalDeliveryAssessment.Consumer,Attempt>>();
    private int count;

    LocalLabExperimentalTerminalAttemptFence(World world){
        this.world=Objects.requireNonNull(world,"world");
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
                if(token.originStatus!=death.originStatus)
                    throw new IllegalStateException(
                        "TERMINAL_ATTEMPT_CONFLICTING_ORIGIN_EVIDENCE");
            }
            if(existing.containsKey(checked))
                throw new IllegalStateException(
                    "TERMINAL_ATTEMPT_DUPLICATE_CONSUMER");
        }
        if(count>=MAX_ATTEMPTS)
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_CAPACITY_FAIL_CLOSED");
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
        if(checked.owner!=this)
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_FOREIGN_FENCE");
        if(checked.observation!=Observation.RESERVED_UNCERTAIN)
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_ALREADY_OBSERVED");
        Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt> row=
            attempted.get(new EventKey(checked.npcId,checked.deathTick,checked.recipientRef));
        if(row==null||row.get(checked.consumer)!=checked)
            throw new IllegalStateException(
                "TERMINAL_ATTEMPT_UNKNOWN_RESERVATION");
        checked.observation=outcome;
        checked.failureType=failureType;
    }

    synchronized Snapshot snapshot(){
        List<Entry> copies=new ArrayList<Entry>(count);
        for(Map<LocalLabTerminalDeliveryAssessment.Consumer,Attempt> row:
                attempted.values())
            for(Attempt token:row.values())
                copies.add(new Entry(token));
        return new Snapshot(copies);
    }
}

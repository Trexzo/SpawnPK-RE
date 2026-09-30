package spk.local;

import java.util.*;

/**
 * Protocol-independent PvP Hotspot gameplay composition.
 *
 * The exact client proves hotspot presentation/state exists, but original
 * location rotation, kill-credit, multipliers, rewards and teleport behavior
 * remain external. This service owns only semantic event presence and
 * caller-validated PvP kill bookkeeping.
 */
final class PvpHotspotService {
    static final class ParticipantSnapshot {
        final String participantRef;
        final boolean present;
        final long kills;
        final long deaths;

        ParticipantSnapshot(Participant participant){
            this.participantRef=
                participant.participantRef;
            this.present=participant.present;
            this.kills=participant.kills;
            this.deaths=participant.deaths;
        }
    }

    static final class Snapshot {
        final WorldEventId eventId;
        final String zoneKey;
        final String policyAuthority;
        final GlobalEventService.Lifecycle lifecycle;
        final List<ParticipantSnapshot> participants;

        Snapshot(
            Entry entry,
            GlobalEventService.Snapshot event
        ){
            this.eventId=entry.eventId;
            this.zoneKey=entry.zoneKey;
            this.policyAuthority=
                entry.policyAuthority;
            this.lifecycle=event.lifecycle;

            ArrayList<ParticipantSnapshot> out=
                new ArrayList<>();

            for(Participant participant:
                    entry.participants.values())
                out.add(
                    new ParticipantSnapshot(
                        participant
                    )
                );

            this.participants=
                Collections.unmodifiableList(
                    out
                );
        }

        ParticipantSnapshot participant(
            String participantRef
        ){
            String ref=
                PartyService.requireRef(
                    participantRef
                );

            for(ParticipantSnapshot participant:
                    participants)
                if(participant.participantRef
                        .equals(ref))
                    return participant;

            return null;
        }

        int presentCount(){
            int count=0;

            for(ParticipantSnapshot participant:
                    participants)
                if(participant.present)
                    count++;

            return count;
        }

        boolean terminal(){
            return lifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED||
                lifecycle==
                    GlobalEventService
                        .Lifecycle.CANCELLED;
        }
    }

    static final class KillResult {
        final WorldEventId eventId;
        final String attackerRef;
        final String victimRef;
        final long attackerKills;
        final long victimDeaths;
        final long worldTick;

        KillResult(
            WorldEventId eventId,
            Participant attacker,
            Participant victim,
            long worldTick
        ){
            this.eventId=eventId;
            this.attackerRef=
                attacker.participantRef;
            this.victimRef=
                victim.participantRef;
            this.attackerKills=
                attacker.kills;
            this.victimDeaths=
                victim.deaths;
            this.worldTick=worldTick;
        }
    }

    private static final class Participant {
        final String participantRef;
        boolean present;
        long kills;
        long deaths;

        Participant(String participantRef){
            this.participantRef=
                participantRef;
        }
    }

    private static final class Entry {
        final WorldEventId eventId;
        final String zoneKey;
        final String policyAuthority;
        final LinkedHashMap<String,Participant>
            participants=
                new LinkedHashMap<>();

        Entry(
            WorldEventId eventId,
            String zoneKey,
            String policyAuthority
        ){
            this.eventId=eventId;
            this.zoneKey=zoneKey;
            this.policyAuthority=
                policyAuthority;
        }
    }

    private final GlobalEventService events;

    private final LinkedHashMap<WorldEventId,Entry>
        hotspots=
            new LinkedHashMap<>();

    PvpHotspotService(
        GlobalEventService events
    ){
        this.events=
            Objects.requireNonNull(
                events,
                "events"
            );
    }

    synchronized Snapshot registerHotspot(
        WorldEventDefinition definition,
        String zoneKey,
        String policyAuthority
    ){
        WorldEventDefinition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        String authority=
            MatchRules.requireText(
                policyAuthority,
                "policyAuthority"
            );

        if(hotspots.containsKey(
                checked.id))
            throw new IllegalStateException(
                "duplicate PvP Hotspot "+
                checked.id
            );

        if(events.get(checked.id)!=null)
            throw new IllegalStateException(
                "GlobalEvent already exists "+
                checked.id
            );

        if(!authority.equals(
                checked.sourceAuthority))
            throw new IllegalArgumentException(
                "PvP Hotspot event authority mismatch"
            );

        Entry entry=
            new Entry(
                checked.id,
                MatchRules.normalizeKey(
                    zoneKey,
                    "zoneKey"
                ),
                authority
            );

        // All local validation is complete before mutating GlobalEventService.
        final Snapshot[] result=
            new Snapshot[1];

        try{
            events.registerWithCompositionOwnership(
                checked,
                ()->{
                    Snapshot created=
                        snapshotOf(
                            entry
                        );
                    hotspots.put(
                        entry.eventId,
                        entry
                    );
                    result[0]=created;
                }
            );
        }catch(RuntimeException failure){
            hotspots.remove(
                entry.eventId,
                entry
            );
            throw failure;
        }catch(Error failure){
            hotspots.remove(
                entry.eventId,
                entry
            );
            throw failure;
        }catch(Exception failure){
            hotspots.remove(
                entry.eventId,
                entry
            );
            throw new IllegalStateException(
                "unexpected PvP Hotspot GlobalEvent registration ownership failure",
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "registered PvP Hotspot snapshot"
        );
    }

    synchronized Snapshot enter(
        WorldEventId eventId,
        String participantRef,
        long worldTick
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    observe(
                        entry,
                        worldTick
                    );

                requireActive(
                    event,
                    "enter"
                );

                String participantRefNormalized=
                    PartyService.requireRef(
                        participantRef
                    );

                Participant participant=
                    entry.participants.get(
                        participantRefNormalized
                    );

                if(participant==null){
                    participant=
                        new Participant(
                            participantRefNormalized
                        );
                    entry.participants.put(
                        participantRefNormalized,
                        participant
                    );
                }

                if(participant.present)
                    throw new IllegalStateException(
                        "participant already present "+
                        participantRefNormalized
                    );

                participant.present=true;

                result[0]=
                    snapshotOf(
                        entry,
                        event
                    );
            }
        );

        return result[0];
    }

    synchronized Snapshot leave(
        WorldEventId eventId,
        String participantRef,
        long worldTick
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    observe(
                        entry,
                        worldTick
                    );

                requireActive(
                    event,
                    "leave"
                );

                Participant participant=
                    requireParticipant(
                        entry,
                        participantRef
                    );

                if(!participant.present)
                    throw new IllegalStateException(
                        "participant not present "+
                        participant.participantRef
                    );

                participant.present=false;

                result[0]=
                    snapshotOf(
                        entry,
                        event
                    );
            }
        );

        return result[0];
    }

    synchronized KillResult recordValidatedKill(
        WorldEventId eventId,
        String attackerRef,
        String victimRef,
        long worldTick
    ){
        Entry entry=require(eventId);
        final KillResult[] result=
            new KillResult[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    observe(
                        entry,
                        worldTick
                    );

                requireActive(
                    event,
                    "recordValidatedKill"
                );

                Participant attacker=
                    requireParticipant(
                        entry,
                        attackerRef
                    );
                Participant victim=
                    requireParticipant(
                        entry,
                        victimRef
                    );

                if(attacker.participantRef.equals(
                    victim.participantRef))
                    throw new IllegalArgumentException(
                        "PvP Hotspot self-kill"
                    );

                if(!attacker.present)
                    throw new IllegalStateException(
                        "attacker not present "+
                        attacker.participantRef
                    );

                if(!victim.present)
                    throw new IllegalStateException(
                        "victim not present "+
                        victim.participantRef
                    );

                final long nextAttackerKills;
                final long nextVictimDeaths;

                try{
                    nextAttackerKills=
                        Math.addExact(
                            attacker.kills,
                            1L
                        );
                    nextVictimDeaths=
                        Math.addExact(
                            victim.deaths,
                            1L
                        );
                }catch(
                    ArithmeticException overflow
                ){
                    throw new IllegalStateException(
                        "PvP Hotspot counter overflow",
                        overflow
                    );
                }

                attacker.kills=
                    nextAttackerKills;
                victim.deaths=
                    nextVictimDeaths;

                result[0]=
                    new KillResult(
                        entry.eventId,
                        attacker,
                        victim,
                        worldTick
                    );
            }
        );

        return result[0];
    }

    synchronized Snapshot complete(
        WorldEventId eventId,
        long worldTick
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.MutationResult mutation=
                    events.complete(
                        entry.eventId,
                        worldTick
                    );

                clearPresence(entry);

                result[0]=
                    snapshotOf(
                        entry,
                        mutation.snapshot
                    );
            }
        );

        return result[0];
    }

    synchronized Snapshot cancel(
        WorldEventId eventId,
        long worldTick
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.MutationResult mutation=
                    events.cancel(
                        entry.eventId,
                        worldTick
                    );

                clearPresence(entry);

                result[0]=
                    snapshotOf(
                        entry,
                        mutation.snapshot
                    );
            }
        );

        return result[0];
    }

    synchronized Snapshot get(
        WorldEventId eventId
    ){
        Entry entry=
            hotspots.get(
                Objects.requireNonNull(
                    eventId,
                    "eventId"
                )
            );

        return entry==null
            ?null
            :snapshotOf(entry);
    }

    synchronized int size(){
        return hotspots.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=
            new ArrayList<>(
                hotspots.values()
            );

        ordered.sort(
            Comparator.comparing(
                value->
                    value.eventId
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:ordered)
            out.add(
                snapshotOf(entry)
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private GlobalEventService.Snapshot observe(
        Entry entry,
        long worldTick
    ){
        // GlobalEventService owns monotonic world-tick observation and also
        // advances scheduled/end deadlines before hotspot gameplay mutation.
        events.tick(worldTick);

        return reconcileTerminalPresence(
            entry
        );
    }

    private Snapshot snapshotOf(
        Entry entry
    ){
        return snapshotOf(
            entry,
            reconcileTerminalPresence(
                entry
            )
        );
    }

    private GlobalEventService.Snapshot
        reconcileTerminalPresence(
            Entry entry
        ){
        GlobalEventService.Snapshot event=
            requireEvent(entry);

        /*
         * Backing GlobalEvent lifecycle may become terminal without passing
         * through this service: end-deadline advancement and direct caller
         * complete/cancel are both valid GlobalEvent operations. Presence is
         * transient application state, so every Hotspot observation/projected
         * snapshot must reconcile it to zero once that backing lifecycle is
         * terminal. Historical kill/death counters remain untouched.
         */
        if(event.terminal())
            clearPresence(entry);

        return event;
    }

    private static Snapshot snapshotOf(
        Entry entry,
        GlobalEventService.Snapshot event
    ){
        return new Snapshot(
            entry,
            event
        );
    }

    private Entry require(
        WorldEventId eventId
    ){
        WorldEventId id=
            Objects.requireNonNull(
                eventId,
                "eventId"
            );

        Entry entry=
            hotspots.get(id);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown PvP Hotspot "+
                id
            );

        return entry;
    }

    private void withEventCompositionOwnership(
        WorldEventId eventId,
        GlobalEventService.EventCompositionAction action
    ){
        try{
            events.withEventCompositionOwnership(
                eventId,
                action
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected PvP Hotspot GlobalEvent ownership failure",
                failure
            );
        }
    }

    private GlobalEventService.Snapshot
        requireEvent(
            Entry entry
        ){
        GlobalEventService.Snapshot event=
            events.get(
                entry.eventId
            );

        if(event==null)
            throw new IllegalStateException(
                "backing GlobalEvent disappeared "+
                entry.eventId
            );

        return event;
    }

    private static Participant requireParticipant(
        Entry entry,
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        Participant value=
            entry.participants.get(
                participant
            );

        if(value==null)
            throw new IllegalArgumentException(
                "unknown PvP Hotspot participant "+
                participant
            );

        return value;
    }

    private static void requireActive(
        GlobalEventService.Snapshot event,
        String operation
    ){
        if(event.lifecycle!=
                GlobalEventService
                    .Lifecycle.ACTIVE)
            throw new IllegalStateException(
                operation+
                " requires ACTIVE PvP Hotspot lifecycle="+
                event.lifecycle
            );
    }

    private static void clearPresence(
        Entry entry
    ){
        for(Participant participant:
                entry.participants.values())
            participant.present=false;
    }
}

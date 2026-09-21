package spk.local;

import java.util.*;

/**
 * Concrete protocol-independent Raid run lifecycle composed from PartyService
 * and WorldInstanceService.
 *
 * Client research proves Raid presentation concepts such as readiness, party
 * rows, stage 0..5, points and time. Encounter rules, eligibility, rewards and
 * scoring formulas remain caller-owned policy.
 */
final class RaidRunService {
    static final int MIN_STAGE=0;
    static final int MAX_STAGE=5;
    static final long NO_TICK=-1L;

    enum Lifecycle {
        LOBBY,
        ACTIVE,
        COMPLETED,
        WIPED,
        CANCELLED
    }

    static final class RaidRunId
        implements Comparable<RaidRunId> {

        private final String value;

        RaidRunId(String value){
            this.value=normalizeKey(
                value,
                "raidRunId"
            );
        }

        static RaidRunId of(
            String value
        ){
            return new RaidRunId(value);
        }

        String value(){
            return value;
        }

        @Override public int compareTo(
            RaidRunId other
        ){
            return value.compareTo(
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(
            Object other
        ){
            return other instanceof RaidRunId&&
                value.equals(
                    ((RaidRunId)other).value
                );
        }

        @Override public int hashCode(){
            return value.hashCode();
        }

        @Override public String toString(){
            return value;
        }
    }

    static final class ParticipantSnapshot {
        final String participantRef;
        final boolean ready;
        final long points;

        ParticipantSnapshot(
            Participant participant
        ){
            this.participantRef=
                participant.participantRef;
            this.ready=participant.ready;
            this.points=participant.points;
        }
    }

    static final class Snapshot {
        final RaidRunId id;
        final PartyId partyId;
        final String raidKey;
        final String difficultyKey;
        final String leaderRef;
        final Lifecycle lifecycle;
        final List<ParticipantSnapshot> participants;
        final int stage;
        final WorldInstanceId instanceId;
        final long startTick;
        final long terminalTick;
        final String sourceAuthority;
        final String policyAuthority;

        Snapshot(
            Entry entry
        ){
            this.id=entry.id;
            this.partyId=entry.partyId;
            this.raidKey=entry.raidKey;
            this.difficultyKey=
                entry.difficultyKey;
            this.leaderRef=entry.leaderRef;
            this.lifecycle=entry.lifecycle;

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
            this.stage=entry.stage;
            this.instanceId=entry.instanceId;
            this.startTick=entry.startTick;
            this.terminalTick=
                entry.terminalTick;
            this.sourceAuthority=
                entry.sourceAuthority;
            this.policyAuthority=
                entry.policyAuthority;
        }

        boolean allReady(){
            if(participants.isEmpty())
                return false;

            for(ParticipantSnapshot participant:
                    participants)
                if(!participant.ready)
                    return false;

            return true;
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

        boolean terminal(){
            return lifecycle==
                    Lifecycle.COMPLETED||
                lifecycle==
                    Lifecycle.WIPED||
                lifecycle==
                    Lifecycle.CANCELLED;
        }

        long elapsedTicks(
            long currentTick
        ){
            if(startTick==NO_TICK)
                return 0L;

            long end=
                terminalTick==NO_TICK
                    ?currentTick
                    :terminalTick;

            if(end<startTick)
                throw new IllegalArgumentException(
                    "currentTick before raid start"
                );

            return end-startTick;
        }
    }

    private static final class Participant {
        final String participantRef;
        boolean ready;
        long points;

        Participant(
            String participantRef
        ){
            this.participantRef=
                participantRef;
        }
    }

    private static final class Entry {
        final RaidRunId id;
        final PartyId partyId;
        final String raidKey;
        final String difficultyKey;
        final String leaderRef;
        final LinkedHashMap<String,Participant>
            participants=
                new LinkedHashMap<>();
        final String sourceAuthority;
        final String policyAuthority;

        Lifecycle lifecycle=
            Lifecycle.LOBBY;
        int stage=MIN_STAGE;
        WorldInstanceId instanceId;
        long startTick=NO_TICK;
        long terminalTick=NO_TICK;

        Entry(
            RaidRunId id,
            PartyId partyId,
            String raidKey,
            String difficultyKey,
            String leaderRef,
            Collection<String> participantRefs,
            String sourceAuthority,
            String policyAuthority
        ){
            this.id=id;
            this.partyId=partyId;
            this.raidKey=raidKey;
            this.difficultyKey=
                difficultyKey;
            this.leaderRef=leaderRef;
            this.sourceAuthority=
                sourceAuthority;
            this.policyAuthority=
                policyAuthority;

            for(String participantRef:
                    participantRefs){
                String ref=
                    PartyService.requireRef(
                        participantRef
                    );

                if(participants.put(
                        ref,
                        new Participant(ref))!=null)
                    throw new IllegalStateException(
                        "duplicate raid participant "+
                        ref
                    );
            }

            if(!participants.containsKey(
                    leaderRef))
                throw new IllegalStateException(
                    "raid leader not in party snapshot "+
                    leaderRef
                );
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final PartyService parties;
    private final WorldInstanceService instances;

    private final LinkedHashMap<RaidRunId,Entry>
        runs=
            new LinkedHashMap<>();

    RaidRunService(
        PartyService parties,
        WorldInstanceService instances
    ){
        this.parties=
            Objects.requireNonNull(
                parties,
                "parties"
            );
        this.instances=
            Objects.requireNonNull(
                instances,
                "instances"
            );
    }

    synchronized Snapshot createLobby(
        RaidRunId id,
        PartyId partyId,
        String raidKey,
        String difficultyKey,
        String sourceAuthority,
        String policyAuthority
    ){
        RaidRunId checkedId=
            Objects.requireNonNull(
                id,
                "id"
            );

        if(runs.containsKey(
                checkedId))
            throw new IllegalStateException(
                "duplicate raid run id="+
                checkedId
            );

        PartyId checkedPartyId=
            Objects.requireNonNull(
                partyId,
                "partyId"
            );

        PartyService.Snapshot party=
            parties.get(
                checkedPartyId
            );

        if(party==null)
            throw new IllegalArgumentException(
                "unknown raid party id="+
                checkedPartyId
            );

        if(party.members.isEmpty())
            throw new IllegalStateException(
                "raid party has no members "+
                checkedPartyId
            );

        Entry entry=
            new Entry(
                checkedId,
                checkedPartyId,
                normalizeKey(
                    raidKey,
                    "raidKey"
                ),
                normalizeKey(
                    difficultyKey,
                    "difficultyKey"
                ),
                PartyService.requireRef(
                    party.leaderRef
                ),
                party.members,
                requireAuthority(
                    sourceAuthority,
                    "sourceAuthority"
                ),
                requireAuthority(
                    policyAuthority,
                    "policyAuthority"
                )
            );

        runs.put(
            checkedId,
            entry
        );

        return entry.snapshot();
    }

    synchronized Snapshot setReady(
        RaidRunId id,
        String participantRef,
        boolean ready
    ){
        Entry entry=
            requireLobby(id);
        Participant participant=
            requireParticipant(
                entry,
                participantRef
            );

        participant.ready=ready;
        return entry.snapshot();
    }

    synchronized Snapshot start(
        RaidRunId id,
        String requestedBy,
        WorldInstanceId instanceId,
        long worldTick
    ){
        Entry entry=
            requireLobby(id);
        String requester=
            PartyService.requireRef(
                requestedBy
            );

        if(!entry.leaderRef.equals(
                requester))
            throw new IllegalStateException(
                "only raid leader may start run="+
                entry.id+
                " leader="+entry.leaderRef+
                " requester="+requester
            );

        if(!entry.snapshot().allReady())
            throw new IllegalStateException(
                "raid party not fully ready run="+
                entry.id
            );

        observeTick(
            worldTick,
            NO_TICK
        );

        WorldInstanceId instance=
            Objects.requireNonNull(
                instanceId,
                "instanceId"
            );

        if(instances.get(instance)!=null)
            throw new IllegalStateException(
                "raid world instance already exists "+
                instance
            );

        instances.create(
            instance,
            entry.partyId.toString(),
            entry.sourceAuthority
        );

        for(Participant participant:
                entry.participants.values())
            instances.attach(
                instance,
                participant.participantRef
            );

        instances.activate(
            instance
        );

        entry.instanceId=instance;
        entry.startTick=worldTick;
        entry.stage=MIN_STAGE;
        entry.lifecycle=Lifecycle.ACTIVE;

        return entry.snapshot();
    }

    synchronized Snapshot advanceStage(
        RaidRunId id,
        int nextStage
    ){
        Entry entry=
            requireActive(id);

        if(nextStage<MIN_STAGE||
           nextStage>MAX_STAGE)
            throw new IllegalArgumentException(
                "raid stage="+nextStage
            );

        if(nextStage!=entry.stage+1)
            throw new IllegalStateException(
                "raid stage must advance exactly one step "+
                entry.stage+" -> "+nextStage
            );

        entry.stage=nextStage;
        return entry.snapshot();
    }

    synchronized Snapshot awardPoints(
        RaidRunId id,
        String participantRef,
        long amount
    ){
        Entry entry=
            requireActive(id);

        if(amount<=0L)
            throw new IllegalArgumentException(
                "points amount="+amount
            );

        Participant participant=
            requireParticipant(
                entry,
                participantRef
            );

        try{
            participant.points=
                Math.addExact(
                    participant.points,
                    amount
                );
        }catch(
            ArithmeticException overflow
        ){
            throw new IllegalArgumentException(
                "raid points overflow participant="+
                participant.participantRef,
                overflow
            );
        }

        return entry.snapshot();
    }

    synchronized Snapshot complete(
        RaidRunId id,
        long worldTick
    ){
        return terminateActive(
            id,
            Lifecycle.COMPLETED,
            worldTick
        );
    }

    synchronized Snapshot wipe(
        RaidRunId id,
        long worldTick
    ){
        return terminateActive(
            id,
            Lifecycle.WIPED,
            worldTick
        );
    }

    synchronized Snapshot cancelLobby(
        RaidRunId id,
        long worldTick
    ){
        Entry entry=
            requireLobby(id);

        observeTick(
            worldTick,
            NO_TICK
        );

        entry.lifecycle=
            Lifecycle.CANCELLED;
        entry.terminalTick=
            worldTick;

        return entry.snapshot();
    }

    synchronized Snapshot get(
        RaidRunId id
    ){
        Entry entry=
            runs.get(
                Objects.requireNonNull(
                    id,
                    "id"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized int size(){
        return runs.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=
            new ArrayList<>(
                runs.values()
            );

        ordered.sort(
            Comparator.comparing(
                value->value.id
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:
                ordered)
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private Snapshot terminateActive(
        RaidRunId id,
        Lifecycle terminal,
        long worldTick
    ){
        Entry entry=
            requireActive(id);

        if(terminal!=Lifecycle.COMPLETED&&
           terminal!=Lifecycle.WIPED)
            throw new IllegalArgumentException(
                "invalid active raid terminal="+
                terminal
            );

        observeTick(
            worldTick,
            entry.startTick
        );

        WorldInstanceService.Snapshot instance=
            instances.get(
                Objects.requireNonNull(
                    entry.instanceId,
                    "raid instance"
                )
            );

        if(instance==null||
           instance.lifecycle!=
                WorldInstanceService
                    .Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "raid world instance not active run="+
                entry.id+
                " instance="+entry.instanceId
            );

        LinkedHashSet<String> expected=
            new LinkedHashSet<>(
                entry.participants.keySet()
            );

        LinkedHashSet<String> actual=
            new LinkedHashSet<>(
                instance.participants
            );

        if(!expected.equals(actual))
            throw new IllegalStateException(
                "raid world instance participant drift run="+
                entry.id+
                " expected="+expected+
                " actual="+actual
            );

        instances.beginClosing(
            entry.instanceId
        );

        for(String participant:
                expected)
            instances.detach(
                entry.instanceId,
                participant
            );

        instances.close(
            entry.instanceId
        );

        entry.lifecycle=terminal;
        entry.terminalTick=worldTick;

        return entry.snapshot();
    }

    private Entry requireLobby(
        RaidRunId id
    ){
        Entry entry=require(id);

        if(entry.lifecycle!=
                Lifecycle.LOBBY)
            throw invalid(
                entry,
                "lobby mutation"
            );

        return entry;
    }

    private Entry requireActive(
        RaidRunId id
    ){
        Entry entry=require(id);

        if(entry.lifecycle!=
                Lifecycle.ACTIVE)
            throw invalid(
                entry,
                "active mutation"
            );

        return entry;
    }

    private Entry require(
        RaidRunId id
    ){
        RaidRunId key=
            Objects.requireNonNull(
                id,
                "id"
            );
        Entry entry=runs.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown raid run id="+key
            );

        return entry;
    }

    private static Participant requireParticipant(
        Entry entry,
        String participantRef
    ){
        String ref=
            PartyService.requireRef(
                participantRef
            );

        Participant participant=
            entry.participants.get(ref);

        if(participant==null)
            throw new IllegalArgumentException(
                "participant not in raid run "+
                entry.id+
                " ref="+ref
            );

        return participant;
    }

    private static IllegalStateException invalid(
        Entry entry,
        String operation
    ){
        return new IllegalStateException(
            operation+
            " invalid from "+
            entry.lifecycle+
            " for "+
            entry.id
        );
    }

    private static void observeTick(
        long worldTick,
        long lowerBound
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        if(lowerBound!=NO_TICK&&
           worldTick<lowerBound)
            throw new IllegalArgumentException(
                "raid world tick moved backwards "+
                worldTick+
                " < "+lowerBound
            );
    }

    private static String normalizeKey(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(
                label
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        if(normalized.length()>128)
            throw new IllegalArgumentException(
                label+" too long"
            );

        for(int i=0;
            i<normalized.length();
            i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||
                c=='-'||c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid "+label+"="+value
                );
        }

        return normalized;
    }

    private static String requireAuthority(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(
                label
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        return normalized;
    }
}

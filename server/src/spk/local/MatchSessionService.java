package spk.local;

import java.util.*;

/**
 * Mutable owner of semantic match/team/scoring lifecycle.
 *
 * Matchmaking assignment, world-instance lifecycle, combat enforcement,
 * rewards and winner policy are external.
 */
final class MatchSessionService {
    private static final class TeamState {
        final MatchTeamId id;
        final LinkedHashSet<String> members=
            new LinkedHashSet<>();
        final LinkedHashMap<String,Long> scores=
            new LinkedHashMap<>();

        TeamState(MatchTeamId id){
            this.id=id;
        }
    }

    private static final class ParticipantState {
        final String participantRef;
        final MatchTeamId teamId;
        final LinkedHashMap<String,Long> scores=
            new LinkedHashMap<>();

        MatchSession.ParticipantStatus status=
            MatchSession.ParticipantStatus.PRESENT;

        ParticipantState(
            String participantRef,
            MatchTeamId teamId
        ){
            this.participantRef=participantRef;
            this.teamId=teamId;
        }
    }

    private static final class Entry {
        final MatchId id;
        final MatchRules rules;
        final LinkedHashMap<MatchTeamId,TeamState> teams=
            new LinkedHashMap<>();
        final LinkedHashMap<String,ParticipantState> participants=
            new LinkedHashMap<>();
        final LinkedHashSet<String> terminalHolds=
            new LinkedHashSet<>();

        MatchSession.State state=
            MatchSession.State.CREATED;

        WorldInstanceId instanceId;
        MatchSession.Result result;
        String cancellationReasonKey;

        Entry(MatchId id,MatchRules rules){
            this.id=id;
            this.rules=rules;
        }
    }

    private final LinkedHashMap<MatchId,Entry> matches=
        new LinkedHashMap<>();

    interface MatchInstanceCompositionAction {
        void run() throws Exception;
    }

    synchronized void withWorldInstanceCompositionOwnership(
        WorldInstanceService instances,
        MatchInstanceCompositionAction action
    )throws Exception{
        Objects.requireNonNull(
            instances,
            "instances"
        );
        Objects.requireNonNull(
            action,
            "action"
        );

        instances.withMatchCompositionOwnership(
            action
        );
    }

    synchronized void acquireTerminalHold(
        MatchId matchId,
        String holdKey
    ){
        Entry entry=requireActive(matchId);
        String key=requireHoldKey(holdKey);

        if(!entry.terminalHolds.add(key))
            throw new IllegalStateException(
                "duplicate MatchSession terminal hold match="+
                entry.id+
                " key="+key
            );
    }

    synchronized void releaseTerminalHold(
        MatchId matchId,
        String holdKey
    ){
        Entry entry=require(matchId);
        String key=requireHoldKey(holdKey);

        if(!entry.terminalHolds.remove(key))
            throw new IllegalStateException(
                "missing MatchSession terminal hold match="+
                entry.id+
                " key="+key
            );
    }

    synchronized int terminalHoldCount(
        MatchId matchId
    ){
        return require(matchId)
            .terminalHolds.size();
    }

    synchronized MatchSession create(
        MatchId id,
        MatchRules rules
    ){
        MatchId key=Objects.requireNonNull(id,"id");

        if(matches.containsKey(key))
            throw new IllegalStateException(
                "duplicate match id="+key
            );

        Entry entry=
            new Entry(
                key,
                Objects.requireNonNull(
                    rules,
                    "rules"
                )
            );

        matches.put(key,entry);
        return snapshot(entry);
    }

    synchronized MatchSession addTeam(
        MatchId matchId,
        MatchTeamId teamId
    ){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.CREATED,
            "addTeam"
        );

        MatchTeamId team=
            Objects.requireNonNull(
                teamId,
                "teamId"
            );

        if(entry.teams.containsKey(team))
            throw new IllegalStateException(
                "duplicate team id="+team
            );

        entry.teams.put(
            team,
            new TeamState(team)
        );

        return snapshot(entry);
    }

    synchronized MatchSession join(
        MatchId matchId,
        MatchTeamId teamId,
        String participantRef
    ){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.CREATED,
            "join"
        );

        MatchTeamId team=
            Objects.requireNonNull(
                teamId,
                "teamId"
            );

        TeamState teamState=
            entry.teams.get(team);

        if(teamState==null)
            throw new IllegalArgumentException(
                "unknown team id="+team
            );

        String participant=
            PartyService.requireRef(
                participantRef
            );

        if(entry.participants.containsKey(participant))
            throw new IllegalStateException(
                "duplicate match participant "+
                participant
            );

        ParticipantState state=
            new ParticipantState(
                participant,
                team
            );

        entry.participants.put(
            participant,
            state
        );
        teamState.members.add(participant);

        return snapshot(entry);
    }

    synchronized MatchSession attachInstance(
        MatchId matchId,
        WorldInstanceId instanceId
    ){
        Entry entry=require(matchId);

        if(entry.state!=MatchSession.State.CREATED&&
           entry.state!=MatchSession.State.READY)
            throw invalid(
                entry,
                "attachInstance"
            );

        WorldInstanceId instance=
            Objects.requireNonNull(
                instanceId,
                "instanceId"
            );

        if(entry.instanceId!=null){
            if(entry.instanceId.equals(instance))
                return snapshot(entry);

            throw new IllegalStateException(
                "match already attached to "+
                entry.instanceId
            );
        }

        entry.instanceId=instance;
        return snapshot(entry);
    }

    synchronized MatchSession markReady(MatchId matchId){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.CREATED,
            "markReady"
        );

        if(entry.teams.isEmpty())
            throw new IllegalStateException(
                "match has no teams"
            );

        if(!hasPresentParticipant(entry))
            throw new IllegalStateException(
                "match has no present participants"
            );

        entry.state=MatchSession.State.READY;
        return snapshot(entry);
    }

    synchronized MatchSession activate(MatchId matchId){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.READY,
            "activate"
        );

        if(entry.instanceId==null)
            throw new IllegalStateException(
                "match has no WorldInstance reference"
            );

        if(!hasPresentParticipant(entry))
            throw new IllegalStateException(
                "match has no present participants"
            );

        entry.state=MatchSession.State.ACTIVE;
        return snapshot(entry);
    }

    synchronized MatchSession adjustTeamScore(
        MatchId matchId,
        MatchTeamId teamId,
        String counterKey,
        long delta
    ){
        Entry entry=requireActive(matchId);
        TeamState team=
            entry.teams.get(
                Objects.requireNonNull(
                    teamId,
                    "teamId"
                )
            );

        if(team==null)
            throw new IllegalArgumentException(
                "unknown team id="+teamId
            );

        adjustScore(
            team.scores,
            counterKey,
            delta
        );

        return snapshot(entry);
    }

    synchronized MatchSession adjustParticipantScore(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta
    ){
        Entry entry=requireActive(matchId);
        ParticipantState participant=
            requireParticipant(
                entry,
                participantRef
            );

        if(participant.status!=
                MatchSession.ParticipantStatus.PRESENT)
            throw new IllegalStateException(
                "participant not present "+
                participant.participantRef+
                " status="+participant.status
            );

        adjustScore(
            participant.scores,
            counterKey,
            delta
        );

        return snapshot(entry);
    }

    /**
     * Atomically score one participant only while the match is ACTIVE and the
     * participant is still PRESENT. Returns false for a terminal/inactive
     * match or a no-longer-present participant instead of exposing a
     * snapshot->mutation race to semantic observers.
     */
    synchronized boolean tryAdjustPresentParticipantScore(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta
    ){
        Entry entry=require(matchId);

        if(entry.state!=
                MatchSession.State.ACTIVE)
            return false;

        ParticipantState participant=
            requireParticipant(
                entry,
                participantRef
            );

        if(participant.status!=
                MatchSession.ParticipantStatus.PRESENT)
            return false;

        adjustScore(
            participant.scores,
            counterKey,
            delta
        );

        return true;
    }

    /**
     * Atomically resolve the PRESENT participant's current team and score that
     * team. Presence and team resolution happen under the same match monitor.
     */
    synchronized boolean tryAdjustPresentParticipantTeamScore(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta
    ){
        Entry entry=require(matchId);

        if(entry.state!=
                MatchSession.State.ACTIVE)
            return false;

        ParticipantState participant=
            requireParticipant(
                entry,
                participantRef
            );

        if(participant.status!=
                MatchSession.ParticipantStatus.PRESENT)
            return false;

        TeamState team=
            entry.teams.get(
                participant.teamId
            );

        if(team==null)
            throw new IllegalStateException(
                "participant team disappeared "+
                participant.teamId+
                " ref="+
                participant.participantRef
            );

        adjustScore(
            team.scores,
            counterKey,
            delta
        );

        return true;
    }

    synchronized MatchSession leave(
        MatchId matchId,
        String participantRef
    ){
        return markParticipant(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.LEFT
        );
    }

    synchronized MatchSession forfeit(
        MatchId matchId,
        String participantRef
    ){
        return markParticipant(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.FORFEITED
        );
    }

    synchronized MatchSession disconnect(
        MatchId matchId,
        String participantRef
    ){
        return markParticipant(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.DISCONNECTED
        );
    }

    synchronized MatchSession complete(
        MatchId matchId,
        MatchSession.Result result
    ){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.ACTIVE,
            "complete"
        );
        requireNoTerminalHolds(
            entry,
            "complete"
        );

        MatchSession.Result checked=
            Objects.requireNonNull(
                result,
                "result"
            );

        if(checked.winnerTeamId!=null&&
           !entry.teams.containsKey(
                checked.winnerTeamId))
            throw new IllegalArgumentException(
                "winner team not in match "+
                checked.winnerTeamId
            );

        entry.result=checked;
        entry.state=MatchSession.State.COMPLETED;
        return snapshot(entry);
    }

    synchronized MatchSession cancel(
        MatchId matchId,
        String reasonKey
    ){
        Entry entry=require(matchId);
        String reason=
            MatchRules.normalizeKey(
                reasonKey,
                "reasonKey"
            );

        if(entry.state==MatchSession.State.CANCELLED){
            if(entry.cancellationReasonKey.equals(reason))
                return snapshot(entry);

            throw new IllegalStateException(
                "match already cancelled reason="+
                entry.cancellationReasonKey
            );
        }

        if(entry.state==MatchSession.State.COMPLETED)
            throw invalid(entry,"cancel");

        requireNoTerminalHolds(
            entry,
            "cancel"
        );

        entry.cancellationReasonKey=reason;
        entry.state=MatchSession.State.CANCELLED;
        return snapshot(entry);
    }

    synchronized MatchSession get(MatchId id){
        Entry entry=matches.get(
            Objects.requireNonNull(id,"id")
        );
        return entry==null?null:snapshot(entry);
    }

    synchronized int size(){
        return matches.size();
    }

    synchronized List<MatchSession> snapshot(){
        ArrayList<Entry> ordered=
            new ArrayList<>(
                matches.values()
            );

        ordered.sort(
            Comparator.comparing(value->value.id)
        );

        ArrayList<MatchSession> out=
            new ArrayList<>();

        for(Entry entry:ordered)
            out.add(snapshot(entry));

        return Collections.unmodifiableList(out);
    }

    private MatchSession markParticipant(
        MatchId matchId,
        String participantRef,
        MatchSession.ParticipantStatus status
    ){
        Entry entry=require(matchId);

        if(entry.state==MatchSession.State.COMPLETED||
           entry.state==MatchSession.State.CANCELLED)
            throw invalid(
                entry,
                status.name().toLowerCase(
                    Locale.ROOT
                )
            );

        ParticipantState participant=
            requireParticipant(
                entry,
                participantRef
            );

        if(participant.status!=
                MatchSession.ParticipantStatus.PRESENT)
            throw new IllegalStateException(
                "participant transition from "+
                participant.status+
                " ref="+participant.participantRef
            );

        participant.status=status;
        return snapshot(entry);
    }

    private static boolean hasPresentParticipant(Entry entry){
        for(ParticipantState participant:
                entry.participants.values())
            if(participant.status==
                    MatchSession.ParticipantStatus.PRESENT)
                return true;

        return false;
    }

    private Entry requireActive(MatchId id){
        Entry entry=require(id);
        requireState(
            entry,
            MatchSession.State.ACTIVE,
            "score"
        );
        return entry;
    }

    private Entry require(MatchId id){
        MatchId key=Objects.requireNonNull(id,"id");
        Entry entry=matches.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown match id="+key
            );

        return entry;
    }

    private static ParticipantState requireParticipant(
        Entry entry,
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        ParticipantState state=
            entry.participants.get(participant);

        if(state==null)
            throw new IllegalArgumentException(
                "unknown match participant "+
                participant
            );

        return state;
    }

    private static void adjustScore(
        Map<String,Long> scores,
        String counterKey,
        long delta
    ){
        if(delta==0)
            throw new IllegalArgumentException(
                "score delta=0"
            );

        String key=
            MatchRules.normalizeKey(
                counterKey,
                "counterKey"
            );

        long current=
            scores.getOrDefault(key,0L);

        long next;

        try{
            next=Math.addExact(current,delta);
        }catch(ArithmeticException error){
            throw new IllegalArgumentException(
                "score overflow",
                error
            );
        }

        if(next<0)
            throw new IllegalArgumentException(
                "score would become negative "+
                key+"="+next
            );

        scores.put(key,next);
    }

    private static void requireNoTerminalHolds(
        Entry entry,
        String operation
    ){
        if(!entry.terminalHolds.isEmpty())
            throw new IllegalStateException(
                operation+
                " blocked by MatchSession terminal holds match="+
                entry.id+
                " holds="+
                entry.terminalHolds
            );
    }

    private static String requireHoldKey(
        String holdKey
    ){
        if(holdKey==null)
            throw new NullPointerException("holdKey");

        String key=holdKey.trim();

        if(key.isEmpty())
            throw new IllegalArgumentException(
                "holdKey blank"
            );

        return key;
    }

    private static void requireState(
        Entry entry,
        MatchSession.State expected,
        String operation
    ){
        if(entry.state!=expected)
            throw invalid(entry,operation);
    }

    private static IllegalStateException invalid(
        Entry entry,
        String operation
    ){
        return new IllegalStateException(
            operation+
            " invalid from "+
            entry.state+
            " for "+
            entry.id
        );
    }

    private static MatchSession snapshot(Entry entry){
        ArrayList<MatchSession.Team> teams=
            new ArrayList<>();

        for(TeamState team:entry.teams.values())
            teams.add(
                new MatchSession.Team(
                    team.id,
                    team.members,
                    team.scores
                )
            );

        ArrayList<MatchSession.Participant> participants=
            new ArrayList<>();

        for(ParticipantState participant:
                entry.participants.values())
            participants.add(
                new MatchSession.Participant(
                    participant.participantRef,
                    participant.teamId,
                    participant.status,
                    participant.scores
                )
            );

        return new MatchSession(
            entry.id,
            entry.rules,
            entry.state,
            entry.instanceId,
            teams,
            participants,
            entry.result,
            entry.cancellationReasonKey
        );
    }
}

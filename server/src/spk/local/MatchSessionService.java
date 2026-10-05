package spk.local;

import java.util.*;

/**
 * Mutable owner of semantic match/team/scoring lifecycle.
 *
 * Matchmaking assignment, world-instance lifecycle, combat enforcement,
 * rewards and winner policy are external.
 */
final class MatchSessionService {
    static final class CompositionLease {
        private final String ownerRef;

        private CompositionLease(
            String ownerRef
        ){
            this.ownerRef=
                requireCompositionLeaseLabel(
                    ownerRef
                );
        }

        @Override public String toString(){
            return "CompositionLease{"+
                ownerRef+
                "}";
        }
    }

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

        MatchSession.State state=
            MatchSession.State.CREATED;

        WorldInstanceId instanceId;
        MatchSession.Result result;
        String cancellationReasonKey;
        CompositionLease compositionLease;

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

    synchronized CompositionLease acquireWorldInstanceCompositionLease(
        WorldInstanceService instances,
        MatchId matchId,
        WorldInstanceId instanceId,
        String ownerRef
    )throws Exception{
        Objects.requireNonNull(
            instances,
            "instances"
        );

        CompositionLease lease=
            new CompositionLease(
                ownerRef
            );

        instances.withMatchCompositionOwnership(
            ()->{
                Entry entry=
                    require(
                        matchId
                    );
                WorldInstanceId checkedInstanceId=
                    Objects.requireNonNull(
                        instanceId,
                        "instanceId"
                    );

                requireState(
                    entry,
                    MatchSession.State.ACTIVE,
                    "acquireCompositionLease"
                );

                if(entry.instanceId==null||
                   !entry.instanceId.equals(
                        checkedInstanceId))
                    throw new IllegalStateException(
                        "match/instance lease mismatch match="+
                        entry.id+
                        " expected="+
                        entry.instanceId+
                        " actual="+
                        checkedInstanceId
                    );

                requireNoCompositionLease(
                    entry,
                    "acquireCompositionLease"
                );
                instances.requireCompositionLeaseAvailable(
                    checkedInstanceId
                );

                entry.compositionLease=
                    lease;

                try{
                    instances.acquireCompositionLease(
                        checkedInstanceId,
                        lease
                    );
                }catch(RuntimeException failure){
                    entry.compositionLease=null;
                    throw failure;
                }catch(Error failure){
                    entry.compositionLease=null;
                    throw failure;
                }
            }
        );

        return lease;
    }

    synchronized void releaseWorldInstanceCompositionLease(
        WorldInstanceService instances,
        MatchId matchId,
        WorldInstanceId instanceId,
        CompositionLease lease
    )throws Exception{
        Objects.requireNonNull(
            instances,
            "instances"
        );
        CompositionLease checkedLease=
            Objects.requireNonNull(
                lease,
                "lease"
            );

        instances.withMatchCompositionOwnership(
            ()->{
                Entry entry=
                    require(
                        matchId
                    );
                WorldInstanceId checkedInstanceId=
                    Objects.requireNonNull(
                        instanceId,
                        "instanceId"
                    );

                if(entry.instanceId==null||
                   !entry.instanceId.equals(
                        checkedInstanceId))
                    throw new IllegalStateException(
                        "match/instance lease mismatch match="+
                        entry.id+
                        " expected="+
                        entry.instanceId+
                        " actual="+
                        checkedInstanceId
                    );

                requireCompositionLease(
                    entry,
                    checkedLease
                );
                instances.requireCompositionLease(
                    checkedInstanceId,
                    checkedLease
                );

                instances.releaseCompositionLease(
                    checkedInstanceId,
                    checkedLease
                );
                entry.compositionLease=
                    null;
            }
        );
    }

    synchronized boolean compositionLeaseHeld(
        MatchId matchId
    ){
        return require(
            matchId
        ).compositionLease!=null;
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
        Entry entry=requireActive(
            matchId
        );
        requireNoCompositionLease(
            entry,
            "adjustTeamScore"
        );

        return adjustTeamScoreEntry(
            entry,
            teamId,
            counterKey,
            delta
        );
    }

    synchronized MatchSession adjustTeamScoreOwned(
        MatchId matchId,
        MatchTeamId teamId,
        String counterKey,
        long delta,
        CompositionLease lease
    ){
        Entry entry=requireActive(
            matchId
        );
        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return adjustTeamScoreEntry(
            entry,
            teamId,
            counterKey,
            delta
        );
    }

    synchronized MatchSession adjustParticipantScore(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta
    ){
        Entry entry=requireActive(
            matchId
        );
        requireNoCompositionLease(
            entry,
            "adjustParticipantScore"
        );

        return adjustParticipantScoreEntry(
            entry,
            participantRef,
            counterKey,
            delta
        );
    }

    synchronized MatchSession adjustParticipantScoreOwned(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta,
        CompositionLease lease
    ){
        Entry entry=requireActive(
            matchId
        );
        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return adjustParticipantScoreEntry(
            entry,
            participantRef,
            counterKey,
            delta
        );
    }

    synchronized boolean tryAdjustPresentParticipantScore(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta
    ){
        Entry entry=require(
            matchId
        );
        requireNoCompositionLease(
            entry,
            "tryAdjustPresentParticipantScore"
        );

        return tryAdjustPresentParticipantScoreEntry(
            entry,
            participantRef,
            counterKey,
            delta
        );
    }

    synchronized boolean tryAdjustPresentParticipantScoreOwned(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta,
        CompositionLease lease
    ){
        Entry entry=require(
            matchId
        );
        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return tryAdjustPresentParticipantScoreEntry(
            entry,
            participantRef,
            counterKey,
            delta
        );
    }

    synchronized boolean tryAdjustPresentParticipantTeamScore(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta
    ){
        Entry entry=require(
            matchId
        );
        requireNoCompositionLease(
            entry,
            "tryAdjustPresentParticipantTeamScore"
        );

        return tryAdjustPresentParticipantTeamScoreEntry(
            entry,
            participantRef,
            counterKey,
            delta
        );
    }

    synchronized boolean tryAdjustPresentParticipantTeamScoreOwned(
        MatchId matchId,
        String participantRef,
        String counterKey,
        long delta,
        CompositionLease lease
    ){
        Entry entry=require(
            matchId
        );
        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return tryAdjustPresentParticipantTeamScoreEntry(
            entry,
            participantRef,
            counterKey,
            delta
        );
    }

    synchronized MatchSession leave(
        MatchId matchId,
        String participantRef
    ){
        return markParticipantUnowned(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.LEFT
        );
    }

    synchronized MatchSession leaveOwned(
        MatchId matchId,
        String participantRef,
        CompositionLease lease
    ){
        return markParticipantOwned(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.LEFT,
            lease
        );
    }

    synchronized MatchSession forfeit(
        MatchId matchId,
        String participantRef
    ){
        return markParticipantUnowned(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.FORFEITED
        );
    }

    synchronized MatchSession forfeitOwned(
        MatchId matchId,
        String participantRef,
        CompositionLease lease
    ){
        return markParticipantOwned(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.FORFEITED,
            lease
        );
    }

    synchronized MatchSession disconnect(
        MatchId matchId,
        String participantRef
    ){
        return markParticipantUnowned(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.DISCONNECTED
        );
    }

    synchronized MatchSession disconnectOwned(
        MatchId matchId,
        String participantRef,
        CompositionLease lease
    ){
        return markParticipantOwned(
            matchId,
            participantRef,
            MatchSession.ParticipantStatus.DISCONNECTED,
            lease
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
        requireNoCompositionLease(
            entry,
            "complete"
        );

        return completeEntry(
            entry,
            result
        );
    }

    synchronized MatchSession completeOwned(
        MatchId matchId,
        MatchSession.Result result,
        CompositionLease lease
    ){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.ACTIVE,
            "completeOwned"
        );
        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return completeEntry(
            entry,
            result
        );
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

        requireNoCompositionLease(
            entry,
            "cancel"
        );

        return cancelEntry(
            entry,
            reason
        );
    }

    synchronized MatchSession cancelOwned(
        MatchId matchId,
        String reasonKey,
        CompositionLease lease
    ){
        Entry entry=require(matchId);
        requireState(
            entry,
            MatchSession.State.ACTIVE,
            "cancelOwned"
        );
        String reason=
            MatchRules.normalizeKey(
                reasonKey,
                "reasonKey"
            );

        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return cancelEntry(
            entry,
            reason
        );
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

    private static MatchSession completeEntry(
        Entry entry,
        MatchSession.Result result
    ){
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
        entry.state=
            MatchSession.State.COMPLETED;
        return snapshot(entry);
    }

    private static MatchSession cancelEntry(
        Entry entry,
        String reason
    ){
        entry.cancellationReasonKey=
            Objects.requireNonNull(
                reason,
                "reason"
            );
        entry.state=
            MatchSession.State.CANCELLED;
        return snapshot(entry);
    }

    private static MatchSession adjustTeamScoreEntry(
        Entry entry,
        MatchTeamId teamId,
        String counterKey,
        long delta
    ){
        TeamState team=
            entry.teams.get(
                Objects.requireNonNull(
                    teamId,
                    "teamId"
                )
            );

        if(team==null)
            throw new IllegalArgumentException(
                "unknown team id="+
                teamId
            );

        adjustScore(
            team.scores,
            counterKey,
            delta
        );

        return snapshot(
            entry
        );
    }

    private static MatchSession adjustParticipantScoreEntry(
        Entry entry,
        String participantRef,
        String counterKey,
        long delta
    ){
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
                " status="+
                participant.status
            );

        adjustScore(
            participant.scores,
            counterKey,
            delta
        );

        return snapshot(
            entry
        );
    }

    private static boolean tryAdjustPresentParticipantScoreEntry(
        Entry entry,
        String participantRef,
        String counterKey,
        long delta
    ){
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

    private static boolean tryAdjustPresentParticipantTeamScoreEntry(
        Entry entry,
        String participantRef,
        String counterKey,
        long delta
    ){
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

    private MatchSession markParticipantUnowned(
        MatchId matchId,
        String participantRef,
        MatchSession.ParticipantStatus status
    ){
        Entry entry=require(matchId);

        requireNoCompositionLease(
            entry,
            status.name().toLowerCase(
                Locale.ROOT
            )
        );

        return markParticipantEntry(
            entry,
            participantRef,
            status
        );
    }

    private MatchSession markParticipantOwned(
        MatchId matchId,
        String participantRef,
        MatchSession.ParticipantStatus status,
        CompositionLease lease
    ){
        Entry entry=require(matchId);

        requireCompositionLease(
            entry,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return markParticipantEntry(
            entry,
            participantRef,
            status
        );
    }

    private static MatchSession markParticipantEntry(
        Entry entry,
        String participantRef,
        MatchSession.ParticipantStatus status
    ){
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

    private static void requireNoCompositionLease(
        Entry entry,
        String operation
    ){
        if(entry.compositionLease!=null)
            throw new IllegalStateException(
                operation+
                " blocked by composition lease match="+
                entry.id+
                " lease="+
                entry.compositionLease
            );
    }

    private static void requireCompositionLease(
        Entry entry,
        CompositionLease lease
    ){
        if(entry.compositionLease!=lease)
            throw new IllegalStateException(
                "composition lease identity mismatch match="+
                entry.id
            );
    }

    private static String requireCompositionLeaseLabel(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "ownerRef"
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "ownerRef blank"
            );

        return normalized;
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

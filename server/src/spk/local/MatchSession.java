package spk.local;

import java.util.*;

/** Immutable snapshot of one semantic competitive match session. */
final class MatchSession {
    enum State {
        CREATED,
        READY,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    enum ParticipantStatus {
        PRESENT,
        LEFT,
        FORFEITED,
        DISCONNECTED
    }

    static final class Team {
        final MatchTeamId id;
        final List<String> members;
        final Map<String,Long> scores;

        Team(
            MatchTeamId id,
            Collection<String> members,
            Map<String,Long> scores
        ){
            this.id=Objects.requireNonNull(id,"id");
            this.members=
                Collections.unmodifiableList(
                    new ArrayList<>(members)
                );
            this.scores=immutableScores(scores);
        }
    }

    static final class Participant {
        final String participantRef;
        final MatchTeamId teamId;
        final ParticipantStatus status;
        final Map<String,Long> scores;

        Participant(
            String participantRef,
            MatchTeamId teamId,
            ParticipantStatus status,
            Map<String,Long> scores
        ){
            this.participantRef=
                PartyService.requireRef(
                    participantRef
                );
            this.teamId=
                Objects.requireNonNull(
                    teamId,
                    "teamId"
                );
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.scores=immutableScores(scores);
        }
    }

    static final class Result {
        final String outcomeKey;
        final MatchTeamId winnerTeamId;
        final String decisionAuthority;

        Result(
            String outcomeKey,
            MatchTeamId winnerTeamId,
            String decisionAuthority
        ){
            this.outcomeKey=
                MatchRules.normalizeKey(
                    outcomeKey,
                    "outcomeKey"
                );
            this.winnerTeamId=winnerTeamId;
            this.decisionAuthority=
                MatchRules.requireText(
                    decisionAuthority,
                    "decisionAuthority"
                );
        }

        boolean hasWinner(){
            return winnerTeamId!=null;
        }
    }

    final MatchId id;
    final MatchRules rules;
    final State state;
    final WorldInstanceId instanceId;
    final List<Team> teams;
    final List<Participant> participants;
    final Result result;
    final String cancellationReasonKey;

    MatchSession(
        MatchId id,
        MatchRules rules,
        State state,
        WorldInstanceId instanceId,
        List<Team> teams,
        List<Participant> participants,
        Result result,
        String cancellationReasonKey
    ){
        this.id=Objects.requireNonNull(id,"id");
        this.rules=Objects.requireNonNull(rules,"rules");
        this.state=Objects.requireNonNull(state,"state");
        this.instanceId=instanceId;
        this.teams=
            Collections.unmodifiableList(
                new ArrayList<>(teams)
            );
        this.participants=
            Collections.unmodifiableList(
                new ArrayList<>(participants)
            );
        this.result=result;
        this.cancellationReasonKey=
            cancellationReasonKey;
    }

    boolean hasInstance(){
        return instanceId!=null;
    }

    boolean terminal(){
        return state==State.COMPLETED||
            state==State.CANCELLED;
    }

    Team team(MatchTeamId id){
        for(Team team:teams){
            if(team.id.equals(id))
                return team;
        }
        return null;
    }

    Participant participant(String participantRef){
        String ref=PartyService.requireRef(
            participantRef
        );

        for(Participant participant:participants){
            if(participant.participantRef.equals(ref))
                return participant;
        }

        return null;
    }

    private static Map<String,Long> immutableScores(
        Map<String,Long> scores
    ){
        LinkedHashMap<String,Long> copy=
            new LinkedHashMap<>();

        for(Map.Entry<String,Long> entry:
                scores.entrySet())
            copy.put(
                entry.getKey(),
                entry.getValue()
            );

        return Collections.unmodifiableMap(copy);
    }
}

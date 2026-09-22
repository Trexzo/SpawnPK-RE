package spk.local;

import java.util.*;

/**
 * Explicit subject-scoped bridge from semantic combat outcomes into one or more
 * MatchSession counters.
 *
 * Scoring policy is caller-owned. This observer never chooses winners,
 * completes matches, changes participant status or grants rewards.
 */
final class CombatOutcomeMatchObserver
    implements CombatOutcomeObserver {

    enum SubjectRole {
        ATTACKER,
        VICTIM
    }

    enum ScoreScope {
        PARTICIPANT,
        SUBJECT_TEAM
    }

    static final class Binding {
        final CombatOutcomeType outcomeType;
        final CombatOutcomeContext context;
        final SubjectRole subjectRole;
        final MatchId matchId;
        final ScoreScope scoreScope;
        final String counterKey;
        final long amount;
        final String sourceAuthority;

        Binding(
            CombatOutcomeType outcomeType,
            CombatOutcomeContext context,
            SubjectRole subjectRole,
            MatchId matchId,
            ScoreScope scoreScope,
            String counterKey,
            long amount,
            String sourceAuthority
        ){
            this.outcomeType=
                Objects.requireNonNull(
                    outcomeType,
                    "outcomeType"
                );
            this.context=
                Objects.requireNonNull(
                    context,
                    "context"
                );
            this.subjectRole=
                Objects.requireNonNull(
                    subjectRole,
                    "subjectRole"
                );
            this.matchId=
                Objects.requireNonNull(
                    matchId,
                    "matchId"
                );
            this.scoreScope=
                Objects.requireNonNull(
                    scoreScope,
                    "scoreScope"
                );
            this.counterKey=
                MatchRules.normalizeKey(
                    counterKey,
                    "counterKey"
                );

            if(amount<=0L)
                throw new IllegalArgumentException(
                    "amount="+amount
                );

            this.amount=amount;
            this.sourceAuthority=
                MatchRules.requireText(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }

        String duplicateKey(){
            return outcomeType.name()+"|"+
                context.name()+"|"+
                subjectRole.name()+"|"+
                matchId.toString()+"|"+
                scoreScope.name()+"|"+
                counterKey;
        }
    }

    private final MatchSessionService matches;
    private final String subjectRef;
    private final List<Binding> bindings;

    CombatOutcomeMatchObserver(
        MatchSessionService matches,
        String subjectRef,
        Collection<Binding> bindings
    ){
        this.matches=
            Objects.requireNonNull(
                matches,
                "matches"
            );
        this.subjectRef=
            PartyService.requireRef(
                subjectRef
            );

        Objects.requireNonNull(
            bindings,
            "bindings"
        );

        if(bindings.isEmpty())
            throw new IllegalArgumentException(
                "bindings empty"
            );

        ArrayList<Binding> copy=
            new ArrayList<>();
        HashSet<String> duplicateGuard=
            new HashSet<>();

        for(Binding binding:bindings){
            Binding checked=
                Objects.requireNonNull(
                    binding,
                    "binding"
                );

            MatchSession match=
                matches.get(
                    checked.matchId
                );

            if(match==null)
                throw new IllegalArgumentException(
                    "unknown match id="+
                    checked.matchId
                );

            if(match.participant(
                    this.subjectRef)==null)
                throw new IllegalArgumentException(
                    "subject "+
                    this.subjectRef+
                    " not in match "+
                    checked.matchId
                );

            if(!duplicateGuard.add(
                    checked.duplicateKey()))
                throw new IllegalArgumentException(
                    "duplicate combat match binding "+
                    checked.duplicateKey()
                );

            copy.add(checked);
        }

        this.bindings=
            Collections.unmodifiableList(
                copy
            );
    }

    @Override
    public void onCombatOutcome(
        CombatOutcome outcome
    ){
        Objects.requireNonNull(
            outcome,
            "outcome"
        );

        for(Binding binding:bindings){
            if(binding.outcomeType!=
                    outcome.type()||
               binding.context!=
                    outcome.context()||
               !subjectMatches(
                    binding.subjectRole,
                    outcome))
                continue;

            if(binding.scoreScope==
                    ScoreScope.PARTICIPANT){
                matches.tryAdjustPresentParticipantScore(
                    binding.matchId,
                    subjectRef,
                    binding.counterKey,
                    binding.amount
                );
            }else{
                matches.tryAdjustPresentParticipantTeamScore(
                    binding.matchId,
                    subjectRef,
                    binding.counterKey,
                    binding.amount
                );
            }
        }
    }

    List<Binding> bindings(){
        return bindings;
    }

    String subjectRef(){
        return subjectRef;
    }

    private boolean subjectMatches(
        SubjectRole role,
        CombatOutcome outcome
    ){
        String actual=
            role==SubjectRole.ATTACKER
                ?outcome.attacker()
                :outcome.victim();

        return subjectRef.equals(actual);
    }
}

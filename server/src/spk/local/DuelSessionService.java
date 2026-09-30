package spk.local;

import java.util.*;

/**
 * Protocol-independent 1v1 Duel challenge + runtime composition.
 *
 * Exact client surfaces prove Duel exists, but stake rules, combat-rule
 * enforcement, rematch/load-last behavior, rewards and winner policy remain
 * external. This service owns only challenge/session lifecycle and composition
 * into MatchSessionService + WorldInstanceService.
 */
final class DuelSessionService {
    enum State {
        PROPOSED,
        ACCEPTED,
        ACTIVE,
        DECLINED,
        CANCELLED,
        COMPLETED
    }

    static final class ChallengeId
        implements Comparable<ChallengeId> {

        private final String value;

        ChallengeId(String value){
            this.value=
                MatchRules.normalizeKey(
                    value,
                    "challengeId"
                );
        }

        static ChallengeId of(String value){
            return new ChallengeId(value);
        }

        String value(){
            return value;
        }

        @Override public int compareTo(
            ChallengeId other
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
            return other instanceof ChallengeId&&
                value.equals(
                    ((ChallengeId)other).value
                );
        }

        @Override public int hashCode(){
            return value.hashCode();
        }

        @Override public String toString(){
            return value;
        }
    }

    static final class Snapshot {
        final ChallengeId challengeId;
        final String challengerRef;
        final String challengedRef;
        final String duelTypeKey;
        final MatchRules rules;
        final String policyAuthority;
        final State state;
        final MatchId matchId;
        final WorldInstanceId instanceId;
        final MatchTeamId challengerTeamId;
        final MatchTeamId challengedTeamId;

        Snapshot(Entry entry){
            this.challengeId=
                entry.challengeId;
            this.challengerRef=
                entry.challengerRef;
            this.challengedRef=
                entry.challengedRef;
            this.duelTypeKey=
                entry.duelTypeKey;
            this.rules=entry.rules;
            this.policyAuthority=
                entry.policyAuthority;
            this.state=entry.state;
            this.matchId=entry.matchId;
            this.instanceId=
                entry.instanceId;
            this.challengerTeamId=
                entry.challengerTeamId;
            this.challengedTeamId=
                entry.challengedTeamId;
        }

        boolean terminal(){
            return state==
                    State.DECLINED||
                state==
                    State.CANCELLED||
                state==
                    State.COMPLETED;
        }

        boolean active(){
            return state==State.ACTIVE;
        }

        boolean participant(
            String playerRef
        ){
            String ref=
                PartyService.requireRef(
                    playerRef
                );

            return challengerRef.equals(ref)||
                challengedRef.equals(ref);
        }

        MatchTeamId teamFor(
            String playerRef
        ){
            String ref=
                PartyService.requireRef(
                    playerRef
                );

            if(challengerRef.equals(ref))
                return challengerTeamId;

            if(challengedRef.equals(ref))
                return challengedTeamId;

            return null;
        }
    }

    private static final class Entry {
        final ChallengeId challengeId;
        final String challengerRef;
        final String challengedRef;
        final String duelTypeKey;
        final MatchRules rules;
        final String policyAuthority;

        final MatchTeamId challengerTeamId=
            MatchTeamId.of(
                "duel:challenger"
            );
        final MatchTeamId challengedTeamId=
            MatchTeamId.of(
                "duel:challenged"
            );

        State state=State.PROPOSED;
        MatchId matchId;
        WorldInstanceId instanceId;
        MatchSessionService.CompositionLease
            childLease;

        Entry(
            ChallengeId challengeId,
            String challengerRef,
            String challengedRef,
            String duelTypeKey,
            MatchRules rules,
            String policyAuthority
        ){
            this.challengeId=challengeId;
            this.challengerRef=
                challengerRef;
            this.challengedRef=
                challengedRef;
            this.duelTypeKey=
                duelTypeKey;
            this.rules=rules;
            this.policyAuthority=
                policyAuthority;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final MatchSessionService matches;
    private final WorldInstanceService instances;

    private final LinkedHashMap<ChallengeId,Entry>
        entries=
            new LinkedHashMap<>();

    private final HashMap<String,ChallengeId>
        openByParticipant=
            new HashMap<>();

    DuelSessionService(
        MatchSessionService matches,
        WorldInstanceService instances
    ){
        this.matches=
            Objects.requireNonNull(
                matches,
                "matches"
            );
        this.instances=
            Objects.requireNonNull(
                instances,
                "instances"
            );
    }

    synchronized Snapshot propose(
        ChallengeId challengeId,
        String challengerRef,
        String challengedRef,
        String duelTypeKey,
        MatchRules rules,
        String policyAuthority
    ){
        ChallengeId id=
            Objects.requireNonNull(
                challengeId,
                "challengeId"
            );

        if(entries.containsKey(id))
            throw new IllegalStateException(
                "duplicate Duel challenge id="+id
            );

        String challenger=
            PartyService.requireRef(
                challengerRef
            );
        String challenged=
            PartyService.requireRef(
                challengedRef
            );

        if(challenger.equals(challenged))
            throw new IllegalArgumentException(
                "Duel participants must differ"
            );

        requireAvailable(challenger);
        requireAvailable(challenged);

        MatchRules checkedRules=
            Objects.requireNonNull(
                rules,
                "rules"
            );

        String authority=
            MatchRules.requireText(
                policyAuthority,
                "policyAuthority"
            );

        if(!authority.equals(
                checkedRules.sourceAuthority))
            throw new IllegalArgumentException(
                "Duel MatchRules authority "+
                checkedRules.sourceAuthority+
                " does not match policy authority "+
                authority
            );

        Entry entry=
            new Entry(
                id,
                challenger,
                challenged,
                MatchRules.normalizeKey(
                    duelTypeKey,
                    "duelTypeKey"
                ),
                checkedRules,
                authority
            );

        entries.put(id,entry);
        openByParticipant.put(
            challenger,
            id
        );
        openByParticipant.put(
            challenged,
            id
        );

        return entry.snapshot();
    }

    synchronized Snapshot accept(
        ChallengeId challengeId,
        String actorRef
    ){
        Entry entry=
            requireState(
                challengeId,
                State.PROPOSED,
                "accept"
            );

        requireChallengedActor(
            entry,
            actorRef,
            "accept"
        );

        entry.state=State.ACCEPTED;
        return entry.snapshot();
    }

    synchronized Snapshot decline(
        ChallengeId challengeId,
        String actorRef
    ){
        Entry entry=
            requireState(
                challengeId,
                State.PROPOSED,
                "decline"
            );

        requireChallengedActor(
            entry,
            actorRef,
            "decline"
        );

        entry.state=State.DECLINED;
        releaseParticipants(entry);
        return entry.snapshot();
    }

    synchronized Snapshot cancelOpen(
        ChallengeId challengeId,
        String actorRef
    ){
        Entry entry=require(challengeId);

        if(entry.state!=State.PROPOSED&&
           entry.state!=State.ACCEPTED)
            throw invalid(
                entry,
                "cancelOpen"
            );

        requireParticipant(
            entry,
            actorRef
        );

        entry.state=State.CANCELLED;
        releaseParticipants(entry);
        return entry.snapshot();
    }

    synchronized Snapshot startAccepted(
        ChallengeId challengeId,
        MatchId matchId,
        WorldInstanceId instanceId
    ){
        Entry entry=
            requireState(
                challengeId,
                State.ACCEPTED,
                "startAccepted"
            );

        if(entry.rules.teamMode!=
                MatchRules.TeamMode.TEAMS)
            throw new IllegalArgumentException(
                "Duel MatchRules must use TEAMS"
            );

        if(!entry.policyAuthority.equals(
                entry.rules.sourceAuthority))
            throw new IllegalStateException(
                "Duel MatchRules authority drift"
            );

        MatchId checkedMatchId=
            Objects.requireNonNull(
                matchId,
                "matchId"
            );
        WorldInstanceId checkedInstanceId=
            Objects.requireNonNull(
                instanceId,
                "instanceId"
            );

        withCompositionOwnership(
            ()->{
                if(matches.get(
                        checkedMatchId)!=null)
                    throw new IllegalStateException(
                        "Duel match id already exists "+
                        checkedMatchId
                    );

                if(instances.get(
                        checkedInstanceId)!=null)
                    throw new IllegalStateException(
                        "Duel instance id already exists "+
                        checkedInstanceId
                    );

            matches.create(
                checkedMatchId,
                entry.rules
            );
            matches.addTeam(
                checkedMatchId,
                entry.challengerTeamId
            );
            matches.addTeam(
                checkedMatchId,
                entry.challengedTeamId
            );
            matches.join(
                checkedMatchId,
                entry.challengerTeamId,
                entry.challengerRef
            );
            matches.join(
                checkedMatchId,
                entry.challengedTeamId,
                entry.challengedRef
            );
    
            instances.create(
                checkedInstanceId,
                checkedMatchId.toString(),
                entry.policyAuthority
            );
            instances.attach(
                checkedInstanceId,
                entry.challengerRef
            );
            instances.attach(
                checkedInstanceId,
                entry.challengedRef
            );
    
            matches.attachInstance(
                checkedMatchId,
                checkedInstanceId
            );
            matches.markReady(
                checkedMatchId
            );
            instances.activate(
                checkedInstanceId
            );
            matches.activate(
                checkedMatchId
            );

            entry.childLease=
                matches.acquireWorldInstanceCompositionLease(
                    instances,
                    checkedMatchId,
                    checkedInstanceId,
                    childLeaseLabel(
                        checkedMatchId
                    )
                );
    
    
            }
        );

        entry.matchId=checkedMatchId;
        entry.instanceId=
            checkedInstanceId;
        entry.state=State.ACTIVE;

        return entry.snapshot();
    }

    synchronized Snapshot adjustParticipantScore(
        ChallengeId challengeId,
        String playerRef,
        String counterKey,
        long delta
    ){
        Entry entry=
            requireState(
                challengeId,
                State.ACTIVE,
                "adjustParticipantScore"
            );

        String participant=
            requireParticipant(
                entry,
                playerRef
            );

        matches.adjustParticipantScoreOwned(
            entry.matchId,
            participant,
            counterKey,
            delta,
            requireChildLease(
                entry
            )
        );

        return entry.snapshot();
    }

    synchronized Snapshot adjustTeamScore(
        ChallengeId challengeId,
        String playerRef,
        String counterKey,
        long delta
    ){
        Entry entry=
            requireState(
                challengeId,
                State.ACTIVE,
                "adjustTeamScore"
            );

        MatchTeamId team=
            teamForParticipant(
                entry,
                playerRef
            );

        matches.adjustTeamScoreOwned(
            entry.matchId,
            team,
            counterKey,
            delta,
            requireChildLease(
                entry
            )
        );

        return entry.snapshot();
    }

    synchronized Snapshot forfeit(
        ChallengeId challengeId,
        String playerRef
    ){
        Entry entry=
            requireState(
                challengeId,
                State.ACTIVE,
                "forfeit"
            );

        String participant=
            requireParticipant(
                entry,
                playerRef
            );

        matches.forfeitOwned(
            entry.matchId,
            participant,
            requireChildLease(
                entry
            )
        );

        // Forfeit is bookkeeping only. Winner/outcome remains caller-owned.
        return entry.snapshot();
    }

    synchronized Snapshot complete(
        ChallengeId challengeId,
        String winnerPlayerRef,
        String outcomeKey,
        String decisionAuthority
    ){
        Entry entry=
            requireState(
                challengeId,
                State.ACTIVE,
                "complete"
            );

        MatchTeamId winner=null;

        if(winnerPlayerRef!=null)
            winner=
                teamForParticipant(
                    entry,
                    winnerPlayerRef
                );

        MatchSession.Result result=
            new MatchSession.Result(
                outcomeKey,
                winner,
                MatchRules.requireText(
                    decisionAuthority,
                    "decisionAuthority"
                )
            );

        withCompositionOwnership(
            ()->{
                preflightOwnedInstance(entry);

                MatchSessionService.CompositionLease lease=
                    requireChildLease(
                        entry
                    );

                matches.completeOwned(
                    entry.matchId,
                    result,
                    lease
                );
                closeOwnedInstance(
                    entry,
                    lease
                );

                entry.state=State.COMPLETED;
                releaseParticipants(entry);

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    entry.matchId,
                    entry.instanceId,
                    lease
                );
                entry.childLease=null;
            }
        );

        return entry.snapshot();
    }

    synchronized Snapshot cancelActive(
        ChallengeId challengeId,
        String reasonKey
    ){
        Entry entry=
            requireState(
                challengeId,
                State.ACTIVE,
                "cancelActive"
            );
        String reason=
            MatchRules.normalizeKey(
                reasonKey,
                "reasonKey"
            );

        withCompositionOwnership(
            ()->{
                preflightOwnedInstance(entry);

                MatchSessionService.CompositionLease lease=
                    requireChildLease(
                        entry
                    );

                matches.cancelOwned(
                    entry.matchId,
                    reason,
                    lease
                );
                closeOwnedInstance(
                    entry,
                    lease
                );

                entry.state=State.CANCELLED;
                releaseParticipants(entry);

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    entry.matchId,
                    entry.instanceId,
                    lease
                );
                entry.childLease=null;
            }
        );

        return entry.snapshot();
    }

    synchronized Snapshot get(
        ChallengeId challengeId
    ){
        Entry entry=
            entries.get(
                Objects.requireNonNull(
                    challengeId,
                    "challengeId"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized Snapshot openFor(
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        ChallengeId id=
            openByParticipant.get(
                participant
            );

        return id==null
            ?null
            :get(id);
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=
            new ArrayList<>(
                entries.values()
            );

        ordered.sort(
            Comparator.comparing(
                value->
                    value.challengeId
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:ordered)
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private static String childLeaseLabel(
        MatchId matchId
    ){
        return "duel:"+
            Objects.requireNonNull(
                matchId,
                "matchId"
            );
    }

    private static MatchSessionService.CompositionLease
        requireChildLease(
            Entry entry
        ){
        if(entry.childLease==null)
            throw new IllegalStateException(
                "Duel child lease missing "+
                entry.matchId
            );

        return entry.childLease;
    }

    private void preflightOwnedInstance(
        Entry entry
    ){
        MatchSession match=
            matches.get(
                Objects.requireNonNull(
                    entry.matchId,
                    "matchId"
                )
            );

        if(match==null||
           match.state!=
                MatchSession.State.ACTIVE)
            throw new IllegalStateException(
                "Duel MatchSession not active "+
                entry.matchId
            );

        WorldInstanceService.Snapshot instance=
            instances.get(
                Objects.requireNonNull(
                    entry.instanceId,
                    "instanceId"
                )
            );

        if(instance==null||
           instance.lifecycle!=
                WorldInstanceService
                    .Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "Duel WorldInstance not active "+
                entry.instanceId
            );

        LinkedHashSet<String> expected=
            new LinkedHashSet<>();

        expected.add(
            entry.challengerRef
        );
        expected.add(
            entry.challengedRef
        );

        LinkedHashSet<String> actual=
            new LinkedHashSet<>(
                instance.participants
            );

        if(!expected.equals(actual))
            throw new IllegalStateException(
                "Duel instance participant drift expected="+
                expected+
                " actual="+actual
            );
    }

    private void closeOwnedInstance(
        Entry entry,
        MatchSessionService.CompositionLease lease
    ){
        instances.beginClosingOwned(
            entry.instanceId,
            lease
        );
        instances.detachOwned(
            entry.instanceId,
            entry.challengerRef,
            lease
        );
        instances.detachOwned(
            entry.instanceId,
            entry.challengedRef,
            lease
        );
        instances.closeOwned(
            entry.instanceId,
            lease
        );
    }

    private void requireAvailable(
        String participant
    ){
        ChallengeId existing=
            openByParticipant.get(
                participant
            );

        if(existing!=null)
            throw new IllegalStateException(
                "participant already has open Duel "+
                participant+
                " challenge="+existing
            );
    }

    private static void requireChallengedActor(
        Entry entry,
        String actorRef,
        String operation
    ){
        String actor=
            PartyService.requireRef(
                actorRef
            );

        if(!entry.challengedRef.equals(
                actor))
            throw new IllegalArgumentException(
                operation+
                " requires challenged participant "+
                entry.challengedRef+
                " actor="+actor
            );
    }

    private static String requireParticipant(
        Entry entry,
        String playerRef
    ){
        String participant=
            PartyService.requireRef(
                playerRef
            );

        if(!entry.challengerRef.equals(
                participant)&&
           !entry.challengedRef.equals(
                participant))
            throw new IllegalArgumentException(
                "player not part of Duel "+
                participant
            );

        return participant;
    }

    private static MatchTeamId teamForParticipant(
        Entry entry,
        String playerRef
    ){
        String participant=
            requireParticipant(
                entry,
                playerRef
            );

        return entry.challengerRef.equals(
                participant)
            ?entry.challengerTeamId
            :entry.challengedTeamId;
    }

    private void withCompositionOwnership(
        MatchSessionService.MatchInstanceCompositionAction action
    ){
        try{
            matches.withWorldInstanceCompositionOwnership(
                instances,
                action
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected Duel composition ownership failure",
                failure
            );
        }
    }

    private Entry requireState(
        ChallengeId challengeId,
        State expected,
        String operation
    ){
        Entry entry=require(challengeId);

        if(entry.state!=expected)
            throw invalid(
                entry,
                operation
            );

        return entry;
    }

    private Entry require(
        ChallengeId challengeId
    ){
        ChallengeId id=
            Objects.requireNonNull(
                challengeId,
                "challengeId"
            );

        Entry entry=entries.get(id);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown Duel challenge "+
                id
            );

        return entry;
    }

    private void releaseParticipants(
        Entry entry
    ){
        openByParticipant.remove(
            entry.challengerRef,
            entry.challengeId
        );
        openByParticipant.remove(
            entry.challengedRef,
            entry.challengeId
        );
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
            entry.challengeId
        );
    }
}

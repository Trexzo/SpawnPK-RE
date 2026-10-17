package spk.local;

import java.util.*;

/**
 * Protocol-independent World Tournament composition over GlobalEvent,
 * MatchSession and WorldInstance foundations.
 *
 * Pairing, eligibility, prizes, spectator/shop behavior and exact SpawnPK
 * tournament policy remain external. This first playable slice uses an explicit
 * caller-defined single-elimination policy.
 */
final class TournamentService {
    enum EntrantState {
        REGISTERED,
        IN_MATCH,
        ELIMINATED,
        WITHDRAWN
    }

    enum TournamentMatchState {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    static final class EntrantSnapshot {
        final String participantRef;
        final EntrantState state;
        final MatchId activeMatchId;

        EntrantSnapshot(Entrant entrant){
            this.participantRef=
                entrant.participantRef;
            this.state=entrant.state;
            this.activeMatchId=
                entrant.activeMatchId;
        }
    }

    static final class MatchSnapshot {
        final MatchId matchId;
        final WorldInstanceId instanceId;
        final String firstParticipant;
        final String secondParticipant;
        final MatchTeamId firstTeamId;
        final MatchTeamId secondTeamId;
        final TournamentMatchState state;
        final String winnerRef;

        MatchSnapshot(TournamentMatch match){
            this.matchId=match.matchId;
            this.instanceId=
                match.instanceId;
            this.firstParticipant=
                match.firstParticipant;
            this.secondParticipant=
                match.secondParticipant;
            this.firstTeamId=
                match.firstTeamId;
            this.secondTeamId=
                match.secondTeamId;
            this.state=match.state;
            this.winnerRef=
                match.winnerRef;
        }

        boolean terminal(){
            return state!=
                TournamentMatchState.ACTIVE;
        }
    }

    static final class Snapshot {
        final WorldEventId eventId;
        final GlobalEventService.Lifecycle
            eventLifecycle;
        final MatchRules rules;
        final String policyAuthority;
        final List<EntrantSnapshot> entrants;
        final List<MatchSnapshot> matches;

        Snapshot(
            Entry entry,
            GlobalEventService.Snapshot event
        ){
            this.eventId=entry.eventId;
            this.eventLifecycle=
                event.lifecycle;
            this.rules=entry.rules;
            this.policyAuthority=
                entry.policyAuthority;

            ArrayList<EntrantSnapshot>
                entrantSnapshots=
                    new ArrayList<>();

            for(Entrant entrant:
                    entry.entrants.values())
                entrantSnapshots.add(
                    new EntrantSnapshot(
                        entrant
                    )
                );

            this.entrants=
                Collections.unmodifiableList(
                    entrantSnapshots
                );

            ArrayList<MatchSnapshot>
                matchSnapshots=
                    new ArrayList<>();

            for(TournamentMatch match:
                    entry.matches.values())
                matchSnapshots.add(
                    new MatchSnapshot(
                        match
                    )
                );

            this.matches=
                Collections.unmodifiableList(
                    matchSnapshots
                );
        }

        EntrantSnapshot entrant(
            String participantRef
        ){
            String ref=
                PartyService.requireRef(
                    participantRef
                );

            for(EntrantSnapshot entrant:
                    entrants)
                if(entrant.participantRef
                        .equals(ref))
                    return entrant;

            return null;
        }

        MatchSnapshot match(
            MatchId matchId
        ){
            MatchId id=
                Objects.requireNonNull(
                    matchId,
                    "matchId"
                );

            for(MatchSnapshot match:
                    matches)
                if(match.matchId.equals(id))
                    return match;

            return null;
        }
    }

    private static final class Entrant {
        final String participantRef;
        EntrantState state=
            EntrantState.REGISTERED;
        MatchId activeMatchId;

        Entrant(String participantRef){
            this.participantRef=
                participantRef;
        }
    }

    private static final class TournamentMatch {
        final MatchId matchId;
        final WorldInstanceId instanceId;
        final String firstParticipant;
        final String secondParticipant;
        final MatchTeamId firstTeamId=
            MatchTeamId.of(
                "tournament:first"
            );
        final MatchTeamId secondTeamId=
            MatchTeamId.of(
                "tournament:second"
            );

        TournamentMatchState state=
            TournamentMatchState.ACTIVE;
        String winnerRef;
        MatchSessionService.CompositionLease
            childLease;
        GlobalEventService.TerminalHold
            eventHold;

        TournamentMatch(
            MatchId matchId,
            WorldInstanceId instanceId,
            String firstParticipant,
            String secondParticipant
        ){
            this.matchId=matchId;
            this.instanceId=instanceId;
            this.firstParticipant=
                firstParticipant;
            this.secondParticipant=
                secondParticipant;
        }
    }

    private static final class Entry {
        final WorldEventId eventId;
        final MatchRules rules;
        final String policyAuthority;

        final LinkedHashMap<String,Entrant>
            entrants=
                new LinkedHashMap<>();

        final LinkedHashMap<MatchId,TournamentMatch>
            matches=
                new LinkedHashMap<>();

        Entry(
            WorldEventId eventId,
            MatchRules rules,
            String policyAuthority
        ){
            this.eventId=eventId;
            this.rules=rules;
            this.policyAuthority=
                policyAuthority;
        }
    }

    private final GlobalEventService events;
    private final MatchSessionService matches;
    private final WorldInstanceService instances;

    private final LinkedHashMap<WorldEventId,Entry>
        tournaments=
            new LinkedHashMap<>();

    TournamentService(
        GlobalEventService events,
        MatchSessionService matches,
        WorldInstanceService instances
    ){
        this.events=
            Objects.requireNonNull(
                events,
                "events"
            );
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

    synchronized Snapshot registerTournament(
        WorldEventDefinition definition,
        MatchRules rules,
        String policyAuthority
    ){
        WorldEventDefinition checkedDefinition=
            Objects.requireNonNull(
                definition,
                "definition"
            );
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

        if(tournaments.containsKey(
                checkedDefinition.id))
            throw new IllegalStateException(
                "duplicate tournament "+
                checkedDefinition.id
            );

        if(events.get(
                checkedDefinition.id)!=null)
            throw new IllegalStateException(
                "GlobalEvent already exists "+
                checkedDefinition.id
            );

        if(checkedRules.teamMode!=
                MatchRules.TeamMode.TEAMS)
            throw new IllegalArgumentException(
                "Tournament MatchRules must use TEAMS"
            );

        if(!authority.equals(
                checkedRules.sourceAuthority))
            throw new IllegalArgumentException(
                "Tournament MatchRules authority mismatch"
            );

        if(!authority.equals(
                checkedDefinition.sourceAuthority))
            throw new IllegalArgumentException(
                "Tournament event authority mismatch"
            );

        Entry entry=
            new Entry(
                checkedDefinition.id,
                checkedRules,
                authority
            );
        final Snapshot[] result=
            new Snapshot[1];

        try{
            events.registerWithCompositionOwnership(
                checkedDefinition,
                ()->{
                    Snapshot created=
                        snapshotOf(
                            entry
                        );
                    tournaments.put(
                        entry.eventId,
                        entry
                    );
                    result[0]=created;
                }
            );
        }catch(RuntimeException failure){
            tournaments.remove(
                entry.eventId,
                entry
            );
            throw failure;
        }catch(Error failure){
            tournaments.remove(
                entry.eventId,
                entry
            );
            throw failure;
        }catch(Exception failure){
            tournaments.remove(
                entry.eventId,
                entry
            );
            throw new IllegalStateException(
                "unexpected Tournament GlobalEvent registration ownership failure",
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "registered Tournament snapshot"
        );
    }

    synchronized Snapshot registerEntrant(
        WorldEventId eventId,
        String participantRef
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    requireEvent(entry);

                if(event.lifecycle!=
                        GlobalEventService
                            .Lifecycle.SCHEDULED)
                    throw new IllegalStateException(
                        "Tournament registration closed lifecycle="+
                        event.lifecycle
                    );

                String participant=
                    PartyService.requireRef(
                        participantRef
                    );

                if(entry.entrants.containsKey(
                        participant))
                    throw new IllegalStateException(
                        "duplicate tournament entrant "+
                        participant
                    );

                entry.entrants.put(
                    participant,
                    new Entrant(participant)
                );

                result[0]=
                    new Snapshot(
                        entry,
                        event
                    );
            }
        );

        return result[0];
    }

    synchronized Snapshot withdrawEntrant(
        WorldEventId eventId,
        String participantRef
    ){
        Entry entry=require(eventId);
        Entrant entrant=
            requireEntrant(
                entry,
                participantRef
            );

        if(entrant.state==
                EntrantState.WITHDRAWN)
            return snapshotOf(entry);

        if(entrant.state!=
                EntrantState.REGISTERED)
            throw new IllegalStateException(
                "cannot withdraw entrant from "+
                entrant.state+
                " ref="+
                entrant.participantRef
            );

        entrant.state=
            EntrantState.WITHDRAWN;

        return snapshotOf(entry);
    }

    synchronized Snapshot startMatch(
        WorldEventId eventId,
        String firstParticipant,
        String secondParticipant,
        MatchId matchId,
        WorldInstanceId instanceId
    ){
        Entry entry=require(eventId);

        Entrant first=
            requireRegistered(
                entry,
                firstParticipant
            );
        Entrant second=
            requireRegistered(
                entry,
                secondParticipant
            );

        if(first.participantRef.equals(
                second.participantRef))
            throw new IllegalArgumentException(
                "Tournament match participants must differ"
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

        if(entry.matches.containsKey(
                checkedMatchId))
            throw new IllegalStateException(
                "Tournament match id already exists "+
                checkedMatchId
            );

        TournamentMatch tournamentMatch=
            new TournamentMatch(
                checkedMatchId,
                checkedInstanceId,
                first.participantRef,
                second.participantRef
            );

        final Snapshot[] result=
            new Snapshot[1];

        /*
         * Explicit lock order:
         * TournamentService -> GlobalEventService ->
         * MatchSessionService -> WorldInstanceService.
         */
        withEventAndCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    requireEvent(entry);

                if(event.lifecycle!=
                        GlobalEventService
                            .Lifecycle.ACTIVE)
                    throw new IllegalStateException(
                        "Tournament match requires ACTIVE event lifecycle="+
                        event.lifecycle
                    );

                if(matches.get(
                        checkedMatchId)!=null)
                    throw new IllegalStateException(
                        "Tournament match id already exists "+
                        checkedMatchId
                    );

                if(instances.get(
                        checkedInstanceId)!=null)
                    throw new IllegalStateException(
                        "Tournament instance id already exists "+
                        checkedInstanceId
                    );

                String holdLabel=
                    terminalHoldLabel(
                        checkedMatchId
                    );

                tournamentMatch.eventHold=
                    events.acquireTerminalHold(
                        entry.eventId,
                        holdLabel
                    );

                boolean published=false;
                boolean childLeaseAcquired=false;

                try{
                    matches.create(
                        checkedMatchId,
                        entry.rules
                    );
                    matches.addTeam(
                        checkedMatchId,
                        tournamentMatch.firstTeamId
                    );
                    matches.addTeam(
                        checkedMatchId,
                        tournamentMatch.secondTeamId
                    );
                    matches.join(
                        checkedMatchId,
                        tournamentMatch.firstTeamId,
                        tournamentMatch.firstParticipant
                    );
                    matches.join(
                        checkedMatchId,
                        tournamentMatch.secondTeamId,
                        tournamentMatch.secondParticipant
                    );

                    instances.create(
                        checkedInstanceId,
                        checkedMatchId.toString(),
                        entry.policyAuthority
                    );
                    instances.attach(
                        checkedInstanceId,
                        tournamentMatch.firstParticipant
                    );
                    instances.attach(
                        checkedInstanceId,
                        tournamentMatch.secondParticipant
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

                    tournamentMatch.childLease=
                        matches.acquireWorldInstanceCompositionLease(
                            instances,
                            checkedMatchId,
                            checkedInstanceId,
                            holdLabel
                        );
                    childLeaseAcquired=true;

                    first.state=
                        EntrantState.IN_MATCH;
                    first.activeMatchId=
                        checkedMatchId;
                    second.state=
                        EntrantState.IN_MATCH;
                    second.activeMatchId=
                        checkedMatchId;

                    entry.matches.put(
                        checkedMatchId,
                        tournamentMatch
                    );

                    published=true;

                    result[0]=
                        new Snapshot(
                            entry,
                            event
                        );
                }finally{
                    if(!published){
                        if(childLeaseAcquired){
                            matches.releaseWorldInstanceCompositionLease(
                                instances,
                                checkedMatchId,
                                checkedInstanceId,
                                requireChildLease(
                                    tournamentMatch
                                )
                            );
                            tournamentMatch.childLease=null;
                        }

                        events.releaseTerminalHold(
                            requireEventHold(
                                tournamentMatch
                            )
                        );
                        tournamentMatch.eventHold=null;
                    }
                }
            }
        );

        return result[0];
    }

    synchronized Snapshot completeMatch(
        WorldEventId eventId,
        MatchId matchId,
        String winnerRef,
        String outcomeKey,
        String decisionAuthority
    ){
        Entry entry=require(eventId);
        TournamentMatch tournamentMatch=
            requireActiveMatch(
                entry,
                matchId
            );

        String winner=
            PartyService.requireRef(
                winnerRef
            );

        String loser;
        MatchTeamId winnerTeam;

        if(tournamentMatch.firstParticipant
                .equals(winner)){
            loser=
                tournamentMatch.secondParticipant;
            winnerTeam=
                tournamentMatch.firstTeamId;
        }else if(tournamentMatch
                .secondParticipant
                .equals(winner)){
            loser=
                tournamentMatch.firstParticipant;
            winnerTeam=
                tournamentMatch.secondTeamId;
        }else
            throw new IllegalArgumentException(
                "winner not in tournament match "+
                winner
            );

        MatchSession.Result result=
            new MatchSession.Result(
                outcomeKey,
                winnerTeam,
                MatchRules.requireText(
                    decisionAuthority,
                    "decisionAuthority"
                )
            );

        withEventAndCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    requireEvent(entry);

                if(event.lifecycle!=
                        GlobalEventService
                            .Lifecycle.ACTIVE)
                    throw new IllegalStateException(
                        "Tournament child completion requires ACTIVE event lifecycle="+
                        event.lifecycle
                    );

                preflightOwnedInstance(
                    tournamentMatch
                );

                MatchSessionService.CompositionLease lease=
                    requireChildLease(
                        tournamentMatch
                    );

                matches.completeOwned(
                    tournamentMatch.matchId,
                    result,
                    lease
                );
                closeOwnedInstance(
                    tournamentMatch,
                    lease
                );

                Entrant winningEntrant=
                    requireEntrant(
                        entry,
                        winner
                    );
                Entrant losingEntrant=
                    requireEntrant(
                        entry,
                        loser
                    );

                winningEntrant.state=
                    EntrantState.REGISTERED;
                winningEntrant.activeMatchId=
                    null;

                losingEntrant.state=
                    EntrantState.ELIMINATED;
                losingEntrant.activeMatchId=
                    null;

                tournamentMatch.state=
                    TournamentMatchState.COMPLETED;
                tournamentMatch.winnerRef=
                    winner;

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    tournamentMatch.matchId,
                    tournamentMatch.instanceId,
                    lease
                );
                tournamentMatch.childLease=null;

                events.releaseTerminalHold(
                    requireEventHold(
                        tournamentMatch
                    )
                );
                tournamentMatch.eventHold=null;
            }
        );

        return snapshotOf(entry);
    }

    synchronized Snapshot cancelMatch(
        WorldEventId eventId,
        MatchId matchId,
        String reasonKey
    ){
        Entry entry=require(eventId);
        TournamentMatch tournamentMatch=
            requireActiveMatch(
                entry,
                matchId
            );
        String reason=
            MatchRules.normalizeKey(
                reasonKey,
                "reasonKey"
            );

        withEventAndCompositionOwnership(
            entry.eventId,
            ()->{
                GlobalEventService.Snapshot event=
                    requireEvent(entry);

                if(event.lifecycle!=
                        GlobalEventService
                            .Lifecycle.ACTIVE)
                    throw new IllegalStateException(
                        "Tournament child cancellation requires ACTIVE event lifecycle="+
                        event.lifecycle
                    );

                preflightOwnedInstance(
                    tournamentMatch
                );

                MatchSessionService.CompositionLease lease=
                    requireChildLease(
                        tournamentMatch
                    );

                matches.cancelOwned(
                    tournamentMatch.matchId,
                    reason,
                    lease
                );
                closeOwnedInstance(
                    tournamentMatch,
                    lease
                );

                Entrant first=
                    requireEntrant(
                        entry,
                        tournamentMatch.firstParticipant
                    );
                Entrant second=
                    requireEntrant(
                        entry,
                        tournamentMatch.secondParticipant
                    );

                first.state=
                    EntrantState.REGISTERED;
                first.activeMatchId=null;
                second.state=
                    EntrantState.REGISTERED;
                second.activeMatchId=null;

                tournamentMatch.state=
                    TournamentMatchState.CANCELLED;

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    tournamentMatch.matchId,
                    tournamentMatch.instanceId,
                    lease
                );
                tournamentMatch.childLease=null;

                events.releaseTerminalHold(
                    requireEventHold(
                        tournamentMatch
                    )
                );
                tournamentMatch.eventHold=null;
            }
        );

        return snapshotOf(entry);
    }

    synchronized Snapshot completeTournament(
        WorldEventId eventId,
        long worldTick
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                requireNoActiveMatches(entry);

                GlobalEventService.MutationResult mutation=
                    events.complete(
                        entry.eventId,
                        worldTick
                    );

                result[0]=
                    new Snapshot(
                        entry,
                        mutation.snapshot
                    );
            }
        );

        return result[0];
    }

    synchronized Snapshot cancelTournament(
        WorldEventId eventId,
        long worldTick
    ){
        Entry entry=require(eventId);
        final Snapshot[] result=
            new Snapshot[1];

        withEventCompositionOwnership(
            entry.eventId,
            ()->{
                requireNoActiveMatches(entry);

                GlobalEventService.MutationResult mutation=
                    events.cancel(
                        entry.eventId,
                        worldTick
                    );

                for(Entrant entrant:
                        entry.entrants.values())
                    if(entrant.state==
                            EntrantState.REGISTERED)
                        entrant.state=
                            EntrantState.WITHDRAWN;

                result[0]=
                    new Snapshot(
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
            tournaments.get(
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
        return tournaments.size();
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
                "unexpected Tournament GlobalEvent ownership failure",
                failure
            );
        }
    }

    private void withEventAndCompositionOwnership(
        WorldEventId eventId,
        MatchSessionService.MatchInstanceCompositionAction action
    ){
        withEventCompositionOwnership(
            eventId,
            ()->matches.withWorldInstanceCompositionOwnership(
                instances,
                action
            )
        );
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
                "unexpected Tournament composition ownership failure",
                failure
            );
        }
    }

    private Snapshot snapshotOf(
        Entry entry
    ){
        return new Snapshot(
            entry,
            requireEvent(entry)
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
            tournaments.get(id);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown tournament "+
                id
            );

        return entry;
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

    private static Entrant requireEntrant(
        Entry entry,
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        Entrant entrant=
            entry.entrants.get(
                participant
            );

        if(entrant==null)
            throw new IllegalArgumentException(
                "unknown tournament entrant "+
                participant
            );

        return entrant;
    }

    private static Entrant requireRegistered(
        Entry entry,
        String participantRef
    ){
        Entrant entrant=
            requireEntrant(
                entry,
                participantRef
            );

        if(entrant.state!=
                EntrantState.REGISTERED)
            throw new IllegalStateException(
                "tournament entrant not REGISTERED "+
                entrant.participantRef+
                " state="+entrant.state
            );

        return entrant;
    }

    private static TournamentMatch
        requireActiveMatch(
            Entry entry,
            MatchId matchId
        ){
        MatchId id=
            Objects.requireNonNull(
                matchId,
                "matchId"
            );

        TournamentMatch match=
            entry.matches.get(id);

        if(match==null)
            throw new IllegalArgumentException(
                "unknown tournament match "+
                id
            );

        if(match.state!=
                TournamentMatchState.ACTIVE)
            throw new IllegalStateException(
                "tournament match terminal "+
                id+
                " state="+match.state
            );

        return match;
    }

    private void preflightOwnedInstance(
        TournamentMatch tournamentMatch
    ){
        MatchSession match=
            matches.get(
                tournamentMatch.matchId
            );

        if(match==null||
           match.state!=
                MatchSession.State.ACTIVE)
            throw new IllegalStateException(
                "Tournament MatchSession not active "+
                tournamentMatch.matchId
            );

        WorldInstanceService.Snapshot instance=
            instances.get(
                tournamentMatch.instanceId
            );

        if(instance==null||
           instance.lifecycle!=
                WorldInstanceService
                    .Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "Tournament WorldInstance not active "+
                tournamentMatch.instanceId
            );

        LinkedHashSet<String> expected=
            new LinkedHashSet<>(
                Arrays.asList(
                    tournamentMatch.firstParticipant,
                    tournamentMatch.secondParticipant
                )
            );

        LinkedHashSet<String> actual=
            new LinkedHashSet<>(
                instance.participants
            );

        if(!expected.equals(actual))
            throw new IllegalStateException(
                "Tournament instance participant drift expected="+
                expected+
                " actual="+actual
            );
    }

    private void closeOwnedInstance(
        TournamentMatch tournamentMatch,
        MatchSessionService.CompositionLease lease
    ){
        instances.beginClosingOwned(
            tournamentMatch.instanceId,
            lease
        );
        instances.detachOwned(
            tournamentMatch.instanceId,
            tournamentMatch.firstParticipant,
            lease
        );
        instances.detachOwned(
            tournamentMatch.instanceId,
            tournamentMatch.secondParticipant,
            lease
        );
        instances.closeOwned(
            tournamentMatch.instanceId,
            lease
        );
    }

    private static MatchSessionService.CompositionLease
        requireChildLease(
            TournamentMatch match
        ){
        if(match.childLease==null)
            throw new IllegalStateException(
                "Tournament child lease missing "+
                match.matchId
            );

        return match.childLease;
    }

    private static GlobalEventService.TerminalHold
        requireEventHold(
            TournamentMatch match
        ){
        if(match.eventHold==null)
            throw new IllegalStateException(
                "Tournament event hold missing "+
                match.matchId
            );

        return match.eventHold;
    }

    private static String terminalHoldLabel(
        MatchId matchId
    ){
        return "tournament-match:"+
            Objects.requireNonNull(
                matchId,
                "matchId"
            );
    }

    private static void requireNoActiveMatches(
        Entry entry
    ){
        for(TournamentMatch match:
                entry.matches.values())
            if(match.state==
                    TournamentMatchState.ACTIVE)
                throw new IllegalStateException(
                    "tournament has active match "+
                    match.matchId
                );
    }
}

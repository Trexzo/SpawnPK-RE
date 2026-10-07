package spk.local;

import java.util.*;

/**
 * World-owned LocalLab registration slice over the reusable Tournament domain.
 *
 * G9.1 keeps the backing event SCHEDULED until explicit runtime activation.
 * G9.2 adds caller-paired 1v1 start. G9.3 adds explicit caller-resolved match
 * completion. G9.4 adds explicit multi-round reuse and terminal completion.
 * Automatic pairing, PvP winner inference, champion policy, arenas, rewards
 * and all original SpawnPK tournament policy remain external authority.
 */
final class LocalLabTournamentRuntime {
    @FunctionalInterface
    interface MatchAdmissionFence {
        void requireAvailable(
            String firstParticipant,
            String secondParticipant
        );
    }

    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G91_TOURNAMENT_REGISTRATION_V1";
    static final WorldEventId EVENT_ID=
        WorldEventId.of(
            "locallab:tournament:g91"
        );
    static final String PVP_DEATH_AUTHORITY=
        "LOCAL_LAB_CANONICAL_PVP_DEATH";

    /*
     * The event is registered SCHEDULED and advances only when this runtime
     * explicitly ticks GlobalEventService. No World pulse is bound here.
     */
    private static final long START_TICK=0L;
    private static final long END_TICK=Long.MAX_VALUE;

    static final class RegistrationResult {
        final boolean created;
        final TournamentService.Snapshot snapshot;

        RegistrationResult(
            boolean created,
            TournamentService.Snapshot snapshot
        ){
            this.created=created;
            this.snapshot=Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        }
    }

    static final class PvpDeathResult {
        final boolean tournamentMatch;
        final boolean completedNow;
        final MatchId matchId;
        final TournamentService.Snapshot snapshot;

        PvpDeathResult(
            boolean tournamentMatch,
            boolean completedNow,
            MatchId matchId,
            TournamentService.Snapshot snapshot
        ){
            this.tournamentMatch=tournamentMatch;
            this.completedNow=completedNow;
            this.matchId=matchId;
            this.snapshot=Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        }
    }

    static final class MatchStartResult {
        final MatchId matchId;
        final WorldInstanceId instanceId;
        final TournamentService.Snapshot snapshot;

        MatchStartResult(
            MatchId matchId,
            WorldInstanceId instanceId,
            TournamentService.Snapshot snapshot
        ){
            this.matchId=Objects.requireNonNull(
                matchId,
                "matchId"
            );
            this.instanceId=Objects.requireNonNull(
                instanceId,
                "instanceId"
            );
            this.snapshot=Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        }
    }

    private final GlobalEventService events=
        new GlobalEventService();
    private final MatchSessionService matches=
        new MatchSessionService();
    private final WorldInstanceService instances=
        new WorldInstanceService();
    private final TournamentService tournament=
        new TournamentService(
            events,
            matches,
            instances
        );
    private long matchSequence=1L;
    private final LinkedHashMap<String,PvpDeathResult>
        pvpDeathByIdentity=
            new LinkedHashMap<>();
    private final Object competitiveAdmissionLock;
    private MatchAdmissionFence matchAdmissionFence=
        (first,second)->{};
    private boolean matchAdmissionFenceInstalled;

    LocalLabTournamentRuntime(){
        this(new Object());
    }

    LocalLabTournamentRuntime(
        Object competitiveAdmissionLock
    ){
        this.competitiveAdmissionLock=
            Objects.requireNonNull(
                competitiveAdmissionLock,
                "competitiveAdmissionLock"
            );
        TournamentService.Snapshot created=
            tournament.registerTournament(
                new WorldEventDefinition(
                    EVENT_ID,
                    START_TICK,
                    END_TICK,
                    Collections.singletonList(
                        new WorldEventDefinition
                            .PhaseDefinition(
                                "registration",
                                START_TICK
                            )
                    ),
                    AUTHORITY
                ),
                new MatchRules(
                    MatchRules.TeamMode.TEAMS,
                    MatchRules.SpellPolicy.UNRESTRICTED,
                    MatchRules.PrayerPolicy.UNRESTRICTED,
                    MatchRules.RestrictionPolicy.ALLOWED,
                    MatchRules.RestrictionPolicy.ALLOWED,
                    MatchRules.WinConditionKind.CALLER_RESOLVED,
                    MatchRules.NO_SCORE_TARGET,
                    "locallab_tournament_registration",
                    AUTHORITY
                ),
                AUTHORITY
            );

        if(created.eventLifecycle!=
                GlobalEventService.Lifecycle.SCHEDULED)
            throw new IllegalStateException(
                "G9.1 Tournament did not initialize SCHEDULED"
            );
    }

    synchronized void installMatchAdmissionFence(
        MatchAdmissionFence fence
    ){
        if(matchAdmissionFenceInstalled)
            throw new IllegalStateException(
                "Tournament match admission fence already installed"
            );

        matchAdmissionFence=
            Objects.requireNonNull(
                fence,
                "fence"
            );
        matchAdmissionFenceInstalled=true;
    }

    boolean participantInActiveMatch(
        String participantRef
    ){
        TournamentService.EntrantSnapshot entrant=
            tournament.get(
                EVENT_ID
            ).entrant(
                PartyService.requireRef(
                    participantRef
                )
            );

        return entrant!=null&&
            entrant.state==
                TournamentService.EntrantState.IN_MATCH;
    }

    synchronized RegistrationResult register(
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        TournamentService.Snapshot before=
            tournament.get(
                EVENT_ID
            );

        TournamentService.EntrantSnapshot existing=
            before.entrant(
                participant
            );

        if(existing!=null)
            return new RegistrationResult(
                false,
                before
            );

        return new RegistrationResult(
            true,
            tournament.registerEntrant(
                EVENT_ID,
                participant
            )
        );
    }

    synchronized MatchStartResult activateAndStartMatch(
        String firstParticipant,
        String secondParticipant,
        long worldTick
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        String first=
            PartyService.requireRef(
                firstParticipant
            );
        String second=
            PartyService.requireRef(
                secondParticipant
            );

        if(first.equals(second))
            throw new IllegalArgumentException(
                "Tournament participants must differ"
            );

        TournamentService.Snapshot before=
            tournament.get(
                EVENT_ID
            );

        TournamentService.EntrantSnapshot firstEntrant=
            before.entrant(first);
        TournamentService.EntrantSnapshot secondEntrant=
            before.entrant(second);

        if(firstEntrant==null||
           secondEntrant==null||
           firstEntrant.state!=
                TournamentService.EntrantState.REGISTERED||
           secondEntrant.state!=
                TournamentService.EntrantState.REGISTERED)
            throw new IllegalStateException(
                "G9.2 requires two REGISTERED entrants"
            );

        synchronized(competitiveAdmissionLock){
            matchAdmissionFence.requireAvailable(
                first,
                second
            );

            events.tick(worldTick);

            GlobalEventService.Snapshot event=
                events.get(
                    EVENT_ID
                );

            if(event.lifecycle!=
                    GlobalEventService.Lifecycle.ACTIVE)
                throw new IllegalStateException(
                    "G9.2 Tournament did not activate lifecycle="+
                    event.lifecycle
                );

            long sequence=matchSequence;
            MatchId matchId=
                MatchId.of(
                    "locallab:tournament:g92:match:"+
                    sequence
                );
            WorldInstanceId instanceId=
                WorldInstanceId.of(
                    "locallab:tournament:g92:instance:"+
                    sequence
                );

            TournamentService.Snapshot started=
                tournament.startMatch(
                    EVENT_ID,
                    first,
                    second,
                    matchId,
                    instanceId
                );

            matchSequence=
                Math.addExact(
                    sequence,
                    1L
                );

            return new MatchStartResult(
                matchId,
                instanceId,
                started
            );
        }
    }

    synchronized TournamentService.Snapshot completeMatch(
        MatchId matchId,
        String winnerRef
    ){
        MatchId id=Objects.requireNonNull(
            matchId,
            "matchId"
        );
        String winner=
            PartyService.requireRef(
                winnerRef
            );

        TournamentService.Snapshot before=
            tournament.get(
                EVENT_ID
            );
        TournamentService.MatchSnapshot match=
            before.match(id);

        if(match==null||
           match.state!=
                TournamentService
                    .TournamentMatchState.ACTIVE)
            throw new IllegalStateException(
                "G9.3 requires ACTIVE Tournament match "+
                id
            );

        if(!winner.equals(
                match.firstParticipant)&&
           !winner.equals(
                match.secondParticipant))
            throw new IllegalArgumentException(
                "winner not in Tournament match "+
                winner
            );

        return tournament.completeMatch(
            EVENT_ID,
            id,
            winner,
            "caller-resolved-win",
            AUTHORITY
        );
    }

    synchronized boolean claimsCanonicalPvpDeath(
        String attackerRef,
        String victimRef,
        long deathSequence
    ){
        String attacker=
            PartyService.requireRef(
                attackerRef
            );
        String victim=
            PartyService.requireRef(
                victimRef
            );

        if(attacker.equals(victim))
            throw new IllegalArgumentException(
                "Tournament PvP attacker/victim identical"
            );

        String deathKey=
            pvpDeathKey(
                attacker,
                victim,
                deathSequence
            );

        if(pvpDeathByIdentity.containsKey(
                deathKey))
            return true;

        TournamentService.Snapshot before=
            tournament.get(
                EVENT_ID
            );
        TournamentService.MatchSnapshot active=
            null;

        for(TournamentService.MatchSnapshot match:
                before.matches){
            if(match.state!=
                    TournamentService
                        .TournamentMatchState.ACTIVE)
                continue;

            boolean exactPair=
                attacker.equals(
                    match.firstParticipant
                )&&
                victim.equals(
                    match.secondParticipant
                )||
                attacker.equals(
                    match.secondParticipant
                )&&
                victim.equals(
                    match.firstParticipant
                );

            if(!exactPair)
                continue;

            if(active!=null)
                throw new IllegalStateException(
                    "multiple ACTIVE Tournament matches for canonical PvP pair"
                );

            active=match;
        }

        return active!=null;
    }

    synchronized PvpDeathResult recordCanonicalPvpDeath(
        String attackerRef,
        String victimRef,
        long deathSequence
    ){
        String attacker=
            PartyService.requireRef(
                attackerRef
            );
        String victim=
            PartyService.requireRef(
                victimRef
            );

        if(attacker.equals(victim))
            throw new IllegalArgumentException(
                "Tournament PvP attacker/victim identical"
            );

        String deathKey=
            pvpDeathKey(
                attacker,
                victim,
                deathSequence
            );

        PvpDeathResult existing=
            pvpDeathByIdentity.get(
                deathKey
            );
        if(existing!=null)
            return existing;

        TournamentService.Snapshot before=
            tournament.get(
                EVENT_ID
            );
        TournamentService.MatchSnapshot active=
            null;

        for(TournamentService.MatchSnapshot match:
                before.matches){
            if(match.state!=
                    TournamentService
                        .TournamentMatchState.ACTIVE)
                continue;

            boolean exactPair=
                attacker.equals(
                    match.firstParticipant
                )&&
                victim.equals(
                    match.secondParticipant
                )||
                attacker.equals(
                    match.secondParticipant
                )&&
                victim.equals(
                    match.firstParticipant
                );

            if(!exactPair)
                continue;

            if(active!=null)
                throw new IllegalStateException(
                    "multiple ACTIVE Tournament matches for canonical PvP pair"
                );

            active=match;
        }

        if(active==null)
            return new PvpDeathResult(
                false,
                false,
                null,
                before
            );

        TournamentService.Snapshot completed=
            tournament.completeMatch(
                EVENT_ID,
                active.matchId,
                attacker,
                "canonical-pvp-death",
                PVP_DEATH_AUTHORITY
            );

        PvpDeathResult result=
            new PvpDeathResult(
                true,
                true,
                active.matchId,
                completed
            );

        pvpDeathByIdentity.put(
            deathKey,
            result
        );

        return result;
    }

    synchronized void retireCanonicalPvpDeath(
        String attackerRef,
        String victimRef,
        long deathSequence
    ){
        String attacker=
            PartyService.requireRef(
                attackerRef
            );
        String victim=
            PartyService.requireRef(
                victimRef
            );

        pvpDeathByIdentity.remove(
            pvpDeathKey(
                attacker,
                victim,
                deathSequence
            )
        );
    }

    synchronized int pvpDeathDedupeCount(){
        return pvpDeathByIdentity.size();
    }

    private static String pvpDeathKey(
        String attacker,
        String victim,
        long deathSequence
    ){
        if(deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+deathSequence
            );

        return attacker+"|"+
            victim+"|"+
            Long.toUnsignedString(
                deathSequence
            );
    }

    synchronized TournamentService.Snapshot completeTournament(
        long worldTick
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        return tournament.completeTournament(
            EVENT_ID,
            worldTick
        );
    }

    synchronized TournamentService.Snapshot snapshot(){
        return tournament.get(
            EVENT_ID
        );
    }

    synchronized int entrantCount(){
        return snapshot()
            .entrants
            .size();
    }

    TournamentService tournament(){
        return tournament;
    }

    GlobalEventService events(){
        return events;
    }

    MatchSessionService matches(){
        return matches;
    }

    WorldInstanceService instances(){
        return instances;
    }
}

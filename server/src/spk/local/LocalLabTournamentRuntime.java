package spk.local;

import java.util.Collections;
import java.util.Objects;

/**
 * World-owned LocalLab registration slice over the reusable Tournament domain.
 *
 * G9.1 keeps the backing event SCHEDULED until explicit runtime activation.
 * G9.2 adds caller-paired 1v1 start only. Automatic pairing, arenas, rewards
 * and all original SpawnPK tournament policy remain external authority.
 */
final class LocalLabTournamentRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G91_TOURNAMENT_REGISTRATION_V1";
    static final WorldEventId EVENT_ID=
        WorldEventId.of(
            "locallab:tournament:g91"
        );

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

    LocalLabTournamentRuntime(){
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

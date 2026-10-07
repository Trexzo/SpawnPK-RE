package spk.local;

import java.util.Collections;
import java.util.Objects;

/**
 * World-owned LocalLab registration slice over the reusable Tournament domain.
 *
 * G9.1 deliberately keeps the backing event SCHEDULED. Event activation,
 * pairing, arenas, rewards and all original SpawnPK tournament policy remain
 * external authority.
 */
final class LocalLabTournamentRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G91_TOURNAMENT_REGISTRATION_V1";
    static final WorldEventId EVENT_ID=
        WorldEventId.of(
            "locallab:tournament:g91"
        );

    /*
     * Sentinel semantic window only. G9.1 never advances the backing event;
     * a later gameplay slice must explicitly own activation/scheduling.
     */
    private static final long SENTINEL_START_TICK=
        Long.MAX_VALUE-1L;
    private static final long SENTINEL_END_TICK=
        Long.MAX_VALUE;

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

    LocalLabTournamentRuntime(){
        TournamentService.Snapshot created=
            tournament.registerTournament(
                new WorldEventDefinition(
                    EVENT_ID,
                    SENTINEL_START_TICK,
                    SENTINEL_END_TICK,
                    Collections.singletonList(
                        new WorldEventDefinition
                            .PhaseDefinition(
                                "registration",
                                SENTINEL_START_TICK
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

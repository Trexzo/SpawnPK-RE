package spk.local;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

/**
 * World-owned LocalLab Daily Challenge runtime.
 *
 * G14.1 deliberately owns one opt-in PvM challenge only. The challenge is
 * assigned on first status access and may then advance exclusively from the
 * caller-validated Monster Spawner terminal-finalization seam.
 *
 * Reset cadence, rewards, claims and persistence are intentionally absent.
 */
final class LocalLabDailyChallengeRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G14_DAILY_PVM_V1";
    static final int CLIENT_CHALLENGE_ID=1;
    static final String CHALLENGE_KEY=
        "locallab:daily:pvm:kills";
    static final String OBJECTIVE_KEY=
        "locallab:daily:pvm:kills:objective";
    static final long GOAL=3L;

    static final class StatusResult {
        final boolean assignedNow;
        final DailyChallengeApplicationService.ChallengeSnapshot
            challenge;

        StatusResult(
            boolean assignedNow,
            DailyChallengeApplicationService.ChallengeSnapshot
                challenge
        ){
            this.assignedNow=assignedNow;
            this.challenge=
                Objects.requireNonNull(
                    challenge,
                    "challenge"
                );
        }
    }

    static final class ProgressResult {
        final boolean assigned;
        final boolean eligibleDefinition;
        final boolean progressed;
        final boolean completedNow;
        final DailyChallengeApplicationService.ChallengeSnapshot
            challenge;

        ProgressResult(
            boolean assigned,
            boolean eligibleDefinition,
            boolean progressed,
            boolean completedNow,
            DailyChallengeApplicationService.ChallengeSnapshot
                challenge
        ){
            this.assigned=assigned;
            this.eligibleDefinition=eligibleDefinition;
            this.progressed=progressed;
            this.completedNow=completedNow;
            this.challenge=challenge;
        }
    }

    private final World world;
    private final DailyChallengeApplicationService service=
        new DailyChallengeApplicationService();

    LocalLabDailyChallengeRuntime(
        World world
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    StatusResult status(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        WorldPlayer owner=
            requireCurrentPlayer(
                player
            );
        long generation=
            owner.generation();
        final StatusResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        DailyChallengeApplicationService
                            .PlayerSnapshot existing=
                                service.get(
                                    player
                                );

                        boolean assignedNow=
                            existing==null;

                        if(assignedNow)
                            service.replaceAll(
                                player,
                                Collections.singletonList(
                                    assignment()
                                )
                            );

                        DailyChallengeApplicationService
                            .ChallengeSnapshot challenge=
                                service.info(
                                    player,
                                    CHALLENGE_KEY
                                );

                        result[0]=
                            new StatusResult(
                                assignedNow,
                                challenge
                            );
                    }
                );

            if(!current)
                throw new IllegalStateException(
                    "stale Daily Challenge player "+
                    player
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Daily Challenge status ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Daily Challenge status result"
        );
    }

    ProgressResult recordMonsterSpawnerFinalization(
        String playerRef,
        int definitionId,
        long deathTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        if(deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+deathTick
            );

        WorldPlayer owner=
            requireCurrentPlayer(
                player
            );
        long generation=
            owner.generation();
        final ProgressResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        DailyChallengeApplicationService
                            .PlayerSnapshot existing=
                                service.get(
                                    player
                                );

                        if(existing==null){
                            result[0]=
                                new ProgressResult(
                                    false,
                                    definitionId==
                                        LocalLabMonsterSpawnerProvisioning
                                            .NPC_DEFINITION_ID,
                                    false,
                                    false,
                                    null
                                );
                            return;
                        }

                        DailyChallengeApplicationService
                            .ChallengeSnapshot before=
                                existing.challenge(
                                    CHALLENGE_KEY
                                );

                        if(before==null)
                            throw new IllegalStateException(
                                "Daily Challenge assignment missing "+
                                CHALLENGE_KEY
                            );

                        if(definitionId!=
                                LocalLabMonsterSpawnerProvisioning
                                    .NPC_DEFINITION_ID){
                            result[0]=
                                new ProgressResult(
                                    true,
                                    false,
                                    false,
                                    false,
                                    before
                                );
                            return;
                        }

                        DailyChallengeApplicationService
                            .ProgressResult progress=
                                service.recordValidatedProgress(
                                    player,
                                    CHALLENGE_KEY,
                                    1L
                                );

                        result[0]=
                            new ProgressResult(
                                true,
                                true,
                                progress.challenge.current>
                                    before.current,
                                progress.completedNow,
                                progress.challenge
                            );
                    }
                );

            if(!current)
                return new ProgressResult(
                    false,
                    definitionId==
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                    false,
                    false,
                    null
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Daily Challenge PvM credit ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Daily Challenge PvM credit result"
        );
    }

    DailyChallengeApplicationService.PlayerSnapshot get(
        String playerRef
    ){
        return service.get(
            normalizePlayer(
                playerRef
            )
        );
    }

    private DailyChallengeApplicationService.AssignmentSpec
        assignment()
    {
        return new DailyChallengeApplicationService
            .AssignmentSpec(
                CLIENT_CHALLENGE_ID,
                CHALLENGE_KEY,
                "Kill 3 certified Monster Spawner PvM targets",
                "CUSTOM_LOCALLAB_MONSTER_SPAWNER_TERMINAL",
                "NPC_DEFINITION_ID="+
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                new ObjectiveDefinition(
                    OBJECTIVE_KEY,
                    GOAL,
                    AUTHORITY
                ),
                0L,
                false,
                AUTHORITY
            );
    }

    private WorldPlayer requireCurrentPlayer(
        String player
    ){
        WorldPlayer owner=
            world.players().byName(
                player
            );

        if(owner==null||
           !world.players().owns(
                owner,
                owner.generation()))
            throw new IllegalStateException(
                "Daily Challenge requires exact current player "+
                player
            );

        return owner;
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String player=
            value.trim().toLowerCase(
                Locale.ROOT
            );

        if(player.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return player;
    }
}

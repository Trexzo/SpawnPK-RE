package spk.local;

import java.util.*;

/**
 * World-owned LocalLab Daily Challenge runtime.
 *
 * G14.1 deliberately owns one opt-in PvM challenge only. The challenge is
 * assigned on first status access and may then advance exclusively from the
 * caller-validated Monster Spawner terminal-finalization seam.
 *
 * Reset cadence, rewards and claims remain absent. G14.2 persists only the
 * explicit LocalLab assignment/progress through a versioned snapshot extension.
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
    private final Set<WorldPlayer> hydratedPlayers=
        Collections.newSetFromMap(
            new IdentityHashMap<WorldPlayer,Boolean>()
        );
    private final IdentityHashMap<WorldPlayer,String>
        invalidPersistence=
            new IdentityHashMap<>();

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
                        ensureRestoredOwned(
                            player,
                            owner
                        );

                        String invalid=
                            invalidPersistence.get(
                                owner
                            );

                        if(invalid!=null)
                            throw new IllegalStateException(
                                "Daily Challenge persistence invalid player="+
                                player+
                                " reason="+
                                invalid
                            );

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
                                    assignment(0L)
                                )
                            );

                        DailyChallengeApplicationService
                            .ChallengeSnapshot challenge=
                                service.info(
                                    player,
                                    CHALLENGE_KEY
                                );

                        if(assignedNow)
                            persistOwned(
                                owner,
                                challenge
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
                        ensureRestoredOwned(
                            player,
                            owner
                        );

                        String invalid=
                            invalidPersistence.get(
                                owner
                            );

                        if(invalid!=null)
                            throw new IllegalStateException(
                                "Daily Challenge persistence invalid player="+
                                player+
                                " reason="+
                                invalid
                            );

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

                        persistOwned(
                            owner,
                            progress.challenge
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
        assignment(
            long initialProgress
        )
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
                initialProgress,
                false,
                AUTHORITY
            );
    }

    private void ensureRestoredOwned(
        String player,
        WorldPlayer owner
    ){
        synchronized(this){
            if(hydratedPlayers.contains(owner))
                return;
        }

        SortedMap<String,String> values=
            owner.snapshotExtensions()
                .namespace(
                    LocalLabDailyChallengePersistence
                        .NAMESPACE
                );

        final LocalLabDailyChallengePersistence.Snapshot
            decoded;

        try{
            decoded=
                LocalLabDailyChallengePersistence
                    .decode(values);
        }catch(RuntimeException failure){
            synchronized(this){
                invalidPersistence.put(
                    owner,
                    failure.getMessage()
                );
                hydratedPlayers.add(owner);
            }
            return;
        }

        if(decoded!=null){
            DailyChallengeApplicationService.PlayerSnapshot
                restored=
                    service.replaceAll(
                        player,
                        Collections.singletonList(
                            assignment(
                                decoded.progress
                            )
                        )
                    );

            DailyChallengeApplicationService.ChallengeSnapshot
                challenge=
                    restored.challenge(
                        CHALLENGE_KEY
                    );

            if(challenge==null||
               challenge.current!=
                    decoded.progress||
               challenge.complete!=
                    decoded.complete())
                throw new IllegalStateException(
                    "Daily Challenge persisted replay mismatch player="+
                    player
                );
        }

        synchronized(this){
            invalidPersistence.remove(owner);
            hydratedPlayers.add(owner);
        }
    }

    private void persistOwned(
        WorldPlayer owner,
        DailyChallengeApplicationService.ChallengeSnapshot
            challenge
    ){
        owner.snapshotExtensions()
            .replaceNamespace(
                LocalLabDailyChallengePersistence
                    .NAMESPACE,
                LocalLabDailyChallengePersistence
                    .encode(challenge)
            );

        synchronized(this){
            invalidPersistence.remove(owner);
            hydratedPlayers.add(owner);
        }
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

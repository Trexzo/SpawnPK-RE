package spk.local;

import java.util.*;

/**
 * World-owned CUSTOM_LOCALLAB Adventure gameplay runtime.
 *
 * G16.3 owns one explicit PvM chapter and one objective only. Progress is
 * attributed exclusively from the already-certified Monster Spawner terminal
 * finalization seam after explicit per-player activation.
 *
 * Reward settlement, teleport execution, persistence and original SpawnPK
 * Adventure policy remain outside this runtime.
 */
final class LocalLabAdventureRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G16_ADVENTURE_PVM_V1";
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT_V308_ADVENTURE_BOOK";
    static final String CHAPTER_KEY=
        "locallab:adventure:pvm";
    static final String OBJECTIVE_KEY=
        "locallab:adventure:pvm:kills";
    static final long GOAL=3L;

    static final class ActivationResult {
        final boolean activatedNow;
        final AdventureService.Snapshot adventure;

        ActivationResult(
            boolean activatedNow,
            AdventureService.Snapshot adventure
        ){
            this.activatedNow=activatedNow;
            this.adventure=
                Objects.requireNonNull(
                    adventure,
                    "adventure"
                );
        }
    }

    static final class ProgressResult {
        final boolean activated;
        final boolean eligibleDefinition;
        final boolean progressed;
        final boolean completedNow;
        final AdventureService.Snapshot adventure;
        final ObjectiveProgressService.Snapshot objective;

        ProgressResult(
            boolean activated,
            boolean eligibleDefinition,
            boolean progressed,
            boolean completedNow,
            AdventureService.Snapshot adventure,
            ObjectiveProgressService.Snapshot objective
        ){
            this.activated=activated;
            this.eligibleDefinition=
                eligibleDefinition;
            this.progressed=progressed;
            this.completedNow=completedNow;
            this.adventure=adventure;
            this.objective=objective;
        }
    }

    private static final class PlayerState {
        final ObjectiveProgressService objectives;
        final AdventureService adventure;

        PlayerState(
            ObjectiveProgressService objectives,
            AdventureService adventure
        ){
            this.objectives=
                Objects.requireNonNull(
                    objectives,
                    "objectives"
                );
            this.adventure=
                Objects.requireNonNull(
                    adventure,
                    "adventure"
                );
        }
    }

    private final World world;
    private final Map<String,PlayerState> byPlayer=
        new HashMap<>();

    LocalLabAdventureRuntime(
        World world
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    ActivationResult activate(
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
        final ActivationResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        PlayerState existing=
                            state(
                                player
                            );

                        if(existing!=null){
                            result[0]=
                                new ActivationResult(
                                    false,
                                    existing.adventure
                                        .snapshot()
                                );
                            return;
                        }

                        PlayerState created=
                            createState();

                        synchronized(this){
                            PlayerState raced=
                                byPlayer.get(
                                    player
                                );

                            if(raced!=null){
                                result[0]=
                                    new ActivationResult(
                                        false,
                                        raced.adventure
                                            .snapshot()
                                    );
                                return;
                            }

                            byPlayer.put(
                                player,
                                created
                            );
                        }

                        result[0]=
                            new ActivationResult(
                                true,
                                created.adventure
                                    .snapshot()
                            );
                    }
                );

            if(!current)
                throw new IllegalStateException(
                    "stale Adventure player "+
                    player
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Adventure activation ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Adventure activation result"
        );
    }

    AdventureService.Snapshot snapshot(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        requireCurrentPlayer(
            player
        );

        PlayerState state=
            state(
                player
            );

        return state==null
            ?null
            :state.adventure.snapshot();
    }

    AdventureBookProjectionMapper.Snapshot projection(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        requireCurrentPlayer(
            player
        );

        PlayerState state=
            state(
                player
            );

        return state==null
            ?null
            :state.adventure
                .currentProjection();
    }

    ObjectiveProgressService.Snapshot objective(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        requireCurrentPlayer(
            player
        );

        PlayerState state=
            state(
                player
            );

        return state==null
            ?null
            :state.objectives.get(
                OBJECTIVE_KEY
            );
    }

    ProgressResult recordMonsterSpawnerFinalization(
        String playerRef,
        int definitionId,
        long deathTick
    ){
        if(deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+deathTick
            );

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
        final ProgressResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        PlayerState state=
                            state(
                                player
                            );

                        if(state==null){
                            result[0]=
                                new ProgressResult(
                                    false,
                                    definitionId==
                                        LocalLabMonsterSpawnerProvisioning
                                            .NPC_DEFINITION_ID,
                                    false,
                                    false,
                                    null,
                                    null
                                );
                            return;
                        }

                        ObjectiveProgressService.Snapshot before=
                            state.objectives.get(
                                OBJECTIVE_KEY
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
                                    state.adventure
                                        .snapshot(),
                                    before
                                );
                            return;
                        }

                        ObjectiveProgressService.ProgressResult
                            progress=
                                state.objectives
                                    .advance(
                                        OBJECTIVE_KEY,
                                        1L
                                    );

                        result[0]=
                            new ProgressResult(
                                true,
                                true,
                                progress.after.progress>
                                    progress.before.progress,
                                progress.completedNow,
                                state.adventure
                                    .snapshot(),
                                progress.after
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
                    null,
                    null
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Adventure PvM credit ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Adventure PvM progress result"
        );
    }

    synchronized int activePlayerCount(){
        return byPlayer.size();
    }

    private PlayerState createState(){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                OBJECTIVE_KEY,
                GOAL,
                AUTHORITY
            )
        );

        AdventureService adventure=
            new AdventureService(
                objectives,
                new AdventureBookProjectionMapper(),
                PRESENTATION_AUTHORITY,
                AUTHORITY
            );

        adventure.replaceChapters(
            Collections.singletonList(
                new AdventureService.ChapterSpec(
                    CHAPTER_KEY,
                    Collections.singletonList(
                        OBJECTIVE_KEY
                    )
                )
            )
        );

        return new PlayerState(
            objectives,
            adventure
        );
    }

    private synchronized PlayerState state(
        String player
    ){
        return byPlayer.get(
            player
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
                "Adventure requires exact current player "+
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
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(player.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return player;
    }
}

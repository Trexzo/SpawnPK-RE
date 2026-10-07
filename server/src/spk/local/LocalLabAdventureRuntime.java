package spk.local;

import java.util.*;

/**
 * World-owned CUSTOM_LOCALLAB Adventure gameplay runtime.
 *
 * G16 owns one explicit PvM chapter and one objective only. Progress is
 * attributed exclusively from the already-certified Monster Spawner terminal
 * finalization seam after explicit per-player activation.
 *
 * G16.5 persists only activation/progress through a versioned PlayerSnapshot
 * extension. Reward settlement, teleport execution, reset cadence and original
 * SpawnPK Adventure policy remain outside this runtime.
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
    private final Set<WorldPlayer> hydratedPlayers=
        Collections.newSetFromMap(
            new IdentityHashMap<WorldPlayer,Boolean>()
        );
    private final IdentityHashMap<WorldPlayer,String>
        invalidPersistence=
            new IdentityHashMap<>();

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
                        ensureRestoredOwned(
                            player,
                            owner
                        );
                        requirePersistenceValid(
                            player,
                            owner
                        );

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
                            createState(
                                0L
                            );

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

                        persistOwned(
                            owner,
                            created.objectives.get(
                                OBJECTIVE_KEY
                            )
                        );

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
        PlayerState state=
            resolveState(
                playerRef
            );

        return state==null
            ?null
            :state.adventure.snapshot();
    }

    AdventureBookProjectionMapper.Snapshot projection(
        String playerRef
    ){
        PlayerState state=
            resolveState(
                playerRef
            );

        return state==null
            ?null
            :state.adventure
                .currentProjection();
    }

    ObjectiveProgressService.Snapshot objective(
        String playerRef
    ){
        PlayerState state=
            resolveState(
                playerRef
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
                        ensureRestoredOwned(
                            player,
                            owner
                        );
                        requirePersistenceValid(
                            player,
                            owner
                        );

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

                        persistOwned(
                            owner,
                            progress.after
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

    private PlayerState resolveState(
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
        final PlayerState[] result={null};

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
                        requirePersistenceValid(
                            player,
                            owner
                        );
                        result[0]=
                            state(
                                player
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
                "Adventure state ownership failed player="+
                player,
                failure
            );
        }

        return result[0];
    }

    private void ensureRestoredOwned(
        String player,
        WorldPlayer owner
    ){
        synchronized(this){
            if(hydratedPlayers.contains(
                    owner))
                return;
        }

        SortedMap<String,String> values=
            owner.snapshotExtensions()
                .namespace(
                    LocalLabAdventurePersistence
                        .NAMESPACE
                );

        try{
            LocalLabAdventurePersistence.Snapshot
                decoded=
                    LocalLabAdventurePersistence
                        .decode(values);

            if(decoded==null){
                synchronized(this){
                    /*
                     * Snapshot absence is authoritative for this player
                     * identity. Do not leak a prior same-username runtime
                     * entry across unregister/register boundaries.
                     */
                    byPlayer.remove(
                        player
                    );
                }
            }else{
                PlayerState restored=
                    createState(
                        decoded.progress
                    );

                ObjectiveProgressService.Snapshot
                    objective=
                        restored.objectives.get(
                            OBJECTIVE_KEY
                        );

                if(objective.progress!=
                        decoded.progress||
                   objective.goal!=GOAL||
                   objective.complete!=
                        decoded.complete()||
                   objective.claimed)
                    throw new IllegalStateException(
                        "Adventure persisted replay mismatch player="+
                        player
                    );

                synchronized(this){
                    /*
                     * The loaded namespace is authoritative for a newly
                     * hydrated player identity, so it replaces any stale
                     * same-username entry left by an older registration.
                     */
                    byPlayer.put(
                        player,
                        restored
                    );
                }
            }
        }catch(RuntimeException failure){
            synchronized(this){
                byPlayer.remove(
                    player
                );
                invalidPersistence.put(
                    owner,
                    failure.getMessage()==null
                        ?failure.getClass()
                            .getSimpleName()
                        :failure.getMessage()
                );
                hydratedPlayers.add(
                    owner
                );
            }
            return;
        }

        synchronized(this){
            invalidPersistence.remove(
                owner
            );
            hydratedPlayers.add(
                owner
            );
        }
    }

    private void requirePersistenceValid(
        String player,
        WorldPlayer owner
    ){
        final String invalid;

        synchronized(this){
            invalid=
                invalidPersistence.get(
                    owner
                );
        }

        if(invalid!=null)
            throw new IllegalStateException(
                "Adventure persistence invalid player="+
                player+
                " reason="+
                invalid
            );
    }

    private void persistOwned(
        WorldPlayer owner,
        ObjectiveProgressService.Snapshot objective
    ){
        owner.snapshotExtensions()
            .replaceNamespace(
                LocalLabAdventurePersistence
                    .NAMESPACE,
                LocalLabAdventurePersistence
                    .encode(
                        objective
                    )
            );

        synchronized(this){
            invalidPersistence.remove(
                owner
            );
            hydratedPlayers.add(
                owner
            );
        }
    }

    private PlayerState createState(
        long initialProgress
    ){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                OBJECTIVE_KEY,
                GOAL,
                AUTHORITY
            ),
            initialProgress,
            false
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

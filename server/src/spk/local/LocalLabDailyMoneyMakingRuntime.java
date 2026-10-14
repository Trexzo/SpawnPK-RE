package spk.local;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * World-owned CUSTOM_LOCALLAB Daily Money Making runtime.
 *
 * G18.3 owns one explicit EASY PvM activity only. Progress is attributed
 * exclusively from the already-certified Monster Spawner terminal-finalization
 * seam after explicit activation. MEDIUM/HARD activities, rewards, teleport
 * policy, reset cadence and original SpawnPK rules remain absent.
 */
final class LocalLabDailyMoneyMakingRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G18_DAILY_MONEY_MAKING_EASY_PVM_V1";
    static final String OBJECTIVE_KEY=
        "locallab:dmm:easy:pvm:kills";
    static final long GOAL=3L;

    static final class Snapshot {
        final DailyMoneyMakingStateService.Snapshot state;
        final ObjectiveProgressService.Snapshot objective;

        Snapshot(
            DailyMoneyMakingStateService.Snapshot state,
            ObjectiveProgressService.Snapshot objective
        ){
            this.state=Objects.requireNonNull(
                state,
                "state"
            );
            this.objective=Objects.requireNonNull(
                objective,
                "objective"
            );
        }
    }

    static final class ActivationResult {
        final boolean activatedNow;
        final Snapshot snapshot;

        ActivationResult(
            boolean activatedNow,
            Snapshot snapshot
        ){
            this.activatedNow=activatedNow;
            this.snapshot=Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        }
    }

    static final class ProgressResult {
        final boolean activated;
        final boolean eligibleDefinition;
        final boolean progressed;
        final boolean completedNow;
        final Snapshot snapshot;

        ProgressResult(
            boolean activated,
            boolean eligibleDefinition,
            boolean progressed,
            boolean completedNow,
            Snapshot snapshot
        ){
            this.activated=activated;
            this.eligibleDefinition=eligibleDefinition;
            this.progressed=progressed;
            this.completedNow=completedNow;
            this.snapshot=snapshot;
        }
    }

    private static final class PlayerState {
        final ObjectiveProgressService objectives;
        final DailyMoneyMakingStateService state;

        PlayerState(
            ObjectiveProgressService objectives,
            DailyMoneyMakingStateService state
        ){
            this.objectives=Objects.requireNonNull(
                objectives,
                "objectives"
            );
            this.state=Objects.requireNonNull(
                state,
                "state"
            );
        }

        Snapshot snapshot(){
            return new Snapshot(
                state.snapshot(),
                Objects.requireNonNull(
                    objectives.get(OBJECTIVE_KEY),
                    "objective"
                )
            );
        }
    }

    private final World world;
    private final Map<String,PlayerState> byPlayer=
        new HashMap<>();

    LocalLabDailyMoneyMakingRuntime(
        World world
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
    }

    ActivationResult activateEasy(
        String playerRef
    ){
        String player=normalizePlayer(
            playerRef
        );
        WorldPlayer owner=requireCurrentPlayer(
            player
        );
        ActivationResult result=activateEasyIfCurrent(
            player,owner,owner.generation()
        );
        if(result==null)
            throw new IllegalStateException(
                "stale Daily Money Making player "+player
            );
        return result;
    }

    /**
     * Exact-session activation seam: a stale UI must not retarget another
     * WorldPlayer simply because its old username was admitted again.
     * Null means this exact player/generation/name is not current.
     */
    ActivationResult activateEasyIfCurrent(
        String playerRef,
        WorldPlayer owner,
        long expectedGeneration
    ){
        String player=normalizePlayer(playerRef);
        Objects.requireNonNull(owner,"owner");
        if(expectedGeneration<0L)
            throw new IllegalArgumentException("generation="+expectedGeneration);

        final ActivationResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    expectedGeneration,
                    ()->{
                        if(world.players().byName(player)!=owner)
                            return;

                        PlayerState existing=state(
                            player
                        );

                        if(existing!=null){
                            Snapshot snapshot=existing.snapshot();

                            if(snapshot.state.selectedDifficulty!=
                                    DailyMoneyMakingStateService
                                        .Difficulty.EASY||
                               !OBJECTIVE_KEY.equals(
                                    snapshot.state.trackedObjectiveKey))
                                throw new IllegalStateException(
                                    "Daily Money Making active state drift player="+
                                    player
                                );

                            result[0]=new ActivationResult(
                                false,
                                snapshot
                            );
                            return;
                        }

                        PlayerState created=createState();

                        synchronized(this){
                            PlayerState raced=byPlayer.get(
                                player
                            );

                            if(raced!=null){
                                result[0]=new ActivationResult(
                                    false,
                                    raced.snapshot()
                                );
                                return;
                            }

                            byPlayer.put(
                                player,
                                created
                            );
                        }

                        result[0]=new ActivationResult(
                            true,
                            created.snapshot()
                        );
                    }
                );

            if(!current)
                return null;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Daily Money Making activation ownership failed player="+
                player,
                failure
            );
        }

        return result[0];
    }

    Snapshot snapshot(
        String playerRef
    ){
        String player=normalizePlayer(
            playerRef
        );
        WorldPlayer owner=requireCurrentPlayer(
            player
        );
        long generation=owner.generation();
        final Snapshot[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        PlayerState state=state(
                            player
                        );
                        result[0]=
                            state==null
                                ?null
                                :state.snapshot();
                    }
                );

            if(!current)
                throw new IllegalStateException(
                    "stale Daily Money Making player "+
                    player
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Daily Money Making snapshot ownership failed player="+
                player,
                failure
            );
        }

        return result[0];
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

        String player=normalizePlayer(
            playerRef
        );
        WorldPlayer owner=requireCurrentPlayer(
            player
        );
        long generation=owner.generation();
        final ProgressResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        PlayerState state=state(
                            player
                        );

                        if(state==null){
                            result[0]=new ProgressResult(
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

                        Snapshot before=state.snapshot();

                        if(definitionId!=
                                LocalLabMonsterSpawnerProvisioning
                                    .NPC_DEFINITION_ID){
                            result[0]=new ProgressResult(
                                true,
                                false,
                                false,
                                false,
                                before
                            );
                            return;
                        }

                        ObjectiveProgressService.ProgressResult progress=
                            state.objectives.advance(
                                OBJECTIVE_KEY,
                                1L
                            );

                        result[0]=new ProgressResult(
                            true,
                            true,
                            progress.after.progress>
                                progress.before.progress,
                            progress.completedNow,
                            state.snapshot()
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
                "Daily Money Making PvM credit ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Daily Money Making PvM result"
        );
    }

    /**
     * Opt-in exact-origin credit seam for callers that have independently
     * captured the ORIGINAL WorldPlayer and generation of a terminal event.
     *
     * This is NOT wired to the existing name-only Monster Spawner observer:
     * its finalization currently supplies recipientRef, not a verified
     * recipient player/generation from spawn time. Callers must never obtain
     * expectedOwner with a fresh byName lookup at terminal time and claim it
     * proves attribution to an older event.
     *
     * False/null snapshot is a nonmutating stale-owner refusal. The legacy
     * recipientRef-only path remains unchanged until event origin authority
     * is actually recovered.
     */
    ProgressResult recordMonsterSpawnerFinalizationIfCurrent(
        String playerRef,
        WorldPlayer expectedOwner,
        long expectedGeneration,
        int definitionId,
        long deathTick
    ){
        if(deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+deathTick
            );
        if(expectedGeneration<0L)
            throw new IllegalArgumentException(
                "expectedGeneration="+expectedGeneration
            );

        String player=normalizePlayer(playerRef);
        Objects.requireNonNull(expectedOwner,"expectedOwner");

        final ProgressResult[] attributed={null};
        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    expectedOwner,
                    expectedGeneration,
                    ()->{
                        if(world.players().byName(player)!=expectedOwner)
                            return;

                        // The World lifecycle and exact-owner mutation locks
                        // are reentrant, so the existing semantic credit path
                        // executes without reopening an admission race.
                        attributed[0]=recordMonsterSpawnerFinalization(
                            player,definitionId,deathTick
                        );
                    }
                );
            if(!current||attributed[0]==null)
                return new ProgressResult(
                    false,
                    definitionId==
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                    false,false,null
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Daily Money Making exact-origin credit ownership failed player="+
                player,
                failure
            );
        }

        return attributed[0];
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

        DailyMoneyMakingStateService state=
            new DailyMoneyMakingStateService(
                objectives
            );

        state.selectDifficulty(
            DailyMoneyMakingStateService
                .Difficulty.EASY
        );
        state.trackObjective(
            OBJECTIVE_KEY
        );

        return new PlayerState(
            objectives,
            state
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
                "Daily Money Making requires exact current player "+
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

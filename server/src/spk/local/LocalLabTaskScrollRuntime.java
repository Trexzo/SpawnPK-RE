package spk.local;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * World-owned LocalLab Task Scroll runtime.
 *
 * G15.2 deliberately owns one explicit CUSTOM_LOCALLAB PvM task only. Progress
 * may advance exclusively from the already-certified Monster Spawner terminal
 * finalization seam. Rewards, claims, persistence, Track/Collect actions,
 * expiry and original SpawnPK policy remain absent.
 */
final class LocalLabTaskScrollRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G15_TASK_SCROLL_PVM_V1";
    static final String TASK_KEY=
        "locallab:taskscroll:pvm:kills";
    static final String OBJECTIVE_KEY=
        "locallab:taskscroll:pvm:kills:objective";
    static final long GOAL=3L;

    static final class AssignmentResult {
        final boolean assignedNow;
        final TaskScrollService.Snapshot task;

        AssignmentResult(
            boolean assignedNow,
            TaskScrollService.Snapshot task
        ){
            this.assignedNow=assignedNow;
            this.task=
                Objects.requireNonNull(
                    task,
                    "task"
                );
        }
    }

    static final class ProgressResult {
        final boolean assigned;
        final boolean eligibleDefinition;
        final boolean progressed;
        final boolean completedNow;
        final TaskScrollService.Snapshot task;

        ProgressResult(
            boolean assigned,
            boolean eligibleDefinition,
            boolean progressed,
            boolean completedNow,
            TaskScrollService.Snapshot task
        ){
            this.assigned=assigned;
            this.eligibleDefinition=
                eligibleDefinition;
            this.progressed=progressed;
            this.completedNow=completedNow;
            this.task=task;
        }
    }

    private final World world;
    private final Map<String,ObjectiveProgressService>
        ledgers=
            new HashMap<>();
    private final TaskScrollService service;

    LocalLabTaskScrollRuntime(
        World world
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.service=
            new TaskScrollService(
                this::ledgerForExistingPlayer
            );

        service.registerDefinition(
            new TaskScrollService.Definition(
                TASK_KEY,
                OBJECTIVE_KEY,
                java.util.Arrays.asList(
                    "Defeat 3 certified LocalLab Monster Spawner targets.",
                    "Reward settlement is not configured."
                ),
                AUTHORITY
            )
        );
    }

    AssignmentResult assign(
        String playerRef,
        long worldTick
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
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
        final AssignmentResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        ensureLedger(
                            player
                        );

                        TaskScrollService.Snapshot
                            existing=
                                service.active(
                                    player
                                );

                        if(existing!=null){
                            result[0]=
                                new AssignmentResult(
                                    false,
                                    existing
                                );
                            return;
                        }

                        result[0]=
                            new AssignmentResult(
                                true,
                                service.assign(
                                    player,
                                    TASK_KEY,
                                    worldTick
                                )
                            );
                    }
                );

            if(!current)
                throw new IllegalStateException(
                    "stale Task Scroll player "+
                    player
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Task Scroll assignment ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Task Scroll assignment result"
        );
    }

    TaskScrollService.Snapshot active(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        requireCurrentPlayer(
            player
        );

        return service.active(
            player
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
                        TaskScrollService.Snapshot before=
                            service.active(
                                player
                            );

                        if(before==null){
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

                        TaskScrollService.ProgressResult
                            progress=
                                service.recordValidatedProgress(
                                    player,
                                    1L,
                                    deathTick
                                );

                        result[0]=
                            new ProgressResult(
                                true,
                                true,
                                progress.task.objective.progress>
                                    before.objective.progress,
                                progress.completedNow,
                                progress.task
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
                "Task Scroll PvM credit ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Task Scroll PvM progress result"
        );
    }

    int assignmentCount(){
        return service.assignmentCount();
    }

    private synchronized ObjectiveProgressService
        ensureLedger(
            String player
        )
    {
        ObjectiveProgressService existing=
            ledgers.get(
                player
            );

        if(existing!=null)
            return existing;

        ObjectiveProgressService created=
            new ObjectiveProgressService();

        created.define(
            new ObjectiveDefinition(
                OBJECTIVE_KEY,
                GOAL,
                AUTHORITY
            )
        );

        ledgers.put(
            player,
            created
        );

        return created;
    }

    private synchronized ObjectiveProgressService
        ledgerForExistingPlayer(
            String player
        )
    {
        return ledgers.get(
            normalizePlayer(
                player
            )
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
                "Task Scroll requires exact current player "+
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

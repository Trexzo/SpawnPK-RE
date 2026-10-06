package spk.local;

import java.util.*;

/**
 * World-owned LocalLab composition for the first playable Blood Slayer slice.
 *
 * Exact-current client authority proves the Blood Slayer mode vocabulary.
 * Task target, goal, assignment and progression policy below are explicitly
 * LocalLab-owned and must not be represented as recovered SpawnPK balance.
 */
final class LocalLabSlayerRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G4_BLOOD_SLAYER_MONSTER_HUNTER_V1";
    static final String TASK_KEY=
        "locallab:blood-slayer:monster-hunter:definition-1";
    static final String FAMILY_KEY=
        "blood-slayer";
    static final String TARGET_KEY=
        "npc-definition:1";
    static final String OBJECTIVE_KEY=
        "locallab:blood-slayer:monster-hunter:definition-1:kills";
    static final int TARGET_DEFINITION_ID=1;
    static final long OBJECTIVE_GOAL=1L;

    static final class StatusSnapshot {
        final String playerRef;
        final BloodSlayerModeService.Mode selectedMode;
        final SlayerTaskService.Snapshot task;
        final String authority;

        StatusSnapshot(
            String playerRef,
            BloodSlayerModeService.Mode selectedMode,
            SlayerTaskService.Snapshot task
        ){
            this.playerRef=playerRef;
            this.selectedMode=selectedMode;
            this.task=task;
            this.authority=AUTHORITY;
        }

        boolean hasTask(){
            return task!=null;
        }

        boolean active(){
            return task!=null&&
                task.state==SlayerTaskService.State.ACTIVE;
        }

        boolean complete(){
            return task!=null&&
                task.state==SlayerTaskService.State.COMPLETED;
        }
    }

    static final class StartResult {
        final boolean created;
        final StatusSnapshot status;

        StartResult(
            boolean created,
            StatusSnapshot status
        ){
            this.created=created;
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
        }
    }

    static final class KillCreditResult {
        final boolean eligibleDefinition;
        final boolean activeTask;
        final boolean progressed;
        final boolean completedNow;
        final StatusSnapshot status;

        KillCreditResult(
            boolean eligibleDefinition,
            boolean activeTask,
            boolean progressed,
            boolean completedNow,
            StatusSnapshot status
        ){
            this.eligibleDefinition=eligibleDefinition;
            this.activeTask=activeTask;
            this.progressed=progressed;
            this.completedNow=completedNow;
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
        }
    }

    private final LinkedHashMap<String,ObjectiveProgressService>
        ledgers=new LinkedHashMap<>();
    private final LinkedHashMap<String,SlayerTaskService.TaskId>
        latestTaskByPlayer=new LinkedHashMap<>();
    private final SlayerTaskService slayer;
    private final BloodSlayerModeService bloodSlayer;

    LocalLabSlayerRuntime(){
        slayer=
            new SlayerTaskService(
                this::ledgerIfPresent
            );

        bloodSlayer=
            new BloodSlayerModeService(
                slayer,
                (playerRef,mode)->{
                    if(mode!=
                            BloodSlayerModeService.Mode
                                .MONSTER_HUNTER_PVM)
                        throw new IllegalArgumentException(
                            "G4.1 allocator only owns Monster Hunter PvM"
                        );
                    return TASK_KEY;
                },
                AUTHORITY
            );

        bloodSlayer.registerTaskDefinition(
            new SlayerTaskService.Definition(
                TASK_KEY,
                FAMILY_KEY,
                TARGET_KEY,
                OBJECTIVE_KEY,
                AUTHORITY
            ),
            Collections.singletonList(
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM
            )
        );
    }

    StartResult startMonsterHunter(
        String playerRef,
        long worldTick
    ){
        String player=normalizePlayer(playerRef);
        requireTick(worldTick);

        SlayerTaskService.Snapshot active=
            slayer.active(player);

        if(active!=null){
            remember(
                player,
                active.taskId
            );
            return new StartResult(
                false,
                snapshot(
                    player,
                    active
                )
            );
        }

        SlayerTaskService.TaskId latest=
            latestTask(player);

        if(latest!=null){
            SlayerTaskService.Snapshot prior=
                slayer.get(latest);

            if(prior!=null&&prior.terminal())
                return new StartResult(
                    false,
                    snapshot(
                        player,
                        prior
                    )
                );
        }

        ObjectiveProgressService ledger=
            ensureLedger(player);

        if(ledger.get(OBJECTIVE_KEY)==null)
            ledger.define(
                new ObjectiveDefinition(
                    OBJECTIVE_KEY,
                    OBJECTIVE_GOAL,
                    AUTHORITY
                )
            );

        bloodSlayer.selectMode(
            player,
            BloodSlayerModeService.Mode
                .MONSTER_HUNTER_PVM
        );

        BloodSlayerModeService.AssignmentResult
            assigned=
                bloodSlayer.requestTask(
                    player,
                    worldTick
                );

        remember(
            player,
            assigned.task.taskId
        );

        return new StartResult(
            true,
            snapshot(
                player,
                assigned.task
            )
        );
    }

    StatusSnapshot status(
        String playerRef
    ){
        String player=normalizePlayer(playerRef);

        SlayerTaskService.Snapshot active=
            slayer.active(player);

        if(active!=null){
            remember(
                player,
                active.taskId
            );
            return snapshot(
                player,
                active
            );
        }

        SlayerTaskService.TaskId latest=
            latestTask(player);

        return snapshot(
            player,
            latest==null
                ?null
                :slayer.get(latest)
        );
    }

    KillCreditResult recordMonsterSpawnerKill(
        String playerRef,
        int definitionId,
        long worldTick
    ){
        String player=normalizePlayer(playerRef);
        requireTick(worldTick);

        if(definitionId!=TARGET_DEFINITION_ID)
            return new KillCreditResult(
                false,
                slayer.active(player)!=null,
                false,
                false,
                status(player)
            );

        SlayerTaskService.Snapshot active=
            slayer.active(player);

        if(active==null)
            return new KillCreditResult(
                true,
                false,
                false,
                false,
                status(player)
            );

        SlayerTaskService.KillResult result=
            slayer.recordValidatedKill(
                player,
                TARGET_KEY,
                1L,
                worldTick
            );

        remember(
            player,
            result.task.taskId
        );

        return new KillCreditResult(
            true,
            true,
            result.progressed,
            result.completedNow,
            snapshot(
                player,
                result.task
            )
        );
    }

    SlayerTaskService slayer(){
        return slayer;
    }

    BloodSlayerModeService bloodSlayer(){
        return bloodSlayer;
    }

    private StatusSnapshot snapshot(
        String player,
        SlayerTaskService.Snapshot task
    ){
        BloodSlayerModeService.Snapshot blood=
            bloodSlayer.get(player);

        return new StatusSnapshot(
            player,
            blood.selectedMode,
            task
        );
    }

    private synchronized ObjectiveProgressService
        ensureLedger(
            String player
        ){
        ObjectiveProgressService ledger=
            ledgers.get(player);

        if(ledger==null){
            ledger=
                new ObjectiveProgressService();
            ledgers.put(
                player,
                ledger
            );
        }

        return ledger;
    }

    private synchronized ObjectiveProgressService
        ledgerIfPresent(
            String player
        ){
        return ledgers.get(
            normalizePlayer(player)
        );
    }

    private synchronized void remember(
        String player,
        SlayerTaskService.TaskId taskId
    ){
        latestTaskByPlayer.put(
            player,
            Objects.requireNonNull(
                taskId,
                "taskId"
            )
        );
    }

    private synchronized SlayerTaskService.TaskId
        latestTask(
            String player
        ){
        return latestTaskByPlayer.get(player);
    }

    private static void requireTick(
        long worldTick
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );
    }

    private static String normalizePlayer(
        String playerRef
    ){
        if(playerRef==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            playerRef.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }
}

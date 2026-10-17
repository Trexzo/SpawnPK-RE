package spk.local;

import java.util.*;

public final class G6BloodSlayerRepeatableIntegrationTest {
    private static final String OWNER="g61-repeat-slayer";

    public static void main(String[] args)throws Exception{
        MemoryPlayers repository=new MemoryPlayers();

        boolean firstCompleted=false;
        boolean renewalFailureAtomic=false;
        boolean secondAssigned=false;
        boolean newTaskId=false;
        boolean oldTaskFrozenComplete=false;
        boolean secondStartsZero=false;
        boolean secondKillCompletes=false;
        boolean freshWorldCompletedRenews=false;
        boolean nativeGetTaskRenews=false;

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer player=
            new WorldPlayer();
        world.registerPlayer(
            player,
            OWNER
        );

        PlayerSnapshot completedSnapshot;

        try{
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    player,
                    world.localLabSlayer()
                );

            LocalBloodSlayerUiHandler.Result selected=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .MONSTER_HUNTER_WIDGET
                    ),
                    world.clock().tick()
                );

            require(
                selected.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED,
                "Monster Hunter mode selection failed"
            );

            LocalBloodSlayerUiHandler.Result first=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    world.clock().tick()
                );

            require(
                first.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                first.taskStatus.active()&&
                first.taskStatus.task.objective
                    .progress==0L,
                "first task assignment failed"
            );

            SlayerTaskService.TaskId firstId=
                first.taskStatus.task.taskId;

            LocalLabSlayerRuntime.KillCreditResult
                firstKill=
                    world.localLabSlayer()
                        .recordMonsterSpawnerKill(
                            OWNER,
                            LocalLabSlayerRuntime
                                .TARGET_DEFINITION_ID,
                            world.clock().tick()
                        );

            firstCompleted=
                firstKill.completedNow&&
                firstKill.status.complete()&&
                firstKill.status.task.objective
                    .progress==1L;

            require(
                firstCompleted,
                "first task did not complete"
            );

            boolean renewalFailed=false;
            Object removedBinding=
                removeTaskBinding(
                    world.localLabSlayer()
                        .bloodSlayer(),
                    LocalLabSlayerRuntime
                        .TASK_KEY
                );

            try{
                try{
                    world.localLabSlayer()
                        .startMonsterHunter(
                            OWNER,
                            world.clock().tick()
                        );
                }catch(IllegalArgumentException expected){
                    renewalFailed=
                        expected.getMessage()!=null&&
                        expected.getMessage()
                            .contains(
                                "unregistered Blood Slayer task"
                            );
                }
            }finally{
                restoreTaskBinding(
                    world.localLabSlayer()
                        .bloodSlayer(),
                    LocalLabSlayerRuntime
                        .TASK_KEY,
                    removedBinding
                );
            }

            LocalLabSlayerRuntime.StatusSnapshot
                afterFailedRenewal=
                    world.localLabSlayer()
                        .status(OWNER);

            renewalFailureAtomic=
                renewalFailed&&
                afterFailedRenewal.complete()&&
                afterFailedRenewal.task.taskId
                    .equals(firstId)&&
                afterFailedRenewal.task.objective
                    .progress==1L&&
                world.localLabSlayer()
                    .slayer()
                    .taskCount()==1;

            require(
                renewalFailureAtomic,
                "failed renewal did not restore completed objective"
            );

            LocalBloodSlayerUiHandler.Result second=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    world.clock().tick()
                );

            secondAssigned=
                second.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                second.taskStatus.active();

            SlayerTaskService.TaskId secondId=
                second.taskStatus.task.taskId;

            newTaskId=
                !secondId.equals(firstId);

            secondStartsZero=
                second.taskStatus.task.objective
                    .progress==0L;

            SlayerTaskService.Snapshot old=
                world.localLabSlayer()
                    .slayer()
                    .get(firstId);

            oldTaskFrozenComplete=
                old!=null&&
                old.state==
                    SlayerTaskService.State
                        .COMPLETED&&
                old.objective.complete&&
                old.objective.progress==1L;

            require(
                secondAssigned&&
                newTaskId&&
                secondStartsZero&&
                oldTaskFrozenComplete,
                "completed task renewal corrupted task history"
            );

            LocalBloodSlayerUiHandler.Result duplicate=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    world.clock().tick()
                );

            require(
                duplicate.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_EXISTING&&
                duplicate.taskStatus.task.taskId
                    .equals(secondId)&&
                world.localLabSlayer()
                    .slayer()
                    .taskCount()==2,
                "active repeat request duplicated task"
            );

            LocalLabSlayerRuntime.KillCreditResult
                secondKill=
                    world.localLabSlayer()
                        .recordMonsterSpawnerKill(
                            OWNER,
                            LocalLabSlayerRuntime
                                .TARGET_DEFINITION_ID,
                            world.clock().tick()
                        );

            secondKillCompletes=
                secondKill.completedNow&&
                secondKill.status.complete()&&
                secondKill.status.task.taskId
                    .equals(secondId)&&
                secondKill.status.task.objective
                    .progress==1L;

            require(
                secondKillCompletes,
                "second task did not complete"
            );

            completedSnapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    player
                );
            repository.save(
                completedSnapshot
            );
        }finally{
            world.close();
        }

        World fresh=
            World.isolatedForTest(
                60_000L,
                repository
            );
        WorldPlayer restored=
            loadAndRegister(
                fresh,
                repository,
                OWNER
            );

        try{
            LocalLabSlayerRuntime.StatusSnapshot
                before=
                    fresh.localLabSlayer()
                        .status(OWNER);

            require(
                before.complete()&&
                before.task.objective.progress==1L,
                "fresh World did not restore completed task"
            );

            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    restored,
                    fresh.localLabSlayer()
                );

            LocalBloodSlayerUiHandler.Result renewed=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    fresh.clock().tick()
                );

            nativeGetTaskRenews=
                renewed.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                LocalBloodSlayerUiHandler.SAVE_TASK
                    .equals(
                        renewed.saveReason
                    );

            freshWorldCompletedRenews=
                nativeGetTaskRenews&&
                renewed.taskStatus.active()&&
                renewed.taskStatus.task.objective
                    .progress==0L&&
                "ACTIVE".equals(
                    restored.snapshotExtensions()
                        .namespace(
                            LocalLabSlayerPersistence
                                .NAMESPACE
                        )
                        .get("state")
                )&&
                "0".equals(
                    restored.snapshotExtensions()
                        .namespace(
                            LocalLabSlayerPersistence
                                .NAMESPACE
                        )
                        .get("progress")
                );

            require(
                freshWorldCompletedRenews,
                "fresh World completed task did not renew"
            );
        }finally{
            fresh.close();
        }

        System.out.println(
            "G6_BLOOD_SLAYER_REPEATABLE_PASS"+
            " firstCompleted="+firstCompleted+
            " renewalFailureAtomic="+
                renewalFailureAtomic+
            " secondAssigned="+secondAssigned+
            " newTaskId="+newTaskId+
            " oldTaskFrozenComplete="+
                oldTaskFrozenComplete+
            " secondStartsZero="+secondStartsZero+
            " secondKillCompletes="+secondKillCompletes+
            " freshWorldCompletedRenews="+
                freshWorldCompletedRenews+
            " nativeGetTaskRenews="+nativeGetTaskRenews+
            " pointsClaim=false"+
            " rewardClaim=false"+
            " originalSpawnpkTaskPolicyClaim=false"
        );
    }

    @SuppressWarnings("unchecked")
    private static Object removeTaskBinding(
        BloodSlayerModeService service,
        String taskKey
    )throws Exception{
        java.lang.reflect.Field field=
            BloodSlayerModeService.class
                .getDeclaredField(
                    "tasks"
                );
        field.setAccessible(true);

        Map<String,Object> tasks=
            (Map<String,Object>)
                field.get(service);

        Object removed=
            tasks.remove(taskKey);

        require(
            removed!=null,
            "missing task binding for injected renewal failure"
        );

        return removed;
    }

    @SuppressWarnings("unchecked")
    private static void restoreTaskBinding(
        BloodSlayerModeService service,
        String taskKey,
        Object binding
    )throws Exception{
        java.lang.reflect.Field field=
            BloodSlayerModeService.class
                .getDeclaredField(
                    "tasks"
                );
        field.setAccessible(true);

        Map<String,Object> tasks=
            (Map<String,Object>)
                field.get(service);

        require(
            tasks.put(
                taskKey,
                binding
            )==null,
            "task binding unexpectedly replaced during renewal injection"
        );
    }

    private static WorldPlayer loadAndRegister(
        World world,
        MemoryPlayers repository,
        String username
    )throws Exception{
        Optional<PlayerSnapshot> loaded=
            repository.load(username);

        require(
            loaded.isPresent(),
            "missing persisted snapshot "+username
        );

        WorldPlayer player=
            new WorldPlayer();

        PlayerSnapshotCodec.applyValidated(
            loaded.get(),
            player
        );
        world.registerPlayer(
            player,
            username
        );

        return player;
    }

    private static final class MemoryPlayers
        implements PlayerRepository {
        private final HashMap<String,PlayerSnapshot>
            values=
                new HashMap<>();

        @Override public synchronized
            Optional<PlayerSnapshot> load(
                String username
            ){
            return Optional.ofNullable(
                values.get(
                    normalize(username)
                )
            );
        }

        @Override public synchronized void save(
            PlayerSnapshot snapshot
        ){
            values.put(
                normalize(
                    snapshot.username()
                ),
                snapshot
            );
        }

        private static String normalize(
            String value
        ){
            return value==null
                ?""
                :value.trim()
                    .toLowerCase(
                        Locale.ROOT
                    );
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G6BloodSlayerRepeatableIntegrationTest(){}
}

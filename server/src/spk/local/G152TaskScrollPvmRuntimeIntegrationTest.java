package spk.local;

public final class G152TaskScrollPvmRuntimeIntegrationTest {
    private static final String PLAYER_A="task-scroll-a";
    private static final String PLAYER_B="task-scroll-b";

    public static void main(String[] args)throws Exception{
        boolean worldOwned=false;
        boolean explicitAssign=false;
        boolean noProgressBeforeAssign=false;
        boolean eligibleDefinitionOnly=false;
        boolean progress1=false;
        boolean progress2=false;
        boolean complete3=false;
        boolean goalClamp=false;
        boolean playerScoped=false;
        boolean sharedTerminalSeam=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                a,
                PLAYER_A
            );
        long generationB=
            world.registerPlayer(
                b,
                PLAYER_B
            );

        try{
            LocalLabTaskScrollRuntime runtime=
                world.localLabTaskScrolls();

            worldOwned=
                runtime!=null&&
                runtime==
                    world.localLabTaskScrolls();

            boolean preCredit=
                LocalLabMonsterSpawnerProvisioning
                    .creditTerminalProgression(
                        world,
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                        new Tile(
                            3087,
                            3495,
                            0
                        ),
                        10L
                    );

            noProgressBeforeAssign=
                preCredit&&
                runtime.active(
                    PLAYER_A
                )==null&&
                runtime.assignmentCount()==0;

            LocalLabTaskScrollRuntime.AssignmentResult
                assignedA=
                    runtime.assign(
                        PLAYER_A,
                        11L
                    );

            LocalLabTaskScrollRuntime.AssignmentResult
                duplicateA=
                    runtime.assign(
                        PLAYER_A,
                        11L
                    );

            LocalLabTaskScrollRuntime.AssignmentResult
                assignedB=
                    runtime.assign(
                        PLAYER_B,
                        11L
                    );

            explicitAssign=
                assignedA.assignedNow&&
                !duplicateA.assignedNow&&
                assignedA.task.taskScrollId.equals(
                    duplicateA.task.taskScrollId
                )&&
                LocalLabTaskScrollRuntime
                    .TASK_KEY.equals(
                        assignedA.task.definition.taskKey
                    )&&
                assignedA.task.objective.goal==
                    LocalLabTaskScrollRuntime.GOAL&&
                runtime.assignmentCount()==2;

            LocalLabTaskScrollRuntime.ProgressResult
                wrong=
                    runtime.recordMonsterSpawnerFinalization(
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID+1,
                        12L
                    );

            eligibleDefinitionOnly=
                wrong.assigned&&
                !wrong.eligibleDefinition&&
                !wrong.progressed&&
                wrong.task.objective.progress==0L;

            sharedTerminalSeam=
                LocalLabMonsterSpawnerProvisioning
                    .creditTerminalProgression(
                        world,
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                        new Tile(
                            3087,
                            3495,
                            0
                        ),
                        13L
                    );

            TaskScrollService.Snapshot afterOne=
                runtime.active(
                    PLAYER_A
                );

            progress1=
                afterOne!=null&&
                afterOne.objective.progress==1L&&
                !afterOne.objective.complete;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER_A,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        3087,
                        3495,
                        0
                    ),
                    14L
                );

            TaskScrollService.Snapshot afterTwo=
                runtime.active(
                    PLAYER_A
                );

            progress2=
                afterTwo!=null&&
                afterTwo.objective.progress==2L&&
                !afterTwo.objective.complete;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER_A,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        3087,
                        3495,
                        0
                    ),
                    15L
                );

            TaskScrollService.Snapshot complete=
                runtime.active(
                    PLAYER_A
                );

            complete3=
                complete!=null&&
                complete.objective.progress==3L&&
                complete.objective.complete&&
                complete.state==
                    TaskScrollService.State
                        .COMPLETE_UNCLAIMED;

            LocalLabTaskScrollRuntime.ProgressResult
                clamped=
                    runtime.recordMonsterSpawnerFinalization(
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                        16L
                    );

            goalClamp=
                !clamped.progressed&&
                !clamped.completedNow&&
                clamped.task.objective.progress==3L&&
                clamped.task.objective.complete;

            TaskScrollService.Snapshot bSnapshot=
                runtime.active(
                    PLAYER_B
                );

            playerScoped=
                assignedB.assignedNow&&
                bSnapshot!=null&&
                bSnapshot.objective.progress==0L&&
                !bSnapshot.objective.complete;

            require(
                worldOwned&&
                explicitAssign&&
                noProgressBeforeAssign&&
                eligibleDefinitionOnly&&
                progress1&&
                progress2&&
                complete3&&
                goalClamp&&
                playerScoped&&
                sharedTerminalSeam,
                "G15.2 acceptance"
            );

            System.out.println(
                "G152_TASK_SCROLL_PVM_RUNTIME_PASS"+
                " worldOwned="+worldOwned+
                " explicitAssign="+explicitAssign+
                " noProgressBeforeAssign="+
                    noProgressBeforeAssign+
                " eligibleDefinitionOnly="+
                    eligibleDefinitionOnly+
                " progress1="+progress1+
                " progress2="+progress2+
                " complete3="+complete3+
                " goalClamp="+goalClamp+
                " playerScoped="+playerScoped+
                " sharedTerminalSeam="+
                    sharedTerminalSeam+
                " rewardPolicyClaim=false"+
                " persistenceClaim=false"+
                " trackClaim=false"+
                " collectClaim=false"+
                " originalServerPolicyClaim=false"
            );
        }finally{
            if(world.players().owns(
                    a,
                    generationA))
                world.unregisterPlayer(
                    a,
                    generationA
                );

            if(world.players().owns(
                    b,
                    generationB))
                world.unregisterPlayer(
                    b,
                    generationB
                );

            world.close();
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G152TaskScrollPvmRuntimeIntegrationTest(){}
}

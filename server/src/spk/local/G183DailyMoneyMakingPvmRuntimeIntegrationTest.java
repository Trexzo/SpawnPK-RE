package spk.local;

public final class G183DailyMoneyMakingPvmRuntimeIntegrationTest {
    private static final String PLAYER_A="dmm-pvm-a";
    private static final String PLAYER_B="dmm-pvm-b";

    public static void main(String[] args)throws Exception{
        boolean worldOwned=false;
        boolean easyOnly=false;
        boolean explicitActivation=false;
        boolean semanticTracking=false;
        boolean noProgressBeforeActivation=false;
        boolean eligibleDefinitionOnly=false;
        boolean progress1=false;
        boolean progress2=false;
        boolean complete3=false;
        boolean goalClamp=false;
        boolean playerScoped=false;
        boolean sharedTerminalSeam=false;

        World world=World.isolatedForTest(60_000L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        long generationA=world.registerPlayer(a,PLAYER_A);
        long generationB=world.registerPlayer(b,PLAYER_B);

        try{
            LocalLabDailyMoneyMakingRuntime runtime=
                world.localLabDailyMoneyMaking();

            worldOwned=
                runtime!=null&&
                runtime==world.localLabDailyMoneyMaking();

            boolean preCredit=
                LocalLabMonsterSpawnerProvisioning
                    .creditTerminalProgression(
                        world,
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                        new Tile(3087,3495,0),
                        10L
                    );

            noProgressBeforeActivation=
                preCredit&&
                runtime.snapshot(PLAYER_A)==null&&
                runtime.activePlayerCount()==0;

            LocalLabDailyMoneyMakingRuntime.ActivationResult activatedA=
                runtime.activateEasy(PLAYER_A);
            LocalLabDailyMoneyMakingRuntime.ActivationResult duplicateA=
                runtime.activateEasy(PLAYER_A);
            LocalLabDailyMoneyMakingRuntime.ActivationResult activatedB=
                runtime.activateEasy(PLAYER_B);

            explicitActivation=
                activatedA.activatedNow&&
                !duplicateA.activatedNow&&
                activatedB.activatedNow&&
                runtime.activePlayerCount()==2;

            semanticTracking=
                activatedA.snapshot.state.selectedDifficulty==
                    DailyMoneyMakingStateService.Difficulty.EASY&&
                LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY.equals(
                    activatedA.snapshot.state.trackedObjectiveKey
                )&&
                activatedA.snapshot.objective.progress==0L&&
                activatedA.snapshot.objective.goal==
                    LocalLabDailyMoneyMakingRuntime.GOAL;

            easyOnly=
                activatedA.snapshot.state.selectedDifficulty==
                    DailyMoneyMakingStateService.Difficulty.EASY&&
                activatedB.snapshot.state.selectedDifficulty==
                    DailyMoneyMakingStateService.Difficulty.EASY;

            LocalLabDailyMoneyMakingRuntime.ProgressResult wrong=
                runtime.recordMonsterSpawnerFinalization(
                    PLAYER_A,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID+1,
                    11L
                );

            eligibleDefinitionOnly=
                wrong.activated&&
                !wrong.eligibleDefinition&&
                !wrong.progressed&&
                wrong.snapshot.objective.progress==0L;

            sharedTerminalSeam=
                LocalLabMonsterSpawnerProvisioning
                    .creditTerminalProgression(
                        world,
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                        new Tile(3087,3495,0),
                        12L
                    );

            LocalLabDailyMoneyMakingRuntime.Snapshot one=
                runtime.snapshot(PLAYER_A);

            progress1=
                one.objective.progress==1L&&
                !one.objective.complete;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER_A,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    new Tile(3087,3495,0),
                    13L
                );

            LocalLabDailyMoneyMakingRuntime.Snapshot two=
                runtime.snapshot(PLAYER_A);

            progress2=
                two.objective.progress==2L&&
                !two.objective.complete;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER_A,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    new Tile(3087,3495,0),
                    14L
                );

            LocalLabDailyMoneyMakingRuntime.Snapshot three=
                runtime.snapshot(PLAYER_A);

            complete3=
                three.objective.progress==3L&&
                three.objective.complete;

            LocalLabDailyMoneyMakingRuntime.ProgressResult clamped=
                runtime.recordMonsterSpawnerFinalization(
                    PLAYER_A,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    15L
                );

            goalClamp=
                !clamped.progressed&&
                !clamped.completedNow&&
                clamped.snapshot.objective.progress==3L&&
                clamped.snapshot.objective.complete;

            LocalLabDailyMoneyMakingRuntime.Snapshot bState=
                runtime.snapshot(PLAYER_B);

            playerScoped=
                bState.objective.progress==0L&&
                !bState.objective.complete&&
                LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY.equals(
                    bState.state.trackedObjectiveKey
                );

            require(
                worldOwned&&easyOnly&&explicitActivation&&semanticTracking&&
                noProgressBeforeActivation&&eligibleDefinitionOnly&&
                progress1&&progress2&&complete3&&goalClamp&&
                playerScoped&&sharedTerminalSeam,
                "G18.3 acceptance"
            );

            System.out.println(
                "G183_DAILY_MONEY_MAKING_PVM_RUNTIME_PASS"+
                " worldOwned="+worldOwned+
                " easyOnly="+easyOnly+
                " explicitActivation="+explicitActivation+
                " semanticTracking="+semanticTracking+
                " noProgressBeforeActivation="+noProgressBeforeActivation+
                " eligibleDefinitionOnly="+eligibleDefinitionOnly+
                " progress1="+progress1+
                " progress2="+progress2+
                " complete3="+complete3+
                " goalClamp="+goalClamp+
                " playerScoped="+playerScoped+
                " sharedTerminalSeam="+sharedTerminalSeam+
                " mediumActivityClaim=false"+
                " hardActivityClaim=false"+
                " rewardPolicyClaim=false"+
                " teleportPolicyClaim=false"+
                " resetPolicyClaim=false"+
                " persistenceClaim=false"+
                " originalServerPolicyClaim=false"
            );
        }finally{
            if(world.players().owns(a,generationA))
                world.unregisterPlayer(a,generationA);
            if(world.players().owns(b,generationB))
                world.unregisterPlayer(b,generationB);
            world.close();
        }
    }

    private static void require(boolean condition,String message){
        if(!condition) throw new AssertionError(message);
    }

    private G183DailyMoneyMakingPvmRuntimeIntegrationTest(){}
}

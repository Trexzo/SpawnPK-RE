package spk.local;

public final class G163AdventurePvmRuntimeIntegrationTest {
    private static final String PLAYER_A="adventure-pvm-a";
    private static final String PLAYER_B="adventure-pvm-b";

    public static void main(String[] args)throws Exception{
        boolean worldOwned=false;
        boolean explicitActivation=false;
        boolean noProgressBeforeActivation=false;
        boolean eligibleDefinitionOnly=false;
        boolean progress1=false;
        boolean progress2=false;
        boolean complete3=false;
        boolean goalClamp=false;
        boolean playerScoped=false;
        boolean adventureServiceReuse=false;
        boolean sharedTerminalSeam=false;
        boolean rewardMutation=false;

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
            LocalLabAdventureRuntime runtime=
                world.localLabAdventures();

            worldOwned=
                runtime!=null&&
                runtime==
                    world.localLabAdventures();

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

            noProgressBeforeActivation=
                preCredit&&
                runtime.snapshot(
                    PLAYER_A
                )==null&&
                runtime.objective(
                    PLAYER_A
                )==null&&
                runtime.activePlayerCount()==0;

            LocalLabAdventureRuntime.ActivationResult
                activatedA=
                    runtime.activate(
                        PLAYER_A
                    );

            LocalLabAdventureRuntime.ActivationResult
                duplicateA=
                    runtime.activate(
                        PLAYER_A
                    );

            LocalLabAdventureRuntime.ActivationResult
                activatedB=
                    runtime.activate(
                        PLAYER_B
                    );

            ObjectiveProgressService.Snapshot
                aInitial=
                    runtime.objective(
                        PLAYER_A
                    );
            ObjectiveProgressService.Snapshot
                bInitial=
                    runtime.objective(
                        PLAYER_B
                    );

            explicitActivation=
                activatedA.activatedNow&&
                !duplicateA.activatedNow&&
                activatedB.activatedNow&&
                runtime.activePlayerCount()==2&&
                LocalLabAdventureRuntime
                    .CHAPTER_KEY.equals(
                        activatedA.adventure
                            .selectedChapterKey
                    )&&
                activatedA.adventure
                    .chapter.objectives.size()==1&&
                aInitial!=null&&
                aInitial.progress==0L&&
                aInitial.goal==
                    LocalLabAdventureRuntime.GOAL&&
                !aInitial.complete&&
                bInitial!=null&&
                bInitial.progress==0L;

            AdventureBookProjectionMapper.Snapshot
                initialProjection=
                    runtime.projection(
                        PLAYER_A
                    );

            adventureServiceReuse=
                initialProjection!=null&&
                initialProjection.rows().size()==1&&
                LocalLabAdventureRuntime
                    .OBJECTIVE_KEY.equals(
                        initialProjection.rows()
                            .get(0)
                            .objectiveKey
                    )&&
                initialProjection.rows()
                    .get(0)
                    .claimWidget()==null;

            LocalLabAdventureRuntime.ProgressResult
                wrong=
                    runtime.recordMonsterSpawnerFinalization(
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID+1,
                        11L
                    );

            eligibleDefinitionOnly=
                wrong.activated&&
                !wrong.eligibleDefinition&&
                !wrong.progressed&&
                wrong.objective.progress==0L;

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
                        12L
                    );

            ObjectiveProgressService.Snapshot one=
                runtime.objective(
                    PLAYER_A
                );

            progress1=
                one!=null&&
                one.progress==1L&&
                !one.complete;

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

            ObjectiveProgressService.Snapshot two=
                runtime.objective(
                    PLAYER_A
                );

            progress2=
                two!=null&&
                two.progress==2L&&
                !two.complete;

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

            ObjectiveProgressService.Snapshot complete=
                runtime.objective(
                    PLAYER_A
                );

            AdventureBookProjectionMapper.Snapshot
                completeProjection=
                    runtime.projection(
                        PLAYER_A
                    );

            complete3=
                complete!=null&&
                complete.progress==3L&&
                complete.complete&&
                !complete.claimed&&
                completeProjection!=null&&
                completeProjection.rows().size()==1&&
                completeProjection.rows()
                    .get(0)
                    .claimWidget()!=null;

            LocalLabAdventureRuntime.ProgressResult
                clamped=
                    runtime.recordMonsterSpawnerFinalization(
                        PLAYER_A,
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                        15L
                    );

            goalClamp=
                !clamped.progressed&&
                !clamped.completedNow&&
                clamped.objective.progress==3L&&
                clamped.objective.complete;

            ObjectiveProgressService.Snapshot
                bAfter=
                    runtime.objective(
                        PLAYER_B
                    );

            playerScoped=
                bAfter!=null&&
                bAfter.progress==0L&&
                !bAfter.complete;

            rewardMutation=
                complete.claimed;

            require(
                worldOwned&&
                explicitActivation&&
                noProgressBeforeActivation&&
                eligibleDefinitionOnly&&
                progress1&&
                progress2&&
                complete3&&
                goalClamp&&
                playerScoped&&
                adventureServiceReuse&&
                sharedTerminalSeam&&
                !rewardMutation,
                "G16.3 acceptance"
            );

            System.out.println(
                "G163_ADVENTURE_PVM_RUNTIME_PASS"+
                " worldOwned="+worldOwned+
                " explicitActivation="+
                    explicitActivation+
                " noProgressBeforeActivation="+
                    noProgressBeforeActivation+
                " eligibleDefinitionOnly="+
                    eligibleDefinitionOnly+
                " progress1="+progress1+
                " progress2="+progress2+
                " complete3="+complete3+
                " goalClamp="+goalClamp+
                " playerScoped="+playerScoped+
                " adventureServiceReuse="+
                    adventureServiceReuse+
                " sharedTerminalSeam="+
                    sharedTerminalSeam+
                " rewardMutation="+rewardMutation+
                " teleportPolicyClaim=false"+
                " persistenceClaim=false"+
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
            throw new AssertionError(
                message
            );
    }

    private G163AdventurePvmRuntimeIntegrationTest(){}
}

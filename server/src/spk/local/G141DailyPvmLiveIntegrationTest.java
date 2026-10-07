package spk.local;

public final class G141DailyPvmLiveIntegrationTest {
    private static final String PLAYER="daily-pvm";

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean worldOwned=false;
        boolean assignedOnFirstStatus=false;
        boolean goal3=false;
        boolean canonicalDefinition1=false;
        boolean terminalProgress1_2_3=false;
        boolean completionAt3=false;
        boolean goalClamp=false;
        boolean repeatedStatusStable=false;
        boolean wrongDefinitionNoProgress=false;
        boolean rewardClaimExposed=false;

        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                PLAYER
            );

        player.movement().restoreAccountState(
            false,
            100,
            MovementState.INITIAL_X,
            MovementState.INITIAL_Y,
            0
        );

        try{
            LocalLabDailyChallengeRuntime daily=
                world.localLabDailyChallenges();

            worldOwned=
                daily!=null&&
                daily==
                    world.localLabDailyChallenges();

            LocalDailyChallengeCommandHandler commands=
                new LocalDailyChallengeCommandHandler(
                    player,
                    daily
                );

            customCommand=
                commands.handle(
                    new String[]{"notdaily"}
                )==null;

            LocalDailyChallengeCommandHandler.Result
                firstStatus=
                    commands.handle(
                        new String[]{"daily"}
                    );

            customCommand&=
                firstStatus!=null&&
                firstStatus.clientMessage.contains(
                    "Daily PvM:"
                );

            DailyChallengeApplicationService.PlayerSnapshot
                assigned=
                    daily.get(
                        PLAYER
                    );

            DailyChallengeApplicationService.ChallengeSnapshot
                challenge=
                    assigned==null
                        ?null
                        :assigned.challenge(
                            LocalLabDailyChallengeRuntime
                                .CHALLENGE_KEY
                        );

            assignedOnFirstStatus=
                firstStatus!=null&&
                firstStatus.logText.contains(
                    "assignedNow=true"
                )&&
                challenge!=null&&
                challenge.current==0L;

            goal3=
                challenge!=null&&
                challenge.target==
                    LocalLabDailyChallengeRuntime
                        .GOAL&&
                challenge.target==3L;

            canonicalDefinition1=
                LocalLabMonsterSpawnerProvisioning
                    .NPC_DEFINITION_ID==1;

            LocalDailyChallengeCommandHandler.Result
                repeated=
                    commands.handle(
                        new String[]{"daily","status"}
                    );

            DailyChallengeApplicationService.ChallengeSnapshot
                repeatedSnapshot=
                    daily.get(
                        PLAYER
                    ).challenge(
                        LocalLabDailyChallengeRuntime
                            .CHALLENGE_KEY
                    );

            repeatedStatusStable=
                repeated!=null&&
                repeated.logText.contains(
                    "assignedNow=false"
                )&&
                repeatedSnapshot.current==0L&&
                repeatedSnapshot.target==3L;

            LocalLabDailyChallengeRuntime.ProgressResult
                wrong=
                    daily.recordMonsterSpawnerFinalization(
                        PLAYER,
                        2,
                        0L
                    );

            DailyChallengeApplicationService.ChallengeSnapshot
                afterWrong=
                    daily.get(
                        PLAYER
                    ).challenge(
                        LocalLabDailyChallengeRuntime
                            .CHALLENGE_KEY
                    );

            wrongDefinitionNoProgress=
                wrong.assigned&&
                !wrong.eligibleDefinition&&
                !wrong.progressed&&
                !wrong.completedNow&&
                wrong.challenge!=null&&
                wrong.challenge.current==0L&&
                afterWrong.current==0L;

            LocalMonsterSpawnerActivationRuntime activation=
                LocalLabMonsterSpawnerProvisioning
                    .create(
                        world
                    );
            MonsterSpawnerService spawner=
                activation.service();

            spawner.openSession(
                PLAYER,
                LocalLabMonsterSpawnerProvisioning
                    .SESSION_AUTHORITY
            );

            MonsterSpawnerService.SessionSnapshot
                selected=
                    spawner.selectRow(
                        PLAYER,
                        0
                    );

            spawner.activateIfCurrent(
                PLAYER,
                selected,
                LocalLabMonsterSpawnerProvisioning
                    .ACTIVATION_BUDGET
            );

            MonsterSpawnerPvmSpawnExecutor.Result
                first=
                    activation.executor()
                        .execute(
                            PLAYER
                        );

            require(
                first.status==
                    MonsterSpawnerPvmSpawnExecutor
                        .Status.SPAWNED&&
                first.spawn!=null,
                "first Daily PvM canonical spawn"
            );

            WorldNpc npc=
                first.spawn.spawn.combat.spawn.npc;

            long[] observed=new long[4];
            boolean[] completedNow=new boolean[4];

            for(int kill=0;kill<4;kill++){
                require(
                    npc!=null&&
                    npc.definitionId==
                        LocalLabMonsterSpawnerProvisioning
                            .NPC_DEFINITION_ID,
                    "Daily PvM canonical definition kill="+
                    kill
                );

                long deathTick=
                    world.clock().tick()+1L;

                NpcLifecycleService.DamageResult lethal=
                    world.npcLifecycle()
                        .applyDamage(
                            npc.id,
                            99,
                            deathTick
                        );

                require(
                    lethal.newlyDied,
                    "Daily PvM lethal kill="+kill
                );

                MonsterSpawnerPvmRuntime.FinalizeResult
                    finalized=
                        world.finalizeMonsterSpawnerPvmIfOwned(
                            npc
                        );

                require(
                    finalized!=null&&
                    finalized.status==
                        MonsterSpawnerPvmRuntime
                            .FinalizeStatus.FINALIZED&&
                    finalized.finalization!=null&&
                    finalized.settlement!=null,
                    "Daily PvM terminal settlement kill="+
                    kill
                );

                DailyChallengeApplicationService.ChallengeSnapshot
                    after=
                        daily.get(
                            PLAYER
                        ).challenge(
                            LocalLabDailyChallengeRuntime
                                .CHALLENGE_KEY
                        );

                observed[kill]=after.current;
                completedNow[kill]=after.complete;

                if(kill<3)
                    npc=
                        awaitRespawn(
                            world,
                            spawner
                        );
            }

            terminalProgress1_2_3=
                observed[0]==1L&&
                observed[1]==2L&&
                observed[2]==3L;

            completionAt3=
                !completedNow[0]&&
                !completedNow[1]&&
                completedNow[2];

            goalClamp=
                observed[3]==3L&&
                completedNow[3];

            LocalDailyChallengeCommandHandler.Result
                completeStatus=
                    commands.handle(
                        new String[]{"daily","status"}
                    );

            repeatedStatusStable&=
                completeStatus!=null&&
                completeStatus.clientMessage.contains(
                    "Daily PvM complete: 3/3"
                )&&
                daily.get(
                    PLAYER
                ).challenge(
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                ).current==3L;

            LocalDailyChallengeCommandHandler.Result
                claimSyntax=
                    commands.handle(
                        new String[]{"daily","claim"}
                    );

            rewardClaimExposed=
                claimSyntax!=null&&
                claimSyntax.logText.contains(
                    "REJECTED_SYNTAX"
                )&&
                claimSyntax.logText.contains(
                    "rewardClaimExposed=false"
                );

            require(
                customCommand&&
                worldOwned&&
                assignedOnFirstStatus&&
                goal3&&
                canonicalDefinition1&&
                terminalProgress1_2_3&&
                completionAt3&&
                goalClamp&&
                repeatedStatusStable&&
                wrongDefinitionNoProgress&&
                rewardClaimExposed,
                "G14.1 acceptance"
            );

            System.out.println(
                "G141_DAILY_PVM_LIVE_PASS"+
                " customCommand="+customCommand+
                " worldOwned="+worldOwned+
                " assignedOnFirstStatus="+
                    assignedOnFirstStatus+
                " goal3="+goal3+
                " canonicalDefinition1="+
                    canonicalDefinition1+
                " terminalProgress1_2_3="+
                    terminalProgress1_2_3+
                " completionAt3="+completionAt3+
                " goalClamp="+goalClamp+
                " repeatedStatusStable="+
                    repeatedStatusStable+
                " wrongDefinitionNoProgress="+
                    wrongDefinitionNoProgress+
                " rewardClaimExposed=false"+
                " rewardPolicyClaim=false"+
                " resetPolicyClaim=false"+
                " persistenceClaim=false"+
                " originalServerPolicyClaim=false"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static WorldNpc awaitRespawn(
        World world,
        MonsterSpawnerService spawner
    )throws Exception{
        for(int i=0;i<20;i++){
            MonsterSpawnerService.SessionSnapshot
                session=
                    spawner.getSession(
                        PLAYER
                    );

            if(session!=null&&
               session.spawnedNpcIds.size()==1){
                WorldNpc npc=
                    world.npcs().byId(
                        session.spawnedNpcIds
                            .get(0)
                    );

                if(npc!=null)
                    return npc;
            }

            long nextTick=
                world.clock().tick()+1L;

            world.pulse().pulseOnce(
                nextTick*
                    GameClock.TICK_MILLIS
            );
        }

        throw new AssertionError(
            "Daily PvM respawn did not materialize"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G141DailyPvmLiveIntegrationTest(){}
}

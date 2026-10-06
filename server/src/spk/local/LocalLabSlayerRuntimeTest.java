package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalLabSlayerRuntimeTest {
    private static final int[] SEED={1,2,3,4};

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=new WorldPlayer();
        WorldPlayer observer=new WorldPlayer();

        world.registerPlayer(
            player,
            "OpenSrc"
        );
        world.registerPlayer(
            observer,
            "observer"
        );

        try{
            LocalLabSlayerRuntime runtime=
                world.localLabSlayer();

            require(
                runtime==world.localLabSlayer(),
                "Slayer runtime is not World-owned"
            );

            LocalSlayerCommandHandler commands=
                new LocalSlayerCommandHandler(
                    player,
                    runtime
                );

            LocalSlayerCommandHandler observerCommands=
                new LocalSlayerCommandHandler(
                    observer,
                    runtime
                );

            Captured empty=
                capture(
                    commands,
                    new String[]{"slayer","status"},
                    0L
                );

            require(
                empty.result.logText.contains(
                    "task=NONE"
                ),
                "empty Slayer status"
            );

            Captured started=
                capture(
                    commands,
                    LocalCommandDispatcher.tokens(
                        LocalCommandDispatcher.clean(
                            "::slayer start"
                        )
                    ),
                    0L
                );

            LocalLabSlayerRuntime.StatusSnapshot
                active=
                    runtime.status(
                        "opensrc"
                    );

            require(
                started.result.logText.contains(
                    "START_ASSIGNED"
                )&&
                active.active()&&
                active.selectedMode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM&&
                active.task.objective.progress==0L&&
                active.task.objective.goal==
                    LocalLabSlayerRuntime
                        .OBJECTIVE_GOAL,
                "Slayer start did not create canonical Monster Hunter task"
            );

            SlayerTaskService.TaskId taskId=
                active.task.taskId;

            Captured duplicateStart=
                capture(
                    commands,
                    new String[]{"slayer","start"},
                    0L
                );

            require(
                duplicateStart.result.logText.contains(
                    "START_EXISTING"
                )&&
                runtime.status(
                    "opensrc"
                ).task.taskId.equals(
                    taskId
                ),
                "duplicate Slayer start created a second task"
            );

            LocalLabSlayerRuntime.KillCreditResult
                wrongDefinition=
                    runtime.recordMonsterSpawnerKill(
                        "opensrc",
                        2,
                        0L
                    );

            require(
                !wrongDefinition.eligibleDefinition&&
                !wrongDefinition.progressed&&
                runtime.status(
                    "opensrc"
                ).task.objective.progress==0L,
                "wrong Monster Spawner definition progressed Slayer"
            );

            LocalLabSlayerRuntime.KillCreditResult
                noTask=
                    runtime.recordMonsterSpawnerKill(
                        "observer",
                        LocalLabSlayerRuntime
                            .TARGET_DEFINITION_ID,
                        0L
                    );

            require(
                noTask.eligibleDefinition&&
                !noTask.activeTask&&
                !noTask.progressed&&
                runtime.status(
                    "observer"
                ).task==null,
                "player without Slayer task mutated"
            );

            LocalLabSlayerRuntime.KillCreditResult
                kill=
                    runtime.recordMonsterSpawnerKill(
                        "opensrc",
                        LocalLabSlayerRuntime
                            .TARGET_DEFINITION_ID,
                        1L
                    );

            require(
                kill.eligibleDefinition&&
                kill.activeTask&&
                kill.progressed&&
                kill.completedNow&&
                kill.status.complete()&&
                kill.status.task.objective.progress==1L&&
                kill.status.task.objective.goal==1L,
                "validated Monster Spawner kill did not complete Slayer task"
            );

            LocalLabSlayerRuntime.KillCreditResult
                duplicateKill=
                    runtime.recordMonsterSpawnerKill(
                        "opensrc",
                        LocalLabSlayerRuntime
                            .TARGET_DEFINITION_ID,
                        2L
                    );

            require(
                duplicateKill.eligibleDefinition&&
                !duplicateKill.activeTask&&
                !duplicateKill.progressed&&
                runtime.status(
                    "opensrc"
                ).task.objective.progress==1L,
                "post-completion kill duplicated Slayer progress"
            );

            Captured completed=
                capture(
                    commands,
                    new String[]{"slayer","status"},
                    2L
                );

            require(
                completed.result.logText.contains(
                    "state=COMPLETED"
                )&&
                completed.result.logText.contains(
                    "progress=1/1"
                ),
                "completed Slayer status feedback"
            );

            Captured completedRestart=
                capture(
                    commands,
                    new String[]{"slayer","start"},
                    2L
                );

            require(
                completedRestart.result.logText.contains(
                    "START_EXISTING"
                )&&
                runtime.status(
                    "opensrc"
                ).task.taskId.equals(
                    taskId
                ),
                "completed one-shot G4.1 task was silently reset"
            );

            Captured malformed=
                capture(
                    commands,
                    new String[]{
                        "slayer",
                        "start",
                        "junk"
                    },
                    2L
                );

            require(
                malformed.result.logText.contains(
                    "REJECTED_SYNTAX"
                )&&
                runtime.status(
                    "opensrc"
                ).task.objective.progress==1L,
                "invalid Slayer command mutated task"
            );

            Captured observerStatus=
                capture(
                    observerCommands,
                    new String[]{"slayer"},
                    2L
                );

            require(
                observerStatus.result.logText.contains(
                    "task=NONE"
                ),
                "Slayer state leaked between players"
            );

            require(
                LocalLabSlayerRuntime
                    .TARGET_DEFINITION_ID==
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                "G4.1 target drifted from certified Monster Spawner definition"
            );

            System.out.println(
                "G4_BLOOD_SLAYER_PVM_PASS "+
                "worldOwnedRuntime=true "+
                "monsterHunterMode=true "+
                "commandAdapter=true "+
                "exactC2S103Bridge=true "+
                "serverMessage253=true "+
                "definition1Target=true "+
                "validatedKillOnly=true "+
                "targetMismatchNoop=true "+
                "noTaskNoop=true "+
                "completionExactlyOnce=true "+
                "playerIsolation=true "+
                "pointsClaim=false "+
                "rewardClaim=false "+
                "persistenceClaim=false "+
                "native54100WidgetClaim=false "+
                "originalSpawnpkTaskPolicyClaim=false "+
                "authority="+
                LocalLabSlayerRuntime.AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );
            if(observer.registered())
                world.unregisterPlayer(
                    observer,
                    observer.generation()
                );
            world.close();
        }
    }

    private static Captured capture(
        LocalSlayerCommandHandler handler,
        String[] tokens,
        long worldTick
    )throws Exception{
        LocalSlayerCommandHandler.Result result=
            handler.handle(
                tokens,
                worldTick
            );

        require(
            result!=null,
            "Slayer command was not handled"
        );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        new SocialChatPresentationPublisher(
            writer
        ).serverMessage(
            result.clientMessage
        );

        byte[] frame=out.toByteArray();

        require(
            frame.length>=3,
            "Slayer command produced no S2C253 frame"
        );

        IsaacCipher cipher=
            new IsaacCipher(
                SEED.clone()
            );

        int opcode=
            ((frame[0]&255)-
                cipher.nextInt())&
                255;

        require(
            opcode==253,
            "Slayer feedback opcode="+
                opcode
        );

        return new Captured(
            result,
            frame
        );
    }

    private static final class Captured {
        final LocalSlayerCommandHandler.Result result;
        final byte[] frame;

        Captured(
            LocalSlayerCommandHandler.Result result,
            byte[] frame
        ){
            this.result=result;
            this.frame=frame;
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

    private LocalLabSlayerRuntimeTest(){}
}

package spk.local;

import java.io.ByteArrayOutputStream;

public final class G4BloodSlayerNativeUiIntegrationTest {
    private static final int[] SEED={1,2,3,4};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"opensrc");

        try{
            LocalBloodSlayerUiHandler handler=
                new LocalBloodSlayerUiHandler(
                    player,
                    world.localLabSlayer()
                );

            ByteArrayOutputStream rootBytes=new ByteArrayOutputStream();
            ServerPacketWriter rootWriter=writer(rootBytes);
            BloodSlayerPresentation.open(rootWriter);

            byte[] root=rootBytes.toByteArray();
            require(root.length==3,"Blood Slayer root frame length");
            IsaacCipher rootCipher=new IsaacCipher(SEED.clone());
            int rootOpcode=((root[0]&255)-rootCipher.nextInt())&255;
            require(
                rootOpcode==97&&
                (root[1]&255)==0xd3&&
                (root[2]&255)==0x54,
                "exact Blood Slayer root 54100"
            );

            ByteArrayOutputStream feedback=new ByteArrayOutputStream();
            ServerPacketWriter packets=writer(feedback);

            LocalBloodSlayerUiHandler.Result unsupported=
                handler.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation.BOSS_HUNTER_WIDGET
                    ),
                    0L,
                    packets
                );
            require(
                unsupported.status==
                    LocalBloodSlayerUiHandler.Status.UNSUPPORTED_MODE&&
                world.localLabSlayer().status("opensrc").selectedMode==null&&
                world.localLabSlayer().status("opensrc").task==null,
                "unsupported exact mode mutated LocalLab Slayer"
            );

            LocalBloodSlayerUiHandler.Result prematureTask=
                handler.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation.GET_TASK_WIDGET
                    ),
                    0L,
                    packets
                );
            require(
                prematureTask.status==
                    LocalBloodSlayerUiHandler.Status.NO_SUPPORTED_MODE_SELECTED&&
                world.localLabSlayer().status("opensrc").task==null,
                "Get Task without supported mode mutated state"
            );

            BloodSlayerPresentation.Input monsterInput=
                BloodSlayerPresentation.resolveWidget(
                    BloodSlayerPresentation.MONSTER_HUNTER_WIDGET
                );
            require(
                monsterInput!=null&&
                monsterInput.kind==BloodSlayerPresentation.InputKind.SELECT_MODE&&
                monsterInput.mode==BloodSlayerModeService.Mode.MONSTER_HUNTER_PVM,
                "exact widget 54109 routing"
            );

            LocalBloodSlayerUiHandler.Result selected=
                handler.handle(monsterInput,0L,packets);
            LocalLabSlayerRuntime.StatusSnapshot selectedStatus=
                world.localLabSlayer().status("opensrc");
            require(
                selected.status==LocalBloodSlayerUiHandler.Status.MODE_SELECTED&&
                selectedStatus.selectedMode==
                    BloodSlayerModeService.Mode.MONSTER_HUNTER_PVM&&
                selectedStatus.task==null,
                "exact Monster Hunter selection did not remain task-free"
            );

            LocalBloodSlayerUiHandler.Result assigned=
                handler.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation.GET_TASK_WIDGET
                    ),
                    0L,
                    packets
                );
            require(
                assigned.status==LocalBloodSlayerUiHandler.Status.TASK_ASSIGNED&&
                assigned.taskStatus.active()&&
                assigned.taskStatus.task.objective.progress==0L&&
                assigned.taskStatus.task.objective.goal==1L,
                "exact Get Task did not assign G4.1 task"
            );

            SlayerTaskService.TaskId taskId=assigned.taskStatus.task.taskId;
            LocalBloodSlayerUiHandler.Result duplicate=
                handler.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation.GET_TASK_WIDGET
                    ),
                    0L,
                    packets
                );
            require(
                duplicate.status==LocalBloodSlayerUiHandler.Status.TASK_EXISTING&&
                duplicate.taskStatus.task.taskId.equals(taskId),
                "repeated exact Get Task duplicated task"
            );

            LocalLabSlayerRuntime.KillCreditResult kill=
                world.localLabSlayer().recordMonsterSpawnerKill(
                    "opensrc",
                    LocalLabSlayerRuntime.TARGET_DEFINITION_ID,
                    1L
                );
            require(
                kill.completedNow&&kill.status.complete(),
                "native-UI task lost canonical PvM compatibility"
            );

            require(
                BloodSlayerPresentation.WIDGET_ACTION_OPCODE==185,
                "Blood Slayer widget opcode drift"
            );
            requireFeedbackFrames(feedback.toByteArray(),4);

            System.out.println(
                "G4_BLOOD_SLAYER_NATIVE_UI_PASS "+
                "root54100=true "+
                "monsterHunter54109=true "+
                "getTask54113=true "+
                "c2s185=true "+
                "taskAssignment=true "+
                "canonicalPvmCompletionCompatible=true "+
                "unsupportedModesFailClosed=true "+
                "pointsPublished=false "+
                "rewardClaim=false "+
                "persistenceClaim=false "+
                "commandOpenOriginalEntrypointClaim=false "+
                "presentationAuthority="+
                BloodSlayerPresentation.PRESENTATION_AUTHORITY+
                " gameplayAuthority="+LocalLabSlayerRuntime.AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,player.generation());
            world.close();
        }
    }

    private static ServerPacketWriter writer(ByteArrayOutputStream out){
        return new ServerPacketWriter(out,new IsaacCipher(SEED.clone()));
    }

    private static void requireFeedbackFrames(byte[] bytes,int expectedFrames){
        int offset=0;
        IsaacCipher cipher=new IsaacCipher(SEED.clone());

        for(int i=0;i<expectedFrames;i++){
            require(offset+2<=bytes.length,"missing feedback frame "+i);
            int opcode=((bytes[offset]&255)-cipher.nextInt())&255;
            require(opcode==253,"feedback opcode "+opcode+" frame="+i);
            int len=bytes[offset+1]&255;
            offset+=2+len;
            require(offset<=bytes.length,"truncated feedback frame "+i);
        }
        require(offset==bytes.length,"unexpected feedback bytes");
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private G4BloodSlayerNativeUiIntegrationTest(){}
}

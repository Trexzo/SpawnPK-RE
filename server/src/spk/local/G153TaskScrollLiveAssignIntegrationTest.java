package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class G153TaskScrollLiveAssignIntegrationTest {
    private static final String PLAYER=
        "task-scroll-live";
    private static final int[] SEED=
        {121,122,123,124};

    public static void main(String[] args)throws Exception{
        boolean emptyBeforeAssign=false;
        boolean explicitAssign=false;
        boolean populatedRoot=false;
        boolean semanticInfo=false;
        boolean progress0=false;
        boolean progress2=false;
        boolean complete3=false;
        boolean rewardsEmpty=false;
        boolean repeatedOpenStable=false;
        boolean inputRouter=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                PLAYER
            );

        try{
            LocalLabTaskScrollRuntime runtime=
                world.localLabTaskScrolls();

            final TaskScrollService.Snapshot[]
                lastRendered={null};
            final boolean[] lastAssignedRequest=
                {false};

            LocalCommandDispatcher.SessionBridge bridge=
                new LocalCommandDispatcher.SessionBridge(){
                    @Override public SceneUpdatePublisher scenePublisher(){
                        return null;
                    }

                    @Override public void replaceScenePublisher(
                        SceneUpdatePublisher replacement
                    ){}

                    @Override public void saveAccount(
                        String tag,
                        String reason
                    ){}

                    @Override public void openDevPanel(
                        ServerPacketWriter serverPackets
                    ){}

                    @Override public boolean openTaskScroll(
                        boolean assignIfAbsent,
                        long worldTick,
                        ServerPacketWriter serverPackets
                    )throws java.io.IOException{
                        lastAssignedRequest[0]=
                            assignIfAbsent;

                        TaskScrollService.Snapshot task=
                            assignIfAbsent
                                ?runtime.assign(
                                    PLAYER,
                                    worldTick
                                ).task
                                :runtime.active(
                                    PLAYER
                                );

                        lastRendered[0]=task;

                        if(task==null)
                            TaskScrollPresentation.openEmpty(
                                serverPackets
                            );
                        else
                            LocalLabTaskScrollPresentation.open(
                                serverPackets,
                                task
                            );

                        return true;
                    }

                    @Override public void applyPetDialog(
                        LocalPetInventoryDialogHandler.Result result,
                        String tag
                    ){}
                };

            ByteArrayOutputStream emptyWire=
                new ByteArrayOutputStream();

            boolean emptyHandled=
                LocalCommandDispatcher
                    .dispatchTaskScrollCommand(
                        new String[]{"taskscroll"},
                        10L,
                        bridge,
                        new ServerPacketWriter(
                            emptyWire,
                            new IsaacCipher(
                                SEED.clone()
                            )
                        ),
                        "[g153] "
                    );

            emptyBeforeAssign=
                emptyHandled&&
                !lastAssignedRequest[0]&&
                lastRendered[0]==null&&
                runtime.active(
                    PLAYER
                )==null&&
                assertEmptyProjection(
                    emptyWire.toByteArray()
                );

            ByteArrayOutputStream assignedWire=
                new ByteArrayOutputStream();

            boolean assignedHandled=
                LocalCommandDispatcher
                    .dispatchTaskScrollCommand(
                        new String[]{
                            "taskscroll",
                            "assign"
                        },
                        11L,
                        bridge,
                        new ServerPacketWriter(
                            assignedWire,
                            new IsaacCipher(
                                SEED.clone()
                            )
                        ),
                        "[g153] "
                    );

            TaskScrollService.Snapshot assigned=
                runtime.active(
                    PLAYER
                );

            explicitAssign=
                assignedHandled&&
                lastAssignedRequest[0]&&
                assigned!=null&&
                lastRendered[0]!=null&&
                assigned.taskScrollId.equals(
                    lastRendered[0].taskScrollId
                )&&
                runtime.assignmentCount()==1;

            ProjectionCheck zeroProjection=
                assertAssignedProjection(
                    assignedWire.toByteArray(),
                    assigned,
                    "0/3"
                );

            populatedRoot=
                zeroProjection.root;
            semanticInfo=
                zeroProjection.info;
            progress0=
                zeroProjection.progress;
            rewardsEmpty=
                zeroProjection.rewards;

            TaskScrollService.TaskScrollId stableId=
                assigned.taskScrollId;

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        3087,
                        3495,
                        0
                    ),
                    12L
                );
            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        3087,
                        3495,
                        0
                    ),
                    13L
                );

            ByteArrayOutputStream twoWire=
                new ByteArrayOutputStream();

            boolean twoHandled=
                LocalCommandDispatcher
                    .dispatchTaskScrollCommand(
                        new String[]{"tscroll"},
                        14L,
                        bridge,
                        new ServerPacketWriter(
                            twoWire,
                            new IsaacCipher(
                                SEED.clone()
                            )
                        ),
                        "[g153] "
                    );

            TaskScrollService.Snapshot two=
                runtime.active(
                    PLAYER
                );

            ProjectionCheck twoProjection=
                assertAssignedProjection(
                    twoWire.toByteArray(),
                    two,
                    "2/3"
                );

            progress2=
                twoHandled&&
                two.objective.progress==2L&&
                twoProjection.progress;

            repeatedOpenStable=
                !lastAssignedRequest[0]&&
                runtime.assignmentCount()==1&&
                stableId.equals(
                    two.taskScrollId
                )&&
                stableId.equals(
                    lastRendered[0].taskScrollId
                );

            LocalLabMonsterSpawnerProvisioning
                .creditTerminalProgression(
                    world,
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning
                        .NPC_DEFINITION_ID,
                    new Tile(
                        3087,
                        3495,
                        0
                    ),
                    15L
                );

            ByteArrayOutputStream completeWire=
                new ByteArrayOutputStream();

            LocalCommandDispatcher
                .dispatchTaskScrollCommand(
                    new String[]{"taskscroll"},
                    16L,
                    bridge,
                    new ServerPacketWriter(
                        completeWire,
                        new IsaacCipher(
                            SEED.clone()
                        )
                    ),
                    "[g153] "
                );

            TaskScrollService.Snapshot complete=
                runtime.active(
                    PLAYER
                );

            ProjectionCheck completeProjection=
                assertAssignedProjection(
                    completeWire.toByteArray(),
                    complete,
                    "Complete (3/3)"
                );

            complete3=
                complete.objective.progress==3L&&
                complete.objective.complete&&
                complete.state==
                    TaskScrollService.State
                        .COMPLETE_UNCLAIMED&&
                completeProjection.progress;

            inputRouter=
                hasInputRouter();

            require(
                emptyBeforeAssign&&
                explicitAssign&&
                populatedRoot&&
                semanticInfo&&
                progress0&&
                progress2&&
                complete3&&
                rewardsEmpty&&
                repeatedOpenStable&&
                !inputRouter,
                "G15.3 acceptance"
            );

            System.out.println(
                "G153_TASK_SCROLL_LIVE_ASSIGN_PASS"+
                " emptyBeforeAssign="+
                    emptyBeforeAssign+
                " explicitAssign="+explicitAssign+
                " populatedRoot="+populatedRoot+
                " semanticInfo="+semanticInfo+
                " progress0="+progress0+
                " progress2="+progress2+
                " complete3="+complete3+
                " rewardsEmpty="+rewardsEmpty+
                " repeatedOpenStable="+
                    repeatedOpenStable+
                " inputRouter="+inputRouter+
                " rewardPolicyClaim=false"+
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

    private static ProjectionCheck assertAssignedProjection(
        byte[] wire,
        TaskScrollService.Snapshot task,
        String progressText
    )throws Exception{
        require(
            task!=null,
            "assigned projection task"
        );

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        int offset=0;

        offset=
            assertFixed(
                wire,
                offset,
                decode,
                97,
                BootstrapPackets.interface97(
                    TaskScrollPresentation.ROOT
                )
            );
        boolean root=true;

        offset=
            assertVarShort(
                wire,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    TaskScrollPresentation
                        .TITLE_WIDGET,
                    LocalLabTaskScrollPresentation
                        .TITLE
                )
            );

        boolean info=true;

        for(int i=0;
            i<TaskScrollPresentation.INFO_ROWS;
            i++){
            String text=
                i<task.definition
                    .informationLines.size()
                    ?task.definition
                        .informationLines.get(i)
                    :"";

            offset=
                assertVarShort(
                    wire,
                    offset,
                    decode,
                    126,
                    BootstrapPackets.widgetText126(
                        TaskScrollPresentation
                            .informationWidget(i),
                        text
                    )
                );
        }

        offset=
            assertVarShort(
                wire,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    TaskScrollPresentation
                        .PROGRESS_TEXT_WIDGET,
                    progressText
                )
            );

        boolean progress=true;

        int[] ids=
            new int[
                TaskScrollPresentation.REWARD_SLOTS
            ];
        int[] qty=
            new int[
                TaskScrollPresentation.REWARD_SLOTS
            ];

        Arrays.fill(
            ids,
            -1
        );

        offset=
            assertVarShort(
                wire,
                offset,
                decode,
                53,
                BootstrapPackets.itemContainer53(
                    TaskScrollPresentation
                        .REWARD_GRID_WIDGET,
                    ids,
                    qty
                )
            );

        boolean rewards=
            offset==wire.length;

        return new ProjectionCheck(
            root,
            info,
            progress,
            rewards
        );
    }

    private static boolean assertEmptyProjection(
        byte[] wire
    )throws Exception{
        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        int offset=0;

        offset=
            assertFixed(
                wire,
                offset,
                decode,
                97,
                BootstrapPackets.interface97(
                    TaskScrollPresentation.ROOT
                )
            );

        offset=
            assertVarShort(
                wire,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    TaskScrollPresentation
                        .TITLE_WIDGET,
                    "No Task Scroll assigned"
                )
            );

        for(int i=0;
            i<TaskScrollPresentation.INFO_ROWS;
            i++)
            offset=
                assertVarShort(
                    wire,
                    offset,
                    decode,
                    126,
                    BootstrapPackets.widgetText126(
                        TaskScrollPresentation
                            .informationWidget(i),
                        ""
                    )
                );

        offset=
            assertVarShort(
                wire,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    TaskScrollPresentation
                        .PROGRESS_TEXT_WIDGET,
                    ""
                )
            );

        int[] ids=
            new int[
                TaskScrollPresentation.REWARD_SLOTS
            ];
        int[] qty=
            new int[
                TaskScrollPresentation.REWARD_SLOTS
            ];

        Arrays.fill(
            ids,
            -1
        );

        offset=
            assertVarShort(
                wire,
                offset,
                decode,
                53,
                BootstrapPackets.itemContainer53(
                    TaskScrollPresentation
                        .REWARD_GRID_WIDGET,
                    ids,
                    qty
                )
            );

        return offset==wire.length;
    }

    private static boolean hasInputRouter(){
        for(java.lang.reflect.Method method:
                TaskScrollPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("dispatchinput"))
                return true;
        }

        return false;
    }

    private static int assertFixed(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        int opcode,
        byte[] body
    ){
        int actualOpcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(actualOpcode!=opcode)
            throw new AssertionError(
                "fixed opcode expected="+
                opcode+
                " actual="+
                actualOpcode
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+body.length
            );

        if(!Arrays.equals(
                body,
                actual))
            throw new AssertionError(
                "fixed body opcode="+opcode
            );

        return offset+body.length;
    }

    private static int assertVarShort(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        int opcode,
        byte[] body
    ){
        int actualOpcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(actualOpcode!=opcode)
            throw new AssertionError(
                "var-short opcode expected="+
                opcode+
                " actual="+
                actualOpcode
            );

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        if(length!=body.length)
            throw new AssertionError(
                "var-short length opcode="+
                opcode+
                " expected="+
                body.length+
                " actual="+
                length
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+length
            );

        if(!Arrays.equals(
                body,
                actual))
            throw new AssertionError(
                "var-short body opcode="+
                opcode
            );

        return offset+length;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class ProjectionCheck {
        final boolean root;
        final boolean info;
        final boolean progress;
        final boolean rewards;

        ProjectionCheck(
            boolean root,
            boolean info,
            boolean progress,
            boolean rewards
        ){
            this.root=root;
            this.info=info;
            this.progress=progress;
            this.rewards=rewards;
        }
    }

    private G153TaskScrollLiveAssignIntegrationTest(){}
}

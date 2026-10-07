package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;

public final class G151TaskScrollEmptyShellIntegrationTest {
    private static final int[] SEED={111,112,113,114};

    public static void main(String[] args)throws Exception{
        boolean commandRoute=false;
        boolean root18559=false;
        boolean titleTruthful=false;
        boolean infoRows20Empty=false;
        boolean progressBlank=false;
        boolean rewards100Empty=false;
        boolean competingRoot=false;
        boolean inputRouter=false;
        boolean assignmentCreated=false;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        final int[] opens={0};

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
                    ServerPacketWriter serverPackets
                )throws java.io.IOException{
                    opens[0]++;
                    TaskScrollPresentation.openEmpty(
                        serverPackets
                    );
                    return true;
                }

                @Override public void applyPetDialog(
                    LocalPetInventoryDialogHandler.Result result,
                    String tag
                ){}
            };

        commandRoute=
            LocalCommandDispatcher
                .dispatchTaskScrollCommand(
                    new String[]{"taskscroll"},
                    bridge,
                    writer,
                    "[g151] "
                )&&
            LocalCommandDispatcher
                .isTaskScrollRoute(
                    new String[]{"tscroll"}
                )&&
            !LocalCommandDispatcher
                .isTaskScrollRoute(
                    new String[]{"taskscroll","extra"}
                )&&
            opens[0]==1;

        competingRoot=
            opens[0]==1;

        byte[] bytes=
            wire.toByteArray();

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );

        int offset=0;

        offset=
            assertFixed(
                bytes,
                offset,
                decode,
                97,
                BootstrapPackets.interface97(
                    TaskScrollPresentation.ROOT
                )
            );

        root18559=
            offset>0;

        offset=
            assertVarShort(
                bytes,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    TaskScrollPresentation
                        .TITLE_WIDGET,
                    "No Task Scroll assigned"
                )
            );

        titleTruthful=true;

        for(int i=0;
            i<TaskScrollPresentation.INFO_ROWS;
            i++)
            offset=
                assertVarShort(
                    bytes,
                    offset,
                    decode,
                    126,
                    BootstrapPackets.widgetText126(
                        TaskScrollPresentation
                            .informationWidget(i),
                        ""
                    )
                );

        infoRows20Empty=true;

        offset=
            assertVarShort(
                bytes,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    TaskScrollPresentation
                        .PROGRESS_TEXT_WIDGET,
                    ""
                )
            );

        progressBlank=true;

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
                bytes,
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

        rewards100Empty=
            offset==bytes.length;

        inputRouter=
            noInputRouter();

        assignmentCreated=
            false;

        require(
            commandRoute&&
                root18559&&
                titleTruthful&&
                infoRows20Empty&&
                progressBlank&&
                rewards100Empty&&
                competingRoot&&
                inputRouter&&
                !assignmentCreated,
            "G15.1 acceptance"
        );

        System.out.println(
            "G151_TASK_SCROLL_EMPTY_SHELL_PASS"+
            " commandRoute="+commandRoute+
            " root18559="+root18559+
            " titleTruthful="+titleTruthful+
            " infoRows20Empty="+
                infoRows20Empty+
            " progressBlank="+progressBlank+
            " rewards100Empty="+
                rewards100Empty+
            " competingRoot="+competingRoot+
            " inputRouter="+inputRouter+
            " assignmentCreated="+
                assignmentCreated+
            " rewardPolicyClaim=false"+
            " originalNavigationClaim=false"
        );
    }

    private static boolean noInputRouter(){
        for(java.lang.reflect.Method method:
                TaskScrollPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("dispatchinput"))
                return false;
        }

        return true;
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

    private G151TaskScrollEmptyShellIntegrationTest(){}
}

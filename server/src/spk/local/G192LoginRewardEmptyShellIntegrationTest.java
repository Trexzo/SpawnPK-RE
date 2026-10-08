package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class G192LoginRewardEmptyShellIntegrationTest {
    private static final int[] SEED={191,192,193,194};

    public static void main(String[] args)throws Exception{
        boolean commandRoute=false;
        boolean root50600=false;
        boolean container50615=false;
        boolean slots20Empty=false;
        boolean competingRoot=false;
        boolean indexPublished=false;
        boolean inputRouter=false;
        boolean gameplayState=false;

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

                @Override public boolean openLoginRewards(
                    ServerPacketWriter serverPackets
                )throws java.io.IOException{
                    opens[0]++;
                    LoginRewardPresentation.openEmpty(
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
            LocalCommandDispatcher.isLoginRewardRoute(
                new String[]{"loginrewards"}
            )&&
            LocalCommandDispatcher.isLoginRewardRoute(
                new String[]{"loginreward"}
            )&&
            !LocalCommandDispatcher.isLoginRewardRoute(
                new String[]{"loginrewards","extra"}
            );

        boolean handled=
            LocalCommandDispatcher
                .dispatchLoginRewardCommand(
                    new String[]{"loginrewards"},
                    bridge,
                    writer,
                    "[g192] "
                );

        commandRoute=
            commandRoute&&handled&&
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
                    LoginRewardPresentation.ROOT
                )
            );

        root50600=
            LoginRewardPresentation.ROOT==50600;

        int[] ids=
            new int[
                LoginRewardPresentation.SLOT_COUNT
            ];
        int[] qty=
            new int[
                LoginRewardPresentation.SLOT_COUNT
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
                    LoginRewardPresentation
                        .ITEM_CONTAINER_WIDGET,
                    ids,
                    qty
                )
            );

        container50615=
            LoginRewardPresentation
                .ITEM_CONTAINER_WIDGET==50615;

        slots20Empty=
            LoginRewardPresentation
                .SLOT_COUNT==20&&
            offset==bytes.length;

        /*
         * The exact wire ends after root + empty container. No S2C126
         * LOGIN_REWARD_IDX is synthesized because its server semantic policy
         * is unknown.
         */
        indexPublished=false;

        inputRouter=
            hasInputRouter();

        gameplayState=
            hasGameplayState();

        require(
            commandRoute&&
            root50600&&
            container50615&&
            slots20Empty&&
            competingRoot&&
            !indexPublished&&
            !inputRouter&&
            !gameplayState,
            "G19.2 acceptance"
        );

        System.out.println(
            "G192_LOGIN_REWARD_EMPTY_SHELL_PASS"+
            " commandRoute="+commandRoute+
            " root50600="+root50600+
            " container50615="+container50615+
            " slots20Empty="+slots20Empty+
            " competingRoot="+competingRoot+
            " indexPublished="+indexPublished+
            " inputRouter="+inputRouter+
            " gameplayState="+gameplayState+
            " rewardPolicyClaim=false"+
            " cadenceClaim=false"+
            " streakClaim=false"+
            " claimTransportClaim=false"+
            " settlementClaim=false"+
            " persistenceClaim=false"+
            " originalNavigationClaim=false"
        );
    }

    private static boolean hasInputRouter(){
        for(java.lang.reflect.Method method:
                LoginRewardPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("dispatchinput"))
                return true;
        }

        return false;
    }

    private static boolean hasGameplayState(){
        for(java.lang.reflect.Field field:
                LoginRewardPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

            if(name.contains("claimed")||
               name.contains("eligible")||
               name.contains("streak")||
               name.contains("rewardstate")||
               name.contains("lastclaim"))
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
                "var-short length expected="+
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
                "var-short body opcode="+opcode
            );

        return offset+length;
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

    private G192LoginRewardEmptyShellIntegrationTest(){}
}

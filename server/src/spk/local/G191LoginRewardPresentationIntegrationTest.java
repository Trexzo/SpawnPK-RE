package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class G191LoginRewardPresentationIntegrationTest {
    private static final int[] SEED={181,182,183,184};

    public static void main(String[] args)throws Exception{
        boolean root50600=false;
        boolean container50615=false;
        boolean slots20=false;
        boolean emptyProjection=false;
        boolean loginRewardIdxExact=false;
        boolean target1=false;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        LoginRewardPresentation.openEmpty(
            writer
        );

        ApplicationControl126Service
            .loginRewardContainerIndex(
                writer,
                7
            );

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
        int[] quantities=
            new int[
                LoginRewardPresentation.SLOT_COUNT
            ];

        Arrays.fill(
            ids,
            -1
        );

        byte[] container=
            BootstrapPackets.itemContainer53(
                LoginRewardPresentation
                    .ITEM_CONTAINER_WIDGET,
                ids,
                quantities
            );

        offset=
            assertVarShort(
                bytes,
                offset,
                decode,
                53,
                container
            );

        container50615=
            LoginRewardPresentation
                .ITEM_CONTAINER_WIDGET==50615;

        slots20=
            LoginRewardPresentation
                .SLOT_COUNT==20;

        emptyProjection=
            container50615&&
            slots20;

        offset=
            assertVarShort(
                bytes,
                offset,
                decode,
                126,
                BootstrapPackets.widgetText126(
                    1,
                    "LOGIN_REWARD_IDX 7"
                )
            );

        loginRewardIdxExact=true;
        target1=
            offset==bytes.length;

        boolean badSlotsRejected=false;

        try{
            LoginRewardPresentation
                .publishContainer(
                    writer,
                    new int[19],
                    new int[19]
                );
        }catch(IllegalArgumentException expected){
            badSlotsRejected=true;
        }

        require(
            root50600&&
                container50615&&
                slots20&&
                emptyProjection&&
                loginRewardIdxExact&&
                target1&&
                badSlotsRejected,
            "G19.1 acceptance"
        );

        System.out.println(
            "G191_LOGIN_REWARD_PRESENTATION_PASS"+
            " root50600="+root50600+
            " container50615="+container50615+
            " slots20="+slots20+
            " emptyProjection="+emptyProjection+
            " loginRewardIdxExact="+
                loginRewardIdxExact+
            " target1="+target1+
            " rewardPolicyClaim=false"+
            " cadenceClaim=false"+
            " streakClaim=false"+
            " claimTransportClaim=false"+
            " settlementClaim=false"+
            " persistenceClaim=false"
        );
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
                "fixed body opcode="+
                opcode
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
            throw new AssertionError(
                message
            );
    }

    private G191LoginRewardPresentationIntegrationTest(){}
}

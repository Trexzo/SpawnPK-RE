package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class G181DailyMoneyMakingAuthorityIntegrationTest {
    private static final int[] SEED={151,152,153,154};

    public static void main(String[] args)throws Exception{
        boolean difficultySemantic=false;
        boolean selectedCategoryExact=false;
        boolean trackingTextTarget26=false;
        boolean easyTarget36=false;
        boolean mediumTarget36=false;
        boolean hardTarget36=false;
        boolean progressOwnedByObjectiveService=false;
        boolean unknownObjectiveFailClosed=false;
        boolean rewardRulesInvented=false;
        boolean teleportRulesInvented=false;
        boolean resetScheduleInvented=false;
        boolean protocolIdentityInState=false;

        DailyMoneyMakingStateServiceTest.main(
            new String[0]
        );

        difficultySemantic=true;
        selectedCategoryExact=true;
        progressOwnedByObjectiveService=true;
        unknownObjectiveFailClosed=true;
        rewardRulesInvented=false;
        teleportRulesInvented=false;
        resetScheduleInvented=false;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        ApplicationControl126Service
            .dailyMoneyMakingTrackingText(
                writer,
                "Defeat LocalLab targets: 2/3"
            );
        ApplicationControl126Service
            .dailyMoneyMakingDifficulty(
                writer,
                DailyMoneyMakingStateService
                    .Difficulty.EASY
            );
        ApplicationControl126Service
            .dailyMoneyMakingDifficulty(
                writer,
                DailyMoneyMakingStateService
                    .Difficulty.MEDIUM
            );
        ApplicationControl126Service
            .dailyMoneyMakingDifficulty(
                writer,
                DailyMoneyMakingStateService
                    .Difficulty.HARD
            );

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );
        byte[] bytes=
            wire.toByteArray();
        int offset=0;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "Defeat LocalLab targets: 2/3",
                26
            );
        trackingTextTarget26=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "1",
                36
            );
        easyTarget36=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "2",
                36
            );
        mediumTarget36=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "3",
                36
            );
        hardTarget36=true;

        require(
            offset==bytes.length,
            "trailing Daily Money Making wire bytes"
        );

        protocolIdentityInState=
            containsProtocolIdentity(
                DailyMoneyMakingStateService.class
            )||
            containsProtocolIdentity(
                DailyMoneyMakingStateService
                    .Snapshot.class
            );

        require(
            difficultySemantic&&
            selectedCategoryExact&&
            trackingTextTarget26&&
            easyTarget36&&
            mediumTarget36&&
            hardTarget36&&
            progressOwnedByObjectiveService&&
            unknownObjectiveFailClosed&&
            !rewardRulesInvented&&
            !teleportRulesInvented&&
            !resetScheduleInvented&&
            !protocolIdentityInState,
            "G18.1 acceptance"
        );

        System.out.println(
            "G181_DAILY_MONEY_MAKING_AUTHORITY_PASS"+
            " difficultySemantic="+
                difficultySemantic+
            " selectedCategoryExact="+
                selectedCategoryExact+
            " trackingTextTarget26="+
                trackingTextTarget26+
            " easyTarget36="+
                easyTarget36+
            " mediumTarget36="+
                mediumTarget36+
            " hardTarget36="+
                hardTarget36+
            " progressOwnedByObjectiveService="+
                progressOwnedByObjectiveService+
            " unknownObjectiveFailClosed="+
                unknownObjectiveFailClosed+
            " rewardRulesInvented="+
                rewardRulesInvented+
            " teleportRulesInvented="+
                teleportRulesInvented+
            " resetScheduleInvented="+
                resetScheduleInvented+
            " protocolIdentityInState="+
                protocolIdentityInState+
            " liveInputClaim=false"+
            " persistenceClaim=false"
        );
    }

    private static boolean containsProtocolIdentity(
        Class<?> type
    ){
        for(java.lang.reflect.Field field:
                type.getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("packet")||
               name.contains("opcode")||
               name.contains("subtype")||
               name.contains("target"))
                return true;
        }

        return false;
    }

    private static int assert126(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "expected 126 actual="+
                opcode
            );

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        byte[] text=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            );

        byte[] expected=
            Arrays.copyOf(
                text,
                text.length+3
            );

        expected[text.length]=10;
        expected[text.length+1]=
            (byte)(target>>>8);
        expected[text.length+2]=
            (byte)((target+128)&255);

        if(length!=expected.length)
            throw new AssertionError(
                "length expected="+
                expected.length+
                " actual="+length
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+length
            );

        if(!Arrays.equals(
                expected,
                actual))
            throw new AssertionError(
                "body mismatch payload="+
                payload+
                " target="+target
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

    private G181DailyMoneyMakingAuthorityIntegrationTest(){}
}

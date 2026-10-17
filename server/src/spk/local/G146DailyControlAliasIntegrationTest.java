package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G146DailyControlAliasIntegrationTest {
    private static final int[] SEED={101,102,103,104};

    public static void main(String[] args)throws Exception{
        boolean clearTarget1=false;
        boolean buildTarget1=false;
        boolean clearLegacyBytes=false;
        boolean buildLegacyBytes=false;
        boolean semanticDailyApi=false;

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
            .dailyChallengesClear(
                writer
            );
        ApplicationControl126Service
            .dailyChallengesBuild(
                writer
            );

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );

        int offset=
            assert126(
                wire.toByteArray(),
                0,
                decode,
                "CLEAR_ACHIEVEMENT_TAB",
                1
            );

        clearTarget1=
            offset>0;
        clearLegacyBytes=true;

        int end=
            assert126(
                wire.toByteArray(),
                offset,
                decode,
                "BUILD_ACHIEVEMENT_TAB",
                1
            );

        buildTarget1=
            end==wire.size();
        buildLegacyBytes=true;

        semanticDailyApi=
            ApplicationControl126Service.class
                .getDeclaredMethod(
                    "dailyChallengesClear",
                    ServerPacketWriter.class
                )!=null&&
            ApplicationControl126Service.class
                .getDeclaredMethod(
                    "dailyChallengesBuild",
                    ServerPacketWriter.class
                )!=null;

        require(
            clearTarget1&&
            buildTarget1&&
            clearLegacyBytes&&
            buildLegacyBytes&&
            semanticDailyApi,
            "G14.6 acceptance"
        );

        System.out.println(
            "G146_DAILY_CONTROL_ALIAS_PASS"+
            " clearTarget1="+clearTarget1+
            " buildTarget1="+buildTarget1+
            " clearLegacyBytes="+clearLegacyBytes+
            " buildLegacyBytes="+buildLegacyBytes+
            " semanticDailyApi="+semanticDailyApi+
            " sequencingClaim=false"+
            " target56Claim=false"+
            " rewardPolicyClaim=false"+
            " resetPolicyClaim=false"
        );
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
                "expected 126 actual="+opcode
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
                "body mismatch"
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

    private G146DailyControlAliasIntegrationTest(){}
}

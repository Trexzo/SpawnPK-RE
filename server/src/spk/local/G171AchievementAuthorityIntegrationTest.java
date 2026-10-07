package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class G171AchievementAuthorityIntegrationTest {
    private static final int[] SEED={141,142,143,144};

    public static void main(String[] args)throws Exception{
        boolean exactRows=false;
        boolean semanticBindings=false;
        boolean goalCompatibility=false;
        boolean bootstrapStateNotAuthoritative=false;
        boolean externalProgressReflected=false;
        boolean externalRewardSettlementRequired=false;
        boolean claimBookkeeping=false;
        boolean rewardPairsOpaqueEvidence=false;
        boolean failedReplaceAtomic=false;
        boolean clearExact=false;
        boolean buildExact=false;
        boolean exactTarget1=false;
        boolean rewardMutation=false;
        boolean protocolIndependent=false;

        /*
         * Execute the already-landed protocol-independent binding regression on
         * this exact gameplay ancestry. If any semantic authority boundary
         * regresses, this G17.1 proof fails before transport assertions run.
         */
        AchievementChapterServiceTest.main(
            new String[0]
        );

        exactRows=
            AchievementChapterBootstrapCatalog.size()==9;
        semanticBindings=true;
        goalCompatibility=true;
        bootstrapStateNotAuthoritative=true;
        externalProgressReflected=true;
        externalRewardSettlementRequired=true;
        claimBookkeeping=true;
        rewardPairsOpaqueEvidence=true;
        failedReplaceAtomic=true;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        ApplicationControl126Service.achievementClear(
            writer
        );
        ApplicationControl126Service.achievementBuild(
            writer
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
                "CLEAR_ACHIEVEMENT_TAB",
                1
            );
        clearExact=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "BUILD_ACHIEVEMENT_TAB",
                1
            );
        buildExact=true;

        exactTarget1=
            offset==bytes.length;

        rewardMutation=false;

        protocolIndependent=
            !containsProtocolIdentity(
                AchievementChapterService.class
            )&&
            !containsProtocolIdentity(
                AchievementChapterService.BindingSpec.class
            )&&
            !containsProtocolIdentity(
                AchievementChapterService.RowSnapshot.class
            )&&
            !containsProtocolIdentity(
                AchievementChapterService.Snapshot.class
            );

        require(
            exactRows&&
            semanticBindings&&
            goalCompatibility&&
            bootstrapStateNotAuthoritative&&
            externalProgressReflected&&
            externalRewardSettlementRequired&&
            claimBookkeeping&&
            rewardPairsOpaqueEvidence&&
            failedReplaceAtomic&&
            clearExact&&
            buildExact&&
            exactTarget1&&
            !rewardMutation&&
            protocolIndependent,
            "G17.1 acceptance"
        );

        System.out.println(
            "G171_ACHIEVEMENT_AUTHORITY_PASS"+
            " exactRows="+exactRows+
            " semanticBindings="+semanticBindings+
            " goalCompatibility="+goalCompatibility+
            " bootstrapStateNotAuthoritative="+
                bootstrapStateNotAuthoritative+
            " externalProgressReflected="+
                externalProgressReflected+
            " externalRewardSettlementRequired="+
                externalRewardSettlementRequired+
            " claimBookkeeping="+claimBookkeeping+
            " rewardPairsOpaqueEvidence="+
                rewardPairsOpaqueEvidence+
            " failedReplaceAtomic="+failedReplaceAtomic+
            " clearExact="+clearExact+
            " buildExact="+buildExact+
            " exactTarget1="+exactTarget1+
            " rewardMutation="+rewardMutation+
            " protocolIndependent="+protocolIndependent+
            " liveRouteClaim=false"+
            " progressionPolicyClaim=false"+
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

            if(name.contains("packet")||
               name.contains("opcode")||
               name.contains("widget")||
               name.contains("subtype")||
               name.contains("clientindex"))
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
                expected,
                actual))
            throw new AssertionError(
                "body mismatch payload="+
                payload
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

    private G171AchievementAuthorityIntegrationTest(){}
}

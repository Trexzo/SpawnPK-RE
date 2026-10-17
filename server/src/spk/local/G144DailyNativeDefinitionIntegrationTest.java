package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G144DailyNativeDefinitionIntegrationTest {
    private static final String PLAYER="daily-native";
    private static final int[] SEED={81,82,83,84};

    public static void main(String[] args)throws Exception{
        boolean target55=false;
        boolean semicolonGrammar=false;
        boolean localKey=false;
        boolean description=false;
        boolean metadataPlaceholders=false;
        boolean progress0=false;
        boolean progress2=false;
        boolean exact126=false;

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
            LocalLabDailyChallengeRuntime daily=
                world.localLabDailyChallenges();

            DailyChallengeApplicationService
                .ChallengeSnapshot zero=
                    daily.status(
                        PLAYER
                    ).challenge;

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            LocalLabDailyChallengePresentation
                presentation=
                    new LocalLabDailyChallengePresentation(
                        writer
                    );

            presentation.publishDefinition(
                zero
            );

            daily.recordMonsterSpawnerFinalization(
                PLAYER,
                LocalLabMonsterSpawnerProvisioning
                    .NPC_DEFINITION_ID,
                1L
            );
            daily.recordMonsterSpawnerFinalization(
                PLAYER,
                LocalLabMonsterSpawnerProvisioning
                    .NPC_DEFINITION_ID,
                2L
            );

            DailyChallengeApplicationService
                .ChallengeSnapshot two=
                    daily.status(
                        PLAYER
                    ).challenge;

            presentation.publishDefinition(
                two
            );

            String prefix=
                "0;0;"+
                LocalLabDailyChallengeRuntime
                    .CHALLENGE_KEY+
                ";Kill 3 certified Monster Spawner PvM targets;";

            String expectedZero=
                prefix+"0;3";
            String expectedTwo=
                prefix+"2;3";

            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );

            int offset=
                assertPacket(
                    wire.toByteArray(),
                    0,
                    decode,
                    expectedZero,
                    55
                );

            offset=
                assertPacket(
                    wire.toByteArray(),
                    offset,
                    decode,
                    expectedTwo,
                    55
                );

            if(offset!=wire.size())
                throw new AssertionError(
                    "trailing target-55 bytes="+
                    (wire.size()-offset)
                );

            ApplicationControl126Command command=
                ApplicationControl126Command
                    .dailyChallengeDefinition(
                        0,
                        0,
                        LocalLabDailyChallengeRuntime
                            .CHALLENGE_KEY,
                        zero.description,
                        0,
                        3
                    );

            target55=
                command.target().key()==55&&
                command.authority()==
                    ApplicationControl126Command
                        .Authority
                        .EXACT_CURRENT_CLIENT;

            semicolonGrammar=
                expectedZero.equals(
                    command.payload()
                )&&
                count(
                    command.payload(),
                    ';'
                )==5;

            localKey=
                zero.challengeKey.equals(
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                )&&
                command.payload().contains(
                    ";"+
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY+
                    ";"
                );

            description=
                "Kill 3 certified Monster Spawner PvM targets"
                    .equals(
                        zero.description
                    )&&
                command.payload().contains(
                    ";"+
                    zero.description+
                    ";"
                );

            metadataPlaceholders=
                LocalLabDailyChallengePresentation
                    .METADATA_A==0&&
                LocalLabDailyChallengePresentation
                    .METADATA_B==0&&
                command.payload().startsWith(
                    "0;0;"
                );

            progress0=
                zero.current==0L&&
                zero.target==3L&&
                expectedZero.endsWith(
                    ";0;3"
                );

            progress2=
                two.current==2L&&
                two.target==3L&&
                expectedTwo.endsWith(
                    ";2;3"
                );

            exact126=
                offset==wire.size();

            expectInvalid(
                ()->ApplicationControl126Command
                    .dailyChallengeDefinition(
                        0,
                        0,
                        "bad;key",
                        "description",
                        0,
                        3
                    )
            );

            expectInvalid(
                ()->ApplicationControl126Command
                    .dailyChallengeDefinition(
                        0,
                        0,
                        "good-key",
                        "bad\ndescription",
                        0,
                        3
                    )
            );

            require(
                target55&&
                semicolonGrammar&&
                localKey&&
                description&&
                metadataPlaceholders&&
                progress0&&
                progress2&&
                exact126,
                "G14.4 acceptance"
            );

            System.out.println(
                "G144_DAILY_NATIVE_DEFINITION_PASS"+
                " target55="+target55+
                " semicolonGrammar="+
                    semicolonGrammar+
                " localKey="+localKey+
                " description="+description+
                " metadataPlaceholders=0,0"+
                " progress0="+progress0+
                " progress2="+progress2+
                " exact126="+exact126+
                " target56Claim=false"+
                " buildClearClaim=false"+
                " metadataMeaningClaim=false"+
                " rewardPolicyClaim=false"+
                " resetPolicyClaim=false"
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

    private static int assertPacket(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        if(offset>=wire.length)
            throw new AssertionError(
                "missing packet"
            );

        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "opcode="+opcode
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
                "packet body mismatch"
            );

        return offset+length;
    }

    private static int count(
        String value,
        char token
    ){
        int count=0;

        for(int i=0;i<value.length();i++)
            if(value.charAt(i)==token)
                count++;

        return count;
    }

    private static void expectInvalid(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "invalid target-55 field accepted"
            );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G144DailyNativeDefinitionIntegrationTest(){}
}

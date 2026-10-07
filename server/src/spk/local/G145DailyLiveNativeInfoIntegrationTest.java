package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G145DailyLiveNativeInfoIntegrationTest {
    private static final String PLAYER="daily-live-native";
    private static final int[] SEED={91,92,93,94};

    public static void main(String[] args)throws Exception{
        boolean ownedInfo=false;
        boolean target55First=false;
        boolean chatSecond=false;
        boolean assigned0=false;
        boolean progressed2=false;
        boolean claimNoNative=false;
        boolean foreignNoNative=false;
        boolean foreignFailClosed=false;

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
            LocalDailyChallengeCommandHandler handler=
                new LocalDailyChallengeCommandHandler(
                    player,
                    daily
                );

            DailyChallengeClientRequest info=
                ClientPacketProbe.dailyChallengeRequest(
                    "infochallenge "+
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                );

            require(
                info!=null,
                "owned INFO not normalized"
            );

            LocalDailyChallengeCommandHandler.Result
                zeroResult=
                    handler.handle(info);

            ownedInfo=
                zeroResult!=null&&
                zeroResult.nativeDefinition!=null&&
                zeroResult.nativeDefinition
                    .challengeKey.equals(
                        LocalLabDailyChallengeRuntime
                            .CHALLENGE_KEY
                    );

            assigned0=
                zeroResult.nativeDefinition.current==0L&&
                zeroResult.nativeDefinition.target==3L;

            ByteArrayOutputStream zeroWire=
                new ByteArrayOutputStream();
            ServerPacketWriter zeroWriter=
                new ServerPacketWriter(
                    zeroWire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            LocalPendingRequestDispatcher
                .publishDailyChallengeResult(
                    zeroResult,
                    zeroWriter
                );

            IsaacCipher zeroDecode=
                new IsaacCipher(
                    SEED.clone()
                );

            int offset=
                assert126(
                    zeroWire.toByteArray(),
                    0,
                    zeroDecode,
                    definitionPayload(
                        zeroResult.nativeDefinition
                    ),
                    55
                );

            target55First=
                offset>0;

            int afterChat=
                assert253(
                    zeroWire.toByteArray(),
                    offset,
                    zeroDecode,
                    zeroResult.clientMessage
                );

            chatSecond=
                afterChat==zeroWire.size();

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

            LocalDailyChallengeCommandHandler.Result
                twoResult=
                    handler.handle(info);

            ByteArrayOutputStream twoWire=
                new ByteArrayOutputStream();
            ServerPacketWriter twoWriter=
                new ServerPacketWriter(
                    twoWire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            LocalPendingRequestDispatcher
                .publishDailyChallengeResult(
                    twoResult,
                    twoWriter
                );

            IsaacCipher twoDecode=
                new IsaacCipher(
                    SEED.clone()
                );

            int twoOffset=
                assert126(
                    twoWire.toByteArray(),
                    0,
                    twoDecode,
                    definitionPayload(
                        twoResult.nativeDefinition
                    ),
                    55
                );

            int twoEnd=
                assert253(
                    twoWire.toByteArray(),
                    twoOffset,
                    twoDecode,
                    twoResult.clientMessage
                );

            progressed2=
                twoResult.nativeDefinition.current==2L&&
                twoResult.nativeDefinition.target==3L&&
                twoEnd==twoWire.size();

            DailyChallengeClientRequest claim=
                ClientPacketProbe.dailyChallengeRequest(
                    "claimchallenge "+
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                );

            LocalDailyChallengeCommandHandler.Result
                claimResult=
                    handler.handle(claim);

            require(
                claimResult!=null,
                "owned CLAIM not handled"
            );

            ByteArrayOutputStream claimWire=
                new ByteArrayOutputStream();
            ServerPacketWriter claimWriter=
                new ServerPacketWriter(
                    claimWire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            LocalPendingRequestDispatcher
                .publishDailyChallengeResult(
                    claimResult,
                    claimWriter
                );

            IsaacCipher claimDecode=
                new IsaacCipher(
                    SEED.clone()
                );

            int claimEnd=
                assert253(
                    claimWire.toByteArray(),
                    0,
                    claimDecode,
                    claimResult.clientMessage
                );

            claimNoNative=
                claimResult.nativeDefinition==null&&
                claimEnd==claimWire.size();

            DailyChallengeClientRequest foreign=
                ClientPacketProbe.dailyChallengeRequest(
                    "infochallenge foreign-live-key"
                );

            LocalDailyChallengeCommandHandler.Result
                foreignResult=
                    handler.handle(foreign);

            ByteArrayOutputStream foreignWire=
                new ByteArrayOutputStream();

            foreignNoNative=
                foreignResult==null&&
                foreignWire.size()==0;

            String foreignDiagnostic=
                LocalPendingRequestDispatcher
                    .dailyChallengeFailClosedDiagnostic(
                        foreign
                    );

            foreignFailClosed=
                foreignDiagnostic.contains(
                    "DAILY_CHALLENGE_KEY_ADAPTER_UNPROVEN"
                )&&
                foreignDiagnostic.contains(
                    "stateMutation=false"
                );

            require(
                ownedInfo&&
                target55First&&
                chatSecond&&
                assigned0&&
                progressed2&&
                claimNoNative&&
                foreignNoNative&&
                foreignFailClosed,
                "G14.5 acceptance"
            );

            System.out.println(
                "G145_DAILY_LIVE_NATIVE_INFO_PASS"+
                " ownedInfo="+ownedInfo+
                " target55First="+target55First+
                " chatSecond="+chatSecond+
                " assigned0="+assigned0+
                " progressed2="+progressed2+
                " claimNoNative="+claimNoNative+
                " foreignNoNative="+foreignNoNative+
                " foreignFailClosed="+foreignFailClosed+
                " target56Claim=false"+
                " buildClearClaim=false"+
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

    private static String definitionPayload(
        DailyChallengeApplicationService
            .ChallengeSnapshot challenge
    ){
        return "0;0;"+
            challenge.challengeKey+
            ";"+
            challenge.description+
            ";"+
            challenge.current+
            ";"+
            challenge.target;
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
                "target55 length"
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
                "target55 body"
            );

        return offset+length;
    }

    private static int assert253(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String message
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=253)
            throw new AssertionError(
                "expected 253 actual="+opcode
            );

        int length=wire[offset++]&255;

        byte[] text=
            message.getBytes(
                StandardCharsets.ISO_8859_1
            );
        byte[] expected=
            Arrays.copyOf(
                text,
                text.length+1
            );
        expected[text.length]=10;

        if(length!=expected.length)
            throw new AssertionError(
                "server-message length"
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
                "server-message body"
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

    private G145DailyLiveNativeInfoIntegrationTest(){}
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G182DailyMoneyMakingWidgetsIntegrationTest {
    private static final int[] SEED={161,162,163,164};

    public static void main(String[] args)throws Exception{
        boolean easy55021=false;
        boolean medium55018=false;
        boolean hard55015=false;
        boolean semanticSelection=false;
        boolean target36Projection=false;
        boolean duplicateStable=false;
        boolean track55002FailClosed=false;
        boolean teleport55012FailClosed=false;
        boolean trackingMutation=false;
        boolean teleportMutation=false;

        LocalDailyMoneyMakingUiHandler handler=
            new LocalDailyMoneyMakingUiHandler();

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        LocalDailyMoneyMakingUiHandler.Result easy=
            LocalSessionUiActionHandler
                .dispatchDailyMoneyMakingWidget(
                    handler,
                    DailyMoneyMakingPresentation
                        .EASY_WIDGET,
                    writer
                );

        easy55021=
            easy!=null&&
            easy.input==
                DailyMoneyMakingPresentation
                    .InputKind.SELECT_EASY&&
            easy.succeeded()&&
            easy.stateChanged&&
            easy.snapshot.selectedDifficulty==
                DailyMoneyMakingStateService
                    .Difficulty.EASY;

        int afterEasy=
            wire.size();

        LocalDailyMoneyMakingUiHandler.Result
            duplicate=
                LocalSessionUiActionHandler
                    .dispatchDailyMoneyMakingWidget(
                        handler,
                        DailyMoneyMakingPresentation
                            .EASY_WIDGET,
                        writer
                    );

        duplicateStable=
            duplicate!=null&&
            duplicate.succeeded()&&
            !duplicate.stateChanged&&
            duplicate.snapshot.selectedDifficulty==
                DailyMoneyMakingStateService
                    .Difficulty.EASY&&
            wire.size()>afterEasy;

        int beforeTrack=
            wire.size();

        LocalDailyMoneyMakingUiHandler.Result track=
            LocalSessionUiActionHandler
                .dispatchDailyMoneyMakingWidget(
                    handler,
                    DailyMoneyMakingPresentation
                        .TRACK_WIDGET,
                    writer
                );

        int afterTrack=
            wire.size();

        track55002FailClosed=
            track!=null&&
            track.input==
                DailyMoneyMakingPresentation
                    .InputKind.TRACK&&
            "NO_ACTIVITY_AUTHORITY".equals(
                track.status
            )&&
            !track.stateChanged&&
            afterTrack==beforeTrack;

        trackingMutation=
            track.snapshot.trackedObjectiveKey!=null;

        DailyMoneyMakingStateService.Difficulty
            difficultyBeforeTeleport=
                track.snapshot.selectedDifficulty;

        int wireBeforeTeleport=
            wire.size();

        LocalDailyMoneyMakingUiHandler.Result teleport=
            LocalSessionUiActionHandler
                .dispatchDailyMoneyMakingWidget(
                    handler,
                    DailyMoneyMakingPresentation
                        .TELEPORT_WIDGET,
                    writer
                );

        teleport55012FailClosed=
            teleport!=null&&
            teleport.input==
                DailyMoneyMakingPresentation
                    .InputKind.TELEPORT&&
            "NO_TELEPORT_AUTHORITY".equals(
                teleport.status
            )&&
            !teleport.stateChanged&&
            wire.size()==wireBeforeTeleport;

        teleportMutation=
            teleport.snapshot.selectedDifficulty!=
                difficultyBeforeTeleport||
            teleport.snapshot.trackedObjectiveKey!=null;

        LocalDailyMoneyMakingUiHandler.Result medium=
            LocalSessionUiActionHandler
                .dispatchDailyMoneyMakingWidget(
                    handler,
                    DailyMoneyMakingPresentation
                        .MEDIUM_WIDGET,
                    writer
                );

        medium55018=
            medium!=null&&
            medium.succeeded()&&
            medium.snapshot.selectedDifficulty==
                DailyMoneyMakingStateService
                    .Difficulty.MEDIUM;

        LocalDailyMoneyMakingUiHandler.Result hard=
            LocalSessionUiActionHandler
                .dispatchDailyMoneyMakingWidget(
                    handler,
                    DailyMoneyMakingPresentation
                        .HARD_WIDGET,
                    writer
                );

        hard55015=
            hard!=null&&
            hard.succeeded()&&
            hard.snapshot.selectedDifficulty==
                DailyMoneyMakingStateService
                    .Difficulty.HARD;

        semanticSelection=
            handler.snapshot()
                .selectedDifficulty==
                DailyMoneyMakingStateService
                    .Difficulty.HARD&&
            handler.snapshot()
                .trackedObjectiveKey==null;

        LocalDailyMoneyMakingUiHandler.Result unknown=
            LocalSessionUiActionHandler
                .dispatchDailyMoneyMakingWidget(
                    handler,
                    55016,
                    writer
                );

        require(
            unknown==null,
            "unknown Daily Money Making widget routed"
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
                "1",
                36
            );
        offset=
            assert126(
                bytes,
                offset,
                decode,
                "1",
                36
            );
        offset=
            assert126(
                bytes,
                offset,
                decode,
                "2",
                36
            );
        offset=
            assert126(
                bytes,
                offset,
                decode,
                "3",
                36
            );

        target36Projection=
            offset==bytes.length;

        require(
            easy55021&&
            medium55018&&
            hard55015&&
            semanticSelection&&
            target36Projection&&
            duplicateStable&&
            track55002FailClosed&&
            teleport55012FailClosed&&
            !trackingMutation&&
            !teleportMutation,
            "G18.2 acceptance"
        );

        System.out.println(
            "G182_DAILY_MONEY_MAKING_WIDGETS_PASS"+
            " easy55021="+easy55021+
            " medium55018="+medium55018+
            " hard55015="+hard55015+
            " semanticSelection="+
                semanticSelection+
            " target36Projection="+
                target36Projection+
            " duplicateStable="+
                duplicateStable+
            " track55002FailClosed="+
                track55002FailClosed+
            " teleport55012FailClosed="+
                teleport55012FailClosed+
            " trackingMutation="+
                trackingMutation+
            " teleportMutation="+
                teleportMutation+
            " rootClaim=false"+
            " rewardPolicyClaim=false"+
            " progressPolicyClaim=false"+
            " persistenceClaim=false"
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

    private G182DailyMoneyMakingWidgetsIntegrationTest(){}
}

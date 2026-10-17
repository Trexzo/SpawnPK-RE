package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * LocalLab-only adapter from the certified G14 Daily PvM semantic snapshot to
 * exact-current S2C126 target 55.
 *
 * The exact client retains two integer metadata fields but the recovered
 * renderer does not establish their meaning. LocalLab therefore publishes
 * explicit neutral placeholders rather than inventing original-server policy.
 */
final class LocalLabDailyChallengePresentation {
    static final int METADATA_A=0;
    static final int METADATA_B=0;
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G144_DAILY_NATIVE_DEFINITION";

    private final ServerPacketWriter writer;

    LocalLabDailyChallengePresentation(
        ServerPacketWriter writer
    ){
        this.writer=
            Objects.requireNonNull(
                writer,
                "writer"
            );
    }

    void publishDefinition(
        DailyChallengeApplicationService
            .ChallengeSnapshot challenge
    )throws IOException{
        DailyChallengeApplicationService
            .ChallengeSnapshot checked=
                Objects.requireNonNull(
                    challenge,
                    "challenge"
                );

        if(!LocalLabDailyChallengeRuntime
                .CHALLENGE_KEY
                .equals(
                    checked.challengeKey
                ))
            throw new IllegalArgumentException(
                "Unowned LocalLab Daily Challenge key "+
                checked.challengeKey
            );

        int current=
            exactInt(
                checked.current,
                "current"
            );
        int target=
            exactInt(
                checked.target,
                "target"
            );

        ApplicationControl126Service
            .dailyChallengeDefinition(
                writer,
                METADATA_A,
                METADATA_B,
                checked.challengeKey,
                checked.description,
                current,
                target
            );
    }

    private static int exactInt(
        long value,
        String name
    ){
        if(value<Integer.MIN_VALUE||
           value>Integer.MAX_VALUE)
            throw new IllegalArgumentException(
                name+
                " outside exact Daily Challenge int range: "+
                value
            );

        return (int)value;
    }
}

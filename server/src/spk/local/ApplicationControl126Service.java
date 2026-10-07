package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Typed publication boundary for exact-current S2C126 application control/state.
 *
 * Domain/content callers should choose semantic operations here rather than
 * constructing target ids or control strings.
 */
final class ApplicationControl126Service {
    static void publish(
        ServerPacketWriter writer,
        ApplicationControl126Command command
    )throws IOException{
        Objects.requireNonNull(
            writer,
            "writer"
        );
        Objects.requireNonNull(
            command,
            "command"
        );

        ApplicationBus126Publisher.send(
            writer,
            command
        );
    }

    static void constructionBuild(
        ServerPacketWriter writer,
        boolean enabled
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                enabled
                    ?ApplicationControl126Command.Token
                        .CONSTRUCTION_BUILD_ON
                    :ApplicationControl126Command.Token
                        .CONSTRUCTION_BUILD_OFF
            )
        );
    }

    static void adventureBegin(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token.BEGIN_ADVENTURE
            )
        );
    }

    static void adventureOrb(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token.BEGIN_ADVENTURE_ORB
            )
        );
    }

    static void adventureBook(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token.BEGIN_ADVENTURE_BOOK
            )
        );
    }

    static void adventureEnd(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token.END_ADVENTURE
            )
        );
    }

    static void raidInstance(
        ServerPacketWriter writer,
        boolean enabled
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                enabled
                    ?ApplicationControl126Command.Token
                        .RAID_INSTANCE_ON
                    :ApplicationControl126Command.Token
                        .RAID_INSTANCE_OFF
            )
        );
    }

    static void achievementClear(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CLEAR_ACHIEVEMENT_TAB
            )
        );
    }

    static void achievementBuild(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .BUILD_ACHIEVEMENT_TAB
            )
        );
    }

    static void achievementRebuild(
        ServerPacketWriter writer
    )throws IOException{
        achievementClear(
            writer
        );
        achievementBuild(
            writer
        );
    }

    static void dailyChallengesClear(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CLEAR_ACHIEVEMENT_TAB
            )
        );
    }

    static void dailyChallengesBuild(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .BUILD_ACHIEVEMENT_TAB
            )
        );
    }

    static void exchangeClear(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CLEAR_EXCHANGE
            )
        );
    }

    static void exchangeAdd(
        ServerPacketWriter writer,
        int first,
        int second
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.addExchange(
                first,
                second
            )
        );
    }

    static void exchangeUpdate(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .UPDATE_EXCHANGE
            )
        );
    }

    static void marketClearSell(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CLEAR_SELL_MARKET
            )
        );
    }

    static void marketClearBuy(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token
                    .CLEAR_BUY_MARKET
            )
        );
    }

    static void marketSetSellItem(
        ServerPacketWriter writer,
        int itemId
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command
                .setSellItem(
                    itemId
                )
        );
    }

    static void loginRewardIndex(
        ServerPacketWriter writer,
        int index
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command
                .loginRewardIndex(
                    index
                )
        );
    }

    static void dailyChallengeDefinition(
        ServerPacketWriter writer,
        int metadataA,
        int metadataB,
        String challengeKey,
        String description,
        int current,
        int target
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command
                .dailyChallengeDefinition(
                    metadataA,
                    metadataB,
                    challengeKey,
                    description,
                    current,
                    target
                )
        );
    }

    static void bloodPoolResetSlots(
        ServerPacketWriter writer
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command
                .bloodPoolResetSlots()
        );
    }

    static void bloodPoolAppendOpaqueSlot(
        ServerPacketWriter writer,
        String exactRecord
    )throws IOException{
        publish(
            writer,
            ApplicationControl126Command
                .bloodPoolAppendOpaque(
                    exactRecord
                )
        );
    }

    private ApplicationControl126Service(){}
}

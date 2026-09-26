package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 World Tournament / Tournament Leaderboards presentation and
 * widget-input adapter.
 *
 * This boundary owns only exact client roots, C2S185 widget normalization,
 * text-row address ranges and S2C126-compatible text publication.
 *
 * Scheduling, eligibility, bracket/matchmaking rules, maps, elimination,
 * scoring, shop catalog/prices, rewards and persistence remain server policy.
 */
final class TournamentPresentation {
    static final int TOURNAMENT_ROOT=27400;
    static final int LEADERBOARD_ROOT=61011;
    static final int PRIZE_WIDGET=56007;

    static final int ENTER_WIDGET=56044;
    static final int SPECTATE_WIDGET=56049;
    static final int SHOP_WIDGET=56053;

    static final int PLAYER_WEEK_WIDGET=61016;
    static final int PLAYER_ALL_TIME_WIDGET=61017;
    static final int CLAN_WEEK_WIDGET=61020;
    static final int CLAN_ALL_TIME_WIDGET=61021;

    static final int HISTORY_FIRST_WIDGET=56009;
    static final int HISTORY_LAST_WIDGET=56043;
    static final int PLAYER_FIRST_WIDGET=61025;
    static final int PLAYER_LAST_WIDGET=61050;
    static final int CLAN_FIRST_WIDGET=61052;
    static final int CLAN_LAST_WIDGET=61077;

    static final int HISTORY_ROWS=
        HISTORY_LAST_WIDGET-HISTORY_FIRST_WIDGET+1;
    static final int PLAYER_ROWS=
        PLAYER_LAST_WIDGET-PLAYER_FIRST_WIDGET+1;
    static final int CLAN_ROWS=
        CLAN_LAST_WIDGET-CLAN_FIRST_WIDGET+1;

    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum InputKind {
        ENTER,
        SPECTATE,
        OPEN_SHOP,
        LEADERBOARD_FILTER
    }

    enum LeaderboardScope {
        PLAYERS_THIS_WEEK,
        PLAYERS_ALL_TIME,
        CLANS_THIS_WEEK,
        CLANS_ALL_TIME
    }

    static final class Input {
        final InputKind kind;
        final LeaderboardScope leaderboardScope;

        private Input(
            InputKind kind,
            LeaderboardScope leaderboardScope
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.leaderboardScope=leaderboardScope;
        }

        static Input simple(InputKind kind){
            if(kind==InputKind.LEADERBOARD_FILTER)
                throw new IllegalArgumentException(
                    "leaderboard filter requires scope"
                );

            return new Input(kind,null);
        }

        static Input filter(LeaderboardScope scope){
            return new Input(
                InputKind.LEADERBOARD_FILTER,
                Objects.requireNonNull(scope,"scope")
            );
        }
    }

    static Input resolveWidget(int widgetId){
        checkedWidget(widgetId);

        switch(widgetId){
            case ENTER_WIDGET:
                return Input.simple(InputKind.ENTER);
            case SPECTATE_WIDGET:
                return Input.simple(InputKind.SPECTATE);
            case SHOP_WIDGET:
                return Input.simple(InputKind.OPEN_SHOP);
            case PLAYER_WEEK_WIDGET:
                return Input.filter(
                    LeaderboardScope.PLAYERS_THIS_WEEK
                );
            case PLAYER_ALL_TIME_WIDGET:
                return Input.filter(
                    LeaderboardScope.PLAYERS_ALL_TIME
                );
            case CLAN_WEEK_WIDGET:
                return Input.filter(
                    LeaderboardScope.CLANS_THIS_WEEK
                );
            case CLAN_ALL_TIME_WIDGET:
                return Input.filter(
                    LeaderboardScope.CLANS_ALL_TIME
                );
            default:
                return null;
        }
    }

    static void openTournament(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    TOURNAMENT_ROOT
                )
            );
    }

    static void openLeaderboard(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    LEADERBOARD_ROOT
                )
            );
    }

    static void publishHistoryRow(
        ServerPacketWriter packets,
        int rowIndex,
        String text
    )throws IOException{
        publishRow(
            packets,
            historyRowWidget(rowIndex),
            text
        );
    }

    static void publishPlayerRow(
        ServerPacketWriter packets,
        int rowIndex,
        String text
    )throws IOException{
        publishRow(
            packets,
            playerRowWidget(rowIndex),
            text
        );
    }

    static void publishClanRow(
        ServerPacketWriter packets,
        int rowIndex,
        String text
    )throws IOException{
        publishRow(
            packets,
            clanRowWidget(rowIndex),
            text
        );
    }

    static int historyRowWidget(int rowIndex){
        return HISTORY_FIRST_WIDGET+
            checkedRow(
                rowIndex,
                HISTORY_ROWS,
                "historyRowIndex"
            );
    }

    static int playerRowWidget(int rowIndex){
        return PLAYER_FIRST_WIDGET+
            checkedRow(
                rowIndex,
                PLAYER_ROWS,
                "playerRowIndex"
            );
    }

    static int clanRowWidget(int rowIndex){
        return CLAN_FIRST_WIDGET+
            checkedRow(
                rowIndex,
                CLAN_ROWS,
                "clanRowIndex"
            );
    }

    private static void publishRow(
        ServerPacketWriter packets,
        int widgetId,
        String text
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            widgetId,
            requireText(text)
        );
    }

    private static String requireText(String text){
        if(text==null)
            throw new NullPointerException("text");

        if(text.indexOf('\n')>=0||
           text.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                "text contains line terminator"
            );

        return text;
    }

    private static int checkedRow(
        int rowIndex,
        int rowCount,
        String label
    ){
        if(rowIndex<0||rowIndex>=rowCount)
            throw new IllegalArgumentException(
                label+"="+rowIndex
            );

        return rowIndex;
    }

    private static void checkedWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );
    }

    private TournamentPresentation(){}
}

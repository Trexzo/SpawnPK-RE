package spk.local;

import java.lang.reflect.Method;
import java.util.Locale;

public final class TournamentPresentationTest {
    public static void main(String[] args)
        throws Exception{
        exactRootsAndRanges();
        exactWidgetRouting();
        exactPacketCompatibility();
        policyStillUnowned();

        System.out.println(
            "TOURNAMENT_PRESENTATION_PASS "+
            "root27400=true "+
            "leaderboard61011=true "+
            "hubActions3=true "+
            "filters4=true "+
            "historyRows35=true "+
            "playerRows26=true "+
            "clanRows26=true "+
            "prizeWidget56007Unowned=true "+
            "policyOwned=false"
        );
    }

    private static void exactRootsAndRanges(){
        require(
            TournamentPresentation.TOURNAMENT_ROOT==
                27400,
            "tournament root"
        );
        require(
            TournamentPresentation.LEADERBOARD_ROOT==
                61011,
            "leaderboard root"
        );
        require(
            TournamentPresentation.PRIZE_WIDGET==
                56007,
            "prize widget"
        );

        require(
            TournamentPresentation.ENTER_WIDGET==0xdaec&&
            TournamentPresentation.SPECTATE_WIDGET==0xdaf1&&
            TournamentPresentation.SHOP_WIDGET==0xdaf5,
            "hub C2S185 wire bodies"
        );
        require(
            TournamentPresentation.PLAYER_WEEK_WIDGET==0xee58&&
            TournamentPresentation.PLAYER_ALL_TIME_WIDGET==0xee59&&
            TournamentPresentation.CLAN_WEEK_WIDGET==0xee5c&&
            TournamentPresentation.CLAN_ALL_TIME_WIDGET==0xee5d,
            "leaderboard C2S185 wire bodies"
        );
        require(
            TournamentPresentation.WIDGET_ACTION_OPCODE==185,
            "widget action opcode"
        );

        require(
            TournamentPresentation.HISTORY_ROWS==35&&
            TournamentPresentation
                .historyRowWidget(0)==56009&&
            TournamentPresentation
                .historyRowWidget(34)==56043,
            "history rows"
        );

        require(
            TournamentPresentation.PLAYER_ROWS==26&&
            TournamentPresentation
                .playerRowWidget(0)==61025&&
            TournamentPresentation
                .playerRowWidget(25)==61050,
            "player leaderboard rows"
        );

        require(
            TournamentPresentation.CLAN_ROWS==26&&
            TournamentPresentation
                .clanRowWidget(0)==61052&&
            TournamentPresentation
                .clanRowWidget(25)==61077,
            "clan leaderboard rows"
        );

        expect(
            IllegalArgumentException.class,
            ()->TournamentPresentation
                .historyRowWidget(35),
            "history overflow"
        );
        expect(
            IllegalArgumentException.class,
            ()->TournamentPresentation
                .playerRowWidget(26),
            "player overflow"
        );
        expect(
            IllegalArgumentException.class,
            ()->TournamentPresentation
                .clanRowWidget(26),
            "clan overflow"
        );
    }

    private static void exactWidgetRouting(){
        requireKind(
            56044,
            TournamentPresentation.InputKind.ENTER
        );
        requireKind(
            56049,
            TournamentPresentation.InputKind.SPECTATE
        );
        requireKind(
            56053,
            TournamentPresentation.InputKind.OPEN_SHOP
        );

        requireFilter(
            61016,
            TournamentPresentation
                .LeaderboardScope
                .PLAYERS_THIS_WEEK
        );
        requireFilter(
            61017,
            TournamentPresentation
                .LeaderboardScope
                .PLAYERS_ALL_TIME
        );
        requireFilter(
            61020,
            TournamentPresentation
                .LeaderboardScope
                .CLANS_THIS_WEEK
        );
        requireFilter(
            61021,
            TournamentPresentation
                .LeaderboardScope
                .CLANS_ALL_TIME
        );

        for(int widgetId:new int[]{
                56043,56045,56048,56050,
                56052,56054,61015,61018,
                61019,61022
            })
            require(
                TournamentPresentation
                    .resolveWidget(widgetId)==null,
                "neighbor widget "+widgetId
            );

        expect(
            IllegalArgumentException.class,
            ()->TournamentPresentation
                .resolveWidget(65536),
            "u16 widget fence"
        );
    }

    private static void exactPacketCompatibility()
        throws Exception{
        byte[] tournamentRoot=
            BootstrapPackets.interface97(
                TournamentPresentation
                    .TOURNAMENT_ROOT
            );
        byte[] leaderboardRoot=
            BootstrapPackets.interface97(
                TournamentPresentation
                    .LEADERBOARD_ROOT
            );

        require(
            tournamentRoot.length==2&&
            readU16(tournamentRoot)==27400,
            "tournament S2C97"
        );
        require(
            leaderboardRoot.length==2&&
            readU16(leaderboardRoot)==61011,
            "leaderboard S2C97"
        );

        assertTextBody(
            TournamentPresentation
                .historyRowWidget(0),
            "history"
        );
        assertTextBody(
            TournamentPresentation
                .playerRowWidget(25),
            "player"
        );
        assertTextBody(
            TournamentPresentation
                .clanRowWidget(25),
            "clan"
        );

        expect(
            IllegalArgumentException.class,
            ()->{
                try{
                    BootstrapPackets.widgetText126(
                        TournamentPresentation
                            .playerRowWidget(0),
                        "bad\nrow"
                    );
                    /*
                     * The low-level packet helper deliberately accepts
                     * arbitrary ISO-8859-1 text. The Tournament adapter
                     * owns the stronger line-terminator rejection.
                     */
                    Method publish=
                        TournamentPresentation.class
                            .getDeclaredMethod(
                                "requireText",
                                String.class
                            );
                    publish.setAccessible(true);
                    publish.invoke(
                        null,
                        "bad\nrow"
                    );
                }catch(
                    java.lang.reflect
                        .InvocationTargetException failure
                ){
                    Throwable cause=failure.getCause();
                    if(cause instanceof RuntimeException)
                        throw (RuntimeException)cause;
                    throw new RuntimeException(cause);
                }catch(ReflectiveOperationException|
                       java.io.IOException failure){
                    throw new RuntimeException(failure);
                }
            },
            "line terminator"
        );
    }

    private static void assertTextBody(
        int widgetId,
        String text
    )throws Exception{
        byte[] body=
            BootstrapPackets.widgetText126(
                widgetId,
                text
            );

        int n=body.length;

        require(
            n==text.length()+3,
            "S2C126 length widget="+widgetId
        );
        require(
            (body[text.length()]&255)==10,
            "S2C126 newline widget="+widgetId
        );

        int target=
            ((body[n-2]&255)<<8)|
            (((body[n-1]&255)-128)&255);

        require(
            target==widgetId,
            "S2C126 target widget="+widgetId+
            " decoded="+target
        );
    }

    private static void policyStillUnowned(){
        for(Method method:
                TournamentPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("schedule")||
               name.contains("eligible")||
               name.contains("bracket")||
               name.contains("matchmake")||
               name.contains("eliminate")||
               name.contains("score")||
               name.contains("reward")||
               name.contains("price")||
               name.contains("persist"))
                throw new AssertionError(
                    "unowned Tournament policy method "+
                    method.getName()
                );
        }
    }

    private static void requireKind(
        int widgetId,
        TournamentPresentation.InputKind expected
    ){
        TournamentPresentation.Input input=
            TournamentPresentation
                .resolveWidget(widgetId);

        require(
            input!=null&&
            input.kind==expected&&
            input.leaderboardScope==null,
            "widget "+widgetId+" -> "+expected
        );
    }

    private static void requireFilter(
        int widgetId,
        TournamentPresentation.LeaderboardScope expected
    ){
        TournamentPresentation.Input input=
            TournamentPresentation
                .resolveWidget(widgetId);

        require(
            input!=null&&
            input.kind==
                TournamentPresentation
                    .InputKind
                    .LEADERBOARD_FILTER&&
            input.leaderboardScope==expected,
            "filter "+widgetId+" -> "+expected
        );
    }

    private static int readU16(byte[] body){
        return ((body[0]&255)<<8)|
            (body[1]&255);
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private TournamentPresentationTest(){}
}

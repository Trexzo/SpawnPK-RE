package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class G113PkRatingsReadOnlyNavigationIntegrationTest {
    private static final String A="g113-a";
    private static final String B="g113-b";

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalPkRatingsUiHandler ratings;

        Bridge(LocalPkRatingsUiHandler ratings){
            this.ratings=ratings;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void clearDialogNumberKeys(){}

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter serverPackets,
            String tag
        )throws IOException{}

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}

        @Override public boolean openPkRatings(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            ratings.open(writer);
            return true;
        }

        @Override public PkRatingsService.Snapshot
            handlePkRatingsNavigation(
                PkRatingsService.Navigation navigation,
                ServerPacketWriter writer,
                String tag
            )throws IOException{
            return ratings.navigate(
                A,
                navigation,
                writer
            );
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean daily61002=false;
        boolean tournament61005=false;
        boolean rootOwnedOnly=false;
        boolean dailyReadOnly=false;
        boolean tournamentReadOnly=false;
        boolean onlineRosterOnly=false;
        boolean nativeTabReturnsBase=false;
        boolean competingRootRetires=false;
        boolean interfaceCloseRetires=false;
        boolean closedUiNoop=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(player,A);
        world.registerPlayer(
            new WorldPlayer(),
            B
        );

        try{
            LocalPkRatingsUiHandler ratings=
                new LocalPkRatingsUiHandler(
                    world
                );
            Bridge bridge=new Bridge(ratings);
            LocalSessionUiActionHandler ui=
                uiHandler(
                    player,
                    bridge
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{41,42,43,44}
                    )
                );

            PkRatingsPresentation.Input daily=
                PkRatingsPresentation.resolveWidget(
                    PkRatingsPresentation
                        .DAILY_PK_WIDGET
                );
            PkRatingsPresentation.Input tournament=
                PkRatingsPresentation.resolveWidget(
                    PkRatingsPresentation
                        .TOURNAMENT_PK_WIDGET
                );

            daily61002=
                PkRatingsPresentation.DAILY_PK_WIDGET==
                    61002&&
                daily!=null&&
                daily.kind==
                    PkRatingsPresentation.InputKind.NAVIGATE&&
                daily.navigation==
                    PkRatingsService.Navigation.DAILY_PK;

            tournament61005=
                PkRatingsPresentation.TOURNAMENT_PK_WIDGET==
                    61005&&
                tournament!=null&&
                tournament.kind==
                    PkRatingsPresentation.InputKind.NAVIGATE&&
                tournament.navigation==
                    PkRatingsService.Navigation.TOURNAMENT_PK;

            require(
                daily61002&&tournament61005,
                "exact navigation setup"
            );

            long beforeClosed=
                ratings.snapshot().datasetRevision;

            ui.handleWidget(
                PkRatingsPresentation.DAILY_PK_WIDGET,
                writer,
                "[g113-closed-before-open] "
            );

            rootOwnedOnly=
                ratings.snapshot().datasetRevision==
                    beforeClosed;

            require(
                rootOwnedOnly,
                "closed PK Ratings navigation mutated state"
            );

            ui.handleWidget(
                PkRatingsPresentation.RATINGS_TAB_WIDGET,
                writer,
                "[g113-open] "
            );

            ui.handleWidget(
                PkRatingsPresentation.DAILY_PK_WIDGET,
                writer,
                "[g113-daily] "
            );

            PkRatingsService.Snapshot dailySnapshot=
                ratings.snapshot();

            dailyReadOnly=
                dailySnapshot.rows.size()==3&&
                "LocalLab Daily PK (not scored)"
                    .equals(
                        dailySnapshot.rows.get(0).displayText
                    )&&
                allNonSelectable(dailySnapshot);

            onlineRosterOnly=
                ("Online: "+A).equals(
                    dailySnapshot.rows.get(1).displayText
                )&&
                ("Online: "+B).equals(
                    dailySnapshot.rows.get(2).displayText
                );

            require(
                dailyReadOnly&&onlineRosterOnly,
                "Daily PK read-only roster view"
            );

            ui.handleWidget(
                PkRatingsPresentation.TOURNAMENT_PK_WIDGET,
                writer,
                "[g113-tournament] "
            );

            PkRatingsService.Snapshot tournamentSnapshot=
                ratings.snapshot();

            tournamentReadOnly=
                tournamentSnapshot.rows.size()==3&&
                "LocalLab Tournament PK (not scored)"
                    .equals(
                        tournamentSnapshot.rows.get(0).displayText
                    )&&
                allNonSelectable(tournamentSnapshot)&&
                ("Online: "+A).equals(
                    tournamentSnapshot.rows.get(1).displayText
                )&&
                ("Online: "+B).equals(
                    tournamentSnapshot.rows.get(2).displayText
                );

            require(
                tournamentReadOnly,
                "Tournament PK read-only roster view"
            );

            ui.handleWidget(
                PkRatingsPresentation.RATINGS_TAB_WIDGET,
                writer,
                "[g113-base] "
            );

            PkRatingsService.Snapshot base=
                ratings.snapshot();

            nativeTabReturnsBase=
                base.rows.size()==3&&
                "LocalLab PK Ratings (not ranked)"
                    .equals(
                        base.rows.get(0).displayText
                    )&&
                allNonSelectable(base);

            require(
                nativeTabReturnsBase,
                "native tab did not restore base feed"
            );

            ui.replaceMonsterSpawnerWithDuelRoot(
                ()->"DUEL_ROOT_OPENED"
            );

            long beforeCompeting=
                ratings.snapshot().datasetRevision;

            ui.handleWidget(
                PkRatingsPresentation.DAILY_PK_WIDGET,
                writer,
                "[g113-after-competing] "
            );

            competingRootRetires=
                ratings.snapshot().datasetRevision==
                    beforeCompeting;

            require(
                competingRootRetires,
                "competing root did not retire PK Ratings ownership"
            );

            ui.handleWidget(
                PkRatingsPresentation.RATINGS_TAB_WIDGET,
                writer,
                "[g113-reopen] "
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g113-close] "
            );

            long beforeInterfaceClosed=
                ratings.snapshot().datasetRevision;

            ui.handleWidget(
                PkRatingsPresentation.TOURNAMENT_PK_WIDGET,
                writer,
                "[g113-after-close] "
            );

            interfaceCloseRetires=
                ratings.snapshot().datasetRevision==
                    beforeInterfaceClosed;

            closedUiNoop=
                rootOwnedOnly&&
                competingRootRetires&&
                interfaceCloseRetires;

            require(
                interfaceCloseRetires&&
                closedUiNoop,
                "interface close did not retire PK Ratings ownership"
            );

            System.out.println(
                "G113_PK_RATINGS_READ_ONLY_NAV_PASS"+
                " daily61002="+daily61002+
                " tournament61005="+tournament61005+
                " rootOwnedOnly="+rootOwnedOnly+
                " dailyReadOnly="+dailyReadOnly+
                " tournamentReadOnly="+tournamentReadOnly+
                " onlineRosterOnly="+onlineRosterOnly+
                " nativeTabReturnsBase="+nativeTabReturnsBase+
                " competingRootRetires="+competingRootRetires+
                " interfaceCloseRetires="+interfaceCloseRetires+
                " closedUiNoop="+closedUiNoop+
                " scoreClaim=false"+
                " rankingOrderClaim=false"+
                " rowSelectionClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean allNonSelectable(
        PkRatingsService.Snapshot snapshot
    ){
        for(PkRatingsService.Row row:snapshot.rows)
            if(row.selectable)
                return false;
        return true;
    }

    private static LocalSessionUiActionHandler uiHandler(
        WorldPlayer player,
        Bridge bridge
    ){
        BankState bank=player.bank();
        EquipmentState equipment=
            player.equipment();
        MovementState movement=
            player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);

        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                bank,
                player.miniPets(),
                player.petState(),
                npcs,
                movement,
                player.petAccessoryState()
            );

        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(
                player.prayers(),
                player.playerState(),
                equipment,
                player.combatStyles(),
                player.magic(),
                bank
            );

        LocalCompCapeCustomizeHandler compCape=
            new LocalCompCapeCustomizeHandler(
                bank,
                player.playerState()
            );

        return new LocalSessionUiActionHandler(
            player,
            new NativeItemLibraryService(),
            new DevControlCenter(),
            bank,
            compCape,
            petDialogs,
            gameplay,
            movement,
            true,
            equipment,
            bridge
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G113PkRatingsReadOnlyNavigationIntegrationTest(){}
}

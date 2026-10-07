package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class G114PkRatingsRowFailClosedIntegrationTest {
    private static final String A="g114-a";
    private static final String B="g114-b";

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
        boolean exactRowRange=false;
        boolean openHeaderNoop=false;
        boolean openRosterNoop=false;
        boolean closedUiNoop=false;
        boolean datasetUnchanged=false;
        boolean navigationStillWorks=false;

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
            Bridge bridge=
                new Bridge(ratings);
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
                        new int[]{51,52,53,54}
                    )
                );

            exactRowRange=
                PkRatingsPresentation.FIRST_ROW_WIDGET==
                    40405&&
                PkRatingsPresentation.LAST_ROW_WIDGET==
                    40454&&
                PkRatingsPresentation.rowWidget(0)==
                    40405&&
                PkRatingsPresentation.rowWidget(49)==
                    40454;

            require(
                exactRowRange,
                "exact PK Ratings row range"
            );

            ui.handleWidget(
                PkRatingsPresentation.RATINGS_TAB_WIDGET,
                writer,
                "[g114-open] "
            );

            PkRatingsService.Snapshot before=
                ratings.snapshot();

            require(
                before.rows.size()==3,
                "expected header + two online rows"
            );

            ui.handleWidget(
                PkRatingsPresentation.rowWidget(0),
                writer,
                "[g114-header] "
            );

            PkRatingsService.Snapshot afterHeader=
                ratings.snapshot();

            openHeaderNoop=
                sameDataset(
                    before,
                    afterHeader
                );

            require(
                openHeaderNoop,
                "header row action mutated read-only dataset"
            );

            ui.handleWidget(
                PkRatingsPresentation.rowWidget(1),
                writer,
                "[g114-roster] "
            );

            PkRatingsService.Snapshot afterRoster=
                ratings.snapshot();

            openRosterNoop=
                sameDataset(
                    afterHeader,
                    afterRoster
                );

            require(
                openRosterNoop,
                "online roster row action mutated read-only dataset"
            );

            ui.handleWidget(
                PkRatingsPresentation.LAST_ROW_WIDGET,
                writer,
                "[g114-empty-row] "
            );

            PkRatingsService.Snapshot afterAbsentRow=
                ratings.snapshot();

            datasetUnchanged=
                sameDataset(
                    before,
                    afterAbsentRow
                );

            require(
                datasetUnchanged,
                "unpopulated exact row action mutated dataset"
            );

            ui.handleWidget(
                PkRatingsPresentation.DAILY_PK_WIDGET,
                writer,
                "[g114-daily] "
            );

            PkRatingsService.Snapshot daily=
                ratings.snapshot();

            navigationStillWorks=
                daily.datasetRevision==
                    before.datasetRevision+1L&&
                daily.rows.size()==3&&
                "LocalLab Daily PK (not scored)"
                    .equals(
                        daily.rows.get(0).displayText
                    );

            require(
                navigationStillWorks,
                "read-only row fence broke navigation"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g114-close] "
            );

            PkRatingsService.Snapshot beforeClosedRow=
                ratings.snapshot();

            ui.handleWidget(
                PkRatingsPresentation.rowWidget(1),
                writer,
                "[g114-closed-row] "
            );

            closedUiNoop=
                sameDataset(
                    beforeClosedRow,
                    ratings.snapshot()
                );

            require(
                closedUiNoop,
                "closed-root PK Ratings row action mutated dataset"
            );

            System.out.println(
                "G114_PK_RATINGS_ROW_FAIL_CLOSED_PASS"+
                " exactRowRange="+exactRowRange+
                " openHeaderNoop="+openHeaderNoop+
                " openRosterNoop="+openRosterNoop+
                " closedUiNoop="+closedUiNoop+
                " datasetUnchanged="+datasetUnchanged+
                " navigationStillWorks="+navigationStillWorks+
                " rowSelectionClaim=false"+
                " targetClaim=false"+
                " ratingClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean sameDataset(
        PkRatingsService.Snapshot first,
        PkRatingsService.Snapshot second
    ){
        if(first.datasetRevision!=second.datasetRevision||
           first.rows.size()!=second.rows.size())
            return false;

        for(int i=0;i<first.rows.size();i++){
            PkRatingsService.Row a=first.rows.get(i);
            PkRatingsService.Row b=second.rows.get(i);

            if(!a.rowKey.equals(b.rowKey)||
               !a.displayText.equals(b.displayText)||
               a.selectable!=b.selectable||
               a.sourceAuthority!=b.sourceAuthority)
                return false;
        }

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

    private G114PkRatingsRowFailClosedIntegrationTest(){}
}

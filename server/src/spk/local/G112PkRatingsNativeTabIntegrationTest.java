package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class G112PkRatingsNativeTabIntegrationTest {
    private static final String A="g112-a";
    private static final String B="g112-b";
    private static final int[] SEED={31,32,33,34};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalPkRatingsUiHandler ratings;
        boolean tournamentOpen=true;
        boolean duelOpen=true;
        boolean nativeOpened;

        Bridge(
            LocalPkRatingsUiHandler ratings
        ){
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

        @Override public boolean retireTournamentRoot(){
            boolean wasOpen=tournamentOpen;
            tournamentOpen=false;
            return wasOpen;
        }

        @Override public boolean retireDuelRoot(){
            boolean wasOpen=duelOpen;
            duelOpen=false;
            return wasOpen;
        }

        @Override public boolean openPkRatings(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            ratings.open(writer);
            nativeOpened=true;
            return true;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean exactTab32017=false;
        boolean nativeTabOpen=false;
        boolean exactRoot40403=false;
        boolean exactSubtype16=false;
        boolean onlineRoster=false;
        boolean competingRootLifecycle=false;
        boolean commandRouteInherited=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            A
        );
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
                new Bridge(
                    ratings
                );
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
                        SEED.clone()
                    )
                );

            PkRatingsPresentation.Input exact=
                PkRatingsPresentation.resolveWidget(
                    PkRatingsPresentation
                        .RATINGS_TAB_WIDGET
                );

            exactTab32017=
                PkRatingsPresentation
                    .RATINGS_TAB_WIDGET==32017&&
                exact!=null&&
                exact.kind==
                    PkRatingsPresentation
                        .InputKind
                        .OPEN_RATINGS_TAB;

            commandRouteInherited=
                LocalCommandDispatcher
                    .isPkRatingsRoute(
                        new String[]{"pkratings"}
                    );

            require(
                exactTab32017&&
                commandRouteInherited,
                "G11.2 exact tab/command authority setup failed"
            );

            int beforeUnsupported=
                wire.size();
            long beforeRevision=
                ratings.snapshot()
                    .datasetRevision;

            ui.handleWidget(
                PkRatingsPresentation
                    .DAILY_PK_WIDGET,
                writer,
                "[g112-daily] "
            );
            ui.handleWidget(
                PkRatingsPresentation
                    .TOURNAMENT_PK_WIDGET,
                writer,
                "[g112-tournament] "
            );
            ui.handleWidget(
                PkRatingsPresentation
                    .FIRST_ROW_WIDGET,
                writer,
                "[g112-row] "
            );

            require(
                wire.size()==beforeUnsupported&&
                ratings.snapshot()
                    .datasetRevision==
                    beforeRevision,
                "G11.2 claimed navigation/row input"
            );

            ui.handleWidget(
                PkRatingsPresentation
                    .RATINGS_TAB_WIDGET,
                writer,
                "[g112-tab] "
            );

            nativeTabOpen=
                bridge.nativeOpened&&
                wire.size()>beforeUnsupported;

            PkRatingsService.Snapshot snapshot=
                ratings.snapshot();

            onlineRoster=
                snapshot.rows.size()==3&&
                "LocalLab PK Ratings (not ranked)"
                    .equals(
                        snapshot.rows
                            .get(0)
                            .displayText
                    )&&
                ("Online: "+A)
                    .equals(
                        snapshot.rows
                            .get(1)
                            .displayText
                    )&&
                ("Online: "+B)
                    .equals(
                        snapshot.rows
                            .get(2)
                            .displayText
                    );

            competingRootLifecycle=
                !bridge.tournamentOpen&&
                !bridge.duelOpen;

            WireAuthority decoded=
                decodeAuthority(
                    wire.toByteArray()
                );

            exactRoot40403=
                PkRatingsPresentation
                    .RATINGS_ROOT==40403&&
                decoded.root==
                    PkRatingsPresentation
                        .RATINGS_ROOT;

            exactSubtype16=
                PkRatingsPresentation
                    .APPLICATION_SUBTYPE==16&&
                decoded.firstSubtype==
                    PkRatingsPresentation
                        .APPLICATION_SUBTYPE&&
                decoded.firstOperation==0;

            require(
                nativeTabOpen&&
                exactRoot40403&&
                exactSubtype16&&
                onlineRoster&&
                competingRootLifecycle,
                "G11.2 native PK Ratings postimage failed"
            );

            System.out.println(
                "G112_PK_RATINGS_NATIVE_TAB_PASS"+
                " exactTab32017="+exactTab32017+
                " nativeTabOpen="+nativeTabOpen+
                " exactRoot40403="+exactRoot40403+
                " exactSubtype16="+exactSubtype16+
                " onlineRoster="+onlineRoster+
                " competingRootLifecycle="+
                    competingRootLifecycle+
                " commandRouteInherited="+
                    commandRouteInherited+
                " dailyNavigationClaim=false"+
                " tournamentNavigationClaim=false"+
                " rowSelectionClaim=false"+
                " ratingFormulaClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
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

    private static WireAuthority decodeAuthority(
        byte[] wire
    ){
        IsaacCipher decoder=
            new IsaacCipher(
                SEED.clone()
            );

        int p=0;

        require(
            wire.length>=8,
            "G11.2 wire too short"
        );

        int rootOpcode=
            ((wire[p++]&255)-
                decoder.nextInt())&
            255;

        require(
            rootOpcode==97,
            "G11.2 first opcode="+
                rootOpcode
        );

        int root=
            ((wire[p++]&255)<<8)|
            (wire[p++]&255);

        int appOpcode=
            ((wire[p++]&255)-
                decoder.nextInt())&
            255;

        require(
            appOpcode==
                ApplicationPacket250Writer
                    .OPCODE,
            "G11.2 application opcode="+
                appOpcode
        );

        int length=wire[p++]&255;

        require(
            length>=3&&
                p+length<=wire.length,
            "G11.2 first application length="+
                length
        );

        int subtype=
            ((wire[p++]&255)<<8)|
            (wire[p++]&255);
        int operation=wire[p++]&255;

        return new WireAuthority(
            root,
            subtype,
            operation
        );
    }

    private static final class WireAuthority {
        final int root;
        final int firstSubtype;
        final int firstOperation;

        WireAuthority(
            int root,
            int firstSubtype,
            int firstOperation
        ){
            this.root=root;
            this.firstSubtype=firstSubtype;
            this.firstOperation=firstOperation;
        }
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

    private G112PkRatingsNativeTabIntegrationTest(){}
}

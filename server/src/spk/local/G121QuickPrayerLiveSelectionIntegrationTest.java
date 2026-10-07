package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

public final class G121QuickPrayerLiveSelectionIntegrationTest {
    private static final String PLAYER="g121-player";
    private static final String POLICY=
        "LOCAL_LAB_POLICY_G121_QUICK_SELECTION";

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final QuickPrayerSelectionService selections;
        final QuickPrayerSelectionPresentation presentation;

        Bridge(WorldPlayer player){
            this.selections=
                new QuickPrayerSelectionService(
                    player,
                    POLICY
                );
            this.presentation=
                new QuickPrayerSelectionPresentation(
                    selections
                );
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

        @Override public boolean retireQuickPrayerRoot(){
            return presentation.close();
        }

        @Override public boolean openQuickPrayer(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            presentation.open(
                selections.snapshot().currentBook,
                writer
            );
            return true;
        }

        @Override public String handleQuickPrayerWidget(
            int widget,
            String tag
        )throws IOException{
            return presentation.handleWidget(widget);
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean select5001=false;
        boolean currentBookRoot=false;
        boolean normal17202=false;
        boolean curse17202=false;
        boolean confirm17241=false;
        boolean closedSelectorNoop=false;
        boolean unattachedCurseNoop=false;
        boolean interfaceCloseCancels=false;
        boolean competingRootRetires=false;
        boolean independentConfirmed=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            PLAYER
        );

        try{
            Bridge bridge=
                new Bridge(player);
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
                        new int[]{61,62,63,64}
                    )
                );

            select5001=
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET==5001&&
                QuickPrayerSelectionPresentation
                    .QUICK_ON_WIDGET==5000&&
                QuickPrayerSelectionPresentation
                    .QUICK_OFF_WIDGET==4999&&
                QuickPrayerSelectionPresentation
                    .NORMAL_ROOT==20000&&
                QuickPrayerSelectionPresentation
                    .CURSES_ROOT==22000;

            require(
                select5001,
                "exact quick-orb selector contract"
            );

            QuickPrayerSelectionService.Snapshot
                beforeClosed=
                    bridge.selections.snapshot();

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR,
                writer,
                "[g121-closed-selector] "
            );

            QuickPrayerSelectionService.Snapshot
                afterClosed=
                    bridge.selections.snapshot();

            closedSelectorNoop=
                !afterClosed.editing&&
                afterClosed.revision(
                    QuickPrayerSelectionService.Book.NORMAL
                )==
                    beforeClosed.revision(
                        QuickPrayerSelectionService.Book.NORMAL
                    )&&
                afterClosed.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL
                ).equals(
                    beforeClosed.confirmed(
                        QuickPrayerSelectionService.Book.NORMAL
                    )
                );

            require(
                closedSelectorNoop,
                "closed selector action mutated state"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET,
                writer,
                "[g121-open-normal] "
            );

            currentBookRoot=
                bridge.presentation.open()&&
                bridge.presentation.activeBook()==
                    QuickPrayerSelectionService.Book.NORMAL&&
                bridge.selections.snapshot().editingBook==
                    QuickPrayerSelectionService.Book.NORMAL;

            require(
                currentBookRoot,
                "5001 did not open current NORMAL book"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR,
                writer,
                "[g121-normal-17202] "
            );

            normal17202=
                bridge.selections.snapshot()
                    .draftSelection
                    .equals(
                        Collections.singletonList(
                            "normal:thick_skin"
                        )
                    );

            require(
                normal17202,
                "17202 did not resolve NORMAL context"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .CONFIRM_WIDGET,
                writer,
                "[g121-normal-confirm] "
            );

            QuickPrayerSelectionService.Snapshot
                normalConfirmed=
                    bridge.selections.snapshot();

            confirm17241=
                !bridge.presentation.open()&&
                !normalConfirmed.editing&&
                normalConfirmed.revision(
                    QuickPrayerSelectionService.Book.NORMAL
                )==1L&&
                normalConfirmed.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL
                ).equals(
                    Collections.singletonList(
                        "normal:thick_skin"
                    )
                );

            require(
                confirm17241,
                "17241 did not confirm NORMAL selection"
            );

            player.prayers().switchBook(
                "curses",
                writer
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET,
                writer,
                "[g121-open-curses] "
            );

            require(
                bridge.presentation.open()&&
                bridge.presentation.activeBook()==
                    QuickPrayerSelectionService.Book.CURSES,
                "5001 did not open current CURSES book"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR,
                writer,
                "[g121-curse-17202] "
            );

            curse17202=
                bridge.selections.snapshot()
                    .draftSelection
                    .equals(
                        Collections.singletonList(
                            "curses:protect_item"
                        )
                    );

            require(
                curse17202,
                "17202 did not resolve CURSES context"
            );

            QuickPrayerSelectionService.Snapshot
                beforeUnattached=
                    bridge.selections.snapshot();

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .LAST_CURSE_SELECTOR+1,
                writer,
                "[g121-unattached-curse] "
            );

            QuickPrayerSelectionService.Snapshot
                afterUnattached=
                    bridge.selections.snapshot();

            unattachedCurseNoop=
                afterUnattached.editing&&
                afterUnattached.draftSelection.equals(
                    beforeUnattached.draftSelection
                )&&
                afterUnattached.revision(
                    QuickPrayerSelectionService.Book.CURSES
                )==
                    beforeUnattached.revision(
                        QuickPrayerSelectionService.Book.CURSES
                    );

            require(
                unattachedCurseNoop,
                "unattached CURSES selector mutated draft"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g121-interface-close] "
            );

            QuickPrayerSelectionService.Snapshot
                afterInterfaceClose=
                    bridge.selections.snapshot();

            interfaceCloseCancels=
                !bridge.presentation.open()&&
                !afterInterfaceClose.editing&&
                afterInterfaceClose.confirmed(
                    QuickPrayerSelectionService.Book.CURSES
                ).isEmpty()&&
                afterInterfaceClose.revision(
                    QuickPrayerSelectionService.Book.CURSES
                )==0L;

            require(
                interfaceCloseCancels,
                "interface close did not cancel CURSES draft"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET,
                writer,
                "[g121-reopen-curses] "
            );
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR,
                writer,
                "[g121-confirmed-curse-17202] "
            );
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .CONFIRM_WIDGET,
                writer,
                "[g121-curse-confirm] "
            );

            QuickPrayerSelectionService.Snapshot
                bothConfirmed=
                    bridge.selections.snapshot();

            independentConfirmed=
                bothConfirmed.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL
                ).equals(
                    Collections.singletonList(
                        "normal:thick_skin"
                    )
                )&&
                bothConfirmed.confirmed(
                    QuickPrayerSelectionService.Book.CURSES
                ).equals(
                    Collections.singletonList(
                        "curses:protect_item"
                    )
                )&&
                bothConfirmed.revision(
                    QuickPrayerSelectionService.Book.NORMAL
                )==1L&&
                bothConfirmed.revision(
                    QuickPrayerSelectionService.Book.CURSES
                )==1L;

            require(
                independentConfirmed,
                "confirmed book selections were not independent"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET,
                writer,
                "[g121-competing-open] "
            );
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR+1,
                writer,
                "[g121-competing-draft] "
            );

            require(
                bridge.presentation.open()&&
                bridge.selections.snapshot().editing,
                "competing-root precondition"
            );

            ui.replaceMonsterSpawnerWithDuelRoot(
                ()->"DUEL_ROOT_OPENED"
            );

            QuickPrayerSelectionService.Snapshot
                afterCompeting=
                    bridge.selections.snapshot();

            competingRootRetires=
                !bridge.presentation.open()&&
                !afterCompeting.editing&&
                afterCompeting.confirmed(
                    QuickPrayerSelectionService.Book.CURSES
                ).equals(
                    Collections.singletonList(
                        "curses:protect_item"
                    )
                )&&
                afterCompeting.revision(
                    QuickPrayerSelectionService.Book.CURSES
                )==1L;

            require(
                competingRootRetires,
                "competing root did not retire quick selector draft"
            );

            System.out.println(
                "G121_QUICK_PRAYER_LIVE_SELECTION_PASS"+
                " select5001="+select5001+
                " currentBookRoot="+currentBookRoot+
                " normal17202="+normal17202+
                " curse17202="+curse17202+
                " confirm17241="+confirm17241+
                " closedSelectorNoop="+closedSelectorNoop+
                " unattachedCurseNoop="+unattachedCurseNoop+
                " interfaceCloseCancels="+interfaceCloseCancels+
                " competingRootRetires="+competingRootRetires+
                " independentConfirmed="+independentConfirmed+
                " activation4999Claim=false"+
                " activation5000Claim=false"+
                " selectedConfigClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkRootPolicyClaim=false"
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

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G121QuickPrayerLiveSelectionIntegrationTest(){}
}

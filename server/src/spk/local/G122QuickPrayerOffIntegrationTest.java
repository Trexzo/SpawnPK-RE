package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class G122QuickPrayerOffIntegrationTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_G121_QUICK_SELECTION";

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final WorldPlayer player;
        final QuickPrayerSelectionService selections;
        final QuickPrayerSelectionPresentation presentation;
        boolean exactOffToken;

        Bridge(WorldPlayer player){
            this.player=player;
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
            ServerPacketWriter writer,
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

        @Override public String handleQuickPrayerOff(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            String result=
                player.prayers().deactivateAll(
                    writer
                );

            QuickPrayerSelectionPresentation
                .publishActive(
                    writer,
                    false
                );

            exactOffToken=true;
            return result;
        }

        @Override public String handleQuickPrayerWidget(
            int widget,
            String tag
        )throws IOException{
            return presentation.handleWidget(
                widget
            );
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean off4999=false;
        boolean exactOffToken=false;
        boolean normalBookCleared=false;
        boolean cursesBookCleared=false;
        boolean otherBookPreserved=false;
        boolean selectionDraftPreserved=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g122-player"
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
                        new int[]{71,72,73,74}
                    )
                );

            off4999=
                QuickPrayerSelectionPresentation
                    .QUICK_OFF_WIDGET==4999&&
                QuickPrayerSelectionPresentation
                    .QUICK_ON_WIDGET==5000;

            require(
                off4999,
                "exact Quick OFF/ON widget contract"
            );

            // Confirmed quick-selection state is intentionally independent
            // from whether the current book is presently active.
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET,
                writer,
                "[g122-select-open] "
            );
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR,
                writer,
                "[g122-select-toggle] "
            );
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .CONFIRM_WIDGET,
                writer,
                "[g122-select-confirm] "
            );

            QuickPrayerSelectionService.Snapshot
                confirmedBeforeOff=
                    bridge.selections.snapshot();

            require(
                confirmedBeforeOff.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL
                ).contains(
                    "normal:thick_skin"
                ),
                "normal quick selection precondition"
            );

            PrayerDefinitionRepository.Def
                thickSkin=
                    PrayerDefinitionRepository
                        .byWidget(5609);
            PrayerDefinitionRepository.Def
                burstStrength=
                    PrayerDefinitionRepository
                        .byWidget(5610);

            player.prayers().click(
                thickSkin,
                player.playerState(),
                writer
            );
            player.prayers().click(
                burstStrength,
                player.playerState(),
                writer
            );

            require(
                player.prayers()
                    .activeWidget(5609)&&
                player.prayers()
                    .activeWidget(5610),
                "normal activation precondition"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .QUICK_OFF_WIDGET,
                writer,
                "[g122-normal-off] "
            );

            normalBookCleared=
                !player.prayers()
                    .activeWidget(5609)&&
                !player.prayers()
                    .activeWidget(5610)&&
                player.prayers().activeCount()==0;

            exactOffToken=bridge.exactOffToken;

            require(
                normalBookCleared,
                "4999 did not clear current NORMAL book"
            );
            require(
                exactOffToken,
                "4999 did not publish exact OFF token"
            );

            QuickPrayerSelectionService.Snapshot
                selectionAfterNormalOff=
                    bridge.selections.snapshot();

            require(
                selectionAfterNormalOff.confirmed(
                    QuickPrayerSelectionService.Book.NORMAL
                ).equals(
                    confirmedBeforeOff.confirmed(
                        QuickPrayerSelectionService.Book.NORMAL
                    )
                )&&
                selectionAfterNormalOff.revision(
                    QuickPrayerSelectionService.Book.NORMAL
                )==
                    confirmedBeforeOff.revision(
                        QuickPrayerSelectionService.Book.NORMAL
                    ),
                "OFF mutated confirmed quick selection"
            );

            // Keep a NORMAL prayer active while CURSES is the current book.
            player.prayers().click(
                thickSkin,
                player.playerState(),
                writer
            );

            player.prayers().switchBook(
                "curses",
                writer
            );

            PrayerDefinitionRepository.Def
                curseProtectItem=
                    PrayerDefinitionRepository
                        .byWidget(22503);
            PrayerDefinitionRepository.Def
                sapWarrior=
                    PrayerDefinitionRepository
                        .byWidget(22505);

            player.prayers().click(
                curseProtectItem,
                player.playerState(),
                writer
            );
            player.prayers().click(
                sapWarrior,
                player.playerState(),
                writer
            );

            require(
                player.prayers()
                    .activeWidget(5609)&&
                player.prayers()
                    .activeWidget(22503)&&
                player.prayers()
                    .activeWidget(22505),
                "cross-book activation precondition"
            );

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .QUICK_OFF_WIDGET,
                writer,
                "[g122-curses-off] "
            );

            cursesBookCleared=
                !player.prayers()
                    .activeWidget(22503)&&
                !player.prayers()
                    .activeWidget(22505);

            otherBookPreserved=
                player.prayers()
                    .activeWidget(5609);

            require(
                cursesBookCleared,
                "4999 did not clear current CURSES book"
            );
            require(
                otherBookPreserved,
                "4999 cleared non-current NORMAL state"
            );

            // OFF must not turn selection editing into an activation-policy
            // operation. Keep the draft and owned selector root intact.
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .SELECT_WIDGET,
                writer,
                "[g122-draft-open] "
            );
            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .FIRST_SELECTOR,
                writer,
                "[g122-draft-toggle] "
            );

            QuickPrayerSelectionService.Snapshot
                draftBeforeOff=
                    bridge.selections.snapshot();

            List<String> expectedDraft=
                draftBeforeOff.draftSelection;

            ui.handleWidget(
                QuickPrayerSelectionPresentation
                    .QUICK_OFF_WIDGET,
                writer,
                "[g122-off-during-draft] "
            );

            QuickPrayerSelectionService.Snapshot
                draftAfterOff=
                    bridge.selections.snapshot();

            selectionDraftPreserved=
                bridge.presentation.open()&&
                draftAfterOff.editing&&
                draftAfterOff.editingBook==
                    QuickPrayerSelectionService.Book.CURSES&&
                draftAfterOff.draftSelection
                    .equals(expectedDraft)&&
                draftAfterOff.revision(
                    QuickPrayerSelectionService.Book.CURSES
                )==
                    draftBeforeOff.revision(
                        QuickPrayerSelectionService.Book.CURSES
                    );

            require(
                selectionDraftPreserved,
                "4999 mutated or retired selection draft"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g122-cleanup] "
            );

            System.out.println(
                "G122_QUICK_PRAYER_OFF_PASS"+
                " off4999="+off4999+
                " exactOffToken="+exactOffToken+
                " normalBookCleared="+
                    normalBookCleared+
                " cursesBookCleared="+
                    cursesBookCleared+
                " otherBookPreserved="+
                    otherBookPreserved+
                " selectionDraftPreserved="+
                    selectionDraftPreserved+
                " quickOn5000Claim=false"+
                " activationOrderClaim=false"+
                " conflictClaim=false"+
                " drainClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkOffScopeClaim=false"
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

    private G122QuickPrayerOffIntegrationTest(){}
}

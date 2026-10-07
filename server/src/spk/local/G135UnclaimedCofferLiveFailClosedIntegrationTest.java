package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G135UnclaimedCofferLiveFailClosedIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalUnclaimedRewardCofferUiHandler coffer;
        int actionCalls;
        LocalUnclaimedRewardCofferUiHandler.Result lastResult;

        Bridge(WorldPlayer player){
            this.coffer=
                new LocalUnclaimedRewardCofferUiHandler(
                    player
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

        @Override public boolean retireUnclaimedCofferRoot(){
            return coffer.close();
        }

        @Override public boolean openUnclaimedCoffer(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            coffer.open(writer);
            return true;
        }

        @Override public LocalUnclaimedRewardCofferUiHandler.Result
            handleUnclaimedCofferBulk(
                UnclaimedRewardCofferPresentation.BulkIntent intent,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                coffer.handleBulk(intent);
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root42100=false;
        boolean container42101=false;
        boolean emptyCapacity70=false;
        boolean bulkInventory42104Disabled=false;
        boolean bulkBank42108Disabled=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean semanticCofferCreated=false;
        boolean emptyItemActionRejected=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g135-player"
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
                        new int[]{121,122,123,124}
                    )
                );

            customCommand=
                LocalCommandDispatcher
                    .isUnclaimedCofferRoute(
                        new String[]{"coffer"}
                    )&&
                LocalCommandDispatcher
                    .isUnclaimedCofferRoute(
                        new String[]{"unclaimed"}
                    )&&
                !LocalCommandDispatcher
                    .isUnclaimedCofferRoute(
                        new String[]{"coffer","invent"}
                    );

            require(
                customCommand,
                "Unclaimed Coffer LocalLab routes"
            );

            String opened=
                ui.replaceMonsterSpawnerWithUnclaimedCofferRoot(
                    ()->{
                        bridge.openUnclaimedCoffer(
                            writer,
                            "[g135-open] "
                        );
                        return "UNCLAIMED_COFFER_ROOT_OPENED";
                    }
                );

            UnclaimedRewardCofferPresentation.Projection projection=
                bridge.coffer.projection();

            root42100=
                "UNCLAIMED_COFFER_ROOT_OPENED"
                    .equals(opened)&&
                UnclaimedRewardCofferPresentation
                    .ROOT==42100&&
                bridge.coffer.isOpen()&&
                wire.size()>0;

            container42101=
                UnclaimedRewardCofferPresentation
                    .CONTAINER_WIDGET==42101&&
                projection!=null;

            emptyCapacity70=
                UnclaimedRewardCofferPresentation
                    .CAPACITY==70&&
                projection!=null&&
                projection.slots.isEmpty()&&
                projection.containerBody()
                    .length>0;

            require(
                root42100&&
                container42101&&
                emptyCapacity70,
                "Unclaimed Coffer exact empty projection"
            );

            try{
                UnclaimedRewardCofferPresentation
                    .resolveItemAction(
                        new ItemContainerAction(
                            145,
                            42101,
                            0,
                            995,
                            0,
                            "remove-one"
                        ),
                        projection
                    );
            }catch(IllegalStateException expected){
                emptyItemActionRejected=true;
            }

            require(
                emptyItemActionRejected,
                "empty Coffer accepted stale per-item action"
            );

            ui.handleWidget(
                UnclaimedRewardCofferPresentation
                    .BULK_INVENTORY_WIDGET,
                writer,
                "[g135-inventory] "
            );

            bulkInventory42104Disabled=
                bridge.actionCalls==1&&
                bridge.lastResult!=null&&
                bridge.lastResult.destination==
                    UnclaimedRewardCofferService
                        .Destination.INVENTORY&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                bulkInventory42104Disabled,
                "Coffer Inventory bulk action did not fail closed"
            );

            ui.handleWidget(
                UnclaimedRewardCofferPresentation
                    .BULK_BANK_WIDGET,
                writer,
                "[g135-bank] "
            );

            bulkBank42108Disabled=
                bridge.actionCalls==2&&
                bridge.lastResult.destination==
                    UnclaimedRewardCofferService
                        .Destination.BANK&&
                !bridge.lastResult.succeeded&&
                "DISABLED_NO_GAMEPLAY_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            require(
                bulkBank42108Disabled,
                "Coffer Bank bulk action did not fail closed"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g135-close] "
            );

            interfaceCloseRetires=
                !bridge.coffer.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Coffer"
            );

            int callsBeforeClosed=
                bridge.actionCalls;

            ui.handleWidget(
                UnclaimedRewardCofferPresentation
                    .BULK_INVENTORY_WIDGET,
                writer,
                "[g135-closed] "
            );

            closedUiNoop=
                bridge.actionCalls==
                    callsBeforeClosed;

            require(
                closedUiNoop,
                "closed Coffer bulk action escaped root fence"
            );

            ui.replaceMonsterSpawnerWithUnclaimedCofferRoot(
                ()->{
                    bridge.openUnclaimedCoffer(
                        writer,
                        "[g135-reopen] "
                    );
                    return "UNCLAIMED_COFFER_ROOT_OPENED";
                }
            );

            require(
                bridge.coffer.isOpen(),
                "Coffer reopen precondition"
            );

            ui.replaceMonsterSpawnerWithLotteryRoot(
                ()->"LOTTERY_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.coffer.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Coffer"
            );

            for(Field field:
                    LocalUnclaimedRewardCofferUiHandler
                        .class
                        .getDeclaredFields())
                if(field.getType()==
                        UnclaimedRewardCofferService.class)
                    semanticCofferCreated=true;

            require(
                !semanticCofferCreated,
                "live Coffer shell created semantic reward service"
            );

            System.out.println(
                "G135_UNCLAIMED_COFFER_LIVE_FAIL_CLOSED_PASS"+
                " customCommand="+customCommand+
                " root42100="+root42100+
                " container42101="+container42101+
                " emptyCapacity70="+emptyCapacity70+
                " bulkInventory42104Disabled="+
                    bulkInventory42104Disabled+
                " bulkBank42108Disabled="+
                    bulkBank42108Disabled+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " semanticCofferCreated="+
                    semanticCofferCreated+
                " emptyItemActionRejected="+
                    emptyItemActionRejected+
                " perItemMutationClaim=false"+
                " persistenceClaim=false"+
                " expiryClaim=false"+
                " overflowClaim=false"+
                " originalNavigationClaim=false"
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

    private G135UnclaimedCofferLiveFailClosedIntegrationTest(){}
}

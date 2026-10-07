package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G1311ItemEnchantmentCategoryShellIntegrationTest {
    private static final int[] SEED={181,182,183,184};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalItemEnchantmentUiHandler enchantment=
            new LocalItemEnchantmentUiHandler();
        int categoryCalls;
        LocalItemEnchantmentUiHandler.Result lastResult;

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

        @Override public boolean retireItemEnchantmentRoot(){
            return enchantment.close();
        }

        @Override public boolean openItemEnchantmentCategories(
            ServerPacketWriter writer,
            String tag
        )throws IOException{
            enchantment.openCategories(writer);
            return true;
        }

        @Override public LocalItemEnchantmentUiHandler.Result
            handleItemEnchantmentCategory(
                ItemEnchantmentPresentation.Input input,
                String tag
            )throws IOException{
            categoryCalls++;
            lastResult=
                enchantment.handleCategory(input);
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean categoryRoot31243=false;
        boolean categories7=false;
        boolean categoryWidgetsExact=false;
        boolean categoryClicksDisabled=false;
        boolean mainRootNotOpened=false;
        boolean closedUiNoop=false;
        boolean interfaceCloseRetires=false;
        boolean competingRootRetires=false;
        boolean enchantmentServiceCreated=false;
        boolean catalogCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g1311-player"
        );

        try{
            customCommand=
                LocalCommandDispatcher
                    .isItemEnchantmentRoute(
                        new String[]{"enchantments"}
                    )&&
                LocalCommandDispatcher
                    .isItemEnchantmentRoute(
                        new String[]{"enchanting"}
                    )&&
                !LocalCommandDispatcher
                    .isItemEnchantmentRoute(
                        new String[]{"enchanting","invent"}
                    );

            require(
                customCommand,
                "Item Enchantment LocalLab route"
            );

            ItemEnchantmentService.Category[] cats=
                ItemEnchantmentService
                    .Category
                    .values();

            int[] widgets={
                50327,50329,50331,50333,
                50335,50337,50339
            };

            categories7=
                cats.length==7&&
                widgets.length==7;

            categoryWidgetsExact=
                categories7;

            for(int i=0;i<widgets.length;i++){
                ItemEnchantmentPresentation.Input input=
                    ItemEnchantmentPresentation
                        .resolveWidget(
                            ItemEnchantmentPresentation
                                .CATEGORY_ROOT,
                            widgets[i]
                        );

                categoryWidgetsExact&=
                    ItemEnchantmentPresentation
                        .categoryWidget(
                            cats[i]
                        )==
                        widgets[i]&&
                    input!=null&&
                    input.kind==
                        ItemEnchantmentPresentation
                            .InputKind.SELECT_CATEGORY&&
                    input.category==cats[i];
            }

            require(
                categoryWidgetsExact,
                "Item Enchantment exact category map"
            );

            Bridge bridge=
                new Bridge();
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

            String opened=
                ui.replaceMonsterSpawnerWithItemEnchantmentRoot(
                    ()->{
                        bridge.openItemEnchantmentCategories(
                            writer,
                            "[g1311-open] "
                        );
                        return "ITEM_ENCHANTMENT_CATEGORY_ROOT_OPENED";
                    }
                );

            byte[] rootWire=
                wire.toByteArray();
            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );

            int opcode=
                ((rootWire[0]&255)-
                    decode.nextInt())&
                    255;
            int root=
                ((rootWire[1]&255)<<8)|
                (rootWire[2]&255);

            categoryRoot31243=
                "ITEM_ENCHANTMENT_CATEGORY_ROOT_OPENED"
                    .equals(opened)&&
                bridge.enchantment.isOpen()&&
                ItemEnchantmentPresentation
                    .CATEGORY_ROOT==31243&&
                opcode==97&&
                root==31243;

            mainRootNotOpened=
                ItemEnchantmentPresentation
                    .MAIN_ROOT==31244&&
                rootWire.length==3&&
                root!=
                    ItemEnchantmentPresentation
                        .MAIN_ROOT;

            require(
                categoryRoot31243&&
                mainRootNotOpened,
                "Item Enchantment category-only root"
            );

            categoryClicksDisabled=true;

            for(int i=0;i<widgets.length;i++){
                int before=
                    bridge.categoryCalls;

                ui.handleWidget(
                    widgets[i],
                    writer,
                    "[g1311-category-"+i+"] "
                );

                categoryClicksDisabled&=
                    bridge.categoryCalls==
                        before+1&&
                    bridge.lastResult!=null&&
                    bridge.lastResult.input.category==
                        cats[i]&&
                    "DISABLED_NO_CATALOG_AUTHORITY"
                        .equals(
                            bridge.lastResult.status
                        )&&
                    !bridge.lastResult.succeeded&&
                    bridge.enchantment.isOpen();
            }

            require(
                categoryClicksDisabled,
                "Item Enchantment category click escaped fail-closed shell"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g1311-interface-close] "
            );

            interfaceCloseRetires=
                !bridge.enchantment.isOpen();

            require(
                interfaceCloseRetires,
                "interface close did not retire Item Enchantment"
            );

            int closedCalls=
                bridge.categoryCalls;

            ui.handleWidget(
                widgets[0],
                writer,
                "[g1311-closed-category] "
            );

            closedUiNoop=
                bridge.categoryCalls==
                    closedCalls;

            require(
                closedUiNoop,
                "closed category widget escaped root gate"
            );

            ItemEnchantmentPresentation.Input closedInput=
                ItemEnchantmentPresentation
                    .resolveWidget(
                        ItemEnchantmentPresentation
                            .CATEGORY_ROOT,
                        widgets[0]
                    );

            LocalItemEnchantmentUiHandler.Result
                directClosed=
                    bridge.enchantment
                        .handleCategory(
                            closedInput
                        );

            closedUiNoop&=
                "CLOSED_UI_NOOP".equals(
                    directClosed.status
                )&&
                !directClosed.succeeded;

            require(
                closedUiNoop,
                "closed Item Enchantment handler did not no-op"
            );

            ui.replaceMonsterSpawnerWithItemEnchantmentRoot(
                ()->{
                    bridge.openItemEnchantmentCategories(
                        writer,
                        "[g1311-reopen] "
                    );
                    return "ITEM_ENCHANTMENT_CATEGORY_ROOT_OPENED";
                }
            );

            require(
                bridge.enchantment.isOpen(),
                "competing-root precondition"
            );

            ui.replaceMonsterSpawnerWithLegendaryPetFusionRoot(
                ()->"LEGENDARY_PET_FUSION_ROOT_OPENED"
            );

            competingRootRetires=
                !bridge.enchantment.isOpen();

            require(
                competingRootRetires,
                "competing root did not retire Item Enchantment"
            );

            for(Field field:
                    LocalItemEnchantmentUiHandler
                        .class
                        .getDeclaredFields()){
                if(field.getType()==
                        ItemEnchantmentService.class)
                    enchantmentServiceCreated=true;

                if(field.getType()==
                        RecipeCatalog.class)
                    catalogCreated=true;
            }

            require(
                !enchantmentServiceCreated&&
                !catalogCreated,
                "Item Enchantment shell created catalog/service state"
            );

            System.out.println(
                "G1311_ITEM_ENCHANTMENT_CATEGORY_SHELL_PASS"+
                " customCommand="+customCommand+
                " categoryRoot31243="+
                    categoryRoot31243+
                " categories7="+categories7+
                " categoryWidgetsExact="+
                    categoryWidgetsExact+
                " categoryClicksDisabled="+
                    categoryClicksDisabled+
                " mainRootNotOpened="+
                    mainRootNotOpened+
                " closedUiNoop="+closedUiNoop+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " competingRootRetires="+
                    competingRootRetires+
                " enchantmentServiceCreated="+
                    enchantmentServiceCreated+
                " catalogCreated="+catalogCreated+
                " recipeClaim=false"+
                " searchTransportClaim=false"+
                " chanceClaim=false"+
                " rngClaim=false"+
                " persistenceClaim=false"+
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

    private G1311ItemEnchantmentCategoryShellIntegrationTest(){}
}

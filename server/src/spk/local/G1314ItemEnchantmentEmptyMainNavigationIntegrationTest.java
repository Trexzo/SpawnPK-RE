package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class G1314ItemEnchantmentEmptyMainNavigationIntegrationTest {
    private static final int[] SEED={211,212,213,214};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalItemEnchantmentUiHandler enchantment=
            new LocalItemEnchantmentUiHandler();
        int actionCalls;
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
            handleItemEnchantmentInput(
                ItemEnchantmentPresentation.Input input,
                ServerPacketWriter writer,
                String tag
            )throws IOException{
            actionCalls++;
            lastResult=
                enchantment.handle(
                    input,
                    writer
                );
            return lastResult;
        }

        @Override public void requestLogout(){}
    }

    private static final class TextPacket {
        final int target;
        final String text;

        TextPacket(
            int target,
            String text
        ){
            this.target=target;
            this.text=text;
        }
    }

    public static void main(String[] args)throws Exception{
        boolean categoryRoot31243=false;
        boolean categoryToMain=false;
        boolean mainRoot31244=false;
        boolean emptyRows16=false;
        boolean truthfulDetailText=false;
        boolean idleResult0=false;
        boolean rowsDisabled=false;
        boolean attemptDisabled=false;
        boolean searchDisabled=false;
        boolean back50319=false;
        boolean backToCategories=false;
        boolean wrongSurfaceNoop=false;
        boolean serviceCreated=false;
        boolean catalogCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g1314-player"
        );

        try{
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
                            "[g1314-open] "
                        );
                        return "ITEM_ENCHANTMENT_CATEGORY_ROOT_OPENED";
                    }
                );

            byte[] initial=
                wire.toByteArray();
            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );
            int initialOffset=0;
            int initialOpcode=
                ((initial[initialOffset++]&255)-
                    decode.nextInt())&
                    255;
            int initialRoot=
                ((initial[initialOffset++]&255)<<8)|
                (initial[initialOffset++]&255);

            categoryRoot31243=
                "ITEM_ENCHANTMENT_CATEGORY_ROOT_OPENED"
                    .equals(opened)&&
                initialOpcode==97&&
                initialRoot==
                    ItemEnchantmentPresentation
                        .CATEGORY_ROOT&&
                initialRoot==31243&&
                bridge.enchantment.surface()==
                    LocalItemEnchantmentUiHandler
                        .Surface.CATEGORIES;

            require(
                categoryRoot31243,
                "Item Enchantment category root"
            );

            int beforeMain=
                wire.size();

            ui.handleWidget(
                ItemEnchantmentPresentation
                    .categoryWidget(
                        ItemEnchantmentService
                            .Category.ARMOR
                    ),
                writer,
                "[g1314-category] "
            );

            categoryToMain=
                bridge.actionCalls==1&&
                bridge.lastResult!=null&&
                bridge.lastResult.input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.SELECT_CATEGORY&&
                bridge.lastResult.input.category==
                    ItemEnchantmentService
                        .Category.ARMOR&&
                "NAVIGATED_TO_EMPTY_MAIN"
                    .equals(
                        bridge.lastResult.status
                    )&&
                bridge.lastResult.succeeded&&
                bridge.enchantment.surface()==
                    LocalItemEnchantmentUiHandler
                        .Surface.MAIN&&
                bridge.enchantment.selectedCategory()==
                    ItemEnchantmentService
                        .Category.ARMOR;

            require(
                categoryToMain,
                "category did not navigate to empty main"
            );

            byte[] all=
                wire.toByteArray();
            byte[] mainBytes=
                Arrays.copyOfRange(
                    all,
                    beforeMain,
                    all.length
                );
            int offset=0;

            int mainOpcode=
                ((mainBytes[offset++]&255)-
                    decode.nextInt())&
                    255;
            int mainRoot=
                ((mainBytes[offset++]&255)<<8)|
                (mainBytes[offset++]&255);

            mainRoot31244=
                mainOpcode==97&&
                mainRoot==
                    ItemEnchantmentPresentation
                        .MAIN_ROOT&&
                mainRoot==31244;

            require(
                mainRoot31244,
                "Item Enchantment main root"
            );

            emptyRows16=true;

            for(int i=0;
                i<ItemEnchantmentPresentation.ROWS;
                i++){
                TextPacket packet=
                    readText126(
                        mainBytes,
                        decode,
                        new int[]{offset}
                    );

                /*
                 * readText126 receives an offset holder; recalculate offset
                 * deterministically from packet framing below.
                 */
                int opcode=
                    ((mainBytes[offset++]&255)-
                        decodeBackOneUnsupported())&
                        255;
            }

            /*
             * Re-decode the main publication cleanly. This keeps the helper
             * simple and also proves the full packet sequence from scratch.
             */
            decode=
                new IsaacCipher(
                    SEED.clone()
                );
            decode.nextInt();
            offset=0;
            require(
                (((mainBytes[offset++]&255)-
                    decode.nextInt())&255)==97,
                "main root opcode replay"
            );
            offset+=2;

            for(int i=0;
                i<ItemEnchantmentPresentation.ROWS;
                i++){
                int[] holder={offset};
                TextPacket packet=
                    readText126(
                        mainBytes,
                        decode,
                        holder
                    );
                offset=holder[0];

                emptyRows16&=
                    packet.target==
                        ItemEnchantmentPresentation
                            .rowWidget(i)&&
                    packet.text.isEmpty();
            }

            require(
                emptyRows16,
                "Item Enchantment empty row projection"
            );

            int[] holder={offset};
            TextPacket categoryText=
                readText126(
                    mainBytes,
                    decode,
                    holder
                );
            offset=holder[0];

            holder[0]=offset;
            TextPacket chanceText=
                readText126(
                    mainBytes,
                    decode,
                    holder
                );
            offset=holder[0];

            holder[0]=offset;
            TextPacket promptText=
                readText126(
                    mainBytes,
                    decode,
                    holder
                );
            offset=holder[0];

            holder[0]=offset;
            TextPacket ingredientText=
                readText126(
                    mainBytes,
                    decode,
                    holder
                );
            offset=holder[0];

            truthfulDetailText=
                categoryText.target==
                    ItemEnchantmentPresentation
                        .CATEGORY_TEXT_WIDGET&&
                "Armor - no LocalLab enchantments configured"
                    .equals(
                        categoryText.text
                    )&&
                chanceText.target==
                    ItemEnchantmentPresentation
                        .SUCCESS_CHANCE_TEXT_WIDGET&&
                LocalItemEnchantmentUiHandler
                    .SUCCESS_CHANCE_TEXT
                    .equals(
                        chanceText.text
                    )&&
                promptText.target==
                    ItemEnchantmentPresentation
                        .CATEGORY_PROMPT_TEXT_WIDGET&&
                LocalItemEnchantmentUiHandler
                    .PROMPT_TEXT
                    .equals(
                        promptText.text
                    )&&
                ingredientText.target==
                    ItemEnchantmentPresentation
                        .INGREDIENT_HEADING_TEXT_WIDGET&&
                LocalItemEnchantmentUiHandler
                    .INGREDIENT_HEADING
                    .equals(
                        ingredientText.text
                    );

            require(
                truthfulDetailText,
                "Item Enchantment truthful detail text"
            );

            holder[0]=offset;
            TextPacket result=
                readText126(
                    mainBytes,
                    decode,
                    holder
                );
            offset=holder[0];

            idleResult0=
                result.target==
                    ItemEnchantmentPresentation
                        .RESULT_TARGET&&
                "0".equals(
                    result.text
                )&&
                offset==mainBytes.length;

            require(
                idleResult0,
                "Item Enchantment idle result state"
            );

            int beforeRows=
                bridge.actionCalls;

            ui.handleWidget(
                ItemEnchantmentPresentation
                    .rowWidget(0),
                writer,
                "[g1314-row0] "
            );
            boolean row0=
                bridge.actionCalls==
                    beforeRows+1&&
                bridge.lastResult!=null&&
                bridge.lastResult.input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.SELECT_ROW&&
                bridge.lastResult.input.rowIndex==0&&
                "DISABLED_NO_CATALOG_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    )&&
                !bridge.lastResult.succeeded;

            int beforeLastRow=
                bridge.actionCalls;

            ui.handleWidget(
                ItemEnchantmentPresentation
                    .rowWidget(15),
                writer,
                "[g1314-row15] "
            );
            boolean row15=
                bridge.actionCalls==
                    beforeLastRow+1&&
                bridge.lastResult.input.rowIndex==15&&
                "DISABLED_NO_CATALOG_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    );

            rowsDisabled=row0&&row15;

            require(
                rowsDisabled,
                "Item Enchantment rows did not fail closed"
            );

            int beforeAttempt=
                bridge.actionCalls;
            ui.handleWidget(
                ItemEnchantmentPresentation
                    .ATTEMPT_WIDGET,
                writer,
                "[g1314-attempt] "
            );
            attemptDisabled=
                bridge.actionCalls==
                    beforeAttempt+1&&
                bridge.lastResult.input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.ATTEMPT&&
                "DISABLED_NO_CATALOG_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    )&&
                !bridge.lastResult.succeeded;

            int beforeSearch=
                bridge.actionCalls;
            ui.handleWidget(
                ItemEnchantmentPresentation
                    .SEARCH_WIDGET,
                writer,
                "[g1314-search] "
            );
            searchDisabled=
                bridge.actionCalls==
                    beforeSearch+1&&
                bridge.lastResult.input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.SEARCH_REQUEST&&
                "DISABLED_NO_CATALOG_AUTHORITY"
                    .equals(
                        bridge.lastResult.status
                    )&&
                !bridge.lastResult.succeeded;

            require(
                attemptDisabled&&searchDisabled,
                "Item Enchantment main controls did not fail closed"
            );

            int beforeWrong=
                bridge.actionCalls;
            ui.handleWidget(
                ItemEnchantmentPresentation
                    .categoryWidget(
                        ItemEnchantmentService
                            .Category.WEAPONS
                    ),
                writer,
                "[g1314-wrong-surface-category] "
            );

            wrongSurfaceNoop=
                bridge.actionCalls==
                    beforeWrong;

            require(
                wrongSurfaceNoop,
                "category widget escaped MAIN surface"
            );

            int beforeBackBytes=
                wire.size();
            int beforeBackCalls=
                bridge.actionCalls;

            ui.handleWidget(
                ItemEnchantmentPresentation
                    .BACK_WIDGET,
                writer,
                "[g1314-back] "
            );

            back50319=
                ItemEnchantmentPresentation
                    .BACK_WIDGET==50319&&
                bridge.actionCalls==
                    beforeBackCalls+1&&
                bridge.lastResult.input.kind==
                    ItemEnchantmentPresentation
                        .InputKind.BACK&&
                "NAVIGATED_TO_CATEGORIES"
                    .equals(
                        bridge.lastResult.status
                    )&&
                bridge.lastResult.succeeded;

            byte[] afterBack=
                wire.toByteArray();
            byte[] backBytes=
                Arrays.copyOfRange(
                    afterBack,
                    beforeBackBytes,
                    afterBack.length
                );

            int backOffset=0;
            int backOpcode=
                ((backBytes[backOffset++]&255)-
                    decode.nextInt())&
                    255;
            int backRoot=
                ((backBytes[backOffset++]&255)<<8)|
                (backBytes[backOffset++]&255);

            backToCategories=
                back50319&&
                backOpcode==97&&
                backRoot==
                    ItemEnchantmentPresentation
                        .CATEGORY_ROOT&&
                backOffset==backBytes.length&&
                bridge.enchantment.surface()==
                    LocalItemEnchantmentUiHandler
                        .Surface.CATEGORIES&&
                bridge.enchantment.selectedCategory()==null;

            require(
                backToCategories,
                "Item Enchantment Back did not return categories"
            );

            int beforeWrongMain=
                bridge.actionCalls;
            ui.handleWidget(
                ItemEnchantmentPresentation
                    .ATTEMPT_WIDGET,
                writer,
                "[g1314-wrong-surface-attempt] "
            );
            wrongSurfaceNoop&=
                bridge.actionCalls==
                    beforeWrongMain;

            require(
                wrongSurfaceNoop,
                "main widget escaped CATEGORY surface"
            );

            ItemEnchantmentPresentation.Input itemIntent=
                ItemEnchantmentPresentation
                    .resolveItemOption1(
                        ItemEnchantmentPresentation
                            .MAIN_ROOT,
                        ItemEnchantmentPresentation
                            .ITEM_SELECTION_WIDGET,
                        0,
                        4151
                    );

            require(
                itemIntent!=null&&
                itemIntent.kind==
                    ItemEnchantmentPresentation
                        .InputKind.SELECT_ITEM,
                "exact item-option identity"
            );

            for(Field field:
                    LocalItemEnchantmentUiHandler
                        .class
                        .getDeclaredFields()){
                if(field.getType()==
                        ItemEnchantmentService.class)
                    serviceCreated=true;

                if(field.getType()==
                        RecipeCatalog.class)
                    catalogCreated=true;
            }

            require(
                !serviceCreated&&!catalogCreated,
                "empty Item Enchantment navigation created semantic state"
            );

            System.out.println(
                "G1314_ITEM_ENCHANTMENT_EMPTY_MAIN_NAV_PASS"+
                " categoryRoot31243="+
                    categoryRoot31243+
                " categoryToMain="+categoryToMain+
                " mainRoot31244="+mainRoot31244+
                " emptyRows16="+emptyRows16+
                " truthfulDetailText="+
                    truthfulDetailText+
                " idleResult0="+idleResult0+
                " rowsDisabled="+rowsDisabled+
                " attemptDisabled="+attemptDisabled+
                " searchDisabled="+searchDisabled+
                " back50319="+back50319+
                " backToCategories="+
                    backToCategories+
                " wrongSurfaceNoop="+
                    wrongSurfaceNoop+
                " itemOptionMutationClaim=false"+
                " serviceCreated="+serviceCreated+
                " catalogCreated="+catalogCreated+
                " searchTransportClaim=false"+
                " rngClaim=false"+
                " persistenceClaim=false"+
                " originalNavigationClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static TextPacket readText126(
        byte[] bytes,
        IsaacCipher decode,
        int[] offsetHolder
    ){
        int offset=offsetHolder[0];

        int opcode=
            ((bytes[offset++]&255)-
                decode.nextInt())&
                255;

        require(
            opcode==126,
            "expected packet 126 actual="+
            opcode
        );

        int length=
            ((bytes[offset++]&255)<<8)|
            (bytes[offset++]&255);

        require(
            length>=3&&
            offset+length<=bytes.length,
            "packet 126 length"
        );

        int bodyStart=offset;
        int newline=-1;

        for(int i=0;i<length-2;i++)
            if((bytes[offset+i]&255)==10){
                newline=i;
                break;
            }

        require(
            newline>=0,
            "packet 126 newline"
        );

        String text=
            new String(
                bytes,
                bodyStart,
                newline,
                StandardCharsets.ISO_8859_1
            );

        offset=
            bodyStart+length-2;

        int target=
            ((bytes[offset++]&255)<<8)|
            (((bytes[offset++]&255)-128)&255);

        offsetHolder[0]=
            bodyStart+length;

        return new TextPacket(
            target,
            text
        );
    }

    private static int decodeBackOneUnsupported(){
        throw new AssertionError(
            "unreachable pre-replay decoder path"
        );
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

    private G1314ItemEnchantmentEmptyMainNavigationIntegrationTest(){}
}

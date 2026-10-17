package spk.local;

import java.io.IOException;
import java.util.*;

/**
 * Exact-v308 Item Enchantment presentation/input adapter.
 *
 * Owns only proven client roots, widgets, exact result-state transport and
 * translation into the existing ItemEnchantmentService. Recipe contents,
 * search matching, inventory ownership, chance calculation, RNG and settlement
 * remain outside this adapter.
 */
final class ItemEnchantmentPresentation {
    static final int MAIN_ROOT=31244;
    static final int CATEGORY_ROOT=31243;

    static final int FIRST_ROW_WIDGET=49970;
    static final int LAST_ROW_WIDGET=49985;
    static final int ROWS=
        LAST_ROW_WIDGET-FIRST_ROW_WIDGET+1;

    static final int ATTEMPT_WIDGET=49991;
    static final int ROW_SCROLL_ROOT=49992;
    static final int INGREDIENT_SCROLL_ROOT=49994;

    static final int CATEGORY_TEXT_WIDGET=50245;
    static final int ITEM_SCROLL_ROOT=50244;
    static final int ITEM_SELECTION_WIDGET=50253;
    static final int SUCCESS_CHANCE_TEXT_WIDGET=50249;
    static final int CATEGORY_PROMPT_TEXT_WIDGET=50250;
    static final int INGREDIENT_HEADING_TEXT_WIDGET=50252;

    static final int SEARCH_WIDGET=50314;
    static final int BACK_WIDGET=50319;

    static final int RESULT_TARGET=38;

    static final int WIDGET_ACTION_OPCODE=185;
    static final int ITEM_OPTION_1_OPCODE=145;
    static final boolean SEARCH_PROMPT_WIRE_OWNED=false;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum InputKind {
        SELECT_CATEGORY,
        SELECT_ROW,
        SEARCH_REQUEST,
        BACK,
        ATTEMPT,
        SELECT_ITEM
    }

    static final class Input {
        final InputKind kind;
        final ItemEnchantmentService.Category category;
        final int rowIndex;
        final int slot;
        final int itemId;

        private Input(
            InputKind kind,
            ItemEnchantmentService.Category category,
            int rowIndex,
            int slot,
            int itemId
        ){
            this.kind=
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.category=category;
            this.rowIndex=rowIndex;
            this.slot=slot;
            this.itemId=itemId;
        }

        static Input category(
            ItemEnchantmentService.Category category
        ){
            return new Input(
                InputKind.SELECT_CATEGORY,
                Objects.requireNonNull(
                    category,
                    "category"
                ),
                -1,
                -1,
                -1
            );
        }

        static Input row(int rowIndex){
            return new Input(
                InputKind.SELECT_ROW,
                null,
                checkedRow(rowIndex),
                -1,
                -1
            );
        }

        static Input simple(InputKind kind){
            return new Input(
                kind,
                null,
                -1,
                -1,
                -1
            );
        }

        static Input item(
            int slot,
            int itemId
        ){
            checkedU16(
                slot,
                "slot"
            );
            checkedU16(
                itemId,
                "itemId"
            );

            return new Input(
                InputKind.SELECT_ITEM,
                null,
                -1,
                slot,
                itemId
            );
        }
    }

    static final class Action {
        final InputKind kind;
        final ItemEnchantmentService.PlayerSnapshot player;
        final ConversionService.RecipeAttemptId attemptId;

        private Action(
            InputKind kind,
            ItemEnchantmentService.PlayerSnapshot player,
            ConversionService.RecipeAttemptId attemptId
        ){
            this.kind=
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.player=player;
            this.attemptId=attemptId;
        }
    }

    static final class Row {
        final int index;
        final int widgetId;
        final String enchantmentKey;
        final String displayName;

        Row(
            int index,
            ItemEnchantmentService.Entry entry
        ){
            this.index=checkedRow(index);
            this.widgetId=
                rowWidget(index);
            this.enchantmentKey=
                entry.enchantmentKey;
            this.displayName=
                requireSingleLine(
                    entry.displayName,
                    "displayName"
                );
        }
    }

    static final class View {
        final ItemEnchantmentService.PlayerSnapshot player;
        final List<Row> rows;

        private View(
            ItemEnchantmentService.PlayerSnapshot player,
            List<Row> rows
        ){
            this.player=
                Objects.requireNonNull(
                    player,
                    "player"
                );
            this.rows=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        rows
                    )
                );
        }
    }

    static Input resolveWidget(
        int activeRoot,
        int widgetId
    ){
        checkedU16(
            activeRoot,
            "activeRoot"
        );
        checkedU16(
            widgetId,
            "widgetId"
        );

        if(activeRoot==CATEGORY_ROOT){
            ItemEnchantmentService.Category
                category=
                    categoryForWidget(
                        widgetId
                    );

            return category==null
                ?null
                :Input.category(
                    category
                );
        }

        if(activeRoot!=MAIN_ROOT)
            return null;

        if(widgetId>=FIRST_ROW_WIDGET&&
           widgetId<=LAST_ROW_WIDGET)
            return Input.row(
                widgetId-FIRST_ROW_WIDGET
            );

        if(widgetId==ATTEMPT_WIDGET)
            return Input.simple(
                InputKind.ATTEMPT
            );

        if(widgetId==SEARCH_WIDGET)
            return Input.simple(
                InputKind.SEARCH_REQUEST
            );

        if(widgetId==BACK_WIDGET)
            return Input.simple(
                InputKind.BACK
            );

        return null;
    }

    static Input resolveItemOption1(
        int activeRoot,
        int widgetId,
        int slot,
        int itemId
    ){
        checkedU16(
            activeRoot,
            "activeRoot"
        );
        checkedU16(
            widgetId,
            "widgetId"
        );

        if(activeRoot!=MAIN_ROOT||
           widgetId!=ITEM_SELECTION_WIDGET)
            return null;

        return Input.item(
            slot,
            itemId
        );
    }

    /**
     * Applies only actions represented by ItemEnchantmentService itself.
     *
     * SEARCH_REQUEST has no text payload and remains presentation-only because
     * the exact search prompt/response round-trip is not proven.
     */
    static Action applyWidget(
        ItemEnchantmentService service,
        String playerRef,
        int activeRoot,
        int widgetId
    ){
        Objects.requireNonNull(
            service,
            "service"
        );

        Input input=
            resolveWidget(
                activeRoot,
                widgetId
            );

        if(input==null)
            return null;

        switch(input.kind){
            case SELECT_CATEGORY:
                return new Action(
                    input.kind,
                    service.selectCategory(
                        playerRef,
                        input.category
                    ),
                    null
                );

            case SELECT_ROW:
                List<ItemEnchantmentService.Entry>
                    visible=
                        service.visibleEntries(
                            playerRef
                        );

                if(input.rowIndex>=visible.size())
                    throw new IllegalStateException(
                        "enchantment row has no visible entry index="+
                        input.rowIndex
                    );

                return new Action(
                    input.kind,
                    service.selectEntry(
                        playerRef,
                        visible.get(
                            input.rowIndex
                        ).enchantmentKey
                    ),
                    null
                );

            case BACK:
                return new Action(
                    input.kind,
                    service.clearCategory(
                        playerRef
                    ),
                    null
                );

            case ATTEMPT:
                ConversionService.RecipeAttemptId
                    attempt=
                        service.beginAttempt(
                            playerRef
                        );

                return new Action(
                    input.kind,
                    service.getPlayer(
                        playerRef
                    ),
                    attempt
                );

            case SEARCH_REQUEST:
                return new Action(
                    input.kind,
                    currentPlayer(
                        service,
                        playerRef
                    ),
                    null
                );

            default:
                throw new IllegalStateException(
                    "unsupported widget action "+
                    input.kind
                );
        }
    }

    static ItemEnchantmentService.PlayerSnapshot
        applySearchQuery(
            ItemEnchantmentService service,
            String playerRef,
            String query
        ){
        return Objects.requireNonNull(
            service,
            "service"
        ).setSearchQuery(
            playerRef,
            query
        );
    }

    static View project(
        ItemEnchantmentService service,
        String playerRef
    ){
        Objects.requireNonNull(
            service,
            "service"
        );

        List<ItemEnchantmentService.Entry>
            entries=
                service.visibleEntries(
                    playerRef
                );

        ArrayList<Row> rows=
            new ArrayList<>(
                entries.size()
            );

        for(int i=0;i<entries.size();i++)
            rows.add(
                new Row(
                    i,
                    entries.get(i)
                )
            );

        return new View(
            currentPlayer(
                service,
                playerRef
            ),
            rows
        );
    }

    static void openMain(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        ).fixed(
            97,
            BootstrapPackets
                .interface97(MAIN_ROOT)
        );
    }

    static void openCategories(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        ).fixed(
            97,
            BootstrapPackets
                .interface97(CATEGORY_ROOT)
        );
    }

    static void publishEmptyRows(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        for(int i=0;i<ROWS;i++)
            ApplicationBus126Publisher.send(
                packets,
                rowWidget(i),
                ""
            );
    }

    static void publishRows(
        ServerPacketWriter packets,
        View view
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );
        Objects.requireNonNull(
            view,
            "view"
        );

        for(int i=0;i<ROWS;i++){
            String text=
                i<view.rows.size()
                    ?view.rows.get(i)
                        .displayName
                    :"";

            ApplicationBus126Publisher.send(
                packets,
                rowWidget(i),
                text
            );
        }
    }

    /**
     * Caller supplies presentation text only. No chance/search/economy
     * calculation is performed here.
     */
    static void publishDetailText(
        ServerPacketWriter packets,
        String categoryText,
        String successChanceText,
        String promptText,
        String ingredientHeading
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        ApplicationBus126Publisher.send(
            packets,
            CATEGORY_TEXT_WIDGET,
            requireSingleLine(
                categoryText,
                "categoryText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            SUCCESS_CHANCE_TEXT_WIDGET,
            requireSingleLine(
                successChanceText,
                "successChanceText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            CATEGORY_PROMPT_TEXT_WIDGET,
            requireSingleLine(
                promptText,
                "promptText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            INGREDIENT_HEADING_TEXT_WIDGET,
            requireSingleLine(
                ingredientHeading,
                "ingredientHeading"
            )
        );
    }

    static void publishResultState(
        ServerPacketWriter packets,
        ItemEnchantmentService.PresentationState
            state
    )throws IOException{
        ApplicationBus126Publisher.send(
            Objects.requireNonNull(
                packets,
                "packets"
            ),
            RESULT_TARGET,
            Integer.toString(
                resultState(
                    state
                )
            )
        );
    }

    static int resultState(
        ItemEnchantmentService.PresentationState
            state
    ){
        switch(
            Objects.requireNonNull(
                state,
                "state"
            )
        ){
            case IDLE:
                return 0;
            case PREPARATION:
                return 1;
            case SUCCESS:
                return 2;
            case FAILURE:
                return 3;
            default:
                throw new IllegalStateException(
                    "unknown enchantment presentation state "+
                    state
                );
        }
    }

    static int rowWidget(int rowIndex){
        return FIRST_ROW_WIDGET+
            checkedRow(
                rowIndex
            );
    }

    static int categoryWidget(
        ItemEnchantmentService.Category category
    ){
        switch(
            Objects.requireNonNull(
                category,
                "category"
            )
        ){
            case ARMOR:
                return 50327;
            case WEAPONS:
                return 50329;
            case CAPES:
                return 50331;
            case TRINKETS:
                return 50333;
            case PETS:
                return 50335;
            case COSMETICS:
                return 50337;
            case MISC:
                return 50339;
            default:
                throw new IllegalStateException(
                    "unknown category "+
                    category
                );
        }
    }

    private static ItemEnchantmentService.Category
        categoryForWidget(
            int widgetId
        ){
        for(ItemEnchantmentService.Category category:
                ItemEnchantmentService.Category.values())
            if(categoryWidget(category)==
                    widgetId)
                return category;

        return null;
    }

    private static ItemEnchantmentService.PlayerSnapshot
        currentPlayer(
            ItemEnchantmentService service,
            String playerRef
        ){
        ItemEnchantmentService.PlayerSnapshot
            player=
                service.getPlayer(
                    playerRef
                );

        if(player!=null)
            return player;

        /*
         * visibleEntries() establishes the service's normalized player state
         * without inventing a selection.
         */
        service.visibleEntries(
            playerRef
        );

        return service.getPlayer(
            playerRef
        );
    }

    private static int checkedRow(
        int rowIndex
    ){
        if(rowIndex<0||
           rowIndex>=ROWS)
            throw new IllegalArgumentException(
                "rowIndex="+rowIndex
            );

        return rowIndex;
    }

    private static void checkedU16(
        int value,
        String field
    ){
        if(value<0||
           value>0xffff)
            throw new IllegalArgumentException(
                field+"="+value
            );
    }

    private static String requireSingleLine(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=
            value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        if(clean.indexOf('\n')>=0||
           clean.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                field+" contains line terminator"
            );

        for(int i=0;i<clean.length();i++)
            if(clean.charAt(i)>0xff)
                throw new IllegalArgumentException(
                    field+
                    " not ISO-8859-1 at index="+
                    i
                );

        return clean;
    }

    private ItemEnchantmentPresentation(){}
}

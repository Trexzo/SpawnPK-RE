package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab composition for exact-v308 Item Enchantment.
 *
 * Exact roots/widgets are client authority. Category-to-main navigation and
 * truthful empty-main text are explicit LocalLab composition; catalog, recipe,
 * search-response and conversion mechanics remain unowned.
 */
final class LocalItemEnchantmentUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_ITEM_ENCHANTMENT_EMPTY_MAIN_NAV";

    static final String SUCCESS_CHANCE_TEXT=
        "Success chance: not configured";
    static final String PROMPT_TEXT=
        "No LocalLab enchantments configured";
    static final String INGREDIENT_HEADING=
        "Ingredients: not configured";

    enum Surface {
        CATEGORIES,
        MAIN
    }

    static final class Result {
        final String status;
        final ItemEnchantmentPresentation.Input input;
        final boolean succeeded;

        Result(
            String status,
            ItemEnchantmentPresentation.Input input,
            boolean succeeded
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.input=
                Objects.requireNonNull(
                    input,
                    "input"
                );
            this.succeeded=succeeded;
        }
    }

    private Surface surface;
    private ItemEnchantmentService.Category
        selectedCategory;

    synchronized void openCategories(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        ItemEnchantmentPresentation.openCategories(
            packets
        );
        selectedCategory=null;
        surface=Surface.CATEGORIES;
    }

    /**
     * Compatibility path retained for the certified G13.11 category-shell
     * regression. Production LocalSession uses handle(input, packets).
     */
    synchronized Result handleCategory(
        ItemEnchantmentPresentation.Input input
    ){
        ItemEnchantmentPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        if(checked.kind!=
                ItemEnchantmentPresentation
                    .InputKind.SELECT_CATEGORY)
            throw new IllegalArgumentException(
                "not category input "+
                checked.kind
            );

        if(surface==null)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        return new Result(
            "DISABLED_NO_CATALOG_AUTHORITY",
            checked,
            false
        );
    }

    synchronized Result handle(
        ItemEnchantmentPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        ItemEnchantmentPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );
        Objects.requireNonNull(
            packets,
            "packets"
        );

        if(surface==null)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        if(surface==Surface.CATEGORIES){
            if(checked.kind!=
                    ItemEnchantmentPresentation
                        .InputKind.SELECT_CATEGORY)
                return new Result(
                    "WRONG_SURFACE_NOOP",
                    checked,
                    false
                );

            selectedCategory=
                Objects.requireNonNull(
                    checked.category,
                    "category"
                );

            ItemEnchantmentPresentation.openMain(
                packets
            );
            ItemEnchantmentPresentation.publishEmptyRows(
                packets
            );
            ItemEnchantmentPresentation.publishDetailText(
                packets,
                categoryLabel(selectedCategory),
                SUCCESS_CHANCE_TEXT,
                PROMPT_TEXT,
                INGREDIENT_HEADING
            );
            ItemEnchantmentPresentation.publishResultState(
                packets,
                ItemEnchantmentService
                    .PresentationState.IDLE
            );

            surface=Surface.MAIN;

            return new Result(
                "NAVIGATED_TO_EMPTY_MAIN",
                checked,
                true
            );
        }

        if(checked.kind==
                ItemEnchantmentPresentation
                    .InputKind.BACK){
            ItemEnchantmentPresentation.openCategories(
                packets
            );
            selectedCategory=null;
            surface=Surface.CATEGORIES;

            return new Result(
                "NAVIGATED_TO_CATEGORIES",
                checked,
                true
            );
        }

        if(checked.kind==
                ItemEnchantmentPresentation
                    .InputKind.SELECT_ROW||
           checked.kind==
                ItemEnchantmentPresentation
                    .InputKind.ATTEMPT||
           checked.kind==
                ItemEnchantmentPresentation
                    .InputKind.SEARCH_REQUEST)
            return new Result(
                "DISABLED_NO_CATALOG_AUTHORITY",
                checked,
                false
            );

        return new Result(
            "WRONG_SURFACE_NOOP",
            checked,
            false
        );
    }

    synchronized boolean close(){
        boolean wasOpen=
            surface!=null;
        surface=null;
        selectedCategory=null;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return surface!=null;
    }

    synchronized Surface surface(){
        return surface;
    }

    synchronized ItemEnchantmentService.Category
        selectedCategory(){
        return selectedCategory;
    }

    private static String categoryLabel(
        ItemEnchantmentService.Category category
    ){
        switch(
            Objects.requireNonNull(
                category,
                "category"
            )
        ){
            case ARMOR:
                return "Armor - no LocalLab enchantments configured";
            case WEAPONS:
                return "Weapons - no LocalLab enchantments configured";
            case CAPES:
                return "Capes - no LocalLab enchantments configured";
            case TRINKETS:
                return "Trinkets - no LocalLab enchantments configured";
            case PETS:
                return "Pets - no LocalLab enchantments configured";
            case COSMETICS:
                return "Cosmetics - no LocalLab enchantments configured";
            case MISC:
                return "Misc - no LocalLab enchantments configured";
            default:
                throw new IllegalStateException(
                    "unknown enchantment category "+
                    category
                );
        }
    }
}

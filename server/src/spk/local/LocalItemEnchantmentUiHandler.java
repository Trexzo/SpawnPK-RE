package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab shell for exact-v308 Item Enchantment categories.
 *
 * The category root and category widget identities are exact client authority.
 * Catalog contents, recipe bindings and all main-root mechanics remain unowned.
 */
final class LocalItemEnchantmentUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_ITEM_ENCHANTMENT_CATEGORY_SHELL";

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

    private boolean open;

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
        open=true;
    }

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

        if(!open)
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

    synchronized boolean close(){
        boolean wasOpen=open;
        open=false;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return open;
    }
}

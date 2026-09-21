package spk.content.api;

/**
 * Semantic item-on-ground-item intent. Selected inventory slot, widget/container
 * identity and packet metadata are intentionally absent.
 */
public interface ContentItemOnGroundItemContext {
    int itemId();
    int groundItemId();
    int worldX();
    int worldY();
}

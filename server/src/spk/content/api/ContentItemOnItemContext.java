package spk.content.api;

/**
 * Semantic item-on-item intent. Selected/target inventory slots, widget/container
 * identity and packet metadata are intentionally absent.
 */
public interface ContentItemOnItemContext {
    int selectedItemId();
    int targetItemId();
}

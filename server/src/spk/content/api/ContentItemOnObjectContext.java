package spk.content.api;

/**
 * Semantic item-on-object intent. Selected inventory slot, widget/container id,
 * opcode/schema metadata and packet/runtime state are intentionally absent.
 */
public interface ContentItemOnObjectContext {
    int itemId();
    int objectId();
    int worldX();
    int worldY();
}

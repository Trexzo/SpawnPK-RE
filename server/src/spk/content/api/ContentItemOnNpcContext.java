package spk.content.api;

/**
 * Semantic item-on-NPC intent after the authoritative NPC target has been
 * resolved. Client scene index, inventory slot, widget id and packet metadata
 * are intentionally absent.
 */
public interface ContentItemOnNpcContext {
    int itemId();
    int npcDefinitionId();
    int worldX();
    int worldY();
}

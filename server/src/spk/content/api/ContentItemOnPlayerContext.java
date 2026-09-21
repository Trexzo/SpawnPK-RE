package spk.content.api;

/**
 * Semantic item-on-player intent after the target player has been resolved.
 * Viewer-local player indices, selected slot/widget identity and packet metadata
 * are intentionally absent.
 */
public interface ContentItemOnPlayerContext {
    int itemId();
    ContentPlayer target();
}

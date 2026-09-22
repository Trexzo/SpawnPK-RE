package spk.content.api;

/**
 * Language-neutral registration surface.
 *
 * This registrar is scoped to one {@link ContentModule#register(ContentRegistrar)}\n * invocation. Modules may retain returned {@link ContentRegistration}\n * handles, but late registrar mutations after register(...) returns are rejected.\n *\n * Provenance is intentionally absent: registry/core installation assigns it so
 * module code cannot self-label custom behavior as recovered authority.
 */
public interface ContentRegistrar {
    ContentRegistration command(
        String name,
        int priority,
        ContentCommandHandler handler
    );

    ContentRegistration objectOption(
        int objectId,
        int option,
        int priority,
        ContentObjectOptionHandler handler
    );

    ContentRegistration itemOption(
        int itemId,
        int option,
        int priority,
        ContentItemOptionHandler handler
    );

    ContentRegistration itemOnNpc(
        int itemId,
        int npcDefinitionId,
        int priority,
        ContentItemOnNpcHandler handler
    );

    ContentRegistration itemOnGroundItem(
        int itemId,
        int groundItemId,
        int priority,
        ContentItemOnGroundItemHandler handler
    );

    ContentRegistration itemOnItem(
        int selectedItemId,
        int targetItemId,
        int priority,
        ContentItemOnItemHandler handler
    );

    ContentRegistration itemOnObject(
        int itemId,
        int objectId,
        int priority,
        ContentItemOnObjectHandler handler
    );

    ContentRegistration itemOnPlayer(
        int itemId,
        int priority,
        ContentItemOnPlayerHandler handler
    );

    ContentRegistration npcOption(
        int npcDefinitionId,
        int option,
        int priority,
        ContentNpcOptionHandler handler
    );
}

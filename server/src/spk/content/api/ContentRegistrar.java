package spk.content.api;

/**
 * Language-neutral registration surface.
 *
 * Provenance is intentionally absent: registry/core installation assigns it so
 * module code cannot self-label custom behavior as recovered authority.
 */
public interface ContentRegistrar {
    void command(
        String name,
        int priority,
        ContentCommandHandler handler
    );

    void objectOption(
        int objectId,
        int option,
        int priority,
        ContentObjectOptionHandler handler
    );

    void npcOption(
        int npcDefinitionId,
        int option,
        int priority,
        ContentNpcOptionHandler handler
    );
}

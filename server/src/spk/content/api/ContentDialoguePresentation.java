package spk.content.api;

import java.util.List;

/**
 * Semantic standard-dialogue presentation capability for content modules.
 *
 * Implementations own exact-current client roots/widgets/packets internally.
 * Content supplies only semantic NPC identity, speaker text and dialogue lines.
 */
public interface ContentDialoguePresentation {
    /**
     * Show a model-free standard statement dialogue.
     *
     * Exact-current presentation currently supports 1..5 lines.
     */
    void statement(
        List<String> lines
    );

    /**
     * Show a standard named NPC dialogue.
     *
     * Exact-current presentation currently supports 1..4 lines.
     */
    void namedNpc(
        int npcDefinitionId,
        String speakerName,
        List<String> lines
    );

    /**
     * Close the current dialogue/interface presentation.
     */
    void close();
}

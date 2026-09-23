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
     * Show the exact-current standard two-option dialogue family.
     *
     * This capability is deliberately limited to exactly two options because
     * only that child-widget presentation contract is closed by current v308
     * evidence. It must not be interpreted as a generalized 2..5 option API.
     */
    default void twoOptions(
        String title,
        List<String> options
    ){
        throw new UnsupportedOperationException(
            "two-option dialogue presentation unavailable"
        );
    }

    /**
     * Close the current dialogue/interface presentation.
     */
    void close();
}

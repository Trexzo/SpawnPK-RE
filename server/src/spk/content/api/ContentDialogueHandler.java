package spk.content.api;

/** Content-owned semantic dialogue transition policy. */
@FunctionalInterface
public interface ContentDialogueHandler {
    ContentDialogueTransition handle(
        ContentDialogueContext context
    );

    /**
     * Optional semantic topology for this dialogue binding.
     *
     * Returning null keeps transition-only handlers backward-compatible while
     * allowing a lower-priority content binding to remain the topology source.
     */
    default ContentDialogueDefinition definition(){
        return null;
    }
}

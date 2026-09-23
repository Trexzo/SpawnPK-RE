package spk.content.api;

/** Content-owned semantic dialogue transition policy. */
@FunctionalInterface
public interface ContentDialogueHandler {
    ContentDialogueTransition handle(
        ContentDialogueContext context
    );
}

package spk.content.api;

/** Semantic dialogue transition context with raw transport/UI identity removed. */
public interface ContentDialogueContext {
    String dialogueKey();
    String nodeKey();
    ContentDialogueIntent intent();
    ContentPlayer player();
}

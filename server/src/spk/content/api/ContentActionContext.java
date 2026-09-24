package spk.content.api;

/** Semantic content action context with no transport or cache identity. */
public interface ContentActionContext {
    String actionKey();
    ContentPlayer player();
}

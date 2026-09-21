package spk.content.api;

public interface ContentItemOnObjectHandler {
    ContentInteractionResult handle(
        ContentItemOnObjectContext context
    );
}

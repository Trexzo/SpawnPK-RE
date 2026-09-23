package spk.content.api;

@FunctionalInterface
public interface ContentActionHandler {
    ContentActionResult handle(
        ContentActionContext context
    );
}

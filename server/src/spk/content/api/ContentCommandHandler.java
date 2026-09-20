package spk.content.api;

public interface ContentCommandHandler {
    ContentResult handle(
        ContentCommandContext context
    )throws Exception;
}

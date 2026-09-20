package spk.content.api;

import java.util.List;

/** Validated command content context. */
public interface ContentCommandContext {
    String rawCommand();
    String commandName();
    List<String> arguments();
    ContentPlayer player();
    ContentPresentation presentation();
}

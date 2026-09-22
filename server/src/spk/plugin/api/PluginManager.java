package spk.plugin.api;

import java.util.Collection;
import java.util.List;

/** Core-owned plugin lifecycle surface. */
public interface PluginManager {
    PluginHandle enable(
        Plugin plugin
    ) throws Exception;

    List<PluginHandle> enableAll(
        Collection<? extends Plugin> plugins
    ) throws Exception;

    boolean disable(
        String pluginId
    );

    PluginHandle plugin(
        String pluginId
    );

    List<PluginHandle> enabled();
}

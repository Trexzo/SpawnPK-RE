package spk.local;

import spk.plugin.api.Plugin;

/** Internal source-loader boundary shared by JVM plugin formats. */
interface PluginLoader {
    boolean supports(
        PluginSource source
    );

    Plugin load(
        PluginSource source
    ) throws Exception;
}

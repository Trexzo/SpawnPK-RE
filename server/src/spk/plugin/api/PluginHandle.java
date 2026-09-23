package spk.plugin.api;

/** Read-only lifecycle view for one enabled or previously-enabled plugin. */
public interface PluginHandle {
    PluginManifest manifest();
    boolean enabled();
}

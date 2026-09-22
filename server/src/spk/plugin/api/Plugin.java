package spk.plugin.api;

/** JVM plugin contract shared by Java and other JVM languages. */
public interface Plugin {
    PluginManifest manifest();

    void enable(
        PluginContext context
    ) throws Exception;

    default void disable() throws Exception {}
}

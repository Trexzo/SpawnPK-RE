package spk.local;

/** Internal source-loader boundary shared by JVM plugin formats. */
interface PluginLoader {
    boolean supports(
        PluginSource source
    );

    PluginRuntime load(
        PluginSource source
    ) throws Exception;
}

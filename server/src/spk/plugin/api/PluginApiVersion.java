package spk.plugin.api;

/** Stable JVM plugin API compatibility boundary. */
public final class PluginApiVersion {
    public static final int CURRENT=1;

    public static boolean compatible(
        int requiredVersion
    ){
        return requiredVersion==CURRENT;
    }

    private PluginApiVersion(){}
}

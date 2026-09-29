package spk.local;

import spk.plugin.api.Plugin;

/** Managed plugin runtime produced by a source loader. */
interface PluginRuntime extends Plugin,AutoCloseable {
    ClassLoader callbackClassLoader();

    @Override void close() throws Exception;
}

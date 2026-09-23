package spk.plugin.api;

/** Lifecycle handle for one plugin-owned logical World task. */
public interface PluginTask extends AutoCloseable {
    boolean active();
    boolean cancel();

    @Override
    default void close(){
        cancel();
    }
}

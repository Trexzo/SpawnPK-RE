package spk.plugin.api;

import spk.content.api.ContentRegistrar;

/**
 * Capability-scoped enable context.
 *
 * Registration capabilities are valid only while Plugin.enable(...) is running.
 * Runtime callbacks use the safe content/domain objects supplied to those
 * registrations rather than retaining server internals.
 */
public interface PluginContext {
    ContentRegistrar content();
    PluginEvents events();

    /**
     * Returns the plugin-lifetime logical-tick scheduler capability.
     *
     * The returned scheduler may be retained after enable(...) completes.
     * It becomes terminal when the owning plugin is disabled or the World closes.
     */
    PluginScheduler scheduler();
}

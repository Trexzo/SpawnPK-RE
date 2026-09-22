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
}

/**
 * Stable JVM plugin API.
 *
 * <p>Compatibility policy:</p>
 * <ul>
 *   <li>{@link spk.plugin.api.PluginApiVersion#CURRENT} is the core API version.</li>
 *   <li>A plugin declares the API version it requires in its immutable
 *       {@link spk.plugin.api.PluginManifest}.</li>
 *   <li>The initial kernel uses exact-version compatibility. A future compatible
 *       version range may be introduced only by changing PluginApiVersion policy,
 *       not by individual plugins.</li>
 *   <li>Public types in this package may depend on the public content API and
 *       validated domain-event API, but never on spk.local runtime internals,
 *       sockets, packet writers, ISAAC state, raw opcodes or viewer-local indexes.</li>
 *   <li>Kotlin/JVM and later JVM language adapters consume this same Java ABI.
 *       Language-specific runtimes must not create a parallel gameplay API.</li>
 * </ul>
 *
 * <p>Registration capabilities supplied through {@link spk.plugin.api.PluginContext}
 * are enable-phase scoped. Runtime behavior happens through registered content
 * handlers and validated domain-event listeners.</p>
 */
package spk.plugin.api;

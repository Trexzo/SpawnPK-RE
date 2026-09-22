package spk.plugin.api;

/**
 * Plugin-owned authoritative logical-tick scheduling capability.
 *
 * Implementations execute callbacks on the existing World execution context.
 * Delays and periods are positive logical ticks, never wall-clock time.
 */
public interface PluginScheduler {
    PluginTask schedule(
        long delayTicks,
        Runnable task
    );

    PluginTask scheduleRepeating(
        long initialDelayTicks,
        long periodTicks,
        Runnable task
    );
}

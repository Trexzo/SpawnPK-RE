package spk.content.api;

/**
 * Opaque lifecycle handle for one content binding.
 *
 * Implementations remain owned by the core registry. Content code may only
 * inspect whether the binding is active and unregister that exact binding.
 */
public interface ContentRegistration extends AutoCloseable {
    /** True only while this binding is committed in the registry. */
    boolean active();

    /**
     * Remove this exact binding.
     *
     * @return true when this call changed pending/active state; false when the
     *         handle had already been removed or invalidated.
     */
    boolean unregister();

    @Override default void close(){
        unregister();
    }
}

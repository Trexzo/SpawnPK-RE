package spk.content.api;

/** Static Java content module; later language runtimes should target this API. */
public interface ContentModule {
    String id();
    void register(ContentRegistrar registrar);
}

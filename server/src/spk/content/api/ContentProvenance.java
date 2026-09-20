package spk.content.api;

/** Evidence/provenance attached by the core registry, never by a handler. */
public enum ContentProvenance {
    EXACT_CURRENT_CLIENT,
    EXACT_CURRENT_CACHE,
    LOCAL_RUNTIME_PROVEN,
    HISTORICAL_CORROBORATION,
    INFERENCE,
    UNKNOWN_SERVER_AUTHORITY,
    CUSTOM_LOCALLAB
}

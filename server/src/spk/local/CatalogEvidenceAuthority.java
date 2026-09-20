package spk.local;

/** Provenance for immutable read-only catalog/query definitions. */
enum CatalogEvidenceAuthority {
    EXACT_CURRENT_CLIENT_OR_CACHE,
    RECOVERED_SERVER_OR_DATASET,
    CUSTOM_LOCALLAB,
    UNKNOWN_SERVER_AUTHORITY
}

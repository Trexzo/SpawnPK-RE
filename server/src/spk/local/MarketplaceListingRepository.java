package spk.local;

/** Persistence boundary for immutable Marketplace listing snapshots. */
interface MarketplaceListingRepository {
    void save(MarketplaceListing.Snapshot snapshot);
    MarketplaceListing.Snapshot find(MarketplaceListing.Id listingId);
}

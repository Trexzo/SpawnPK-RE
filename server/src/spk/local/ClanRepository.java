package spk.local;

import java.util.Optional;

/**
 * Persistence seam for the clan aggregate.
 *
 * Storage format, transactional durability and migration policy belong to the
 * persistence layer and are intentionally not selected here.
 */
interface ClanRepository {
    Optional<ClanAggregate.Snapshot> find(ClanAggregate.ClanId id);
    void save(ClanAggregate.Snapshot snapshot);
}

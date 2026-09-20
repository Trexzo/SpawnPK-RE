package spk.local;

import java.util.Optional;

/** Persistence seam for immutable semantic house snapshots. */
interface HouseRepository {
    Optional<ConstructionService.Snapshot> find(ConstructionService.HouseId houseId);
    void save(ConstructionService.Snapshot snapshot);
}

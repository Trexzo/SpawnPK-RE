package spk.local;

import java.util.Optional;

/**
 * Persistence seam for Collection Log runtime progress.
 *
 * Concrete storage format, migration and save cadence remain owned by the
 * persistence layer.
 */
interface CollectionLogRepository {
    Optional<CollectionLogService.ProgressSnapshot> find(CollectionLogDefinition.CollectionId collectionId);
    void save(CollectionLogService.ProgressSnapshot snapshot);
}

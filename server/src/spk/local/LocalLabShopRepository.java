package spk.local;

import java.io.IOException;
import java.util.Optional;

/** Persistence boundary for explicit LocalLab world-owned Shop state. */
interface LocalLabShopRepository {
    Optional<LocalLabShopSnapshot> load()
        throws IOException;

    void save(
        LocalLabShopSnapshot snapshot
    )throws IOException;
}

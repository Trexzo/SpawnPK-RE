package spk.local;

import java.io.IOException;
import java.util.Optional;

/**
 * Storage-agnostic gameplay-state persistence boundary.
 *
 * Authentication/credentials deliberately remain outside this interface.
 */
interface PlayerRepository {
    Optional<PlayerSnapshot> load(
        String username
    )throws IOException;

    void save(
        PlayerSnapshot snapshot
    )throws IOException;
}

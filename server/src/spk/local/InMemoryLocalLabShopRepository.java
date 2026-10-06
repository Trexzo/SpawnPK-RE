package spk.local;

import java.io.IOException;
import java.util.Optional;

/** Test/default isolated-World repository with no filesystem side effects. */
final class InMemoryLocalLabShopRepository
    implements LocalLabShopRepository {

    private LocalLabShopSnapshot snapshot;

    @Override public synchronized
        Optional<LocalLabShopSnapshot> load()
            throws IOException{
        return Optional.ofNullable(
            snapshot
        );
    }

    @Override public synchronized void save(
        LocalLabShopSnapshot snapshot
    )throws IOException{
        if(snapshot==null)
            throw new NullPointerException(
                "snapshot"
            );

        this.snapshot=
            LocalLabShopSnapshot.ofRocktailStock(
                snapshot.rocktailStock
            );
    }
}

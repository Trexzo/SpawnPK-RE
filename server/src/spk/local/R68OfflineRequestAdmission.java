package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R68 OFFLINE ONLY, explicit test hook for the real LocalPendingRequestDispatcher.
 *
 * Runs *every* typed request through R66's current-owner+actor gate before
 * the actual domain router can execute it. R60's cooperative OS lock is held
 * across ownership validation and the handler body. A late request captured
 * from a superseded R64 session never enters the handler.
 *
 * No LocalSession constructs or installs this hook; it is not a production
 * security boundary. The locked handler may synchronously call other
 * subsystems, so live integration would require an independent deadlock,
 * session-authentication and async-writer audit.
 */
final class R68OfflineRequestAdmission
        implements LocalPendingRequestDispatcher.OfflineRequestAdmission {
    private final Path journal;
    private final R66OfflineGameEventDispatch actors;
    private final R66OfflineGameEventDispatch.Actor actor;
    private final AtomicInteger attempted=new AtomicInteger();
    private final AtomicInteger admitted=new AtomicInteger();

    R68OfflineRequestAdmission(
        Path journal,R66OfflineGameEventDispatch actors,
        R66OfflineGameEventDispatch.Actor actor
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.actors=Objects.requireNonNull(actors,"actors");
        this.actor=Objects.requireNonNull(actor,"actor");
    }

    @Override public void withAdmission(
        ClientRequest request,
        LocalPendingRequestDispatcher.CheckedRequestHandler handler
    )throws IOException{
        Objects.requireNonNull(request,"request");
        Objects.requireNonNull(handler,"handler");
        ClientRequestMetadata metadata=request.metadata();
        if(metadata==null||
           metadata.provenance!=
                ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new IOException(
                "R68_ADMISSION_DENIED unknown request provenance"
            );
        attempted.incrementAndGet();
        actors.runVerifiedHandler(
            actor,UUID.randomUUID(),()->{
                R60TradePairCoordinator.requireHeldOnCurrentThread(journal);
                admitted.incrementAndGet();
                handler.run();
            }
        );
    }

    int attempted(){return attempted.get();}
    int admitted(){return admitted.get();}
}

package spk.local;

import java.io.IOException;
import java.util.Objects;
import spk.event.DomainEventBus;

/**
 * R77 TEST ONLY: explicitly owner-scoped domain-event publication.
 *
 * Existing domain events without a proven WorldPlayer generation remain
 * transport-neutral/global and are NOT silently reclassified as player-owned.
 * Production DomainEventBus does not install this policy.
 */
final class R77OfflineOwnedDomainPublicationFence {
    /**
     * Only offline fixtures supply this complete identity. An attacker
     * or network request cannot make a real account authenticated by
     * implementing this marker.
     */
    interface OfflineOwnedEvent extends DomainEventBus.Event {
        EntityId offlineOwnerId();
        long offlineOwnerGeneration();
    }

    private final World world;
    private final R74OfflineCommandIngressFence quiescence;
    private boolean installed;

    R77OfflineOwnedDomainPublicationFence(
        World world,R74OfflineCommandIngressFence quiescence
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.quiescence=Objects.requireNonNull(quiescence,"quiescence");
    }

    synchronized void installForTest(){
        if(installed)
            throw new IllegalStateException(
                "R77_DOMAIN_GATE_DENIED already installed"
            );
        world.domainEvents().installOfflinePublicationAdmissionForTest(
            (event,callback)->{
                if(!(event instanceof OfflineOwnedEvent)){
                    // Explicitly unclassified/global event: do not infer
                    // ownership from listeners, caller or event names.
                    callback.run();
                    return;
                }

                OfflineOwnedEvent owned=(OfflineOwnedEvent)event;
                EntityId id=owned.offlineOwnerId();
                long generation=owned.offlineOwnerGeneration();
                if(id==null||generation<=0L)
                    throw new IOException(
                        "R77_DOMAIN_DENIED missing owner metadata"
                    );

                WorldPlayer player=world.players().byId(id);
                if(player==null||
                   !world.players().owns(player,generation))
                    throw new IOException(
                        "R77_DOMAIN_DENIED stale owner generation"
                    );

                // The bus has already checked the actual World
                // execution context. Hold the same World-player
                // mutation lock for one real listener invocation.
                boolean invoked=
                    world.withOpenPlayerMutationOwnershipIfCurrent(
                        player,generation,()->{
                            if(quiescence.isQuiesced(player,generation))
                                throw new IOException(
                                    "R77_DOMAIN_DENIED owner quiesced"
                                );
                            callback.run();
                        }
                    );
                if(!invoked)
                    throw new IOException(
                        "R77_DOMAIN_DENIED World generation lost"
                    );
            }
        );
        installed=true;
    }
}

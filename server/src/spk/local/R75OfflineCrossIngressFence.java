package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * R75 OFFLINE ONLY. Compose R74's terminal World-player quiescence with
 * three independent callback paths that do not themselves pass through
 * WorldCommandInbox: WorldRealtimeQueue, WorldPetNpcService and the
 * lower-level decoded LocalPendingRequestDispatcher test seam.
 *
 * Network LocalSession still normally submits typed requests through the
 * World command inbox, but the dispatcher also exposes a direct drain()
 * API used by offline fixtures. No real socket/session installs these hooks.
 *
 * This policy is not login authorization, an R64 ownership validator,
 * or a global guard against every World mutation. It only denies new
 * callbacks for one explicitly named isolated World actor/generation.
 */
final class R75OfflineCrossIngressFence {
    private final World world;
    private final WorldPlayer owner;
    private final long generation;
    private final R74OfflineCommandIngressFence commandFence;
    private boolean installed;

    R75OfflineCrossIngressFence(
        World world,WorldPlayer owner,long generation,
        R74OfflineCommandIngressFence commandFence
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        this.generation=generation;
        this.commandFence=Objects.requireNonNull(
            commandFence,"commandFence"
        );
        if(!world.players().owns(owner,generation))
            throw new IllegalStateException(
                "R75_CROSS_INGRESS_DENIED not World owner"
            );
    }

    synchronized void installForTest(){
        if(installed)
            throw new IllegalStateException(
                "R75_CROSS_INGRESS_DENIED already installed"
            );
        if(!world.players().owns(owner,generation))
            throw new IllegalStateException(
                "R75_CROSS_INGRESS_DENIED stale World owner"
            );

        // Each actual queue/service enforces the test-only callbacks;
        // none is installed by LocalSession or a production World.
        world.realtime().installOfflineRealtimeAdmissionForTest(
            (p,g,task)->!(p==owner&&g==generation&&
                commandFence.isQuiesced(owner,generation))
        );
        world.petNpcs().installOfflinePetMutationAdmissionForTest(
            id->!owner.id().equals(id)||
                !commandFence.isQuiesced(owner,generation)
        );
        installed=true;
    }

    /**
     * Offline adapter for the existing R68 decoded-request test seam.
     * Without this explicit installation the real dispatcher still uses
     * its old handlers; no direct network/live gate is implicitly enabled.
     */
    LocalPendingRequestDispatcher.OfflineRequestAdmission
        decodedRequestAdmissionForTest(){
        return (request,handler)->{
            if(!world.players().owns(owner,generation))
                throw new IOException(
                    "R75_DECODED_REQUEST_DENIED owner no longer registered"
                );
            if(commandFence.isQuiesced(owner,generation))
                throw new IOException(
                    "R75_DECODED_REQUEST_DENIED after final quiescence"
                );
            handler.run();
        };
    }
}

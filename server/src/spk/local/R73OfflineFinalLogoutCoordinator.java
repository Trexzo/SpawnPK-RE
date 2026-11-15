package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R73 TEST ONLY: finish a real isolated World's captured final save,
 * drain the real IO worker, then release the current offline R64 owner
 * and unregister the World actor in a single World-thread callback.
 *
 * This never attaches to LocalSession, production accounts or login. The
 * R64 UUID/epoch are not real authentication. Failure/timeout is always
 * indeterminate and never authorizes owner release or auto-recovery.
 */
final class R73OfflineFinalLogoutCoordinator {
    static final class Outcome {
        final R64OfflineSessionOwnership.Token released;
        final long finalCaptureOrdinal;
        Outcome(R64OfflineSessionOwnership.Token released,long ordinal){
            this.released=released;
            this.finalCaptureOrdinal=ordinal;
        }
    }

    static Outcome finish(
        World world,WorldPlayer player,long generation,
        R72OfflineDurableCaptureOrder gate,
        WorldPlayerPersistence.CapturedSave finalCapture,
        long timeoutMillis
    )throws Exception{
        return finish(
            world,player,generation,gate,finalCapture,
            timeoutMillis,null
        );
    }

    /**
     * R74 opt-in variant. Ordinary commands have been fenced by the World
     * actor before this method is called. Only the verified final-release
     * callback is allowed to bypass that test-only command-admission gate.
     */
    static Outcome finish(
        World world,WorldPlayer player,long generation,
        R72OfflineDurableCaptureOrder gate,
        WorldPlayerPersistence.CapturedSave finalCapture,
        long timeoutMillis,
        R74OfflineCommandIngressFence commandFence
    )throws Exception{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(player,"player");
        Objects.requireNonNull(gate,"gate");
        Objects.requireNonNull(finalCapture,"final capture");
        if(timeoutMillis<=0L)
            throw new IllegalArgumentException(
                "R73_LOGOUT_DENIED invalid timeout"
            );
        if(world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R73_LOGOUT_DENIED cannot block World actor"
            );

        WorldPlayerPersistence persistence=world.persistence();
        if(!persistence.offlineTerminalFenceEnabledForTest()||
           !finalCapture.finalSnapshot||
           finalCapture.owner!=player||
           finalCapture.expectedGeneration!=generation||
           !world.players().owns(player,generation)||
           !persistence.checkpointSuppressed(player.id(),generation))
            throw new IOException(
                "R73_LOGOUT_DENIED final World capture/fence invalid"
            );
        if(persistence.failedCount()!=0L||
           persistence.checkpointRejectedCount()!=0L)
            throw new IOException(
                "R73_LOGOUT_DENIED prior IO failures"
            );

        WorldPlayerPersistence.SaveTicket submitted=
            persistence.submitCapturedWithBackpressure(
                finalCapture,timeoutMillis
            );

        try{
            submitted.completion.get(
                timeoutMillis,TimeUnit.MILLISECONDS
            );
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException(
                "R73_LOGOUT_DENIED final IO interrupted",interrupted
            );
        }catch(TimeoutException timedOut){
            throw new IOException(
                "R73_LOGOUT_DENIED final IO uncertain timeout",timedOut
            );
        }catch(ExecutionException failed){
            throw new IOException(
                "R73_LOGOUT_DENIED final IO rejected",failed.getCause()
            );
        }

        // The sentinel must run on the actual one-thread bounded executor
        // AFTER every earlier queued checkpoint/save. The final completion
        // alone does not prove the queue has been drained.
        persistence.awaitOfflineIoDrainForTest(timeoutMillis);
        if(persistence.failedCount()!=0L||
           persistence.checkpointRejectedCount()!=0L||
           persistence.queuedWrites()!=0)
            throw new IOException(
                "R73_LOGOUT_DENIED IO failure or unsettled queue"
            );

        AtomicReference<R64OfflineSessionOwnership.Token> released=
            new AtomicReference<>();
        // Actor callback holds the player mutationLock. The verified
        // owner release runs before the same callback unregisters this
        // exact World generation, cancelling queued actor commands.
        WorldCommandInbox.Action finalRelease=()->{
            if(!persistence.checkpointSuppressed(player.id(),generation))
                throw new IOException(
                    "R73_LOGOUT_DENIED final capture fence disappeared"
                );
            released.set(gate.releaseVerifiedFinalForTest(
                submitted.captureOrdinal,submitted.snapshot
            ));
            if(!world.unregisterPlayer(player,generation))
                throw new IOException(
                    "R73_LOGOUT_DENIED release succeeded but actor unregister failed"
                );
        };
        if(commandFence!=null){
            if(!commandFence.isQuiesced(player,generation))
                throw new IOException(
                    "R74_LOGOUT_DENIED World command ingress not quiesced"
                );
            finalRelease=commandFence.trustedFinalReleaseForTest(
                player,generation,finalRelease
            );
        }
        world.submitAndWait(
            player,generation,finalRelease,timeoutMillis
        );

        R64OfflineSessionOwnership.Token token=released.get();
        if(token==null||
           token.phase!=R64OfflineSessionOwnership.Phase.RELEASED ||
           world.players().owns(player,generation))
            throw new IOException(
                "R73_LOGOUT_DENIED release/unregister verification failed"
            );
        return new Outcome(token,submitted.captureOrdinal);
    }

    private R73OfflineFinalLogoutCoordinator(){}
}

package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R70 OFFLINE-ONLY explicit World-actor to R64 owner binding for the
 * actual WorldPlayerPersistence coalescing checkpoint queue.
 *
 * At World-thread capture, return the token associated with THAT exact
 * WorldPlayer instance and World actor generation. The token and immutable
 * PlayerSnapshot are frozen together in PendingCheckpoint. Coalescing
 * replaces the entire tuple, never only one component.
 *
 * At the actual I/O commit, reacquire the shared R60 OS FileLock and
 * revalidate R64/R63/R59 before the real repository write. The binding
 * is NOT a real login credential. No LocalSession constructs this gate.
 */
final class R70OfflineCheckpointAuthority
        implements WorldPlayerPersistence.OfflineCheckpointOwnerCapture,
                   WorldPlayerPersistence.OfflineIoWriteAdmission {
    static final class Pause {
        final CountDownLatch arrived=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
    }
    private static final class Binding {
        final long actorGeneration;
        final R64OfflineSessionOwnership.Token owner;
        Binding(long generation,R64OfflineSessionOwnership.Token owner){
            actorGeneration=generation;
            this.owner=owner;
        }
    }

    private final Path journal,accounts,generations,sessions;
    private final Map<WorldPlayer,Binding> actors=
        Collections.synchronizedMap(new IdentityHashMap<>());
    private final R69OfflineIoCommitGate manualGate;
    private final AtomicReference<Pause> pauseNext=new AtomicReference<>();
    private final AtomicInteger attempts=new AtomicInteger();
    private final AtomicInteger successes=new AtomicInteger();
    private final AtomicInteger refusedUnbound=new AtomicInteger();

    R70OfflineCheckpointAuthority(
        Path journal,Path accounts,Path generations,Path sessions
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        manualGate=new R69OfflineIoCommitGate(
            journal,accounts,generations,sessions
        );
    }

    /**
     * Explicit test-only world-actor binding. A later owner takeover does
     * NOT silently update any previously captured PendingCheckpoint:
     * existing queued tuples keep their original owner token.
     */
    void bindForTest(
        WorldPlayer actor,long worldGeneration,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        Objects.requireNonNull(actor,"actor");
        Objects.requireNonNull(owner,"owner");
        if(worldGeneration<=0L||
           worldGeneration!=actor.generation()||
           !owner.username.equals(actor.username())||
           owner.phase!=R64OfflineSessionOwnership.Phase.ACTIVE)
            throw new IOException(
                "R70_BIND_DENIED mismatched World actor or owner"
            );
        actors.put(actor,new Binding(worldGeneration,owner));
    }

    void unbindForTest(WorldPlayer actor){
        actors.remove(actor);
    }

    @Override public R64OfflineSessionOwnership.Token captureOwnerForTest(
        WorldPlayer actor,long generation,PlayerSnapshot snapshot
    ){
        Binding binding=actors.get(actor);
        if(binding==null||
           binding.actorGeneration!=generation||
           !binding.owner.username.equals(snapshot.username())||
           binding.owner.phase!=R64OfflineSessionOwnership.Phase.ACTIVE)
            return null;
        // IMPORTANT: no attempt to acquire the OS lock under the
        // World actor mutationLock. Authority is rechecked on the worker.
        return binding.owner;
    }

    void pauseNextCheckpoint(Pause pause){
        Objects.requireNonNull(pause,"pause");
        if(!pauseNext.compareAndSet(null,pause))
            throw new IllegalStateException(
                "R70_PAUSE_DENIED already armed"
            );
    }

    @Override public void persistCheckpointAtCommit(
        long saveSequence,PlayerSnapshot snapshot,
        PlayerRepository repository,
        R64OfflineSessionOwnership.Token capturedOwner
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(repository,"repository");
        attempts.incrementAndGet();
        if(capturedOwner==null){
            refusedUnbound.incrementAndGet();
            throw new IOException(
                "R70_CHECKPOINT_IO_DENIED missing captured owner"
            );
        }
        if(capturedOwner.phase!=
                R64OfflineSessionOwnership.Phase.ACTIVE||
           !capturedOwner.username.equals(snapshot.username()))
            throw new IOException(
                "R70_CHECKPOINT_IO_DENIED foreign/released token"
            );

        Pause pause=pauseNext.getAndSet(null);
        if(pause!=null){
            pause.arrived.countDown();
            try{
                if(!pause.release.await(15L,TimeUnit.SECONDS))
                    throw new IOException(
                        "R70_CHECKPOINT_IO_DENIED test pause timed out"
                    );
            }catch(InterruptedException error){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "R70_CHECKPOINT_IO_DENIED worker interrupted",error
                );
            }
        }

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.saveOwnedUnderLock(
                journal,accounts,generations,sessions,
                capturedOwner,snapshot,false,repository
            );
            successes.incrementAndGet();
            return null;
        });
    }

    /** Preserve R69's fail-closed MANUAL save behavior in test mode. */
    @Override public void persistAtCommit(
        long saveSequence,PlayerSnapshot snapshot,
        PlayerRepository repository,boolean checkpoint
    )throws IOException{
        if(checkpoint)
            throw new IOException(
                "R70_CHECKPOINT_IO_DENIED missing captured owner"
            );
        manualGate.persistAtCommit(
            saveSequence,snapshot,repository,false
        );
    }

    void bindManualForTest(
        WorldPlayerPersistence.CapturedSave captured,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        manualGate.bindForTest(captured,owner);
    }

    int checkpointAttempts(){return attempts.get();}
    int checkpointSuccesses(){return successes.get();}
    int refusedUnbound(){return refusedUnbound.get();}
    int actorBindings(){return actors.size();}
}

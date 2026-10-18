package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Optional source-only session/writer witness for future verified recipes.
 * Bound to one LocalSession's exact writer and current World owner generation.
 * NOT a globally exclusive account lease or client packet authorization.
 *
 * Locking rule: invoke runBound ONLY inside existing World
 * withOpenPlayerMutationOwnershipIfCurrent, so World -> witness is the
 * lock order. revokeAndAwait runs without holding World lifecycle locks.
 */
final class RecipeSessionWriterGuard {
    @FunctionalInterface
    interface Action<T> {
        T run() throws Exception;
    }

    private final World world;
    private final WorldPlayer player;
    private final long generation;
    private final ServerPacketWriter writer;
    private boolean active=true;

    RecipeSessionWriterGuard(
        World world,WorldPlayer player,long generation,
        ServerPacketWriter writer
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.writer=Objects.requireNonNull(writer,"writer");
        if(generation<=0)
            throw new IllegalArgumentException("invalid writer owner generation");
        // Claim before exposing a usable guard. A logout/revocation must
        // never be bypassed by creating a second witness with the same
        // WorldPlayer generation and writer (or a substituted writer).
        player.claimRecipeWriterGeneration(generation);
        this.generation=generation;
    }

    World world(){ return world; }
    WorldPlayer player(){ return player; }
    long generation(){ return generation; }
    ServerPacketWriter writer(){ return writer; }

    synchronized <T> T runBound(
        World expectedWorld,WorldPlayer expectedPlayer,
        long expectedGeneration,ServerPacketWriter expectedWriter,
        Action<T> task
    )throws Exception{
        Objects.requireNonNull(task,"task");
        if(!active)
            throw new IllegalStateException("RECIPE_SESSION_WRITER_REVOKED");
        if(world!=expectedWorld||player!=expectedPlayer||
           generation!=expectedGeneration||writer!=expectedWriter)
            throw new IllegalStateException(
                "RECIPE_SESSION_WRITER_IDENTITY_MISMATCH");
        if(writer.terminal())
            throw new IOException("terminal recipe session writer");
        return task.run();
    }

    /** Waits for any in-flight guarded recipe action, then rejects all new work. */
    synchronized void revokeAndAwait(){
        active=false;
    }

    synchronized boolean activeForTest(){
        return active;
    }
}

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
 * lock order. Teardown may revoke without World locks; World-owned
 * logout also takes the same World -> witness order.
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
    // Java monitors are reentrant; do not allow a callback on this same
    // session writer to recursively start a second recipe authority action.
    private boolean inFlight;

    RecipeSessionWriterGuard(
        World world,WorldPlayer player,long generation,
        ServerPacketWriter writer
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.writer=Objects.requireNonNull(writer,"writer");
        if(generation<=0)
            throw new IllegalArgumentException("invalid writer owner generation");
        // A terminal writer has no valid recipe publication authority and
        // must not consume the player's irreversible generation claim.
        if(writer.terminal())
            throw new IllegalStateException(
                "RECIPE_TERMINAL_WRITER_CANNOT_CLAIM");
        // Claim only under the existing World lifecycle and exact-player
        // mutation authority. A foreign World must not preempt the real
        // owner's one-time witness or create an unowned token.
        final boolean owned;
        try{
            owned=world.withOpenPlayerMutationOwnershipIfCurrent(
                player,generation,
                ()->player.claimRecipeWriterGeneration(generation));
        }catch(RuntimeException|Error failure){
            throw failure;
        }catch(Exception unexpected){
            throw new IllegalStateException(
                "RECIPE_SESSION_WRITER_BINDING_FAILED",unexpected);
        }
        if(!owned)
            throw new IllegalStateException(
                "RECIPE_WORLD_OWNER_GENERATION_NOT_CURRENT");
        // Same generation can never be re-minted after revocation, even
        // with the exact same writer or a substituted writer.
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
        // A player monitor alone is not an ownership lease. Verify
        // current-thread World lifecycle lock, exact player's mutation
        // lock, registry membership AND current generation together.
        if(!world.currentThreadHoldsOpenPlayerMutationOwnership(
                player,generation))
            throw new IllegalStateException(
                "RECIPE_WORLD_MUTATION_OWNERSHIP_REQUIRED");
        if(writer.terminal()){
            // Terminal transport is never a recoverable retry. Revoke even
            // if no recipe packet was queued and prohibit same-gen remint.
            active=false;
            throw new IOException("terminal recipe session writer");
        }
        if(inFlight)
            throw new IllegalStateException(
                "RECIPE_REENTRANT_WRITER_ACTION_DENIED");
        inFlight=true;
        try{
            return task.run();
        }finally{
            // The transport can die INSIDE a previously admitted action.
            // Revoke when it leaves as well as on the pre-entry check.
            // A terminal writer may never regain this generation claim.
            if(writer.terminal())active=false;
            inFlight=false;
        }
    }

    /** Waits for any in-flight guarded recipe action, then rejects all new work. */
    synchronized void revokeAndAwait(){
        active=false;
    }

    synchronized boolean activeForTest(){
        return active;
    }
}

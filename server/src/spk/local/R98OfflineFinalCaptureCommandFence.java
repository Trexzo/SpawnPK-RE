package spk.local;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * R98 OFFLINE TEST ONLY. Reuses the R74 WorldCommandInbox admission seam:
 * every command is checked at enqueue AND in the actual player mutation
 * critical section at dispatch. A World actor atomically marks ingress
 * quiesced and captures its final immutable snapshot in one command.
 *
 * An already-queued command is not grandfathered past the final capture.
 * No production LocalSession, socket, or World.shared installs this gate.
 * Other mutable paths (realtime, plugins, direct state access) are outside
 * this command-inbox-specific regression.
 */
final class R98OfflineFinalCaptureCommandFence
        implements WorldCommandInbox.OfflineCommandAdmission {
    private static final class State {
        boolean quiesced;
        WorldPlayerPersistence.SaveTicket finalTicket;
    }

    private final World world;
    private final IdentityHashMap<WorldPlayer,Map<Long,State>> owners=
        new IdentityHashMap<>();

    R98OfflineFinalCaptureCommandFence(World world){
        this.world=Objects.requireNonNull(world,"world");
    }

    void installForTest(){
        // The existing R72 terminal-capture fence prevents later
        // checkpoints from being submitted for the same generation.
        // Both hooks are offline and must be installed before start.
        world.persistence().installOfflineTerminalCaptureFenceForTest();
        // R74 allows exactly one offline command-admission policy per
        // isolated World, so this cannot silently replace another policy.
        world.commands().installOfflineCommandAdmissionForTest(this);
    }

    private State state(WorldPlayer actor,long generation){
        Map<Long,State> generations=owners.get(actor);
        if(generations==null){
            generations=new HashMap<>();
            owners.put(actor,generations);
        }
        State result=generations.get(generation);
        if(result==null){
            result=new State();
            generations.put(generation,result);
        }
        return result;
    }

    @Override public boolean permits(
        WorldPlayer actor,long generation,
        WorldCommandInbox.Action action){
        synchronized(owners){
            // Absent a freeze, existing commands continue normally.
            // No trusted bypass is installed for this fixture.
            Map<Long,State> generations=owners.get(actor);
            State current=generations==null?null:
                generations.get(generation);
            return current==null||!current.quiesced;
        }
    }

    /**
     * Must be invoked inside the current, actual World command handler.
     * The World dispatcher owns lifecycleLock+player.mutationLock while
     * executing this method, so no other inbox action can run between
     * the quiescence flip and the capture.
     */
    WorldPlayerPersistence.CapturedSave
            quiesceAndCaptureFinalInsideActorForTest(
        WorldPlayer actor,long generation,
        String username,int petAccessoryItem,String tag,String reason
    )throws IOException{
        Objects.requireNonNull(actor,"actor");
        Objects.requireNonNull(username,"username");
        if(!world.pulse().inExecutionContext()||
                !Thread.holdsLock(actor.mutationLock())||
                !world.players().owns(actor,generation)||
                !username.equals(actor.username()))
            throw new IOException(
                "R98_FINAL_CAPTURE_DENIED wrong World actor");

        synchronized(owners){
            State current=state(actor,generation);
            if(current.quiesced)
                throw new IOException(
                    "R98_FINAL_CAPTURE_DENIED already quiesced");
            // Sticky fail-closed marker, including when capture throws.
            current.quiesced=true;
        }

        // Capture only on the World actor. Enqueue with backpressure
        // *after* this handler returns, outside the World execution thread.
        WorldPlayerPersistence.CapturedSave captured=
            world.persistence().captureDeferredFinalSave(
                username,actor,generation,petAccessoryItem,tag,reason);
        WorldPlayerPersistence.SaveTicket ticket=captured.ticket;
        if(!captured.finalSnapshot||captured.owner!=actor||
                captured.expectedGeneration!=generation||
                ticket.provenance==null||
                ticket.provenance.capturedOwner!=actor||
                ticket.provenance.worldPlayerGeneration!=generation||
                ticket.provenance.captureOrdinal!=ticket.captureOrdinal)
            throw new IOException(
                "R98_FINAL_CAPTURE_DENIED missing World provenance");
        synchronized(owners){
            state(actor,generation).finalTicket=ticket;
        }
        return captured;
    }

    void requireCompletedFinalCaptureForTest(
        WorldPlayer actor,long generation,long ordinal)throws IOException{
        synchronized(owners){
            Map<Long,State> generations=owners.get(actor);
            State current=generations==null?null:
                generations.get(generation);
            if(current==null||!current.quiesced||
                    current.finalTicket==null||
                    current.finalTicket.captureOrdinal!=ordinal||
                    ordinal<1L||
                    !current.finalTicket.completion.isDone()||
                    current.finalTicket.completion.isCompletedExceptionally()||
                    current.finalTicket.completion.isCancelled())
                throw new IOException(
                    "R98_RETIRE_DENIED no successful quiesced final capture");
        }
    }

    boolean quiescedForTest(WorldPlayer actor,long generation){
        synchronized(owners){
            Map<Long,State> generations=owners.get(actor);
            State current=generations==null?null:
                generations.get(generation);
            return current!=null&&current.quiesced;
        }
    }
}

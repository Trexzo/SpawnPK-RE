package spk.local;

import java.util.Objects;

/**
 * P5.30 explicitly opted-in, in-JVM shared LocalLab PvM admission.
 *
 * One source-only player-bound lease arbitrates canonical NPC basic
 * clicks and isolated Wind Strike. It is intentionally NOT an original
 * SpawnPK combat policy, not a lock on World/player/NPC, not a cross-JVM
 * account lease, and not durable.
 *
 * No external Java monitors are acquired by begin/commit/exit, avoiding
 * inverse lock ordering between melee (canonical attack monitor first)
 * and magic (World lifecycle/player mutation first).
 */
final class LocalLabSharedPvmActionGate {
    enum Kind { CANONICAL_NPC_BASIC, WIND_STRIKE }

    static final class Ticket {
        final World world;
        final long generation;
        final Kind kind;
        final long tick;
        private boolean mayHaveCommitted;
        private boolean committed;

        private Ticket(World world,long generation,Kind kind,long tick){
            this.world=world;
            this.generation=generation;
            this.kind=kind;
            this.tick=tick;
        }
    }

    static final class Admission {
        final Ticket ticket;
        final String denial;
        private Admission(Ticket ticket,String denial){
            this.ticket=ticket;
            this.denial=denial;
        }
        static Admission allowed(Ticket ticket){
            return new Admission(ticket,null);
        }
        static Admission blocked(String reason){
            return new Admission(null,reason);
        }
    }

    private World installedWorld;
    private long installedGeneration=-1L;
    private Ticket active;
    private boolean faulted;
    private boolean hasCommittedTick;
    private long lastCommittedTick=-1L;

    /**
     * Caller MUST have verified exact World/player ownership. A new
     * session generation can install a fresh policy; a stale session
     * cannot rebind it. No downgrade/reinstall in the same generation.
     */
    synchronized void install(World world,long generation){
        Objects.requireNonNull(world,"world");
        if(generation<0L)throw new IllegalArgumentException("generation");
        if(installedWorld==world&&installedGeneration==generation)
            throw new IllegalStateException(
                "P530_SHARED_PVM_ALREADY_INSTALLED");
        if(active!=null)
            throw new IllegalStateException(
                "P530_SHARED_PVM_IN_FLIGHT_INSTALL");
        installedWorld=world;
        installedGeneration=generation;
        faulted=false;
        hasCommittedTick=false;
        lastCommittedTick=-1L;
    }

    synchronized Admission begin(
        World world,long generation,Kind kind,long tick
    ){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(kind,"kind");
        if(generation<0L||tick<0L)
            throw new IllegalArgumentException("generation/tick");
        if(installedWorld!=world||installedGeneration!=generation)
            return Admission.allowed(null); // default OFF
        if(faulted)
            return Admission.blocked(
                "REJECTED_SHARED_PVM_ACTION_FAULTED");
        if(active!=null)
            return Admission.blocked(
                "REJECTED_SHARED_PVM_ACTION_IN_FLIGHT");
        if(hasCommittedTick){
            if(tick<lastCommittedTick)
                return Admission.blocked(
                    "REJECTED_SHARED_PVM_TICK_REGRESSION");
            if(tick==lastCommittedTick)
                return Admission.blocked(
                    "REJECTED_SHARED_PVM_ACTION_SAME_TICK");
        }
        Ticket ticket=new Ticket(world,generation,kind,tick);
        active=ticket;
        return Admission.allowed(ticket);
    }

    /**
     * Record uncertain entry immediately before an HP-changing call.
     * A thrown exception after this point poisons the generation rather
     * than potentially replaying an already-applied melee hit.
     */
    synchronized void mayHaveCommitted(Ticket ticket){
        if(ticket==null)return; // default OFF
        requireActive(ticket);
        ticket.mayHaveCommitted=true;
    }

    /** Only call when this specific action's HP/state has committed. */
    synchronized void committed(Ticket ticket){
        if(ticket==null)return;
        requireActive(ticket);
        if(ticket.committed)
            throw new IllegalStateException(
                "P530_SHARED_PVM_DOUBLE_COMMIT");
        if(hasCommittedTick&&ticket.tick<=lastCommittedTick)
            throw new IllegalStateException(
                "P530_SHARED_PVM_NONMONOTONIC_COMMIT");
        ticket.committed=true;
        hasCommittedTick=true;
        lastCommittedTick=ticket.tick;
    }

    /**
     * Must be invoked in the caller's finally, including exceptions.
     * An interrupted potentially HP-changing operation cannot release
     * the slot as READY unless a semantic commit is acknowledged.
     */
    synchronized void exit(Ticket ticket){
        if(ticket==null)return;
        if(active!=ticket)return; // stale ticket has no authority
        if(ticket.mayHaveCommitted&&!ticket.committed)
            faulted=true;
        active=null;
    }

    synchronized boolean faulted(World world,long generation){
        return installedWorld==world&&
            installedGeneration==generation&&faulted;
    }

    synchronized boolean enabled(World world,long generation){
        return installedWorld==world&&installedGeneration==generation;
    }

    private void requireActive(Ticket ticket){
        if(active!=ticket||
           ticket.world!=installedWorld||
           ticket.generation!=installedGeneration)
            throw new IllegalStateException(
                "P530_SHARED_PVM_STALE_TICKET");
    }
}

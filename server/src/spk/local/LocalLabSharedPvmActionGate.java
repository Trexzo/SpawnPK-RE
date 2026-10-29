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
        private boolean confirmedPreDamageRejection;
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
    // P5.32: the committing style's *known source-policy* recovery
    // interval, carried across melee and magic by this opt-in gate.
    private int lastCommittedRecoveryTicks=1;

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
        lastCommittedRecoveryTicks=1;
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
            // Both ticks are nonnegative and monotonic here.
            // Avoid adding to lastCommittedTick near Long.MAX_VALUE.
            if(tick-lastCommittedTick<lastCommittedRecoveryTicks)
                return Admission.blocked(
                    "REJECTED_SHARED_PVM_ACTION_RECOVERING");
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
        if(ticket.confirmedPreDamageRejection)
            throw new IllegalStateException(
                "P530_SHARED_PVM_REJECTED_TICKET_NOT_REUSABLE");
        ticket.mayHaveCommitted=true;
    }

    /**
     * Caller has received a TYPED resolver rejection that is known to
     * happen strictly before NpcLifecycleService changes any HP:
     * range admission, stale attacker/target, or stale lifecycle.
     * All other exceptions remain uncertain and must fault closed.
     */
    synchronized void confirmedPreDamageRejection(Ticket ticket){
        if(ticket==null)return;
        requireActive(ticket);
        if(!ticket.mayHaveCommitted||ticket.committed||
           ticket.confirmedPreDamageRejection)
            throw new IllegalStateException(
                "P530_SHARED_PVM_INVALID_PRE_DAMAGE_PROOF");
        ticket.confirmedPreDamageRejection=true;
    }

    /** Compatibility for existing one-tick source fixtures. */
    synchronized void committed(Ticket ticket){
        committed(ticket,1);
    }

    /**
     * Only after source-side HP/rune semantics commit, record the exact
     * duration asserted by THAT action's existing LocalLab policy.
     * Never let a declined or uncertain attack extend the timer.
     * This is not an original SpawnPK attack interval or animation.
     */
    synchronized void committed(Ticket ticket,int recoveryTicks){
        if(ticket==null)return; // Default OFF, including production.
        requireActive(ticket);
        if(recoveryTicks<1||recoveryTicks>64)
            throw new IllegalArgumentException(
                "P532_INVALID_SHARED_SOURCE_RECOVERY_TICKS");
        if(ticket.committed||ticket.confirmedPreDamageRejection)
            throw new IllegalStateException(
                "P530_SHARED_PVM_DOUBLE_OR_REJECTED_COMMIT");
        if(hasCommittedTick&&ticket.tick<=lastCommittedTick)
            throw new IllegalStateException(
                "P530_SHARED_PVM_NONMONOTONIC_COMMIT");
        ticket.committed=true;
        hasCommittedTick=true;
        lastCommittedTick=ticket.tick;
        lastCommittedRecoveryTicks=recoveryTicks;
    }

    /**
     * Must be invoked in the caller's finally, including exceptions.
     * An interrupted potentially HP-changing operation cannot release
     * the slot as READY unless a semantic commit is acknowledged.
     */
    synchronized void exit(Ticket ticket){
        if(ticket==null)return;
        if(active!=ticket)return; // stale ticket has no authority
        if(ticket.mayHaveCommitted&&!ticket.committed&&
           !ticket.confirmedPreDamageRejection)
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

    /**
     * Read-only optimization for the default-OFF canonical click path.
     * Without an installed policy the historical World-closed gate
     * must run BEFORE the generation supplier is evaluated.
     * The actual opt-in generation still gets checked by begin().
     */
    synchronized boolean installedForWorld(World world){
        return installedWorld==world;
    }

    private void requireActive(Ticket ticket){
        if(active!=ticket||
           ticket.world!=installedWorld||
           ticket.generation!=installedGeneration)
            throw new IllegalStateException(
                "P530_SHARED_PVM_STALE_TICKET");
    }
}

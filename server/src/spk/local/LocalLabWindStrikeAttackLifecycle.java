package spk.local;

import java.util.Objects;

/**
 * P5.29 source-only, in-JVM attack transaction phases.
 *
 * One canonical WorldPlayer owns this machine across all Wind Strike
 * wrappers. It is NOT a projectile scheduler, real SpawnPK spell timing,
 * original combat state, a durable transaction journal, or account lease.
 *
 * Caller MUST hold the current World/player generation ownership barrier
 * and the WorldPlayer mutation lock for the entire attempt.
 */
final class LocalLabWindStrikeAttackLifecycle {
    enum Phase {
        READY,
        VALIDATING,
        PUBLISHING,
        PACKETS_ADMITTED,
        RECOVERING,
        FAULTED
    }

    static final class Ticket {
        final World world;
        final long generation;
        final long tick;

        private Ticket(World world,long generation,long tick){
            this.world=world;
            this.generation=generation;
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
        static Admission accepted(Ticket ticket){
            return new Admission(ticket,null);
        }
        static Admission rejected(String reason){
            return new Admission(null,reason);
        }
    }

    private World ownerWorld;
    private long ownerGeneration=-1L;
    private Phase phase=Phase.READY;
    private Ticket active;

    /**
     * Source-only state is re-bound only to a *verified* current owner
     * generation by the caller. A retired owner cannot clear a fault.
     */
    synchronized Admission begin(
        World world,long generation,long tick
    ){
        Objects.requireNonNull(world,"world");
        if(generation<0L||tick<0L)
            throw new IllegalArgumentException(
                "invalid Wind Strike owner/tick");

        if(active!=null)
            return Admission.rejected(
                "REJECTED_WIND_STRIKE_ATTACK_IN_FLIGHT");

        if(ownerWorld!=world||ownerGeneration!=generation){
            ownerWorld=world;
            ownerGeneration=generation;
            phase=Phase.READY;
        }

        if(phase==Phase.FAULTED)
            return Admission.rejected(
                "REJECTED_WIND_STRIKE_ATTACK_FAULTED");

        // WorldPlayer owns the separate committed-tick and recovery
        // admission. This phase only controls an individual attempt.
        Ticket ticket=new Ticket(world,generation,tick);
        active=ticket;
        phase=Phase.VALIDATING;
        return Admission.accepted(ticket);
    }

    synchronized void publishing(Ticket ticket){
        requireCurrent(ticket,Phase.VALIDATING);
        phase=Phase.PUBLISHING;
    }

    /**
     * The queue has already accepted the exact S2C packets. From here,
     * aborting must poison this owner generation instead of allowing
     * a retry which might double-spend or double-hit.
     */
    synchronized void packetsAdmitted(Ticket ticket){
        requireCurrent(ticket,Phase.PUBLISHING);
        phase=Phase.PACKETS_ADMITTED;
    }

    /** Call ONLY after both NPC damage and rune postimage have committed. */
    synchronized void hitCommitted(Ticket ticket){
        requireCurrent(ticket,Phase.PACKETS_ADMITTED);
        phase=Phase.RECOVERING;
        active=null;
    }

    /**
     * Safe on the finally path, including reentrant callback failures.
     * Before queue admission a failed attempt releases its slot.
     * After queue admission and before semantic completion the owner
     * enters a closed fault state until *new* World/player registration.
     */
    synchronized void exit(Ticket ticket){
        if(active!=ticket)return;
        phase=phase==Phase.PACKETS_ADMITTED
            ?Phase.FAULTED
            :Phase.READY;
        active=null;
    }

    /** Read-only instrumentation for deterministic source regression. */
    synchronized Phase phase(World world,long generation){
        if(world!=ownerWorld||generation!=ownerGeneration)
            return Phase.READY;
        return phase;
    }

    private void requireCurrent(Ticket ticket,Phase required){
        if(ticket==null||active!=ticket||
           ticket.world!=ownerWorld||
           ticket.generation!=ownerGeneration||
           phase!=required)
            throw new IllegalStateException(
                "P529_WIND_STRIKE_PHASE_TRANSITION_REQUIRED_"+
                required+"_ACTUAL_"+phase);
    }
}

package spk.local;

/**
 * Canonical player lifecycle state.
 *
 * Lifecycle timing in this class is LocalLab-defined until exact SpawnPK server
 * death/respawn rules are recovered. Provenance must remain explicit.
 */
final class PlayerLifecycleState {
    enum Phase {
        ALIVE,
        DEAD_WAITING_RESPAWN
    }

    static final String AUTHORITY="CUSTOM_LOCALLAB";

    private Phase phase=Phase.ALIVE;
    private long deathTick=-1L;
    private long respawnTick=-1L;
    private long deathSequence;
    private String cause="NONE";

    Phase phase(){return phase;}
    boolean alive(){return phase==Phase.ALIVE;}
    boolean dead(){return phase==Phase.DEAD_WAITING_RESPAWN;}
    long deathTick(){return deathTick;}
    long respawnTick(){return respawnTick;}
    long deathSequence(){return deathSequence;}
    String cause(){return cause;}

    void markDead(
        long worldTick,
        long respawnDelayTicks,
        String cause
    ){
        if(respawnDelayTicks<0)
            throw new IllegalArgumentException(
                "respawnDelayTicks="+respawnDelayTicks
            );

        final long nextDeathSequence;
        try{
            nextDeathSequence=
                Math.addExact(
                    deathSequence,
                    1L
                );
        }catch(ArithmeticException overflow){
            throw new IllegalStateException(
                "death sequence overflow",
                overflow
            );
        }

        this.phase=Phase.DEAD_WAITING_RESPAWN;
        this.deathSequence=nextDeathSequence;
        this.deathTick=worldTick;
        this.respawnTick=worldTick+respawnDelayTicks;
        this.cause=
            cause==null||cause.isEmpty()
                ?"UNSPECIFIED"
                :cause;
    }

    boolean dueRespawn(long worldTick){
        return dead()&&worldTick>=respawnTick;
    }

    void markRespawned(){
        phase=Phase.ALIVE;
        deathTick=-1L;
        respawnTick=-1L;
        cause="NONE";
    }

    @Override public String toString(){
        return "PlayerLifecycleState{phase="+phase+
            ",deathTick="+deathTick+
            ",respawnTick="+respawnTick+
            ",deathSequence="+deathSequence+
            ",cause="+cause+
            ",authority="+AUTHORITY+"}";
    }
}

package spk.local;

/**
 * Canonical player lifecycle state.
 *
 * Lifecycle timing in this class is LocalLab-defined until exact SpawnPK server
 * death/respawn rules are recovered. Provenance must remain explicit.
 */
final class PlayerLifecycleState {
    static final class DeathAttribution {
        final long deathSequence;
        final EntityId attackerId;
        final long attackerGeneration;
        final String attackerUsername;
        final String context;

        private DeathAttribution(
            long deathSequence,
            EntityId attackerId,
            long attackerGeneration,
            String attackerUsername,
            String context
        ){
            this.deathSequence=deathSequence;
            this.attackerId=attackerId;
            this.attackerGeneration=attackerGeneration;
            this.attackerUsername=cleanOptionalUsername(
                attackerUsername
            );
            this.context=context;
        }

        @Override public String toString(){
            return "DeathAttribution{deathSequence="+deathSequence+
                ",attackerId="+attackerId+
                ",attackerGeneration="+attackerGeneration+
                ",attackerUsername="+attackerUsername+
                ",context="+context+"}";
        }
    }

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
    private DeathAttribution deathAttribution;

    Phase phase(){return phase;}
    boolean alive(){return phase==Phase.ALIVE;}
    boolean dead(){return phase==Phase.DEAD_WAITING_RESPAWN;}
    long deathTick(){return deathTick;}
    long respawnTick(){return respawnTick;}
    long deathSequence(){return deathSequence;}
    String cause(){return cause;}
    DeathAttribution deathAttribution(){return deathAttribution;}

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
        this.deathAttribution=null;
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

    void attributeCurrentDeath(
        long expectedDeathSequence,
        EntityId attackerId,
        long attackerGeneration,
        String context
    ){
        attributeCurrentDeath(
            expectedDeathSequence,
            attackerId,
            attackerGeneration,
            null,
            context
        );
    }

    void attributeCurrentDeath(
        long expectedDeathSequence,
        EntityId attackerId,
        long attackerGeneration,
        String attackerUsername,
        String context
    ){
        if(!dead()||
           deathSequence!=expectedDeathSequence)
            throw new IllegalStateException(
                "death attribution sequence changed expected="+
                expectedDeathSequence+
                " actual="+deathSequence+
                " phase="+phase
            );

        if(attackerId==null)
            throw new NullPointerException(
                "attackerId"
            );

        if(attackerGeneration<=0L)
            throw new IllegalArgumentException(
                "attackerGeneration="+
                attackerGeneration
            );

        if(context==null||
           context.trim().isEmpty())
            throw new IllegalArgumentException(
                "context"
            );

        deathAttribution=
            new DeathAttribution(
                deathSequence,
                attackerId,
                attackerGeneration,
                attackerUsername,
                context.trim()
            );
    }

    private static String cleanOptionalUsername(
        String value
    ){
        if(value==null)
            return null;

        String clean=value.trim();
        return clean.isEmpty()
            ?null
            :clean;
    }

    void markRespawned(){
        phase=Phase.ALIVE;
        deathTick=-1L;
        respawnTick=-1L;
        cause="NONE";
        deathAttribution=null;
    }

    @Override public String toString(){
        return "PlayerLifecycleState{phase="+phase+
            ",deathTick="+deathTick+
            ",respawnTick="+respawnTick+
            ",deathSequence="+deathSequence+
            ",cause="+cause+
            ",deathAttribution="+deathAttribution+
            ",authority="+AUTHORITY+"}";
    }
}

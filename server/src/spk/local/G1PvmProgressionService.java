package spk.local;

import java.util.*;

/**
 * Explicit LocalLab PvM progression for the G1 vertical slice.
 *
 * A terminal Monster Spawner death grants one semantic PvM kill/progression
 * point to the exact current recipient generation. No item, coin, XP-rate or
 * original SpawnPK reward table is inferred here.
 */
final class G1PvmProgressionService {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G1_PVM_PROGRESSION_V1";
    static final String NAMESPACE=
        "g1_pvm";

    static final class Result {
        final boolean granted;
        final boolean replay;
        final String reason;
        final long kills;
        final long points;
        final boolean persistenceRequested;

        Result(
            boolean granted,
            boolean replay,
            String reason,
            long kills,
            long points,
            boolean persistenceRequested
        ){
            this.granted=granted;
            this.replay=replay;
            this.reason=reason;
            this.kills=kills;
            this.points=points;
            this.persistenceRequested=persistenceRequested;
        }

        @Override public String toString(){
            return "Result{granted="+granted+
                ",replay="+replay+
                ",reason="+reason+
                ",kills="+kills+
                ",points="+points+
                ",persistenceRequested="+
                persistenceRequested+"}";
        }
    }

    private static final class DeathKey {
        final EntityId npcId;
        final long deathTick;

        DeathKey(
            EntityId npcId,
            long deathTick
        ){
            this.npcId=
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                );
            this.deathTick=deathTick;
        }

        @Override public boolean equals(
            Object other
        ){
            if(!(other instanceof DeathKey))
                return false;
            DeathKey key=(DeathKey)other;
            return npcId.equals(key.npcId)&&
                deathTick==key.deathTick;
        }

        @Override public int hashCode(){
            return 31*npcId.hashCode()+
                Long.hashCode(deathTick);
        }
    }

    private final World world;
    private final LinkedHashMap<DeathKey,Result>
        settled=new LinkedHashMap<>();

    G1PvmProgressionService(
        World world
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    synchronized Result settle(
        String recipientRef,
        EntityId npcId,
        long deathTick
    ){
        String recipient=
            PartyService.requireRef(
                recipientRef
            );

        if(deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+deathTick
            );

        DeathKey key=
            new DeathKey(
                npcId,
                deathTick
            );

        Result prior=
            settled.get(
                key
            );

        if(prior!=null)
            return new Result(
                false,
                true,
                "REPLAY",
                prior.kills,
                prior.points,
                false
            );

        WorldPlayer player=
            world.players().byName(
                recipient
            );

        if(player==null)
            return new Result(
                false,
                false,
                "RECIPIENT_OFFLINE",
                0L,
                0L,
                false
            );

        long generation=
            player.generation();

        final Result[] outcome={null};

        try{
            boolean current=
                world.withOpenPlayerOwnershipIfCurrent(
                    player,
                    generation,
                    ()->outcome[0]=
                        mutateAndPersist(
                            player,
                            generation,
                            recipient
                        )
                );

            if(!current)
                return new Result(
                    false,
                    false,
                    "RECIPIENT_GENERATION_STALE",
                    0L,
                    0L,
                    false
                );
        }catch(java.io.IOException impossible){
            throw new IllegalStateException(
                "PvM progression ownership action raised unexpected IO",
                impossible
            );
        }

        Result applied=
            Objects.requireNonNull(
                outcome[0],
                "PvM progression result"
            );

        if(applied.granted)
            settled.put(
                key,
                applied
            );

        return applied;
    }

    private Result mutateAndPersist(
        WorldPlayer player,
        long generation,
        String recipient
    ){
        synchronized(player.mutationLock()){
            SortedMap<String,String> before=
                player.snapshotExtensions()
                    .namespace(
                        NAMESPACE
                    );

            long kills=
                parseCounter(
                    before,
                    "kills"
                );
            long points=
                parseCounter(
                    before,
                    "points"
                );

            final long nextKills;
            final long nextPoints;

            try{
                nextKills=
                    Math.addExact(
                        kills,
                        1L
                    );
                nextPoints=
                    Math.addExact(
                        points,
                        1L
                    );
            }catch(ArithmeticException overflow){
                return new Result(
                    false,
                    false,
                    "COUNTER_OVERFLOW",
                    kills,
                    points,
                    false
                );
            }

            TreeMap<String,String> next=
                new TreeMap<>(
                    before
                );
            next.put(
                "version",
                "1"
            );
            next.put(
                "authority",
                AUTHORITY
            );
            next.put(
                "kills",
                Long.toString(
                    nextKills
                )
            );
            next.put(
                "points",
                Long.toString(
                    nextPoints
                )
            );

            player.snapshotExtensions()
                .replaceNamespace(
                    NAMESPACE,
                    next
                );

            boolean saveRequested=false;

            if(LocalAccountProfiles
                    .isPersistent(
                        recipient
                    )){
                try{
                    world.persistence()
                        .captureAndSave(
                            recipient,
                            player,
                            generation,
                            player.petAccessoryState()
                                .activeItem(),
                            "[g1-pvm] ",
                            "G1_PVM_PROGRESSION"
                        );
                    saveRequested=true;
                }catch(Throwable failure){
                    player.snapshotExtensions()
                        .replaceNamespace(
                            NAMESPACE,
                            before
                        );
                    return new Result(
                        false,
                        false,
                        "PERSISTENCE_REQUEST_FAILED",
                        kills,
                        points,
                        false
                    );
                }
            }

            return new Result(
                true,
                false,
                "GRANTED",
                nextKills,
                nextPoints,
                saveRequested
            );
        }
    }

    private static long parseCounter(
        SortedMap<String,String> state,
        String key
    ){
        if(state.isEmpty())
            return 0L;

        String version=
            state.get(
                "version"
            );
        String authority=
            state.get(
                "authority"
            );

        if(!"1".equals(version)||
           !AUTHORITY.equals(authority))
            throw new IllegalStateException(
                "invalid G1 PvM progression namespace"
            );

        String value=
            state.get(
                key
            );

        if(value==null)
            throw new IllegalStateException(
                "missing G1 PvM counter "+
                key
            );

        final long parsed;

        try{
            parsed=
                Long.parseLong(
                    value
                );
        }catch(NumberFormatException failure){
            throw new IllegalStateException(
                "invalid G1 PvM counter "+
                key,
                failure
            );
        }

        if(parsed<0L)
            throw new IllegalStateException(
                "negative G1 PvM counter "+
                key
            );

        return parsed;
    }

    synchronized int settledCount(){
        return settled.size();
    }
}

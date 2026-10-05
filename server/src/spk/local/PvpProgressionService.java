package spk.local;

import java.util.*;

/**
 * Persistent LocalLab-owned PvP progression.
 *
 * This deliberately owns counters only. Reward economics, PK-point values,
 * anti-farm policy and original SpawnPK formulas remain separate concerns.
 */
final class PvpProgressionService {
    static final String NAMESPACE="pvp.progression";
    static final String AUTHORITY="LOCAL_LAB_POLICY_PVP_PROGRESS_V1";

    static final class Snapshot {
        final long kills;
        final long deaths;
        final long revision;

        Snapshot(long kills,long deaths,long revision){
            this.kills=kills;
            this.deaths=deaths;
            this.revision=revision;
        }

        @Override public String toString(){
            return "PvpProgression{kills="+kills+
                ",deaths="+deaths+
                ",revision="+revision+
                ",authority="+AUTHORITY+"}";
        }
    }

    private final WorldPlayer player;

    PvpProgressionService(WorldPlayer player){
        this.player=Objects.requireNonNull(player,"player");
    }

    Snapshot snapshot(){
        synchronized(player.mutationLock()){
            return decodeLocked();
        }
    }

    Snapshot recordKill(){
        return mutate(true,false);
    }

    Snapshot recordDeath(){
        return mutate(false,true);
    }

    private Snapshot mutate(boolean kill,boolean death){
        synchronized(player.mutationLock()){
            Snapshot before=decodeLocked();
            long kills=before.kills;
            long deaths=before.deaths;

            try{
                if(kill)kills=Math.addExact(kills,1L);
                if(death)deaths=Math.addExact(deaths,1L);
                long revision=Math.addExact(before.revision,1L);

                TreeMap<String,String> next=new TreeMap<>();
                next.put("kills",Long.toString(kills));
                next.put("deaths",Long.toString(deaths));
                next.put("revision",Long.toString(revision));

                player.snapshotExtensions().replaceNamespace(
                    NAMESPACE,
                    next
                );

                return new Snapshot(kills,deaths,revision);
            }catch(ArithmeticException overflow){
                throw new IllegalStateException(
                    "PvP progression overflow player="+player.id(),
                    overflow
                );
            }
        }
    }

    private Snapshot decodeLocked(){
        SortedMap<String,String> values=
            player.snapshotExtensions().namespace(NAMESPACE);

        return new Snapshot(
            nonNegative(values.get("kills"),"kills"),
            nonNegative(values.get("deaths"),"deaths"),
            nonNegative(values.get("revision"),"revision")
        );
    }

    private static long nonNegative(String value,String key){
        if(value==null||value.isEmpty())return 0L;

        final long parsed;
        try{
            parsed=Long.parseLong(value);
        }catch(NumberFormatException error){
            throw new IllegalStateException(
                "Invalid PvP progression "+key+"="+value,
                error
            );
        }

        if(parsed<0L)
            throw new IllegalStateException(
                "Negative PvP progression "+key+"="+parsed
            );

        return parsed;
    }
}

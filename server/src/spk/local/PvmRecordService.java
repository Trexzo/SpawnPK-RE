package spk.local;

import java.util.*;

final class PvmRecordService {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G1_PVM_RECORD_V1";
    static final String NAMESPACE=
        "pvm-record";
    static final String VERSION="1";

    static final class Snapshot {
        final long kills;

        Snapshot(long kills){
            this.kills=kills;
        }

        @Override public String toString(){
            return "PvmRecord{kills="+kills+"}";
        }
    }

    static final class Receipt {
        final boolean applied;
        final boolean replayed;
        final String reason;
        final EntityId npcId;
        final long deathTick;
        final long kills;

        private Receipt(
            boolean applied,
            boolean replayed,
            String reason,
            EntityId npcId,
            long deathTick,
            long kills
        ){
            this.applied=applied;
            this.replayed=replayed;
            this.reason=reason;
            this.npcId=npcId;
            this.deathTick=deathTick;
            this.kills=kills;
        }

        static Receipt applied(
            EntityId npcId,
            long deathTick,
            long kills
        ){
            return new Receipt(
                true,false,"APPLIED",
                npcId,deathTick,kills
            );
        }

        static Receipt replay(
            EntityId npcId,
            long deathTick,
            long kills
        ){
            return new Receipt(
                false,true,"REPLAY",
                npcId,deathTick,kills
            );
        }

        static Receipt rejected(
            String reason,
            EntityId npcId,
            long deathTick,
            long kills
        ){
            return new Receipt(
                false,false,reason,
                npcId,deathTick,kills
            );
        }
    }

    private static final class KillIdentity {
        final EntityId npcId;
        final long deathTick;

        KillIdentity(
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
            if(!(other instanceof KillIdentity))
                return false;

            KillIdentity that=
                (KillIdentity)other;

            return npcId.equals(that.npcId)&&
                deathTick==that.deathTick;
        }

        @Override public int hashCode(){
            return 31*npcId.hashCode()+
                Long.hashCode(deathTick);
        }
    }

    private final World world;
    private final LinkedHashMap<KillIdentity,Receipt>
        receipts=new LinkedHashMap<>();

    PvmRecordService(World world){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    synchronized Receipt recordIfCurrent(
        WorldPlayer player,
        long expectedGeneration,
        EntityId npcId,
        long deathTick
    ){
        Objects.requireNonNull(
            player,
            "player"
        );
        Objects.requireNonNull(
            npcId,
            "npcId"
        );

        if(deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+deathTick
            );

        KillIdentity identity=
            new KillIdentity(
                npcId,
                deathTick
            );

        Receipt prior=
            receipts.get(
                identity
            );

        if(prior!=null)
            return Receipt.replay(
                npcId,
                deathTick,
                prior.kills
            );

        final Receipt[] result={null};

        try{
            boolean owned=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    player,
                    expectedGeneration,
                    ()->{
                        SortedMap<String,String> current=
                            player.snapshotExtensions()
                                .namespace(
                                    NAMESPACE
                                );

                        Long before=
                            decodeKills(
                                current
                            );

                        if(before==null){
                            result[0]=
                                Receipt.rejected(
                                    "INVALID_STATE",
                                    npcId,
                                    deathTick,
                                    0L
                                );
                            return;
                        }

                        if(before.longValue()==
                                Long.MAX_VALUE){
                            result[0]=
                                Receipt.rejected(
                                    "OVERFLOW_STATE",
                                    npcId,
                                    deathTick,
                                    before.longValue()
                                );
                            return;
                        }

                        long next=
                            before.longValue()+1L;

                        TreeMap<String,String> replacement=
                            new TreeMap<>();
                        replacement.put(
                            "version",
                            VERSION
                        );
                        replacement.put(
                            "authority",
                            AUTHORITY
                        );
                        replacement.put(
                            "kills",
                            Long.toString(next)
                        );

                        player.snapshotExtensions()
                            .replaceNamespace(
                                NAMESPACE,
                                replacement
                            );

                        result[0]=
                            Receipt.applied(
                                npcId,
                                deathTick,
                                next
                            );
                    }
                );

            if(!owned)
                return Receipt.rejected(
                    "STALE_PLAYER_GENERATION",
                    npcId,
                    deathTick,
                    0L
                );
        }catch(Exception failure){
            throw new IllegalStateException(
                "PvM record mutation failed player="+
                player.id()+
                " npc="+npcId,
                failure
            );
        }

        Receipt committed=
            Objects.requireNonNull(
                result[0],
                "PvM record result"
            );

        if(committed.applied)
            receipts.put(
                identity,
                committed
            );

        return committed;
    }

    Snapshot snapshot(
        WorldPlayer player
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        synchronized(player.mutationLock()){
            Long kills=
                decodeKills(
                    player.snapshotExtensions()
                        .namespace(
                            NAMESPACE
                        )
                );

            return new Snapshot(
                kills==null
                    ?0L
                    :kills.longValue()
            );
        }
    }

    synchronized int receiptCount(){
        return receipts.size();
    }

    private static Long decodeKills(
        SortedMap<String,String> values
    ){
        if(values==null||
           values.isEmpty())
            return Long.valueOf(0L);

        if(!VERSION.equals(
                trim(
                    values.get(
                        "version"
                    )
                )
            ))
            return null;

        if(!AUTHORITY.equals(
                trim(
                    values.get(
                        "authority"
                    )
                )
            ))
            return null;

        String raw=
            trim(
                values.get(
                    "kills"
                )
            );

        if(raw==null)
            return null;

        try{
            long parsed=
                Long.parseLong(raw);

            return parsed<0L
                ?null
                :Long.valueOf(parsed);
        }catch(NumberFormatException failure){
            return null;
        }
    }

    private static String trim(
        String value
    ){
        if(value==null)
            return null;

        String clean=value.trim();
        return clean.isEmpty()
            ?null
            :clean;
    }
}

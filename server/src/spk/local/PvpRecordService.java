package spk.local;

import java.util.Locale;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * LocalLab-owned durable PvP record projection.
 *
 * This service consumes already-canonical combat outcome facts. It owns no
 * combat resolution, reward, currency, packet or original SpawnPK policy.
 * State is persisted under PlayerSnapshotExtensionState namespace
 * "pvp-record" so recovered schema keys remain untouched.
 */
final class PvpRecordService
    implements CombatOutcomeObserver {

    static final String SOURCE_AUTHORITY=
        "CUSTOM_LOCALLAB";
    static final String NAMESPACE=
        "pvp-record";
    static final String VERSION=
        "1";

    static final class Record {
        final long kills;
        final long deaths;
        final long currentStreak;
        final long bestStreak;

        Record(
            long kills,
            long deaths,
            long currentStreak,
            long bestStreak
        ){
            this.kills=nonNegative(kills);
            this.deaths=nonNegative(deaths);
            this.currentStreak=
                nonNegative(currentStreak);
            this.bestStreak=
                Math.max(
                    nonNegative(bestStreak),
                    this.currentStreak
                );
        }

        @Override public String toString(){
            return "PvpRecord{kills="+kills+
                ",deaths="+deaths+
                ",currentStreak="+currentStreak+
                ",bestStreak="+bestStreak+"}";
        }
    }

    private final World world;

    PvpRecordService(World world){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
    }

    @Override
    public void onCombatOutcome(
        CombatOutcome outcome
    ){
        CombatOutcome fact=
            Objects.requireNonNull(
                outcome,
                "outcome"
            );

        if(fact.context()!=
                CombatOutcomeContext.PLAYER_PVP)
            return;

        switch(fact.type()){
            case PLAYER_KILL:
                mutate(
                    fact.attacker(),
                    true
                );
                return;
            case PLAYER_DEATH:
                mutate(
                    fact.victim(),
                    false
                );
                return;
            default:
                return;
        }
    }

    Record snapshot(
        WorldPlayer player
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        synchronized(player.mutationLock()){
            return decode(
                player.snapshotExtensions()
                    .namespace(NAMESPACE)
            );
        }
    }

    private void mutate(
        String playerRef,
        boolean kill
    ){
        EntityId id=
            parseEntityId(playerRef);

        if(id==null)
            return;

        WorldPlayer player=
            world.players().byId(id);

        if(player==null)
            return;

        long generation=
            player.generation();

        try{
            world.withOpenPlayerMutationOwnershipIfCurrent(
                player,
                generation,
                ()->{
                    Record before=
                        decode(
                            player.snapshotExtensions()
                                .namespace(
                                    NAMESPACE
                                )
                        );

                    Record after;

                    if(kill){
                        long nextKills=
                            incrementSaturated(
                                before.kills
                            );
                        long nextStreak=
                            incrementSaturated(
                                before.currentStreak
                            );

                        after=
                            new Record(
                                nextKills,
                                before.deaths,
                                nextStreak,
                                Math.max(
                                    before.bestStreak,
                                    nextStreak
                                )
                            );
                    }else{
                        after=
                            new Record(
                                before.kills,
                                incrementSaturated(
                                    before.deaths
                                ),
                                0L,
                                before.bestStreak
                            );
                    }

                    player.snapshotExtensions()
                        .replaceNamespace(
                            NAMESPACE,
                            encode(after)
                        );
                }
            );
        }catch(Exception failure){
            throw new IllegalStateException(
                "PvP record mutation failed player="+
                playerRef+
                " kind="+
                (kill?"KILL":"DEATH"),
                failure
            );
        }
    }

    private static TreeMap<String,String>
        encode(
            Record record
        ){
        TreeMap<String,String> values=
            new TreeMap<>();

        values.put(
            "version",
            VERSION
        );
        values.put(
            "kills",
            Long.toString(
                record.kills
            )
        );
        values.put(
            "deaths",
            Long.toString(
                record.deaths
            )
        );
        values.put(
            "current-streak",
            Long.toString(
                record.currentStreak
            )
        );
        values.put(
            "best-streak",
            Long.toString(
                record.bestStreak
            )
        );

        return values;
    }

    private static Record decode(
        SortedMap<String,String> values
    ){
        if(values==null||
           values.isEmpty())
            return new Record(
                0L,
                0L,
                0L,
                0L
            );

        /*
         * Unknown/future versions fail closed to an empty LocalLab record
         * rather than making the whole account snapshot unloadable.
         */
        String version=
            values.get("version");

        if(version!=null&&
           !VERSION.equals(
                version.trim()
            ))
            return new Record(
                0L,
                0L,
                0L,
                0L
            );

        return new Record(
            parseCounter(
                values.get("kills")
            ),
            parseCounter(
                values.get("deaths")
            ),
            parseCounter(
                values.get(
                    "current-streak"
                )
            ),
            parseCounter(
                values.get(
                    "best-streak"
                )
            )
        );
    }

    private static long parseCounter(
        String value
    ){
        if(value==null)
            return 0L;

        try{
            long parsed=
                Long.parseLong(
                    value.trim()
                );

            return parsed<0L
                ?0L
                :parsed;
        }catch(RuntimeException ignored){
            return 0L;
        }
    }

    private static EntityId parseEntityId(
        String value
    ){
        if(value==null)
            return null;

        String clean=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(clean.isEmpty())
            return null;

        try{
            long id=
                Long.parseUnsignedLong(
                    clean
                );

            if(id<=0L)
                return null;

            return new EntityId(id);
        }catch(RuntimeException ignored){
            return null;
        }
    }

    private static long incrementSaturated(
        long value
    ){
        long normalized=
            nonNegative(value);

        return normalized==
                Long.MAX_VALUE
            ?Long.MAX_VALUE
            :normalized+1L;
    }

    private static long nonNegative(
        long value
    ){
        return Math.max(
            0L,
            value
        );
    }
}

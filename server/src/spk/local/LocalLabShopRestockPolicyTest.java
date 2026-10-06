package spk.local;

import java.io.IOException;
import java.util.*;

public final class LocalLabShopRestockPolicyTest {
    public static void main(String[] args)
        throws Exception{
        boolean zeroWaits=false;
        boolean dueTickRestocks=false;
        boolean cappedAtBaseline=false;
        boolean aboveBaselinePreserved=false;
        boolean worldOwned=false;
        boolean gracefulPersistence=false;

        InMemoryLocalLabShopRepository zeroRepo=
            seeded(0L);
        World zeroWorld=
            World.isolatedForTest(
                60_000L,
                new MemoryPlayers(),
                zeroRepo
            );

        try{
            LocalLabShopRuntime runtime=
                zeroWorld.localLabShops();

            worldOwned=
                runtime==
                    zeroWorld.localLabShops();

            for(int tick=1;
                tick<
                    LocalLabShopRuntime
                        .ROCKTAIL_RESTOCK_INTERVAL_TICKS;
                tick++){
                zeroWorld.observePulse(
                    9_999_999_999L+
                    tick
                );

                require(
                    runtime.rocktailStock()==0L,
                    "zero stock changed before exact restock due tick tick="+
                    tick+
                    " stock="+
                    runtime.rocktailStock()
                );
            }

            zeroWaits=true;

            long due=
                zeroWorld.observePulse(
                    99_999_999_999L
                );

            require(
                due==
                    LocalLabShopRuntime
                        .ROCKTAIL_RESTOCK_INTERVAL_TICKS&&
                runtime.rocktailStock()==1L,
                "exact due World tick did not replenish one Rocktail tick="+
                due+
                " stock="+
                runtime.rocktailStock()
            );

            dueTickRestocks=true;
        }finally{
            zeroWorld.close();
        }

        gracefulPersistence=
            zeroRepo.load().isPresent()&&
            zeroRepo.load().get()
                .rocktailStock==1L;

        require(
            gracefulPersistence,
            "G2.7 graceful persistence did not capture restocked stock"
        );

        InMemoryLocalLabShopRepository capRepo=
            seeded(
                LocalLabShopRuntime
                    .INITIAL_ROCKTAIL_STOCK-
                1L
            );
        World capWorld=
            World.isolatedForTest(
                60_000L,
                new MemoryPlayers(),
                capRepo
            );

        try{
            pulseToDue(capWorld);

            require(
                capWorld.localLabShops()
                    .rocktailStock()==
                    LocalLabShopRuntime
                        .INITIAL_ROCKTAIL_STOCK,
                "restock did not reach explicit LocalLab baseline"
            );

            pulseToDue(capWorld);

            require(
                capWorld.localLabShops()
                    .rocktailStock()==
                    LocalLabShopRuntime
                        .INITIAL_ROCKTAIL_STOCK,
                "restock exceeded explicit LocalLab baseline"
            );

            cappedAtBaseline=true;
        }finally{
            capWorld.close();
        }

        InMemoryLocalLabShopRepository aboveRepo=
            seeded(
                LocalLabShopRuntime
                    .INITIAL_ROCKTAIL_STOCK+
                5L
            );
        World aboveWorld=
            World.isolatedForTest(
                60_000L,
                new MemoryPlayers(),
                aboveRepo
            );

        try{
            pulseToDue(aboveWorld);

            require(
                aboveWorld.localLabShops()
                    .rocktailStock()==
                    LocalLabShopRuntime
                        .INITIAL_ROCKTAIL_STOCK+
                    5L,
                "restock policy reduced player-contributed stock above baseline"
            );

            aboveBaselinePreserved=true;
        }finally{
            aboveWorld.close();
        }

        require(
            worldOwned,
            "restock runtime is not World-owned"
        );

        System.out.println(
            "G2_SHOP_RESTOCK_POLICY_PASS"+
            " worldTickDriven=true"+
            " intervalTicks="+
                LocalLabShopRuntime
                    .ROCKTAIL_RESTOCK_INTERVAL_TICKS+
            " increment="+
                LocalLabShopRuntime
                    .ROCKTAIL_RESTOCK_INCREMENT+
            " baseline="+
                LocalLabShopRuntime
                    .INITIAL_ROCKTAIL_STOCK+
            " zeroWaits="+zeroWaits+
            " dueTickRestocks="+dueTickRestocks+
            " cappedAtBaseline="+cappedAtBaseline+
            " aboveBaselinePreserved="+
                aboveBaselinePreserved+
            " worldOwned="+worldOwned+
            " gracefulPersistence="+
                gracefulPersistence+
            " offlineCatchupClaim=false"+
            " originalSpawnpkEconomyClaim=false"
        );
    }

    private static void pulseToDue(
        World world
    ){
        long interval=
            LocalLabShopRuntime
                .ROCKTAIL_RESTOCK_INTERVAL_TICKS;
        long current=
            world.clock().tick();
        long remainder=
            current%interval;
        long count=
            remainder==0L
                ?interval
                :interval-remainder;

        for(long n=0L;n<count;n++)
            world.observePulse(
                123_456_789L+n
            );
    }

    private static InMemoryLocalLabShopRepository
        seeded(
            long stock
        )throws IOException{
        InMemoryLocalLabShopRepository repository=
            new InMemoryLocalLabShopRepository();

        repository.save(
            LocalLabShopSnapshot
                .ofRocktailStock(stock)
        );

        return repository;
    }

    private static final class MemoryPlayers
        implements PlayerRepository {

        private final HashMap<String,PlayerSnapshot>
            snapshots=
                new HashMap<>();

        @Override public Optional<PlayerSnapshot> load(
            String username
        )throws IOException{
            return Optional.ofNullable(
                snapshots.get(username)
            );
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            snapshots.put(
                snapshot.username(),
                snapshot
            );
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }
}

package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class LocalLabShopWorldPersistenceTest {
    public static void main(String[] args)
        throws Exception{
        boolean committedStock=false;
        boolean gracefulCloseSaved=false;
        boolean restartRestored=false;
        boolean missingStateDefaults=false;
        boolean malformedRejected=false;
        boolean filesystemIsolation=false;

        InMemoryLocalLabShopRepository shared=
            new InMemoryLocalLabShopRepository();

        World first=
            World.isolatedForTest(
                60_000L,
                new MemoryPlayers(),
                shared
            );
        WorldPlayer buyer=
            new WorldPlayer();

        first.registerPlayer(
            buyer,
            "opensrc"
        );

        try{
            installCoins(
                buyer,
                100
            );

            G2ShopPurchaseService.Result bought=
                new G2ShopPurchaseService(
                    first,
                    buyer,
                    first.localLabShops()
                        .shops()
                ).purchase(
                    LocalLabShopRuntime.SUPPLIES,
                    "item:"+
                        LocalLabShopRuntime.ROCKTAIL,
                    2L
                );

            committedStock=
                bought.purchased()&&
                first.localLabShops()
                    .rocktailStock()==98L;

            require(
                committedStock,
                "committed purchase did not mutate shared Shop stock"
            );
        }finally{
            first.close();
        }

        gracefulCloseSaved=
            shared.load().isPresent()&&
            shared.load().get()
                .rocktailStock==98L;

        require(
            gracefulCloseSaved,
            "World close did not save Shop snapshot"
        );

        World restored=
            World.isolatedForTest(
                60_000L,
                new MemoryPlayers(),
                shared
            );

        try{
            restartRestored=
                restored.localLabShops()
                    .rocktailStock()==98L;

            require(
                restartRestored,
                "reconstructed World did not restore exact finite stock"
            );
        }finally{
            restored.close();
        }

        InMemoryLocalLabShopRepository empty=
            new InMemoryLocalLabShopRepository();
        World defaults=
            World.isolatedForTest(
                60_000L,
                new MemoryPlayers(),
                empty
            );

        try{
            missingStateDefaults=
                defaults.localLabShops()
                    .rocktailStock()==
                    LocalLabShopRuntime
                        .INITIAL_ROCKTAIL_STOCK;

            require(
                missingStateDefaults,
                "missing Shop state did not use explicit LocalLab default"
            );
        }finally{
            defaults.close();
        }

        Path root=
            Files.createTempDirectory(
                "locallab-shop-world-"
            );
        Path file=
            root.resolve(
                "shop.properties"
            );

        try{
            FileLocalLabShopRepository fileRepo=
                new FileLocalLabShopRepository(
                    ()->file
                );

            fileRepo.save(
                LocalLabShopSnapshot
                    .ofRocktailStock(77L)
            );

            require(
                fileRepo.load().isPresent()&&
                fileRepo.load().get()
                    .rocktailStock==77L,
                "file repository did not round-trip valid Shop state"
            );

            Files.write(
                file,
                Arrays.asList(
                    "version=1",
                    "supplies.rocktail.stock=-1"
                ),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );

            try{
                fileRepo.load();
            }catch(IOException expected){
                malformedRejected=true;
            }

            require(
                malformedRejected,
                "malformed persisted Shop state was not rejected"
            );
        }finally{
            deleteTree(root);
        }

        World isolatedOne=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer isolatedBuyer=
            new WorldPlayer();

        isolatedOne.registerPlayer(
            isolatedBuyer,
            "isolated"
        );

        try{
            installCoins(
                isolatedBuyer,
                100
            );

            new G2ShopPurchaseService(
                isolatedOne,
                isolatedBuyer,
                isolatedOne.localLabShops()
                    .shops()
            ).purchase(
                LocalLabShopRuntime.SUPPLIES,
                "item:"+
                    LocalLabShopRuntime.ROCKTAIL,
                1L
            );

            require(
                isolatedOne.localLabShops()
                    .rocktailStock()==99L,
                "isolated mutation setup failed"
            );
        }finally{
            isolatedOne.close();
        }

        World isolatedTwo=
            World.isolatedForTest(
                60_000L
            );

        try{
            filesystemIsolation=
                isolatedTwo.localLabShops()
                    .rocktailStock()==
                    LocalLabShopRuntime
                        .INITIAL_ROCKTAIL_STOCK;

            require(
                filesystemIsolation,
                "ordinary isolated World leaked Shop state across instances"
            );
        }finally{
            isolatedTwo.close();
        }

        System.out.println(
            "G2_SHOP_WORLD_RESTART_PERSISTENCE_PASS"+
            " committedStock="+committedStock+
            " gracefulCloseSaved="+gracefulCloseSaved+
            " restartRestored="+restartRestored+
            " missingStateDefaults="+missingStateDefaults+
            " malformedRejected="+malformedRejected+
            " filesystemIsolation="+filesystemIsolation+
            " originalSpawnpkEconomyClaim=false"
        );
    }

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        Arrays.fill(
            items,
            -1
        );

        items[0]=
            LocalLabShopRuntime.COINS;
        quantities[0]=quantity;

        player.bank()
            .replaceInventorySemantic(
                items,
                quantities
            );
    }

    private static void deleteTree(
        Path root
    )throws IOException{
        if(root==null||
           !Files.exists(root))
            return;

        try(java.util.stream.Stream<Path> paths=
                Files.walk(root)){
            Iterator<Path> iterator=
                paths.sorted(
                    Comparator.reverseOrder()
                ).iterator();

            while(iterator.hasNext())
                Files.deleteIfExists(
                    iterator.next()
                );
        }
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

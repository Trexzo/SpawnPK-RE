package spk.local;

import java.util.*;
import java.nio.file.*;
import java.util.stream.Stream;

public final class PlayableShopEconomyReconnectTest {
    private static final AtomicTransactionService.SourceAuthority AUTHORITY=
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;
    private static final int COINS=995;
    private static final int ROCKTAIL=15272;

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected P4 args");
        Path root=Files.createTempDirectory("spk-playable-p4-shop-");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"opensrc");

        AtomicTransactionService transactions=new AtomicTransactionService();
        ShopService shops=new ShopService(transactions);
        ShopService.ShopId shopId=ShopService.ShopId.of("shop:g2-round-trip");

        shops.register(new ShopService.ShopDefinition(
            shopId,
            "G2 Round Trip",
            "shop-owner:g2-round-trip",
            Collections.singletonList(
                ShopService.Offer.finite(
                    "item:"+ROCKTAIL,
                    "item:"+COINS,
                    10L,
                    5L
                )
            ),
            AUTHORITY
        ));

        ShopSellbackService sellbacks=new ShopSellbackService(shops);
        sellbacks.register(new ShopSellbackService.BuybackDefinition(
            shopId,
            "item:"+ROCKTAIL,
            "item:"+COINS,
            4L,
            AUTHORITY
        ));

        G2ShopPurchaseService purchase=
            new G2ShopPurchaseService(world,player,shops);
        G2ShopSellbackInventoryService sellback=
            new G2ShopSellbackInventoryService(world,player,sellbacks);

        try{
            installCoins(player,100);

            G2ShopPurchaseService.Result firstBuy=
                purchase.purchase(shopId,"item:"+ROCKTAIL,2L);

            require(
                firstBuy.purchased() &&
                player.bank().inventoryCount(COINS)==80 &&
                player.bank().inventoryCount(ROCKTAIL)==2 &&
                finiteStock(shops,shopId)==3L,
                "first buy postimage mismatch "+firstBuy
            );

            G2ShopSellbackInventoryService.Result sold=
                sellback.sell(shopId,"item:"+ROCKTAIL,1L);

            require(
                sold.sold() &&
                player.bank().inventoryCount(COINS)==84 &&
                player.bank().inventoryCount(ROCKTAIL)==1 &&
                finiteStock(shops,shopId)==4L,
                "sellback postimage mismatch "+sold
            );

            G2ShopPurchaseService.Result secondBuy=
                purchase.purchase(shopId,"item:"+ROCKTAIL,1L);

            require(
                secondBuy.purchased() &&
                player.bank().inventoryCount(COINS)==74 &&
                player.bank().inventoryCount(ROCKTAIL)==2 &&
                finiteStock(shops,shopId)==3L,
                "second buy postimage mismatch "+secondBuy
            );

            require(
                committed(transactions,firstBuy.transactionId) &&
                committed(transactions,sold.transactionId) &&
                committed(transactions,secondBuy.transactionId) &&
                !firstBuy.transactionId.equals(sold.transactionId) &&
                !firstBuy.transactionId.equals(secondBuy.transactionId) &&
                !sold.transactionId.equals(secondBuy.transactionId),
                "round-trip transactions were not exact independent commits"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture("opensrc",player);
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(snapshot,restored);

            require(
                restored.bank().inventoryCount(COINS)==74 &&
                restored.bank().inventoryCount(ROCKTAIL)==2,
                "round-trip final postimage did not persist"
            );

            // Unlike the G2 in-memory round-trip, write the exact
            // purchased goods to the normal file-backed account before
            // ending this World. This is a healthy checkpoint, NOT
            // an authenticated final-save crash-recovery claim.
            repository.save(snapshot);
            require(world.unregisterPlayer(player,player.generation()),
                "original shop participant did not unregister");
            world.close();
            World reconnected=World.isolatedForTest(60_000L,repository);
            try{
                Optional<PlayerSnapshot> persisted=
                    repository.loadForWorldSession("opensrc");
                require(persisted.isPresent(),
                    "shop account file missing after World close");
                WorldPlayer fresh=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(persisted.get(),fresh);
                long freshGeneration=
                    reconnected.registerPlayer(fresh,"opensrc");
                require(fresh.bank().inventoryCount(COINS)==74&&
                    fresh.bank().inventoryCount(ROCKTAIL)==2,
                    "fresh World changed shop purchase inventory");
                require(fresh.bank().bankAt(0)!=null&&
                    fresh.bank().bankAt(0).itemId==COINS&&
                    fresh.bank().bankAt(0).qty==100_000,
                    "shop purchases improperly consumed initial bank coins");
                require(reconnected.unregisterPlayer(
                    fresh,freshGeneration),
                    "fresh shop player did not unregister");
            }finally{
                if(!reconnected.closed())reconnected.close();
            }
            require(Files.isRegularFile(root.resolve("opensrc.properties")),
                "shop account disappeared after reconnect");
            System.out.println(
                "P4_PLAYABLE_SHOP_ECONOMY_RECONNECT_PASS "+
                "buy=true sellback=true rebuy=true "+
                "transactionsDistinct=true stockInSameWorld=3 "+
                "accountCheckpointFile=true freshWorldInventoryCoins=74 "+
                "freshWorldRocktails=2 noDuplicateItems=true "+
                "serverPolicy=CUSTOM_LOCALLAB "+
                "noRealClientOrCrossWorldStockDurabilityProof=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,player.generation());
            if(!world.closed())world.close();
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }

    private static void installCoins(WorldPlayer player,int quantity){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(items,quantities);
        }
    }

    private static long finiteStock(ShopService shops,ShopService.ShopId shopId){
        ShopService.OfferSnapshot offer=
            shops.getShop(shopId).offer("item:"+ROCKTAIL);

        require(
            offer!=null && offer.availableStock.isPresent(),
            "finite Shop offer missing"
        );
        return offer.availableStock.getAsLong();
    }

    private static boolean committed(
        AtomicTransactionService transactions,
        AtomicTransactionService.TransactionId id
    ){
        return id!=null &&
            transactions.snapshot(id).state==
                AtomicTransactionService.TransactionState.COMMITTED;
    }

    private static void require(boolean condition,String message){
        if(!condition)
            throw new AssertionError(message);
    }

    private G2ShopRoundTripIntegrationTest(){}
}

package spk.local;

import java.util.Arrays;

/** World-owned explicit LocalLab supplies economy. */
final class LocalLabShopRuntime {
    static final String AUTHORITY="CUSTOM_LOCALLAB_G2_LIVE_SHOP_V1";
    static final int COINS=995;
    static final int ROCKTAIL=15272;
    static final int STARTER_WHIP=4151;
    static final long ROCKTAIL_BUY_PRICE=10L;
    static final long STARTER_WHIP_BUY_PRICE=100L;
    static final long ROCKTAIL_SELL_PRICE=4L;
    static final long INITIAL_ROCKTAIL_STOCK=100L;
    static final long ROCKTAIL_RESTOCK_INTERVAL_TICKS=10L;
    static final long ROCKTAIL_RESTOCK_INCREMENT=1L;
    static final ShopService.ShopId SUPPLIES=
        ShopService.ShopId.of("shop:locallab-supplies");

    private final AtomicTransactionService transactions=
        new AtomicTransactionService();
    private final ShopService shops=
        new ShopService(transactions);
    private final ShopSellbackService sellbacks=
        new ShopSellbackService(shops);

    LocalLabShopRuntime(){
        this(null);
    }

    LocalLabShopRuntime(
        LocalLabShopSnapshot restored
    ){
        AtomicTransactionService.SourceAuthority authority=
            AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;

        shops.register(new ShopService.ShopDefinition(
            SUPPLIES,
            "LocalLab Supplies",
            "shop-owner:locallab-supplies",
            Arrays.asList(
                ShopService.Offer.finite(
                    "item:"+ROCKTAIL,
                    "item:"+COINS,
                    ROCKTAIL_BUY_PRICE,
                    restored==null
                        ?INITIAL_ROCKTAIL_STOCK
                        :restored.rocktailStock
                ),
                ShopService.Offer.unlimited(
                    "item:"+STARTER_WHIP,
                    "item:"+COINS,
                    STARTER_WHIP_BUY_PRICE
                )
            ),
            authority
        ));

        sellbacks.register(new ShopSellbackService.BuybackDefinition(
            SUPPLIES,
            "item:"+ROCKTAIL,
            "item:"+COINS,
            ROCKTAIL_SELL_PRICE,
            authority
        ));
    }

    ShopService shops(){return shops;}
    ShopSellbackService sellbacks(){return sellbacks;}
    AtomicTransactionService transactions(){return transactions;}

    long onWorldTick(
        long authoritativeTick
    ){
        if(authoritativeTick<=0L)
            throw new IllegalArgumentException(
                "authoritativeTick="+authoritativeTick
            );

        if(authoritativeTick%
                ROCKTAIL_RESTOCK_INTERVAL_TICKS!=0L)
            return 0L;

        return shops.replenishFiniteStock(
            SUPPLIES,
            "item:"+ROCKTAIL,
            ROCKTAIL_RESTOCK_INCREMENT,
            INITIAL_ROCKTAIL_STOCK
        );
    }

    LocalLabShopSnapshot snapshot(){
        return LocalLabShopSnapshot.ofRocktailStock(
            rocktailStock()
        );
    }

    long rocktailStock(){
        ShopService.OfferSnapshot offer=
            shops.getShop(SUPPLIES).offer("item:"+ROCKTAIL);
        if(offer==null||!offer.availableStock.isPresent())
            throw new IllegalStateException("LocalLab supplies finite stock missing");
        return offer.availableStock.getAsLong();
    }
}

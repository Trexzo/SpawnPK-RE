package spk.local;

import java.util.Locale;
import java.util.Objects;

/**
 * Explicit LocalLab command access to the world-owned G2 Shop economy.
 *
 * Native exact-v308 Shop widget identity remains unproven. This adapter owns
 * only semantic command parsing + canonical Shop mutation. Exact S2C253
 * presentation stays at the command/session boundary so successful state can
 * request persistence before advisory feedback is written.
 */
final class LocalShopCommandHandler {
    static final String AUTHORITY=LocalLabShopRuntime.AUTHORITY;
    static final String SAVE_BUY="G2_LIVE_SHOP_BUY";
    static final String SAVE_SELL="G2_LIVE_SHOP_SELL";

    static final class Result {
        final String logText;
        final String saveReason;
        final String clientMessage;

        Result(
            String logText,
            String saveReason,
            String clientMessage
        ){
            this.logText=Objects.requireNonNull(logText,"logText");
            this.saveReason=saveReason;
            this.clientMessage=
                Objects.requireNonNull(clientMessage,"clientMessage");
        }
    }

    private final LocalLabShopRuntime runtime;
    private final G2ShopPurchaseService purchases;
    private final G2ShopSellbackInventoryService sellbacks;

    LocalShopCommandHandler(
        World world,
        WorldPlayer player,
        LocalLabShopRuntime runtime
    ){
        this.runtime=Objects.requireNonNull(runtime,"runtime");
        this.purchases=new G2ShopPurchaseService(
            Objects.requireNonNull(world,"world"),
            Objects.requireNonNull(player,"player"),
            runtime.shops()
        );
        this.sellbacks=new G2ShopSellbackInventoryService(
            world,
            player,
            runtime.sellbacks()
        );
    }

    Result handle(String[] p){
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("shop"))
            return null;

        if(p.length==1||
           (p.length==2&&p[1].equalsIgnoreCase("stock"))){
            long stock=runtime.rocktailStock();
            return new Result(
                "G2_LIVE_SHOP_COMMAND action=STOCK stock="+stock+
                " clientFeedbackPrepared=true authority="+AUTHORITY,
                null,
                "LocalLab Supplies: Rocktail x"+stock+
                " | buy="+LocalLabShopRuntime.ROCKTAIL_BUY_PRICE+
                " coins | sell="+LocalLabShopRuntime.ROCKTAIL_SELL_PRICE+
                " coins | ::shop buy rocktail <qty> / ::shop sell rocktail <qty>"
            );
        }

        /*
         * Only the exact LocalLab grammar is admitted. In particular, never
         * silently ignore trailing tokens: malformed input must be mutation-free.
         */
        if(p.length<3||p.length>4){
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_SYNTAX stateMutation=false authority="+
                AUTHORITY,
                null,
                "Usage: ::shop buy rocktail <qty> or ::shop sell rocktail <qty>"
            );
        }

        String verb=p[1].toLowerCase(Locale.ROOT);
        if(!"buy".equals(verb)&&!"sell".equals(verb)){
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_VERB verb="+verb+
                " stateMutation=false authority="+AUTHORITY,
                null,
                "Usage: ::shop buy rocktail <qty> or ::shop sell rocktail <qty>"
            );
        }

        if(!isRocktail(p[2])){
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_ITEM item="+
                p[2]+
                " stateMutation=false authority="+AUTHORITY,
                null,
                "LocalLab Supplies currently sells Rocktail only."
            );
        }

        long quantity=parseQuantity(p.length==4?p[3]:"1");
        if(quantity<=0L){
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_QUANTITY stateMutation=false authority="+
                AUTHORITY,
                null,
                "Quantity must be between 1 and 1000000."
            );
        }

        if("buy".equals(verb)){
            G2ShopPurchaseService.Result result=
                purchases.purchase(
                    LocalLabShopRuntime.SUPPLIES,
                    "item:"+LocalLabShopRuntime.ROCKTAIL,
                    quantity
                );

            if(result.purchased()){
                long stock=runtime.rocktailStock();
                return new Result(
                    "G2_LIVE_SHOP_COMMAND action=BUY quantity="+quantity+
                    " result=PURCHASED stock="+stock+
                    " clientFeedbackPrepared=true authority="+AUTHORITY,
                    SAVE_BUY,
                    "Bought "+quantity+" Rocktail for "+
                    result.currencySpent+" coins. Stock="+stock
                );
            }

            return new Result(
                "G2_LIVE_SHOP_COMMAND action=BUY quantity="+quantity+
                " result="+result.status+
                " stateMutation=false authority="+AUTHORITY,
                null,
                "Shop buy rejected: "+result.status
            );
        }

        G2ShopSellbackInventoryService.Result result=
            sellbacks.sell(
                LocalLabShopRuntime.SUPPLIES,
                "item:"+LocalLabShopRuntime.ROCKTAIL,
                quantity
            );

        if(result.sold()){
            long stock=runtime.rocktailStock();
            return new Result(
                "G2_LIVE_SHOP_COMMAND action=SELL quantity="+quantity+
                " result=SOLD stock="+stock+
                " clientFeedbackPrepared=true authority="+AUTHORITY,
                SAVE_SELL,
                "Sold "+quantity+" Rocktail for "+
                result.payout+" coins. Stock="+stock
            );
        }

        return new Result(
            "G2_LIVE_SHOP_COMMAND action=SELL quantity="+quantity+
            " result="+result.status+
            " stateMutation=false authority="+AUTHORITY,
            null,
            "Shop sell rejected: "+result.status
        );
    }

    private static boolean isRocktail(String token){
        return token!=null&&(
            token.equalsIgnoreCase("rocktail")||
            token.equals(Integer.toString(LocalLabShopRuntime.ROCKTAIL))
        );
    }

    private static long parseQuantity(String token){
        try{
            long value=Long.parseLong(token);
            return value>=1L&&value<=1_000_000L?value:-1L;
        }catch(Exception ignored){
            return -1L;
        }
    }
}

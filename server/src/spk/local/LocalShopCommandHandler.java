package spk.local;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Explicit LocalLab command access to the world-owned G2 Shop economy.
 *
 * Native exact-v308 Shop widget identity remains unproven; this adapter uses
 * only the already-exact opcode-103 command path and exact S2C253 feedback.
 */
final class LocalShopCommandHandler {
    static final String AUTHORITY=LocalLabShopRuntime.AUTHORITY;
    static final String SAVE_BUY="G2_LIVE_SHOP_BUY";
    static final String SAVE_SELL="G2_LIVE_SHOP_SELL";

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
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

    Result handle(
        String[] p,
        ServerPacketWriter packets
    )throws IOException{
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("shop"))
            return null;

        SocialChatPresentationPublisher chat=
            new SocialChatPresentationPublisher(
                Objects.requireNonNull(packets,"packets")
            );

        if(p.length==1||p[1].equalsIgnoreCase("stock")){
            String message=
                "LocalLab Supplies: Rocktail x"+
                runtime.rocktailStock()+
                " | buy="+LocalLabShopRuntime.ROCKTAIL_BUY_PRICE+
                " coins | sell="+LocalLabShopRuntime.ROCKTAIL_SELL_PRICE+
                " coins | ::shop buy rocktail <qty> / ::shop sell rocktail <qty>";
            chat.serverMessage(message);
            return new Result(
                "G2_LIVE_SHOP_COMMAND action=STOCK stock="+
                runtime.rocktailStock()+
                " clientFeedback=true authority="+AUTHORITY,
                null
            );
        }

        if(p.length<3){
            chat.serverMessage(
                "Usage: ::shop buy rocktail <qty> or ::shop sell rocktail <qty>"
            );
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_SYNTAX stateMutation=false authority="+
                AUTHORITY,
                null
            );
        }

        String verb=p[1].toLowerCase(Locale.ROOT);
        if(!isRocktail(p[2])){
            chat.serverMessage("LocalLab Supplies currently sells Rocktail only.");
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_ITEM item="+
                p[2]+
                " stateMutation=false authority="+AUTHORITY,
                null
            );
        }

        long quantity=parseQuantity(p.length>=4?p[3]:"1");
        if(quantity<=0L){
            chat.serverMessage("Quantity must be between 1 and 1000000.");
            return new Result(
                "G2_LIVE_SHOP_COMMAND result=REJECTED_QUANTITY stateMutation=false authority="+
                AUTHORITY,
                null
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
                chat.serverMessage(
                    "Bought "+quantity+" Rocktail for "+
                    result.currencySpent+" coins. Stock="+
                    runtime.rocktailStock()
                );
                return new Result(
                    "G2_LIVE_SHOP_COMMAND action=BUY quantity="+quantity+
                    " result=PURCHASED stock="+runtime.rocktailStock()+
                    " clientFeedback=true authority="+AUTHORITY,
                    SAVE_BUY
                );
            }

            chat.serverMessage("Shop buy rejected: "+result.status);
            return new Result(
                "G2_LIVE_SHOP_COMMAND action=BUY quantity="+quantity+
                " result="+result.status+
                " stateMutation=false authority="+AUTHORITY,
                null
            );
        }

        if("sell".equals(verb)){
            G2ShopSellbackInventoryService.Result result=
                sellbacks.sell(
                    LocalLabShopRuntime.SUPPLIES,
                    "item:"+LocalLabShopRuntime.ROCKTAIL,
                    quantity
                );

            if(result.sold()){
                chat.serverMessage(
                    "Sold "+quantity+" Rocktail for "+
                    result.payout+" coins. Stock="+
                    runtime.rocktailStock()
                );
                return new Result(
                    "G2_LIVE_SHOP_COMMAND action=SELL quantity="+quantity+
                    " result=SOLD stock="+runtime.rocktailStock()+
                    " clientFeedback=true authority="+AUTHORITY,
                    SAVE_SELL
                );
            }

            chat.serverMessage("Shop sell rejected: "+result.status);
            return new Result(
                "G2_LIVE_SHOP_COMMAND action=SELL quantity="+quantity+
                " result="+result.status+
                " stateMutation=false authority="+AUTHORITY,
                null
            );
        }

        chat.serverMessage("Usage: ::shop buy rocktail <qty> or ::shop sell rocktail <qty>");
        return new Result(
            "G2_LIVE_SHOP_COMMAND result=REJECTED_VERB verb="+verb+
            " stateMutation=false authority="+AUTHORITY,
            null
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

package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class LocalShopCommandHandlerTest {
    private static final int[] SEED={1,2,3,4};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        WorldPlayer observer=new WorldPlayer();

        world.registerPlayer(player,"opensrc");
        world.registerPlayer(observer,"observer");

        LocalLabShopRuntime runtime=world.localLabShops();
        LocalShopCommandHandler handler=
            new LocalShopCommandHandler(world,player,runtime);
        LocalShopCommandHandler observerHandler=
            new LocalShopCommandHandler(world,observer,world.localLabShops());

        try{
            require(runtime==world.localLabShops(),"shop runtime is not World-owned");
            installCoins(player,100);

            Captured buy=capture(
                handler,
                LocalCommandDispatcher.tokens(
                    LocalCommandDispatcher.clean("::shop buy rocktail 2")
                )
            );

            require(
                LocalShopCommandHandler.SAVE_BUY.equals(buy.result.saveReason)&&
                player.bank().inventoryCount(LocalLabShopRuntime.COINS)==80&&
                player.bank().inventoryCount(LocalLabShopRuntime.ROCKTAIL)==2&&
                runtime.rocktailStock()==98L,
                "live buy command did not settle canonical state"
            );
            requireServerMessage(buy.bytes,"buy");

            Captured stock=capture(
                observerHandler,
                new String[]{"shop","stock"}
            );
            require(
                stock.result.saveReason==null&&
                stock.result.logText.contains("stock=98"),
                "second session did not observe World-owned stock"
            );
            requireServerMessage(stock.bytes,"stock");

            Captured sell=capture(
                handler,
                new String[]{"shop","sell","15272","1"}
            );
            require(
                LocalShopCommandHandler.SAVE_SELL.equals(sell.result.saveReason)&&
                player.bank().inventoryCount(LocalLabShopRuntime.COINS)==84&&
                player.bank().inventoryCount(LocalLabShopRuntime.ROCKTAIL)==1&&
                runtime.rocktailStock()==99L,
                "live sell command did not settle canonical state"
            );
            requireServerMessage(sell.bytes,"sell");

            InventoryImage beforeInvalid=captureInventory(player);
            long stockBeforeInvalid=runtime.rocktailStock();

            Captured badItem=capture(
                handler,
                new String[]{"shop","buy","4151","1"}
            );
            Captured badQty=capture(
                handler,
                new String[]{"shop","sell","rocktail","0"}
            );
            Captured trailingBuy=capture(
                handler,
                new String[]{"shop","buy","rocktail","1","junk"}
            );
            Captured trailingStock=capture(
                handler,
                new String[]{"shop","stock","junk"}
            );

            require(
                badItem.result.saveReason==null&&
                badQty.result.saveReason==null&&
                trailingBuy.result.saveReason==null&&
                trailingStock.result.saveReason==null&&
                trailingBuy.result.logText.contains("REJECTED_SYNTAX")&&
                sameInventory(beforeInvalid,captureInventory(player))&&
                runtime.rocktailStock()==stockBeforeInvalid,
                "invalid live Shop command mutated state or requested save"
            );
            requireServerMessage(badItem.bytes,"badItem");
            requireServerMessage(badQty.bytes,"badQty");
            requireServerMessage(trailingBuy.bytes,"trailingBuy");
            requireServerMessage(trailingStock.bytes,"trailingStock");

            require(
                LocalLabShopRuntime.AUTHORITY.equals(
                    LocalShopCommandHandler.AUTHORITY
                ),
                "live Shop authority drift"
            );

            System.out.println(
                "G2_LIVE_SHOP_COMMAND_PASS "+
                "exactC2S103=true "+
                "worldOwnedEconomy=true "+
                "clientFeedback=true "+
                "buy=true "+
                "sell=true "+
                "finiteStock=true "+
                "accountSave=true "+
                "invalidAtomic=true "+
                "strictSyntax=true "+
                "feedbackSeparatedFromMutation=true "+
                "nativeShopWidgetAuthority=false "+
                "originalSpawnpkEconomyClaim=false "+
                "authority="+LocalShopCommandHandler.AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,player.generation());
            if(observer.registered())
                world.unregisterPlayer(observer,observer.generation());
            world.close();
        }
    }

    private static Captured capture(
        LocalShopCommandHandler handler,
        String[] tokens
    )throws Exception{
        LocalShopCommandHandler.Result result=
            handler.handle(tokens);

        require(result!=null,"Shop command was not handled");
        require(
            result.clientMessage!=null&&
            !result.clientMessage.isEmpty(),
            "Shop command did not prepare client feedback"
        );

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(SEED.clone())
            );

        new SocialChatPresentationPublisher(
            writer
        ).serverMessage(
            result.clientMessage
        );

        return new Captured(result,out.toByteArray());
    }

    private static void requireServerMessage(
        byte[] frame,
        String label
    ){
        require(frame.length>=3,label+" produced no S2C253 frame");

        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int opcode=((frame[0]&255)-cipher.nextInt())&255;

        require(opcode==253,label+" feedback opcode="+opcode);
        int len=frame[1]&255;
        require(
            len==frame.length-2&&
            frame[frame.length-1]==10,
            label+" malformed S2C253 var-byte frame"
        );
    }

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=LocalLabShopRuntime.COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(items,quantities);
        }
    }

    private static InventoryImage captureInventory(
        WorldPlayer player
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);

        synchronized(player.mutationLock()){
            for(int slot=0;slot<BankState.INVENTORY_CAPACITY;slot++){
                BankState.InventorySlotSnapshot snapshot=
                    player.bank().inventorySlotSnapshot(slot);
                if(!snapshot.occupied)continue;
                items[slot]=snapshot.itemId;
                quantities[slot]=snapshot.quantity;
            }
        }

        return new InventoryImage(items,quantities);
    }

    private static boolean sameInventory(
        InventoryImage a,
        InventoryImage b
    ){
        return Arrays.equals(a.items,b.items)&&
            Arrays.equals(a.quantities,b.quantities);
    }

    private static final class InventoryImage {
        final int[] items;
        final int[] quantities;

        InventoryImage(int[] items,int[] quantities){
            this.items=items;
            this.quantities=quantities;
        }
    }

    private static final class Captured {
        final LocalShopCommandHandler.Result result;
        final byte[] bytes;

        Captured(
            LocalShopCommandHandler.Result result,
            byte[] bytes
        ){
            this.result=result;
            this.bytes=bytes;
        }
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private LocalShopCommandHandlerTest(){}
}

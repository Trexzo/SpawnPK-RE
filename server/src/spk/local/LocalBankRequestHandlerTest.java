package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalBankRequestHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        LocalBankRequestHandler h=new LocalBankRequestHandler(player,bank);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalBankRequestHandler.Result amount=h.handleAmount(7,w);
        if(amount==null||!amount.logText.contains("IGNORED_BANK_CLOSED"))
            throw new AssertionError("closed bank amount route");
        if(!"BANK_AMOUNT".equals(amount.saveReason))
            throw new AssertionError("bank amount save contract");

        LocalBankRequestHandler.Result bankCommand=h.handleCommand("::banksearch whip",w);
        if(bankCommand==null||!bankCommand.logText.contains("IGNORED_BANK_CLOSED"))
            throw new AssertionError("bank command route");
        if(!"BANK_COMMAND".equals(bankCommand.saveReason))
            throw new AssertionError("bank command save contract");

        if(h.handleCommand("::nurse",w)!=null)
            throw new AssertionError("non-bank command must fall through");

        bank.spawnItem(4151,1,w);
        bank.spawnItem(995,100,w);
        if(bank.inventoryAt(0)==null||bank.inventoryAt(1)==null)
            throw new AssertionError("drag precondition inventory");

        int first=bank.inventoryAt(0).itemId;
        int second=bank.inventoryAt(1).itemId;
        ContainerDrag drag=new ContainerDrag(
            BankState.NORMAL_INVENTORY_CONTAINER,0,0,1);

        LocalBankRequestHandler.Result dragResult=h.handleDrag(drag,w);
        if(dragResult==null||!dragResult.logText.contains("INVENTORY_DRAG_OK"))
            throw new AssertionError("inventory drag route="+
                (dragResult==null?"null":dragResult.logText));
        if(!"INVENTORY_DRAG".equals(dragResult.saveReason))
            throw new AssertionError("inventory drag save reason");
        if(bank.inventoryAt(0)==null||bank.inventoryAt(1)==null||
           bank.inventoryAt(0).itemId!=second||bank.inventoryAt(1).itemId!=first)
            throw new AssertionError("authoritative inventory drag not applied");

        System.out.println("LOCAL_BANK_REQUEST_HANDLER_PASS amountRouting=true bankCommandFallthrough=true inventoryDrag=true persistenceSignals=true");
    }
}

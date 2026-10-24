package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalItemOnItemHandlerTest {
    public static void main(String[] args)throws Exception{
        BankState bank=new BankState();
        LocalItemOnItemHandler h=new LocalItemOnItemHandler(bank);

        // Item grants now require a recoverable queued publication; an
        // ordinary direct socket writer may emit partial bytes and is denied.
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter w=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1,2,3,4}));

        bank.spawnItem(28824,1,w);
        bank.spawnItem(3241,1,w);
        queue.drainTo(new ByteArrayOutputStream(),8192);

        if(bank.inventoryAt(0)==null||bank.inventoryAt(1)==null)
            throw new AssertionError("combine precondition inventory");

        ItemOnItemAction dye=new ItemOnItemAction(
            1,0,3241,
            BankState.NORMAL_INVENTORY_CONTAINER,
            28824,
            BankState.NORMAL_INVENTORY_CONTAINER
        );

        LocalItemOnItemHandler.Result combined=h.handle(dye,w);
        if(combined==null||!combined.logText.contains("INVENTORY_COMBINE_OK"))
            throw new AssertionError("combine route="+
                (combined==null?"null":combined.logText));
        if(!"DOPPELGANGER_APPLY_DYE".equals(combined.saveReason))
            throw new AssertionError("combine save reason="+combined.saveReason);
        if(bank.inventoryCount(28807)!=1)
            throw new AssertionError("result item 28807 missing");
        if(bank.inventoryCount(28824)!=0||bank.inventoryCount(3241)!=0)
            throw new AssertionError("source items not consumed");
        if(queue.queuedPackets()!=1||queue.queuedBytes()==0)
            throw new AssertionError("dye was not admitted as one batch");
        ByteArrayOutputStream committedWire=new ByteArrayOutputStream();
        if(queue.drainTo(committedWire,8192)<=0||
           committedWire.size()==0||queue.queuedPackets()!=0)
            throw new AssertionError("dye batch not drainable");

        ItemOnItemAction unknown=new ItemOnItemAction(
            0,0,4151,
            BankState.NORMAL_INVENTORY_CONTAINER,
            995,
            BankState.NORMAL_INVENTORY_CONTAINER
        );
        LocalItemOnItemHandler.Result failClosed=h.handle(unknown,w);
        if(failClosed==null||!failClosed.logText.contains("DECODED_NO_SEMANTIC_HANDLER"))
            throw new AssertionError("unknown pair did not fail closed");
        if(failClosed.saveReason!=null)
            throw new AssertionError("unknown pair must not save");

        ItemOnItemAction wrongWidget=new ItemOnItemAction(
            0,0,4151,12345,995,BankState.NORMAL_INVENTORY_CONTAINER
        );
        LocalItemOnItemHandler.Result unsupported=h.handle(wrongWidget,w);
        if(unsupported==null||!unsupported.logText.contains("DECODED_UNSUPPORTED_WIDGET"))
            throw new AssertionError("unsupported widget route");
        if(unsupported.saveReason!=null)
            throw new AssertionError("unsupported widget must not save");

        System.out.println("LOCAL_ITEM_ON_ITEM_HANDLER_PASS doppelCombine=true failClosedUnknown=true unsupportedWidget=true persistenceSignal=true queueOnlyGrant=true");
    }
}

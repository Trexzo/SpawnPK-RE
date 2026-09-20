package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalItemSpawnCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        BankState bank=new BankState();
        LocalItemSpawnCommandHandler h=new LocalItemSpawnCommandHandler(bank);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalItemSpawnCommandHandler.Result one=
            h.handle(new String[]{"item","4151"},"::item 4151",w);
        if(one==null||!one.logText.contains("source=item command=::item 4151"))
            throw new AssertionError("item route");
        if(!"ITEM_SPAWN".equals(one.saveReason))
            throw new AssertionError("item save reason="+one.saveReason);
        if(bank.inventoryCount(4151)!=1)
            throw new AssertionError("item 4151 not spawned");

        LocalItemSpawnCommandHandler.Result scaled=
            h.handle(new String[]{"tabitem","995","2k"},"::tabitem 995 2k",w);
        if(scaled==null||!scaled.logText.contains("source=tabitem"))
            throw new AssertionError("tabitem route");
        if(bank.inventoryCount(995)!=2000)
            throw new AssertionError("2k amount parse failed actual="+bank.inventoryCount(995));

        LocalItemSpawnCommandHandler.Result invalid=
            h.handle(new String[]{"item","999999"},"::item 999999",w);
        if(invalid==null||!invalid.logText.contains("REJECTED"))
            throw new AssertionError("invalid item route="+(invalid==null?"null":invalid.logText));
        // Current LocalSession behavior saves after every handled ::item/::tabitem
        // command even when BankState rejects the spawn. Preserve that contract.
        if(!"ITEM_SPAWN".equals(invalid.saveReason))
            throw new AssertionError("rejected item must preserve save call contract");

        if(h.handle(new String[]{"minipet","status"},"::minipet status",w)!=null)
            throw new AssertionError("unrelated command must remain outside item spawn handler");

        System.out.println("LOCAL_ITEM_SPAWN_COMMAND_HANDLER_PASS item=true tabitem=true scaledAmount=true rejectedSaveContract=true unrelatedRejected=true");
    }
}

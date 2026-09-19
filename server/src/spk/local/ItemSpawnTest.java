package spk.local;

import java.io.*;

public final class ItemSpawnTest {
    public static void main(String[] args) throws Exception {
        BankState bank=new BankState();
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        String a=bank.spawnItem(28526,1,w);
        if(!a.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(a);
        BankState.Stack s=bank.inventoryAt(0);
        if(s==null||s.itemId!=28526||s.qty!=1)throw new AssertionError("Bloodrend spawn="+s);

        String b=bank.spawnItem(28512,2,w); // current catalog item; unknown stackability defaults non-stack
        if(!b.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(b);
        if(bank.inventoryAt(1)==null||bank.inventoryAt(2)==null)throw new AssertionError("generic nonstack slots");

        String c=bank.spawnItem(999999,1,w);
        if(!c.startsWith("REJECTED_UNKNOWN_ITEM"))throw new AssertionError(c);

        System.out.println("V41_GENERIC_ITEM_SPAWN_PASS catalog="+ItemCatalog.count()
                         +" anyKnownId=true bloodrend28526=true unknownRejected=true");
    }
}

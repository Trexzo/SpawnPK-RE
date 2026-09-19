package spk.local;

import java.io.ByteArrayOutputStream;

/** Runtime-state regression for exact-current R18 bank action corrections. */
public final class BankAuthorityR18BehaviorTest {
    public static void main(String[] args) throws Exception {
        ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{0,0,0,0}));

        BankState dynamic=new BankState();
        dynamic.open(w);
        int before560=dynamic.bankAt(1).qty;
        ItemContainerAction amount37=new ItemContainerAction(141,BankState.BANK_CONTAINER,1,560,37,"WITHDRAW_CONFIGURED_AMOUNT");
        String r=dynamic.apply(amount37,w);
        if(!r.startsWith("WITHDRAW_CONFIGURED_OK"))throw new AssertionError(r);
        if(dynamic.bankAt(1).qty!=before560-37)throw new AssertionError("dynamic bank qty="+dynamic.bankAt(1).qty);
        if(dynamic.inventoryCount(560)!=37)throw new AssertionError("dynamic inventory="+dynamic.inventoryCount(560));

        BankState coin=new BankState();
        coin.open(w);
        int before995=coin.bankAt(0).qty;
        ItemContainerAction bag=new ItemContainerAction(140,BankState.BANK_CONTAINER,0,995,0,"BAG_EXCHANGE_REQUEST");
        String br=coin.apply(bag,w);
        if(!br.startsWith("BAG_EXCHANGE_SERVER_AUTHORITY_DEFERRED"))throw new AssertionError(br);
        if(coin.bankAt(0).qty!=before995 || coin.inventoryCount(995)!=0)throw new AssertionError("coin mutation occurred");

        BankState allButOne=new BankState();
        allButOne.open(w);
        int before565=allButOne.bankAt(2).qty;
        ItemContainerAction abo=new ItemContainerAction(140,BankState.BANK_CONTAINER,2,565,0,"WITHDRAW_ALL_BUT_ONE");
        String ar=allButOne.apply(abo,w);
        if(!ar.startsWith("WITHDRAW_ALL_BUT_ONE_OK"))throw new AssertionError(ar);
        if(allButOne.bankAt(2).qty!=1 || allButOne.inventoryCount(565)!=before565-1)throw new AssertionError("all-but-one mismatch");

        System.out.println("V5124_BANK_AUTHORITY_R18_PASS dynamic141=37 coin140=FAIL_CLOSED noncoin140=ALL_BUT_ONE");
    }
}

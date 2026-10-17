package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalCompCapeCustomizeHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        String spawned=bank.spawnItem(23063,1,writer);
        int slot=findSlot(bank,23063);
        if(slot<0)
            throw new AssertionError(
                "special comp cape spawn failed: "+spawned);

        LocalCompCapeCustomizeHandler handler=
            new LocalCompCapeCustomizeHandler(
                bank,player.playerState());

        ItemContainerAction openAction=
            new ItemContainerAction(
                75,
                BankState.NORMAL_INVENTORY_CONTAINER,
                slot,
                23063,
                0,
                "INVENTORY_OPTION_3"
            );

        if(!handler.willOpenRoot(
                openAction
            ))
            throw new AssertionError(
                "valid comp cape customize was not classified as root-publishing"
            );

        ItemContainerAction mismatchedOpen=
            new ItemContainerAction(
                75,
                BankState.NORMAL_INVENTORY_CONTAINER,
                slot,
                21963,
                0,
                "INVENTORY_OPTION_3"
            );

        if(handler.willOpenRoot(
                mismatchedOpen
            ))
            throw new AssertionError(
                "inventory-mismatched comp cape action classified as root-publishing"
            );

        int before=wire.size();
        String opened=
            handler.handleItemAction(openAction,writer);

        if(opened==null||
           !opened.contains("V55_COMP_CAPE_CUSTOMIZE_OPEN")||
           !opened.contains("OPENED_NATIVE_ROOT_63036")||
           !handler.isOpen())
            throw new AssertionError("open="+opened);

        if(wire.size()<=before)
            throw new AssertionError(
                "native customize open emitted no packet");

        String ignored=
            new LocalCompCapeCustomizeHandler(
                bank,player.playerState())
                .handleWidget(63027,writer);

        if(ignored==null||
           !ignored.contains("IGNORED_NOT_OPEN"))
            throw new AssertionError(
                "closed handler widget route="+ignored);

        String closed=handler.handleWidget(63031,writer);
        if(closed==null||
           !closed.contains("action=CANCEL")||
           !closed.contains("CLOSED_NATIVE_ROOT_63036")||
           handler.isOpen())
            throw new AssertionError("close="+closed);

        String unrelated=handler.handleItemAction(
            new ItemContainerAction(
                122,
                BankState.NORMAL_INVENTORY_CONTAINER,
                slot,
                23063,
                0,
                "OPTION_1"),
            writer);

        if(unrelated!=null)
            throw new AssertionError(
                "unrelated action should remain outside handler");

        System.out.println(
            "LOCAL_COMP_CAPE_CUSTOMIZE_HANDLER_PASS open=true nativeRoot63036=true close=true ignoredWhenClosed=true rootPreflight=true invalidPreflightPreserved=true");
    }

    private static int findSlot(BankState bank,int itemId){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId&&stack.qty>0)
                return i;
        }
        return -1;
    }
}

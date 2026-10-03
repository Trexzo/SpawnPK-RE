package spk.local;

import java.io.IOException;

/**
 * Native Grand completionist cape Customize UI state.
 *
 * The exact client proves the item action and root/widget presentation. Selector
 * application remains outside this handler because original-server semantics are
 * not recovered here.
 */
final class LocalCompCapeCustomizeHandler {
    private final BankState bank;
    private final PlayerState playerState;
    private boolean open;

    LocalCompCapeCustomizeHandler(
        BankState bank,
        PlayerState playerState
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.playerState=java.util.Objects.requireNonNull(
            playerState,"playerState");
    }

    boolean willOpenRoot(
        ItemContainerAction action
    ){
        if(action==null||
           action.opcode!=75||
           action.widgetId!=BankState.NORMAL_INVENTORY_CONTAINER||
           !isSpecialCompCape(action.itemId))
            return false;

        BankState.Stack stack=
            bank.inventoryAt(
                action.slot
            );

        return stack!=null&&
            stack.itemId==action.itemId&&
            stack.qty>0;
    }

    String handleItemAction(
        ItemContainerAction action,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(action==null||
           action.opcode!=75||
           action.widgetId!=BankState.NORMAL_INVENTORY_CONTAINER||
           !isSpecialCompCape(action.itemId)){
            return null;
        }

        BankState.Stack stack=bank.inventoryAt(action.slot);
        if(stack==null||
           stack.itemId!=action.itemId||
           stack.qty<=0){
            return "V54_COMP_CAPE_CUSTOMIZE_OPEN "+action+
                " result=REJECTED_INVENTORY_MISMATCH";
        }

        serverPackets.fixed(
            97,
            BootstrapPackets.interface97(63036)
        );
        open=true;

        return "V55_COMP_CAPE_CUSTOMIZE_OPEN "+action+
            " result=OPENED_NATIVE_ROOT_63036 selectors="+
            playerState.compSelectorSummary();
    }

    String handleWidget(
        int widget,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(widget!=63027&&widget!=63031)return null;

        if(!open){
            return "V55_COMP_CAPE_WIDGET widget="+widget+
                " result=IGNORED_NOT_OPEN";
        }

        boolean confirm=widget==63027;
        serverPackets.fixed(219,new byte[0]);
        open=false;

        return "V55_COMP_CAPE_WIDGET widget="+widget+
            " action="+(confirm?"CONFIRM":"CANCEL")+
            " result=CLOSED_NATIVE_ROOT_63036 selectors="+
            playerState.compSelectorSummary();
    }

    boolean close(){
        boolean wasOpen=open;
        open=false;
        return wasOpen;
    }

    boolean isOpen(){
        return open;
    }

    private static boolean isSpecialCompCape(int itemId){
        return itemId==23063||itemId==21963||itemId==21964;
    }
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Verify a bank command or UI request without an observable state change
 * does not emit a container packet, advance a mutation generation, or earn
 * the positive save-intent label consumed by the existing bank routing.
 */
public final class BankNoopSaveAndLineageRegression {
    private static final int BANK=BankState.BANK_CONTAINER;
    private static final int NORMAL=BankState.NORMAL_INVENTORY_CONTAINER;
    private static final int BANK_INV=BankState.BANK_INVENTORY_CONTAINER;

    private BankNoopSaveAndLineageRegression(){}

    private static void check(boolean value,String label){
        if(!value)throw new AssertionError("BANK_NOOP_LINEAGE "+label);
    }

    private static void noop(
        BankState bank,ByteArrayOutputStream wire,
        String result,String expected,String label,
        long inventory,long bankRevision,int bytes
    ){
        check(result.startsWith(expected),
            label+" unexpectedly accepted or changed: "+result);
        check(bank.inventoryMutationRevision()==inventory&&
            bank.bankMutationRevision()==bankRevision,
            label+" changed semantic lineage");
        check(wire.size()==bytes,label+" emitted packet bytes");
        check(!BankMutationSaveClassifier.drag(result)&&
            !BankMutationSaveClassifier.command(result),
            label+" granted save-intent label");
    }

    private static void emptyInventoryDrags()throws Exception{
        BankState bank=new BankState();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            bytes,new IsaacCipher(new int[]{1,2,3,4}));
        long inv=bank.inventoryMutationRevision();
        long rev=bank.bankMutationRevision();
        noop(bank,bytes,bank.applyDrag(
            new ContainerDrag(NORMAL,0,0,1),writer),
            "REJECTED_DRAG_SOURCE_EMPTY","closed bank normal empty",
            inv,rev,0);
        bank.open(writer);
        inv=bank.inventoryMutationRevision();
        rev=bank.bankMutationRevision();
        int size=bytes.size();
        noop(bank,bytes,bank.applyDrag(
            new ContainerDrag(BANK,0,9,10),writer),
            "REJECTED_DRAG_SOURCE_EMPTY","bank empty source",
            inv,rev,size);
        noop(bank,bytes,bank.applyDrag(
            new ContainerDrag(BANK_INV,0,9,10),writer),
            "REJECTED_DRAG_SOURCE_EMPTY","bank inventory empty",
            inv,rev,size);
        // An empty drag-source may not steal the destination's coins.
        noop(bank,bytes,bank.applyDrag(
            new ContainerDrag(BANK,0,9,0),writer),
            "REJECTED_DRAG_SOURCE_EMPTY","bank empty source to occupied",
            inv,rev,size);
        check(bank.bankAt(0).itemId==995&&bank.bankAt(9)==null,
            "empty source drag stole bank coins");

        int[] ids=new int[bank.inventoryCapacity()];
        int[] amount=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=995;amount[0]=5;
        ids[1]=995;amount[1]=5;
        bank.replaceInventorySemantic(ids,amount);
        inv=bank.inventoryMutationRevision();rev=bank.bankMutationRevision();
        size=bytes.size();
        noop(bank,bytes,bank.applyDrag(
            new ContainerDrag(NORMAL,0,0,1),writer),
            "INVENTORY_DRAG_NOOP","equal stacks swap",
            inv,rev,size);
        check(bank.inventoryAt(0).qty==5&&bank.inventoryAt(1).qty==5,
            "equal-value inventory no-op changed state");

        String moved=bank.applyDrag(
            new ContainerDrag(NORMAL,0,0,2),writer);
        check(moved.startsWith("INVENTORY_DRAG_OK")&&
            BankMutationSaveClassifier.drag(moved)&&
            bank.inventoryAt(0)==null&&
            bank.inventoryAt(2).qty==5&&
            bank.inventoryMutationRevision()==inv+1&&
            bytes.size()>size,
            "genuine normal inventory drag did not publish and commit");

        long beforeBank=bank.bankMutationRevision();
        size=bytes.size();
        moved=bank.applyDrag(
            new ContainerDrag(BANK,0,0,9),writer);
        check(moved.startsWith("DRAG_OK")&&
            bank.bankAt(0)==null&&bank.bankAt(9).itemId==995&&
            bank.bankMutationRevision()==beforeBank+1&&
            bytes.size()>size,
            "genuine bank drag lost its move");
    }

    private static void bankTabNoops()throws Exception{
        BankState bank=new BankState();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            bytes,new IsaacCipher(new int[]{1,2,3,4}));
        bank.open(writer);
        long inv=bank.inventoryMutationRevision();
        long rev=bank.bankMutationRevision();
        int wire=bytes.size();
        noop(bank,bytes,bank.applyCommand(
            "::setbanktab 0 0",writer),
            "SETBANKTAB_NOOP","already assigned tab",
            inv,rev,wire);
        noop(bank,bytes,bank.applyCommand(
            "::swapbanktab 0 2 2",writer),
            "SWAPBANKTAB_NOOP","same source-target tab",
            inv,rev,wire);
        noop(bank,bytes,bank.applyCommand(
            "::swapbanktab 0 8 7",writer),
            "SWAPBANKTAB_NOOP","empty source/target tab",
            inv,rev,wire);
        String result=bank.applyCommand(
            "::setbanktab 0 2",writer);
        check(result.startsWith("SETBANKTAB_OK")&&
            BankMutationSaveClassifier.command(result)&&
            bank.bankAt(0).tab==2&&
            bank.bankMutationRevision()==rev+1&&
            bytes.size()>wire,
            "genuine bank-tab change was lost");
        rev=bank.bankMutationRevision();wire=bytes.size();
        noop(bank,bytes,bank.applyCommand(
            "::setbanktab 0 2",writer),
            "SETBANKTAB_NOOP","repeat same tab",
            inv,rev,wire);
    }

    private static void depositAllNoops()throws Exception{
        BankState bank=new BankState();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            bytes,new IsaacCipher(new int[]{2,3,5,7}));
        long rev=bank.bankMutationRevision();
        long inv=bank.inventoryMutationRevision();
        String closed=bank.depositInventory(writer);
        noop(bank,bytes,closed,"IGNORED_BANK_CLOSED",
            "deposit closed bank",inv,rev,0);

        bank.open(writer);
        rev=bank.bankMutationRevision();
        inv=bank.inventoryMutationRevision();
        int size=bytes.size();
        noop(bank,bytes,bank.depositInventory(writer),
            "DEPOSITED_ALL_NOOP movedQty=0",
            "empty deposit all",inv,rev,size);

        int[] ids=new int[bank.inventoryCapacity()];
        int[] amounts=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=995;amounts[0]=5;
        bank.replaceInventorySemantic(ids,amounts);
        inv=bank.inventoryMutationRevision();
        rev=bank.bankMutationRevision();
        size=bytes.size();
        String result=bank.depositInventory(writer);
        check(result.startsWith("DEPOSITED_ALL movedQty=5")&&
            bank.inventoryCount(995)==0&&
            bank.bankAt(0).qty==100005&&
            bank.inventoryMutationRevision()==inv+1&&
            bank.bankMutationRevision()==rev+1&&
            bytes.size()>size,
            "positive deposit-all failed to move five coins");
        inv=bank.inventoryMutationRevision();rev=bank.bankMutationRevision();
        size=bytes.size();
        noop(bank,bytes,bank.depositInventory(writer),
            "DEPOSITED_ALL_NOOP movedQty=0",
            "repeat already-empty deposit",inv,rev,size);

        // Placeholder toggles are genuine persistent metadata even when
        // there are no zero-quantity placeholders to remove.
        rev=bank.bankMutationRevision();
        result=bank.togglePlaceholders(writer);
        check(result.startsWith("PLACEHOLDERS_ENABLED")&&
            bank.bankMutationRevision()==rev+1,
            "legitimate placeholder setting lost persistence lineage");
    }

    public static void main(String[] args)throws Exception{
        emptyInventoryDrags();
        bankTabNoops();
        depositAllNoops();
        System.out.println(
            "BANK_NOOP_SAVE_LINEAGE_PASS"+
            " emptySourceNoItemSteal=true"+
            " sameValueDragNoPacket=true"+
            " validDragStillMutates=true"+
            " duplicateSetTabNoPacket=true"+
            " emptySwapTabNoPacket=true"+
            " changedTabStillPersists=true"+
            " emptyDepositNoPacket=true"+
            " positiveDepositStillConserves=true"+
            " placeholderToggleStillPersistent=true");
    }
}

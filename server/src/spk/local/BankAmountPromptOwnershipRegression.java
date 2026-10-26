package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * The existing bank X amount prompt is a single-use capability bound to
 * one exact packet writer, bank presentation, bank contents and inventory
 * mutation lineage. An ABA restoration cannot revive a stale prompt.
 *
 * Does not assert durable account leases or remote client acknowledgement.
 */
public final class BankAmountPromptOwnershipRegression {
    private static final int BANK=BankState.BANK_CONTAINER;
    private static final int BANK_INV=BankState.BANK_INVENTORY_CONTAINER;

    private BankAmountPromptOwnershipRegression(){}

    private static void check(boolean valid,String why){
        if(!valid)throw new AssertionError("BANK_X_OWNERSHIP "+why);
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(8192),
            new IsaacCipher(new int[]{0,0,0,0}));
    }

    private static ItemContainerAction action(
        int widget,int slot,int id
    ){
        return new ItemContainerAction(135,widget,slot,id,0,"X");
    }

    private static void prompt(
        BankState bank,ServerPacketWriter writer,
        int widget,int slot,int item
    )throws Exception{
        String value=bank.apply(action(widget,slot,item),writer);
        check(value.startsWith(
            widget==BANK?"WITHDRAW_X_PROMPT_SENT":"STORE_X_PROMPT_SENT"),
            "could not establish bank X prompt "+value);
    }

    private static BankState open(ServerPacketWriter writer)throws Exception{
        BankState bank=new BankState();
        bank.open(writer);
        return bank;
    }

    private static void seedInventory(BankState bank){
        int[] ids=new int[bank.inventoryCapacity()];
        int[] counts=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=995;counts[0]=10;
        bank.replaceInventorySemantic(ids,counts);
    }

    private static void writerRotation()throws Exception{
        ServerPacketWriter first=writer(),second=writer();
        BankState bank=open(first);
        long inventory=bank.inventoryMutationRevision();
        long bankGeneration=bank.bankMutationRevision();
        prompt(bank,first,BANK,0,995);
        check("REJECTED_X_SESSION_WRITER_MISMATCH".equals(
            bank.applyAmount(1,second)),"substituted writer accepted old X");
        check("IGNORED_NO_PENDING_X amount=1".equals(
            bank.applyAmount(1,first)),"writer mismatch resurrected old X");
        check(bank.inventoryMutationRevision()==inventory&&
            bank.bankMutationRevision()==bankGeneration&&
            bank.inventoryCount(995)==0&&
            bank.bankAt(0).qty==100000,
            "writer rotation moved value from bank");
    }

    private static void staleInventory(boolean deposit)throws Exception{
        ServerPacketWriter writer=writer();
        BankState bank=open(writer);
        seedInventory(bank);
        int widget=deposit?BANK_INV:BANK;
        int qty=deposit?10:100000;
        prompt(bank,writer,widget,0,995);
        long afterPrompt=bank.inventoryMutationRevision();
        // Semantically identical inventory replacement changes its lineage.
        int[] ids=new int[bank.inventoryCapacity()];
        int[] counts=new int[ids.length];
        Arrays.fill(ids,-1);ids[0]=995;counts[0]=10;
        bank.replaceInventorySemantic(ids,counts);
        check(bank.inventoryMutationRevision()==afterPrompt+1,
            "ABA inventory restoration did not advance generation");
        long ready=bank.inventoryMutationRevision();
        check("REJECTED_X_STALE_BANK_OR_INVENTORY_GENERATION".equals(
            bank.applyAmount(1,writer)),
            "stale inventory ABA triggered X transfer deposit="+deposit);
        check(bank.inventoryMutationRevision()==ready&&
            bank.inventoryAt(0).qty==10&&
            bank.bankAt(0).qty==100000&&
            bank.inventoryCount(995)==10,
            "stale inventory mutated funds qty="+qty);
    }

    private static void staleBankABA()throws Exception{
        ServerPacketWriter writer=writer();
        BankState bank=open(writer);
        prompt(bank,writer,BANK,0,995);
        long before=bank.bankMutationRevision();
        String moved=bank.applyDrag(
            new ContainerDrag(BANK,0,0,1),writer);
        String restored=bank.applyDrag(
            new ContainerDrag(BANK,0,0,1),writer);
        check(moved.startsWith("DRAG_OK")&&
            restored.startsWith("DRAG_OK")&&
            bank.bankMutationRevision()==before+2&&
            bank.bankAt(0).itemId==995,
            "bank ABA fixture did not restore same slots");
        long inv=bank.inventoryMutationRevision();
        check("REJECTED_X_STALE_BANK_OR_INVENTORY_GENERATION".equals(
            bank.applyAmount(1,writer)),
            "bank ABA restored stale X authority");
        check(bank.inventoryMutationRevision()==inv&&
            bank.bankAt(0).qty==100000&&
            bank.inventoryCount(995)==0,
            "bank ABA stale X moved items");
    }

    private static void ownerViewReset()throws Exception{
        ServerPacketWriter writer=writer();
        BankState bank=open(writer);
        prompt(bank,writer,BANK,0,995);
        long rev=bank.bankViewRevision();
        check(bank.clientClosed()&&bank.bankViewRevision()==rev+1,
            "bank close did not retire X prompt");
        bank.open(writer);
        check("IGNORED_NO_PENDING_X amount=1".equals(
            bank.applyAmount(1,writer))&&
            bank.inventoryCount(995)==0,
            "bank close/reopen revived stale prompt");
    }

    private static void terminalWriter()throws Exception{
        ServerPacketWriter writer=writer();
        BankState bank=open(writer);
        prompt(bank,writer,BANK,0,995);
        writer.markTerminal();
        check("REJECTED_X_SESSION_WRITER_TERMINAL".equals(
            bank.applyAmount(1,writer)),
            "terminal writer transferred X");
        check(bank.inventoryCount(995)==0,
            "terminal X moved items");
    }

    private static void normalWithdrawAndDeposit()throws Exception{
        ServerPacketWriter writer=writer();
        BankState bank=open(writer);
        seedInventory(bank);
        long bankBefore=bank.bankMutationRevision();
        long inventoryBefore=bank.inventoryMutationRevision();
        prompt(bank,writer,BANK,0,995);
        String withdraw=bank.applyAmount(3,writer);
        check(withdraw.startsWith("WITHDRAW_X_OK amount=3")&&
            bank.bankAt(0).qty==99997&&
            bank.inventoryCount(995)==13&&
            bank.bankMutationRevision()==bankBefore+1&&
            bank.inventoryMutationRevision()==inventoryBefore+1,
            "valid same-writer X withdraw was denied");
        check("IGNORED_NO_PENDING_X amount=1".equals(
            bank.applyAmount(1,writer)),
            "successful withdraw X was replayed");
        prompt(bank,writer,BANK_INV,0,995);
        String store=bank.applyAmount(2,writer);
        check(store.startsWith("STORE_X_OK amount=2")&&
            bank.bankAt(0).qty==99999&&
            bank.inventoryCount(995)==11,
            "valid same-writer store X failed");
    }

    private static void supersededPrompt()throws Exception{
        ServerPacketWriter writer=writer();
        BankState bank=open(writer);
        seedInventory(bank);
        prompt(bank,writer,BANK,0,995);
        prompt(bank,writer,BANK_INV,0,995);
        String result=bank.applyAmount(2,writer);
        check(result.startsWith("STORE_X_OK amount=2")&&
            bank.inventoryCount(995)==8&&
            bank.bankAt(0).qty==100002,
            "superseded X prompt executed older action");
    }

    public static void main(String[] args)throws Exception{
        writerRotation();
        staleInventory(false);
        staleInventory(true);
        staleBankABA();
        ownerViewReset();
        terminalWriter();
        normalWithdrawAndDeposit();
        supersededPrompt();
        System.out.println(
            "BANK_AMOUNT_PROMPT_OWNERSHIP_PASS"+
            " writerIdentityPinned=true terminalDenied=true"+
            " bankABAStaleDenied=true inventoryABAStaleDenied=true"+
            " bankViewResetDenied=true oneShotTransfer=true"+
            " validWithdrawAndStore=true latestPromptWins=true"+
            " crossWorldDurabilityClaim=false");
    }
}

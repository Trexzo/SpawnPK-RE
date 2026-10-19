package spk.local;

import java.io.*;
import java.util.*;

/**
 * Verify that only completed, already-supported bank state transitions can
 * request a LocalSession account save. Read-only search, rejected/stale
 * actions, prompts, no-op drags and no-pending-X must not produce save
 * intents. Does not claim a durable disk receipt or original-v308 GUI run.
 */
public final class BankSaveIntentGateRegression {
    private BankSaveIntentGateRegression(){}
    private static final int INVENTORY=BankState.NORMAL_INVENTORY_CONTAINER;
    private static final int BANK=BankState.BANK_CONTAINER;
    private static final int BANK_INV=BankState.BANK_INVENTORY_CONTAINER;

    private static void require(boolean allowed,String why){
        if(!allowed)throw new AssertionError("BANK_SAVE_INTENT_GATE "+why);
    }
    private static void denial(
        LocalBankRequestHandler.Result result,String expected,
        String label
    ){
        require(result!=null&&result.saveReason==null&&
            result.logText.contains(expected),
            "unexpected persistence on "+label+"="+
            (result==null?"null":result.logText)+
            " reason="+(result==null?"null":result.saveReason));
    }
    private static void success(
        LocalBankRequestHandler.Result result,String reason,
        String resultLabel
    ){
        require(result!=null&&reason.equals(result.saveReason)&&
            result.logText.contains(resultLabel),
            "lost completion save intent for "+resultLabel+
            " result="+(result==null?"null":result.logText)+
            " reason="+(result==null?"null":result.saveReason));
    }

    private static void statusCorpus(){
        int denied=0;
        String[] rejected={
            "IGNORED_BANK_CLOSED","IGNORED_NO_PENDING_X amount=1",
            "X_NO_ITEMS_MOVED amount=0","REJECTED_INVENTORY_SLOT",
            "REJECTED_BANK_ITEM_MISMATCH expected=995",
            "REJECTED_QUANTITY_OVERFLOW item=995",
            "WITHDRAW_X_PROMPT_SENT opcode=27",
            "STORE_X_PROMPT_SENT opcode=27",
            "BAG_EXCHANGE_SERVER_AUTHORITY_DEFERRED item=995 mutation=NONE",
            "DRAG_NOOP","INVENTORY_DRAG_NOOP",
            "REJECTED_DRAG_SLOT","OBSERVED_NON_BANK_DRAG widget=9999",
            "BANKSEARCH_REQUEST_ACCEPTED query=whip",
            "REJECTED_SETBANKTAB_RANGE",
            "SWAPBANKTAB_OBSERVED_UNCERTIFIED target=9",
            "IGNORED_NON_BANK_COMMAND","",
            "WITHDRAW_OKAY amount=1","WITHDRAW_X_OKAY amount=1",
            "STORE_OKAY amount=1","DRAG_OKAY mode=0",
            "SETBANKTAB_OKAY slot=1"
        };
        for(String reject:rejected){
            require(!BankMutationSaveClassifier.itemAction(reject)&&
                !BankMutationSaveClassifier.amount(reject)&&
                !BankMutationSaveClassifier.drag(reject)&&
                !BankMutationSaveClassifier.command(reject),
                "rejected/unverified status authorized save="+reject);
            denied++;
        }
        require(!BankMutationSaveClassifier.itemAction(null)&&
            !BankMutationSaveClassifier.amount(null)&&
            !BankMutationSaveClassifier.drag(null)&&
            !BankMutationSaveClassifier.command(null),
            "null status authorized save");
        for(String ok:new String[]{
            "WITHDRAW_OK amount=1","WITHDRAW_ALL_BUT_ONE_OK amount=1",
            "WITHDRAW_CONFIGURED_OK amount=1","STORE_OK amount=1"
        })require(BankMutationSaveClassifier.itemAction(ok),
            "lost item action status="+ok);
        for(String ok:new String[]{
            "WITHDRAW_X_OK amount=1","STORE_X_OK amount=1"
        })require(BankMutationSaveClassifier.amount(ok),
            "lost amount status="+ok);
        for(String ok:new String[]{
            "INVENTORY_DRAG_OK mode=0","DRAG_OK mode=1"
        })require(BankMutationSaveClassifier.drag(ok),
            "lost drag status="+ok);
        for(String ok:new String[]{
            "SETBANKTAB_OK slot=0","SWAPBANKTAB_OK sourceTab=0"
        })require(BankMutationSaveClassifier.command(ok),
            "lost bank tab mutation="+ok);
        require(denied==23,"denial corpus incomplete");
    }

    public static void main(String[] args)throws Exception{
        statusCorpus();
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        LocalBankRequestHandler handler=
            new LocalBankRequestHandler(player,bank);
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter packetWriter=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{4,8,15,16}));
        long original=bank.inventoryMutationRevision();
        denial(handler.handleAmount(1,packetWriter),
            "IGNORED_BANK_CLOSED","closed amount");
        denial(handler.handleCommand("::banksearch whip",packetWriter),
            "IGNORED_BANK_CLOSED","closed search");
        denial(handler.handleDrag(
            new ContainerDrag(BANK,0,0,1),packetWriter),
            "IGNORED_BANK_CLOSED","closed bank drag");
        denial(handler.handleDrag(
            new ContainerDrag(INVENTORY,0,0,0),packetWriter),
            "INVENTORY_DRAG_NOOP","same-slot inventory");
        require(bank.inventoryMutationRevision()==original,
            "no-op save tests mutated canonical inventory");

        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=4151;qty[0]=1;
        ids[1]=995;qty[1]=100;
        bank.replaceInventorySemantic(ids,qty);
        bank.open(packetWriter);

        denial(handler.handleCommand("::banksearch whip",packetWriter),
            "BANKSEARCH_REQUEST_ACCEPTED","bank search");
        denial(handler.handleCommand("::setbanktab 999 3",packetWriter),
            "REJECTED_SETBANKTAB_RANGE","invalid tab slot");
        denial(handler.handleCommand("::swapbanktab 0 9 10",packetWriter),
            "SWAPBANKTAB_OBSERVED_UNCERTIFIED","unverified swap tab");
        require(handler.handleCommand("::help",packetWriter)==null,
            "nonbank command failed to fall through");
        success(handler.handleCommand("::setbanktab 0 2",packetWriter),
            "BANK_COMMAND","SETBANKTAB_OK");
        success(handler.handleCommand("::swapbanktab 0 2 0",packetWriter),
            "BANK_COMMAND","SWAPBANKTAB_OK");

        denial(handler.handleDrag(
            new ContainerDrag(9999,0,0,1),packetWriter),
            "OBSERVED_NON_BANK_DRAG","unknown widget");
        denial(handler.handleDrag(
            new ContainerDrag(INVENTORY,0,0,0),packetWriter),
            "INVENTORY_DRAG_NOOP","noop inventory");
        denial(handler.handleDrag(
            new ContainerDrag(INVENTORY,0,99,0),packetWriter),
            "REJECTED_DRAG_SLOT","invalid inventory slot");
        denial(handler.handleDrag(
            new ContainerDrag(BANK_INV,0,0,0),packetWriter),
            "DRAG_NOOP","noop bank inventory");
        success(handler.handleDrag(
            new ContainerDrag(INVENTORY,0,0,1),packetWriter),
            "INVENTORY_DRAG","INVENTORY_DRAG_OK");
        require(bank.inventoryAt(0).itemId==995&&
            bank.inventoryAt(1).itemId==4151,
            "inventory drag did not commit semantic exchange");
        success(handler.handleDrag(
            new ContainerDrag(BANK,0,0,1),packetWriter),
            "BANK_DRAG","DRAG_OK");

        denial(handler.handleAmount(5,packetWriter),
            "IGNORED_NO_PENDING_X","unbound amount");
        require(bank.apply(new ItemContainerAction(
            135,BANK,0,560,0,"WITHDRAW_X"),packetWriter)
            .startsWith("WITHDRAW_X_PROMPT_SENT"),
            "withdraw-X fixture failed");
        denial(handler.handleAmount(0,packetWriter),
            "X_NO_ITEMS_MOVED","zero withdraw amount");
        denial(handler.handleAmount(1,packetWriter),
            "IGNORED_NO_PENDING_X","cleared withdraw prompt");
        // After the bank swap, coins are slot 1.
        require(bank.apply(new ItemContainerAction(
            135,BANK,1,995,0,"WITHDRAW_X"),packetWriter)
            .startsWith("WITHDRAW_X_PROMPT_SENT"),
            "withdraw-X positive fixture failed");
        int coinsBefore=bank.inventoryCount(995);
        success(handler.handleAmount(1,packetWriter),
            "BANK_AMOUNT","WITHDRAW_X_OK amount=1");
        require(bank.inventoryCount(995)==coinsBefore+1,
            "withdraw-X completed without inventory credit");

        // The successful withdrawal merged into inventory coin slot 0.
        require(bank.apply(new ItemContainerAction(
            135,BANK_INV,0,995,0,"STORE_X"),packetWriter)
            .startsWith("STORE_X_PROMPT_SENT"),
            "store-X fixture failed");
        success(handler.handleAmount(1,packetWriter),
            "BANK_AMOUNT","STORE_X_OK amount=1");
        require(bank.inventoryCount(995)==coinsBefore,
            "store-X completed without inventory debit");

        bank.close(packetWriter);
        denial(handler.handleCommand("::banksearch whip",packetWriter),
            "IGNORED_BANK_CLOSED","closed bank search");
        denial(handler.handleDrag(
            new ContainerDrag(BANK_INV,0,0,1),packetWriter),
            "IGNORED_BANK_CLOSED","closed bank inventory drag");
        System.out.println("BANK_SAVE_INTENT_GATE_PASS"+
            " failClosedStatusCorpus=23"+
            " closedSearchAmountDragNoSave=true"+
            " openSearchNoSave=true bankTabMutationsSave=true"+
            " invalidAndNoopDragNoSave=true committedDragsSave=true"+
            " amountWithoutPromptNoSave=true zeroXNoSave=true"+
            " XWithdrawSave=true XStoreSave=true"+
            " completedWithdrawStoreConserveItems=true"+
            " sourceOnlyNoDiskDurabilityClaim=true");
    }
}

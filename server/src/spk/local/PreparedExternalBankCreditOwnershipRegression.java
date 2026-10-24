package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * An external bank credit prepared against one exact BankState and lineage
 * cannot be replayed after a semantically identical ABA, applied to another
 * player's equal bank preimage, or committed twice by competing workers.
 *
 * The existing World/mailbox caller still owns durable transaction proof.
 * This only fences a source-local deferred grant.
 */
public final class PreparedExternalBankCreditOwnershipRegression {
    private static final int COINS=995;
    private static final int BANK=BankState.BANK_CONTAINER;

    private PreparedExternalBankCreditOwnershipRegression(){}

    private static void check(boolean condition,String why){
        if(!condition)throw new AssertionError(
            "PREPARED_EXTERNAL_BANK_CREDIT "+why);
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(8192),
            new IsaacCipher(new int[]{0,0,0,0}));
    }

    private static void expectStale(
        BankState target,BankState.PreparedExternalBankCredit prepared,
        String caseName
    ){
        boolean denied=false;
        try{
            target.commitPreparedExternalBankCredit(prepared);
        }catch(IllegalStateException expected){
            denied=true;
        }
        check(denied,caseName+" was accepted");
    }

    private static void exactOwnerGrant(){
        BankState owner=new BankState();
        long generation=owner.bankMutationRevision();
        int before=owner.bankAt(0).qty;
        BankState.PreparedExternalBankCredit prepared=
            owner.prepareExternalBankCredit(COINS,7);
        check(prepared.accepted()&&prepared.owner==owner&&
            prepared.bankGeneration==generation,
            "prepared exact ownership not captured");
        int slot=owner.commitPreparedExternalBankCredit(prepared);
        check(slot==0&&owner.bankAt(0).qty==before+7&&
            owner.bankMutationRevision()==generation+1,
            "valid external credit did not commit once");
        expectStale(owner,prepared,"duplicate exact credit");
        check(owner.bankAt(0).qty==before+7,
            "duplicate credit changed balance");
    }

    private static void wrongBankEqualPreimage(){
        BankState original=new BankState();
        BankState other=new BankState();
        BankState.PreparedExternalBankCredit prepared=
            original.prepareExternalBankCredit(COINS,13);
        long otherRevision=other.bankMutationRevision();
        expectStale(other,prepared,"cross-bank owner replay");
        check(other.bankMutationRevision()==otherRevision&&
            other.bankAt(0).qty==100000&&
            original.bankAt(0).qty==100000,
            "cross-bank replay changed unrelated player");
        check(original.commitPreparedExternalBankCredit(prepared)==0&&
            original.bankAt(0).qty==100013,
            "rejected cross-owner attempt consumed original grant");
    }

    private static void bankSlotABA()throws Exception{
        BankState bank=new BankState();
        ServerPacketWriter writer=writer();
        bank.open(writer);
        long generation=bank.bankMutationRevision();
        BankState.PreparedExternalBankCredit prepared=
            bank.prepareExternalBankCredit(COINS,9);
        String moved=bank.applyDrag(
            new ContainerDrag(BANK,0,0,1),writer);
        String returned=bank.applyDrag(
            new ContainerDrag(BANK,0,0,1),writer);
        check(moved.startsWith("DRAG_OK")&&
            returned.startsWith("DRAG_OK")&&
            bank.bankAt(0).itemId==COINS&&
            bank.bankAt(0).qty==100000&&
            bank.bankMutationRevision()==generation+2,
            "bank ABA fixture did not restore original slots");
        expectStale(bank,prepared,"restored equal bank slot ABA");
        check(bank.bankAt(0).qty==100000,
            "bank ABA injected stale credit");
    }

    private static void restoredAccountABA(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit prepared=
            bank.prepareExternalBankCredit(COINS,21);
        BankState.Stack[] bankPreimage=
            new BankState.Stack[bank.bankCapacity()];
        BankState.Stack[] inventoryPreimage=
            new BankState.Stack[bank.inventoryCapacity()];
        for(int i=0;i<bankPreimage.length;i++)
            bankPreimage[i]=bank.bankAt(i);
        for(int i=0;i<inventoryPreimage.length;i++)
            inventoryPreimage[i]=bank.inventoryAt(i);
        long generation=bank.bankMutationRevision();
        bank.restoreAccountState(
            bankPreimage,inventoryPreimage,false);
        check(bank.bankMutationRevision()==generation+1&&
            bank.bankAt(0).qty==100000,
            "identical account hydrate did not advance bank lineage");
        expectStale(bank,prepared,"account hydrate bank ABA");
        check(bank.bankAt(0).qty==100000,
            "restored bank accepted stale outside credit");
    }

    private static void overlappingCredits()throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<32;trial++){
                BankState bank=new BankState();
                BankState.PreparedExternalBankCredit first=
                    bank.prepareExternalBankCredit(COINS,1);
                BankState.PreparedExternalBankCredit second=
                    bank.prepareExternalBankCredit(COINS,1);
                CountDownLatch ready=new CountDownLatch(2);
                CountDownLatch go=new CountDownLatch(1);
                Callable<Boolean> one=()->{
                    ready.countDown();
                    go.await();
                    try{
                        bank.commitPreparedExternalBankCredit(first);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Callable<Boolean> two=()->{
                    ready.countDown();
                    go.await();
                    try{
                        bank.commitPreparedExternalBankCredit(second);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Future<Boolean> a=workers.submit(one),b=workers.submit(two);
                check(ready.await(5,TimeUnit.SECONDS),
                    "contending credits did not start");
                go.countDown();
                int commits=(a.get(5,TimeUnit.SECONDS)?1:0)+
                    (b.get(5,TimeUnit.SECONDS)?1:0);
                check(commits==1&&bank.bankAt(0).qty==100001&&
                    bank.bankMutationRevision()==1,
                    "overlapping prepared bank credits double granted trial="+
                    trial);
            }
        }finally{
            workers.shutdownNow();
        }
    }

    private static void outstandingXInvalidated()throws Exception{
        BankState bank=new BankState();
        ServerPacketWriter writer=writer();
        bank.open(writer);
        String prompt=bank.apply(
            new ItemContainerAction(
                135,BANK,0,COINS,0,"WITHDRAW_X"),
            writer);
        check(prompt.startsWith("WITHDRAW_X_PROMPT_SENT"),
            "bank X prompt fixture failed");
        BankState.PreparedExternalBankCredit prepared=
            bank.prepareExternalBankCredit(COINS,17);
        bank.commitPreparedExternalBankCredit(prepared);
        check("REJECTED_X_STALE_BANK_OR_INVENTORY_GENERATION".equals(
            bank.applyAmount(1,writer)),
            "external credit did not invalidate prior bank X prompt");
        check(bank.bankAt(0).qty==100017&&
            bank.inventoryCount(COINS)==0,
            "prior X moved external granted coins");
    }

    public static void main(String[] args)throws Exception{
        exactOwnerGrant();
        wrongBankEqualPreimage();
        bankSlotABA();
        restoredAccountABA();
        overlappingCredits();
        outstandingXInvalidated();
        System.out.println(
            "PREPARED_EXTERNAL_BANK_CREDIT_OWNERSHIP_PASS"+
            " exactOwner=true foreignEqualStateDenied=true"+
            " sameSlotABARevoked=true hydrationABARevoked=true"+
            " competingSingleCredit=32 duplicateDenied=true"+
            " pendingBankXRevokedAfterExternalCredit=true"+
            " durableAccountCommitClaim=false");
    }
}

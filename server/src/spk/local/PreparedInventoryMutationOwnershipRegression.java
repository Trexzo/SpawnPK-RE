package spk.local;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Deferred inventory additions and consumptions carry exact source-local
 * owner/generation and an immutable-in-effect preflight postimage.
 * The outer World, packet publication and durable account lease are separate.
 */
public final class PreparedInventoryMutationOwnershipRegression {
    private PreparedInventoryMutationOwnershipRegression(){}

    private static void check(boolean good,String message){
        if(!good)throw new AssertionError("INVENTORY_PREPARED_OWNER "+message);
    }

    private static void seed(BankState bank,int qty){
        int[] ids=new int[bank.inventoryCapacity()];
        int[] amounts=new int[ids.length];
        Arrays.fill(ids,-1);ids[0]=995;amounts[0]=qty;
        bank.replaceInventorySemantic(ids,amounts);
    }

    private static void denied(
        BankState bank,BankState.PreparedInventoryMutation mutation,
        String scenario
    ){
        long revision=bank.inventoryMutationRevision();
        int coins=bank.inventoryCount(995);
        boolean failed=false;
        try{
            bank.commitPreparedInventoryMutation(mutation);
        }catch(IllegalStateException expected){
            failed=true;
        }
        check(failed&&bank.inventoryMutationRevision()==revision&&
            bank.inventoryCount(995)==coins,
            "unsafe mutation accepted "+scenario);
    }

    private static void positiveAndReplay(){
        BankState bank=new BankState();
        seed(bank,10);
        long before=bank.inventoryMutationRevision();
        BankState.PreparedInventoryMutation add=
            bank.prepareAddInventoryAmount(995,7);
        check(add.accepted()&&add.owner==bank&&
            add.inventoryGeneration==before,
            "prepared owner/revision");
        check(bank.commitPreparedInventoryMutation(add)==0&&
            bank.inventoryCount(995)==17,
            "accepted add missing");
        denied(bank,add,"duplicate add");
        BankState.PreparedInventoryMutation remove=
            bank.prepareConsumeInventoryAll(0,995);
        check(remove.accepted()&&
            bank.commitPreparedInventoryMutation(remove)==17&&
            bank.inventoryCount(995)==0,
            "consume-all did not conserve inventory");
        denied(bank,remove,"duplicate consume");
    }

    private static void foreignAndABA()throws Exception{
        BankState a=new BankState(),b=new BankState();
        BankState.PreparedInventoryMutation grant=
            a.prepareAddInventoryAmount(995,3);
        denied(b,grant,"same-valued foreign BankState");
        check(a.commitPreparedInventoryMutation(grant)==0&&
            a.inventoryCount(995)==3&&b.inventoryCount(995)==0,
            "foreign attempt consumed original grant");
        seed(a,5);
        BankState.PreparedInventoryMutation aba=
            a.prepareAddInventoryAmount(995,2);
        long revision=a.inventoryMutationRevision();
        seed(a,5);
        check(a.inventoryMutationRevision()==revision+1,
            "ABA test failed to mutate lineage");
        denied(a,aba,"same-valued inventory ABA");
        BankState.PreparedInventoryMutation view=
            b.prepareAddInventoryAmount(995,1);
        b.open(new ServerPacketWriter(
            new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4})));
        denied(b,view,"bank presentation changed");
    }

    private static void tamperAndPublication()throws Exception{
        BankState bank=new BankState();
        seed(bank,11);
        BankState.PreparedInventoryMutation inflated=
            bank.prepareAddInventoryAmount(995,2);
        inflated.postimage[0].qty=Integer.MAX_VALUE;
        denied(bank,inflated,"coin quantity inflated");
        boolean noPacket=false;
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));
        try{
            bank.writePreparedInventoryMutation(inflated,writer);
        }catch(IllegalStateException expected){
            noPacket=true;
        }
        check(noPacket&&wire.size()==0,
            "tampered preflight sent a packet");
        BankState.PreparedInventoryMutation side=
            bank.prepareAddInventoryAmount(995,1);
        side.postimage[2]=new BankState.Stack(28824,1);
        denied(bank,side,"arbitrary side item");
        BankState.PreparedInventoryMutation consumed=
            bank.prepareConsumeInventoryAll(0,995);
        consumed.expectedInventory[0].qty=1;
        denied(bank,consumed,"forged expected source");
        BankState.PreparedInventoryMutation valid=
            bank.prepareAddInventoryAmount(995,2);
        bank.writePreparedInventoryMutation(valid,writer);
        check(wire.size()>0&&bank.inventoryCount(995)==11,
            "prepublication changed semantic inventory");
        check(bank.commitPreparedInventoryMutation(valid)==0&&
            bank.inventoryCount(995)==13,
            "accepted packet preflight and commit diverged");
    }

    private static void rejected(){
        BankState bank=new BankState();
        BankState.PreparedInventoryMutation bad=
            bank.prepareConsumeInventoryAll(0,995);
        check(!bad.accepted()&&
            "REJECTED_INVENTORY_SLOT".equals(bad.rejection),
            "empty inventory consume not rejected");
        boolean refused=false;
        try{
            bank.commitPreparedInventoryMutation(bad);
        }catch(IllegalArgumentException expected){
            refused=true;
        }
        check(refused&&bank.inventoryCount(995)==0,
            "rejected preflight became a credit");
    }

    private static void competingAdds()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<32;trial++){
                BankState bank=new BankState();
                BankState.PreparedInventoryMutation a=
                    bank.prepareAddInventoryAmount(995,1);
                BankState.PreparedInventoryMutation b=
                    bank.prepareAddInventoryAmount(995,1);
                CountDownLatch ready=new CountDownLatch(2);
                CountDownLatch start=new CountDownLatch(1);
                Callable<Boolean> first=()->{
                    ready.countDown();start.await();
                    try{
                        bank.commitPreparedInventoryMutation(a);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Callable<Boolean> second=()->{
                    ready.countDown();start.await();
                    try{
                        bank.commitPreparedInventoryMutation(b);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Future<Boolean> x=pool.submit(first);
                Future<Boolean> y=pool.submit(second);
                check(ready.await(5,TimeUnit.SECONDS),
                    "concurrent tasks not ready");
                start.countDown();
                int count=(x.get(5,TimeUnit.SECONDS)?1:0)+
                    (y.get(5,TimeUnit.SECONDS)?1:0);
                check(count==1&&bank.inventoryCount(995)==1&&
                    bank.inventoryMutationRevision()==1,
                    "concurrent prepared add duplicated trial="+trial);
            }
        }finally{
            pool.shutdownNow();
        }
    }

    public static void main(String[] args)throws Exception{
        positiveAndReplay();
        foreignAndABA();
        tamperAndPublication();
        rejected();
        competingAdds();
        System.out.println(
            "INVENTORY_PREPARED_OWNER_PASS"+
            " foreignOwnerDenied=true inventoryABARevoked=true"+
            " bankViewRevoked=true packetTamperDenied=true"+
            " sourceAndPostimageTamperDenied=true"+
            " validAddConsumeOneShot=true duplicateDenied=true"+
            " competingAddPairs=32 exactlyOneCommit=true"+
            " extraDonorAuthority=false durableProof=false");
    }
}

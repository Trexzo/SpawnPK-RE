package spk.local;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * R25: preserve certified pet Drop/Pick-up item transitions while preventing
 * foreign, stale or altered prepared pet inventory postimages from being
 * published or committed. This does not simulate remote client acceptance
 * or the World/NPC/petState half of a multi-domain transaction.
 */
public final class PreparedPetInventoryOwnershipRegression {
    private static final int PET=24019;
    private PreparedPetInventoryOwnershipRegression(){}

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(
            "PREPARED_PET_INVENTORY_OWNERSHIP "+message
        );
    }

    private static ServerPacketWriter writer(ByteArrayOutputStream output){
        return new ServerPacketWriter(
            output,new IsaacCipher(new int[]{2,3,5,7})
        );
    }

    private static void seed(BankState bank,int count){
        int[] ids=new int[bank.inventoryCapacity()];
        int[] quantities=new int[ids.length];
        Arrays.fill(ids,-1);
        if(count>0){ids[3]=PET;quantities[3]=count;}
        bank.replaceInventorySemantic(ids,quantities);
    }

    private static void rejectedCommit(
        BankState bank,
        BankState.PreparedPetInventoryMutation prepared,
        String label
    ){
        int before=bank.inventoryCount(PET);
        long generation=bank.inventoryMutationRevision();
        boolean refused=false;
        try{
            bank.commitPreparedPetInventory(prepared);
        }catch(IllegalStateException expected){
            refused=true;
        }
        require(refused&&bank.inventoryCount(PET)==before&&
            bank.inventoryMutationRevision()==generation,
            "unsafe pet mutation committed "+label);
    }

    private static void rejectedPublication(
        BankState bank,
        BankState.PreparedPetInventoryMutation prepared,
        String label
    )throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter output=writer(bytes);
        boolean refused=false;
        try{
            bank.publishPreparedPetInventory(prepared,output);
        }catch(IllegalStateException expected){
            refused=true;
        }
        require(refused&&bytes.size()==0,
            "unsafe pet mutation published packet "+label);
    }

    private static void validDropAndPickup()throws Exception{
        BankState bank=new BankState();
        seed(bank,1);
        long generation=bank.inventoryMutationRevision();
        BankState.PreparedPetInventoryMutation drop=
            bank.preparePetDropInventory(3,PET,null);
        require(drop.accepted()&&drop.owner==bank&&
            drop.inventoryGeneration==generation&&
            drop.newPetSlot==3&&drop.oldPetReturnedSlot==-1,
            "valid drop preflight incorrect");
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter output=writer(wire);
        bank.publishPreparedPetInventory(drop,output);
        require(wire.size()>0&&bank.inventoryCount(PET)==1,
            "drop presentation altered semantic inventory");
        bank.commitPreparedPetInventory(drop);
        require(bank.inventoryCount(PET)==0&&
            bank.inventoryMutationRevision()==generation+1,
            "pet drop did not consume exactly one");
        rejectedCommit(bank,drop,"drop replay");
        rejectedPublication(bank,drop,"drop replay publication");

        BankState.PreparedPetInventoryMutation pickup=
            bank.preparePetPickupInventory(PET);
        require(pickup.accepted()&&pickup.oldPetReturnedSlot==0&&
            pickup.owner==bank,
            "pet pickup prepared wrong slot");
        bank.publishPreparedPetInventory(pickup,output);
        bank.commitPreparedPetInventory(pickup);
        require(bank.inventoryCount(PET)==1&&
            bank.inventoryAt(0).itemId==PET,
            "pet pickup did not restore one owned item");
        rejectedCommit(bank,pickup,"pickup replay");
    }

    private static void foreignAndInventoryABA()throws Exception{
        BankState owner=new BankState();
        BankState unrelated=new BankState();
        BankState.PreparedPetInventoryMutation pickup=
            owner.preparePetPickupInventory(PET);
        rejectedCommit(unrelated,pickup,"equal foreign inventory");
        rejectedPublication(unrelated,pickup,"equal foreign inventory");
        require(owner.inventoryCount(PET)==0&&
            unrelated.inventoryCount(PET)==0,
            "foreign test changed unrelated inventory");
        owner.commitPreparedPetInventory(pickup);
        require(owner.inventoryCount(PET)==1,
            "foreign denial consumed original prepared grant");

        BankState aba=new BankState();
        seed(aba,1);
        BankState.PreparedPetInventoryMutation drop=
            aba.preparePetDropInventory(3,PET,null);
        long before=aba.inventoryMutationRevision();
        seed(aba,1);
        require(aba.inventoryMutationRevision()==before+1,
            "same-contents inventory ABA not recorded");
        rejectedCommit(aba,drop,"same-contents inventory ABA");
        rejectedPublication(aba,drop,"same-contents inventory ABA");
    }

    private static void bankViewABA()throws Exception{
        BankState bank=new BankState();
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter output=writer(wire);
        bank.open(output);
        BankState.PreparedPetInventoryMutation pickup=
            bank.preparePetPickupInventory(PET);
        long initialView=bank.bankViewRevision();
        bank.close(output);
        bank.open(output);
        require(bank.isOpen()&&bank.bankViewRevision()==initialView+2,
            "bank close/open ABA did not rotate view generation");
        rejectedCommit(bank,pickup,"same-open-state bank view ABA");
        rejectedPublication(bank,pickup,"same-open-state bank view ABA");
    }

    private static void snapshotTamper()throws Exception{
        BankState bank=new BankState();
        seed(bank,1);

        BankState.PreparedPetInventoryMutation expected=
            bank.preparePetDropInventory(3,PET,null);
        expected.expectedInventory[3].qty=999;
        rejectedCommit(bank,expected,"inflated expected pet source");
        rejectedPublication(bank,expected,"inflated expected pet source");

        BankState.PreparedPetInventoryMutation post=
            bank.preparePetDropInventory(3,PET,null);
        post.postimage[3]=new BankState.Stack(PET,999);
        rejectedCommit(bank,post,"forged drop postimage");
        rejectedPublication(bank,post,"forged drop postimage");

        BankState.PreparedPetInventoryMutation side=
            bank.preparePetPickupInventory(PET);
        side.postimage[10]=new BankState.Stack(28824,20);
        rejectedCommit(bank,side,"unauthorized side item");
        rejectedPublication(bank,side,"unauthorized side item");

        BankState.PreparedPetInventoryMutation substitute=
            bank.preparePetPickupInventory(PET);
        substitute.postimage[substitute.oldPetReturnedSlot]=
            new BankState.Stack(28807,1);
        rejectedCommit(bank,substitute,"replaced returned pet identity");
        rejectedPublication(bank,substitute,"replaced returned pet identity");
    }

    private static void noInventoryCapacityAndPreflightRejection(){
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,995);
        Arrays.fill(qty,1);
        bank.replaceInventorySemantic(ids,qty);
        BankState.PreparedPetInventoryMutation blocked=
            bank.preparePetPickupInventory(PET);
        require(!blocked.accepted()&&
            "REJECTED_INVENTORY_FULL".equals(blocked.rejection),
            "full inventory pet preflight became grant");
        boolean invalid=false;
        try{
            bank.commitPreparedPetInventory(blocked);
        }catch(IllegalArgumentException expected){
            invalid=true;
        }
        require(invalid&&bank.inventoryCount(PET)==0,
            "rejected full inventory pet pickup mutated");
        BankState.PreparedPetInventoryMutation wrongSlot=
            bank.preparePetDropInventory(3,PET,null);
        require(!wrongSlot.accepted(),
            "wrong clicked pet unexpectedly allowed drop");
    }

    private static void competingPickup()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<32;trial++){
                BankState bank=new BankState();
                BankState.PreparedPetInventoryMutation a=
                    bank.preparePetPickupInventory(PET);
                BankState.PreparedPetInventoryMutation b=
                    bank.preparePetPickupInventory(PET);
                CountDownLatch ready=new CountDownLatch(2);
                CountDownLatch go=new CountDownLatch(1);
                Callable<Boolean> first=()->{
                    ready.countDown();go.await();
                    try{
                        bank.commitPreparedPetInventory(a);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Callable<Boolean> second=()->{
                    ready.countDown();go.await();
                    try{
                        bank.commitPreparedPetInventory(b);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Future<Boolean> x=pool.submit(first);
                Future<Boolean> y=pool.submit(second);
                require(ready.await(5,TimeUnit.SECONDS),
                    "competing prepared pickups not ready");
                go.countDown();
                int committed=(x.get(5,TimeUnit.SECONDS)?1:0)+
                    (y.get(5,TimeUnit.SECONDS)?1:0);
                require(committed==1&&bank.inventoryCount(PET)==1&&
                    bank.inventoryMutationRevision()==1,
                    "competing pet pickup duplicated reward trial="+trial);
            }
        }finally{
            pool.shutdownNow();
        }
    }

    public static void main(String[] args)throws Exception{
        require(PetDefinitionRepository.get(PET)!=null,
            "known pet item mapping missing");
        validDropAndPickup();
        foreignAndInventoryABA();
        bankViewABA();
        snapshotTamper();
        noInventoryCapacityAndPreflightRejection();
        competingPickup();
        System.out.println(
            "PREPARED_PET_INVENTORY_OWNERSHIP_PASS"+
            " knownPetDefinition=true validDropPickup=true"+
            " exactOwner=true foreignOwnerDenied=true"+
            " inventoryABARevoked=true bankViewABARevoked=true"+
            " expectedAndPostimageTamperDenied=true"+
            " foreignAndTamperedPacketPublicationDenied=true"+
            " fullInventoryFailClosed=true"+
            " concurrentPickupPairs=32 exactlyOneCommit=true"+
            " worldActorSettlementNotClaimed=true");
    }
}

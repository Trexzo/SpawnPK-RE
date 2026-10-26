package spk.local;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * R26: exact-source pet color transform is an existing deferred one-slot
 * inventory transaction, not a general permission to change item identities.
 * Verify owner, full preimage, generation, bank-view and sealed-postimage
 * boundaries around its publication and one-shot semantic commit.
 */
public final class PreparedInventoryTransformOwnershipRegression {
    private static final int WHITE=24016;
    private static final int OTHER=24017;
    private PreparedInventoryTransformOwnershipRegression(){}

    private static void check(boolean pass,String why){
        if(!pass)throw new AssertionError(
            "PREPARED_COLOR_TRANSFORM_OWNER "+why);
    }

    private static void seed(BankState bank){
        int[] ids=new int[bank.inventoryCapacity()];
        int[] amounts=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[3]=WHITE;amounts[3]=1;
        ids[10]=995;amounts[10]=100;
        bank.replaceInventorySemantic(ids,amounts);
    }

    private static ServerPacketWriter writer(ByteArrayOutputStream wire){
        return new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{2,3,5,7}));
    }

    private static BankState.PreparedInventoryTransform prepared(
        BankState bank
    ){
        return bank.prepareInventoryTransformOne(3,WHITE,OTHER);
    }

    private static void deniedCommit(
        BankState bank,BankState.PreparedInventoryTransform candidate,
        String why
    ){
        long revision=bank.inventoryMutationRevision();
        int original=bank.inventoryCount(WHITE);
        int replacement=bank.inventoryCount(OTHER);
        int coins=bank.inventoryCount(995);
        boolean refused=false;
        try{
            bank.commitPreparedInventoryTransform(candidate);
        }catch(IllegalStateException expected){
            refused=true;
        }
        check(refused&&bank.inventoryMutationRevision()==revision&&
            bank.inventoryCount(WHITE)==original&&
            bank.inventoryCount(OTHER)==replacement&&
            bank.inventoryCount(995)==coins,
            "stale or tampered transform committed "+why);
    }

    private static void deniedPublication(
        BankState bank,BankState.PreparedInventoryTransform candidate,
        String why
    )throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        boolean refused=false;
        try{
            bank.publishPreparedInventoryTransform(
                candidate,writer(bytes));
        }catch(IllegalStateException expected){
            refused=true;
        }
        check(refused&&bytes.size()==0,
            "unverified transform wrote packet "+why);
    }

    private static void allowedTransform()throws Exception{
        BankState bank=new BankState();
        seed(bank);
        long generation=bank.inventoryMutationRevision();
        BankState.PreparedInventoryTransform candidate=prepared(bank);
        check(candidate.accepted()&&candidate.owner==bank&&
            candidate.inventoryGeneration==generation&&
            candidate.postimage[3].itemId==OTHER&&
            candidate.postimage[10].qty==100,
            "prepared color transform incorrect");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter w=writer(bytes);
        String wire=bank.publishPreparedInventoryTransform(candidate,w);
        check(wire.startsWith("INVENTORY_TRANSFORM_OK")&&
            bytes.size()>0&&bank.inventoryCount(WHITE)==1,
            "valid color preflight publication failed");
        String semantic=bank.commitPreparedInventoryTransform(candidate);
        check(semantic.startsWith("INVENTORY_TRANSFORM_OK")&&
            bank.inventoryCount(WHITE)==0&&
            bank.inventoryCount(OTHER)==1&&
            bank.inventoryCount(995)==100&&
            bank.inventoryMutationRevision()==generation+1,
            "valid one-slot transform lost or duplicated items");
        deniedCommit(bank,candidate,"duplicate commit");
        deniedPublication(bank,candidate,"duplicate publication");

        BankState direct=new BankState();seed(direct);
        String directResult=direct.transformInventoryOne(
            3,WHITE,OTHER,writer(new ByteArrayOutputStream()));
        check(directResult.startsWith("INVENTORY_TRANSFORM_OK")&&
            direct.inventoryCount(WHITE)==0&&
            direct.inventoryCount(OTHER)==1,
            "direct transform compatibility changed");
    }

    private static void foreignAndABA()throws Exception{
        BankState original=new BankState(),foreign=new BankState();
        seed(original);seed(foreign);
        BankState.PreparedInventoryTransform t=prepared(original);
        deniedPublication(foreign,t,"equal-contents foreign owner");
        deniedCommit(foreign,t,"equal-contents foreign owner");
        check(original.commitPreparedInventoryTransform(t)
                .startsWith("INVENTORY_TRANSFORM_OK"),
            "foreign rejection invalidated valid original transform");

        BankState bank=new BankState();seed(bank);
        BankState.PreparedInventoryTransform stale=prepared(bank);
        long revision=bank.inventoryMutationRevision();
        seed(bank);
        check(bank.inventoryMutationRevision()==revision+1,
            "same-value inventory ABA generation not advanced");
        deniedPublication(bank,stale,"equal-state inventory ABA");
        deniedCommit(bank,stale,"equal-state inventory ABA");

        BankState bankView=new BankState();seed(bankView);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter w=writer(bytes);
        bankView.open(w);
        BankState.PreparedInventoryTransform view=prepared(bankView);
        long rev=bankView.bankViewRevision();
        bankView.close(w);bankView.open(w);
        check(bankView.isOpen()&&bankView.bankViewRevision()==rev+2,
            "bank-view ABA fixture changed");
        deniedPublication(bankView,view,"bank-view close/open ABA");
        deniedCommit(bankView,view,"bank-view close/open ABA");
    }

    private static void postimageTamper()throws Exception{
        BankState bank=new BankState();seed(bank);
        BankState.PreparedInventoryTransform quantity=prepared(bank);
        quantity.postimage[3].qty=1_000_000;
        deniedPublication(bank,quantity,"inflated replacement quantity");
        deniedCommit(bank,quantity,"inflated replacement quantity");

        BankState.PreparedInventoryTransform item=prepared(bank);
        item.postimage[3]=new BankState.Stack(28807,1);
        deniedPublication(bank,item,"substituted item");
        deniedCommit(bank,item,"substituted item");

        BankState.PreparedInventoryTransform side=prepared(bank);
        side.postimage[20]=new BankState.Stack(28824,9);
        deniedPublication(bank,side,"unauthorized side item");
        deniedCommit(bank,side,"unauthorized side item");

        BankState.PreparedInventoryTransform source=prepared(bank);
        source.expectedInventory[10].qty=0;
        deniedPublication(bank,source,"modified unrelated preimage");
        deniedCommit(bank,source,"modified unrelated preimage");

        BankState.PreparedInventoryTransform tab=prepared(bank);
        tab.postimage[3].tab=4;
        deniedPublication(bank,tab,"modified destination metadata");
        deniedCommit(bank,tab,"modified destination metadata");
    }

    private static void rejectedPreflight(){
        BankState bank=new BankState();
        BankState.PreparedInventoryTransform missing=prepared(bank);
        check(!missing.accepted()&&
            "REJECTED_INVENTORY_SLOT".equals(missing.rejection),
            "missing pet transformation not rejected");
        long rev=bank.inventoryMutationRevision();
        String result=bank.commitPreparedInventoryTransform(missing);
        check("REJECTED_INVENTORY_SLOT".equals(result)&&
            bank.inventoryMutationRevision()==rev,
            "rejected transform created an item");
    }

    private static void concurrentColorConversions()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            for(int attempt=0;attempt<32;attempt++){
                BankState bank=new BankState();seed(bank);
                BankState.PreparedInventoryTransform first=prepared(bank);
                BankState.PreparedInventoryTransform second=prepared(bank);
                CountDownLatch ready=new CountDownLatch(2);
                CountDownLatch go=new CountDownLatch(1);
                Callable<Boolean> one=()->{
                    ready.countDown();go.await();
                    try{
                        bank.commitPreparedInventoryTransform(first);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Callable<Boolean> two=()->{
                    ready.countDown();go.await();
                    try{
                        bank.commitPreparedInventoryTransform(second);
                        return true;
                    }catch(IllegalStateException expected){
                        return false;
                    }
                };
                Future<Boolean> a=pool.submit(one),b=pool.submit(two);
                check(ready.await(5,TimeUnit.SECONDS),
                    "concurrent transforms not ready");
                go.countDown();
                int committed=(a.get(5,TimeUnit.SECONDS)?1:0)+
                    (b.get(5,TimeUnit.SECONDS)?1:0);
                check(committed==1&&bank.inventoryCount(WHITE)==0&&
                    bank.inventoryCount(OTHER)==1&&
                    bank.inventoryMutationRevision()==2,
                    "concurrent transform duplicate or lost trial="+attempt);
            }
        }finally{pool.shutdownNow();}
    }

    public static void main(String[] args)throws Exception{
        check(ItemDefinitionRepository.get(WHITE)!=null&&
              ItemDefinitionRepository.get(OTHER)!=null,
              "certified Scooby color IDs missing");
        allowedTransform();
        foreignAndABA();
        postimageTamper();
        rejectedPreflight();
        concurrentColorConversions();
        System.out.println(
            "PREPARED_COLOR_TRANSFORM_OWNER_PASS"+
            " exactOwnerAndGeneration=true"+
            " fullInventoryPreimage=true bankViewABARevoked=true"+
            " forgedItemQuantitySideSlotTabDenied=true"+
            " prepublicationTamperDenied=true"+
            " directAndPreparedTransformParity=true"+
            " concurrentPairs=32 exactlyOnce=true"+
            " donorRecipesAdded=false durableProof=false");
    }
}

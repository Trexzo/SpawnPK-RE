package spk.local;
import java.util.*;

/** Source-only bank adapter test; no network, packet, XP or live recipes. */
public final class RecipeBankSemanticAdapterTest {
    static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static void reject(Runnable run){boolean rejected=false;try{run.run();}catch(IllegalStateException expected){rejected=true;}ok(rejected,"stale snapshot committed");}
    public static void main(String[] args)throws Exception{
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()],qty=new int[ids.length];
        Arrays.fill(ids,-1);ids[0]=100;qty[0]=5;ids[1]=200;qty[1]=7;
        bank.replaceInventorySemantic(ids,qty);
        Set<Integer> known=new HashSet<>(Arrays.asList(100,200,300));
        RecipeBankSemanticAdapter.Prepared ready=RecipeBankSemanticAdapter.prepare(
            bank,Collections.singletonMap(100,2),300,3,2,known);
        byte[] encoded=RecipeBankSemanticAdapter.encodePreparedInventory(bank,ready);
        ok(encoded.length>0,"precommit inventory packet encoding empty");
        ok(bank.inventoryAt(0).qty==5&&bank.inventoryAt(2)==null,
            "prepare mutated bank");
        RecipeBankSemanticAdapter.commit(bank,ready);
        ok(bank.inventoryAt(0).qty==1&&bank.inventoryAt(2).itemId==300&&
            bank.inventoryAt(2).qty==6,"commit postimage incorrect");
        RecipeBankSemanticAdapter.Prepared stale=RecipeBankSemanticAdapter.prepare(
            bank,Collections.singletonMap(100,1),300,1,1,known);
        int[] changedIds=new int[ids.length],changedQty=new int[qty.length];
        for(int i=0;i<ids.length;i++){
            BankState.Stack value=bank.inventoryAt(i);
            changedIds[i]=value==null?-1:value.itemId;
            changedQty[i]=value==null?0:value.qty;
        }
        changedQty[1]=6;
        bank.replaceInventorySemantic(changedIds,changedQty);
        reject(()->RecipeBankSemanticAdapter.commit(bank,stale));
        ok(bank.inventoryAt(0).qty==1&&bank.inventoryAt(1).qty==6,
            "stale rejection modified bank");
        RecipeBankSemanticAdapter.Prepared tampered=RecipeBankSemanticAdapter.prepare(
            bank,Collections.singletonMap(100,1),300,1,1,known);
        BankState otherOwner=new BankState();
        int[] otherIds=new int[bank.inventoryCapacity()];
        int[] otherQty=new int[bank.inventoryCapacity()];
        for(int i=0;i<otherIds.length;i++){
            BankState.Stack value=bank.inventoryAt(i);
            otherIds[i]=value==null?-1:value.itemId;
            otherQty[i]=value==null?0:value.qty;
        }
        otherOwner.replaceInventorySemantic(otherIds,otherQty);
        reject(()->RecipeBankSemanticAdapter.commit(otherOwner,tampered));
        ok(otherOwner.inventoryAt(0).qty==1,
            "foreign owner mutated by prepared result");
        tampered.proposed.quantities[0]=Integer.MAX_VALUE;
        boolean refusedEncode=false;
        try{
            RecipeBankSemanticAdapter.encodePreparedInventory(bank,tampered);
        }catch(IllegalStateException expected){refusedEncode=true;}
        ok(refusedEncode,"tampered postimage packet encoding accepted");
        reject(()->RecipeBankSemanticAdapter.commit(bank,tampered));
        ok(bank.inventoryAt(0).qty==1&&bank.inventoryAt(1).qty==6,
            "mutated prepared postimage changed bank");
        BankState directBank=new BankState();
        directBank.replaceInventorySemantic(ids,qty);
        RecipeBankSemanticAdapter.Prepared directReady=RecipeBankSemanticAdapter.prepare(
            directBank,Collections.singletonMap(100,2),300,3,2,known);
        java.io.ByteArrayOutputStream directBytes=new java.io.ByteArrayOutputStream();
        ServerPacketWriter directWriter=new ServerPacketWriter(
            directBytes,new IsaacCipher(new int[]{0,0,0,0}));
        boolean directDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                directBank,directReady,directWriter);
        }catch(java.io.IOException expected){
            directDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_DIRECT_WRITER_NONATOMIC_PUBLICATION_DENIED");
        }
        ok(directDenied&&directBytes.size()==0&&
            directBank.inventoryAt(0).qty==5&&directBank.inventoryAt(2)==null,
            "non-retractable direct publication mutated server or emitted bytes");
        BankState queuedBank=new BankState();
        queuedBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue queue=new OutboundPacketQueue(1024);
        ServerPacketWriter queuedWriter=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared accepted=RecipeBankSemanticAdapter.prepare(
            queuedBank,Collections.singletonMap(100,2),300,3,2,known);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            queuedBank,accepted,queuedWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "queue-backed recipe admission not committed");
        ok(queuedBank.inventoryAt(0).qty==1&&
            queuedBank.inventoryAt(2).qty==6,
            "queue admission did not apply semantic inventory");
        java.io.ByteArrayOutputStream acceptedWire=new java.io.ByteArrayOutputStream();
        ok(queue.drainTo(acceptedWire,2048)>0&&acceptedWire.size()>0,
            "accepted queue published no bytes");

        BankState crowdedBank=new BankState();
        crowdedBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue fullQueue=new OutboundPacketQueue(1024);
        fullQueue.offerBatch(new byte[1024]);
        ServerPacketWriter fullWriter=new ServerPacketWriter(
            fullQueue,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared refused=RecipeBankSemanticAdapter.prepare(
            crowdedBank,Collections.singletonMap(100,2),300,3,2,known);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            crowdedBank,refused,fullWriter)==
            ServerPacketWriter.RecoverablePacketResult.RETRACTED_RETRYABLE,
            "full queue recipe was not retractably rejected");
        ok(crowdedBank.inventoryAt(0).qty==5&&crowdedBank.inventoryAt(2)==null,
            "rejected queue mutated server inventory");
        ok(fullQueue.drainTo(new java.io.ByteArrayOutputStream(),2048)==1024,
            "rejected queue appended recipe bytes");
        BankState openBank=new BankState();
        openBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue openQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter openWriter=new ServerPacketWriter(
            openQueue,new IsaacCipher(new int[]{0,0,0,0}));
        openBank.open(openWriter);
        java.io.ByteArrayOutputStream openInitial=new java.io.ByteArrayOutputStream();
        openQueue.drainTo(openInitial,4096);
        RecipeBankSemanticAdapter.Prepared openReady=RecipeBankSemanticAdapter.prepare(
            openBank,Collections.singletonMap(100,2),300,3,2,known);
        reject(()->{
            try{
                RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                    openBank,openReady,openWriter);
            }catch(java.io.IOException e){throw new AssertionError(e);}
        });
        ok(openBank.inventoryAt(0).qty==5&&openBank.inventoryAt(2)==null,
            "open-bank rejection mutated inventory");
        ok(openQueue.drainTo(new java.io.ByteArrayOutputStream(),4096)==0,
            "open-bank rejection enqueued inventory packets");
        System.out.println("RECIPE_BANK_SEMANTIC_ADAPTER_PASS"+
            " preparedReadOnly=true validCommit=true staleDenied=true"+
            " tamperedResultDenied=true foreignOwnerDenied=true"+
            " precommitPacketEncoded=true tamperedEncodeDenied=true"+
            " directWriterDeniedWithoutMutation=true"+
            " queueCommitted=true queueRefusalRetracted=true"+
            " bankOpenMirrorDenied=true"+
            " liveRecipeRegistered=false");
    }
    private RecipeBankSemanticAdapterTest(){}
}

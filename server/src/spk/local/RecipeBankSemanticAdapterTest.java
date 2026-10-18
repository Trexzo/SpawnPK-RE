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
        boolean stalePreviewDenied=false;
        try{
            RecipeBankSemanticAdapter.encodePreparedInventory(bank,stale);
        }catch(IllegalStateException expected){
            stalePreviewDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_STALE_INVENTORY_POSTIMAGE");
        }
        ok(stalePreviewDenied,
            "stale preimage was allowed to produce a client preview");
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
        // A direct source-only semantic commit cannot reuse an already
        // consumed request, even if the preimage is later restored verbatim.
        BankState directReplayBank=new BankState();
        directReplayBank.replaceInventorySemantic(ids,qty);
        RecipeBankSemanticAdapter.Prepared directReplay=
            RecipeBankSemanticAdapter.prepare(
                directReplayBank,Collections.singletonMap(100,2),
                300,3,2,known);
        RecipeBankSemanticAdapter.commit(directReplayBank,directReplay);
        directReplayBank.replaceInventorySemantic(ids,qty);
        boolean directReplayDenied=false;
        try{
            RecipeBankSemanticAdapter.commit(directReplayBank,directReplay);
        }catch(IllegalStateException expected){
            directReplayDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_PREPARED_ALREADY_COMMITTED");
        }
        ok(directReplayDenied&&directReplayBank.inventoryAt(0).qty==5&&
            directReplayBank.inventoryAt(2)==null,
            "source-only direct commit replayed restored preimage");
        // Privately constructed Prepared objects are mandatory: unrelated
        // source modules must not mint arbitrary postimages as recipe grants.
        for(java.lang.reflect.Constructor<?> ctor:
            RecipeBankSemanticAdapter.Prepared.class.getDeclaredConstructors()){
            ok(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()),
                "untrusted module can directly construct recipe authority");
        }
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
        // A stale postimage is rejected before queue admission: the
        // live server inventory and output queue must remain untouched.
        BankState staleQueuedBank=new BankState();
        staleQueuedBank.replaceInventorySemantic(ids,qty);
        RecipeBankSemanticAdapter.Prepared staleQueueRequest=
            RecipeBankSemanticAdapter.prepare(
                staleQueuedBank,Collections.singletonMap(100,2),
                300,3,2,known);
        int[] freshIds=ids.clone(),freshQuantities=qty.clone();
        freshQuantities[1]=6;
        staleQueuedBank.replaceInventorySemantic(
            freshIds,freshQuantities);
        OutboundPacketQueue staleOutput=new OutboundPacketQueue(1024);
        ServerPacketWriter staleWriter=new ServerPacketWriter(
            staleOutput,new IsaacCipher(new int[]{0,0,0,0}));
        boolean staleQueueDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                staleQueuedBank,staleQueueRequest,staleWriter);
        }catch(IllegalStateException expected){
            staleQueueDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_STALE_INVENTORY_POSTIMAGE");
        }
        ok(staleQueueDenied&&
            staleOutput.drainTo(new java.io.ByteArrayOutputStream(),1024)==0&&
            staleQueuedBank.inventoryAt(0).qty==5&&
            staleQueuedBank.inventoryAt(1).qty==6&&
            staleQueuedBank.inventoryAt(2)==null,
            "stale queue request leaked packets or changed inventory");

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
        // A committed prepared request cannot be replayed even when its
        // resulting inventory happens to equal the original preimage.
        // Restore the *identical* original preimage: snapshot-based guards
        // alone are insufficient to prevent reusing a consumed request.
        queuedBank.replaceInventorySemantic(ids,qty);
        boolean duplicateDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                queuedBank,accepted,queuedWriter);
        }catch(IllegalStateException expected){
            duplicateDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_PREPARED_ALREADY_COMMITTED");
        }
        ok(duplicateDenied,"committed request replayed with restored preimage");
        ok(queuedBank.inventoryAt(0).qty==5&&queuedBank.inventoryAt(2)==null,
            "replay changed bank after restoring original items");
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
        // Rejected queue admission does not consume one-shot authority.
        fullQueue.drainTo(new java.io.ByteArrayOutputStream(),2048);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            crowdedBank,refused,fullWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "retry after queue drain did not commit");
        ok(crowdedBank.inventoryAt(0).qty==1&&
            crowdedBank.inventoryAt(2).qty==6,
            "retry did not apply inventory exactly once");
        boolean retryReplayDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                crowdedBank,refused,fullWriter);
        }catch(IllegalStateException expected){
            retryReplayDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_PREPARED_ALREADY_COMMITTED");
        }
        ok(retryReplayDenied,"successful retry could be replayed");

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
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                openBank,openReady,openWriter)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "bank-open queue recipe not admitted");
        ok(openBank.inventoryAt(0).qty==1&&
            openBank.inventoryAt(2).qty==6,
            "bank-open queue commit failed");
        java.io.ByteArrayOutputStream openWire=
            new java.io.ByteArrayOutputStream();
        ok(openQueue.drainTo(openWire,4096)>0&&
            openWire.size()>0,
            "bank-open mirror batch produced no bytes");

        // Fault-inject the recipe's underlying recoverable queue publisher
        // AFTER one of two bank-open views was framed but BEFORE admission.
        // This checks batch/cipher rollback; it is not a live recipe handler.
        BankState injectedBank=new BankState();
        injectedBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue injectedQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter opener=new ServerPacketWriter(
            injectedQueue,new IsaacCipher(new int[]{0,0,0,0}));
        injectedBank.open(opener);
        injectedQueue.drainTo(new java.io.ByteArrayOutputStream(),4096);
        ServerPacketWriter injectingWriter=new ServerPacketWriter(
            injectedQueue,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared injectedReady=
            RecipeBankSemanticAdapter.prepare(
                injectedBank,Collections.singletonMap(100,2),
                300,3,2,known);
        // Exercise the SAME recoverable publisher used by recipes. Inject
        // between the two prepared inventory views, without subclassing the
        // final writer or manufacturing gameplay registration.
        byte[][] interruptedViews=injectedBank.previewRecipeInventoryViews(
            injectedReady.proposed.ids,injectedReady.proposed.quantities);
        ok(interruptedViews.length==2,
            "fault fixture did not have two bank-open views");
        boolean injectedFailureObserved=false;
        try{
            injectingWriter.publishQueueOnlyRecoverablePacketIfIdle(
                ()->{
                    injectingWriter.varShort(53,interruptedViews[0]);
                    throw new java.io.IOException(
                        "INJECTED_RECIPE_SECOND_VIEW_FAILURE");
                });
        }catch(java.io.IOException expected){
            injectedFailureObserved=String.valueOf(expected.getMessage())
                .contains("INJECTED_RECIPE_SECOND_VIEW_FAILURE");
        }
        ok(injectedFailureObserved&&!injectingWriter.batchActive()&&
            injectedQueue.drainTo(
                new java.io.ByteArrayOutputStream(),4096)==0&&
            injectedBank.inventoryAt(0).qty==5&&
            injectedBank.inventoryAt(2)==null,
            "recoverable writer fault leaked bytes or mutated inventory");
        // The same prepared request remains retryable, and the writer's
        // aborted cipher checkpoint must not prevent a subsequent commit.
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            injectedBank,injectedReady,injectingWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "injected publish failure consumed recipe request");
        java.io.ByteArrayOutputStream retriedWire=
            new java.io.ByteArrayOutputStream();
        int retriedBytes=injectedQueue.drainTo(retriedWire,4096);
        ok(injectedBank.inventoryAt(0).qty==1&&
            injectedBank.inventoryAt(2).qty==6&&retriedBytes>0,
            "retry after injected second-view failure did not publish once");
        // Verify the first failed attempt did not consume outbound ISAAC
        // state: a fresh writer framing the SAME two postimage views must
        // produce byte-for-byte identical opcode 53 packets.
        byte[][] expectedViews=injectedBank.previewRecipeInventoryViews(
            injectedReady.proposed.ids,injectedReady.proposed.quantities);
        OutboundPacketQueue expectedQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter expectedWriter=new ServerPacketWriter(
            expectedQueue,new IsaacCipher(new int[]{0,0,0,0}));
        ok(expectedWriter.publishQueueOnlyRecoverablePacketIfIdle(
            ()->{for(byte[] view:expectedViews)
                expectedWriter.varShort(53,view);})==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "fresh reference queue could not admit recipe views");
        java.io.ByteArrayOutputStream expectedWire=
            new java.io.ByteArrayOutputStream();
        expectedQueue.drainTo(expectedWire,4096);
        ok(Arrays.equals(retriedWire.toByteArray(),
            expectedWire.toByteArray()),
            "failed recipe batch advanced ISAAC or changed retry packets");

        BankState openRefused=new BankState();
        openRefused.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue refusedQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter refusedWriter=new ServerPacketWriter(
            refusedQueue,new IsaacCipher(new int[]{0,0,0,0}));
        openRefused.open(refusedWriter);
        refusedQueue.drainTo(new java.io.ByteArrayOutputStream(),4096);
        refusedQueue.offerBatch(new byte[4096]);
        RecipeBankSemanticAdapter.Prepared denied=RecipeBankSemanticAdapter.prepare(
            openRefused,Collections.singletonMap(100,2),300,3,2,known);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            openRefused,denied,refusedWriter)==
            ServerPacketWriter.RecoverablePacketResult.RETRACTED_RETRYABLE,
            "bank-open full queue was not retracted");
        ok(openRefused.inventoryAt(0).qty==5&&
            openRefused.inventoryAt(2)==null,
            "bank-open full-queue mutated inventory");
        ok(refusedQueue.drainTo(new java.io.ByteArrayOutputStream(),8192)==4096,
            "bank-open retraction appended a partial mirror batch");
        System.out.println("RECIPE_BANK_SEMANTIC_ADAPTER_PASS"+
            " preparedReadOnly=true validCommit=true staleDenied=true"+
            " tamperedResultDenied=true foreignOwnerDenied=true"+
            " precommitPacketEncoded=true tamperedEncodeDenied=true"+
            " stalePreviewDenied=true staleQueueZeroPacket=true"+
            " directReplayDenied=true privatePreparedConstructor=true"+
            " directWriterDeniedWithoutMutation=true"+
            " queueCommitted=true queueRefusalRetracted=true"+
            " restoredPreimageReplayDenied=true retryAfterRefusalOnce=true"+
            " bankOpenMirrorsAtomic=true bankOpenBackpressureAtomic=true"+
            " secondViewFaultRetracted=true faultRetryOnce=true"+
            " retryCipherByteParity=true"+
            " liveRecipeRegistered=false");
    }
    private RecipeBankSemanticAdapterTest(){}
}

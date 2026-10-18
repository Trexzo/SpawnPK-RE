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
        // The standalone container preview must refuse malformed inventory
        // sentinels instead of silently turning them into empty slots.
        int[] malformedIds=ids.clone(),malformedQty=qty.clone();
        malformedIds[3]=-2;
        boolean invalidNegativeDenied=false;
        try{
            bank.previewRecipeInventoryPacket(
                malformedIds,malformedQty);
        }catch(IllegalArgumentException expected){
            invalidNegativeDenied=true;
        }
        ok(invalidNegativeDenied,
            "invalid negative preview item was silently normalized");
        malformedIds[3]=0xffff;
        boolean invalidWireIdDenied=false;
        try{
            bank.previewRecipeInventoryPacket(
                malformedIds,malformedQty);
        }catch(IllegalArgumentException expected){
            invalidWireIdDenied=true;
        }
        ok(invalidWireIdDenied,
            "non-encodable preview item ID accepted");
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

        // A staging writer is not an idle publication boundary. The
        // prepared request remains retryable only after that batch aborts.
        BankState busyBank=new BankState();
        busyBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue busyQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter busyWriter=new ServerPacketWriter(
            busyQueue,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared busyReady=
            RecipeBankSemanticAdapter.prepare(
                busyBank,Collections.singletonMap(100,2),300,3,2,known);
        busyWriter.beginBatch();
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            busyBank,busyReady,busyWriter)==
            ServerPacketWriter.RecoverablePacketResult.RETRACTED_RETRYABLE,
            "active packet batch unexpectedly admitted a recipe");
        ok(busyBank.inventoryAt(0).qty==5&&
            busyQueue.queuedBytes()==0,
            "busy writer mutated inventory or emitted bytes");
        busyWriter.abortBatch();
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            busyBank,busyReady,busyWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "busy-writer retraction consumed retry authority");
        ok(busyBank.inventoryAt(0).qty==1&&
            busyBank.inventoryAt(2).qty==6,
            "busy-writer retry failed to commit exactly once");

        // A terminal writer cannot admit a recipe, even with a valid
        // prepared postimage and otherwise empty output queue.
        BankState terminalBank=new BankState();
        terminalBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue terminalQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter terminalWriter=new ServerPacketWriter(
            terminalQueue,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared terminalReady=
            RecipeBankSemanticAdapter.prepare(
                terminalBank,Collections.singletonMap(100,2),300,3,2,known);
        terminalWriter.markTerminal();
        boolean terminalDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                terminalBank,terminalReady,terminalWriter);
        }catch(java.io.IOException expected){
            terminalDenied=String.valueOf(expected.getMessage()).contains(
                "terminal");
        }
        ok(terminalDenied&&terminalQueue.queuedBytes()==0&&
            terminalBank.inventoryAt(0).qty==5&&
            terminalBank.inventoryAt(2)==null,
            "terminal writer unexpectedly published a recipe");

        // External alteration of the mutable fixture-facing postimage must
        // also fail closed at queue admission, not just at direct commit.
        BankState queueTamperBank=new BankState();
        queueTamperBank.replaceInventorySemantic(ids,qty);
        OutboundPacketQueue queueTamperOutput=new OutboundPacketQueue(4096);
        ServerPacketWriter queueTamperWriter=new ServerPacketWriter(
            queueTamperOutput,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared queueTamper=
            RecipeBankSemanticAdapter.prepare(
                queueTamperBank,Collections.singletonMap(100,2),
                300,3,2,known);
        queueTamper.proposed.ids[2]=999;
        boolean tamperedQueueDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                queueTamperBank,queueTamper,queueTamperWriter);
        }catch(IllegalStateException expected){
            tamperedQueueDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_PREPARED_POSTIMAGE_CHANGED");
        }
        ok(tamperedQueueDenied&&queueTamperOutput.queuedBytes()==0&&
            queueTamperBank.inventoryAt(0).qty==5&&
            queueTamperBank.inventoryAt(2)==null,
            "tampered prepared request affected queue or inventory");

        // End-to-end source-only planner -> BankState -> recoverable queue
        // for an overlapping RETAINED tool, not just a pure arithmetic test.
        BankState retainedToolBank=new BankState();
        retainedToolBank.replaceInventorySemantic(ids,qty);
        RecipeBankSemanticAdapter.Prepared retainedToolReady=
            RecipeBankSemanticAdapter.prepareWithTool(
                retainedToolBank,Collections.singletonMap(100,2),
                300,3,2,known,100,false);
        OutboundPacketQueue retainedToolQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter retainedToolWriter=new ServerPacketWriter(
            retainedToolQueue,new IsaacCipher(new int[]{0,0,0,0}));
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            retainedToolBank,retainedToolReady,retainedToolWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "retained tool postimage was not admitted");
        ok(retainedToolBank.inventoryAt(0).itemId==100&&
            retainedToolBank.inventoryAt(0).qty==1&&
            retainedToolBank.inventoryAt(2).itemId==300&&
            retainedToolBank.inventoryAt(2).qty==6&&
            retainedToolQueue.queuedBytes()>0,
            "retained tool lost or output missing after semantic commit");
        BankState insufficientToolBank=new BankState();
        int[] insufficientIds=ids.clone(),insufficientQty=qty.clone();
        insufficientQty[0]=4;
        insufficientToolBank.replaceInventorySemantic(
            insufficientIds,insufficientQty);
        boolean retentionDenied=false;
        try{
            RecipeBankSemanticAdapter.prepareWithTool(
                insufficientToolBank,Collections.singletonMap(100,2),
                300,3,2,known,100,false);
        }catch(IllegalStateException expected){
            retentionDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_RETAINED_TOOL_REQUIRED");
        }
        ok(retentionDenied&&insufficientToolBank.inventoryAt(0).qty==4&&
            insufficientToolBank.inventoryAt(2)==null,
            "insufficient retained tool request was admitted or mutated");

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
        regressBankOverlayTransitions(ids,qty,known);
        regressBankOverlayAba(ids,qty,known);
        System.out.println("RECIPE_BANK_SEMANTIC_ADAPTER_PASS"+
            " preparedReadOnly=true validCommit=true staleDenied=true"+
            " tamperedResultDenied=true foreignOwnerDenied=true"+
            " precommitPacketEncoded=true tamperedEncodeDenied=true"+
            " malformedPacketSentinelDenied=true"+
            " stalePreviewDenied=true staleQueueZeroPacket=true"+
            " directReplayDenied=true privatePreparedConstructor=true"+
            " directWriterDeniedWithoutMutation=true"+
            " queueCommitted=true queueRefusalRetracted=true"+
            " busyWriterRejected=true terminalWriterDenied=true"+
            " tamperedQueueDenied=true"+
            " retainedToolQueued=true insufficientToolDenied=true"+
            " closedToOpenDenied=true openToClosedDenied=true"+
            " bankViewAbaDenied=true clientCloseAbaDenied=true"+
            " restoredPreimageReplayDenied=true retryAfterRefusalOnce=true"+
            " bankOpenMirrorsAtomic=true bankOpenBackpressureAtomic=true"+
            " secondViewFaultRetracted=true faultRetryOnce=true"+
            " retryCipherByteParity=true"+
            " liveRecipeRegistered=false");
    }
    private static void regressBankOverlayTransitions(
        int[] ids,int[] quantities,Set<Integer> known
    )throws Exception{
        // Closed -> open: an old one-view recipe request must not be
        // allowed to silently publish into a bank-open two-view layout.
        BankState closed=new BankState();
        closed.replaceInventorySemantic(ids,quantities);
        OutboundPacketQueue closedQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter closedWriter=new ServerPacketWriter(
            closedQueue,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared preparedClosed=
            RecipeBankSemanticAdapter.prepare(
                closed,Collections.singletonMap(100,2),
                300,3,2,known);
        closed.open(closedWriter);
        closedQueue.drainTo(new java.io.ByteArrayOutputStream(),4096);
        boolean denied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                closed,preparedClosed,closedWriter);
        }catch(IllegalStateException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_STALE_BANK_VIEW");
        }
        ok(denied&&closedQueue.queuedBytes()==0&&
            closed.inventoryAt(0).qty==5&&
            closed.inventoryAt(2)==null,
            "closed-prepared bank-open request emitted recipe or items");

        // Open -> closed: a stale two-view request cannot publish a
        // postimage after the bank overlay is no longer active.
        BankState open=new BankState();
        open.replaceInventorySemantic(ids,quantities);
        OutboundPacketQueue openQueue=new OutboundPacketQueue(4096);
        ServerPacketWriter openWriter=new ServerPacketWriter(
            openQueue,new IsaacCipher(new int[]{0,0,0,0}));
        open.open(openWriter);
        openQueue.drainTo(new java.io.ByteArrayOutputStream(),4096);
        RecipeBankSemanticAdapter.Prepared preparedOpen=
            RecipeBankSemanticAdapter.prepare(
                open,Collections.singletonMap(100,2),
                300,3,2,known);
        open.close(openWriter);
        openQueue.drainTo(new java.io.ByteArrayOutputStream(),4096);
        denied=false;
        try{
            RecipeBankSemanticAdapter.encodePreparedInventory(
                open,preparedOpen);
        }catch(IllegalStateException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_STALE_BANK_VIEW");
        }
        ok(denied,"open-prepared bank-closed preview accepted");
        denied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                open,preparedOpen,openWriter);
        }catch(IllegalStateException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_STALE_BANK_VIEW");
        }
        ok(denied&&openQueue.queuedBytes()==0&&
            open.inventoryAt(0).qty==5&&open.inventoryAt(2)==null,
            "open-prepared bank-closed request emitted recipe or items");

        // A fresh request made after the transition can still commit;
        // the denial fences only the stale prepared authority.
        RecipeBankSemanticAdapter.Prepared refreshed=
            RecipeBankSemanticAdapter.prepare(
                open,Collections.singletonMap(100,2),
                300,3,2,known);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            open,refreshed,openWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
            open.inventoryAt(0).qty==1&&
            open.inventoryAt(2).qty==6&&
            openQueue.queuedBytes()>0,
            "fresh post-transition recipe could not publish");
    }

    private static void regressBankOverlayAba(
        int[] ids,int[] quantities,Set<Integer> known
    )throws Exception{
        // Even identical final open/closed state is stale if the overlay
        // passed through intervening transitions. Simulates both browser-
        // originated client close and server-published close.
        for(int mode=0;mode<3;mode++){
            BankState bank=new BankState();
            bank.replaceInventorySemantic(ids,quantities);
            OutboundPacketQueue queue=new OutboundPacketQueue(8192);
            ServerPacketWriter writer=new ServerPacketWriter(
                queue,new IsaacCipher(new int[]{0,0,0,0}));
            // 0: open -> close -> open, 1: closed -> open -> close,
            // 2: open -> client-close -> open.
            boolean startsOpen=mode!=1;
            if(startsOpen)bank.open(writer);
            queue.drainTo(new java.io.ByteArrayOutputStream(),8192);
            RecipeBankSemanticAdapter.Prepared prepared=
                RecipeBankSemanticAdapter.prepare(
                    bank,Collections.singletonMap(100,2),
                    300,3,2,known);
            long before=bank.bankViewRevision();
            if(mode==2){
                ok(bank.clientClosed(),"client close did not close open bank");
            }else if(mode==0){
                bank.close(writer);
            }else{
                bank.open(writer);
            }
            if(mode==1)bank.close(writer);
            else bank.open(writer);
            queue.drainTo(new java.io.ByteArrayOutputStream(),8192);
            ok(bank.isOpen()==startsOpen &&
               bank.bankViewRevision()>before,
               "ABA setup did not retain final overlay state");
            boolean blocked=false;
            try{
                RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                    bank,prepared,writer);
            }catch(IllegalStateException expected){
                blocked=String.valueOf(expected.getMessage()).contains(
                    "RECIPE_STALE_BANK_VIEW");
            }
            ok(blocked&&queue.queuedBytes()==0&&
                bank.inventoryAt(0).qty==5&&bank.inventoryAt(2)==null,
                "bank view ABA published obsolete recipe mode="+mode);
            // A freshly prepared action after the same transition works.
            RecipeBankSemanticAdapter.Prepared fresh=
                RecipeBankSemanticAdapter.prepare(
                    bank,Collections.singletonMap(100,2),
                    300,3,2,known);
            ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                bank,fresh,writer)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED &&
                bank.inventoryAt(0).qty==1&&
                bank.inventoryAt(2).qty==6,
                "fresh action blocked after overlay ABA mode="+mode);
        }
    }

    private RecipeBankSemanticAdapterTest(){}
}

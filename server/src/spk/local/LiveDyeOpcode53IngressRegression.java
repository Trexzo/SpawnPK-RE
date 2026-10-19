package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Source-level client-wire-to-live-dye regression.
 *
 * The wire uses the pinned original v308 C2S opcode-53 byte transformations
 * and ISAAC masking. The ordinary decoder creates typed requests; only the
 * existing verified dye handler is invoked. This is not a real TCP login,
 * original-client GUI run, persistence acknowledgement, or donor recipe.
 */
public final class LiveDyeOpcode53IngressRegression {
    private static final int REGULAR=3241,DYE=28824,DYED=28807;
    private static final int INV=BankState.NORMAL_INVENTORY_CONTAINER;
    private static final int[] C2S_SEED={7,11,13,17};
    private static final int[] S2C_SEED={0,0,0,0};
    private LiveDyeOpcode53IngressRegression(){}

    private static void ok(boolean good,String reason){
        if(!good)throw new AssertionError("DYE_OPCODE53_INGRESS "+reason);
    }

    private static void u16(OutputStream out,int value)throws IOException{
        out.write((value>>>8)&255);
        out.write(value&255);
    }
    private static void le(OutputStream out,int value)throws IOException{
        out.write(value&255);
        out.write((value>>>8)&255);
    }
    private static void beA(OutputStream out,int value)throws IOException{
        out.write((value>>>8)&255);
        out.write((value+128)&255);
    }
    private static void leA(OutputStream out,int value)throws IOException{
        out.write((value+128)&255);
        out.write((value>>>8)&255);
    }
    private static void action(
        OutputStream out,IsaacCipher cipher,
        int selectedSlot,int targetSlot,
        int selectedId,int targetId,
        int selectedWidget,int targetWidget
    )throws IOException{
        out.write((53+cipher.nextInt())&255);
        u16(out,targetSlot);
        beA(out,selectedSlot);
        leA(out,targetId);
        u16(out,selectedWidget);
        le(out,selectedId);
        u16(out,targetWidget);
    }
    private static BankState bankWithDyes(int qty){
        BankState state=new BankState();
        int[] ids=new int[state.inventoryCapacity()];
        int[] amounts=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[3]=REGULAR;amounts[3]=1;
        ids[9]=DYE;amounts[9]=qty;
        state.replaceInventorySemantic(ids,amounts);
        return state;
    }
    private static ServerPacketWriter writer(OutboundPacketQueue q){
        return new ServerPacketWriter(q,new IsaacCipher(S2C_SEED.clone()));
    }
    private static byte[] independentExpectedWire(
        int dyeQuantity,boolean bankOpen
    )throws Exception{
        BankState untouched=bankWithDyes(dyeQuantity);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter packetWriter=writer(queue);
        if(bankOpen){
            untouched.open(packetWriter);
            ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0,
                "reference overlay init");
        }
        int[] ids=new int[untouched.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[3]=DYED;qty[3]=1;
        if(dyeQuantity>1){
            ids[9]=DYE;qty[9]=dyeQuantity-1;
        }
        packetWriter.beginBatch();
        boolean completed=false;
        try{
            packetWriter.varShort(53,
                BootstrapPackets.itemContainer53(INV,ids,qty));
            if(bankOpen){
                int[] bankIds=new int[untouched.bankCapacity()];
                int[] bankQty=new int[bankIds.length];
                Arrays.fill(bankIds,-1);
                for(int slot=0;slot<bankIds.length;slot++){
                    BankState.Stack stack=untouched.bankAt(slot);
                    if(stack!=null){
                        bankIds[slot]=stack.itemId;
                        bankQty[slot]=stack.qty;
                    }
                }
                packetWriter.varShort(53,
                    BootstrapPackets.itemContainer53(
                        BankState.BANK_CONTAINER,bankIds,bankQty));
                packetWriter.varShort(53,
                    BootstrapPackets.itemContainer53(
                        BankState.BANK_INVENTORY_CONTAINER,ids,qty));
            }
            packetWriter.endBatch();
            completed=true;
        }finally{
            if(!completed)packetWriter.abortBatch();
        }
        ByteArrayOutputStream golden=new ByteArrayOutputStream();
        ok(queue.drainTo(golden,8192)>0,"expected wire not queued");
        return golden.toByteArray();
    }

    private static void runOne(
        boolean reverse,boolean bankOpen,int dyeQuantity
    )throws Exception{
        BankState bank=bankWithDyes(dyeQuantity);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter outbound=writer(queue);
        if(bankOpen){
            bank.open(outbound);
            ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0,
                "live overlay init failed");
        }
        long mutation=bank.inventoryMutationRevision();
        long presentation=bank.bankViewRevision();
        ByteArrayOutputStream inbound=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(C2S_SEED.clone());
        int selectedSlot=reverse?9:3,targetSlot=reverse?3:9;
        int selectedId=reverse?DYE:REGULAR,targetId=reverse?REGULAR:DYE;
        // Five consecutive actual opcode-53 frames. The decoder does not
        // infer a recipe from their mere existence.
        action(inbound,cipher,0,1,100,200,INV,INV);
        action(inbound,cipher,selectedSlot,targetSlot,
            selectedId,targetId,INV+1,INV);
        action(inbound,cipher,selectedSlot,10,
            selectedId,targetId,INV,INV);
        action(inbound,cipher,selectedSlot,targetSlot,
            selectedId,targetId,INV,INV);
        action(inbound,cipher,selectedSlot,targetSlot,
            selectedId,targetId,INV,INV);
        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(inbound.toByteArray()),
            new IsaacCipher(C2S_SEED.clone()),
            "[dye-ingress] ");
        LocalItemOnItemHandler handler=new LocalItemOnItemHandler(bank);
        for(int i=0;i<5;i++){
            ok(probe.readNextKnownPacket()&&probe.isAligned(),
                "lost C2S alignment at frame="+i);
            ok(probe.typedRequestCount()==1,
                "missing one typed opcode53 frame="+i);
            ClientRequest request=probe.takeTypedRequest();
            ok(request instanceof ItemOnItemClientRequest,
                "opcode53 not typed frame="+i);
            ItemOnItemClientRequest typed=(ItemOnItemClientRequest)request;
            ClientRequestMetadata meta=typed.metadata();
            ok(meta.opcode==53&&
                meta.provenance==ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                "PINNED_CLIENT_ITEM_ON_ITEM_WRITER".equals(meta.source),
                "lost exact C2S provenance frame="+i);
            ItemOnItemAction decoded=typed.action();
            if(i>=1){
                ok(decoded.selectedSlot==selectedSlot&&
                    decoded.selectedItemId==selectedId&&
                    decoded.targetItemId==targetId,
                    "C2S selected/target identity changed frame="+i);
            }
            LocalItemOnItemHandler.Result result=
                handler.handle(decoded,outbound);
            ok(result!=null,"null live item handler result frame="+i);
            if(i==3){
                ok("DOPPELGANGER_APPLY_DYE".equals(result.saveReason)&&
                    result.logText.contains("INVENTORY_COMBINE_OK")&&
                    bank.inventoryMutationRevision()==mutation+1&&
                    bank.inventoryAt(3).itemId==DYED&&
                    (dyeQuantity==1?
                        bank.inventoryAt(9)==null:
                        bank.inventoryAt(9)!=null&&
                            bank.inventoryAt(9).itemId==DYE&&
                            bank.inventoryAt(9).qty==dyeQuantity-1),
                    "valid decoded dye did not grant exactly once");
            }else{
                ok(result.saveReason==null&&
                    !result.logText.contains("INVENTORY_COMBINE_OK"),
                    "unverified/spoofed/replayed C2S frame granted item="+i);
                ok(bank.inventoryMutationRevision()==
                    mutation+(i==4?1:0),
                    "denied frame mutated inventory i="+i);
            }
            ok(bank.bankViewRevision()==presentation&&
                queue.queuedPackets()==(i>=3?1:0),
                "unexpected bank revision or packet frame="+i);
        }
        ok(probe.typedRequestCount()==0&&probe.isAligned()&&
            !probe.readNextKnownPacket()&&probe.decodedCount()==5,
            "opcode53 stream not exhausted cleanly");
        ByteArrayOutputStream actual=new ByteArrayOutputStream();
        ok(queue.drainTo(actual,8192)>0,
            "live dye output not drainable");
        ok(Arrays.equals(
            actual.toByteArray(),
            independentExpectedWire(dyeQuantity,bankOpen)),
            "decoded action differed from independent S2C packet reference"+
            " reverse="+reverse+" bankOpen="+bankOpen+
            " dyes="+dyeQuantity);
        ok(bank.inventoryAt(3).itemId==DYED&&
            (dyeQuantity==1?
                bank.inventoryAt(9)==null:
                bank.inventoryAt(9)!=null&&
                    bank.inventoryAt(9).qty==dyeQuantity-1)&&
            bank.inventoryMutationRevision()==mutation+1&&
            queue.queuedBytes()==0,
            "final decoded dye inventory mismatch");
    }

    /**
     * A REAL decoded opcode-53 request must remain retryable if the complete
     * outbound dye postimage cannot fit in the session queue. The rejected
     * request and accepted retry share both original inbound ISAAC and exact
     * outbound writer; the subsequent replay must not double-grant.
     */
    private static void decodedBackpressureRecovery(
        boolean reverse,boolean bankOpen,int dyeQuantity
    )throws Exception{
        BankState bank=bankWithDyes(dyeQuantity);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter outbound=writer(queue);
        if(bankOpen){
            bank.open(outbound);
            ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0,
                "queue retry setup did not publish bank overlay");
        }
        long original=bank.inventoryMutationRevision();
        long oldView=bank.bankViewRevision();
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        IsaacCipher inCipher=new IsaacCipher(C2S_SEED.clone());
        int selectedSlot=reverse?9:3,targetSlot=reverse?3:9;
        int selectedId=reverse?DYE:REGULAR,targetId=reverse?REGULAR:DYE;
        for(int i=0;i<3;i++)
            action(encoded,inCipher,selectedSlot,targetSlot,
                selectedId,targetId,INV,INV);
        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(encoded.toByteArray()),
            new IsaacCipher(C2S_SEED.clone()),
            "[dye-queue-ingress] ");
        LocalItemOnItemHandler handler=new LocalItemOnItemHandler(bank);
        queue.offerBatch(new byte[8192]);
        ok(queue.queuedBytes()==8192&&queue.queuedPackets()==1,
            "queue retry fixture failed to saturate admission");
        for(int i=0;i<3;i++){
            ok(probe.readNextKnownPacket()&&probe.isAligned()&&
                probe.typedRequestCount()==1,
                "queue retry C2S framing failed index="+i);
            ClientRequest request=probe.takeTypedRequest();
            ok(request instanceof ItemOnItemClientRequest,
                "queue retry lost typed opcode53 index="+i);
            ItemOnItemClientRequest typed=(ItemOnItemClientRequest)request;
            ok(typed.metadata().opcode==53&&
                typed.metadata().provenance==
                    ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "queue retry lost client provenance index="+i);
            ItemOnItemAction decoded=typed.action();
            ok(decoded.selectedSlot==selectedSlot&&
                decoded.targetSlot==targetSlot&&
                decoded.selectedItemId==selectedId&&
                decoded.targetItemId==targetId,
                "queue retry swapped decoder fields index="+i);
            LocalItemOnItemHandler.Result result=
                handler.handle(decoded,outbound);
            if(i==0){
                ok(result!=null&&result.saveReason==null&&
                    result.logText.contains(
                        "REJECTED_COMBINE_QUEUE_RETRYABLE")&&
                    queue.queuedBytes()==8192&&
                    queue.queuedPackets()==1&&
                    bank.inventoryMutationRevision()==original&&
                    bank.bankViewRevision()==oldView&&
                    bank.inventoryAt(3).itemId==REGULAR&&
                    bank.inventoryAt(9).itemId==DYE&&
                    bank.inventoryAt(9).qty==dyeQuantity,
                    "failed decoded admission leaked mutation or save");
                ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0&&
                    queue.queuedBytes()==0&&
                    queue.queuedPackets()==0,
                    "queue backpressure not cleared before valid retry");
            }else if(i==1){
                ok(result!=null&&
                    "DOPPELGANGER_APPLY_DYE".equals(result.saveReason)&&
                    bank.inventoryMutationRevision()==original+1&&
                    queue.queuedPackets()==1&&
                    bank.inventoryAt(3).itemId==DYED&&
                    (dyeQuantity==1?
                        bank.inventoryAt(9)==null:
                        bank.inventoryAt(9)!=null&&
                            bank.inventoryAt(9).itemId==DYE&&
                            bank.inventoryAt(9).qty==dyeQuantity-1),
                    "decoded retry failed to grant once");
            }else{
                ok(result!=null&&result.saveReason==null&&
                    (result.logText.contains("REJECTED_COMBINE_STALE_ITEM")||
                     result.logText.contains("REJECTED_COMBINE_ITEM_MISMATCH"))&&
                    bank.inventoryMutationRevision()==original+1&&
                    queue.queuedPackets()==1&&
                    bank.inventoryAt(3).itemId==DYED,
                    "decoded replay after retry double-granted");
            }
            ok(bank.bankViewRevision()==oldView,
                "decoded retry unexpectedly changed bank presentation");
        }
        ok(probe.typedRequestCount()==0&&
            probe.decodedCount()==3&&
            probe.isAligned()&&!probe.readNextKnownPacket(),
            "decoded retry stream did not terminate cleanly");
        ByteArrayOutputStream actual=new ByteArrayOutputStream();
        ok(queue.drainTo(actual,8192)>0&&
            Arrays.equals(
                actual.toByteArray(),
                independentExpectedWire(dyeQuantity,bankOpen))&&
            queue.queuedPackets()==0,
            "decoded retry poisoned outbound ISAAC or bank mirrors");
    }

    public static void main(String[] args)throws Exception{
        int fixtures=0;
        for(boolean reverse:new boolean[]{false,true})
            for(boolean bankOpen:new boolean[]{false,true})
                for(int qty:new int[]{1,2,255,256,257}){
                    runOne(reverse,bankOpen,qty);
                    fixtures++;
                }
        ok(fixtures==20,"expected 20 C2S fixtures");
        int retryFixtures=0;
        for(boolean reverse:new boolean[]{false,true})
            for(boolean bankOpen:new boolean[]{false,true})
                for(int qty:new int[]{1,2,255,256,257}){
                    decodedBackpressureRecovery(reverse,bankOpen,qty);
                    retryFixtures++;
                }
        ok(retryFixtures==20,"expected 20 decoded retry fixtures");
        System.out.println(
            "LIVE_DYE_OPCODE53_INGRESS_PASS"+
            " decoderToExistingHandler=true c2sFrames=100"+
            " fixtureVariants=20 selectionDirections=2"+
            " bankViews=2 quantityBoundary=1_2_255_256_257"+
            " malformedDenied=60 duplicateReplayDenied=20"+
            " validGrants=20 byteExactS2C=true"+
            " retryEncodedFrames=60 retryFixtures=20"+
            " saturatedQueueNoGrant=20"+
            " recoveredRetries=20 staleReplayDenied=20"+
            " retryIsaacParityBankOpenClosed=true"+
            " donorRecipesEnabled=false"
        );
    }
}

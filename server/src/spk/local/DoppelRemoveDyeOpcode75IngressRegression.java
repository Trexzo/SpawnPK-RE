package spk.local;

import java.io.*;
import java.util.*;

/**
 * Pinned v308 item option-75 (widget LE-A, slot LE, item BE-A) through the
 * real C2S ISAAC decoder and the existing pet-inventory action handler.
 * This source-only ingress test exercises the live remove-dye item grant,
 * but is NOT an actual original-client socket, ACK or durable account save.
 */
public final class DoppelRemoveDyeOpcode75IngressRegression {
    private static final int DYED=28807,REGULAR=3241,DYE=28824;
    private static final int INVENTORY=BankState.NORMAL_INVENTORY_CONTAINER;
    private static final int[] C2S={7,11,13,17},S2C={0,0,0,0};
    private DoppelRemoveDyeOpcode75IngressRegression(){}

    private static void ok(boolean accepted,String reason){
        if(!accepted)throw new AssertionError(
            "DOPPEL_REMOVE_OPCODE75_INGRESS "+reason);
    }

    private static BankState fixture(int petSlot){
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[petSlot]=DYED;qty[petSlot]=1;
        bank.replaceInventorySemantic(ids,qty);
        return bank;
    }

    private static LocalPetInventoryDialogHandler handler(BankState bank){
        WorldPlayer player=new WorldPlayer();
        return new LocalPetInventoryDialogHandler(
            bank,player.miniPets(),player.petState(),
            new NpcRegistry(new DevAuthorityWorkbench()),
            player.movement(),new PetAccessoryState());
    }

    private static ServerPacketWriter writer(OutboundPacketQueue q){
        return new ServerPacketWriter(q,new IsaacCipher(S2C.clone()));
    }

    private static void writeOption75(
        OutputStream out,IsaacCipher cipher,int widget,int slot,int item
    )throws IOException{
        out.write((75+cipher.nextInt())&255);
        // Client fv.p(widgetId) = little-endian short-A.
        out.write((widget+128)&255);
        out.write((widget>>>8)&255);
        // Client fv.n(slot) = ordinary little-endian short.
        out.write(slot&255);
        out.write((slot>>>8)&255);
        // Client fv.o(itemId) = big-endian short-A.
        out.write((item>>>8)&255);
        out.write((item+128)&255);
    }

    private static ItemContainerAction readOption75(ClientPacketProbe probe)
        throws Exception{
        ok(probe.readNextKnownPacket()&&probe.isAligned()&&
            probe.typedRequestCount()==1,
            "C2S opcode75 did not decode exactly once");
        ClientRequest request=probe.takeTypedRequest();
        ok(request instanceof ItemContainerActionClientRequest,
            "decoder did not preserve item option typed request");
        ItemContainerActionClientRequest typed=
            (ItemContainerActionClientRequest)request;
        ClientRequestMetadata metadata=typed.metadata();
        ok(metadata.opcode==75&&
            metadata.provenance==
                ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
            "FIXED6_WIDGET_LE_A_SLOT_LE_ITEM_BE_A".equals(
                metadata.schema)&&
            "PINNED_CLIENT_MENU_ACTION_493_WRITER".equals(metadata.source),
            "pinned opcode75 provenance or transform schema lost");
        ItemContainerAction action=typed.action();
        ok(action.opcode==75&&"ITEM_OPTION_4".equals(action.semantic),
            "decoded remove-dye option semantic changed");
        return action;
    }

    private static byte[] independentlyFramedPostimage(
        boolean open,int petSlot
    )throws Exception{
        BankState baseline=fixture(petSlot);
        OutboundPacketQueue q=new OutboundPacketQueue(8192);
        ServerPacketWriter w=writer(q);
        if(open){
            baseline.open(w);
            ok(q.drainTo(new ByteArrayOutputStream(),8192)>0,
                "independent bank overlay did not initialize");
        }
        int extraSlot=petSlot==0?1:0;
        int[] ids=new int[baseline.inventoryCapacity()];
        int[] quantities=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[petSlot]=REGULAR;quantities[petSlot]=1;
        ids[extraSlot]=DYE;quantities[extraSlot]=1;
        w.beginBatch();
        boolean done=false;
        try{
            w.varShort(53,BootstrapPackets.itemContainer53(
                INVENTORY,ids,quantities));
            if(open){
                int[] bankIds=new int[baseline.bankCapacity()];
                int[] bankQuantities=new int[bankIds.length];
                Arrays.fill(bankIds,-1);
                for(int slot=0;slot<bankIds.length;slot++){
                    BankState.Stack value=baseline.bankAt(slot);
                    if(value!=null){
                        bankIds[slot]=value.itemId;
                        bankQuantities[slot]=value.qty;
                    }
                }
                w.varShort(53,BootstrapPackets.itemContainer53(
                    BankState.BANK_CONTAINER,bankIds,bankQuantities));
                w.varShort(53,BootstrapPackets.itemContainer53(
                    BankState.BANK_INVENTORY_CONTAINER,ids,quantities));
            }
            w.endBatch();
            done=true;
        }finally{
            if(!done)w.abortBatch();
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ok(q.drainTo(bytes,8192)>0,"independent S2C image missing");
        return bytes.toByteArray();
    }

    private static void checkFinalState(
        BankState bank,int petSlot,long prior
    ){
        int extraSlot=petSlot==0?1:0;
        ok(bank.inventoryMutationRevision()==prior+1&&
            bank.inventoryAt(petSlot)!=null&&
            bank.inventoryAt(petSlot).itemId==REGULAR&&
            bank.inventoryAt(petSlot).qty==1&&
            bank.inventoryAt(extraSlot)!=null&&
            bank.inventoryAt(extraSlot).itemId==DYE&&
            bank.inventoryAt(extraSlot).qty==1&&
            bank.inventoryCount(DYED)==0&&
            bank.inventoryCount(REGULAR)==1&&
            bank.inventoryCount(DYE)==1,
            "accepted opcode75 did not conserve one pet and returned dye");
    }

    private static void ingress(boolean bankOpen,int slot,boolean fullQueue)
        throws Exception{
        BankState bank=fixture(slot);
        LocalPetInventoryDialogHandler handler=handler(bank);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        if(bankOpen){
            bank.open(writer);
            ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0,
                "bank-open opcode75 fixture failed to initialize");
        }
        long before=bank.inventoryMutationRevision();
        long view=bank.bankViewRevision();
        ByteArrayOutputStream input=new ByteArrayOutputStream();
        IsaacCipher encoder=new IsaacCipher(C2S.clone());
        if(fullQueue){
            for(int i=0;i<3;i++)
                writeOption75(input,encoder,INVENTORY,slot,DYED);
            queue.offerBatch(new byte[8192]);
        }else{
            writeOption75(input,encoder,BankState.BANK_INVENTORY_CONTAINER,
                slot,DYED);
            writeOption75(input,encoder,INVENTORY,slot,REGULAR);
            writeOption75(input,encoder,INVENTORY,(slot+1)%28,DYED);
            writeOption75(input,encoder,INVENTORY,slot,DYED);
            writeOption75(input,encoder,INVENTORY,slot,DYED);
        }
        ClientPacketProbe decoder=new ClientPacketProbe(
            new ByteArrayInputStream(input.toByteArray()),
            new IsaacCipher(C2S.clone()),"[doppel-remove-ingress] ");
        int total=fullQueue?3:5;
        for(int i=0;i<total;i++){
            ItemContainerAction action=readOption75(decoder);
            LocalPetInventoryDialogHandler.Result result=
                handler.handleItemAction(action,writer);
            boolean succeeds=fullQueue?i==1:i==3;
            if(succeeds){
                ok(result!=null&&
                    "DOPPELGANGER_REMOVE_DYE".equals(result.saveReason)&&
                    result.logText.contains("INVENTORY_SPLIT_OK")&&
                    result.keyAction==
                        LocalPetInventoryDialogHandler.KeyAction.NONE&&
                    queue.queuedPackets()==1,
                    "successful decoded remove-dye lacked save signal");
                checkFinalState(bank,slot,before);
            }else{
                ok(result==null||result.saveReason==null,
                    "refused opcode75 generated an account save intent");
                if(fullQueue&&i==0){
                    ok(result!=null&&
                        result.logText.contains(
                            "REJECTED_SPLIT_QUEUE_RETRYABLE")&&
                        queue.queuedPackets()==1&&
                        queue.queuedBytes()==8192,
                        "queue-full opcode75 leaked a grant");
                    ok(queue.drainTo(
                            new ByteArrayOutputStream(),8192)>0&&
                        queue.queuedPackets()==0,
                        "outbound backpressure could not be cleared");
                }
                if(i<=(fullQueue?0:2)){
                    ok(bank.inventoryMutationRevision()==before&&
                        bank.inventoryAt(slot).itemId==DYED&&
                        bank.inventoryCount(DYE)==0,
                        "refused opcode75 changed inventory");
                }else{
                    checkFinalState(bank,slot,before);
                    ok(result!=null&&
                        result.logText.contains(
                            "REJECTED_INVENTORY_ITEM_MISMATCH"),
                        "duplicate opcode75 unexpectedly received dye");
                }
            }
            ok(bank.bankViewRevision()==view&&
                queue.queuedPackets()==(i>=(fullQueue?1:3)?1:0),
                "opcode75 changed presentation or enqueued extra batch");
        }
        ok(decoder.typedRequestCount()==0&&decoder.isAligned()&&
            decoder.decodedCount()==total&&
            !decoder.readNextKnownPacket(),
            "opcode75 decoded stream not consumed cleanly");
        ByteArrayOutputStream actual=new ByteArrayOutputStream();
        ok(queue.drainTo(actual,8192)>0&&
            Arrays.equals(
                actual.toByteArray(),
                independentlyFramedPostimage(bankOpen,slot))&&
            queue.queuedPackets()==0,
            "opcode75 byte parity/ISAAC changed by rejected requests");
    }

    private static void fullInventoryDenial(boolean open)throws Exception{
        ok(!BankState.isStackable(DYE),
            "dye stackability changed; full-inventory model invalid");
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,995);
        Arrays.fill(qty,1);
        ids[3]=DYED;
        bank.replaceInventorySemantic(ids,qty);
        LocalPetInventoryDialogHandler h=handler(bank);
        OutboundPacketQueue q=new OutboundPacketQueue(8192);
        ServerPacketWriter w=writer(q);
        if(open){
            bank.open(w);
            ok(q.drainTo(new ByteArrayOutputStream(),8192)>0,
                "full-inventory bank open failed");
        }
        long before=bank.inventoryMutationRevision();
        LocalPetInventoryDialogHandler.Result refused=h.handleItemAction(
            new ItemContainerAction(75,INVENTORY,3,DYED,0,"ITEM_OPTION_4"),
            w);
        ok(refused!=null&&refused.saveReason==null&&
            refused.logText.contains("REJECTED_INVENTORY_FULL_FOR_SPLIT")&&
            bank.inventoryMutationRevision()==before&&
            bank.inventoryCount(DYED)==1&&
            bank.inventoryCount(DYE)==0&&q.queuedPackets()==0,
            "no-space remove-dye granted returned item");
        ids[5]=-1;qty[5]=0;
        bank.replaceInventorySemantic(ids,qty);
        long unblocked=bank.inventoryMutationRevision();
        LocalPetInventoryDialogHandler.Result granted=h.handleItemAction(
            new ItemContainerAction(75,INVENTORY,3,DYED,0,"ITEM_OPTION_4"),
            w);
        ok(granted!=null&&
            "DOPPELGANGER_REMOVE_DYE".equals(granted.saveReason)&&
            bank.inventoryMutationRevision()==unblocked+1&&
            bank.inventoryAt(5)!=null&&bank.inventoryAt(5).itemId==DYE&&
            bank.inventoryAt(3).itemId==REGULAR&&
            q.queuedPackets()==1,
            "restored free slot did not permit exactly one removal");
    }

    public static void main(String[] args)throws Exception{
        int direct=0,retry=0;
        for(boolean bankOpen:new boolean[]{false,true}){
            for(int slot:new int[]{0,3,17,27}){
                ingress(bankOpen,slot,false);
                ingress(bankOpen,slot,true);
                direct++;retry++;
            }
            fullInventoryDenial(bankOpen);
        }
        ok(direct==8&&retry==8,"fixture coverage incomplete");
        System.out.println("DOPPEL_REMOVE_OPCODE75_INGRESS_PASS"+
            " pinnedDecoder=true encodedC2SFrames=64"+
            " normalFixtures=8 fullQueueRetryFixtures=8"+
            " simultaneousBankViews=2 petSlots=4"+
            " noSpaceNoGrantTrials=2"+
            " exactOutboundContainer53=true"+
            " saveIntentOnlyAfterQueueAdmission=true"+
            " staleOpcode75ReplayDenied=true"+
            " donorRecipesRegistered=false");
    }
}

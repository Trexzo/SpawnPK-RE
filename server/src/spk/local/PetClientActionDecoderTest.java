package spk.local;

import java.io.*;

/** Exact C2S pet lifecycle action decoding: opcode87 Drop + opcode155 NPC first option. */
public final class PetClientActionDecoderTest {
    public static void main(String[] args)throws Exception{
        int[] seed={11,22,33,44};
        ByteArrayOutputStream wire=new ByteArrayOutputStream(); IsaacCipher enc=new IsaacCipher(seed.clone());
        // 20776 / widget3214 / slot4 using exact o,d,o writers.
        wire.write((87+enc.nextInt())&255); putBEA(wire,20776); putBE(wire,3214); putBEA(wire,4);
        wire.write((155+enc.nextInt())&255); putLE(wire,4);
        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()),new IsaacCipher(seed.clone()),"[pet-decoder] ");
        if(!p.readNextKnownPacket())throw new AssertionError();
        ClientRequest request=p.takeTypedRequest();
        if(!(request instanceof DropItemClientRequest))throw new AssertionError(String.valueOf(request));
        DropItemClientRequest typed=(DropItemClientRequest)request;
        DropItemAction d=typed.action();
        if(d==null||d.itemId!=20776||d.widgetId!=3214||d.slot!=4)throw new AssertionError(String.valueOf(d));
        ClientRequestMetadata metadata=typed.metadata();
        if(metadata.opcode!=87||
           !"FIXED6_ITEM_BE_A_WIDGET_BE_SLOT_BE_A".equals(metadata.schema)||
           !"PINNED_CLIENT_INVENTORY_DROP_WRITER".equals(metadata.source)||
           metadata.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new AssertionError(String.valueOf(metadata));
        if(!p.readNextKnownPacket())throw new AssertionError();
        ClientRequest npcRequest=p.takeTypedRequest();
        if(!(npcRequest instanceof NpcActionClientRequest))throw new AssertionError(String.valueOf(npcRequest));
        NpcActionClientRequest typedNpc=(NpcActionClientRequest)npcRequest;
        NpcAction n=typedNpc.action();
        if(n==null||n.opcode!=155||n.sceneIndex!=4)throw new AssertionError(String.valueOf(n));
        ClientRequestMetadata npcMetadata=typedNpc.metadata();
        if(npcMetadata.opcode!=155||
           !"FIXED2_NPC_SCENE_INDEX_LE".equals(npcMetadata.schema)||
           !"PINNED_CLIENT_NPC_OPTION_1_WRITER".equals(npcMetadata.source)||
           npcMetadata.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new AssertionError(String.valueOf(npcMetadata));
        if(!p.isAligned())throw new AssertionError("decoder unaligned");
        System.out.println("V53_PET_CLIENT_ACTION_DECODER_PASS opcode87=typed:20776/3214/4 opcode155=typed:scene4 aligned=true metadata=true");
    }
    private static void putBE(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write(v);}
    private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void putLE(OutputStream o,int v)throws IOException{o.write(v);o.write(v>>>8);}
}

package spk.local;

import java.io.*;

public final class EngineR1ClientDecodeTest {
    private static byte[] packet(int opcode,byte[] body,int[] seed)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();IsaacCipher c=new IsaacCipher(seed.clone());out.write((opcode+c.nextInt())&255);out.write(body);return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        int[] seed={11,22,33,44};
        // NPC option3 C2S17 = LE-A scene index.
        int scene=204;byte[] npc={(byte)((scene+128)&255),(byte)(scene>>>8)};
        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(packet(17,npc,seed)),new IsaacCipher(seed.clone()),"[decode] ");
        if(!p.readNextKnownPacket())throw new AssertionError();
        ClientRequest npcRequest=p.takeTypedRequest();
        if(!(npcRequest instanceof NpcActionClientRequest))throw new AssertionError("npc17 request="+npcRequest);
        NpcActionClientRequest typedNpc=(NpcActionClientRequest)npcRequest;
        NpcAction na=typedNpc.action();
        if(na==null||na.sceneIndex!=scene||na.opcode!=17)throw new AssertionError("npc17");
        ClientRequestMetadata npcMetadata=typedNpc.metadata();
        if(npcMetadata.opcode!=17||
           !"FIXED2_NPC_SCENE_INDEX_LE_A".equals(npcMetadata.schema)||
           !"PINNED_CLIENT_NPC_OPTION_3_WRITER".equals(npcMetadata.source)||
           npcMetadata.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new AssertionError("npc17 metadata="+npcMetadata);

        // Ground option3 C2S236 = worldY LE, item BE, worldX LE.
        int x=3088,y=3495,item=995;byte[] g={(byte)y,(byte)(y>>>8),(byte)(item>>>8),(byte)item,(byte)x,(byte)(x>>>8)};
        p=new ClientPacketProbe(new ByteArrayInputStream(packet(236,g,seed)),new IsaacCipher(seed.clone()),"[decode] ");p.readNextKnownPacket();
        ClientRequest groundRequest=p.takeTypedRequest();
        if(!(groundRequest instanceof GroundItemClientRequest))throw new AssertionError("ground236 request="+groundRequest);
        GroundItemClientRequest typedGround=(GroundItemClientRequest)groundRequest;
        GroundItemInteraction gi=typedGround.interaction();
        if(gi.option!=3||gi.itemId!=item||gi.worldX!=x||gi.worldY!=y)throw new AssertionError("ground236 "+gi);
        ClientRequestMetadata groundMetadata=typedGround.metadata();
        if(groundMetadata.opcode!=236||
           !"FIXED6_WORLD_Y_LE_ITEM_BE_WORLD_X_LE".equals(groundMetadata.schema)||
           !"PINNED_CLIENT_GROUND_ITEM_OPTION_3_WRITER".equals(groundMetadata.source)||
           groundMetadata.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new AssertionError("ground236 metadata="+groundMetadata);

        // Inventory option1 C2S122 = widget LE-A, slot BE-A, item LE.
        int widget=3214,slot=7,mini=23629;byte[] i={(byte)((widget+128)&255),(byte)(widget>>>8),(byte)(slot>>>8),(byte)((slot+128)&255),(byte)mini,(byte)(mini>>>8)};
        p=new ClientPacketProbe(new ByteArrayInputStream(packet(122,i,seed)),new IsaacCipher(seed.clone()),"[decode] ");p.readNextKnownPacket();
        ClientRequest itemRequest=p.takeTypedRequest();
        if(!(itemRequest instanceof ItemContainerActionClientRequest))throw new AssertionError("item122 request="+itemRequest);
        ItemContainerAction ia=((ItemContainerActionClientRequest)itemRequest).action();
        if(ia.widgetId!=widget||ia.slot!=slot||ia.itemId!=mini)throw new AssertionError("item122 "+ia);
        ClientRequestMetadata itemMetadata=itemRequest.metadata();
        if(itemMetadata.opcode!=122||
           !"FIXED6_WIDGET_LE_A_SLOT_BE_A_ITEM_LE".equals(itemMetadata.schema)||
           !"PINNED_CLIENT_INVENTORY_OPTION_1_WRITER".equals(itemMetadata.source)||
           itemMetadata.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new AssertionError("item122 metadata="+itemMetadata);
        System.out.println("V511_CLIENT_DECODE_PASS npc17_option3=typed ground236_typed=true item122_typed=true exactTransforms=true metadata=true");
    }
}

package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class ClientPacket57ItemOnNpcTest {
    public static void main(String[] args)throws Exception{
        int item=20542;
        int target=4;
        int slot=7;
        int widget=3214;

        byte[] body=new byte[8];
        putBeA(body,0,item);
        putBeA(body,2,target);
        putLe(body,4,slot);
        putBeA(body,6,widget);

        ItemOnNpcAction decoded=
            ClientPacketProbe.decodeItemOnNpc(body);
        assertAction(
            decoded,
            item,
            target,
            slot,
            widget
        );

        if(ClientPacketProbe
                .framingOnlyFixedLength(57)!=8)
            throw new AssertionError(
                "framing57"
            );

        int[] seed={
            0x01020304,
            0x11223344,
            0x55667788,
            0x13572468
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(seed.clone());

        wire.write((57+encoder.nextInt())&255);
        wire.write(body);

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[packet57-item-on-npc] "
            );

        if(!probe.readNextKnownPacket()||
           !probe.isAligned())
            throw new AssertionError(
                "typed packet57 decode failed"
            );

        ClientRequest request=
            probe.takeTypedRequest();
        if(!(request instanceof
                ItemOnNpcClientRequest))
            throw new AssertionError(
                "typed request="+request
            );

        ItemOnNpcClientRequest typed=
            (ItemOnNpcClientRequest)request;
        assertAction(
            typed.action(),
            item,
            target,
            slot,
            widget
        );

        ClientRequestMetadata metadata=
            typed.metadata();
        if(metadata.opcode!=57||
           !"FIXED8_ITEM_BE_A_NPC_BE_A_SLOT_LE_WIDGET_BE_A"
                .equals(metadata.schema)||
           !"PINNED_CLIENT_ITEM_ON_NPC_WRITER"
                .equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "typed queue not empty"
            );

        System.out.println(
            "V5128_C2S57_ITEM_ON_NPC_PASS "+
            "fields=4 transforms=BEA,BEA,LE,BEA "+
            "framing=8 typed=true metadata=true"
        );
    }

    private static void assertAction(
        ItemOnNpcAction action,
        int item,
        int target,
        int slot,
        int widget
    ){
        if(action.itemId!=item||
           action.targetNpcIndex!=target||
           action.slot!=slot||
           action.widgetId!=widget)
            throw new AssertionError(action);
    }

    static void putBeA(
        byte[] b,
        int o,
        int v
    ){
        b[o]=(byte)(v>>>8);
        b[o+1]=(byte)((v+128)&255);
    }

    static void putLe(
        byte[] b,
        int o,
        int v
    ){
        b[o]=(byte)v;
        b[o+1]=(byte)(v>>>8);
    }
}

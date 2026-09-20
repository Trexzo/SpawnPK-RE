package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ClientPacketProbeTest {
    public static void main(String[] args) throws Exception {
        int[] seed = {1,2,3,4};
        IsaacCipher enc = new IsaacCipher(seed.clone());
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        opcode(wire, enc, 185); wire.write(0x03); wire.write(0x90); // widget 912
        opcode(wire, enc, 103); wire.write(4); wire.write("abc\n".getBytes(StandardCharsets.ISO_8859_1));
        opcode(wire, enc, 0);
        opcode(wire, enc, 121);
        opcode(wire, enc, 3); wire.write(0); // focus lost
        opcode(wire, enc, 77); wire.write(12); wire.write(new byte[]{0x24,0x65,(byte)0xE9,(byte)0xB0,0x24,0x63,0x40,0x26,0x5D,(byte)0xD4,(byte)0xBC,(byte)0xF6});
        // Exact live v4 blocker: opcode226 is VARBYTE. First live vector had length 15.
        opcode(wire, enc, 226); wire.write(15); wire.write(new byte[]{(byte)0xE5,0x62,(byte)0xF0,0x1E,0x06,(byte)0x9C,0x02,0x3C,(byte)0xA8,0x1B,(byte)0xDA,(byte)0xE5,(byte)0xFA,(byte)0xF0,(byte)0xD9});
        // v5.2 live blocker: exact pinned-client long-session keepalive, no payload.
        opcode(wire, enc, 202);
        opcode(wire, enc, 36); wire.write(new byte[]{0,0,0,0});
        opcode(wire, enc, 164); wire.write(5); wire.write(new byte[]{(byte)0x8F,0x0C,(byte)0xA7,0x0D,0x00});
        opcode(wire, enc, 185); wire.write(0x00); wire.write(0x98); // live regression: widget 152
        // Live bank/object regression vector: x=3095 objectId=26972 y=3493.
        opcode(wire, enc, 132); wire.write(new byte[]{(byte)0x97,0x0C,0x69,0x5C,0x0D,0x25});
        // Exact v3.1 live blocker: opcode41 item=4151 slot=0 widget=3214.
        opcode(wire, enc, 41); wire.write(new byte[]{0x10,0x37,0x00,(byte)0x80,0x0C,0x0E});
        // v4 exact amount entry: opcode208 + BE int 123.
        opcode(wire, enc, 208); wire.write(new byte[]{0,0,0,123});
        // v4 exact container drag: widget5064 mode0 source0 destination5.
        opcode(wire, enc, 214); wire.write(new byte[]{0x48,0x13,0x00,(byte)0x80,0x00,0x05,0x00});
        opcode(wire, enc, 130); // live v3 regression: close-interface has exact zero payload
        opcode(wire, enc, 164); wire.write(5); wire.write(new byte[]{(byte)0x8F,0x0C,(byte)0xA8,0x0D,0x00});
        opcode(wire, enc, 248); wire.write(19);
        wire.write(new byte[]{(byte)0x90,0x0C,(byte)0xA8,0x0D,0x00});
        wire.write(new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13});

        ClientPacketProbe p = new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()), new IsaacCipher(seed.clone()), "[test] ");
        if (p.readFirst185() != 185) throw new AssertionError();
        for (int i=0;i<9;i++) if (!p.readNextKnownPacket()) throw new AssertionError("decode stopped at startup packet " + i);
        MovementRequest m1=p.takeMovement();
        if (m1==null || m1.opcode!=164 || m1.finalX()!=3087 || m1.finalY()!=3495 || m1.run)
            throw new AssertionError("first movement decode mismatch: "+m1);

        ClientRequest startupCommand=p.takeTypedRequest();
        if(!(startupCommand instanceof CommandClientRequest)||
           !"abc".equals(((CommandClientRequest)startupCommand).command()))
            throw new AssertionError("startup command request="+startupCommand);

        if (!p.readNextKnownPacket()) throw new AssertionError("generic 185 decode stopped");
        ClientRequest widgetRequest=p.takeTypedRequest();
        if(!(widgetRequest instanceof WidgetActionClientRequest)||
           ((WidgetActionClientRequest)widgetRequest).widgetId()!=152)
            throw new AssertionError("widget action mismatch: "+widgetRequest);
        if (!p.readNextKnownPacket()) throw new AssertionError("object132 decode stopped");
        ObjectInteraction oi=p.takeObjectInteraction();
        if (oi==null || oi.opcode!=132 || oi.objectId!=26972 || oi.worldX!=3095 || oi.worldY!=3493)
            throw new AssertionError("object interaction mismatch: "+oi);
        if (!p.readNextKnownPacket()) throw new AssertionError("opcode41 decode stopped");
        ItemContainerAction equip=p.takeItemAction();
        if(equip==null||equip.opcode!=41||equip.widgetId!=3214||equip.slot!=0||equip.itemId!=4151)throw new AssertionError("equip41="+equip);
        if (!p.readNextKnownPacket()) throw new AssertionError("amount208 decode stopped");
        Integer amount=p.takeAmount(); if(amount==null||amount!=123)throw new AssertionError("amount208="+amount);
        if (!p.readNextKnownPacket()) throw new AssertionError("drag214 decode stopped");
        ContainerDrag drag=p.takeContainerDrag(); if(drag==null||drag.widgetId!=5064||drag.mode!=0||drag.sourceSlot!=0||drag.destinationSlot!=5)throw new AssertionError("drag214="+drag);
        if (!p.readNextKnownPacket()) throw new AssertionError("close130 decode stopped");
        ClientRequest closeRequest=p.takeTypedRequest();
        if(!(closeRequest instanceof InterfaceCloseClientRequest))
            throw new AssertionError("close130 typed request missing: "+closeRequest);
        if (!p.readNextKnownPacket()) throw new AssertionError("second movement decode stopped");
        MovementRequest m2=p.takeMovement();
        if (m2==null || m2.opcode!=164 || m2.finalX()!=3087 || m2.finalY()!=3496 || m2.run)
            throw new AssertionError("second movement decode mismatch: "+m2);
        if (!p.readNextKnownPacket()) throw new AssertionError("minimap movement decode stopped");
        MovementRequest m3=p.takeMovement();
        if (m3==null || m3.opcode!=248 || m3.finalX()!=3088 || m3.finalY()!=3496 || m3.telemetry.length!=14)
            throw new AssertionError("minimap movement decode mismatch: "+m3);
        if (!p.isAligned() || p.decodedCount()!=18) throw new AssertionError("aligned="+p.isAligned()+" count="+p.decodedCount());
        System.out.println("CLIENT_PACKET_PROBE_V521_PASS decoded=18 aligned=true opcode226Varbyte=true opcode202Fixed0=true opcode36Fixed4=true walk164->widget185->object132->item41->amount208->drag214->close130->walk164->minimap248 telemetry14=preserved");
    }
    private static void opcode(ByteArrayOutputStream out, IsaacCipher c, int op) {
        out.write((op + c.nextInt()) & 0xff);
    }
}

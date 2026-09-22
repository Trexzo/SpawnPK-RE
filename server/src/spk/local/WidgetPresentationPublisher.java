package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 widget/interface presentation facade.
 *
 * Raw opcodes and transformed field layouts stay inside this package-private
 * protocol boundary. Gameplay/content callers deal only in semantic operations.
 */
final class WidgetPresentationPublisher {
    static final class SlotUpdate {
        final int slot;
        final int itemId;
        final int amount;

        SlotUpdate(int slot,int itemId,int amount){
            if(slot<0||slot>32767)throw new IllegalArgumentException("slot="+slot);
            if(itemId<0||itemId>0xffff)throw new IllegalArgumentException("itemId="+itemId);
            if(amount<0)throw new IllegalArgumentException("amount="+amount);
            this.slot=slot;
            this.itemId=itemId;
            this.amount=amount;
        }
    }

    private final ServerPacketWriter packets;

    WidgetPresentationPublisher(ServerPacketWriter packets){
        this.packets=Objects.requireNonNull(packets,"packets");
    }

    void widgetStaticModel(int widgetId,int modelId)throws IOException{
        packets.fixed(8,new PacketPayloadWriter()
            .putU16LELowAdd128(widgetId)
            .putU16BE(modelId)
            .toByteArray());
    }

    void flashingSidebarTab(int tab)throws IOException{
        packets.fixed(24,new PacketPayloadWriter()
            .putU8_128Minus(tab)
            .toByteArray());
    }

    void widgetContainerPartial(int widgetId,List<SlotUpdate> updates)throws IOException{
        Objects.requireNonNull(updates,"updates");
        PacketPayloadWriter payload=new PacketPayloadWriter().putU16BE(widgetId);
        for(SlotUpdate update:updates){
            Objects.requireNonNull(update,"update");
            payload.putSmartU(update.slot)
                .putU16BE(update.itemId);
            if(update.amount<255){
                payload.putU8(update.amount);
            }else{
                payload.putU8(255)
                    .putI32BE(update.amount);
            }
        }
        packets.varShort(34,payload.toByteArray());
    }

    void widgetPosition(int widgetId,int x,int y)throws IOException{
        packets.fixed(70,new PacketPayloadWriter()
            .putI16BE(x)
            .putI16LE(y)
            .putU16LE(widgetId)
            .toByteArray());
    }

    void widgetContainerClear(int widgetId)throws IOException{
        packets.fixed(72,new PacketPayloadWriter()
            .putU16LE(widgetId)
            .toByteArray());
    }

    void widgetNpcModel(int widgetId,int npcDefinitionId)throws IOException{
        packets.fixed(75,new PacketPayloadWriter()
            .putU16LELowAdd128(npcDefinitionId)
            .putU16LELowAdd128(widgetId)
            .toByteArray());
    }

    void widgetScroll(int widgetId,int scroll)throws IOException{
        packets.fixed(79,new PacketPayloadWriter()
            .putU16LE(widgetId)
            .putU16BELowAdd128(scroll)
            .toByteArray());
    }

    void widgetColor555(int widgetId,int packed555)throws IOException{
        if(packed555<0||packed555>0x7fff)
            throw new IllegalArgumentException("packed555="+packed555);
        packets.fixed(122,new PacketPayloadWriter()
            .putU16LELowAdd128(widgetId)
            .putU16LELowAdd128(packed555)
            .toByteArray());
    }

    void openSidebarOverlay(int interfaceId)throws IOException{
        packets.fixed(142,new PacketPayloadWriter()
            .putU16LE(interfaceId)
            .toByteArray());
    }

    void widgetHidden(int widgetId,boolean hidden)throws IOException{
        packets.fixed(171,new PacketPayloadWriter()
            .putU8(hidden?1:0)
            .putU16BE(widgetId)
            .toByteArray());
    }

    void widgetLocalPlayerModel(int widgetId)throws IOException{
        packets.fixed(185,new PacketPayloadWriter()
            .putU16LELowAdd128(widgetId)
            .toByteArray());
    }

    void openNameInput()throws IOException{
        packets.fixed(187,new byte[0]);
    }

    void widgetAnimation(int widgetId,int animationId)throws IOException{
        packets.fixed(200,new PacketPayloadWriter()
            .putU16BE(widgetId)
            .putI16BE(animationId)
            .toByteArray());
    }

    void dialogChatAreaRoot(int interfaceId)throws IOException{
        packets.fixed(218,new PacketPayloadWriter()
            .putI16LELowAdd128(interfaceId)
            .toByteArray());
    }

    void widgetModelTransform(
        int widgetId,
        int zoom,
        int rotationX,
        int rotationY
    )throws IOException{
        packets.fixed(230,new PacketPayloadWriter()
            .putU16BELowAdd128(zoom)
            .putU16BE(widgetId)
            .putU16BE(rotationX)
            .putU16LELowAdd128(rotationY)
            .toByteArray());
    }

    void widgetItemModel(int widgetId,int scale,int itemId)throws IOException{
        packets.fixed(246,new PacketPayloadWriter()
            .putU16LE(widgetId)
            .putU16BE(scale)
            .putU16BE(itemId)
            .toByteArray());
    }
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public final class WidgetPresentationPublisherTest {
    private static final int[] SEED={1,2,3,4};

    @FunctionalInterface
    private interface Send {
        void run(WidgetPresentationPublisher publisher)throws Exception;
    }

    public static void main(String[] args)throws Exception{
        assertBytes(
            "putI16LE",
            new byte[]{(byte)0xfe,(byte)0xff},
            new PacketPayloadWriter().putI16LE(-2).toByteArray()
        );
        assertBytes(
            "putI16LELowAdd128",
            new byte[]{0x7e,(byte)0xff},
            new PacketPayloadWriter().putI16LELowAdd128(-2).toByteArray()
        );

        assertFixed(8,bytes(0xb4,0x12,0x45,0x67),
            p->p.widgetStaticModel(0x1234,0x4567));
        assertFixed(24,bytes(0x7b),
            p->p.flashingSidebarTab(5));

        assertVarShort(34,bytes(
            0x12,0x34,
            0x07,0x45,0x67,0x2a,
            0x80,0x82,0x22,0x22,0xff,0x01,0x02,0x03,0x04
        ),p->p.widgetContainerPartial(
            0x1234,
            Arrays.asList(
                new WidgetPresentationPublisher.SlotUpdate(7,0x4567,42),
                new WidgetPresentationPublisher.SlotUpdate(130,0x2222,0x01020304)
            )
        ));

        assertFixed(70,bytes(0xff,0xfe,0x34,0x12,0x67,0x45),
            p->p.widgetPosition(0x4567,-2,0x1234));
        assertFixed(72,bytes(0x34,0x12),
            p->p.widgetContainerClear(0x1234));
        assertFixed(75,bytes(0xb4,0x12,0xe7,0x45),
            p->p.widgetNpcModel(0x4567,0x1234));
        assertFixed(79,bytes(0x34,0x12,0x45,0xe7),
            p->p.widgetScroll(0x1234,0x4567));
        assertFixed(122,bytes(0xb4,0x12,0x80,0x7c),
            p->p.widgetColor555(0x1234,0x7c00));
        assertFixed(142,bytes(0x34,0x12),
            p->p.openSidebarOverlay(0x1234));
        assertFixed(171,bytes(0x01,0x12,0x34),
            p->p.widgetHidden(0x1234,true));
        assertFixed(185,bytes(0xb4,0x12),
            p->p.widgetLocalPlayerModel(0x1234));
        assertFixed(187,new byte[0],
            WidgetPresentationPublisher::openNameInput);
        assertFixed(200,bytes(0x12,0x34,0xff,0xfe),
            p->p.widgetAnimation(0x1234,-2));
        assertFixed(218,bytes(0x7e,0xff),
            p->p.dialogChatAreaRoot(-2));
        assertFixed(230,bytes(0x12,0xb4,0x45,0x67,0x23,0x45,0xd6,0x34),
            p->p.widgetModelTransform(0x4567,0x1234,0x2345,0x3456));
        assertFixed(246,bytes(0x34,0x12,0x45,0x67,0xff,0xff),
            p->p.widgetItemModel(0x1234,0x4567,65535));

        boolean badColour=false;
        try{
            freshPublisher().widgetColor555(1,0x8000);
        }catch(IllegalArgumentException expected){
            badColour=true;
        }
        if(!badColour)throw new AssertionError("packed 5/5/5 range guard missing");

        boolean negativeAmount=false;
        try{
            new WidgetPresentationPublisher.SlotUpdate(0,1,-1);
        }catch(IllegalArgumentException expected){
            negativeAmount=true;
        }
        if(!negativeAmount)throw new AssertionError("negative partial-container amount accepted");

        if(Modifier.isPublic(WidgetPresentationPublisher.class.getModifiers()))
            throw new AssertionError("raw presentation facade leaked into public API");

        System.out.println(
            "WIDGET_PRESENTATION_PUBLISHER_PASS "+
            "families=16 partialSparse=true extendedAmount=true "+
            "signedLE16=true signedLE16Add128=true color555=true "+
            "clearItemSentinel=true zeroBody187=true publicApiLeak=false"
        );
    }

    private static WidgetPresentationPublisher freshPublisher(){
        return new WidgetPresentationPublisher(
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(SEED.clone())
            )
        );
    }

    private static void assertFixed(
        int opcode,
        byte[] body,
        Send send
    )throws Exception{
        byte[] actual=capture(send);
        byte[] expected=new byte[1+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        System.arraycopy(body,0,expected,1,body.length);
        assertBytes("fixed opcode="+opcode,expected,actual);
    }

    private static void assertVarShort(
        int opcode,
        byte[] body,
        Send send
    )throws Exception{
        byte[] actual=capture(send);
        byte[] expected=new byte[3+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        expected[1]=(byte)(body.length>>>8);
        expected[2]=(byte)body.length;
        System.arraycopy(body,0,expected,3,body.length);
        assertBytes("varShort opcode="+opcode,expected,actual);
    }

    private static byte[] capture(Send send)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        WidgetPresentationPublisher publisher=
            new WidgetPresentationPublisher(
                new ServerPacketWriter(
                    out,
                    new IsaacCipher(SEED.clone())
                )
            );
        send.run(publisher);
        return out.toByteArray();
    }

    private static int encryptedOpcode(int opcode){
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        return (opcode+cipher.nextInt())&255;
    }

    private static byte[] bytes(int... values){
        byte[] out=new byte[values.length];
        for(int i=0;i<values.length;i++)
            out[i]=(byte)values[i];
        return out;
    }

    private static void assertBytes(
        String name,
        byte[] expected,
        byte[] actual
    ){
        if(!Arrays.equals(expected,actual))
            throw new AssertionError(
                name+
                " expected="+Arrays.toString(expected)+
                " actual="+Arrays.toString(actual)
            );
    }
}

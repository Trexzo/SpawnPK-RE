package spk.local;

import java.io.*;

public final class ClientItemContainerRequestQueueTest {
    private static final int[] SEED={
        0x01020304,
        0x11223344,
        0x55667788,
        0x13572468
    };

    private static final int[] OPCODES={
        41,75,145,117,43,129,135,140,141,122,16
    };

    private static final String[] SCHEMAS={
        "FIXED6_ITEM_BE_SLOT_BE_A_WIDGET_BE_A",
        "FIXED6_WIDGET_LE_A_SLOT_LE_ITEM_BE_A",
        "FIXED6_WIDGET_BE_A_SLOT_BE_A_ITEM_BE_A",
        "FIXED6_WIDGET_LE_A_ITEM_LE_A_SLOT_LE",
        "FIXED6_WIDGET_LE_ITEM_BE_A_SLOT_BE_A",
        "FIXED6_SLOT_BE_A_WIDGET_BE_ITEM_BE_A",
        "FIXED6_SLOT_LE_WIDGET_BE_A_ITEM_LE",
        "FIXED6_SLOT_BE_A_WIDGET_BE_ITEM_BE_A",
        "FIXED10_SLOT_BE_A_WIDGET_BE_ITEM_BE_A_EXTRA_BE32",
        "FIXED6_WIDGET_LE_A_SLOT_BE_A_ITEM_LE",
        "FIXED6_ITEM_BE_A_SLOT_LE_A_WIDGET_LE_A"
    };

    private static final String[] SOURCES={
        "PINNED_CLIENT_MENU_ACTION_454_WRITER",
        "PINNED_CLIENT_MENU_ACTION_493_WRITER",
        "PINNED_CLIENT_MENU_ACTION_632_WRITER",
        "PINNED_CLIENT_MENU_ACTION_78_WRITER",
        "PINNED_CLIENT_MENU_ACTION_867_WRITER",
        "PINNED_CLIENT_MENU_ACTION_431_WRITER",
        "PINNED_CLIENT_MENU_ACTION_53_WRITER",
        "PINNED_CLIENT_MENU_ACTION_291_WRITER",
        "PINNED_CLIENT_MENU_ACTION_300_WRITER",
        "PINNED_CLIENT_INVENTORY_OPTION_1_WRITER",
        "PINNED_CLIENT_OPCODE_16_ITEM_OPTION_3_WRITER"
    };

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(SEED.clone());

        for(int i=0;i<OPCODES.length;i++){
            writeItemAction(
                wire,
                encoder,
                OPCODES[i],
                4000+i,
                20+i,
                1000+i,
                OPCODES[i]==141
                    ?0x12345678
                    :0
            );

            if(i==4)
                writeOpcode(wire,encoder,130);
        }

        writeItemAction(
            wire,
            encoder,
            41,
            5000,
            99,
            2000,
            0
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(SEED.clone()),
                "[typed-item-container] "
            );

        int packetCount=OPCODES.length+2;
        for(int i=0;i<packetCount;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decode stopped index="+i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=packetCount)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        for(int i=0;i<OPCODES.length;i++){
            assertItem(
                probe.takeTypedRequest(),
                OPCODES[i],
                4000+i,
                20+i,
                1000+i,
                OPCODES[i]==141
                    ?0x12345678
                    :0,
                SCHEMAS[i],
                SOURCES[i]
            );

            if(i==4){
                ClientRequest close=
                    probe.takeTypedRequest();
                if(!(close instanceof
                        InterfaceCloseClientRequest))
                    throw new AssertionError(
                        "cross-family FIFO close="+
                        close
                    );
            }
        }

        assertItem(
            probe.takeTypedRequest(),
            41,
            5000,
            99,
            2000,
            0,
            SCHEMAS[0],
            SOURCES[0]
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_ITEM_CONTAINER_REQUEST_QUEUE_PASS "+
            "opcodes=11 transforms=true extra141=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertItem(
        ClientRequest request,
        int opcode,
        int widget,
        int slot,
        int item,
        int extra,
        String schema,
        String source
    ){
        if(!(request instanceof
                ItemContainerActionClientRequest))
            throw new AssertionError(
                "expected item action got "+
                request
            );

        ItemContainerActionClientRequest typed=
            (ItemContainerActionClientRequest)request;
        ItemContainerAction action=typed.action();

        if(action.opcode!=opcode||
           action.widgetId!=widget||
           action.slot!=slot||
           action.itemId!=item||
           action.extra!=extra)
            throw new AssertionError(
                "item action="+action
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        if(metadata.opcode!=opcode||
           !schema.equals(metadata.schema)||
           !source.equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void writeItemAction(
        OutputStream output,
        IsaacCipher cipher,
        int opcode,
        int widget,
        int slot,
        int item,
        int extra
    )throws IOException{
        writeOpcode(output,cipher,opcode);

        switch(opcode){
            case 41:
                writeBe(output,item);
                writeBeA(output,slot);
                writeBeA(output,widget);
                return;

            case 75:
                writeLeA(output,widget);
                writeLe(output,slot);
                writeBeA(output,item);
                return;

            case 145:
                writeBeA(output,widget);
                writeBeA(output,slot);
                writeBeA(output,item);
                return;

            case 117:
                writeLeA(output,widget);
                writeLeA(output,item);
                writeLe(output,slot);
                return;

            case 43:
                writeLe(output,widget);
                writeBeA(output,item);
                writeBeA(output,slot);
                return;

            case 129:
            case 140:
                writeBeA(output,slot);
                writeBe(output,widget);
                writeBeA(output,item);
                return;

            case 135:
                writeLe(output,slot);
                writeBeA(output,widget);
                writeLe(output,item);
                return;

            case 141:
                writeBeA(output,slot);
                writeBe(output,widget);
                writeBeA(output,item);
                writeBe32(output,extra);
                return;

            case 122:
                writeLeA(output,widget);
                writeBeA(output,slot);
                writeLe(output,item);
                return;

            case 16:
                writeBeA(output,item);
                writeLeA(output,slot);
                writeLeA(output,widget);
                return;

            default:
                throw new AssertionError(
                    "unsupported opcode="+opcode
                );
        }
    }

    private static void writeOpcode(
        OutputStream output,
        IsaacCipher cipher,
        int opcode
    )throws IOException{
        output.write(
            (opcode+cipher.nextInt())&255
        );
    }

    private static void writeBe(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>8)&255);
        output.write(value&255);
    }

    private static void writeLe(
        OutputStream output,
        int value
    )throws IOException{
        output.write(value&255);
        output.write((value>>>8)&255);
    }

    private static void writeBeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>8)&255);
        output.write((value+128)&255);
    }

    private static void writeLeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value+128)&255);
        output.write((value>>>8)&255);
    }

    private static void writeBe32(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value>>>24)&255);
        output.write((value>>>16)&255);
        output.write((value>>>8)&255);
        output.write(value&255);
    }
}

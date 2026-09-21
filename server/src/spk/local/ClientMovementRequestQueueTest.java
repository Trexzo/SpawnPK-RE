package spk.local;

import java.io.*;

public final class ClientMovementRequestQueueTest {
    private static final int[] SEED={
        0x01020304,
        0x11223344,
        0x55667788,
        0x13572468
    };

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(SEED.clone());

        writeWalk(
            wire,encoder,164,
            3088,3495,false,
            new int[0],new int[0],
            new byte[0]
        );

        writeWalk(
            wire,encoder,164,
            3089,3495,false,
            new int[0],new int[0],
            new byte[0]
        );

        writeWalk(
            wire,encoder,98,
            3090,3496,true,
            new int[]{-2},
            new int[]{3},
            new byte[0]
        );

        writeOpcode(wire,encoder,130);

        byte[] telemetry=new byte[14];
        for(int i=0;i<telemetry.length;i++)
            telemetry[i]=(byte)(0xA0+i);

        writeWalk(
            wire,encoder,248,
            3100,3500,false,
            new int[0],new int[0],
            telemetry
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(SEED.clone()),
                "[typed-movement] "
            );

        for(int i=0;i<5;i++)
            if(!probe.readNextKnownPacket())
                throw new AssertionError(
                    "decode stopped index="+i
                );

        if(!probe.isAligned())
            throw new AssertionError(
                "decoder lost alignment"
            );

        if(probe.typedRequestCount()!=5)
            throw new AssertionError(
                "typed request count="+
                probe.typedRequestCount()
            );

        assertMovement(
            probe.takeTypedRequest(),
            164,
            false,
            new int[]{3088},
            new int[]{3495},
            new byte[0]
        );

        assertMovement(
            probe.takeTypedRequest(),
            164,
            false,
            new int[]{3089},
            new int[]{3495},
            new byte[0]
        );

        assertMovement(
            probe.takeTypedRequest(),
            98,
            true,
            new int[]{3090,3088},
            new int[]{3496,3499},
            new byte[0]
        );

        ClientRequest close=
            probe.takeTypedRequest();
        if(!(close instanceof
                InterfaceCloseClientRequest))
            throw new AssertionError(
                "cross-family FIFO close="+close
            );

        assertMovement(
            probe.takeTypedRequest(),
            248,
            false,
            new int[]{3100},
            new int[]{3500},
            telemetry
        );

        if(probe.takeTypedRequest()!=null)
            throw new AssertionError(
                "queue not empty"
            );

        System.out.println(
            "CLIENT_MOVEMENT_REQUEST_QUEUE_PASS "+
            "opcodes=164_98_248 transforms=true "+
            "signedDeltas=true runNeg=true telemetry14=true "+
            "duplicatesRetained=true crossFamilyFifo=true "+
            "metadata=true"
        );
    }

    private static void assertMovement(
        ClientRequest request,
        int opcode,
        boolean run,
        int[] xs,
        int[] ys,
        byte[] telemetry
    ){
        if(!(request instanceof
                MovementClientRequest))
            throw new AssertionError(
                "expected movement got "+request
            );

        MovementClientRequest typed=
            (MovementClientRequest)request;
        MovementRequest movement=typed.movement();

        if(movement.opcode!=opcode||
           movement.run!=run||
           movement.x.length!=xs.length||
           movement.y.length!=ys.length)
            throw new AssertionError(
                "movement="+movement
            );

        for(int i=0;i<xs.length;i++)
            if(movement.x[i]!=xs[i]||
               movement.y[i]!=ys[i])
                throw new AssertionError(
                    "waypoint "+i+
                    " movement="+movement
                );

        if(!java.util.Arrays.equals(
                movement.telemetry,
                telemetry
            ))
            throw new AssertionError(
                "telemetry="+
                ClientPacketProbe.hex(
                    movement.telemetry,
                    32
                )
            );

        ClientRequestMetadata metadata=
            typed.metadata();

        String expectedSchema=
            opcode==248
                ?"VARBYTE_PATH_X_LE_A_SIGNED_DELTAS_Y_LE_RUN_NEG_PLUS_OPAQUE14"
                :"VARBYTE_PATH_X_LE_A_SIGNED_DELTAS_Y_LE_RUN_NEG";

        String expectedSource;
        switch(opcode){
            case 164:
                expectedSource=
                    "PINNED_CLIENT_MOVEMENT_OPCODE_164_WRITER";
                break;
            case 98:
                expectedSource=
                    "PINNED_CLIENT_MOVEMENT_OPCODE_98_WRITER";
                break;
            case 248:
                expectedSource=
                    "PINNED_CLIENT_MINIMAP_MOVEMENT_OPCODE_248_WRITER";
                break;
            default:
                throw new AssertionError(
                    "unexpected opcode="+opcode
                );
        }

        if(metadata.opcode!=opcode||
           !expectedSchema.equals(metadata.schema)||
           !expectedSource.equals(metadata.source)||
           metadata.provenance!=
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT)
            throw new AssertionError(
                "metadata="+metadata
            );
    }

    private static void writeWalk(
        OutputStream output,
        IsaacCipher cipher,
        int opcode,
        int startX,
        int startY,
        boolean run,
        int[] dx,
        int[] dy,
        byte[] telemetry
    )throws IOException{
        if(dx.length!=dy.length)
            throw new IllegalArgumentException(
                "deltas"
            );

        ByteArrayOutputStream body=
            new ByteArrayOutputStream();

        writeLeA(body,startX);

        for(int i=0;i<dx.length;i++){
            body.write(dx[i]&255);
            body.write(dy[i]&255);
        }

        writeLe(body,startY);
        body.write(run?255:0);

        if(opcode==248){
            if(telemetry.length!=14)
                throw new IllegalArgumentException(
                    "minimap telemetry"
                );
            body.write(telemetry);
        }else if(telemetry.length!=0){
            throw new IllegalArgumentException(
                "ordinary telemetry"
            );
        }

        byte[] payload=body.toByteArray();

        writeOpcode(output,cipher,opcode);
        output.write(payload.length);
        output.write(payload);
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

    private static void writeLe(
        OutputStream output,
        int value
    )throws IOException{
        output.write(value&255);
        output.write((value>>>8)&255);
    }

    private static void writeLeA(
        OutputStream output,
        int value
    )throws IOException{
        output.write((value+128)&255);
        output.write((value>>>8)&255);
    }
}

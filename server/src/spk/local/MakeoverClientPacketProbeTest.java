package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MakeoverClientPacketProbeTest {
    public static void main(String[] args)throws Exception{
        int[] seed={11,22,33,44};
        IsaacCipher encoder=new IsaacCipher(seed.clone());
        ByteArrayOutputStream wire=new ByteArrayOutputStream();

        opcode(wire,encoder,185);
        wire.write(0x03);
        wire.write(0x90);

        opcode(wire,encoder,40);
        wire.write(0x13);
        wire.write(0x16);

        opcode(wire,encoder,101);
        wire.write(new byte[]{
            1,
            45,(byte)255,56,61,67,70,79,
            11,15,14,5,23
        });

        byte[] option=
            "dialogueoption 1\n".getBytes(
                StandardCharsets.ISO_8859_1
            );
        opcode(wire,encoder,103);
        wire.write(option.length);
        wire.write(option);

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(seed.clone()),
                "[makeover-probe-test] "
            );

        if(probe.readFirst185()!=185)
            throw new AssertionError("bootstrap 185");

        if(!probe.readNextKnownPacket())
            throw new AssertionError("C2S40 decode stopped");

        ClientRequest continueRequest=
            probe.takeTypedRequest();
        if(!(continueRequest instanceof
                DialogueContinueClientRequest)||
           ((DialogueContinueClientRequest)
                continueRequest).widgetId()!=4886)
            throw new AssertionError(
                "continue request="+continueRequest
            );

        if(!probe.readNextKnownPacket())
            throw new AssertionError("C2S101 decode stopped");

        ClientRequest designRequest=
            probe.takeTypedRequest();
        if(!(designRequest instanceof
                CharacterDesignClientRequest))
            throw new AssertionError(
                "design request="+designRequest
            );

        CharacterDesignRequest request=
            ((CharacterDesignClientRequest)
                designRequest).design();

        if(!request.valid()||
           request.gender()!=1||
           !Arrays.equals(
                request.kits(),
                new int[]{45,-1,56,61,67,70,79}
           )||
           !Arrays.equals(
                request.colours(),
                new int[]{11,15,14,5,23}
           ))
            throw new AssertionError(
                "design decode mismatch "+request
            );

        if(!probe.readNextKnownPacket())
            throw new AssertionError(
                "dialogue option decode stopped"
            );

        ClientRequest optionRequest=
            probe.takeTypedRequest();
        if(!(optionRequest instanceof
                DialogueOptionClientRequest)||
           ((DialogueOptionClientRequest)
                optionRequest).optionIndex()!=1)
            throw new AssertionError(
                "option request="+optionRequest
            );

        if(probe.typedRequestCount()!=0||
           !probe.isAligned()||
           probe.decodedCount()!=4)
            throw new AssertionError(
                "queued="+probe.typedRequestCount()+
                " aligned="+probe.isAligned()+
                " count="+probe.decodedCount()
            );

        System.out.println(
            "MAKEOVER_CLIENT_PACKET_PROBE_PASS "+
            "c2s40=typed_continue "+
            "c2s101=typed_design "+
            "c2s103_dialogueoption=typed_option "+
            "femaleJaw255ToMinus1=true aligned=true"
        );
    }

    private static void opcode(
        ByteArrayOutputStream out,
        IsaacCipher cipher,
        int opcode
    ){
        out.write(
            (opcode+cipher.nextInt())&255
        );
    }
}

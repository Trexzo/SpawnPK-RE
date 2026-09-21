package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class MakeoverClientPacketProbeTest {
    public static void main(String[] args)throws Exception{
        int[] seed={11,22,33,44};
        IsaacCipher encoder=new IsaacCipher(seed.clone());
        ByteArrayOutputStream wire=new ByteArrayOutputStream();

        opcode(wire,encoder,185);
        wire.write(0x03);
        wire.write(0x90); // bootstrap widget 912

        opcode(wire,encoder,40);
        wire.write(0x13);
        wire.write(0x16); // widget 4886

        opcode(wire,encoder,101);
        wire.write(new byte[]{
            1,
            45,(byte)255,56,61,67,70,79,
            11,15,14,5,23
        });

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

        Integer continueWidget=
            probe.takeDialogueContinue();

        if(continueWidget==null||
           continueWidget.intValue()!=4886)
            throw new AssertionError(
                "continue widget="+continueWidget
            );

        if(!probe.readNextKnownPacket())
            throw new AssertionError("C2S101 decode stopped");

        CharacterDesignRequest request=
            probe.takeCharacterDesign();

        if(request==null||!request.valid())
            throw new AssertionError(
                "design request="+request
            );

        if(request.gender()!=1||
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

        if(!probe.isAligned()||
           probe.decodedCount()!=3)
            throw new AssertionError(
                "aligned="+probe.isAligned()+
                " count="+probe.decodedCount()
            );

        System.out.println(
            "MAKEOVER_CLIENT_PACKET_PROBE_PASS c2s40=4886 c2s101=fixed13 femaleJaw255ToMinus1=true aligned=true"
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

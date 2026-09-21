package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class MakeoverPresentationMorphCompatibilityTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        dev.setPlayerNpcTransformId(599);

        PlayerPresentationService presentation=
            new PlayerPresentationService(
                dev
            );

        EquipmentState equipment=
            new EquipmentState();
        PlayerState player=
            new PlayerState();

        if(!player.setCharacterAppearance(
                CharacterDesignProfile.FEMALE,
                new int[]{
                    45,-1,56,61,67,70,79
                },
                new int[]{
                    11,15,14,5,23
                }))
            throw new AssertionError(
                "female setup rejected"
            );

        int[] seed={
            7,8,9,10
        };

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    seed.clone()
                )
            );

        presentation.refresh(
            "makeovertest",
            equipment,
            player,
            writer
        );

        byte[] bytes=
            wire.toByteArray();

        IsaacCipher decoder=
            new IsaacCipher(
                seed.clone()
            );

        int opcode=
            ((bytes[0]&255)-
             decoder.nextInt())&
            255;

        if(opcode!=81)
            throw new AssertionError(
                "opcode="+opcode
            );

        int length=
            ((bytes[1]&255)<<8)|
            (bytes[2]&255);

        if(length!=bytes.length-3)
            throw new AssertionError(
                "length="+length+
                " wire="+bytes.length
            );

        byte[] body=
            Arrays.copyOfRange(
                bytes,
                3,
                bytes.length
            );

        byte[] expectedMorph=
            BootstrapPackets
                .player81AppearanceOnly(
                    "makeovertest",
                    equipment.appearanceItems(),
                    player,
                    Integer.valueOf(599)
                );

        byte[] unexpectedNormal=
            BootstrapPackets
                .player81AppearanceOnly(
                    "makeovertest",
                    equipment.appearanceItems(),
                    player
                );

        if(!Arrays.equals(
                body,
                expectedMorph))
            throw new AssertionError(
                "presentation refresh lost morph"
            );

        if(Arrays.equals(
                body,
                unexpectedNormal))
            throw new AssertionError(
                "presentation refresh used normal appearance"
            );

        if(!Integer.valueOf(599).equals(
                dev.playerNpcTransformId()))
            throw new AssertionError(
                "dev morph state changed"
            );

        System.out.println(
            "MAKEOVER_PRESENTATION_MORPH_COMPAT_PASS "+
            "npcMorph=599 packet81=true femaleStatePreserved=true "+
            "normalAppearanceRejected=true"
        );
    }
}

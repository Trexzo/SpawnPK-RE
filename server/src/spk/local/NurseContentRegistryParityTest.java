package spk.local;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.ContentResult;

public final class NurseContentRegistryParityTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer legacyPlayer=
            seededPlayer();
        WorldPlayer contentPlayer=
            seededPlayer();

        ByteArrayOutputStream legacyWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream contentWire=
            new ByteArrayOutputStream();

        ServerPacketWriter legacyWriter=
            new ServerPacketWriter(
                legacyWire,
                new IsaacCipher(
                    new int[]{11,22,33,44}
                )
            );
        ServerPacketWriter contentWriter=
            new ServerPacketWriter(
                contentWire,
                new IsaacCipher(
                    new int[]{11,22,33,44}
                )
            );

        LocalNurseCommandHandler legacy=
            new LocalNurseCommandHandler(
                legacyPlayer.playerState(),
                legacyPlayer.movement(),
                new PlayerStatusService(
                    legacyPlayer
                )
            );

        LocalNurseCommandHandler.Result
            expected=
                legacy.handle(
                    new String[]{"nurse"},
                    "::nurse",
                    false,
                    legacyWriter
                );

        World world=
            World.isolatedForTest(20L);

        try{
            world.registerPlayer(
                contentPlayer,
                "testprofile"
            );
            world.start();

            AtomicReference<ContentResult> actual=
                new AtomicReference<>();

            world.submitAndWait(
                contentPlayer,
                ()->actual.set(
                    world.content()
                        .dispatchCommand(
                            contentPlayer,
                            "::nurse",
                            contentWriter
                        )
                ),
                5_000L
            );

            if(actual.get()==null)
                throw new AssertionError(
                    "content nurse not handled"
                );

            if(!Objects.equals(
                    expected.logText,
                    actual.get().logText())||
               !Objects.equals(
                    expected.saveReason,
                    actual.get().saveReason()))
                throw new AssertionError(
                    "nurse outcome parity mismatch expected="+
                    expected.logText+
                    "/"+expected.saveReason+
                    " actual="+actual.get()
                );

            if(!Arrays.equals(
                    legacyWire.toByteArray(),
                    contentWire.toByteArray()))
                throw new AssertionError(
                    "nurse wire parity mismatch legacy="+
                    hex(legacyWire.toByteArray())+
                    " content="+
                    hex(contentWire.toByteArray())
                );

            assertStateParity(
                legacyPlayer,
                contentPlayer
            );

            ContentRegistry.BindingInfo binding=
                world.content()
                    .commandBinding(
                        "nurse"
                    );

            if(binding==null||
               !"CUSTOM_LOCALLAB".equals(
                    binding.provenance.name()))
                throw new AssertionError(
                    "nurse provenance="+
                    binding
                );

            System.out.println(
                "NURSE_CONTENT_REGISTRY_PARITY_PASS "+
                "state=true "+
                "wireBytes="+contentWire.size()+
                " log=true "+
                "saveReason=true "+
                "provenance=CUSTOM_LOCALLAB"
            );
        }finally{
            if(contentPlayer.registered())
                world.unregisterPlayer(
                    contentPlayer
                );
            world.close();
        }
    }

    private static WorldPlayer seededPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        PlayerState state=
            player.playerState();

        state.setCurrentLevel(
            PlayerState.ATTACK,
            70
        );
        state.setCurrentLevel(
            PlayerState.DEFENCE,
            71
        );
        state.setCurrentLevel(
            PlayerState.STRENGTH,
            72
        );
        state.setCurrentLevel(
            PlayerState.HITPOINTS,
            37
        );
        state.setCurrentLevel(
            PlayerState.RANGED,
            44
        );
        state.setCurrentLevel(
            PlayerState.PRAYER,
            12
        );
        state.setCurrentLevel(
            PlayerState.MAGIC,
            55
        );
        state.setSpecialEnergy(17);
        state.setNegativeEffects(3,4,5);
        player.movement()
            .setRunEnergy(19);

        return player;
    }

    private static void assertStateParity(
        WorldPlayer expected,
        WorldPlayer actual
    ){
        for(int skill=0;
            skill<PlayerState.COMBAT_SKILL_COUNT;
            skill++){
            if(expected.playerState()
                    .currentLevel(skill)!=
               actual.playerState()
                    .currentLevel(skill))
                throw new AssertionError(
                    "skill parity "+skill
                );

            if(expected.playerState()
                    .xp(skill)!=
               actual.playerState()
                    .xp(skill))
                throw new AssertionError(
                    "xp parity "+skill
                );
        }

        if(expected.playerState()
                .specialEnergy()!=
           actual.playerState()
                .specialEnergy()||
           expected.playerState()
                .poison()!=
           actual.playerState()
                .poison()||
           expected.playerState()
                .venom()!=
           actual.playerState()
                .venom()||
           expected.playerState()
                .sicken()!=
           actual.playerState()
                .sicken()||
           expected.movement()
                .runEnergy()!=
           actual.movement()
                .runEnergy())
            throw new AssertionError(
                "nurse scalar state parity"
            );
    }

    private static String hex(byte[] data){
        StringBuilder out=
            new StringBuilder();

        for(byte value:data)
            out.append(
                String.format(
                    "%02X",
                    value&0xff
                )
            );

        return out.toString();
    }
}

package spk.local;

import java.io.*;

public final class PlayerStatusServiceTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        PlayerStatusService statuses=
            new PlayerStatusService(player);
        PlayerState state=player.playerState();

        int hpBefore=
            state.currentLevel(
                PlayerState.HITPOINTS
            );

        PlayerStatusState.Effect poison=
            statuses.apply(
                PlayerStatusState.Type.POISON,
                7,
                3,
                10L,
                "CUSTOM_LOCALLAB_TEST"
            );

        statuses.apply(
            PlayerStatusState.Type.VENOM,
            9,
            5,
            10L,
            "CUSTOM_LOCALLAB_TEST"
        );

        statuses.apply(
            PlayerStatusState.Type.SICKEN,
            2,
            1,
            10L,
            "CUSTOM_LOCALLAB_TEST"
        );

        if(poison.expiresAtTick!=13L)
            throw new AssertionError(
                "poison expiry changed "+
                poison
            );

        if(state.poison()!=7||
           state.venom()!=9||
           state.sicken()!=2)
            throw new AssertionError(
                "status magnitudes not mirrored"
            );

        if(statuses.tick(10L).changed())
            throw new AssertionError(
                "status expired on apply tick"
            );

        PlayerStatusService.TickResult tick11=
            statuses.tick(11L);

        if(!tick11.expired.contains(
                PlayerStatusState.Type.SICKEN)||
           state.sicken()!=0||
           state.poison()!=7||
           state.venom()!=9)
            throw new AssertionError(
                "sicken expiry mismatch "+
                tick11
            );

        if(statuses.tick(12L).changed())
            throw new AssertionError(
                "unexpected tick12 expiry"
            );

        PlayerStatusService.TickResult tick13=
            statuses.tick(13L);

        if(!tick13.expired.contains(
                PlayerStatusState.Type.POISON)||
           state.poison()!=0||
           state.venom()!=9)
            throw new AssertionError(
                "poison expiry mismatch "+
                tick13
            );

        PlayerStatusService.TickResult tick15=
            statuses.tick(15L);

        if(!tick15.expired.contains(
                PlayerStatusState.Type.VENOM)||
           state.poison()!=0||
           state.venom()!=0||
           state.sicken()!=0)
            throw new AssertionError(
                "venom expiry mismatch "+
                tick15
            );

        // Status scheduling must not invent poison/venom damage.
        if(state.currentLevel(
                PlayerState.HITPOINTS)!=hpBefore)
            throw new AssertionError(
                "status scheduler invented HP damage"
            );

        statuses.apply(
            PlayerStatusState.Type.POISON,
            4,
            50,
            20L,
            "CUSTOM_LOCALLAB_TEST"
        );

        LocalNurseCommandHandler nurse=
            new LocalNurseCommandHandler(
                state,
                player.movement(),
                statuses
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        LocalNurseCommandHandler.Result result=
            nurse.handle(
                new String[]{"nurse"},
                "::nurse",
                false,
                new ServerPacketWriter(
                    out,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                )
            );

        if(result==null)
            throw new AssertionError(
                "nurse status integration missing"
            );

        if(!player.statusState().empty()||
           state.poison()!=0||
           state.venom()!=0||
           state.sicken()!=0)
            throw new AssertionError(
                "nurse did not clear canonical timed statuses"
            );

        if(!"UNKNOWN_SERVER_AUTHORITY".equals(
                PlayerStatusService.EFFECT_MECHANICS_AUTHORITY))
            throw new AssertionError(
                "unknown status mechanics lost provenance"
            );

        System.out.println(
            "PLAYER_STATUS_SERVICE_PASS "+
            "sickenExpiry=11 poisonExpiry=13 venomExpiry=15 "+
            "periodicDamageInvented=false "+
            "nurseClear=true "+
            "expiryAuthority="+
            PlayerStatusService.EXPIRY_AUTHORITY+
            " effectMechanics="+
            PlayerStatusService.EFFECT_MECHANICS_AUTHORITY
        );
    }
}

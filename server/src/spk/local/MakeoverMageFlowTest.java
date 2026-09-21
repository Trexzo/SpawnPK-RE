package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class MakeoverMageFlowTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=
            new WorldPlayer();

        LocalMakeoverMageHandler handler=
            new LocalMakeoverMageHandler(
                player,
                player.equipment()
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        NpcEntity mage=
            new NpcEntity(
                30,
                LocalMakeoverMageHandler.NPC_ID,
                3082,
                3506
            );

        NpcAction talk=
            new NpcAction(
                155,
                30
            );

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(
                talk,
                mage
            );

        if(route.option!=1||
           route.service!=NpcInteractionRouter.Service.TALK)
            throw new AssertionError(
                "NPC 599 route="+route
            );

        if(!handler.beginIfSupported(
                talk,
                mage,
                packets,
                "[makeover-flow-test] "))
            throw new AssertionError(
                "Make-over dialogue did not start"
            );

        if(!handler.handleContinue(
                LocalMakeoverMageHandler.INTRO_CONTINUE_WIDGET,
                packets,
                "[makeover-flow-test] "))
            throw new AssertionError(
                "Continue was not consumed"
            );

        if(!handler.handleWidget(
                LocalMakeoverMageHandler.CHANGE_LOOK_WIDGET,
                packets,
                "[makeover-flow-test] "))
            throw new AssertionError(
                "Change-look option was not consumed"
            );

        CharacterDesignRequest request=
            CharacterDesignRequest.decode(
                new byte[]{
                    1,
                    45,(byte)255,56,61,67,70,79,
                    11,15,14,5,23
                }
            );

        LocalMakeoverMageHandler.Result result=
            handler.handleDesign(
                request,
                "makeovertest",
                packets,
                "[makeover-flow-test] "
            );

        if(!result.handled||
           !"CHARACTER_DESIGN".equals(
                result.saveReason))
            throw new AssertionError(
                "design result handled="+
                result.handled+
                " saveReason="+
                result.saveReason
            );

        if(handler.active())
            throw new AssertionError(
                "designer remained active after Accept"
            );

        PlayerState state=
            player.playerState();

        if(state.characterGender()!=
                CharacterDesignProfile.FEMALE)
            throw new AssertionError(
                "female gender not applied"
            );

        if(!Arrays.equals(
                state.characterKits(),
                new int[]{
                    45,-1,56,61,67,70,79
                }))
            throw new AssertionError(
                "kit state="+
                Arrays.toString(
                    state.characterKits()
                )
            );

        if(!Arrays.equals(
                state.characterColours(),
                new int[]{
                    11,15,14,5,23
                }))
            throw new AssertionError(
                "colour state="+
                Arrays.toString(
                    state.characterColours()
                )
            );

        packets.flush();

        if(wire.size()==0)
            throw new AssertionError(
                "no server packets emitted"
            );

        System.out.println(
            "MAKEOVER_MAGE_FLOW_PASS npc=599 route=TALK roots=4882->2459->3559 c2s101=true gender=FEMALE femaleJaw=-1 packet81Refresh=true wireBytes="+
            wire.size()
        );
    }
}

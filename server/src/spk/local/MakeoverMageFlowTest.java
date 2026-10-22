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

        final int[] designerRootPublishes={0};
        handler.installDesignerRootOwner(
            action->{
                designerRootPublishes[0]++;
                action.open();
            }
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
                player.movement().x()+1,
                player.movement().y()
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

        LocalMakeoverMageHandler keyboardAlias=
            new LocalMakeoverMageHandler(
                player,
                player.equipment()
            );

        if(!keyboardAlias.beginIfSupported(
                talk,
                mage,
                packets,
                "[makeover-flow-test] ")||
           !keyboardAlias.handleContinue(
                StandardDialoguePresentationAdapter.KEYBOARD_CONTINUE_WIDGET,
                packets,
                "[makeover-flow-test] ")||
           !keyboardAlias.handleOption(
                1,
                packets,
                "[makeover-flow-test] "))
            throw new AssertionError(
                "keyboard Continue/option path was not accepted"
            );

        if(!handler.handleContinue(
                StandardDialoguePresentationAdapter.namedNpcContinueWidget(1),
                packets,
                "[makeover-flow-test] "))
            throw new AssertionError(
                "Continue was not consumed"
            );

        if(designerRootPublishes[0]!=0)
            throw new AssertionError(
                "dialogue/chatbox transition entered designer root ownership"
            );

        if(!handler.handleWidget(
                StandardDialoguePresentationAdapter.twoOptionWidget(1),
                packets,
                "[makeover-flow-test] "))
            throw new AssertionError(
                "Change-look option was not consumed"
            );

        if(designerRootPublishes[0]!=1)
            throw new AssertionError(
                "designer root 3559 did not enter root ownership exactly once count="+
                designerRootPublishes[0]
            );

        CharacterDesignRequest request=
            CharacterDesignRequest.decode(
                new byte[]{
                    1,
                    45,(byte)255,56,61,67,70,79,
                    11,15,14,5,23
                }
            );

        WorldPlayer retirePlayer=
            new WorldPlayer();
        LocalMakeoverMageHandler retireHandler=
            new LocalMakeoverMageHandler(
                retirePlayer,
                retirePlayer.equipment()
            );
        int[] retireDesignerRootPublishes={0};
        retireHandler.installDesignerRootOwner(
            action->{
                retireDesignerRootPublishes[0]++;
                action.open();
            }
        );

        ByteArrayOutputStream retireWire=
            new ByteArrayOutputStream();
        ServerPacketWriter retirePackets=
            new ServerPacketWriter(
                retireWire,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );
        NpcEntity retireMage=
            new NpcEntity(
                31,
                LocalMakeoverMageHandler.NPC_ID,
                retirePlayer.movement().x()+1,
                retirePlayer.movement().y()
            );
        NpcAction retireTalk=
            new NpcAction(
                155,
                31
            );

        if(!retireHandler.beginIfSupported(
                retireTalk,
                retireMage,
                retirePackets,
                "[makeover-retire-test] "))
            throw new AssertionError(
                "designer-retirement dialogue did not start"
            );

        if(retireHandler.retireDesignerRoot()||
           !retireHandler.active())
            throw new AssertionError(
                "designer-only retirement altered ordinary dialogue"
            );

        if(!retireHandler.handleContinue(
                StandardDialoguePresentationAdapter
                    .namedNpcContinueWidget(1),
                retirePackets,
                "[makeover-retire-test] ")||
           !retireHandler.handleOption(
                1,
                retirePackets,
                "[makeover-retire-test] "))
            throw new AssertionError(
                "designer-retirement fixture did not reach root 3559"
            );

        if(retireDesignerRootPublishes[0]!=1||
           !retireHandler.designActive())
            throw new AssertionError(
                "designer-retirement fixture did not own root 3559"
            );

        PlayerState retireState=
            retirePlayer.playerState();
        int retireGenderBefore=
            retireState.characterGender();
        int[] retireKitsBefore=
            retireState.characterKits().clone();
        int[] retireColoursBefore=
            retireState.characterColours().clone();
        int retireWireBefore=
            retireWire.size();

        if(!retireHandler.retireDesignerRoot()||
           retireHandler.designActive()||
           retireHandler.active())
            throw new AssertionError(
                "designer-only retirement did not clear exact root ownership"
            );

        LocalMakeoverMageHandler.Result lateDesign=
            retireHandler.handleDesign(
                request,
                retirePackets,
                "[makeover-retire-test] "
            );

        if(!lateDesign.handled||
           lateDesign.saveReason!=null||
           lateDesign.logText==null||
           !lateDesign.logText.contains(
                "NO_ACTIVE_DESIGN"
           )||
           retireWire.size()!=retireWireBefore||
           retireState.characterGender()!=
                retireGenderBefore||
           !Arrays.equals(
                retireState.characterKits(),
                retireKitsBefore
           )||
           !Arrays.equals(
                retireState.characterColours(),
                retireColoursBefore
           ))
            throw new AssertionError(
                "retired hidden designer accepted/mutated late character design result="+
                lateDesign.logText
            );

        LocalMakeoverMageHandler.Result result=
            handler.handleDesign(
                request,
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
            "MAKEOVER_MAGE_FLOW_PASS npc=599 route=TALK roots=4882->2459->3559 c2s101=true keyboardContinue4907=true keyboardDialogueOption1=true chatboxPreservesMainRoot=true designerRootOwnership=true designerOnlyRetirement=true ordinaryDialoguePreserved=true hiddenDesignRejected=true gender=FEMALE femaleJaw=-1 appearanceStateApplied=true wireBytes="+
            wire.size()
        );
    }
}

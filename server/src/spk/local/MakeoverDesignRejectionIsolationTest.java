package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Fail-closed isolation proof for exact-current C2S101.
 *
 * A character-design submit is authoritative only while this session has the
 * native Make-over designer open. Unsolicited or invalid submissions must not
 * mutate appearance, request persistence, or leave a stale design stage that
 * contaminates the next legitimate interaction.
 */
public final class MakeoverDesignRejectionIsolationTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
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

        PlayerState state=player.playerState();
        int initialGender=state.characterGender();
        int[] initialKits=state.characterKits();
        int[] initialColours=state.characterColours();

        CharacterDesignRequest validFemale=
            CharacterDesignRequest.decode(
                new byte[]{
                    1,
                    45,(byte)255,56,61,67,70,79,
                    11,15,14,5,23
                }
            );

        // A valid wire payload is still unauthorized without an active designer.
        LocalMakeoverMageHandler.Result unsolicited=
            handler.handleDesign(
                validFemale,
                packets,
                "[makeover-reject-test] "
            );

        assertRejected(
            unsolicited,
            "unsolicited"
        );
        assertAppearance(
            state,
            initialGender,
            initialKits,
            initialColours,
            "unsolicited"
        );
        if(handler.active())
            throw new AssertionError(
                "unsolicited submit activated handler"
            );

        packets.flush();
        if(wire.size()!=0)
            throw new AssertionError(
                "unsolicited submit emitted server output bytes="+
                wire.size()
            );

        openDesigner(
            player,
            handler,
            packets
        );
        packets.flush();
        int wireBeforeInvalid=wire.size();

        byte[] invalidBody={
            1,
            45,10,56,61,67,70,79,
            11,15,14,5,23
        };
        CharacterDesignRequest invalid=
            CharacterDesignRequest.decode(
                invalidBody
            );

        if(invalid.valid())
            throw new AssertionError(
                "invalid female jaw unexpectedly valid"
            );

        LocalMakeoverMageHandler.Result rejected=
            handler.handleDesign(
                invalid,
                packets,
                "[makeover-reject-test] "
            );

        assertRejected(
            rejected,
            "invalid-active"
        );
        assertAppearance(
            state,
            initialGender,
            initialKits,
            initialColours,
            "invalid-active"
        );

        if(handler.active())
            throw new AssertionError(
                "invalid active submit left designer active"
            );

        packets.flush();
        if(wire.size()<=wireBeforeInvalid)
            throw new AssertionError(
                "invalid active submit did not emit close/interface output"
            );

        // A valid request immediately after the rejected one must still be
        // unauthorized until the user explicitly opens a fresh designer.
        LocalMakeoverMageHandler.Result staleFollowup=
            handler.handleDesign(
                validFemale,
                packets,
                "[makeover-reject-test] "
            );

        assertRejected(
            staleFollowup,
            "stale-followup"
        );
        assertAppearance(
            state,
            initialGender,
            initialKits,
            initialColours,
            "stale-followup"
        );

        // Fresh designer: a close-publication failure must leave both the
        // prior appearance and the designer authority intact for a clean retry.
        openDesigner(
            player,
            handler,
            packets
        );

        ServerPacketWriter failedClosePackets=
            new ServerPacketWriter(
                new java.io.OutputStream(){
                    @Override public void write(int value)
                        throws java.io.IOException{
                        throw new java.io.IOException(
                            "EXPECTED_DESIGN_CLOSE_FAILURE"
                        );
                    }
                },
                new IsaacCipher(
                    new int[]{9,10,11,12}
                )
            );

        boolean closeFailed=false;

        try{
            handler.handleDesign(
                validFemale,
                failedClosePackets,
                "[makeover-reject-test] "
            );
        }catch(java.io.IOException expected){
            closeFailed=
                "EXPECTED_DESIGN_CLOSE_FAILURE"
                    .equals(
                        expected.getMessage()
                    );
        }

        assertAppearance(
            state,
            initialGender,
            initialKits,
            initialColours,
            "failed-close"
        );

        if(!closeFailed||
           !handler.active()||
           !handler.designActive())
            throw new AssertionError(
                "failed design close did not preserve designer authority"
            );

        LocalMakeoverMageHandler.Result accepted=
            handler.handleDesign(
                validFemale,
                packets,
                "[makeover-reject-test] "
            );

        if(!accepted.handled||
           !"CHARACTER_DESIGN".equals(
                accepted.saveReason))
            throw new AssertionError(
                "fresh valid request not accepted: handled="+
                accepted.handled+
                " saveReason="+
                accepted.saveReason
            );

        assertAppearance(
            state,
            CharacterDesignProfile.FEMALE,
            new int[]{45,-1,56,61,67,70,79},
            new int[]{11,15,14,5,23},
            "fresh-valid"
        );

        if(handler.active())
            throw new AssertionError(
                "accepted request left designer active"
            );

        System.out.println(
            "MAKEOVER_DESIGN_REJECTION_ISOLATION_PASS "+
            "unsolicitedNoMutation=true invalidNoMutation=true "+
            "invalidClosesStage=true staleFollowupRejected=true "+
            "designCloseFailureAtomic=true "+
            "designCloseFailurePreservesDesigner=true "+
            "freshSessionRecovers=true"
        );
    }

    private static void openDesigner(
        WorldPlayer player,
        LocalMakeoverMageHandler handler,
        ServerPacketWriter packets
    )throws Exception{
        /*
         * This regression tests C2S101 rejection/isolation, not approach routing.
         * Keep its synthetic NPC already adjacent so the designer opens
         * synchronously under the runtime's real NPC-range contract.
         */
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

        if(!handler.beginIfSupported(
                talk,
                mage,
                packets,
                "[makeover-reject-test] "))
            throw new AssertionError(
                "dialogue did not start"
            );

        if(!handler.handleContinue(
                StandardDialoguePresentationAdapter.namedNpcContinueWidget(1),
                packets,
                "[makeover-reject-test] "))
            throw new AssertionError(
                "continue not handled"
            );

        if(!handler.handleWidget(
                StandardDialoguePresentationAdapter.twoOptionWidget(1),
                packets,
                "[makeover-reject-test] "))
            throw new AssertionError(
                "change-look not handled"
            );

        if(!handler.active())
            throw new AssertionError(
                "designer not active"
            );
    }

    private static void assertRejected(
        LocalMakeoverMageHandler.Result result,
        String phase
    ){
        if(!result.handled)
            throw new AssertionError(
                phase+" was not consumed"
            );
        if(result.saveReason!=null)
            throw new AssertionError(
                phase+" requested persistence: "+
                result.saveReason
            );
    }

    private static void assertAppearance(
        PlayerState state,
        int gender,
        int[] kits,
        int[] colours,
        String phase
    ){
        if(state.characterGender()!=gender)
            throw new AssertionError(
                phase+" gender="+
                state.characterGender()
            );

        if(!Arrays.equals(
                state.characterKits(),
                kits))
            throw new AssertionError(
                phase+" kits="+
                Arrays.toString(
                    state.characterKits()
                )
            );

        if(!Arrays.equals(
                state.characterColours(),
                colours))
            throw new AssertionError(
                phase+" colours="+
                Arrays.toString(
                    state.characterColours()
                )
            );
    }
}

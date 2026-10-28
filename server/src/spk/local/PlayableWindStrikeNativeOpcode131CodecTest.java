package spk.local;

/**
 * P5.26 pinned-client opcode131 body contract (NPC LE-A, spell BE-A).
 *
 * This tests the actual inbound body decoder and the exact-current
 * typed metadata needed by the isolated source fixture. It is NOT
 * a real TCP/ISAAC/graphical client acceptance test.
 */
public final class PlayableWindStrikeNativeOpcode131CodecTest {
    private PlayableWindStrikeNativeOpcode131CodecTest(){}

    private static void require(boolean ok,String detail){
        if(!ok)throw new AssertionError(
            "P526_NATIVE_SPELL_OPCODE131 "+detail);
    }

    private static byte[] encode(int scene,int widget){
        return new byte[]{
            (byte)(((scene&255)+128)&255),
            (byte)((scene>>>8)&255),
            (byte)((widget>>>8)&255),
            (byte)(((widget&255)+128)&255)
        };
    }

    private static void invalid(int opcode,byte[] body){
        try{
            ClientPacketProbe.decodeSpellTarget(opcode,body);
            throw new AssertionError(
                "unreviewed request accepted opcode="+opcode);
        }catch(IllegalArgumentException expected){
            // The exact pinned decoder rejects unsupported lengths and opcodes.
        }
    }

    public static void main(String[] args){
        require(args.length==0,"unexpected CLI args");

        final int[] sceneIndexes={
            0,1,127,128,255,256,4095,4096,16382
        };
        final int[] widgets={0,127,128,255,256,1152,19100,65535};
        int decoded=0;
        for(int scene:sceneIndexes)for(int widget:widgets){
            byte[] body=encode(scene,widget);
            SpellTargetRequest req=
                ClientPacketProbe.decodeSpellTarget(131,body);
            require(req.opcode==131&&
                req.kind==SpellTargetRequest.Kind.NPC&&
                req.targetIndex==scene&&
                req.spellWidget==widget&&
                req.targetId==-1&&req.targetWidget==-1&&
                req.targetSlot==-1&&req.worldX==-1&&
                req.worldY==-1,
                "wire body decoded incorrectly scene="+scene+
                    " widget="+widget);
            decoded++;
        }
        for(int size:new int[]{0,1,2,3,5,6,8}){
            invalid(131,new byte[size]);
        }
        invalid(19,encode(1,1152));
        invalid(131,null);

        ClientRequestMetadata exact=
            ClientRequestMetadata.exactCurrent(
                131,"FIXED4_NPC_LE_A_SPELL_BE_A",
                "PINNED_CLIENT_SPELL_ON_NPC_WRITER");
        require(exact.opcode==131&&
            exact.provenance==
                ClientRequestProvenance.EXACT_CURRENT_CLIENT,
            "exact current client metadata changed");

        System.out.println(
            "P526_NATIVE_SPELL_OPCODE131_CODEC_PASS"+
            " decodedCases="+decoded+
            " exactLeANpcAndBeASpell=true"+
            " badLengthsAndUnsupportedOpDenied=true"+
            " typedMetadataExactCurrent=true"+
            " realSocketAndGraphicalClientUnverified=true");
    }
}

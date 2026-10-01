package spk.local;

import java.io.ByteArrayOutputStream;

/** Exact-current packet-81 appearance marker and morph publication atomicity. */
public final class PlayerNpcMorphAppearanceTest {
    public static void main(String[] args)throws Exception{
        exactAppearanceMarker();
        publicationAtomicity();

        System.out.println(
            "V592_PLAYER_NPC_MORPH_APPEARANCE_PASS "+
            "npc=8330 "+
            "marker=FFFF "+
            "entityFamily=PLAYER "+
            "remainingEquipmentOmitted=true "+
            "sessionOnlyReady=true "+
            "morphPublicationAtomic=true "+
            "morphReplacementAtomic=true "+
            "morphClearAtomic=true "+
            "traceCommitOnly=true "+
            "authority=EXACT_CURRENT_CLIENT"
        );
    }

    private static void exactAppearanceMarker(){
        EquipmentState eq=new EquipmentState();
        PlayerState ps=new PlayerState();
        ps.syncEquipmentPresentation(eq);

        byte[] normal=
            BootstrapPackets.appearanceBlock(
                "opensrc",
                eq.appearanceItems(),
                ps
            );
        byte[] morph=
            BootstrapPackets.appearanceBlock(
                "opensrc",
                eq.appearanceItems(),
                ps,
                8330
            );

        req(
            (morph[7]&255)==255 &&
            (morph[8]&255)==255,
            "slot0 marker"
        );
        req(
            (((morph[9]&255)<<8)|
                (morph[10]&255))==8330,
            "npc id"
        );
        // The exact parser exits the normal 12 equipment-slot loop immediately,
        // so the byte after npcId is the optional bs presence flag.
        req(
            (morph[11]&255)==0,
            "bs flag immediately follows transform"
        );
        req(
            morph.length<normal.length,
            "morph must omit remaining normal equipment slots"
        );

        byte[] p81=
            BootstrapPackets.player81AppearanceOnly(
                "opensrc",
                eq.appearanceItems(),
                ps,
                8330
            );
        req(
            p81.length>morph.length &&
            contains(
                p81,
                new byte[]{
                    (byte)0xff,
                    (byte)0xff,
                    0x20,
                    (byte)0x8a
                }
            ),
            "packet81 contains morph marker/id"
        );
    }

    private static void publicationAtomicity()
        throws Exception
    {
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        dev.trace().setEnabled(true);

        PlayerPresentationService presentation=
            new PlayerPresentationService(dev);
        EquipmentState equipment=
            new EquipmentState();
        PlayerState player=
            new PlayerState();
        player.syncEquipmentPresentation(
            equipment
        );

        // NORMAL -> NPC failure keeps NORMAL and records no trace.
        boolean firstFailed=false;
        try{
            presentation.morph(
                8330,
                "opensrc",
                equipment,
                player,
                failingWriter(1)
            );
        }catch(java.io.IOException expected){
            firstFailed=true;
        }

        req(
            firstFailed,
            "initial morph publication did not fail"
        );
        req(
            dev.playerNpcTransformId()==null,
            "failed initial morph committed hidden target"
        );
        req(
            dev.trace().size()==0,
            "failed initial morph recorded trace"
        );

        ServerPacketWriter good=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        String firstRetry=
            presentation.morph(
                8330,
                "opensrc",
                equipment,
                player,
                good
            );

        req(
            firstRetry.startsWith(
                "DEV_PLAYER_MORPH_OK"
            ),
            "initial morph retry result"
        );
        req(
            Integer.valueOf(8330).equals(
                dev.playerNpcTransformId()
            ),
            "initial morph retry did not commit"
        );
        req(
            dev.trace().size()==1,
            "initial morph retry trace count"
        );

        // NPC A -> NPC B failure keeps A and trace count.
        boolean replacementFailed=false;
        try{
            presentation.morph(
                415,
                "opensrc",
                equipment,
                player,
                failingWriter(9)
            );
        }catch(java.io.IOException expected){
            replacementFailed=true;
        }

        req(
            replacementFailed,
            "replacement morph publication did not fail"
        );
        req(
            Integer.valueOf(8330).equals(
                dev.playerNpcTransformId()
            ),
            "failed replacement morph changed target"
        );
        req(
            dev.trace().size()==1,
            "failed replacement morph recorded trace"
        );

        String replacementRetry=
            presentation.morph(
                415,
                "opensrc",
                equipment,
                player,
                good
            );

        req(
            replacementRetry.startsWith(
                "DEV_PLAYER_MORPH_OK"
            ),
            "replacement morph retry result"
        );
        req(
            Integer.valueOf(415).equals(
                dev.playerNpcTransformId()
            ),
            "replacement morph retry did not commit"
        );
        req(
            dev.trace().size()==2,
            "replacement morph retry trace count"
        );

        // NPC -> NORMAL failure keeps prior NPC and trace count.
        boolean clearFailed=false;
        try{
            presentation.clear(
                "opensrc",
                equipment,
                player,
                failingWriter(13)
            );
        }catch(java.io.IOException expected){
            clearFailed=true;
        }

        req(
            clearFailed,
            "clear publication did not fail"
        );
        req(
            Integer.valueOf(415).equals(
                dev.playerNpcTransformId()
            ),
            "failed clear changed morph target"
        );
        req(
            dev.trace().size()==2,
            "failed clear recorded trace"
        );

        String clearRetry=
            presentation.clear(
                "opensrc",
                equipment,
                player,
                good
            );

        req(
            clearRetry.startsWith(
                "DEV_PLAYER_MORPH_CLEAR"
            ),
            "clear retry result"
        );
        req(
            dev.playerNpcTransformId()==null,
            "clear retry did not commit NORMAL"
        );
        req(
            dev.trace().size()==3,
            "clear retry trace count"
        );
    }

    private static ServerPacketWriter failingWriter(
        int seed
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );

        return new ServerPacketWriter(
            queue,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    static boolean contains(
        byte[] a,
        byte[] n
    ){
        outer:
        for(int i=0;i+n.length<=a.length;i++){
            for(int j=0;j<n.length;j++)
                if(a[i+j]!=n[j])
                    continue outer;
            return true;
        }
        return false;
    }

    static void req(boolean b,String s){
        if(!b)
            throw new AssertionError(s);
    }

    private PlayerNpcMorphAppearanceTest(){}
}

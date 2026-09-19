package spk.local;

import java.io.*;

/** Generic packet-65 dev actor controls remain session-only and remove only dev-owned NPCs. */
public final class DevNpcServiceTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        dev.trace().setEnabled(true);
        NpcRegistry n=new NpcRegistry(dev);
        MovementState m=new MovementState();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
        String s=n.devSpawnNpc(8330,1,0,m,w);
        req(s.contains("DEV_NPC_SPAWN_OK"),s);
        NpcEntity e=n.scene(16382); req(e!=null && e.definitionId==8330,"spawn registry");
        req(n.devNpcInfo(16382,m).contains("source=DEV"),"source");
        req(n.devNpcAnimation(16382,7416,0,w).contains("_OK"),"anim");
        req(n.devNpcGfx(16382,1310,0,0,w).contains("_OK"),"gfx");
        req(n.devNpcText(16382,"SNIPE",w).contains("_OK"),"text");
        req(n.devNpcTarget(16382,32769,w).contains("_OK"),"target");
        req(n.devNpcHit(16382,12,88,100,w).contains("_OK"),"hit");
        req(n.devRemoveNpc(16382,w).contains("_OK"),"remove");
        req(n.scene(16382)==null,"removed registry");
        req(n.devRemoveNpc(NpcRegistry.PET_INDEX,w).contains("NOT_DEV_OWNED"),"ownership fence");
        req(out.size()>0,"wire publication");
        req(dev.trace().size()>=7,"trace coverage "+dev.trace().summary());
        System.out.println("V592_DEV_NPC_SERVICE_PASS spawn=true info=true anim=true gfx=true text=true target=true hit=true remove=true ownershipFence=true packet65Published=true trace=true persistence=NONE_BY_DESIGN");
    }
    static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

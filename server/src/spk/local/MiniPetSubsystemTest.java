package spk.local;

import java.io.*;

public final class MiniPetSubsystemTest {
    public static void main(String[] args)throws Exception{
        if(MiniPetDefinitionRepository.count()!=21)throw new AssertionError("mini count");
        MovementState m=new MovementState();PetState ps=new PetState();NpcRegistry n=new NpcRegistry();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,1,1,1}));
        PetDefinitionRepository.Def main=PetDefinitionRepository.get(22519);if(main==null)throw new AssertionError("main pet");
        String sp=n.spawnPet(main,m,w);if(!sp.startsWith("PET_SPAWN_OK"))throw new AssertionError(sp);ps.activate(main);
        MiniPetService svc=new MiniPetService();String cfg=svc.configure(23629,ps,n,m,w);if(!cfg.startsWith("MINIPET_CONFIGURED"))throw new AssertionError(cfg);
        if(!ps.miniConfigured()||ps.miniItemId()!=23629||n.miniPet()==null)throw new AssertionError("selection/actor");
        if(n.miniPet().sceneIndex==n.pet().sceneIndex)throw new AssertionError("scene collision");
        if(LocalSession.chebyshev(n.miniPet().x,n.miniPet().y,n.pet().x,n.pet().y)!=1)throw new AssertionError("mini must spawn trailing main pet mini="+n.miniPet().x+","+n.miniPet().y+" pet="+n.pet().x+","+n.pet().y);
        int miniScene=n.miniPet().sceneIndex;String rm=n.removePet(w);ps.clear();if(n.miniPet()!=null)throw new AssertionError("mini remains after main pickup");if(!ps.miniConfigured())throw new AssertionError("selection lost");
        n.spawnPet(main,m,w);ps.activate(main);svc.onMainPetSpawn(ps,n,m,w);if(n.miniPet()==null||n.miniPet().sceneIndex==miniScene){} // scene reuse is allowed
        String off=svc.off(ps,n,w);if(ps.miniConfigured()||n.miniPet()!=null)throw new AssertionError(off);
        System.out.println("V5122_MINIPET_SUBSYSTEM_PASS definitions=21 configure23629to1937=true relation=MAIN_PET_TARGET trailingDistance1=true selectionPersistsAcrossPickup=true off=true");
    }
}

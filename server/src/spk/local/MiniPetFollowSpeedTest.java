package spk.local;

import java.io.*;

public final class MiniPetFollowSpeedTest {
    public static void main(String[] args)throws Exception{
        MovementState m=new MovementState(); m.setPersistentRun(true);
        PetState ps=new PetState(); NpcRegistry n=new NpcRegistry(); MiniPetService svc=new MiniPetService();
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{7,7,7,7}));
        PetDefinitionRepository.Def main=PetDefinitionRepository.get(22519); if(main==null)throw new AssertionError("main pet missing");
        n.spawnPet(main,m,w); ps.activate(main); svc.configure(23629,ps,n,m,w);
        // Let the normal main-pet owner-tile egress settle first.
        for(int i=0;i<4 && n.hasQueuedFollow();i++)n.tickFollow(m,w);

        int destX=m.x()+8,destY=m.y();
        String accepted=m.accept(new MovementRequest(164,false,new int[]{destX},new int[]{destY},new byte[0]));
        if(!accepted.startsWith("ACCEPTED"))throw new AssertionError(accepted);
        boolean miniUsedRunBudget=false;
        while(m.queued()>0){
            MovementState.Tick t=m.advance(); n.queueOwnerMovement(t);
            String log=n.tickFollow(m,w);
            if(log!=null && log.matches(".*mini=.*dir2=[0-7].*"))miniUsedRunBudget=true;
        }
        for(int i=0;i<16 && n.hasQueuedFollow();i++){
            String log=n.tickFollow(m,w);
            if(log!=null && log.matches(".*mini=.*dir2=[0-7].*"))miniUsedRunBudget=true;
        }
        int gap=LocalSession.chebyshev(n.miniPet().x,n.miniPet().y,n.pet().x,n.pet().y);
        if(gap!=1)throw new AssertionError("mini did not converge to exact 1-tile gap gap="+gap+" mini="+n.miniPet().x+","+n.miniPet().y+" pet="+n.pet().x+","+n.pet().y);
        if(!miniUsedRunBudget)throw new AssertionError("mini never received two-step budget while main pet/player were running");
        System.out.println("V5123_MINIPET_FOLLOW_SPEED_PASS sameTwoStepBudget=true stationaryGap=1 noTwoTileIdleStop=true");
    }
}

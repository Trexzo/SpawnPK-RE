package spk.local;

import java.io.*;

public final class PetBreadcrumbTrainTest {
    public static void main(String[] args)throws Exception{
        MovementState m=new MovementState();m.setPersistentRun(true);
        NpcRegistry n=new NpcRegistry();PetState ps=new PetState();MiniPetService svc=new MiniPetService();
        ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{5,6,7,8}));
        PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519);if(d==null)throw new AssertionError();
        n.spawnPet(d,m,w);ps.activate(d);svc.configure(23629,ps,n,m,w);
        for(int i=0;i<8&&n.hasQueuedFollow();i++)n.tickFollow(m,w);

        // Route around an L corner. Each follower must consume the actor ahead's
        // vacated route rather than aim geometrically at the final owner tile.
        int[][] dest={{m.x()+3,m.y()},{m.x()+3,m.y()+3},{m.x(),m.y()+3},{m.x(),m.y()}};
        boolean sawMainRun=false,sawMiniRun=false;
        for(int[] dxy:dest){
            String a=m.accept(new MovementRequest(164,false,new int[]{dxy[0]},new int[]{dxy[1]},new byte[0]));
            if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);
            while(m.queued()>0){
                MovementState.Tick t=m.advance();n.queueOwnerMovement(t);String log=n.tickFollow(m,w);
                if(log!=null&&log.contains("movement=RUN"))sawMainRun=true;
                if(log!=null&&log.matches(".*mini=.*dir2=[0-7].*"))sawMiniRun=true;
            }
        }
        for(int i=0;i<32&&n.hasQueuedFollow();i++){String log=n.tickFollow(m,w);if(log!=null&&log.matches(".*mini=.*dir2=[0-7].*"))sawMiniRun=true;}
        int mainGap=LocalSession.chebyshev(n.pet().x,n.pet().y,m.x(),m.y());
        int miniGap=LocalSession.chebyshev(n.miniPet().x,n.miniPet().y,n.pet().x,n.pet().y);
        if(mainGap>1)throw new AssertionError("main not settled behind owner gap="+mainGap);
        if(miniGap!=1)throw new AssertionError("mini not one-tile train gap="+miniGap);
        if(!sawMainRun||!sawMiniRun)throw new AssertionError("expected mirrored catch-up budgets mainRun="+sawMainRun+" miniRun="+sawMiniRun);
        System.out.println("V5125_PET_BREADCRUMB_TRAIN_PASS ownerToMain=true mainToMini=true cardinalComponents=true mainGap="+mainGap+" miniGap="+miniGap+" mirroredRun=true");
    }
}

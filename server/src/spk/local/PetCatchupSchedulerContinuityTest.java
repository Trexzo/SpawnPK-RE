package spk.local;
import java.io.*;
public final class PetCatchupSchedulerContinuityTest{
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState(); NpcRegistry n=new NpcRegistry(); PetState ps=new PetState();
    ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4}));
    n.bootstrap(w,m,ps);
    PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519); n.spawnPet(d,m,w);
    int guard=0; while(n.hasQueuedFollow()&&guard++<20)n.tickFollow(m,w);
    n.pet().x=m.x()-6; n.pet().y=m.y();
    if(n.hasQueuedFollow())throw new AssertionError("fixture must start with empty breadcrumb queues");
    if(!n.needsFollow(m))throw new AssertionError("lagging pet must keep scheduler alive");
    int pulses=0;
    while(n.needsFollow(m)&&pulses<10){
      String r=n.tickFollow(m,w); pulses++;
      if(r==null)throw new AssertionError("null follow at pulse "+pulses);
      if(pulses==1 && LocalSession.chebyshev(n.pet().x,n.pet().y,m.x(),m.y())>1 && !n.needsFollow(m))
        throw new AssertionError("R2.10 one-pulse idle regression returned");
    }
    int dist=LocalSession.chebyshev(n.pet().x,n.pet().y,m.x(),m.y());
    if(dist>1)throw new AssertionError("did not close to adjacency dist="+dist+" pulses="+pulses);
    if(pulses<2)throw new AssertionError("fixture failed to exercise repeated scheduler pulses pulses="+pulses);
    System.out.println("V5131_PET_CATCHUP_SCHEDULER_CONTINUITY_PASS startDist=6 pulses="+pulses+" finalDist="+dist+" breadcrumbQueueCanBeEmpty=true");
  }
}

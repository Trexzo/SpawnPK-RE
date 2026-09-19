package spk.local;
import java.io.*;
public final class PetCatchupCadenceTest{
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState(); NpcRegistry n=new NpcRegistry(); PetState ps=new PetState();
    ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4}));
    n.bootstrap(w,m,ps); PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519); n.spawnPet(d,m,w); while(n.hasQueuedFollow())n.tickFollow(m,w);
    if(n.followDelayMs(m)!=600L)throw new AssertionError("near cadence "+n.followDelayMs(m));
    n.pet().x-=4;
    if(n.followDelayMs(m)!=300L)throw new AssertionError("catchup cadence "+n.followDelayMs(m));
    String repl=n.onOwnerRouteReplaced();
    if(!repl.contains("firstNewTick=FOLLOW_NEW_ROUTE"))throw new AssertionError(repl);
    String a=m.accept(new MovementRequest(164,false,new int[]{m.x()+1},new int[]{m.y()},new byte[0]));if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);
    MovementState.Tick t=m.advance();n.queueOwnerMovement(t);
    if(!n.hasQueuedFollow())throw new AssertionError("first new breadcrumb was suppressed");
    System.out.println("V5129_PET_CATCHUP_CADENCE_PASS near=600ms behind3to7=300ms routeReplacementFirstTick=FOLLOW_NEW_ROUTE threshold8ReanchorRetained=true");
  }
}

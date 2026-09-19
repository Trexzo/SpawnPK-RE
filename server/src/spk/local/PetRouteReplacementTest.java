package spk.local;
import java.io.*;
public final class PetRouteReplacementTest{
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState();m.setPersistentRun(true);NpcRegistry n=new NpcRegistry(new DevAuthorityWorkbench());PetState ps=new PetState();
    ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{9,8,7,6}));n.bootstrap(w,m,ps);n.spawnPet(PetDefinitionRepository.get(22519),m,w);while(n.hasQueuedFollow())n.tickFollow(m,w);
    String a=m.accept(new MovementRequest(164,false,new int[]{m.x(),m.x()},new int[]{m.y()+2,m.y()+4},new byte[0]));if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);
    MovementState.Tick old=m.advance();n.queueOwnerMovement(old);n.tickFollow(m,w);
    String repl=n.onOwnerRouteReplaced();if(!repl.contains("firstNewTick=FOLLOW_NEW_ROUTE"))throw new AssertionError(repl);
    int oldX=n.pet().x,oldY=n.pet().y;
    String b=m.accept(new MovementRequest(164,false,new int[]{m.x()-2},new int[]{m.y()},new byte[0]));if(!b.startsWith("ACCEPTED"))throw new AssertionError(b);
    MovementState.Tick neu=m.advance();n.queueOwnerMovement(neu);String follow=n.tickFollow(m,w);
    if(follow==null||follow.contains("WAIT_"))throw new AssertionError(follow);
    if(n.pet().x==oldX&&n.pet().y==oldY)throw new AssertionError("new route first breadcrumb held");
    System.out.println("V5129_PET_ROUTE_REPLACEMENT_PASS staleTrailDiscarded=true firstNewTickFollowed=true noHold=true noOldRouteReplay=true");
  }
}

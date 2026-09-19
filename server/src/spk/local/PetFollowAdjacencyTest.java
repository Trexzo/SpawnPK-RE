package spk.local;
import java.io.*;
public final class PetFollowAdjacencyTest {
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState();NpcRegistry n=new NpcRegistry();PetState ps=new PetState();
    ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4}));n.bootstrap(w,m,ps);
    PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519);if(d==null)throw new AssertionError("main pet missing");
    String spawn=n.spawnPet(d,m,w);if(!spawn.startsWith("PET_SPAWN_OK"))throw new AssertionError(spawn);while(n.hasQueuedFollow())n.tickFollow(m,w);
    moveOne(m,n,w,m.x()+1,m.y()+1);int bx=n.pet().x,by=n.pet().y;String log=moveOne(m,n,w,m.x()+1,m.y()+1);
    int dx=n.pet().x-bx,dy=n.pet().y-by;if(Math.abs(dx)!=1||Math.abs(dy)!=1)throw new AssertionError("diagonal components "+dx+","+dy+" "+log);
    if(log==null||!log.contains("movement=RUN")||!log.contains("dir1=4")||!log.contains("dir2=1"))throw new AssertionError(log);
    n.pet().x-=4;int before=n.pet().x;moveOwnerWithoutFollow(m,n,m.x()+1,m.y());String fluid=n.tickFollow(m,w);
    if(n.pet().x==before)throw new AssertionError("sub-8 broken trail stalled: "+fluid);
    if(fluid==null||fluid.contains("WAIT_"))throw new AssertionError("sub-8 must not wait: "+fluid);
    n.pet().x-=20;String far=n.tickFollow(m,w);
    if(far==null||!far.contains("TEMP_8_TILE_REANCHOR"))throw new AssertionError(far);
    if(LocalSession.chebyshev(n.pet().x,n.pet().y,m.x(),m.y())>1)throw new AssertionError("far reanchor not adjacent "+far);
    System.out.println("V5128_PET_FOLLOW_RECOVERY_PASS diagonal=DETERMINISTIC_CARDINAL_X_THEN_Y sub8ImmediateWalk=true waitTicks=0 threshold8Temporary=true");
  }
  private static String moveOne(MovementState m,NpcRegistry n,ServerPacketWriter w,int x,int y)throws Exception{String a=m.accept(new MovementRequest(164,false,new int[]{x},new int[]{y},new byte[0]));if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);MovementState.Tick t=m.advance();if(t==null)throw new AssertionError("no tick");n.queueOwnerMovement(t);return n.tickFollow(m,w);}
  private static void moveOwnerWithoutFollow(MovementState m,NpcRegistry n,int x,int y){String a=m.accept(new MovementRequest(164,false,new int[]{x},new int[]{y},new byte[0]));if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);MovementState.Tick t=m.advance();if(t==null)throw new AssertionError("no tick");n.queueOwnerMovement(t);}
}

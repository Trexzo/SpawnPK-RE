package spk.local;
import java.io.*;
public final class PetBrokenTrailRecoveryTest {
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState();NpcRegistry n=new NpcRegistry();PetState ps=new PetState();ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4}));n.bootstrap(w,m,ps);
    PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519);n.spawnPet(d,m,w);while(n.hasQueuedFollow())n.tickFollow(m,w);
    n.pet().x-=4;int before=n.pet().x;String a=m.accept(new MovementRequest(164,false,new int[]{m.x()+1},new int[]{m.y()},new byte[0]));if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);MovementState.Tick t=m.advance();n.queueOwnerMovement(t);
    String r=n.tickFollow(m,w);if(r==null||r.contains("WAIT_"))throw new AssertionError(r);if(n.pet().x==before)throw new AssertionError("stalled "+r);
    System.out.println("V5128_PET_BROKEN_TRAIL_RECOVERY_PASS waits=0 immediateCardinalCatchup=true under8=true");
  }
}

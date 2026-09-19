package spk.local;
import java.io.*;
public final class PetDropFacingEgressTest {
  public static void main(String[] args) throws Exception {
    MovementState m=new MovementState();
    m.setPersistentRun(true);
    NpcRegistry n=new NpcRegistry(new DevAuthorityWorkbench());
    PetState ps=new PetState();
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{4,3,2,1}));
    n.bootstrap(w,m,ps);

    String move=m.accept(new MovementRequest(164,false,new int[]{3089},new int[]{3495},new byte[0]));
    if(!move.startsWith("ACCEPTED")) throw new AssertionError(move);
    MovementState.Tick t=m.advance(); n.queueOwnerMovement(t); // records east-facing even without a pet
    PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519);
    String s1=n.spawnPet(d,m,w);
    if(!s1.contains("stepOutTarget=3090,3495")) throw new AssertionError("drop should egress east with owner facing: "+s1);
    n.tickFollow(m,w);
    if(n.pet().x!=3090 || n.pet().y!=3495) throw new AssertionError("first egress not east: "+n.pet());

    String face=n.preparePetPickupFacing(m,w);
    if(!face.contains("facingTemporary=true")) throw new AssertionError(face);
    n.removePet(w);
    String s2=n.spawnPet(d,m,w);
    if(!s2.contains("stepOutTarget=3090,3495")) throw new AssertionError("pickup must not invert next drop egress: "+s2);
    System.out.println("V5126_PET_DROP_FACING_EGRESS_PASS spawnOwnerTile=true firstStepCardinalWalk=true ownerFacingDirection=true pickupDoesNotInvertNextDrop=true");
  }
}

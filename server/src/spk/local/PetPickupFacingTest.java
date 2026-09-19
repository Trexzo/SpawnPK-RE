package spk.local;
import java.io.*;
public final class PetPickupFacingTest {
  public static void main(String[] args) throws Exception {
    MovementState m=new MovementState();
    DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
    NpcRegistry n=new NpcRegistry(dev);
    PetState ps=new PetState();
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
    n.bootstrap(w,m,ps);
    PetDefinitionRepository.Def d=PetDefinitionRepository.get(22519);
    if(d==null) throw new AssertionError("missing test pet");
    String spawn=n.spawnPet(d,m,w);
    if(!spawn.contains("spawn=OWNER_TILE")) throw new AssertionError(spawn);
    n.tickFollow(m,w); // initial cardinal egress
    String facing=n.preparePetPickupFacing(m,w);
    if(!facing.contains("facingTemporary=true") || facing.contains("facingPersists=true") || facing.contains("nextDropBehind="))
      throw new AssertionError(facing);
    byte[] animTarget=CombatSync.player81AnimationAndInteraction(827,NpcRegistry.PET_INDEX);
    int mask=animTarget[3]&255;
    int anim=(animTarget[4]&255)|((animTarget[5]&255)<<8);
    int target=(animTarget[8]&255)|((animTarget[9]&255)<<8);
    if(mask!=9 || anim!=827 || target!=NpcRegistry.PET_INDEX)
      throw new AssertionError("mask="+mask+" anim="+anim+" target="+target);
    byte[] clear=CombatSync.player81InteractionOnly(-1);
    int clearTarget=(clear[4]&255)|((clear[5]&255)<<8);
    if(clearTarget!=65535) throw new AssertionError("clear target="+clearTarget);
    System.out.println("V5126_PET_PICKUP_FACING_PASS anim827=true facePet=true facingTemporary=true clearTarget=true noNextDropBehind=true noGfx=true");
  }
}

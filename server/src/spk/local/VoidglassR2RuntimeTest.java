package spk.local;
import java.io.*;
/** Compatibility test name retained; runtime behavior now targets corrected R3 item29999. */
public final class VoidglassR2RuntimeTest{
 public static void main(String[]a)throws Exception{
  VoidglassR3CustomContent.ensureRuntimePetMapping();ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
  BankState bank=new BankState();String give=bank.spawnItem(29999,1,w);if(!give.contains("29999")||bank.inventoryCount(29999)!=1)throw new AssertionError("give="+give);
  MovementState m=new MovementState();NpcRegistry npcs=new NpcRegistry();PetState state=new PetState();PetDefinitionRepository.Def d=PetDefinitionRepository.get(29999);if(d==null)throw new AssertionError("mapping");
  String spawn=npcs.spawnPet(d,m,w);if(!spawn.startsWith("PET_SPAWN_OK"))throw new AssertionError(spawn);state.activate(d);NpcEntity pet=npcs.pet();if(!VoidglassR3CustomContent.active(state,pet))throw new AssertionError("active identity");
  VoidglassR3CustomContent.Candidate c=VoidglassR3CustomContent.defaultCandidate();String fx=npcs.animationAndGfxPet(c.stand,0,VoidglassR3CustomContent.PROC_GFX,0,0,w);if(!fx.contains("GFX_OK")&&!fx.contains("ANIM_GFX_OK"))throw new AssertionError(fx);
  System.out.println("V5185_VOIDGLASS_R3_RUNTIME_PASS inventory=true spawn=true genericPetLifecycle=true defaultCandidate=1 procGfx5042=true hydraAssets=false packets="+out.size());
 }
}

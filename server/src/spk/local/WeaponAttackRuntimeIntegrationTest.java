package spk.local;
import java.io.*;

public final class WeaponAttackRuntimeIntegrationTest {
 public static void main(String[] args)throws Exception{
  MovementState m=new MovementState(); PetState ps=new PetState(); DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
  NpcRegistry n=new NpcRegistry(dev); ByteArrayOutputStream raw=new ByteArrayOutputStream();
  ServerPacketWriter w=new ServerPacketWriter(raw,new IsaacCipher(new int[]{7,11,13,17})); n.bootstrap(w,m,ps);
  NpcEntity pvm=n.scene(NpcRegistry.PVM_DUMMY_INDEX); pvm.x=m.x()+5; pvm.y=m.y();

  EquipmentState e=new EquipmentState(); e.setWeapon(11235);
  CombatEngine dark=new CombatEngine(dev); String q=dark.request(pvm,m,11235,System.currentTimeMillis()); req(q.contains("NPC_PVM"),q);
  String d=dark.tick(m,n,e,w,10);
  req(d!=null&&d.contains("attackAnim=15409")&&d.contains("actorGfxCandidate=1111")&&d.contains("actorGfxPublished=true")&&d.contains("projectileCandidate=1120")&&d.contains("faceTarget="+pvm.sceneIndex)&&d.contains("speedTicks=4"),String.valueOf(d));
  req(dark.consumeLastDamage()==200,"dark damage fixture");

  e.setWeapon(20483);
  CombatEngine generic=new CombatEngine(dev); q=generic.request(pvm,m,20483,System.currentTimeMillis()); req(q.contains("NPC_PVM"),q);
  String t=generic.tick(m,n,e,w,20);
  req(t!=null&&t.contains("attackAnim=15409")&&t.contains("actorGfxPublished=false")&&t.contains("faceTarget="+pvm.sceneIndex)&&t.contains("speedTicks=4"),String.valueOf(t));
  req(generic.consumeLastDamage()==200,"generic damage fixture");
  System.out.println("V594_WEAPON_ATTACK_RUNTIME_PASS darkBow11235=anim15409+gfx1111+faceTarget projectile1120_metadata genericTwistedBow20483=anim15409+faceTarget fixtureDamage=true fallback4T=true projectilePublication=DEFERRED");
 }
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

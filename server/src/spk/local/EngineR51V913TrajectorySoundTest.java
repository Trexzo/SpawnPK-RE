package spk.local;
import java.io.*;
public final class EngineR51V913TrajectorySoundTest{
 public static void main(String[]a)throws Exception{
  if(V913WeaponRuntimeAuthority.count()!=11)throw new AssertionError("rows="+V913WeaponRuntimeAuthority.count());
  if(V913WeaponRuntimeAuthority.projectileGeometryCount()!=6)throw new AssertionError("geometry="+V913WeaponRuntimeAuthority.projectileGeometryCount());
  if(V913WeaponRuntimeAuthority.exactBasicSoundCount()!=1)throw new AssertionError("basicSounds="+V913WeaponRuntimeAuthority.exactBasicSoundCount());
  int[] ranged={28539,25557,22218,25001,28860};
  for(int id:ranged){V913WeaponRuntimeAuthority.Profile p=V913WeaponRuntimeAuthority.resolve(id);req(p!=null&&p.hasProjectileGeometry(),"missing geometry "+id);req(p.projectileStartHeight==37&&p.projectileEndHeight==31&&p.projectileSlope==16&&p.projectileStartDistance==64,p.toString());}
  V913WeaponRuntimeAuthority.Profile mystic=V913WeaponRuntimeAuthority.resolve(28843);req(mystic.hasProjectileGeometry()&&mystic.projectileStartHeight==0&&mystic.projectileEndHeight==0&&mystic.projectileSlope==0&&mystic.projectileStartDistance==64,mystic.toString());
  V913WeaponRuntimeAuthority.Profile blood=V913WeaponRuntimeAuthority.resolve(25001);req(blood.hasBasicSound()&&blood.soundId==2695&&blood.soundParam2==0&&blood.soundParam3==10,blood.toString());
  V913WeaponRuntimeAuthority.Profile flames=V913WeaponRuntimeAuthority.resolve(11791);req(!flames.directBasicAttack&&flames.soundId==1655&&!flames.hasBasicSound(),flames.toString());

  MovementState m=new MovementState();PetState ps=new PetState();DevAuthorityWorkbench dev=new DevAuthorityWorkbench();NpcRegistry npcs=new NpcRegistry(dev);
  ByteArrayOutputStream boot=new ByteArrayOutputStream();npcs.bootstrap(new ServerPacketWriter(boot,new IsaacCipher(new int[]{1,2,3,4})),m,ps);
  NpcEntity target=npcs.scene(3);target.x=m.x()+1;target.y=m.y();EquipmentState eq=new EquipmentState();eq.setWeapon(25001);CombatEngine c=new CombatEngine(dev);
  String request=c.request(target,m,25001,System.currentTimeMillis());req(request.contains("NPC_PVM"),request);
  int[] seeds={31,32,33,34};ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(seeds.clone()));
  String hit=c.tick(m,npcs,eq,w,100L);req(hit!=null&&hit.contains("runtimeSound=2695")&&hit.contains("runtimeSoundPublished=true"),String.valueOf(hit));req(hit.contains("projectileGeometry=37/31 slope=16 startDistance=64"),hit);req(hit.contains("projectilePublication=SUPPRESSED_NOT_ALLOWLISTED"),hit);
  byte[] wire=out.toByteArray();IsaacCipher d=new IsaacCipher(seeds.clone());int pos=0;
  int op1=decodeOpcode(wire[pos++]&255,d);req(op1==81,"op1="+op1);int n1=((wire[pos++]&255)<<8)|(wire[pos++]&255);pos+=n1;
  int op2=decodeOpcode(wire[pos++]&255,d);req(op2==174,"op2="+op2);req(pos+6==wire.length,"sound length remaining="+(wire.length-pos));
  int sound=((wire[pos]&255)<<8)|(wire[pos+1]&255),p2=((wire[pos+2]&255)<<8)|(wire[pos+3]&255),p3=((wire[pos+4]&255)<<8)|(wire[pos+5]&255);
  req(sound==2695&&p2==0&&p3==10,"sound payload="+sound+","+p2+","+p3);
  req(hit.contains("M2_PRESENTATION_ONLY_SENT")&&hit.contains("mechanicsAuthority=UNRESOLVED_PRESENTATION_ONLY")&&hit.contains("damage=NOT_APPLIED")&&hit.contains("damageAuthority=NOT_APPLIED")&&hit.contains("hitsplatType=NONE"),hit);
  System.out.println("V5160_R51_V913_TRAJECTORY_SOUND_PASS profiles=11 geometry=6 rangedGeometry=37/31/16/64 mysticGeometry=0/0/0/64 bloodSound174=2695/0/10 presentationOnly=true damageNotInvented=true projectile117StillGatedForBloodCbow=true flamesSoundNotBasic=true");
 }
 static int decodeOpcode(int enc,IsaacCipher d){return (enc-d.nextInt())&255;} static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
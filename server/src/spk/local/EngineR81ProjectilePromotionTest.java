package spk.local;
import java.io.*;import java.lang.reflect.*;import java.util.*;
public final class EngineR81ProjectilePromotionTest{
 public static void main(String[]a)throws Exception{
  V913WeaponRuntimeAuthority.Profile web=V913WeaponRuntimeAuthority.resolve(25557),sc=V913WeaponRuntimeAuthority.resolve(28860),blood=V913WeaponRuntimeAuthority.resolve(25001);
  req(V913LiveProjectilePublisher.enabled(web),"webweaver gate");req(V913LiveProjectilePublisher.enabled(sc),"scorch gate");req(!V913LiveProjectilePublisher.enabled(blood),"blood cbow must stay gated");
  req(V913LiveProjectilePublisher.endDelay(web,4)==14,"web flight");req(V913LiveProjectilePublisher.endDelay(sc,1)==9,"scorch near");req(V913LiveProjectilePublisher.endDelay(sc,4)==14,"scorch far");req(V913LiveProjectilePublisher.startDelay(sc)==0,"start delay policy");
  NpcRegistry npcs=new NpcRegistry();MovementState m=new MovementState();Field vf=NpcRegistry.class.getDeclaredField("visible");vf.setAccessible(true);@SuppressWarnings("unchecked") ArrayList<NpcEntity> v=(ArrayList<NpcEntity>)vf.get(npcs);NpcEntity dummy=new NpcEntity(NpcRegistry.PVM_DUMMY_INDEX,1488,m.x()+4,m.y());v.add(dummy);
  int[] seed={31,37,41,43};ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(seed.clone()));SceneUpdatePublisher pub=new SceneUpdatePublisher(w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));
  String result=RuntimeWeaponPresentationLab.preview(sc,npcs,m,pub,w);w.flush();req(result.contains("PUBLISHED id=4079"),result);
  byte[] wire=out.toByteArray();IsaacCipher dec=new IsaacCipher(seed.clone());int pos=0;boolean saw81=false,saw117=false;int proj=-1,lock=-1,start=-1,end=-1,slope=-1,startDist=-1;
  while(pos<wire.length){int op=((wire[pos++]&255)-dec.nextInt())&255;if(op==81){saw81=true;int n=((wire[pos]&255)<<8)|(wire[pos+1]&255);pos+=2+n;}else if(op==85){pos+=2;}else if(op==117){saw117=true;int p0=pos;pos++;pos+=2;lock=(short)(((wire[pos]&255)<<8)|(wire[pos+1]&255));pos+=2;proj=((wire[pos]&255)<<8)|(wire[pos+1]&255);pos+=2;pos+=2;start=((wire[pos]&255)<<8)|(wire[pos+1]&255);pos+=2;end=((wire[pos]&255)<<8)|(wire[pos+1]&255);pos+=2;slope=wire[pos++]&255;startDist=wire[pos++]&255;req(pos-p0==15,"117 length");}else throw new AssertionError("unexpected opcode "+op);}
  req(saw81&&saw117,"wire 81="+saw81+" 117="+saw117);req(proj==4079,"proj="+proj);req(lock==dummy.sceneIndex+1,"lock="+lock+" expected="+(dummy.sceneIndex+1));req(start==0&&end==14&&slope==16&&startDist==64,"timing/geometry "+start+"/"+end+"/"+slope+"/"+startDist);
  System.out.println("V5181_ENGINE_R81_PROJECTILE_PROMOTION_PASS webweaver4057=true scorching4079=true observedWindows=14/9-14 startDelay0ExplicitLocal=true packet117=true lockonScenePlus1=true otherProfilesFailClosed=true");
 }
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

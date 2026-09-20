package spk.local;
import java.io.*;import java.net.*;import java.lang.reflect.*;
public final class EngineR6RegionProjectionTest{
 public static void main(String[]a)throws Exception{
  World world=World.isolatedForTest(600L); LocalSession s=new LocalSession(new Socket(),true,true,world);
  WorldPlayer wp=(WorldPlayer)get(s,"worldPlayer");MovementState m=(MovementState)get(s,"movement");NpcRegistry npcs=(NpcRegistry)get(s,"npcs");PetState pet=(PetState)get(s,"petState");
  LocalRegionDevCommandHandler regions=(LocalRegionDevCommandHandler)get(s,"regionDevCommands");
  world.registerPlayer(wp,"r6projection");
  ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{81,82,83,84}));
  npcs.bootstrapHome(w,m,pet,new HomeWorldRuntimePlan());w.flush();int before=npcs.visibleCount();req(before>20,"home npcs="+before);out.reset();
  LocalRegionDevCommandHandler.Result er=regions.handle(new String[]{"regionload","16193","0"},"r6projection",null,w);w.flush();req(er!=null&&er.detailText.startsWith("OK region=16193"),er==null?"null":er.logText);req(er.detailText.contains("removedHomeNpcView="+before),er.detailText);req(m.transientRegion(),"not transient");req(m.x()==4064&&m.y()==4192,"unexpected deterministic landing="+m.x()+","+m.y());req(out.size()>0,"no transition packets");
  int serverCountDuring=npcs.visibleCount();req(serverCountDuring==before,"server registry mutated entering external region");
  out.reset();LocalRegionDevCommandHandler.Result hr=regions.handle(new String[]{"regionhome"},"r6projection",er.scenePublisher,w);w.flush();req(hr!=null&&hr.detailText.startsWith("OK world="+MovementState.INITIAL_X+","+MovementState.INITIAL_Y),hr==null?"null":hr.logText);req(hr.detailText.contains("npcRepublish="+before),hr.detailText);req(m.inHomeWindow(),"did not restore HOME window");req(npcs.visibleCount()==before,"npc registry duplicated on return before="+before+" after="+npcs.visibleCount());
  world.unregisterPlayer(wp);world.close();
  System.out.println("V5160_ENGINE_R6_REGION_PROJECTION_PASS region=16193 landing=4064,4192 homeNpcViewRemoved="+before+" serverRegistryPreserved=true homeNpcRepublished="+before+" multiplayerGate=true nonpersistent=true");
 }
 static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
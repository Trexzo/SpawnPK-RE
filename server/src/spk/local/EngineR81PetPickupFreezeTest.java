package spk.local;
import java.net.*;import java.lang.reflect.*;
public final class EngineR81PetPickupFreezeTest{
 public static void main(String[]a)throws Exception{
  LocalSession s=new LocalSession(new Socket(),true,true,World.isolatedForTest(600L));NpcRegistry n=(NpcRegistry)get(s,"npcs");
  Method f=LocalSession.class.getDeclaredMethod("freezePetFollowForPickup",String.class);f.setAccessible(true);Method u=LocalSession.class.getDeclaredMethod("releasePetFollowAfterPickup",String.class,String.class);u.setAccessible(true);
  req(!n.followFrozen(),"initial frozen");f.invoke(s,"[test] ");req(n.followFrozen(),"freeze not applied");req((Boolean)get(s,"petPickupOwnedFollowFreeze"),"ownership not recorded");
  u.invoke(s,"[test] ","TEST_RELEASE");req(!n.followFrozen(),"release not applied");req(!(Boolean)get(s,"petPickupOwnedFollowFreeze"),"ownership not cleared");
  n.devFollowFreeze(true);f.invoke(s,"[test] ");req(n.followFrozen(),"preexisting freeze lost");req(!(Boolean)get(s,"petPickupOwnedFollowFreeze"),"must not own preexisting freeze");u.invoke(s,"[test] ","NOOP");req(n.followFrozen(),"preexisting freeze incorrectly released");
  System.out.println("V5181_ENGINE_R81_PET_PICKUP_FREEZE_PASS deferredPickupOwnsFollowFreeze=true completionReleasesOwnedFreeze=true preexistingFreezePreserved=true");
 }
 static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

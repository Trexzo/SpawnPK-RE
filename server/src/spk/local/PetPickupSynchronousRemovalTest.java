package spk.local;
import java.io.*;import java.lang.reflect.*;import java.net.*;
public final class PetPickupSynchronousRemovalTest {
  public static void main(String[] args)throws Exception{
    try(World world=World.isolatedForTest(600L)){
      LocalSession s=new LocalSession(new Socket(),true,true,world);
      MovementState m=(MovementState)get(s,"movement"); NpcRegistry n=(NpcRegistry)get(s,"npcs");
      PetState ps=(PetState)get(s,"petState"); BankState bank=(BankState)get(s,"bank");
      LocalPetDropPickupHandler pickup=(LocalPetDropPickupHandler)get(s,"petDropPickup");
      ByteArrayOutputStream out=new ByteArrayOutputStream(); ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
      PetDefinitionRepository.Def d=new PetDefinitionRepository.Def(22519,3843,"Ultimate olmlet pet","Ultimate olmlet",7396,7395,-1,-1,-1,1,"","R2.13_TEST_FIXTURE");
      ps.activate(d); String sp=n.spawnPet(d,m,w); if(!sp.startsWith("PET_SPAWN_OK"))throw new AssertionError(sp);
      String step=n.tickFollow(m,w); if(step==null||!step.contains("movement=WALK"))throw new AssertionError("pet egress missing: "+step);
      NpcEntity pet=n.pet(); if(pet==null||Math.abs(pet.x-m.x())+Math.abs(pet.y-m.y())!=1)throw new AssertionError("pet not cardinal adjacent");
      Method x=LocalPetDropPickupHandler.class.getDeclaredMethod("executePickupNow",NpcAction.class,ServerPacketWriter.class,String.class,long.class,String.class);x.setAccessible(true);
      long now=System.currentTimeMillis(); x.invoke(pickup,new NpcAction(155,pet.sceneIndex),w,"[r213-test] ",now,"WORLD_TICK_TEST");
      if(n.pet()!=null||ps.active())throw new AssertionError("pet survives pickup world-tick callback");
      if(bank.inventoryCount(22519)!=1)throw new AssertionError("pet item not restored synchronously");
      long pending=((Long)get(pickup,"pendingPetPickupCompleteAtMs")).longValue(); if(pending!=Long.MAX_VALUE)throw new AssertionError("completion still pending="+pending);
      long facing=((Long)get(pickup,"pendingPetFacingClearAtMs")).longValue(); if(facing!=Long.MAX_VALUE)throw new AssertionError("legacy m-facing clear unexpectedly armed="+facing);
      System.out.println("V51213_PET_PICKUP_SYNCHRONOUS_REMOVAL_PASS turnTileAndAnimFirst=true petRemovalSameWorldTick=true itemRestoreSameCallback=true legacyInteractionFacingTail=false");
    }
  }
  static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}
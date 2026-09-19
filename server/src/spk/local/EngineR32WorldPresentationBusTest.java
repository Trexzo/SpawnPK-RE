package spk.local;
import java.io.*;import java.lang.reflect.*;import java.util.*;
public final class EngineR32WorldPresentationBusTest{
 public static void main(String[]a)throws Exception{
  testExactParticleSpawn();
  testRemotePetSnapshotAndBarrier();
  System.out.println("V5132_R32_WORLD_PRESENTATION_BUS_PASS particleSelector=true nativeChargeSnapshot=true nativeChargeRelay=true hitAfterSwing=true");
 }
 static void testExactParticleSpawn()throws Exception{
  NpcRegistry n=new NpcRegistry(new DevAuthorityWorkbench());MovementState m=new MovementState();OutboundPacketQueue q=new OutboundPacketQueue();ServerPacketWriter w=new ServerPacketWriter(q,new IsaacCipher(new int[]{1,2,3,4}));
  int x=m.x()-1,y=m.y();NpcEntity e=n.spawnMirroredNpc(5163,x,y,Integer.valueOf(8),m,w);ByteArrayOutputStream got=new ByteArrayOutputStream();q.drainTo(got,1<<20);byte[] framed=got.toByteArray();if(framed.length<4)throw new AssertionError("missing mirrored add");int len=((framed[1]&255)<<8)|(framed[2]&255);byte[] body=Arrays.copyOfRange(framed,3,3+len);
  NpcEntity expected=new NpcEntity(e.sceneIndex,5163,x,y);Map<Integer,NpcSpawnPresentation> p=Collections.singletonMap(e.sceneIndex,NpcSpawnPresentation.particle(8));byte[] exp=NpcSyncEncoder.encode(Collections.<NpcSyncEncoder.Update>emptyList(),Collections.singletonList(expected),m.x(),m.y(),p);if(!Arrays.equals(body,exp))throw new AssertionError("particle presentation mismatch");
 }
 static void testRemotePetSnapshotAndBarrier()throws Exception{
  World world=World.isolatedForTest(600L);WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();world.registerPlayer(a,"opensrc");world.registerPlayer(b,"src");
  OutboundPacketQueue qa=new OutboundPacketQueue(),qb=new OutboundPacketQueue();ServerPacketWriter wa=new ServerPacketWriter(qa,new IsaacCipher(new int[]{11,12,13,14})),wb=new ServerPacketWriter(qb,new IsaacCipher(new int[]{21,22,23,24}));
  Player81WorldSync.register(wa,world,a,new DevAuthorityWorkbench());Player81WorldSync.register(wb,world,b,new DevAuthorityWorkbench());
  DevAuthorityWorkbench da=new DevAuthorityWorkbench(),db=new DevAuthorityWorkbench();NpcRegistry na=new NpcRegistry(da),nb=new NpcRegistry(db);MovementState ma=new MovementState(),mb=new MovementState();SharedNpcWorldRelay.register(wa,world,a,na,ma);SharedNpcWorldRelay.register(wb,world,b,nb,mb);
  try{
   wa.varShort(81,BootstrapPackets.player81Idle());wb.varShort(81,BootstrapPackets.player81Idle());
   PetDefinitionRepository.Def pd=PetDefinitionRepository.get(24019);na.spawnPet(pd,ma,wa);na.devSetParticleSelector(Integer.valueOf(8),ma,wa);na.setPetNativeState(3,wa);
   SharedNpcWorldRelay.syncRemotePets(wb);NpcEntity remote=findDef(nb,pd.npcId);if(remote==null)throw new AssertionError("remote pet absent");
   ByteArrayOutputStream snap=new ByteArrayOutputStream();qb.drainTo(snap,1<<20);String raw=new String(snap.toByteArray(),"ISO-8859-1");if(raw.indexOf("3\n")<0)throw new AssertionError("native charge snapshot missing");
   // Live state change must wait until viewer receives source packet81 presentation.
   wa.varShort(81,CombatSync.player81AnimationOnly(15552));int before=qb.queuedBytes();na.setPetNativeState(2,wa);if(qb.queuedBytes()!=before)throw new AssertionError("pet state overtook player event");wb.varShort(81,BootstrapPackets.player81Idle());if(qb.queuedBytes()<=before)throw new AssertionError("pet state not released after player event");
   // HOME dummy hit uses the same barrier.
   NpcEntity d1=new NpcEntity(131,1488,ma.x()+1,ma.y()),d2=new NpcEntity(131,1488,mb.x()+1,mb.y());addVisible(na,d1);addVisible(nb,d2);wa.varShort(81,CombatSync.player81AnimationAndInteraction(15552,131));int hb=qb.queuedBytes();na.sendMask(d1,NpcSyncEncoder.Mask.singleHit(100,6,255,255),wa);if(qb.queuedBytes()!=hb)throw new AssertionError("hit overtook swing");wb.varShort(81,BootstrapPackets.player81Idle());if(qb.queuedBytes()<=hb)throw new AssertionError("hit missing after swing");
  }finally{SharedNpcWorldRelay.unregister(wa);SharedNpcWorldRelay.unregister(wb);Player81WorldSync.unregister(wa);Player81WorldSync.unregister(wb);world.unregisterPlayer(a);world.unregisterPlayer(b);world.close();}
 }
 static NpcEntity findDef(NpcRegistry n,int def){for(NpcEntity e:n.snapshot())if(e.definitionId==def)return e;return null;}
 @SuppressWarnings("unchecked") static void addVisible(NpcRegistry n,NpcEntity e)throws Exception{Field f=NpcRegistry.class.getDeclaredField("visible");f.setAccessible(true);((ArrayList<NpcEntity>)f.get(n)).add(e);}
}

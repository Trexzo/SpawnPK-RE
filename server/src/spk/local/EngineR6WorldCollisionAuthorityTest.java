package spk.local;
import java.util.*;
public final class EngineR6WorldCollisionAuthorityTest{
 public static void main(String[]a){
  req(WorldCollisionAuthority.regionCount()==1279,"regions="+WorldCollisionAuthority.regionCount());
  req(WorldCollisionAuthority.entryCount()==4233244,"entries="+WorldCollisionAuthority.entryCount());
  WorldRegionAuthorityRepository.Region arax=WorldRegionAuthorityRepository.get(16193);
  req(arax!=null&&arax.mapPresent&&arax.landPresent&&arax.terrainParseOk&&arax.objectParseOk,"arax coverage="+arax);
  Tile safe=WorldCollisionAuthority.safeTile(16193,0);req(safe!=null,"arax safe tile");
  req(!WorldCollisionAuthority.blockedTile(safe.x,safe.y,safe.plane),"safe tile blocked "+safe);
  req(WorldCollisionAuthority.maskAt(1635,4816,0)==8,"known wall source mask="+WorldCollisionAuthority.maskAt(1635,4816,0));
  req(WorldCollisionAuthority.maskAt(1636,4816,0)==128,"known wall dest mask="+WorldCollisionAuthority.maskAt(1636,4816,0));
  req(!WorldCollisionAuthority.canStep(1635,4816,0,1636,4816),"wall crossing allowed");
  MovementState m=new MovementState();int bx=((1635>>3)-6)<<3,by=((4816>>3)-6)<<3;m.enterTransientRegion(1635,4816,0,bx,by);
  String blocked=m.accept(new MovementRequest(164,false,new int[]{1636},new int[]{4816},new byte[0]));req(blocked.startsWith("REJECT_STATIC_COLLISION"),blocked);
  SortedMap<String,String> p=PersistenceSchemaTestSupport.captureMovement(m);req(Integer.parseInt(p.get("movement.worldX"))==MovementState.INITIAL_X,"transient x persisted");req(Integer.parseInt(p.get("movement.worldY"))==MovementState.INITIAL_Y,"transient y persisted");
  m.returnHome();req(m.inHomeWindow()&&m.x()==MovementState.INITIAL_X&&m.y()==MovementState.INITIAL_Y,"return home failed");
  System.out.println("V5160_ENGINE_R6_WORLD_COLLISION_AUTHORITY_PASS regions=1279 entries=4233244 araxxorSafe="+safe.x+","+safe.y+" knownWall=8/128 blocked=true transientPersistence=HOME_FALLBACK");
 }
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

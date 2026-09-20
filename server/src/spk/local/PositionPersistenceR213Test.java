package spk.local;
import java.util.*;
public final class PositionPersistenceR213Test{
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState();
    MovementRequest r=new MovementRequest(164,false,new int[]{3099},new int[]{3502},new byte[0]);
    String a=m.accept(r);if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);
    while(m.advance()!=null){}
    if(m.x()!=3099||m.y()!=3502)throw new AssertionError("movement fixture failed "+m.x()+","+m.y());
    SortedMap<String,String> p=PersistenceSchemaTestSupport.captureMovement(m);
    if(!"3099".equals(p.get("movement.worldX"))||!"3502".equals(p.get("movement.worldY")))throw new AssertionError(p.toString());
    MovementState restored=new MovementState();PersistenceSchemaTestSupport.restoreMovement(restored,p);
    if(restored.x()!=3099||restored.y()!=3502||restored.plane()!=0)throw new AssertionError("restore="+restored.x()+","+restored.y()+","+restored.plane());
    SortedMap<String,String> old=PersistenceSchemaTestSupport.values("movement.runEnergy","88");
    MovementState legacy=new MovementState();PersistenceSchemaTestSupport.restoreMovement(legacy,old);
    if(legacy.x()!=MovementState.INITIAL_X||legacy.y()!=MovementState.INITIAL_Y)throw new AssertionError("legacy profile fallback broken");
    SortedMap<String,String> bad=PersistenceSchemaTestSupport.values("movement.worldX","999999","movement.worldY","999999","movement.plane","3");
    MovementState failClosed=new MovementState();PersistenceSchemaTestSupport.restoreMovement(failClosed,bad);
    if(failClosed.x()!=MovementState.INITIAL_X||failClosed.y()!=MovementState.INITIAL_Y||failClosed.plane()!=0)throw new AssertionError("corrupt position did not fail closed");
    System.out.println("V51213_POSITION_PERSISTENCE_PASS x=3099 y=3502 plane=0 legacyProfileSpawnFallback=true invalidFailClosed=true");
  }
}

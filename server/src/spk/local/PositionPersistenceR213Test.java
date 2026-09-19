package spk.local;
import java.util.*;
public final class PositionPersistenceR213Test{
  public static void main(String[] args)throws Exception{
    MovementState m=new MovementState();
    MovementRequest r=new MovementRequest(164,false,new int[]{3099},new int[]{3502},new byte[0]);
    String a=m.accept(r);if(!a.startsWith("ACCEPTED"))throw new AssertionError(a);
    while(m.advance()!=null){}
    if(m.x()!=3099||m.y()!=3502)throw new AssertionError("movement fixture failed "+m.x()+","+m.y());
    Properties p=new Properties();m.saveAccountProperties(p);
    if(!"3099".equals(p.getProperty("movement.worldX"))||!"3502".equals(p.getProperty("movement.worldY")))throw new AssertionError(p.toString());
    MovementState restored=new MovementState();restored.loadAccountProperties(p);
    if(restored.x()!=3099||restored.y()!=3502||restored.plane()!=0)throw new AssertionError("restore="+restored.x()+","+restored.y()+","+restored.plane());
    Properties old=new Properties();old.setProperty("movement.runEnergy","88");
    MovementState legacy=new MovementState();legacy.loadAccountProperties(old);
    if(legacy.x()!=MovementState.INITIAL_X||legacy.y()!=MovementState.INITIAL_Y)throw new AssertionError("legacy profile fallback broken");
    Properties bad=new Properties();bad.setProperty("movement.worldX","999999");bad.setProperty("movement.worldY","999999");bad.setProperty("movement.plane","3");
    MovementState failClosed=new MovementState();failClosed.loadAccountProperties(bad);
    if(failClosed.x()!=MovementState.INITIAL_X||failClosed.y()!=MovementState.INITIAL_Y||failClosed.plane()!=0)throw new AssertionError("corrupt position did not fail closed");
    System.out.println("V51213_POSITION_PERSISTENCE_PASS x=3099 y=3502 plane=0 legacyProfileSpawnFallback=true invalidFailClosed=true");
  }
}

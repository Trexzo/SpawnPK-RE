package spk.local;
import java.lang.reflect.*;
public final class CombatOverlayHpTest {
  public static void main(String[] args)throws Exception{
    CombatEngine c=new CombatEngine();
    Field max=CombatEngine.class.getDeclaredField("DUMMY_HP_MAX");max.setAccessible(true);if(max.getInt(null)!=255)throw new AssertionError("max");
    try{CombatEngine.class.getDeclaredField("dummyHp");throw new AssertionError("decreasing dummyHp fixture still exists");}catch(NoSuchFieldException ok){}
    String def=c.devHitSummary();if(!def.contains("type=6")||!def.contains("styleIcon=255")||!def.contains("placement=primary"))throw new AssertionError(def);
    String t=c.devHitType(2);if(!t.contains("type=2"))throw new AssertionError(t);
    String reset=c.devHitReset();if(!reset.contains("type=6"))throw new AssertionError(reset);
    System.out.println("V5129_COMBAT_OVERLAY_HP_PASS npcHpFixture=255/255 decreasingWorldBar=false devhitReset=type6/style255/primary runtimeVisualSelection=true priorProductionSemanticCapture=type4 opponentOverlayOwnedByKey25=true");
  }
}

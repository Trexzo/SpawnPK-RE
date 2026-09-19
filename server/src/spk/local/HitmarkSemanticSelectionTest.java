package spk.local;
import java.lang.reflect.*;
public final class HitmarkSemanticSelectionTest{
  public static void main(String[] args)throws Exception{
    CombatEngine c=new CombatEngine();
    Method m=CombatEngine.class.getDeclaredMethod("effectiveHitType",int.class,int.class);m.setAccessible(true);
    int normal=((Integer)m.invoke(c,37,100)).intValue();
    int max=((Integer)m.invoke(c,100,100)).intValue();
    if(normal!=1||max!=6)throw new AssertionError("auto semantic normal="+normal+" max="+max);
    String forced=c.devHitCommand(new String[]{"devhit","type","5"});
    if(!forced.contains("variantMode=manual"))throw new AssertionError(forced);
    int manual=((Integer)m.invoke(c,37,100)).intValue();if(manual!=5)throw new AssertionError("manual="+manual);
    String auto=c.devHitCommand(new String[]{"devhit","variant","auto"});
    if(!auto.contains("auto(normal=1,max=6)"))throw new AssertionError(auto);
    System.out.println("V5131_HITMARK_SEMANTIC_SELECTION_PASS exactClientBasic=type1 exactClientCritMax=type6 fixtureNormal37=1 fixtureMax100=6 manualOverride=true");
  }
}

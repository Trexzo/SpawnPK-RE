package spk.local;
public final class DevHitDamageLabTest{
  public static void main(String[] args){
    CombatEngine c=new CombatEngine();
    String a=c.devHitCommand(new String[]{"devhit","damage","37"});
    if(!a.contains("fixed:37"))throw new AssertionError(a);
    String b=c.devHitCommand(new String[]{"devhit","sequence","37,100"});
    if(!b.contains("sequence[37, 100]"))throw new AssertionError(b);
    String reset=c.devHitCommand(new String[]{"devhit","reset"});
    if(!reset.contains("legacy_context_fixture")||!reset.contains("type=6")||!reset.contains("auto(normal=1,max=6)"))throw new AssertionError(reset);
    System.out.println("V5131_DEVHIT_DAMAGE_LAB_PASS fixed37=true sequence37_100=true typeIndependent=true resetAutoSemantic=true");
  }
}

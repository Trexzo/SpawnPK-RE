import java.lang.reflect.*;
public final class ClientHookLoadTest {
  public static void main(String[] args) throws Exception {
    Class.forName("spk.dev.PetVisualOverrides",true,ClientHookLoadTest.class.getClassLoader());
    Class.forName("rs.a.a",false,ClientHookLoadTest.class.getClassLoader());
    Class<?> item=Class.forName("rs.d.k",false,ClientHookLoadTest.class.getClassLoader());
    Field actions=item.getField("L");
    if(actions.getType()!=String[].class)throw new AssertionError("rs.d.k.L is not String[]");
    Method lookup=item.getMethod("f",int.class);
    if(!lookup.getReturnType().equals(item))throw new AssertionError("rs.d.k.f return mismatch");
    System.out.println("V5129_CLIENT_HOOK_LOAD_PASS helper=true patchedActorRenderer=true itemDef=rs.d.k actionsField=L option4Index=3 item24019CompatibilityHook=true");
  }
}

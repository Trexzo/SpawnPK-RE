public final class ClientHookLoadTest {
  public static void main(String[] args) throws Exception {
    Class.forName("spk.dev.PetVisualOverrides",true,ClientHookLoadTest.class.getClassLoader());
    Class.forName("rs.a.a",false,ClientHookLoadTest.class.getClassLoader());
    System.out.println("V5124_CLIENT_VISUAL_HOOK_LOAD_PASS helper=true patchedActorRenderer=true");
  }
}

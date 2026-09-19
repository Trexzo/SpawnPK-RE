package spk.local;
import java.net.*;
import java.lang.reflect.*;
import java.nio.file.*;
public final class DialogNumberKeyStateTest{
  public static void main(String[] args)throws Exception{
    Path f=Paths.get("server","data","locallab_dialog_keys.properties");
    Files.deleteIfExists(f);
    LocalSession s=new LocalSession(new Socket(),true,false,World.isolatedForTest(600L));
    Method open=LocalSession.class.getDeclaredMethod("publishDialogNumberKeys",int[].class);open.setAccessible(true);
    Method clear=LocalSession.class.getDeclaredMethod("clearDialogNumberKeys");clear.setAccessible(true);
    open.invoke(s,new Object[]{new int[]{2482,2483,2484,2485}});
    String txt=new String(Files.readAllBytes(f),java.nio.charset.StandardCharsets.UTF_8);
    if(!txt.contains("active=true")||!txt.contains("widgets=2482,2483,2484,2485"))throw new AssertionError(txt);
    clear.invoke(s);
    txt=new String(Files.readAllBytes(f),java.nio.charset.StandardCharsets.UTF_8);
    if(!txt.contains("active=false")||!txt.contains("widgets="))throw new AssertionError(txt);
    Files.deleteIfExists(f);
    System.out.println("V5131_DIALOG_NUMBER_KEY_STATE_PASS ranks1to4=2482..2485 genericCapacity=9 runtimeOnly=true");
  }
}

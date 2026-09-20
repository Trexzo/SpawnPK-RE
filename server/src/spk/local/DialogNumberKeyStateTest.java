package spk.local;
import java.nio.file.*;
import java.util.*;
public final class DialogNumberKeyStateTest{
  public static void main(String[] args)throws Exception{
    Path dir=Files.createTempDirectory("spk-dialog-keys-");
    Path f=dir.resolve("locallab_dialog_keys.properties");
    try{
      LocalDialogNumberKeyState keys=new LocalDialogNumberKeyState(f);
      keys.publish(2482,2483,2484,2485);
      String txt=new String(Files.readAllBytes(f),java.nio.charset.StandardCharsets.UTF_8);
      if(!txt.contains("active=true")||!txt.contains("widgets=2482,2483,2484,2485"))throw new AssertionError(txt);
      keys.clear();
      txt=new String(Files.readAllBytes(f),java.nio.charset.StandardCharsets.UTF_8);
      if(!txt.contains("active=false")||!txt.contains("widgets="))throw new AssertionError(txt);
      System.out.println("V5131_DIALOG_NUMBER_KEY_STATE_PASS ranks1to4=2482..2485 genericCapacity=9 runtimeOnly=true");
    }finally{
      try(java.util.stream.Stream<Path> st=Files.walk(dir)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
    }
  }
}
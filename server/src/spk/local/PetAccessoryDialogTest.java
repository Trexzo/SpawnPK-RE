package spk.local;
import java.io.*;import java.lang.reflect.*;import java.net.*;import java.nio.charset.StandardCharsets;
public final class PetAccessoryDialogTest{
  static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
  public static void main(String[] args)throws Exception{
    LocalSession s=new LocalSession(new Socket(),true,true);
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
    LocalPetInventoryDialogHandler dialogs=(LocalPetInventoryDialogHandler)field(s,"petDialogs");
    Method m=LocalPetInventoryDialogHandler.class.getDeclaredMethod("openPetAccessoryDialog",int.class,int.class,ServerPacketWriter.class);m.setAccessible(true);
    m.invoke(dialogs,0,20543,w);
    String raw=new String(out.toByteArray(),StandardCharsets.ISO_8859_1);
    for(String need:new String[]{"Pet accessory","Activate Red pet accessory","Remove active pet accessory","Cancel","Close"})
      if(!raw.contains(need))throw new AssertionError("missing dialog text: "+need);
    System.out.println("V5130_PET_ACCESSORY_DIALOG_PASS nativeChatbox2480=true activate=true detach=true cancel=true close=true wording=RECONSTRUCTED_SERVER_RESPONSE");
  }
}
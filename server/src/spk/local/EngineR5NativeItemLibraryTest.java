package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class EngineR5NativeItemLibraryTest {
 public static void main(String[]args)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));NativeItemLibraryService s=new NativeItemLibraryService();
  boolean closedFailure=false;
  try{s.open(failingWriter(),28860);}catch(IOException expected){closedFailure=true;}
  if(!closedFailure||s.isOpen()||s.selectedItem()!=-1)throw new AssertionError("closed failed open committed state open="+s.isOpen()+" selected="+s.selectedItem());
  String open=s.open(w,28860);w.flush();byte[] a=out.toByteArray();has(a,"ITEM_GUIDE_RESET_PREVIEW\n");has(a,"ITEM_GUIDE_SET_ITEM_SPRITE\n");has(a,"Scorching bow (i)\n");has(a,"+1 attack speed");has(a,"anim 15409");
  if(!s.isOpen()||s.selectedItem()!=28860||!open.contains("root=47500"))throw new AssertionError(open);
  out.reset();String bonus=s.handleWidget(w,47816);w.flush();byte[] b=out.toByteArray();has(b,"ITEM_GUIDE_BONUS_WIDGET ON\n");has(b,"N/A\n");if(!bonus.contains("FAIL_CLOSED_NA"))throw new AssertionError(bonus);
  boolean replacementFailure=false;
  try{s.open(failingWriter(),28539);}catch(IOException expected){replacementFailure=true;}
  if(!replacementFailure||!s.isOpen()||s.selectedItem()!=28860)throw new AssertionError("failed replacement changed state open="+s.isOpen()+" selected="+s.selectedItem());
  boolean sameItemFailure=false;
  try{s.open(failingWriter(),28860);}catch(IOException expected){sameItemFailure=true;}
  if(!sameItemFailure||!s.isOpen()||s.selectedItem()!=28860)throw new AssertionError("failed same-item reopen changed state open="+s.isOpen()+" selected="+s.selectedItem());
  out.reset();String search=s.searchExact(w,"Tumeken's shadow (i)");w.flush();byte[] c=out.toByteArray();has(c,"Tumeken's shadow (i)\n");has(c,"projectile 5092");if(s.selectedItem()!=28539)throw new AssertionError("igsearch selected="+s.selectedItem());
  System.out.println("V5150_ENGINE_R5_NATIVE_ITEM_LIBRARY_PASS root47500=true igsearchExactName=true staticDescription=true v913RuntimeJoin=true bonusUnknownFailClosed=true searchPromptNotInvented=true targetStateFailureAtomic=true");
 }
 static ServerPacketWriter failingWriter(){
  return new ServerPacketWriter(
   new OutputStream(){@Override public void write(int b)throws IOException{throw new IOException("EXPECTED_ITEM_LIBRARY_WRITE_FAILURE");}},
   new IsaacCipher(new int[]{1,2,3,4})
  );
 }
 static void has(byte[]b,String x){byte[]n=x.getBytes(StandardCharsets.ISO_8859_1);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return;}throw new AssertionError("missing "+x+" bytes="+b.length);}
}

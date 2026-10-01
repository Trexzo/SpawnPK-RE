package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class EngineR5NativeItemLibraryTest {
 public static void main(String[]args)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));NativeItemLibraryService s=new NativeItemLibraryService();
  String open=s.open(w,28860);w.flush();byte[] a=out.toByteArray();has(a,"ITEM_GUIDE_RESET_PREVIEW\n");has(a,"ITEM_GUIDE_SET_ITEM_SPRITE\n");has(a,"Scorching bow (i)\n");has(a,"+1 attack speed");has(a,"anim 15409");
  if(!s.isOpen()||s.selectedItem()!=28860||!open.contains("root=47500"))throw new AssertionError(open);
  out.reset();String bonus=s.handleWidget(w,47816);w.flush();byte[] b=out.toByteArray();has(b,"ITEM_GUIDE_BONUS_WIDGET ON\n");has(b,"N/A\n");if(!bonus.contains("FAIL_CLOSED_NA"))throw new AssertionError(bonus);
  out.reset();String search=s.searchExact(w,"Tumeken's shadow (i)");w.flush();byte[] c=out.toByteArray();has(c,"Tumeken's shadow (i)\n");has(c,"projectile 5092");if(s.selectedItem()!=28539)throw new AssertionError("igsearch selected="+s.selectedItem());

  NativeItemLibraryService fresh=new NativeItemLibraryService();
  SwitchFailOutputStream firstFailOut=new SwitchFailOutputStream();
  firstFailOut.fail=true;
  ServerPacketWriter firstFailWriter=
   new ServerPacketWriter(firstFailOut,new IsaacCipher(new int[]{5,6,7,8}));
  boolean firstFailed=false;
  try{
   fresh.open(firstFailWriter,28860);
  }catch(IOException expected){
   firstFailed="SWITCH_FAIL".equals(expected.getMessage());
  }
  if(!firstFailed||fresh.isOpen()||fresh.selectedItem()!=-1)
   throw new AssertionError(
    "failed first Item Library publication committed state open="+
    fresh.isOpen()+" selected="+fresh.selectedItem()
   );
  if(fresh.handleWidget(firstFailWriter,NativeItemLibraryService.BONUS_BUTTON)!=null)
   throw new AssertionError("failed first publication admitted hidden widget");

  NativeItemLibraryService reopen=new NativeItemLibraryService();
  ByteArrayOutputStream reopenGoodOut=new ByteArrayOutputStream();
  ServerPacketWriter reopenGoodWriter=
   new ServerPacketWriter(reopenGoodOut,new IsaacCipher(new int[]{9,10,11,12}));
  reopen.open(reopenGoodWriter,28860);
  if(!reopen.isOpen()||reopen.selectedItem()!=28860)
   throw new AssertionError("reopen fixture did not establish prior Item Library");

  SwitchFailOutputStream reopenFailOut=new SwitchFailOutputStream();
  reopenFailOut.fail=true;
  ServerPacketWriter reopenFailWriter=
   new ServerPacketWriter(reopenFailOut,new IsaacCipher(new int[]{13,14,15,16}));
  boolean reopenFailed=false;
  try{
   reopen.open(reopenFailWriter,28539);
  }catch(IOException expected){
   reopenFailed="SWITCH_FAIL".equals(expected.getMessage());
  }
  if(!reopenFailed||!reopen.isOpen()||reopen.selectedItem()!=28860)
   throw new AssertionError(
    "failed Item Library reopen replaced prior state open="+
    reopen.isOpen()+" selected="+reopen.selectedItem()
   );

  ByteArrayOutputStream priorWidgetOut=new ByteArrayOutputStream();
  ServerPacketWriter priorWidgetWriter=
   new ServerPacketWriter(priorWidgetOut,new IsaacCipher(new int[]{17,18,19,20}));
  String priorSelected=
   reopen.handleWidget(priorWidgetWriter,47506);
  if(priorSelected==null||!priorSelected.contains("item=28860"))
   throw new AssertionError("failed reopen lost prior selected-row authority "+priorSelected);

  System.out.println("V5150_ENGINE_R5_NATIVE_ITEM_LIBRARY_PASS root47500=true igsearchExactName=true staticDescription=true v913RuntimeJoin=true bonusUnknownFailClosed=true searchPromptNotInvented=true firstPublicationFailureAtomic=true reopenFailurePreservesPriorState=true hiddenWidgetRejectedAfterFailedFirstOpen=true");
 }
 static final class SwitchFailOutputStream extends OutputStream{
  boolean fail;
  final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  @Override public void write(int value)throws IOException{
   if(fail)throw new IOException("SWITCH_FAIL");
   bytes.write(value);
  }
  @Override public void write(byte[] data,int offset,int length)throws IOException{
   if(fail)throw new IOException("SWITCH_FAIL");
   bytes.write(data,offset,length);
  }
  @Override public void flush()throws IOException{
   if(fail)throw new IOException("SWITCH_FAIL");
  }
 }
 static void has(byte[]b,String x){byte[]n=x.getBytes(StandardCharsets.ISO_8859_1);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return;}throw new AssertionError("missing "+x+" bytes="+b.length);}
}

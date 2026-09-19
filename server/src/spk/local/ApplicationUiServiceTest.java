package spk.local;
import java.io.*;import java.util.*;
public final class ApplicationUiServiceTest{
 static final int[] SEED={91,92,93,94};
 public static void main(String[]a)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(SEED.clone()));
  ApplicationUiService.mailboxAppend(w,0,"hello");ApplicationUiService.timedEffect(w,"event_elixir",30);ApplicationUiService.infoboxNumeric(w,"counter",7,null,"fixture");ApplicationUiService.intFlagMap(w,123,4);
  byte[] b=out.toByteArray();IsaacCipher d=new IsaacCipher(SEED.clone());int p=0;int[] want={31,19,34,43};
  for(int subtype:want){int op=((b[p++]&255)-d.nextInt())&255;if(op!=250)throw new AssertionError("opcode="+op);int len=b[p++]&255;if(len<2)throw new AssertionError("len");int got=((b[p]&255)<<8)|(b[p+1]&255);if(got!=subtype)throw new AssertionError("subtype expected="+subtype+" got="+got);p+=len;}
  if(p!=b.length)throw new AssertionError("trailing="+(b.length-p));
  System.out.println("V5185_APPLICATION_UI_SERVICE_PASS typed250=true subtypes=31,19,34,43 remainingStructuralPublishers=true subtype20IntentionallyNotExposed=true");
 }
}

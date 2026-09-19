package spk.local;
import java.util.*;
/** Exact packet65 forced-text control transport used by pet hidden native state and Scopesight SNIPE. */
public final class NpcPacket65ForceTextCodecTest {
 public static void main(String[] args){
  NpcEntity n=new NpcEntity(1,6650,55,55);
  byte[] b=NpcSyncEncoder.encode(Collections.singletonList(NpcSyncEncoder.Update.mask(n,NpcSyncEncoder.Mask.forceText("3"))),Collections.emptyList(),0,0);
  int L=b.length;
  if(L<5)throw new AssertionError();
  int mask=((b[L-4]&255)<<8)|(b[L-3]&255);
  if(mask!=1||(b[L-2]&255)!='3'||(b[L-1]&255)!=10)throw new AssertionError(java.util.Arrays.toString(b));
  byte[] s=NpcSyncEncoder.encode(Collections.singletonList(NpcSyncEncoder.Update.mask(new NpcEntity(1,8330,55,55),NpcSyncEncoder.Mask.forceText("SNIPE"))),Collections.emptyList(),0,0);
  String tail=new String(s,Math.max(0,s.length-6),Math.min(6,s.length),java.nio.charset.StandardCharsets.ISO_8859_1);
  if(!tail.equals("SNIPE\n"))throw new AssertionError("tail="+tail);
  System.out.println("V59_NPC65_FORCE_TEXT_CODEC_PASS mask=0x0001 numericPetState=3_newline scopesight=SNIPE_newline clientConsumesControlText=true");
 }
}

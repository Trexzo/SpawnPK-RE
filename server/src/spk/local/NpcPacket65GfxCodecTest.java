package spk.local;
import java.util.*;
/** Exact pinned-client NPC mask 0x80 codec: gfx u16 BE + packed(height<<16|delay) i32 BE. */
public final class NpcPacket65GfxCodecTest {
 public static void main(String[] args){
  NpcEntity n=new NpcEntity(1,8330,55,55);
  byte[] b=NpcSyncEncoder.encode(Collections.singletonList(NpcSyncEncoder.Update.mask(n,NpcSyncEncoder.Mask.gfx(1310,7,12))),Collections.emptyList(),0,0);
  int L=b.length;
  if(L<8)throw new AssertionError("short "+L);
  // Mask and exact GFX body are last 8 bytes because there is one masked retained NPC.
  int mask=((b[L-8]&255)<<8)|(b[L-7]&255);
  int gfx=((b[L-6]&255)<<8)|(b[L-5]&255);
  int packed=((b[L-4]&255)<<24)|((b[L-3]&255)<<16)|((b[L-2]&255)<<8)|(b[L-1]&255);
  if(mask!=0x80||gfx!=1310||packed!=((7<<16)|12))throw new AssertionError("mask="+mask+" gfx="+gfx+" packed="+Integer.toHexString(packed));
  System.out.println("V59_NPC65_GFX_CODEC_PASS mask=0x80 gfxBE=1310 packedHeightDelay=7,12 decoderOrder=animation_hit_gfx_interaction_text");
 }
}

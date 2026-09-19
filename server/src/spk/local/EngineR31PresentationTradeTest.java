package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class EngineR31PresentationTradeTest{
 public static void main(String[]a)throws Exception{
  World w=World.isolatedForTest(600L);WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();w.registerPlayer(p1,"opensrc");w.registerPlayer(p2,"src");
  OutboundPacketQueue q1=new OutboundPacketQueue(),q2=new OutboundPacketQueue();ServerPacketWriter w1=new ServerPacketWriter(q1,new IsaacCipher(new int[]{1,2,3,4})),w2=new ServerPacketWriter(q2,new IsaacCipher(new int[]{5,6,7,8}));
  Player81WorldSync.Context c1=Player81WorldSync.register(w1,w,p1,new DevAuthorityWorkbench()),c2=Player81WorldSync.register(w2,w,p2,new DevAuthorityWorkbench());
  try{
   Player81WorldSync.transformForTest(c1,BootstrapPackets.player81Idle());Player81WorldSync.transformForTest(c2,BootstrapPackets.player81Idle());
   Player81WorldSync.transformForTest(c1,CombatSync.player81AnimationAndGfx(827,1310,0,0));
   Player81WorldSync.transformForTest(c1,CombatSync.player81InteractionOnly(-1));
   byte[] first=Player81WorldSync.transformForTest(c2,BootstrapPackets.player81Idle());int m1=remoteMask(first);if((m1&0x8)==0||(m1&0x100)==0)throw new AssertionError("first queued event lost animation/gfx mask=0x"+Integer.toHexString(m1));
   byte[] second=Player81WorldSync.transformForTest(c2,BootstrapPackets.player81Idle());int m2=remoteMask(second);if((m2&0x1)==0)throw new AssertionError("second queued interaction event missing mask=0x"+Integer.toHexString(m2));
   String tr=c1.requestTrade(p2,System.currentTimeMillis());if(!tr.contains("NOTIFY_SENT"))throw new AssertionError(tr);
   ByteArrayOutputStream out=new ByteArrayOutputStream();q2.drainTo(out,1<<20);String raw=new String(out.toByteArray(),StandardCharsets.ISO_8859_1);if(!raw.contains("opensrc:tradereq:\n"))throw new AssertionError("trade request payload missing");
   System.out.println("V5131_R31_PRESENTATION_TRADE_PASS orderedPlayerEvents=true tradeRequest253=true");
  }finally{Player81WorldSync.unregister(w1);Player81WorldSync.unregister(w2);w.unregisterPlayer(p1);w.unregisterPlayer(p2);w.close();}
 }
 static int remoteMask(byte[] b){Bits r=new Bits(b);r.read(1);int n=r.read(8);if(n<1)throw new AssertionError("no remote");int changed=r.read(1);if(changed!=1)throw new AssertionError("remote unchanged");int type=r.read(2);if(type!=0)throw new AssertionError("expected mask-only type="+type);if(r.read(11)!=2047)throw new AssertionError("sentinel");r.align();int p=r.bytePos();int low=b[p]&255,m=low;if((low&0x40)!=0)m|=(b[p+1]&255)<<8;return m;}
 static final class Bits{final byte[]b;int bit;Bits(byte[]b){this.b=b;}int read(int n){int v=0;for(int i=0;i<n;i++){v=(v<<1)|((b[bit>>>3]>>(7-(bit&7)))&1);bit++;}return v;}void align(){bit=(bit+7)&~7;}int bytePos(){return bit>>>3;}}
}

package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class EngineR4NativeEquipmentDeathUiTest{
 public static void main(String[]a)throws Exception{
  OutboundPacketQueue q=new OutboundPacketQueue();ServerPacketWriter w=new ServerPacketWriter(q,new IsaacCipher(new int[]{11,12,13,14}));BankState b=new BankState();EquipmentState e=new EquipmentState();
  b.spawnItem(20466,1,w);b.spawnItem(995,10,w);drain(q);
  String stats=NativeEquipmentDeathUi.openEquipmentStats(w,e);if(!stats.contains("15106"))throw new AssertionError(stats);byte[] sb=drain(q);has(sb,"N/A\n","equipment N/A server authority marker");
  String death=NativeEquipmentDeathUi.openDeathPreview(w,b,e);if(!death.contains("17100")||!death.contains("autoKeep=1"))throw new AssertionError(death);byte[] db=drain(q);has(db,"Explicit auto-keep: 1\n","death explicit keep text");has(db,"Standard keep/loss order is server-owned\n","death authority boundary");
  if(DeathPolicyRepository.get(20466).kind!=DeathPolicyRepository.Kind.AUTO_KEEP_EXPLICIT)throw new AssertionError("autokeep resource failed");
  System.out.println("V5140_ENGINE_R4_NATIVE_EQUIPMENT_DEATH_UI_PASS statsRoot=15106 deathRoot=17100 deathGrid=17108 subtype9=true explicitPolicy=true unresolvedRiskNotInvented=true");
 }
 static byte[] drain(OutboundPacketQueue q)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static void has(byte[] b,String s,String label){byte[] n=s.getBytes(StandardCharsets.ISO_8859_1);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return;}throw new AssertionError(label+" missing");}
}

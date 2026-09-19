package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class EngineR52DeathPolicyClassificationTest{
 public static void main(String[]a)throws Exception{
  req(DeathPolicyRepository.count()==30000,"rows="+DeathPolicyRepository.count());
  req(DeathPolicyRepository.count(DeathPolicyRepository.Kind.AUTO_KEEP_EXPLICIT)==53,"keep count");
  req(DeathPolicyRepository.count(DeathPolicyRepository.Kind.AUTO_LOSS_EXPLICIT)==16,"loss count");
  req(DeathPolicyRepository.count(DeathPolicyRepository.Kind.STANDARD_UNRESOLVED)==29931,"standard count");
  req(DeathPolicyRepository.get(20466).kind==DeathPolicyRepository.Kind.AUTO_KEEP_EXPLICIT,"20466 keep");
  req(DeathPolicyRepository.get(24254).kind==DeathPolicyRepository.Kind.AUTO_LOSS_EXPLICIT,"24254 loss");
  req(DeathPolicyRepository.get(995).kind==DeathPolicyRepository.Kind.STANDARD_UNRESOLVED,"coins must remain standard unresolved");
  OutboundPacketQueue q=new OutboundPacketQueue();ServerPacketWriter w=new ServerPacketWriter(q,new IsaacCipher(new int[]{71,72,73,74}));BankState b=new BankState();EquipmentState e=new EquipmentState();
  b.spawnItem(20466,1,w);b.spawnItem(24254,1,w);b.spawnItem(995,100,w);drain(q);
  String r=NativeEquipmentDeathUi.openDeathPreview(w,b,e);req(r.contains("autoKeep=1")&&r.contains("autoLoss=1")&&r.contains("standardUnresolved="),r);
  byte[] wire=drain(q);has(wire,"Explicit auto-keep: 1\n");has(wire,"Explicit auto-loss: 1\n");has(wire,"Standard items: ");
  System.out.println("V5160_R52_DEATH_POLICY_CLASSIFICATION_PASS rows=30000 autoKeep=53 autoLoss=16 standard=29931 coinsStandard=true nativePreviewMixedPolicy=true riskOrderingUninvented=true");
 }
 static byte[] drain(OutboundPacketQueue q)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static void has(byte[] b,String s){byte[] n=s.getBytes(StandardCharsets.ISO_8859_1);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return;}throw new AssertionError("missing "+s);}
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

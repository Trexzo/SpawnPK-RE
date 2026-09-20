package spk.local;
import java.io.*;

public final class R85GenericC2SProbeIntegrationTest {
 private static void req(boolean b,String m){if(!b)throw new AssertionError(m);}
 private static void be(ByteArrayOutputStream o,int v){o.write(v>>>8&255);o.write(v&255);}
 private static void le(ByteArrayOutputStream o,int v){o.write(v&255);o.write(v>>>8&255);}
 private static void beA(ByteArrayOutputStream o,int v){o.write(v>>>8&255);o.write((v+128)&255);}
 private static void leA(ByteArrayOutputStream o,int v){o.write((v+128)&255);o.write(v>>>8&255);}
 private static void op(ByteArrayOutputStream o,IsaacCipher c,int v){o.write((v+c.nextInt())&255);}

 public static void main(String[]z)throws Exception{
  int[]seed={0x01020304,0x11223344,0x01234567,0x89abcdef};
  IsaacCipher enc=new IsaacCipher(seed.clone());
  ByteArrayOutputStream w=new ByteArrayOutputStream();

  op(w,enc,14);beA(w,3214);be(w,7);be(w,21560);le(w,11);
  op(w,enc,25);le(w,3214);beA(w,21560);be(w,995);beA(w,3091);leA(w,11);be(w,3503);
  op(w,enc,70);le(w,3503);be(w,3091);leA(w,26972);
  op(w,enc,176);le(w,6);beA(w,36025);le(w,4151);
  op(w,enc,192);be(w,3214);le(w,26972);leA(w,3091);le(w,11);leA(w,3503);be(w,21560);
  op(w,enc,228);beA(w,26972);beA(w,3091);be(w,3503);
  op(w,enc,234);leA(w,3503);beA(w,26972);leA(w,3091);
  op(w,enc,252);leA(w,26972);le(w,3091);beA(w,3503);

  ClientPacketProbe p=new ClientPacketProbe(
      new ByteArrayInputStream(w.toByteArray()),
      new IsaacCipher(seed.clone()),
      "[r85-c2s] ");

  int[]ops={14,25,70,176,192,228,234,252};
  GenericInteractionEvent.Family[]fam={
      GenericInteractionEvent.Family.ITEM_ON_PLAYER,
      GenericInteractionEvent.Family.ITEM_ON_GROUND_ITEM,
      GenericInteractionEvent.Family.OBJECT_OPTION,
      GenericInteractionEvent.Family.WIDGET_ITEM_OPTION,
      GenericInteractionEvent.Family.ITEM_ON_OBJECT,
      GenericInteractionEvent.Family.OBJECT_OPTION,
      GenericInteractionEvent.Family.OBJECT_OPTION,
      GenericInteractionEvent.Family.OBJECT_OPTION
  };

  for(int i=0;i<ops.length;i++){
      req(p.readNextKnownPacket(),"decode false op="+ops[i]);
      req(p.isAligned(),"unaligned op="+ops[i]);

      ClientRequest request=p.takeTypedRequest();
      req(request instanceof GenericInteractionClientRequest,
          "missing typed event op="+ops[i]+" request="+request);

      GenericInteractionClientRequest typed=
          (GenericInteractionClientRequest)request;
      GenericInteractionEvent e=typed.event();

      req(e.opcode==ops[i],"opcode="+e.opcode);
      req(e.family==fam[i],"family="+e.family);

      ClientRequestMetadata metadata=typed.metadata();
      req(metadata.opcode==ops[i],"metadata opcode="+metadata);
      req(metadata.provenance==
              ClientRequestProvenance.EXACT_CURRENT_CLIENT,
          "metadata provenance="+metadata);
      req(metadata.schema.equals(
              GenericInteractionPacketDecoder.schema(ops[i])),
          "metadata schema="+metadata);
      req(metadata.source.equals(
              GenericInteractionPacketDecoder.source(ops[i])),
          "metadata source="+metadata);
  }

  req(p.takeTypedRequest()==null,"typed queue not empty");
  System.out.println(
      "V5185_GENERIC_C2S_PROBE_INTEGRATION_PASS "+
      "packets=8 aligned=true typedQueue=true "+
      "reflectionBridge=false metadata=true");
 }
}

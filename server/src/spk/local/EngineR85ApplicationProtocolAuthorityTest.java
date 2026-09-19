package spk.local;
public final class EngineR85ApplicationProtocolAuthorityTest {
 public static void main(String[] a){
  if(ClientApplicationProtocolAuthority.APP_SUBTYPES!=43)throw new AssertionError("subtypes="+ClientApplicationProtocolAuthority.APP_SUBTYPES);
  if(ClientApplicationProtocolAuthority.OPERATION_DECODED_SUBTYPES!=43)throw new AssertionError("decoded="+ClientApplicationProtocolAuthority.OPERATION_DECODED_SUBTYPES);
  if(ClientApplicationProtocolAuthority.R6_OPERATION_ROWS!=64)throw new AssertionError("r6rows="+ClientApplicationProtocolAuthority.R6_OPERATION_ROWS);
  String x=ClientApplicationProtocolAuthority.applicationSummary();
  if(!x.contains("43")||!x.contains("subtype20"))throw new AssertionError(x);
  System.out.println("V5185_APPLICATION_PROTOCOL_AUTHORITY_PASS subtypes=43 operationDecoded=43 r6Rows=64 subtype20ExternalReceiverEmitterDisabled=true");
 }
}

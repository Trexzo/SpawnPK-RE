package spk.local;
public final class EngineR84ApplicationProtocolAuthorityTest{
 public static void main(String[]a){
  if(ClientApplicationProtocolAuthority.APP_SUBTYPES!=43)throw new AssertionError("subtypes="+ClientApplicationProtocolAuthority.APP_SUBTYPES);
  if(ClientApplicationProtocolAuthority.OPERATION_DECODED_SUBTYPES!=43)throw new AssertionError("decoded="+ClientApplicationProtocolAuthority.OPERATION_DECODED_SUBTYPES);
  if(ClientApplicationProtocolAuthority.R4_OPERATION_ROWS!=66||ClientApplicationProtocolAuthority.R5_OPERATION_ROWS!=69)throw new AssertionError("ops="+ClientApplicationProtocolAuthority.R4_OPERATION_ROWS+"+"+ClientApplicationProtocolAuthority.R5_OPERATION_ROWS);
  if(ClientApplicationProtocolAuthority.R6_OPERATION_ROWS!=64)throw new AssertionError("r6="+ClientApplicationProtocolAuthority.R6_OPERATION_ROWS);
  if(ClientApplicationProtocolAuthority.VM_OPCODES!=21||ClientApplicationProtocolAuthority.SETTINGS_MAPPED!=22||ClientApplicationProtocolAuthority.MENU_ROUTES!=52||ClientApplicationProtocolAuthority.X_PRODUCERS!=18||ClientApplicationProtocolAuthority.S2C126_ARGUMENT_ROUTES!=15||ClientApplicationProtocolAuthority.CLAN_WARS_WIDGETS!=27||ClientApplicationProtocolAuthority.GAMBLING_WIDGETS!=6)throw new AssertionError(ClientApplicationProtocolAuthority.summary());
  if(!ClientApplicationProtocolAuthority.applicationSummary().contains("all 43")||!ClientApplicationProtocolAuthority.applicationSummary().contains("subtype20")||!ClientApplicationProtocolAuthority.boundary().contains("server-owned"))throw new AssertionError("summary/boundary");
  System.out.println("V5185_ENGINE_R84_COMPAT_APPLICATION_PROTOCOL_AUTHORITY_PASS subtypes=43 decoded=43 r4Rows=66 r5Rows=69 r6Rows=64 vm=21 settings=22 menuRoutes=52 xProducers=18 s2c126=15 clanWars=27 gambling=6 failClosed=true");
 }
}

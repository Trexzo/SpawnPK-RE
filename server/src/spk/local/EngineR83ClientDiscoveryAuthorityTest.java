package spk.local;

public final class EngineR83ClientDiscoveryAuthorityTest {
 public static void main(String[]args)throws Exception{
  if(!"9150bbb90b2d73cfba32e6afd709ea0f01d9b4a75309a7fcaabecb000cee2ba6".equals(ClientDiscoveryAuthority.SOURCE_R2_SHA256))throw new AssertionError("R2 source pin");
  if(ClientDiscoveryAuthority.TIMED_EFFECTS!=64||ClientDiscoveryAuthority.STANDARD_SPELLS!=41||ClientDiscoveryAuthority.CONSTRUCTION_ROOMS!=23||ClientDiscoveryAuthority.PLANK_DISPLAY_PRICES!=4||ClientDiscoveryAuthority.ACHIEVEMENT_BOOTSTRAP!=9||ClientDiscoveryAuthority.TIMED_TASK_RULES!=6||ClientDiscoveryAuthority.CUSTOM_MAGIC_BUILDERS!=8||ClientDiscoveryAuthority.CLIENT_SETTINGS!=29||ClientDiscoveryAuthority.COMMAND_LITERALS!=160||ClientDiscoveryAuthority.UI_FEATURES!=23||ClientDiscoveryAuthority.CLAN_WARS_OPTIONS!=27||ClientDiscoveryAuthority.CONTROL_TOKENS!=35)throw new AssertionError(ClientDiscoveryAuthority.summary());
  String tasks=ClientDiscoveryAuthority.taskAchievementSummary();
  if(!tasks.contains("25 min")||!tasks.contains("1/35 Larran keys")||!tasks.contains("Kill 3-6 bounty hunter targets")||!tasks.contains("Vote for SPK"))throw new AssertionError("tasks "+tasks);
  String magic=ClientDiscoveryAuthority.magicConstructionSummary();
  if(!magic.contains("AIR_STRIKE")||!magic.contains("1152")||!magic.contains("Miasmic barrage")||!magic.contains("Treasure room")||!magic.contains("1000k"))throw new AssertionError("magic/construction "+magic);
  String ui=ClientDiscoveryAuthority.uiControlSummary();
  if(!ui.contains("CONSTRUCTION_BUILD_ON/OFF")||!ui.contains("Hunger Games"))throw new AssertionError("ui "+ui);
  if(!ClientDiscoveryAuthority.boundary().contains("server business rules"))throw new AssertionError("boundary");
  System.out.println("V5183_ENGINE_R83_CLIENT_DISCOVERY_AUTHORITY_PASS effects=64 spells=41 rooms=23 planks=4 achievements=9 tasks=6 customMagic=8 settings=29 commands=160 features=23 clanWars=27 controls=35 failClosed=true");
 }
}

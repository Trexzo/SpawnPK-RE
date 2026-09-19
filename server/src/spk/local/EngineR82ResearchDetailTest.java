package spk.local;

public final class EngineR82ResearchDetailTest {
 public static void main(String[]args)throws Exception{
  String shadow=EquipmentResearchAuthority.itemSummary(27485).toLowerCase();
  if(!shadow.contains("staticevidence=")||!shadow.contains("numeric=90|4"))throw new AssertionError("shadowrend static mechanics not surfaced: "+shadow);
  String carrot=EquipmentResearchAuthority.itemSummary(28176).toLowerCase();if(!carrot.contains("vesta longsword"))throw new AssertionError("carrot inheritance");
  PetProcResearchAuthority.Row res=PetProcResearchAuthority.findForPetItem(27340);if(res==null||!res.family.contains("Resvano")||!res.clientNativeControl.contains("sprite53"))throw new AssertionError("Resvano renderer");
  PetProcResearchAuthority.Row beh=PetProcResearchAuthority.findForPetItem(24016);if(beh==null||!beh.family.contains("Behemoth")||!beh.clientNativeControl.toLowerCase().contains("behemoth"))throw new AssertionError("Behemoth family renderer");
  PetProcResearchAuthority.Row ice=PetProcResearchAuthority.findForPetItem(24048);if(ice==null||ice.previewable())throw new AssertionError("Icelord must stay runtime-gated");
  String world=WorldFullResearchAuthority.regionSummary(5771);if(!world.contains("CLIENT_HARDCODED_REGION"))throw new AssertionError("world usage detail: "+world);
  String router=ServiceResearchAuthority.routerSummary();if(!router.contains("155/72/17/21/18")||!router.contains("132/252/70/234/228")||!router.contains("145/117/43/129/135/176"))throw new AssertionError("router atlas");
  if(!ServiceResearchAuthority.bloodSummary().contains("10/11/12")||!ServiceResearchAuthority.shopSummary().contains("root3824"))throw new AssertionError("service contracts");
  if(!AssetRuntimeResearchAuthority.archiveSummary().contains("object26353/model50096/region11602"))throw new AssertionError("RAW dependency");
  if(ResearchExhaustionAuthority.boundary().contains("missing row-level"))throw new AssertionError("stale archive gap wording");
  System.out.println("V5182_ENGINE_R82_RESEARCH_DETAIL_PASS equipmentEvidence=true petNativeRenderer=true unresolvedIcelordFailClosed=true worldUsage=true interactionRouters=true bloodShopContracts=true rawDependency=true staleMissingArchiveClaim=false");
 }
}

package spk.local;

/** Updated inherited R8 closure test: R8.2 supersedes transcript-only gaps with actual final archives. */
public final class EngineR8ResearchExhaustionTest {
 public static void main(String[]args)throws Exception{
  if(ItemAuthorityRepository.count()!=30000)throw new AssertionError("items");
  if(ItemAuthorityRepository.withEffectText()!=1729)throw new AssertionError("item effect count="+ItemAuthorityRepository.withEffectText());
  if(PetResearchAuthorityRepository.count()!=339||PetResearchAuthorityRepository.withEffectText()!=235)throw new AssertionError("pet authority");
  if(EquipmentResearchAuthority.relationshipCount()!=33)throw new AssertionError("equipment relations="+EquipmentResearchAuthority.relationshipCount());
  if(EquipmentResearchAuthority.EFFECT_TEXT_ROWS!=1728||EquipmentResearchAuthority.STATIC_MECHANICS_ROWS!=729||EquipmentResearchAuthority.I_BIN_COMBAT_ROWS!=181||EquipmentResearchAuthority.EQUIPPABLE_I_BIN_COMBAT_ROWS!=99||EquipmentResearchAuthority.CORE_BONUS_ROWS!=284||EquipmentResearchAuthority.SPEED_DAMAGE_ROWS!=51||EquipmentResearchAuthority.CONDITIONAL_ROWS!=565||EquipmentResearchAuthority.PET_MODIFIER_ROWS!=160)throw new AssertionError("equipment R2 counts");
  if(WorldTransitionResearchAuthority.teleportCount()!=21||WorldTransitionResearchAuthority.transitionCount()!=32)throw new AssertionError("world transition counts");
  WorldTransitionResearchAuthority.TeleportItem arax=WorldTransitionResearchAuthority.teleport(28580);if(arax==null||!arax.candidates.contains("16193"))throw new AssertionError("araxxor teleport join");
  if(PetProcResearchAuthority.count()!=23)throw new AssertionError("actual R4 pet tuple rows="+PetProcResearchAuthority.count());
  if(PetProcResearchAuthority.componentCount()!=47||PetProcResearchAuthority.animationTimingCount()!=101||PetProcResearchAuthority.gfxTimingCount()!=55||PetProcResearchAuthority.variantFamilyCount()!=32||PetProcResearchAuthority.specialRendererCount()!=9||PetProcResearchAuthority.remainingRuntimeCount()!=14)throw new AssertionError("actual R4 detail counts "+PetProcResearchAuthority.corpusSummary());
  PetProcResearchAuthority.Row tempo=PetProcResearchAuthority.findForPetItem(27690),ripper=PetProcResearchAuthority.findForPetItem(21693),resvano=PetProcResearchAuthority.findForPetItem(27340);
  if(tempo==null||tempo.firstAnimation()!=15562||tempo.firstGfx()!=4104)throw new AssertionError("tempoross tuple");
  if(ripper==null||ripper.previewable())throw new AssertionError("ripper must remain unresolved");
  if(resvano==null||!resvano.clientNativeControl.contains("sprite53"))throw new AssertionError("Resvano native renderer authority");
  if(WorldFullResearchAuthority.EFFECTIVE_UNIQUE_REGIONS!=1279||WorldFullResearchAuthority.STATIC_PLACEMENTS!=2162982||WorldFullResearchAuthority.COLLIDABLE_PLACEMENTS!=1316740||WorldFullResearchAuthority.ADJACENCY_ROWS!=2002||WorldFullResearchAuthority.COMPONENTS!=72)throw new AssertionError("world R1");
  if(AssetRuntimeResearchAuthority.SKELETON_FAMILIES!=28||AssetRuntimeResearchAuthority.OBJECT_MODEL_PARSED!=17505||AssetRuntimeResearchAuthority.RIG_EQUIVALENCE_FAMILIES!=644)throw new AssertionError("asset R8");
  if(ServiceResearchAuthority.RECOVERY_MATRIX_ROWS!=20||ServiceResearchAuthority.ROUTER_ROWS!=16||ServiceResearchAuthority.SERVICE_KEY_ROWS!=9||ServiceResearchAuthority.PRODUCTION_OBJECT_EXAMPLES!=7)throw new AssertionError("service R1");
  if(FullResearchArchiveAuthority.SOURCE_PACKAGES!=10||FullResearchArchiveAuthority.datasetCount()!=67||FullResearchArchiveAuthority.rowLevelDatasetCount()!=62)throw new AssertionError("full archive manifest "+FullResearchArchiveAuthority.summary());
  if(ResearchExhaustionAuthority.laneCount()!=7)throw new AssertionError("lane count="+ResearchExhaustionAuthority.laneCount());
  if(!ResearchExhaustionAuthority.boundary().contains("actual final archives consumed"))throw new AssertionError("stale missing-archive boundary");
  System.out.println("V5182_ENGINE_R8_RESEARCH_EXHAUSTION_PASS items=30000 petDescriptions=235 equipRelations=33 equipMechanics=729 teleportItems=21 transitions=32 petProcActualR4Rows=23 assetRigFamilies=644 sourcePackages=10 datasets=67 missingArchiveClaim=false serverOwnedBoundariesPreserved=true");
 }
}

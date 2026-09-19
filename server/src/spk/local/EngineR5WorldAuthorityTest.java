package spk.local;
public final class EngineR5WorldAuthorityTest {
 public static void main(String[]args)throws Exception{
  if(WorldRegionAuthorityRepository.count()!=1279)throw new AssertionError("region count="+WorldRegionAuthorityRepository.count());
  WorldRegionAuthorityRepository.Region home=WorldRegionAuthorityRepository.get(12342);if(home==null)throw new AssertionError("home missing");
  if(!home.contains(3090,3490)||!"PRODUCTION_CONFIRMED".equals(home.usageStatus))throw new AssertionError("home authority="+home);
  WorldRegionAuthorityRepository.Region tile=WorldRegionAuthorityRepository.forTile(3090,3490);if(tile==null||tile.regionId!=12342)throw new AssertionError("home tile lookup="+tile);
  WorldRegionAuthorityRepository.Region arax=WorldRegionAuthorityRepository.get(16193);if(arax==null||!"araxxor_lair".equals(arax.group)||arax.x0!=4032||arax.y0!=4160||!arax.terrainParseOk||!arax.objectParseOk)throw new AssertionError("araxxor="+arax);
  if(WorldRegionAuthorityRepository.fullyDecodedCount()<1200)throw new AssertionError("decoded count="+WorldRegionAuthorityRepository.fullyDecodedCount());
  System.out.println("V5150_ENGINE_R5_WORLD_AUTHORITY_PASS regions="+WorldRegionAuthorityRepository.count()+" decoded="+WorldRegionAuthorityRepository.fullyDecodedCount()+" productionConfirmed="+WorldRegionAuthorityRepository.productionConfirmedCount()+" explicitCustom="+WorldRegionAuthorityRepository.explicitCustomCount()+" home12342=true araxxor16193=true behavior=DATA_ONLY_NO_TELEPORT");
 }
}

package spk.local;
public final class EngineR71AlignmentContractTest {
  public static void main(String[] args){
    if(!ClientAssetAlignmentAuthority.countsAligned())throw new AssertionError("authority counts drift: "+ContentAuthorityRepository.summary());
    if(!ClientAssetAlignmentAuthority.root328Aligned())throw new AssertionError("exact-client root328 reconciliation missing");
    if(!ClientAssetAlignmentAuthority.CLIENT_SHA256.equals("6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662"))throw new AssertionError("client pin drift");
    if(!ClientAssetAlignmentAuthority.SPAWNPK_ASSET_BUNDLE_SHA256.equals("607425ad3fe69f4cfcaaf82220d9a0d4ebff9954e896819245f37271713d299a"))throw new AssertionError("asset pin drift");
    WorldRegionAuthorityRepository.Region home=WorldRegionAuthorityRepository.get(12342);
    if(home==null||home.regionX!=48||home.regionY!=54)throw new AssertionError("HOME region authority drift");
    System.out.println("V5171_ENGINE_R71_ALIGNMENT_CONTRACT_PASS "+ClientAssetAlignmentAuthority.shortStatus()+" counts=30000/339/51/126/18/62/605/107/1279 collisionRegions=1279 worldPlacements="+ClientAssetAlignmentAuthority.STATIC_WORLD_PLACEMENTS+" collisionEntries="+ClientAssetAlignmentAuthority.COLLISION_ENTRIES);
  }
}

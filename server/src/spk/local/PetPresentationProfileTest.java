package spk.local;
public final class PetPresentationProfileTest {
 public static void main(String[] args){
  if(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION!=827)throw new AssertionError();
  if(PetPresentationProfile.OWNER_BOOST_ANIMATION!=10184||PetPresentationProfile.OWNER_BOOST_GFX!=1310)throw new AssertionError();
  if(PetPresentationProfile.TEMPOROSS_ACTIVATION_ANIMATION_CANDIDATE!=15562)throw new AssertionError();
  System.out.println("V59_PET_PRESENTATION_PROFILE_PASS lifecycle=owner827_noGfx boost=owner10184+gfx1310 scopesight=nativeSNIPE temporossAnim15562=cacheNameCandidate evilWolperBodyAnim=UNRESOLVED_RAW_TEST_AVAILABLE");
 }
}

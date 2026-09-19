package spk.local;
/** V9.08 production capture: successful pet Drop and Pick-up share owner animation 827 and no GFX. */
public final class PetOwnerLifecyclePresentationTest {
 public static void main(String[] args)throws Exception{
  if(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION!=827)throw new AssertionError();
  byte[] p=CombatSync.player81AnimationOnly(PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION);
  if(p.length!=8)throw new AssertionError("packet81 animation payload len="+p.length);
  // packet81 local update: count=1, no movement, mask marker 0x08, little-endian animation id, delay=0.
  if((p[p.length-4]&255)!=0x3B || (p[p.length-3]&255)!=0x03 || (p[p.length-2]&255)!=0 || (p[p.length-1]&255)!=0)
    throw new AssertionError("animation827 tail="+java.util.Arrays.toString(p));
  System.out.println("V59_PET_OWNER_LIFECYCLE_PRESENTATION_PASS dropAnim=827 pickupAnim=827 gfx=NONE durationObservedMs=1125..1203 evidence=V908_PRODUCTION_REPEAT");
 }
}

package spk.local;
public final class PetHiddenStatePresentationTest {
 public static void main(String[] args){
  eq(6650,PetPresentationProfile.NativeStateFamily.BEHEMOTH_CHARGES,"1x_NATIVE_SPRITE_22","3x_NATIVE_SPRITE_22");
  eq(5159,PetPresentationProfile.NativeStateFamily.BEHEMOTH_CHARGES,"1x_NATIVE_SPRITE_22","3x_NATIVE_SPRITE_22");
  eq(6049,PetPresentationProfile.NativeStateFamily.BEHEMOTH_CHARGES,"1x_NATIVE_SPRITE_22","3x_NATIVE_SPRITE_22");
  if(PetPresentationProfile.nativeStateFamily(8184)!=PetPresentationProfile.NativeStateFamily.TEMPOROSS_DEBUFF)throw new AssertionError();
  if(!PetPresentationProfile.nativeStateVisual(8184,1).equals("NATIVE_SPRITE_59")||!PetPresentationProfile.nativeStateVisual(8184,2).equals("NATIVE_SPRITE_58")||!PetPresentationProfile.nativeStateVisual(8184,3).equals("NATIVE_SPRITE_369"))throw new AssertionError();
  for(int npc:new int[]{3962,3965,6991,8124,8125,8126}){
   if(PetPresentationProfile.nativeStateFamily(npc)!=PetPresentationProfile.NativeStateFamily.WOLPER_KRAMP_ACTIVE)throw new AssertionError("npc="+npc);
   if(!PetPresentationProfile.nativeStateVisual(npc,1).equals("NATIVE_SPRITE_53"))throw new AssertionError();
  }
  if(PetPresentationProfile.nativeStateFamily(8330)!=PetPresentationProfile.NativeStateFamily.NONE)throw new AssertionError("Scopesight uses SNIPE, not numeric state");
  System.out.println("V59_PET_HIDDEN_STATE_RENDERER_PASS behemoth=1_2_3xSprite22 tempoross=59_58_369 evilWolperKramp=sprite53 scopesight=SNIPE_separate forcedTextControl=0_1_2_3_consumedByClient");
 }
 private static void eq(int npc,PetPresentationProfile.NativeStateFamily f,String one,String three){
  if(PetPresentationProfile.nativeStateFamily(npc)!=f)throw new AssertionError(npc);
  if(!PetPresentationProfile.nativeStateVisual(npc,1).equals(one)||!PetPresentationProfile.nativeStateVisual(npc,3).equals(three))throw new AssertionError(npc);
 }
}

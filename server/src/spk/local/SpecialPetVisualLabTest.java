package spk.local;
public final class SpecialPetVisualLabTest{
 public static void main(String[]a){
  String y=SpecialPetVisualLab.info(1334,null),w=SpecialPetVisualLab.info(8210,3);
  if(!y.contains("alpha=150")||!y.contains("aI=319770")||!y.contains("ownerCopyEligible=interactionTarget>=32768"))throw new AssertionError(y);
  if(!w.contains("DYNAMIC_BLUE_VIOLET")||!w.contains("Client.fg")||!w.contains("PLAYER_MORPH_UNSAFE"))throw new AssertionError(w);
  String x=SpecialPetVisualLab.inspectOnly("alpha",1334);if(!x.contains("INSPECT_ONLY"))throw new AssertionError(x);
  System.out.println("V593_SPECIAL_PET_VISUAL_LAB_PASS yoshiganger1334_ownerCopy=true alpha150=true aI319770=true wondrous8210_dynamicClientFg=true hardcodedFieldsInspectOnly=true");
 }
}

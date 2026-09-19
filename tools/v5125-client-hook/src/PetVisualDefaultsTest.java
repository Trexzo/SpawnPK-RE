public final class PetVisualDefaultsTest {
 public static void main(String[] a)throws Exception{
   Class<?> c=Class.forName("spk.dev.PetVisualOverrides");
   java.lang.reflect.Method t=c.getMethod("defaultBodyTintRgb",long.class);
   java.lang.reflect.Method f=c.getMethod("defaultIntrinsicFxEnabled",long.class);
   if(((Integer)t.invoke(null,1334L)).intValue()!=0x00ff00)throw new AssertionError("yoshi tint");
   if(((Integer)t.invoke(null,8210L)).intValue()!=-1)throw new AssertionError("wondrous native bodycycle");
   if(((Boolean)f.invoke(null,1334L)).booleanValue())throw new AssertionError("yoshi fx should default off");
   if(((Boolean)f.invoke(null,8210L)).booleanValue())throw new AssertionError("wondrous fx should default off");
   if(!((Boolean)f.invoke(null,1335L)).booleanValue())throw new AssertionError("regular doppel native fx retained");
   System.out.println("V5125_SPECIAL_PET_VISUAL_DEFAULTS_PASS yoshiTint=00ff00 yoshiIntrinsicFx=false wondrousBody=NATIVE_DYNAMIC wondrousIntrinsicFx=false regularDoppelNativeFx=true");
 }
}

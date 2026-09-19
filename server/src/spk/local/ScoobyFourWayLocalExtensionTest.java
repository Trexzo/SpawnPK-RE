package spk.local;
import java.lang.reflect.*;import java.util.*;
public final class ScoobyFourWayLocalExtensionTest{
  public static void main(String[] args)throws Exception{
    Method m=LocalSession.class.getDeclaredMethod("petColorFamily",int.class);m.setAccessible(true);
    int[] expected={24016,24017,24018,24019};
    for(int current:expected){
      int[] x=(int[])m.invoke(null,current);
      if(!Arrays.equals(x,expected))throw new AssertionError("current="+current+" got="+Arrays.toString(x));
    }
    System.out.println("V5129_SCOOBY_FOUR_WAY_EXTENSION_PASS variants=24016..24019 choices=all4 labels=colors compatibilityExtension=true");
  }
}

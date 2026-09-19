package spk.local;

import java.lang.reflect.*;
import java.util.*;

/**
 * Exact-current-client proof for the special Completionist-cape appearance extension.
 * rs.a.k.b(int) hardcodes 23063/21963/21964 and its model path dereferences aS/aI;
 * those fields are initialized only by the final six-selector appearance extension.
 */
public final class GrandCompCapeAppearanceClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> playerClass=Class.forName("rs.a.k");
        Class<?> bufferClass=Class.forName("rs.x.e");
        Class<?> colorClass=Class.forName("rs.n.c.w");

        // The real UI initializes this palette during normal client startup. The
        // headless parser fixture supplies only the four entries used by the exact
        // native default Completionist-cape colour vector.
        Field g=colorClass.getDeclaredField("g"); g.setAccessible(true);
        @SuppressWarnings("unchecked") Map<Integer,String> palette=(Map<Integer,String>)g.get(null);
        palette.put(63015,"FF9620"); // selector 13 -> 6015
        palette.put(63011,"940000"); // selector 9  -> 924
        palette.put(63009,"DFD6D7"); // selector 7  -> 62575
        palette.put(63007,"0");      // selector 5  -> raw 0

        Method special=playerClass.getMethod("b",int.class);
        if(!(Boolean)special.invoke(null,23063) || !(Boolean)special.invoke(null,21963) || !(Boolean)special.invoke(null,21964))
            throw new AssertionError("pinned special-cape set changed");
        if((Boolean)special.invoke(null,22123)) throw new AssertionError("ordinary wings flagged special");

        int[] appearance=new int[12]; Arrays.fill(appearance,-1);
        appearance[EquipmentSlot.CAPE.appearanceIndex]=23063;
        byte[] data=BootstrapPackets.appearanceBlock("opensrc",appearance);
        Object player=playerClass.getConstructor().newInstance();
        Object buffer=bufferClass.getConstructor(byte[].class).newInstance((Object)data);
        playerClass.getMethod("a",bufferClass).invoke(player,buffer);
        int consumed=bufferClass.getField("h").getInt(buffer);
        if(consumed!=data.length) throw new AssertionError("consumed="+consumed+" len="+data.length);

        int[] colors=(int[])field(playerClass,"aS").get(player);
        if(colors==null || colors.length!=1206) throw new AssertionError("special cape aS not initialized");
        int aD=field(playerClass,"aD").getInt(player);
        int aE=field(playerClass,"aE").getInt(player);
        int aF=field(playerClass,"aF").getInt(player);
        int aG=field(playerClass,"aG").getInt(player);
        int aH=field(playerClass,"aH").getInt(player);
        int aI=field(playerClass,"aI").getInt(player);
        if(aD!=6015 || aE!=924 || aF!=62575 || aG!=924 || aH!=62575 || aI!=0)
            throw new AssertionError("comp colors="+Arrays.asList(aD,aE,aF,aG,aH,aI));
        int[] br=(int[])playerClass.getField("br").get(player);
        if(br[1] != 512+23063) throw new AssertionError("cape appearance="+br[1]);

        System.out.println("V53_GRAND_COMP_CAPE_APPEARANCE_CLIENT_PARITY_PASS item=23063 capeSlot=1"
            +" specialSet=23063,21963,21964 extension=true selectors=13,9,7,9,7,5"
            +" resolvedColors=6015,924,62575,924,62575,0 aS=1206 consumed="+consumed);
    }
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
}

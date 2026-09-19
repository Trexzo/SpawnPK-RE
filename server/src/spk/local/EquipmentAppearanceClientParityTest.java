package spk.local;

import java.lang.reflect.*;
import java.util.Arrays;

/** Pinned-client proof for v5.1.1 SpawnPK-local scythe sequence + seven-field pose serialization. */
public final class EquipmentAppearanceClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> clientClass = Class.forName("rs.Client");
        Class<?> playerClass = Class.forName("rs.a.k");
        Class<?> bufferClass = Class.forName("rs.x.e");
        // The verified 808 -> 11973 weapon correction is guarded by rs.f.a.aj == false.
        // Force that exact branch in this isolated parser test so the bytecode contract
        // is tested rather than depending on unrelated static initialization order.
        Class<?> clientModeClass = Class.forName("rs.f.a");
        Field mode = clientModeClass.getDeclaredField("aj");
        mode.setAccessible(true);
        mode.setBoolean(null, false);

        EquipmentState equipment = new EquipmentState();
        int[] worn = equipment.appearanceItems();
        EquipmentPoseProfile profile = EquipmentPoseRepository.forAppearance(worn);
        int[] anim = BootstrapPackets.appearanceAnimations(worn);
        int[] expectedScythe = {15692,823,1146,820,821,822,1210};
        if (!Arrays.equals(anim, expectedScythe)) throw new AssertionError("Bloodrend pose="+Arrays.toString(anim));
        if (!"SCYTHE_SPAWNPK_FAMILY".equals(profile.name)) throw new AssertionError("profile="+profile.name);
        if (!profile.weaponSpecificComplete()) throw new AssertionError("production profile not complete");

        byte[] rawAppearance = BootstrapPackets.appearanceBlock("localtest", worn);
        Object directPlayer = playerClass.getConstructor().newInstance();
        Object directBuffer = bufferClass.getConstructor(byte[].class).newInstance((Object) rawAppearance);
        playerClass.getMethod("a", bufferClass).invoke(directPlayer, directBuffer);
        int[] directBr = (int[]) playerClass.getField("br").get(directPlayer);
        if (directBr[EquipmentState.WEAPON_SLOT] != 512 + EquipmentState.BLOODREND_ID)
            throw new AssertionError("direct Bloodrend appearance=" + directBr[EquipmentState.WEAPON_SLOT]);
        assertPoseFields(playerClass,directPlayer,expectedScythe,"Bloodrend");

        Object client = unsafeAllocate(clientClass);
        Object player = playerClass.getConstructor().newInstance();
        Object players = Array.newInstance(playerClass, 2048);
        Array.set(players, 2047, player);
        setStatic(clientClass, "do", players);
        setStatic(clientClass, "eR", player);
        setField(client, clientClass, "kx", new int[2048]);
        setField(client, clientClass, "ky", Array.newInstance(bufferClass, 2048));
        setField(client, clientClass, "kv", new int[2048]);
        setField(client, clientClass, "jO", new int[2048]);

        Method packet81 = clientClass.getDeclaredMethod("b", int.class, bufferClass);
        packet81.setAccessible(true);

        byte[] login81 = BootstrapPackets.player81TeleportWithAppearance(0, 55, 55, "localtest", worn);
        parse81(packet81, client, bufferClass, login81);
        int[] br = (int[]) playerClass.getField("br").get(player);
        if (br[3] != 29038) throw new AssertionError("packet81 Bloodrend br3=" + br[3]);
        assertPoseFields(playerClass,player,expectedScythe,"Bloodrend packet81");

        // Whip is directly production-observed with walk/run 1660/1661. LocalLab
        // sends that full production pose while the exact current client still applies
        // its own verified stand correction 808 -> 11973 for item 4151.
        int[] whip = worn.clone();
        whip[3] = 4151;
        int[] whipWire = BootstrapPackets.appearanceAnimations(whip);
        int[] expectedWire = {808,823,1660,820,821,822,1661};
        if (!Arrays.equals(whipWire, expectedWire)) throw new AssertionError("whip wire pose="+Arrays.toString(whipWire));
        byte[] refresh81 = BootstrapPackets.player81AppearanceOnly("localtest", whip);
        parse81(packet81, client, bufferClass, refresh81);
        br = (int[]) playerClass.getField("br").get(player);
        if (br[3] != 512 + 4151) throw new AssertionError("mask-only whip br3=" + br[3]);
        int clientWhipStand = intField(playerClass,player,"t");
        if (clientWhipStand != 11973) throw new AssertionError("client whip stand correction="+clientWhipStand);
        if (intField(playerClass,player,"u")!=823 || intField(playerClass,player,"ag")!=1660 || intField(playerClass,player,"p")!=1661)
            throw new AssertionError("whip production-observed walk/run pose fields not retained");

        System.out.println("V52_EQUIPMENT_APPEARANCE_CLIENT_PARITY_PASS"
                         + " bloodrendItem=28526 weaponSlot=3 bloodrendAppearance=29038"
                         + " pose7="+Arrays.toString(expectedScythe)+" poseSpecificMask=0x7f complete=true"
                         + " whipItem=4151 wirePose=[808,823,1660,820,821,822,1661] clientCorrectedStand=11973"
                         + " login81=" + login81.length + " refresh81=" + refresh81.length);
    }

    private static void assertPoseFields(Class<?> playerClass,Object player,int[] expected,String label) throws Exception {
        String[] fields={"t","u","ag","ah","ai","aj","p"};
        for(int i=0;i<fields.length;i++) {
            int actual=intField(playerClass,player,fields[i]);
            if(actual!=expected[i]) throw new AssertionError(label+" "+fields[i]+"="+actual+" expected="+expected[i]);
        }
    }

    private static int intField(Class<?> c,Object o,String name) throws Exception {
        return c.getField(name).getInt(o);
    }

    private static void parse81(Method packet81, Object client, Class<?> bufferClass, byte[] payload) throws Exception {
        Object buffer = bufferClass.getConstructor(byte[].class).newInstance((Object) payload);
        packet81.invoke(client, payload.length, buffer);
        int consumed = bufferClass.getField("h").getInt(buffer);
        if (consumed != payload.length) throw new AssertionError("packet81 consumed=" + consumed + " len=" + payload.length);
    }

    private static Object unsafeAllocate(Class<?> c) throws Exception {
        Class<?> uc = Class.forName("sun.misc.Unsafe");
        Field f = uc.getDeclaredField("theUnsafe"); f.setAccessible(true);
        Object u = f.get(null);
        return uc.getMethod("allocateInstance", Class.class).invoke(u, c);
    }
    private static void setField(Object o, Class<?> c, String name, Object value) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(o,value);
    }
    private static void setStatic(Class<?> c, String name, Object value) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(null,value);
    }
}

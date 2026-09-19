package spk.local;

import java.lang.reflect.*;
import java.util.*;

/** Exact-client parser proof for simultaneous head + weapon + feet equipment. */
public final class EquipmentMultiSlotAppearanceClientParityTest {
    public static void main(String[] args) throws Exception {
        EquipmentState eq = new EquipmentState();
        EquipmentMetadataRepository.Meta head = EquipmentMetadataRepository.resolve(27034);
        EquipmentMetadataRepository.Meta feet = EquipmentMetadataRepository.resolve(27486);
        if (head == null || feet == null) throw new AssertionError("custom slot metadata missing");
        eq.equip(head,27034);
        eq.equip(feet,27486);

        int[] appearance = eq.appearanceItems();
        if (appearance[0] != 27034 || appearance[3] != 28526 || appearance[10] != 27486)
            throw new AssertionError("appearance map="+Arrays.toString(appearance));
        int[] container = eq.containerItems();
        if (container[0] != 27034 || container[3] != 28526 || container[10] != 27486 || container.length != 14)
            throw new AssertionError("container map="+Arrays.toString(container));

        Class<?> playerClass=Class.forName("rs.a.k"), bufferClass=Class.forName("rs.x.e");
        Object player=playerClass.getConstructor().newInstance();
        byte[] raw=BootstrapPackets.appearanceBlock("localtest",appearance);
        Object buffer=bufferClass.getConstructor(byte[].class).newInstance((Object)raw);
        playerClass.getMethod("a",bufferClass).invoke(player,buffer);
        int consumed=bufferClass.getField("h").getInt(buffer);
        if(consumed!=raw.length)throw new AssertionError("appearance consumed="+consumed+" len="+raw.length);
        int[] br=(int[])playerClass.getField("br").get(player);
        if(br[0] != 512+27034 || br[3] != 512+28526 || br[10] != 512+27486)
            throw new AssertionError("client br="+Arrays.toString(br));
        // Full-helm policy suppresses the default hair and beard identity-kit positions.
        if(br[8] != 0 || br[11] != 0)
            throw new AssertionError("full helm failed to suppress hair/beard br8="+br[8]+" br11="+br[11]);

        byte[] eq53=BootstrapPackets.equipmentContainer53(container);
        if(Binary.u16(eq53,0)!=1688 || Binary.u16(eq53,2)!=14)
            throw new AssertionError("equipment53 header");
        int[] h=itemAt53(eq53,0), w=itemAt53(eq53,3), f=itemAt53(eq53,10);
        if(h[0]!=27034 || w[0]!=28526 || f[0]!=27486)
            throw new AssertionError("equipment53 items head="+Arrays.toString(h)+" weapon="+Arrays.toString(w)+" feet="+Arrays.toString(f));

        System.out.println("V52_MULTI_SLOT_APPEARANCE_CLIENT_PARITY_PASS"
            + " equipment1688Slots=14 head27034=true weapon28526=true feet27486=true"
            + " packet81Head0Weapon3Feet10=true fullHelmHairBeardSuppressed=true");
    }

    private static int[] itemAt53(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255){int p0=p[off++]&255,p1=p[off++]&255,p2=p[off++]&255,p3=p[off++]&255;q=(p1<<24)|(p0<<16)|(p3<<8)|p2;}int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(i==wanted)return new int[]{id,q};}throw new AssertionError();}
}

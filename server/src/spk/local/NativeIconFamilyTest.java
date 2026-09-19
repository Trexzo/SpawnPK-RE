package spk.local;

/** Certified V9.08 icon examples + clone-lineage classifier regression. */
public final class NativeIconFamilyTest {
    public static void main(String[] args)throws Exception{
        int[] icons={10556,10557,10558,10559,24184,24185,24187,27454,24239,27393,23631,26125,27560,27427};
        for(int id:icons)if(!ItemCatalog.isNativePlayerIcon(id))throw new AssertionError("missing native icon id="+id+" name="+ItemCatalog.name(id));
        int[] ordinaryAmmo={882,11212,877,9341};
        for(int id:ordinaryAmmo)if(ItemCatalog.isNativePlayerIcon(id))throw new AssertionError("ordinary ammo classified icon id="+id);
        System.out.println("V58_NATIVE_ICON_FAMILY_PASS icons="+icons.length+" roots=10556..10559 collection5=27454 ordinaryAmmoExcluded=true");
    }
}

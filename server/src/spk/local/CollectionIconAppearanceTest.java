package spk.local;

/** Supersedes the stale ammo-as-cosmetic regression with the exact dedicated COSMETIC contract. */
public final class CollectionIconAppearanceTest {
    public static void main(String[] args){
        EquipmentState e=new EquipmentState();
        e.setStack(EquipmentSlot.AMMO,11212,420);
        PlayerState p=new PlayerState();
        p.cosmetic().set(27454);
        p.syncEquipmentPresentation(e);
        if(e.itemAt(EquipmentSlot.AMMO)!=11212)throw new AssertionError("ammo changed");
        if(e.quantityAt(EquipmentSlot.AMMO)!=420)throw new AssertionError("ammo qty changed");
        if(p.nativeIconItemId()!=27454)throw new AssertionError("expected dedicated bs=27454 got="+p.nativeIconItemId());
        p.cosmetic().clear();p.syncEquipmentPresentation(e);
        if(p.nativeIconItemId()!=-1||e.itemAt(EquipmentSlot.AMMO)!=11212)throw new AssertionError("cosmetic clear touched ammo");
        System.out.println("V511_COLLECTION_ICON_COSMETIC_PASS dedicatedBs=true ammoIndependent=true arrows11212x420=true collection27454=true");
    }
}

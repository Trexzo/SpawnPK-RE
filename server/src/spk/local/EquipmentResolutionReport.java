package spk.local;

/** Emits the current explicit Wear/Wield/Equip corpus and its LocalLab resolution. */
public final class EquipmentResolutionReport {
    private EquipmentResolutionReport() {}

    public static void main(String[] args) {
        System.out.println("itemId\tname\tactions\tslot\ttwoHanded\tcoverage\tevidence");
        for (ItemCatalog.Meta item : ItemCatalog.all()) {
            if (!(ItemCatalog.canWieldOrWear(item.id))) continue;
            EquipmentMetadataRepository.Meta m = EquipmentMetadataRepository.resolve(item.id);
            System.out.print(item.id); System.out.print('\t');
            System.out.print(clean(item.name)); System.out.print('\t');
            System.out.print(clean(java.util.Arrays.toString(item.actions))); System.out.print('\t');
            if (m == null) {
                System.out.println("UNRESOLVED\tfalse\tNONE\tUNRESOLVED");
            } else {
                System.out.print(m.slot); System.out.print('\t');
                System.out.print(m.twoHanded); System.out.print('\t');
                System.out.print(m.coverage); System.out.print('\t');
                System.out.println(clean(m.evidence));
            }
        }
    }

    private static String clean(Object v) {
        return v == null ? "" : String.valueOf(v).replace('\t',' ').replace('\n',' ').replace('\r',' ');
    }
}

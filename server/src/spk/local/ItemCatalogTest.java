package spk.local;

public final class ItemCatalogTest {
    public static void main(String[] args) throws Exception {
        if (ItemCatalog.count() != 28673) throw new AssertionError("count="+ItemCatalog.count());
        ItemCatalog.Meta bloodrend=ItemCatalog.get(28526);
        if (bloodrend==null || !"Scythe of bloodrend".equals(bloodrend.name))
            throw new AssertionError("bloodrend="+(bloodrend==null?null:bloodrend.name));
        if (!ItemCatalog.canWieldOrWear(28526)) throw new AssertionError("Bloodrend Wield action missing");
        if (!ItemCatalog.isScytheFamily(28526)) throw new AssertionError("Bloodrend clone family missing");
        if (ItemCatalog.isScytheFamily(28524)) throw new AssertionError("Sanguine scythe kit must not be equipment family");
        if (!ItemCatalog.isStackable(995)) throw new AssertionError("coins stackability");
        if (ItemCatalog.isStackable(28526)) throw new AssertionError("Bloodrend stackability");
        System.out.println("V41_ITEM_CATALOG_PASS count="+ItemCatalog.count()
                         +" bloodrend=28526 wield=true scytheFamily=true scytheKitRejected=true"
                         +" genericInventoryIds=28673");
    }
}

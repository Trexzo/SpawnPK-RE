package spk.local;
public final class GroundItemSemanticTest {
 public static void main(String[]a){
  if(!"Take".equals(GroundItemActionRepository.action(995,3)))throw new AssertionError("default Take");
  if(!"Light".equals(GroundItemActionRepository.action(1511,4)))throw new AssertionError("logs Light");
  if(!"Study".equals(GroundItemActionRepository.action(8334,1)))throw new AssertionError("lectern Study");
  if(!"Drop".equalsIgnoreCase(ItemActionResolver.inventoryOption5Semantic(995)))throw new AssertionError("coins default drop");
  int n=InventoryOption5Repository.count();
  if(n!=13839)throw new AssertionError("expected exact non-drop corpus 13839 after retiring invalid client id29999 Dwarf-remains row, got "+n);
  if(!"Destroy".equalsIgnoreCase(ItemActionResolver.inventoryOption5Semantic(0)))throw new AssertionError("item0 Destroy");
  if(!"Drop".equalsIgnoreCase(ItemActionResolver.inventoryOption5Semantic(29999)))throw new AssertionError("Voidglass R3 item29999 must Drop");
  System.out.println("V5185_GROUND_ITEM_SEMANTIC_PASS nonDropCorpus=13839 retiredDefault29999Destroy=true voidglass29999Drop=true");
 }
}

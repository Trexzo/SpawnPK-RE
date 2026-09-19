package spk.local;
public final class R85ActionProbe { public static void main(String[] a){
 System.out.println("name="+ItemDefinitionRepository.name(29999));
 System.out.println("act4="+ItemActionResolver.inventoryAction(29999,4));
 System.out.println("explicit5="+InventoryOption5Repository.explicitAction(29999));
 System.out.println("sem5="+ItemActionResolver.inventoryOption5Semantic(29999));
}}

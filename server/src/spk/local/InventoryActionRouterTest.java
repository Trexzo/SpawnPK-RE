package spk.local;

public final class InventoryActionRouterTest {
    private static void req(boolean ok, String msg){ if(!ok) throw new AssertionError(msg); }
    public static void main(String[] args){
        req("Wield".equalsIgnoreCase(ItemActionResolver.inventoryAction(21560,1)), "Kellatha option2");
        req("Defuse".equalsIgnoreCase(ItemActionResolver.inventoryAction(21560,2)), "Kellatha option3");
        req("Override".equalsIgnoreCase(ItemActionResolver.inventoryAction(21560,3)), "Kellatha option4");
        req("Override".equalsIgnoreCase(ItemActionResolver.inventoryAction(22132,2)), "Owner partyhat option3");

        InventoryActionRouter.Resolution kDef = InventoryActionRouter.resolve(new ItemContainerAction(16,3214,4,21560,0,"ITEM_OPTION_3"));
        InventoryActionRouter.Resolution kOver = InventoryActionRouter.resolve(new ItemContainerAction(75,3214,4,21560,0,"ITEM_OPTION_4"));
        InventoryActionRouter.Resolution pOver = InventoryActionRouter.resolve(new ItemContainerAction(16,3214,4,22132,0,"ITEM_OPTION_3"));
        req(kDef.optionIndex==2 && kDef.is("Defuse"), "C2S16 Kellatha must be Defuse: "+kDef);
        req(kOver.optionIndex==3 && kOver.is("Override"), "C2S75 Kellatha must be Override: "+kOver);
        req(pOver.optionIndex==2 && pOver.is("Override"), "C2S16 partyhat must be Override: "+pOver);
        req(!kDef.is("Override"), "Defuse must never alias Override");
        System.out.println("V51842_INVENTORY_ACTION_ROUTER_PASS kellatha16=Defuse kellatha75=Override ownerPartyhat16=Override opcodeSemanticSeparation=true");
    }
}

package spk.local;

import java.util.*;

/**
 * Explicit source-only recipe adapter for BankState. No packets, XP or
 * client action registration. Caller must supply verified stackability.
 * Call only on the owning World thread; no concurrent inventory mutation.
 */
final class RecipeBankSemanticAdapter {
    private RecipeBankSemanticAdapter(){}
    static RecipeStackPostimagePlanner.Postimage prepare(
        BankState bank,Map<Integer,Integer> ingredients,
        int product,int perBatch,int batches,Set<Integer> stackable
    ){
        Objects.requireNonNull(bank,"bank");
        int n=bank.inventoryCapacity();
        int[] ids=new int[n],qty=new int[n];
        for(int i=0;i<n;i++){
            BankState.Stack stack=bank.inventoryAt(i);
            ids[i]=stack==null?-1:stack.itemId;
            qty[i]=stack==null?0:stack.qty;
        }
        return RecipeStackPostimagePlanner.prepare(
            ids,qty,ingredients,product,perBatch,batches,stackable);
    }
    static void commit(BankState bank,
        RecipeStackPostimagePlanner.Postimage image
    ){
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(image,"image");
        bank.replaceInventorySemantic(image.ids,image.quantities);
    }
}

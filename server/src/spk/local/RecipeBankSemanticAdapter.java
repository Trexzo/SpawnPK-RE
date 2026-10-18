package spk.local;

import java.util.*;

/**
 * Explicit source-only recipe adapter for BankState. No packets, XP or
 * client action registration. Caller must supply verified stackability.
 * Call only on the owning World thread; no concurrent inventory mutation.
 */
final class RecipeBankSemanticAdapter {
    private RecipeBankSemanticAdapter(){}
    static final class Prepared {
        final int[] expectedIds,expectedQuantities;
        final RecipeStackPostimagePlanner.Postimage proposed;
        Prepared(int[] ids,int[] quantities,RecipeStackPostimagePlanner.Postimage image){
            expectedIds=ids.clone();expectedQuantities=quantities.clone();proposed=image;
        }
    }
    static Prepared prepare(
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
        return new Prepared(ids,qty,RecipeStackPostimagePlanner.prepare(
            ids,qty,ingredients,product,perBatch,batches,stackable));
    }
    static void commit(BankState bank,
        Prepared prepared
    ){
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(prepared,"prepared");
        if(bank.inventoryCapacity()!=prepared.expectedIds.length)
            throw new IllegalStateException("RECIPE_INVENTORY_CAPACITY_CHANGED");
        for(int i=0;i<prepared.expectedIds.length;i++){
            BankState.Stack current=bank.inventoryAt(i);
            int id=current==null?-1:current.itemId;
            int qty=current==null?0:current.qty;
            if(id!=prepared.expectedIds[i]||qty!=prepared.expectedQuantities[i])
                throw new IllegalStateException("RECIPE_STALE_INVENTORY_POSTIMAGE");
        }
        bank.replaceInventorySemantic(prepared.proposed.ids,prepared.proposed.quantities);
    }
}

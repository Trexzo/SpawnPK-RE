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
        final BankState owner;
        final int[] expectedIds,expectedQuantities;
        final RecipeStackPostimagePlanner.Postimage proposed;
        final int[] sealedIds,sealedQuantities;
        Prepared(BankState owner,int[] ids,int[] quantities,RecipeStackPostimagePlanner.Postimage image){
            this.owner=Objects.requireNonNull(owner,"owner");
            expectedIds=ids.clone();expectedQuantities=quantities.clone();
            proposed=image;
            sealedIds=image.ids.clone();sealedQuantities=image.quantities.clone();
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
        return new Prepared(bank,ids,qty,RecipeStackPostimagePlanner.prepare(
            ids,qty,ingredients,product,perBatch,batches,stackable));
    }
    static void commit(BankState bank,
        Prepared prepared
    ){
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(prepared,"prepared");
        if(bank!=prepared.owner)
            throw new IllegalStateException("RECIPE_DIFFERENT_INVENTORY_OWNER");
        if(bank.inventoryCapacity()!=prepared.expectedIds.length)
            throw new IllegalStateException("RECIPE_INVENTORY_CAPACITY_CHANGED");
        for(int i=0;i<prepared.expectedIds.length;i++){
            BankState.Stack current=bank.inventoryAt(i);
            int id=current==null?-1:current.itemId;
            int qty=current==null?0:current.qty;
            if(id!=prepared.expectedIds[i]||qty!=prepared.expectedQuantities[i])
                throw new IllegalStateException("RECIPE_STALE_INVENTORY_POSTIMAGE");
        }
        // Refuse mutation of the package-visible prepared postimage between
        // preparation and commit. Apply the privately copied sealed result.
        if(!Arrays.equals(prepared.sealedIds,prepared.proposed.ids)||
           !Arrays.equals(prepared.sealedQuantities,prepared.proposed.quantities))
            throw new IllegalStateException("RECIPE_PREPARED_POSTIMAGE_CHANGED");
        bank.replaceInventorySemantic(prepared.sealedIds.clone(),
            prepared.sealedQuantities.clone());
    }
}

package spk.local;

import java.util.*;

/**
 * Conservative arithmetic-only preflight for a future local recipe adapter.
 * This never mutates items, grants XP, or asserts exact-v308 recipe IDs.
 * Accounts for consumed tools overlapping an input item, unlike the donor's
 * separate per-input and per-tool affordability checks.
 */
final class RecipeConversionQuantityPlanner {
    private RecipeConversionQuantityPlanner(){}

    static int affordable(
        Map<Integer,Integer> inventory,
        Map<Integer,Integer> perRecipeInputs,
        Integer toolId, boolean consumeTool,
        int requested, int outputPerRecipe
    ){
        Objects.requireNonNull(inventory,"inventory");
        Objects.requireNonNull(perRecipeInputs,"perRecipeInputs");
        if(requested<=0||outputPerRecipe<=0||perRecipeInputs.isEmpty())
            return 0;
        Map<Integer,Long> required=new HashMap<>();
        for(Map.Entry<Integer,Integer> e:perRecipeInputs.entrySet()){
            Integer id=e.getKey(),qty=e.getValue();
            if(id==null||id<=0||qty==null||qty<=0)return 0;
            required.put(id,(long)qty);
        }
        if(toolId!=null){
            Integer toolOwned=inventory.get(toolId);
            if(toolId<=0||toolOwned==null||toolOwned<=0)return 0;
            if(consumeTool)
                required.merge(toolId,1L,Long::sum);
        }else if(consumeTool)return 0;
        int possible=Math.min(requested,Integer.MAX_VALUE/outputPerRecipe);
        for(Map.Entry<Integer,Long> e:required.entrySet()){
            Integer owned=inventory.get(e.getKey());
            if(owned==null||owned<0)return 0;
            // A retained tool that is also an ingredient must remain AFTER
            // the conversion. Reserve one permanent tool copy, then consume
            // only the remaining units as ingredients. A consumed tool is
            // already included in the per-batch requirement above.
            long reserved=(toolId!=null&&!consumeTool&&
                toolId.equals(e.getKey()))?1L:0L;
            long available=(long)owned-reserved;
            if(available<0)return 0;
            possible=(int)Math.min((long)possible,
                available/e.getValue());
        }
        return Math.max(0,possible);
    }
}

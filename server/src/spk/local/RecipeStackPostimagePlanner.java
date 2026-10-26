package spk.local;

import java.util.*;

/**
 * Non-mutating, protocol-independent stackable-item recipe postimage planner.
 * Only handles assets whose inventory behavior is explicitly supplied as
 * stackable. Nonstackable products and client-specific recipe rules require
 * a separate verified adapter. Never publishes packets or grants XP.
 */
final class RecipeStackPostimagePlanner {
    static final class Postimage {
        final int[] ids, quantities;
        Postimage(int[] ids,int[] quantities){
            this.ids=ids.clone();this.quantities=quantities.clone();
        }
    }
    private RecipeStackPostimagePlanner(){}

    /**
     * Source-only tool-aware adapter: a retained tool is reserved even when
     * its item ID is also consumed as an ingredient. A consumed tool is
     * charged once per batch and may only be a verified stackable item.
     * This does not establish item ID, skill XP or client recipe authority.
     */
    static Postimage prepareWithTool(
        int[] originalIds,int[] originalQuantities,
        Map<Integer,Integer> inputs,int outputId,int outputPerRecipe,
        int batches,Set<Integer> verifiedStackableIds,
        Integer toolId,boolean consumeTool
    ){
        Objects.requireNonNull(originalIds,"originalIds");
        Objects.requireNonNull(originalQuantities,"originalQuantities");
        Objects.requireNonNull(inputs,"inputs");
        Objects.requireNonNull(verifiedStackableIds,"verifiedStackableIds");
        if(toolId==null){
            if(consumeTool)
                throw new IllegalArgumentException(
                    "consumed recipe tool without an item ID");
            return prepare(originalIds,originalQuantities,inputs,
                outputId,outputPerRecipe,batches,verifiedStackableIds);
        }
        if(toolId<=0||toolId>=0xffff||
           !verifiedStackableIds.contains(toolId)||
           originalIds.length!=originalQuantities.length)
            throw new IllegalArgumentException(
                "unverified stackable recipe tool");
        long owned=0;
        for(int i=0;i<originalIds.length;i++){
            if(originalIds[i]==toolId){
                if(originalQuantities[i]<=0)
                    throw new IllegalArgumentException(
                        "malformed recipe tool inventory");
                owned+=originalQuantities[i];
            }
        }
        if(owned==0)
            throw new IllegalStateException("RECIPE_REQUIRED_TOOL_MISSING");
        Map<Integer,Integer> effective=new HashMap<>(inputs);
        if(consumeTool){
            if(!verifiedStackableIds.contains(toolId))
                throw new IllegalArgumentException(
                    "unverified consumed recipe tool");
            Integer ingredient=effective.get(toolId);
            if(ingredient!=null&&
               (ingredient<=0||ingredient==Integer.MAX_VALUE))
                throw new IllegalArgumentException(
                    "recipe tool quantity overflow");
            effective.put(toolId,ingredient==null?1:ingredient+1);
        }else{
            Integer overlappingIngredient=inputs.get(toolId);
            if(overlappingIngredient!=null){
                if(overlappingIngredient<=0||batches<=0)
                    throw new IllegalArgumentException(
                        "invalid retained recipe tool request");
                long required=Math.addExact(
                    Math.multiplyExact((long)overlappingIngredient,
                        (long)batches),1L);
                if(owned<required)
                    throw new IllegalStateException(
                        "RECIPE_RETAINED_TOOL_REQUIRED");
            }
        }
        return prepare(originalIds,originalQuantities,effective,
            outputId,outputPerRecipe,batches,verifiedStackableIds);
    }

    static Postimage prepare(int[] originalIds,int[] originalQuantities,
        Map<Integer,Integer> inputs,int outputId,int outputPerRecipe,
        int batches,Set<Integer> verifiedStackableIds
    ){
        Objects.requireNonNull(originalIds,"originalIds");
        Objects.requireNonNull(originalQuantities,"originalQuantities");
        Objects.requireNonNull(inputs,"inputs");
        Objects.requireNonNull(verifiedStackableIds,"verifiedStackableIds");
        if(originalIds.length==0||
           originalIds.length!=originalQuantities.length||
           inputs.isEmpty()||batches<=0||
           outputId<=0||outputId>=0xffff||outputPerRecipe<=0||
           !verifiedStackableIds.contains(outputId))
            throw new IllegalArgumentException("invalid recipe request");

        Map<Integer,Long> consume=new HashMap<>();
        for(Map.Entry<Integer,Integer> ingredient:inputs.entrySet()){
            Integer id=ingredient.getKey(),quantity=ingredient.getValue();
            if(id==null||id<=0||id>=0xffff||
               quantity==null||quantity<=0||
               !verifiedStackableIds.contains(id))
                throw new IllegalArgumentException("unverified ingredient");
            consume.put(id,Math.multiplyExact(
                (long)quantity,(long)batches));
        }
        long output=Math.multiplyExact((long)batches,(long)outputPerRecipe);
        if(output>Integer.MAX_VALUE)
            throw new IllegalArgumentException("output overflow");

        int[] ids=originalIds.clone(),counts=originalQuantities.clone();
        for(int i=0;i<ids.length;i++){
            if(ids[i]<-1 || ids[i]>=0xffff ||
               ids[i]==-1&&counts[i]!=0 ||
               ids[i]>=0&&counts[i]<=0)
                throw new IllegalArgumentException("malformed inventory");
            // The stackable-only planner has no nonstackable semantic
            // authority. Preserve unrelated items only if the caller has
            // explicitly verified their stackability, too. Otherwise use
            // the mixed-item planner with both evidence classifications.
            if(ids[i]>=0&&(ids[i]==0||
                !verifiedStackableIds.contains(ids[i])))
                throw new IllegalArgumentException(
                    "unverified occupied stackable inventory item");
        }
        for(Map.Entry<Integer,Long> ingredient:consume.entrySet()){
            long required=ingredient.getValue();
            for(int i=0;i<ids.length&&required>0;i++){
                if(ids[i]!=ingredient.getKey())continue;
                int take=(int)Math.min(required,(long)counts[i]);
                counts[i]-=take;required-=take;
                if(counts[i]==0)ids[i]=-1;
            }
            if(required!=0)
                throw new IllegalStateException("insufficient ingredient");
        }
        long remaining=output;
        for(int i=0;i<ids.length&&remaining>0;i++){
            if(ids[i]!=outputId)continue;
            int add=(int)Math.min(remaining,
                (long)Integer.MAX_VALUE-counts[i]);
            counts[i]+=add;remaining-=add;
        }
        for(int i=0;i<ids.length&&remaining>0;i++){
            if(ids[i]>=0)continue;
            int add=(int)Math.min(remaining,(long)Integer.MAX_VALUE);
            ids[i]=outputId;counts[i]=add;remaining-=add;
        }
        if(remaining>0)
            throw new IllegalStateException("output inventory full");
        return new Postimage(ids,counts);
    }
}

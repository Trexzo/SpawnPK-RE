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
           outputId<=0||outputPerRecipe<=0||
           !verifiedStackableIds.contains(outputId))
            throw new IllegalArgumentException("invalid recipe request");

        Map<Integer,Long> consume=new HashMap<>();
        for(Map.Entry<Integer,Integer> ingredient:inputs.entrySet()){
            Integer id=ingredient.getKey(),quantity=ingredient.getValue();
            if(id==null||id<=0||quantity==null||quantity<=0||
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
            if(ids[i]<-1 ||
               ids[i]==-1&&counts[i]!=0 ||
               ids[i]>=0&&counts[i]<=0)
                throw new IllegalArgumentException("malformed inventory");
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

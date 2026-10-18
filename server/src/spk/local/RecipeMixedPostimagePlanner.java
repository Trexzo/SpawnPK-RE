package spk.local;

import java.util.*;

/**
 * Pure, source-only recipe planner with explicitly classified stackable and
 * nonstackable items. No live recipe registry, XP, packet or account grants.
 * Every occupied input slot and every recipe item needs verified classification.
 */
final class RecipeMixedPostimagePlanner {
    private RecipeMixedPostimagePlanner(){}

    static final class Postimage {
        final int[] ids,quantities;
        private Postimage(int[] ids,int[] quantities){
            this.ids=ids.clone();
            this.quantities=quantities.clone();
        }
    }

    static Postimage prepare(
        int[] originalIds,int[] originalQuantities,
        Map<Integer,Integer> ingredients,
        int outputId,int outputPerBatch,int batches,
        Set<Integer> verifiedStackable,Set<Integer> verifiedNonstackable,
        Integer toolId,boolean consumeTool
    ){
        Objects.requireNonNull(originalIds,"originalIds");
        Objects.requireNonNull(originalQuantities,"originalQuantities");
        Objects.requireNonNull(ingredients,"ingredients");
        Objects.requireNonNull(verifiedStackable,"verifiedStackable");
        Objects.requireNonNull(verifiedNonstackable,"verifiedNonstackable");
        if(originalIds.length==0||
           originalIds.length!=originalQuantities.length||
           batches<=0||outputPerBatch<=0||
           !classified(outputId,verifiedStackable,verifiedNonstackable))
            throw new IllegalArgumentException("invalid mixed recipe request");
        if(toolId==null&&consumeTool)
            throw new IllegalArgumentException("consumed tool without ID");
        if(toolId!=null&&
           !classified(toolId,verifiedStackable,verifiedNonstackable))
            throw new IllegalArgumentException("unverified recipe tool");

        int[] ids=originalIds.clone(),counts=originalQuantities.clone();
        long ownedTool=0;
        for(int slot=0;slot<ids.length;slot++){
            int id=ids[slot],qty=counts[slot];
            if(id==-1){
                if(qty!=0)
                    throw new IllegalArgumentException("occupied empty slot");
                continue;
            }
            if(!classified(id,verifiedStackable,verifiedNonstackable)||
               qty<=0||
               verifiedNonstackable.contains(id)&&qty!=1)
                throw new IllegalArgumentException(
                    "unverified or malformed inventory slot="+slot);
            if(toolId!=null&&toolId==id)ownedTool+=qty;
        }
        if(toolId!=null&&ownedTool==0)
            throw new IllegalStateException("RECIPE_REQUIRED_TOOL_MISSING");

        Map<Integer,Long> perBatch=new HashMap<>();
        for(Map.Entry<Integer,Integer> entry:ingredients.entrySet()){
            Integer id=entry.getKey(),qty=entry.getValue();
            if(id==null||
               !classified(id,verifiedStackable,verifiedNonstackable)||
               qty==null||qty<=0)
                throw new IllegalArgumentException("unverified recipe input");
            perBatch.put(id,(long)qty);
        }
        if(consumeTool)perBatch.merge(toolId,1L,Long::sum);
        if(perBatch.isEmpty())
            throw new IllegalArgumentException("empty mixed recipe");

        long totalOutput=Math.multiplyExact((long)outputPerBatch,(long)batches);
        if(totalOutput>Integer.MAX_VALUE)
            throw new IllegalArgumentException("mixed output quantity overflow");
        // A retained tool overlapping consumed ingredients needs one copy
        // remaining after all batches. This checks totals, never a slot guess.
        if(toolId!=null&&!consumeTool&&perBatch.containsKey(toolId)){
            long required=Math.addExact(
                Math.multiplyExact(perBatch.get(toolId),(long)batches),1L);
            if(ownedTool<required)
                throw new IllegalStateException("RECIPE_RETAINED_TOOL_REQUIRED");
        }

        for(Map.Entry<Integer,Long> input:perBatch.entrySet()){
            int item=input.getKey();
            long remaining=Math.multiplyExact(input.getValue(),(long)batches);
            for(int slot=0;slot<ids.length&&remaining>0;slot++){
                if(ids[slot]!=item)continue;
                int take=(int)Math.min(remaining,(long)counts[slot]);
                counts[slot]-=take;
                remaining-=take;
                if(counts[slot]==0)ids[slot]=-1;
            }
            if(remaining!=0)
                throw new IllegalStateException("RECIPE_INPUTS_INSUFFICIENT");
        }

        long remaining=totalOutput;
        if(verifiedStackable.contains(outputId)){
            // Merge existing product stacks first, then consume empty slots.
            for(int slot=0;slot<ids.length&&remaining>0;slot++){
                if(ids[slot]!=outputId)continue;
                int added=(int)Math.min(remaining,
                    (long)Integer.MAX_VALUE-counts[slot]);
                counts[slot]+=added;
                remaining-=added;
            }
            for(int slot=0;slot<ids.length&&remaining>0;slot++){
                if(ids[slot]!=-1)continue;
                int added=(int)Math.min(remaining,
                    (long)Integer.MAX_VALUE);
                ids[slot]=outputId;
                counts[slot]=added;
                remaining-=added;
            }
        }else{
            // Each nonstackable output occupies one distinct empty slot.
            for(int slot=0;slot<ids.length&&remaining>0;slot++){
                if(ids[slot]!=-1)continue;
                ids[slot]=outputId;
                counts[slot]=1;
                remaining--;
            }
        }
        if(remaining!=0)
            throw new IllegalStateException("RECIPE_OUTPUT_CAPACITY");
        return new Postimage(ids,counts);
    }

    private static boolean classified(
        int id,Set<Integer> stackable,Set<Integer> nonstackable
    ){
        return id>0&&id<0xffff&&
            (stackable.contains(id)^nonstackable.contains(id));
    }
}

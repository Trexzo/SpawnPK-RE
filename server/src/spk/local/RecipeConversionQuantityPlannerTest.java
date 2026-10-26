package spk.local;

import java.util.*;

/** Source-only recipe quantity preflight regression; no inventory mutation. */
public final class RecipeConversionQuantityPlannerTest {
    private static void eq(int expected,int actual,String label){
        if(expected!=actual)throw new AssertionError(label+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args){
        Map<Integer,Integer> inventory=new HashMap<>();
        inventory.put(100,3);
        inventory.put(200,10);
        Map<Integer,Integer> ingredients=new HashMap<>();
        ingredients.put(100,1);
        eq(3,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,200,false,10,1),"kept tool");
        eq(1,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,100,true,10,1),"consumed overlapping input and tool");
        eq(0,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,999,false,10,1),"missing tool");
        // Overlap with a retained tool reserves one copy permanently;
        // allowing all three units to be consumed would discard the tool.
        eq(2,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,100,false,10,1),
            "retained overlapping tool must survive all batches");
        Map<Integer,Integer> oneTool=Collections.singletonMap(100,1);
        eq(0,RecipeConversionQuantityPlanner.affordable(
            oneTool,ingredients,100,false,10,1),
            "sole retained tool cannot also be consumed as ingredient");
        eq(1,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,100,true,10,1),
            "consumed overlapping tool must be deducted every batch");
        Map<Integer,Integer> brokenInventory=new HashMap<>(inventory);
        brokenInventory.put(100,null);
        eq(0,RecipeConversionQuantityPlanner.affordable(
            brokenInventory,ingredients,100,false,10,1),
            "null tool amount not fail closed");
        eq(0,RecipeConversionQuantityPlanner.affordable(
            brokenInventory,ingredients,null,false,10,1),
            "null ingredient amount not fail closed");
        eq(2,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,null,false,Integer.MAX_VALUE,
            Integer.MAX_VALUE/2),"output int bound");
        eq(0,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,null,false,0,1),"nonpositive count");
        eq(0,RecipeConversionQuantityPlanner.affordable(
            inventory,ingredients,null,false,2,0),"nonpositive output");
        eq(3,RecipeConversionQuantityPlanner.affordable(
            inventory,Collections.emptyMap(),100,true,10,1),
            "consumed tool as only conversion input");
        eq(0,RecipeConversionQuantityPlanner.affordable(
            inventory,Collections.emptyMap(),100,false,10,1),
            "empty recipe without consumed tool refused");
        eq(0,RecipeConversionQuantityPlanner.affordable(
            inventory,Collections.emptyMap(),null,true,10,1),
            "consumed tool-only recipe must name a tool");
        Map<Integer,Integer> snapshot=new HashMap<>(inventory);
        RecipeConversionQuantityPlanner.affordable(inventory,ingredients,100,true,10,2);
        if(!snapshot.equals(inventory))throw new AssertionError("preflight mutated inventory");
        regressPostimageParity();
        System.out.println("RECIPE_CONVERSION_QUANTITY_PLANNER_PASS"+
            " toolOverlap=true retainedToolReservation=true"+
            " nullCountsRejected=true toolOnlyConversion=true"+
            " overflowSafe=true noMutation=true"+
            " toolPlannerParityTrials=512"+
            " noLiveGameplayAuthority=true");
    }
    /**
     * The arithmetic preflight must agree with the actual atomic postimage
     * planner for synthetic inventories with guaranteed output capacity.
     * No real item IDs, recipes, client packets or account state are used.
     */
    private static void regressPostimageParity(){
        Random random=new Random(0x5EED2026L);
        Set<Integer> verified=new HashSet<>(
            Arrays.asList(100,200,300,400));
        Integer[] tools={null,100,200,300,999};
        int accepted=0,rejected=0,retainedOverlap=0,
            consumedOverlap=0,toolOnly=0;
        for(int trial=0;trial<512;trial++){
            int[] ids={100,200,300,-1,-1};
            int[] counts={
                1+random.nextInt(12),1+random.nextInt(12),
                1+random.nextInt(12),0,0
            };
            int[] beforeIds=ids.clone(),beforeCounts=counts.clone();
            Map<Integer,Integer> owned=new HashMap<>();
            for(int i=0;i<3;i++)owned.put(ids[i],counts[i]);

            Map<Integer,Integer> ingredients=new HashMap<>();
            if(random.nextInt(4)!=0)
                ingredients.put(ids[random.nextInt(3)],
                    1+random.nextInt(4));
            if(random.nextBoolean())
                ingredients.put(ids[random.nextInt(3)],
                    1+random.nextInt(3));
            Integer toolId=tools[random.nextInt(tools.length)];
            boolean consumeTool=toolId!=null&&random.nextBoolean();
            int requested=1+random.nextInt(7),perOutput=1+random.nextInt(3);
            int possible=RecipeConversionQuantityPlanner.affordable(
                owned,ingredients,toolId,consumeTool,requested,perOutput);
            if(possible>0){
                RecipeStackPostimagePlanner.Postimage post=
                    RecipeStackPostimagePlanner.prepareWithTool(
                        ids,counts,ingredients,400,perOutput,
                        possible,verified,toolId,consumeTool);
                long output=0;
                for(int slot=0;slot<post.ids.length;slot++)
                    if(post.ids[slot]==400)output+=post.quantities[slot];
                eq(possible*perOutput,(int)output,
                    "planner parity output trial="+trial);
                if(toolId!=null&&!consumeTool&&
                   ingredients.containsKey(toolId)){
                    long toolLeft=0;
                    for(int slot=0;slot<post.ids.length;slot++)
                        if(post.ids[slot]==toolId)
                            toolLeft+=post.quantities[slot];
                    if(toolLeft<1)
                        throw new AssertionError(
                            "retained overlapping tool lost trial="+trial);
                    retainedOverlap++;
                }
                if(toolId!=null&&consumeTool&&
                   ingredients.containsKey(toolId))
                    consumedOverlap++;
                if(ingredients.isEmpty()&&consumeTool)toolOnly++;
                accepted++;
            }
            if(possible<requested){
                boolean failed=false;
                try{
                    RecipeStackPostimagePlanner.prepareWithTool(
                        ids,counts,ingredients,400,perOutput,
                        possible+1,verified,toolId,consumeTool);
                }catch(IllegalArgumentException|IllegalStateException expected){
                    failed=true;
                }
                if(!failed)
                    throw new AssertionError(
                        "quantity planner rejected viable postimage trial="+
                        trial+" allowed="+possible);
                rejected++;
            }
            if(!Arrays.equals(ids,beforeIds)||
               !Arrays.equals(counts,beforeCounts))
                throw new AssertionError(
                    "postimage parity modified source trial="+trial);
        }
        if(accepted<100||rejected<100||retainedOverlap==0||
           consumedOverlap==0||toolOnly==0)
            throw new AssertionError(
                "parity fixture undercovered accepted="+accepted+
                " rejected="+rejected+
                " retainedOverlap="+retainedOverlap+
                " consumedOverlap="+consumedOverlap+
                " toolOnly="+toolOnly);
    }

    private RecipeConversionQuantityPlannerTest(){}
}

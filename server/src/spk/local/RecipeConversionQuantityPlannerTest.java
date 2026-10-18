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
        Map<Integer,Integer> snapshot=new HashMap<>(inventory);
        RecipeConversionQuantityPlanner.affordable(inventory,ingredients,100,true,10,2);
        if(!snapshot.equals(inventory))throw new AssertionError("preflight mutated inventory");
        System.out.println("RECIPE_CONVERSION_QUANTITY_PLANNER_PASS"+
            " toolOverlap=true retainedToolReservation=true"+
            " nullCountsRejected=true overflowSafe=true noMutation=true"+
            " noLiveGameplayAuthority=true");
    }
    private RecipeConversionQuantityPlannerTest(){}
}

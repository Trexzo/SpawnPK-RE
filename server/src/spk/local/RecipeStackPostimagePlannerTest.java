package spk.local;

import java.util.*;

/** Pure stack postimage tests: exact conservation, capacity and rollback. */
public final class RecipeStackPostimagePlannerTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("RECIPE_POSTIMAGE "+why);
    }
    private static void denied(Runnable task,String why){
        boolean failed=false;
        try{task.run();}catch(IllegalArgumentException|IllegalStateException expected){
            failed=true;
        }
        check(failed,why);
    }
    public static void main(String[] args){
        int[] ids={100,200,-1,-1},qty={5,7,0,0};
        int[] originalIds=ids.clone(),originalQty=qty.clone();
        Set<Integer> stackable=new HashSet<>(Arrays.asList(100,200,300));
        RecipeStackPostimagePlanner.Postimage a=
            RecipeStackPostimagePlanner.prepare(
                ids,qty,Collections.singletonMap(100,2),
                300,3,2,stackable);
        check(a.ids[0]==100&&a.quantities[0]==1&&
            a.ids[2]==300&&a.quantities[2]==6,
            "incorrect input/output postimage");
        check(Arrays.equals(ids,originalIds)&&Arrays.equals(qty,originalQty),
            "pure planner mutated input");
        denied(()->RecipeStackPostimagePlanner.prepare(
            ids,qty,Collections.singletonMap(100,3),
            300,1,2,stackable),"insufficient input not rejected");
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{100},new int[]{2},
            Collections.singletonMap(100,1),300,1,1,stackable),
            "output capacity failure not rejected");
        denied(()->RecipeStackPostimagePlanner.prepare(
            ids,qty,Collections.singletonMap(100,1),
            300,Integer.MAX_VALUE,2,stackable),
            "output overflow not rejected");
        denied(()->RecipeStackPostimagePlanner.prepare(
            ids,qty,Collections.singletonMap(100,1),
            999,1,1,stackable),
            "unverified output not rejected");
        RecipeStackPostimagePlanner.Postimage overlap=
            RecipeStackPostimagePlanner.prepare(
                new int[]{100,-1},new int[]{1,0},
                Collections.singletonMap(100,1),100,2,1,stackable);
        check(overlap.ids[0]==100&&overlap.quantities[0]==2,
            "overlapping input/output must use consumed postimage");
        System.out.println("RECIPE_STACK_POSTIMAGE_PASS"+
            " conservation=true rollbackByPrevalidation=true"+
            " overflowRejected=true noMutation=true"+
            " liveClientAuthority=false");
    }
    private RecipeStackPostimagePlannerTest(){}
}

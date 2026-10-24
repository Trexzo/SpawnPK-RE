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
        // Canonical empty slots use -1 only. Accepting arbitrary negative
        // values would silently normalize malformed caller inventory data.
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{-2,100},new int[]{0,2},
            Collections.singletonMap(100,1),300,1,1,stackable),
            "noncanonical negative empty slot accepted");
        RecipeStackPostimagePlanner.Postimage split=
            RecipeStackPostimagePlanner.prepare(
                new int[]{100,300,-1},
                new int[]{3,Integer.MAX_VALUE,0},
                Collections.singletonMap(100,1),300,10,1,stackable);
        check(split.ids[0]==100&&split.quantities[0]==2&&
            split.ids[1]==300&&split.quantities[1]==Integer.MAX_VALUE&&
            split.ids[2]==300&&split.quantities[2]==10,
            "maxed product stack did not spill into empty slot");
        RecipeStackPostimagePlanner.Postimage freed=
            RecipeStackPostimagePlanner.prepare(
                new int[]{100,300},new int[]{1,Integer.MAX_VALUE},
                Collections.singletonMap(100,1),300,1,1,stackable);
        check(freed.ids[0]==300&&freed.quantities[0]==1&&
            freed.quantities[1]==Integer.MAX_VALUE,
            "consumed ingredient slot not reused for maxed output");
        // The postimage itself must enforce tool reservations: checking
        // "affordable" separately is insufficient if a caller skips it.
        denied(()->RecipeStackPostimagePlanner.prepareWithTool(
            new int[]{100,-1},new int[]{4,0},
            Collections.singletonMap(100,2),300,3,2,stackable,
            100,false),"last retained tool consumed by ingredients");
        RecipeStackPostimagePlanner.Postimage retained=
            RecipeStackPostimagePlanner.prepareWithTool(
                new int[]{100,-1},new int[]{5,0},
                Collections.singletonMap(100,2),300,3,2,stackable,
                100,false);
        check(retained.ids[0]==100&&retained.quantities[0]==1&&
            retained.ids[1]==300&&retained.quantities[1]==6,
            "retained overlapping tool not preserved in postimage");
        denied(()->RecipeStackPostimagePlanner.prepareWithTool(
            new int[]{100,-1},new int[]{5,0},
            Collections.singletonMap(100,2),300,3,2,stackable,
            100,true),"consumed overlapping tool not added per batch");
        RecipeStackPostimagePlanner.Postimage consumed=
            RecipeStackPostimagePlanner.prepareWithTool(
                new int[]{100,-1},new int[]{7,0},
                Collections.singletonMap(100,2),300,3,2,stackable,
                100,true);
        check(consumed.ids[0]==100&&consumed.quantities[0]==1&&
            consumed.ids[1]==300&&consumed.quantities[1]==6,
            "consumed overlapping tool not charged once per batch");
        denied(()->RecipeStackPostimagePlanner.prepareWithTool(
            new int[]{100,200,-1},new int[]{5,1,0},
            Collections.singletonMap(100,2),300,3,2,stackable,
            999,false),"missing retained tool not denied");
        denied(()->RecipeStackPostimagePlanner.prepareWithTool(
            new int[]{100,200,-1},new int[]{5,1,0},
            Collections.singletonMap(100,2),300,3,2,stackable,
            null,true),"consumed null tool unexpectedly allowed");
        RecipeStackPostimagePlanner.Postimage distinctTool=
            RecipeStackPostimagePlanner.prepareWithTool(
                new int[]{100,200,-1},new int[]{5,1,0},
                Collections.singletonMap(100,2),300,3,2,stackable,
                200,false);
        check(distinctTool.ids[1]==200&&
            distinctTool.quantities[1]==1,
            "retained unrelated tool changed by conversion");
        denied(()->RecipeStackPostimagePlanner.prepareWithTool(
            new int[]{100,555,-1},new int[]{5,1,0},
            Collections.singletonMap(100,2),300,1,1,stackable,
            555,false),"unverified retained tool accepted");
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{100,-1},new int[]{5,0},
            Collections.singletonMap(100,1),65535,1,1,
            new HashSet<>(Arrays.asList(100,65535))),
            "nonencodable output item accepted");
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{100,-1},new int[]{5,0},
            Collections.singletonMap(65535,1),300,1,1,
            new HashSet<>(Arrays.asList(100,300,65535))),
            "nonencodable input item accepted");
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{100,65535},new int[]{5,1},
            Collections.singletonMap(100,1),300,1,1,stackable),
            "nonencodable inventory item accepted");
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{100,555,-1},new int[]{5,1,0},
            Collections.singletonMap(100,1),300,1,1,
            stackable),
            "unverified unrelated occupied item accepted");
        denied(()->RecipeStackPostimagePlanner.prepare(
            new int[]{100,0,-1},new int[]{5,1,0},
            Collections.singletonMap(100,1),300,1,1,
            new HashSet<>(Arrays.asList(0,100,300))),
            "zero-ID occupied item accepted despite classification");
        RecipeStackPostimagePlanner.Postimage knownUnrelated=
            RecipeStackPostimagePlanner.prepare(
                new int[]{100,555,-1},new int[]{5,1,0},
                Collections.singletonMap(100,1),300,1,1,
                new HashSet<>(Arrays.asList(100,300,555)));
        check(knownUnrelated.ids[1]==555&&
            knownUnrelated.quantities[1]==1&&
            knownUnrelated.ids[2]==300&&
            knownUnrelated.quantities[2]==1,
            "verified unrelated stackable item not preserved");
        deterministicLedgerCases();
        System.out.println("RECIPE_STACK_POSTIMAGE_PASS"+
            " conservation=true rollbackByPrevalidation=true"+
            " overflowRejected=true canonicalSlots=true"+
            " maxStackSpill=true toolAwarePostimage=true"+
            " verifiedRetainedTool=true encodableItemIds=true"+
            " occupiedStackabilityEvidenceRequired=true"+
            " deterministicLedgerCases=1024"+
            " noMutation=true"+
            " liveClientAuthority=false");
    }
    // Deterministic broad variation: independently verify per-item ledger
    // conservation for every successful conversion, plus purity on all
    // outcomes. This is source-only synthetic arithmetic, not donor/v308
    // recipe authority or network acceptance.
    private static void deterministicLedgerCases(){
        Random random=new Random(0x5EED2026L);
        int[] items={100,200,300,400};
        Set<Integer> verified=new HashSet<>();
        for(int item:items)verified.add(item);
        int successes=0,insufficient=0,capacityDenied=0;
        for(int trial=0;trial<1024;trial++){
            int[] ids=new int[6],counts=new int[6];
            Map<Integer,Long> original=new HashMap<>();
            for(int slot=0;slot<ids.length;slot++){
                int chosen=random.nextInt(5);
                ids[slot]=chosen==4?-1:items[chosen];
                counts[slot]=ids[slot]<0?0:random.nextInt(15)+1;
                if(ids[slot]>=0)
                    original.merge(ids[slot],(long)counts[slot],Long::sum);
            }
            int[] initialIds=ids.clone(),initialCounts=counts.clone();
            Map<Integer,Integer> inputs=new HashMap<>();
            inputs.put(items[random.nextInt(items.length)],
                random.nextInt(4)+1);
            if(random.nextBoolean())
                inputs.put(items[random.nextInt(items.length)],
                    random.nextInt(4)+1);
            int product=items[random.nextInt(items.length)];
            int batches=random.nextInt(5)+1,outputPer=random.nextInt(5)+1;
            Map<Integer,Long> expected=new HashMap<>(original);
            boolean sufficient=true;
            for(Map.Entry<Integer,Integer> e:inputs.entrySet()){
                long used=(long)e.getValue()*batches;
                long left=expected.getOrDefault(e.getKey(),0L)-used;
                if(left<0)sufficient=false;
                expected.put(e.getKey(),left);
            }
            expected.merge(product,(long)outputPer*batches,Long::sum);
            try{
                RecipeStackPostimagePlanner.Postimage result=
                    RecipeStackPostimagePlanner.prepare(
                        ids,counts,inputs,product,outputPer,batches,verified);
                check(sufficient,
                    "trial "+trial+" accepted unaffordable ingredients");
                Map<Integer,Long> actual=new HashMap<>();
                check(result.ids.length==ids.length&&
                    result.quantities.length==counts.length,
                    "trial "+trial+" changed capacity");
                for(int slot=0;slot<result.ids.length;slot++){
                    int id=result.ids[slot],quantity=result.quantities[slot];
                    check(id==-1?quantity==0:quantity>0&&id>0,
                        "trial "+trial+" emitted malformed slot");
                    if(id>=0)
                        actual.merge(id,(long)quantity,Long::sum);
                }
                for(int item:items)
                    check(actual.getOrDefault(item,0L).equals(
                            expected.getOrDefault(item,0L)),
                        "trial "+trial+" violated conservation for "+item);
                successes++;
            }catch(IllegalStateException ex){
                if(!sufficient)insufficient++;
                else capacityDenied++;
            }
            check(Arrays.equals(ids,initialIds)&&
                Arrays.equals(counts,initialCounts),
                "trial "+trial+" changed original input");
        }
        check(successes>100&&insufficient>100&&capacityDenied>0,
            "deterministic fixture lacked outcome coverage success="+
            successes+" insufficient="+insufficient+" full="+capacityDenied);
    }
    private RecipeStackPostimagePlannerTest(){}
}

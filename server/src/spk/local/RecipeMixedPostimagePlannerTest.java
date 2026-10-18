package spk.local;

import java.util.*;

/** Deterministic source-only mixed-item conversion and conservation tests. */
public final class RecipeMixedPostimagePlannerTest {
    private RecipeMixedPostimagePlannerTest(){}

    private static void ok(boolean value,String reason){
        if(!value)throw new AssertionError("RECIPE_MIXED "+reason);
    }
    private static void denied(Runnable task,String reason){
        boolean blocked=false;
        try{task.run();}
        catch(IllegalStateException|IllegalArgumentException expected){
            blocked=true;
        }
        ok(blocked,reason);
    }
    private static final Set<Integer> STACKABLE=
        new HashSet<>(Arrays.asList(100,300));
    private static final Set<Integer> NONSTACKABLE=
        new HashSet<>(Arrays.asList(200,400));

    public static void main(String[] args){
        int[] ids={100,200,200,300,-1,-1};
        int[] qty={5,1,1,4,0,0};
        Map<Integer,Integer> ingredients=new HashMap<>();
        ingredients.put(100,1);
        ingredients.put(200,1);
        RecipeMixedPostimagePlanner.Postimage made=
            RecipeMixedPostimagePlanner.prepare(
                ids,qty,ingredients,400,1,2,
                STACKABLE,NONSTACKABLE,null,false);
        ok(made.ids[0]==100&&made.quantities[0]==3&&
           made.ids[1]==400&&made.quantities[1]==1&&
           made.ids[2]==400&&made.quantities[2]==1,
           "two nonstackable outputs not placed in freed slots");
        ok(ids[1]==200&&qty[0]==5,
            "successful planning mutated original inventory");

        denied(()->RecipeMixedPostimagePlanner.prepare(
            new int[]{100,200,300},new int[]{5,1,1},
            Collections.singletonMap(100,1),400,2,1,
            STACKABLE,NONSTACKABLE,null,false),
            "full inventory generated a nonstackable grant");
        denied(()->RecipeMixedPostimagePlanner.prepare(
            new int[]{100,200,-1},new int[]{4,1,0},
            Collections.singletonMap(200,1),400,1,1,
            STACKABLE,NONSTACKABLE,200,false),
            "retained nonstackable tool was consumed");
        RecipeMixedPostimagePlanner.Postimage retained=
            RecipeMixedPostimagePlanner.prepare(
                new int[]{200,200,-1},new int[]{1,1,0},
                Collections.singletonMap(200,1),400,1,1,
                STACKABLE,NONSTACKABLE,200,false);
        ok(retained.ids[0]==400&&retained.ids[1]==200,
            "retained nonstackable tool did not survive");
        RecipeMixedPostimagePlanner.Postimage consumed=
            RecipeMixedPostimagePlanner.prepare(
                new int[]{200,200,-1},new int[]{1,1,0},
                Collections.emptyMap(),400,1,2,
                STACKABLE,NONSTACKABLE,200,true);
        ok(consumed.ids[0]==400&&consumed.ids[1]==400&&
           consumed.quantities[0]==1&&consumed.quantities[1]==1,
           "nonstackable tool-only conversion did not charge each batch");
        denied(()->RecipeMixedPostimagePlanner.prepare(
            new int[]{200,-1},new int[]{2,0},
            Collections.singletonMap(200,1),400,1,1,
            STACKABLE,NONSTACKABLE,null,false),
            "stacked nonstackable input accepted");
        denied(()->RecipeMixedPostimagePlanner.prepare(
            new int[]{999,100,-1},new int[]{1,2,0},
            Collections.singletonMap(100,1),400,1,1,
            STACKABLE,NONSTACKABLE,null,false),
            "unclassified unrelated inventory item accepted");
        denied(()->RecipeMixedPostimagePlanner.prepare(
            new int[]{-2,100,-1},new int[]{0,2,0},
            Collections.singletonMap(100,1),400,1,1,
            STACKABLE,NONSTACKABLE,null,false),
            "noncanonical empty sentinel accepted");
        Set<Integer> ambiguous=new HashSet<>(NONSTACKABLE);
        ambiguous.add(100);
        denied(()->RecipeMixedPostimagePlanner.prepare(
            ids,qty,ingredients,400,1,1,
            STACKABLE,ambiguous,null,false),
            "conflicting stackability authority accepted");
        RecipeMixedPostimagePlanner.Postimage maxed=
            RecipeMixedPostimagePlanner.prepare(
                new int[]{100,300,-1},new int[]{
                    2,Integer.MAX_VALUE,0},
                Collections.singletonMap(100,1),300,3,1,
                STACKABLE,NONSTACKABLE,null,false);
        ok(maxed.ids[2]==300&&maxed.quantities[2]==3&&
           maxed.quantities[1]==Integer.MAX_VALUE,
           "stackable output did not spill past capped stack");

        seededConservation();
        seededToolConservation();
        legacyStackableParity();
        System.out.println("RECIPE_MIXED_POSTIMAGE_PASS"+
            " nonstackableOutputs=true retainedAndConsumedTools=true"+
            " malformedClassificationDenied=true maxStackSpill=true"+
            " seededLedgerCases=1024 toolLedgerCases=1024"+
            " legacyParityCases=512"+
            " noMutation=true liveRecipeRegistered=false");
    }

    /**
     * Independently verify per-item ledger conservation when a verified
     * nonstackable or stackable tool is consumed per batch or retained.
     * Leave room for every possible output so only tool/input ownership
     * influences acceptance. No real client asset authority.
     */
    private static void seededToolConservation(){
        Random random=new Random(0xBADC0FFEE1L);
        int[] types={100,200,300,400};
        Integer[] choices={null,100,200,300,400};
        int passed=0,denied=0,retainedOverlap=0,consumedOverlap=0;
        for(int trial=0;trial<1024;trial++){
            int[] ids=new int[20],counts=new int[20];
            Arrays.fill(ids,-1);
            Map<Integer,Long> original=new HashMap<>();
            for(int slot=0;slot<6;slot++){
                int choice=random.nextInt(5);
                ids[slot]=choice==4?-1:types[choice];
                counts[slot]=ids[slot]==-1?0:
                    NONSTACKABLE.contains(ids[slot])?1:
                    1+random.nextInt(8);
                if(ids[slot]>=0)
                    original.merge(ids[slot],(long)counts[slot],Long::sum);
            }
            int[] savedIds=ids.clone(),savedCounts=counts.clone();
            Map<Integer,Integer> input=new HashMap<>();
            input.put(types[random.nextInt(4)],
                1+random.nextInt(3));
            if(random.nextBoolean())
                input.put(types[random.nextInt(4)],
                    1+random.nextInt(3));
            Integer tool=choices[random.nextInt(choices.length)];
            boolean consumed=tool!=null&&random.nextBoolean();
            int output=types[random.nextInt(4)];
            int batches=1+random.nextInt(3),perBatch=1+random.nextInt(3);
            Map<Integer,Long> expected=new HashMap<>(original);
            Map<Integer,Long> perInput=new HashMap<>();
            for(Map.Entry<Integer,Integer> e:input.entrySet())
                perInput.put(e.getKey(),(long)e.getValue());
            if(consumed)
                perInput.merge(tool,1L,Long::sum);
            boolean sufficient=tool==null||
                original.getOrDefault(tool,0L)>0;
            for(Map.Entry<Integer,Long> e:perInput.entrySet()){
                long next=expected.getOrDefault(e.getKey(),0L)-
                    e.getValue()*batches;
                if(next<0)sufficient=false;
                expected.put(e.getKey(),next);
            }
            if(tool!=null&&!consumed&&perInput.containsKey(tool)&&
                expected.getOrDefault(tool,0L)<1)
                sufficient=false;
            expected.merge(output,(long)perBatch*batches,Long::sum);
            try{
                RecipeMixedPostimagePlanner.Postimage post=
                    RecipeMixedPostimagePlanner.prepare(
                        ids,counts,input,output,perBatch,batches,
                        STACKABLE,NONSTACKABLE,tool,consumed);
                ok(sufficient,
                    "tool request admitted without inputs trial="+trial);
                Map<Integer,Long> actual=new HashMap<>();
                for(int slot=0;slot<post.ids.length;slot++){
                    int id=post.ids[slot],count=post.quantities[slot];
                    ok(id==-1?count==0:
                       STACKABLE.contains(id)?count>0:
                       NONSTACKABLE.contains(id)&&count==1,
                       "tool trial emitted malformed slot="+trial);
                    if(id>=0)
                        actual.merge(id,(long)count,Long::sum);
                }
                for(int id:types)
                    ok(actual.getOrDefault(id,0L).equals(
                        expected.getOrDefault(id,0L)),
                        "tool ledger mismatch trial="+trial+" id="+id);
                if(tool!=null&&input.containsKey(tool)){
                    if(consumed)consumedOverlap++;
                    else retainedOverlap++;
                }
                passed++;
            }catch(IllegalStateException rejected){
                ok(!sufficient,
                    "viable tool request wrongly denied trial="+trial+
                    " error="+rejected.getMessage());
                denied++;
            }
            ok(Arrays.equals(ids,savedIds)&&
               Arrays.equals(counts,savedCounts),
               "tool trial mutated source="+trial);
        }
        ok(passed>100&&denied>100&&retainedOverlap>0&&
           consumedOverlap>0,
           "tool fixture undercovered passed="+passed+
           " denied="+denied+
           " retained="+retainedOverlap+
           " consumed="+consumedOverlap);
    }

    private static void legacyStackableParity(){
        Random random=new Random(0x7A5CA11EL);
        Set<Integer> allStackable=new HashSet<>(
            Arrays.asList(100,200,300));
        for(int trial=0;trial<512;trial++){
            int[] ids=new int[6],quantities=new int[6];
            for(int slot=0;slot<ids.length;slot++){
                int choice=random.nextInt(4);
                ids[slot]=choice==3?-1:
                    choice==0?100:choice==1?200:300;
                quantities[slot]=ids[slot]==-1?0:
                    1+random.nextInt(9);
            }
            int[] originalIds=ids.clone(),
                originalQty=quantities.clone();
            Map<Integer,Integer> inputs=new HashMap<>();
            inputs.put(100,1+random.nextInt(3));
            if(random.nextBoolean())
                inputs.put(200,1+random.nextInt(3));
            int output=100+100*random.nextInt(3);
            int batches=1+random.nextInt(4),
                outputPerBatch=1+random.nextInt(3);
            RecipeStackPostimagePlanner.Postimage legacy=null;
            RecipeMixedPostimagePlanner.Postimage mixed=null;
            boolean legacyRejected=false,mixedRejected=false;
            try{
                legacy=RecipeStackPostimagePlanner.prepare(
                    ids,quantities,inputs,output,outputPerBatch,
                    batches,allStackable);
            }catch(IllegalStateException expected){
                legacyRejected=true;
            }
            try{
                mixed=RecipeMixedPostimagePlanner.prepare(
                    ids,quantities,inputs,output,outputPerBatch,
                    batches,allStackable,Collections.emptySet(),
                    null,false);
            }catch(IllegalStateException expected){
                mixedRejected=true;
            }
            ok(legacyRejected==mixedRejected,
                "legacy/mixed rejection parity trial="+trial);
            if(!legacyRejected)
                ok(Arrays.equals(legacy.ids,mixed.ids)&&
                   Arrays.equals(legacy.quantities,mixed.quantities),
                   "legacy/mixed slot postimage differs trial="+trial);
            ok(Arrays.equals(ids,originalIds)&&
               Arrays.equals(quantities,originalQty),
               "legacy parity modified source trial="+trial);
        }
    }

    private static void seededConservation(){
        Random random=new Random(0xC0FFEE2026L);
        int[] types={100,200,300,400};
        int successes=0,failed=0,nonstackOutputs=0;
        for(int trial=0;trial<1024;trial++){
            int[] ids=new int[7],qty=new int[7];
            Map<Integer,Long> before=new HashMap<>();
            for(int slot=0;slot<ids.length;slot++){
                int chosen=random.nextInt(5);
                ids[slot]=chosen==4?-1:types[chosen];
                qty[slot]=ids[slot]==-1?0:
                    NONSTACKABLE.contains(ids[slot])?1:
                    1+random.nextInt(10);
                if(ids[slot]!=-1)
                    before.merge(ids[slot],(long)qty[slot],Long::sum);
            }
            int[] originalIds=ids.clone(),originalQty=qty.clone();
            Map<Integer,Integer> ingredients=new HashMap<>();
            ingredients.put(types[random.nextInt(4)],1+random.nextInt(3));
            if(random.nextBoolean())
                ingredients.put(types[random.nextInt(4)],
                    1+random.nextInt(2));
            int output=types[random.nextInt(4)];
            int amount=1+random.nextInt(3),batches=1+random.nextInt(3);
            Map<Integer,Long> expected=new HashMap<>(before);
            boolean affordable=true;
            for(Map.Entry<Integer,Integer> entry:ingredients.entrySet()){
                long left=expected.getOrDefault(entry.getKey(),0L)-
                    (long)entry.getValue()*batches;
                if(left<0)affordable=false;
                expected.put(entry.getKey(),left);
            }
            expected.merge(output,(long)amount*batches,Long::sum);
            try{
                RecipeMixedPostimagePlanner.Postimage result=
                    RecipeMixedPostimagePlanner.prepare(
                        ids,qty,ingredients,output,amount,batches,
                        STACKABLE,NONSTACKABLE,null,false);
                ok(affordable,
                    "unaffordable inputs accepted trial="+trial);
                Map<Integer,Long> after=new HashMap<>();
                for(int slot=0;slot<result.ids.length;slot++){
                    int id=result.ids[slot],quantity=result.quantities[slot];
                    ok(id==-1?quantity==0:
                       (STACKABLE.contains(id)&&quantity>0)||
                       (NONSTACKABLE.contains(id)&&quantity==1),
                       "invalid output slot trial="+trial);
                    if(id!=-1)
                        after.merge(id,(long)quantity,Long::sum);
                }
                for(int id:types)
                    ok(after.getOrDefault(id,0L).equals(
                        expected.getOrDefault(id,0L)),
                        "ledger mismatch trial="+trial+" item="+id);
                if(NONSTACKABLE.contains(output))nonstackOutputs++;
                successes++;
            }catch(IllegalStateException capacityOrInputs){
                failed++;
            }
            ok(Arrays.equals(ids,originalIds)&&
                Arrays.equals(qty,originalQty),
                "input changed trial="+trial);
        }
        ok(successes>100&&failed>100&&nonstackOutputs>0,
            "seed coverage incomplete successes="+successes+
            " failures="+failed+" nonstackOutputs="+nonstackOutputs);
    }
}

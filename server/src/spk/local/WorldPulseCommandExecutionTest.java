package spk.local;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldPulseCommandExecutionTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(20L);
        WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
        long ga=w.registerPlayer(a,"pulse-a"),gb=w.registerPlayer(b,"pulse-b");
        List<Long> ta=Collections.synchronizedList(new ArrayList<>()),tb=Collections.synchronizedList(new ArrayList<>());
        w.attachTickTarget(target(a,ga,ta));w.attachTickTarget(target(b,gb,tb));
        List<Integer> order=Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> c1=w.submit(a,()->order.add(1));
        CompletableFuture<Void> c2=w.submit(b,()->order.add(2));
        CompletableFuture<Void> c3=w.submit(a,()->order.add(3));
        AtomicInteger scheduled=new AtomicInteger();
        WorldEventQueue.Handle cancelled=w.events().schedule(2,()->scheduled.addAndGet(100));cancelled.cancel();
        w.events().schedule(2,()->{throw new RuntimeException("isolated-test");});
        w.events().schedule(2,scheduled::incrementAndGet);
        w.start();
        CompletableFuture.allOf(c1,c2,c3).get(2,TimeUnit.SECONDS);
        long deadline=System.currentTimeMillis()+1000;while(w.clock().tick()<3&&System.currentTimeMillis()<deadline)Thread.sleep(5);
        if(!order.equals(Arrays.asList(1,2,3)))throw new AssertionError("deterministic command order "+order);
        if(w.clock().tick()<3)throw new AssertionError("world tick stalled");
        if(ta.isEmpty()||tb.isEmpty())throw new AssertionError("targets not ticked");
        int common=0;for(Long x:ta)if(tb.contains(x))common++;
        if(common==0)throw new AssertionError("no shared tick identity ta="+ta+" tb="+tb);
        if(scheduled.get()!=1)throw new AssertionError("scheduler cancellation/isolation result="+scheduled.get());
        String metrics=w.metrics();
        w.unregisterPlayer(a);w.unregisterPlayer(b);w.close();
        System.out.println("V512_WORLD_PULSE_COMMAND_PASS order="+order+" sharedTicks="+common+" scheduled="+scheduled+" metrics="+metrics);
    }
    private static WorldTickTarget target(WorldPlayer p,long generation,List<Long> ticks){
        return new WorldTickTarget(){public EntityId ownerId(){return p.id();}public long ownerGeneration(){return generation;}public void onWorldTick(long tick,long now){ticks.add(tick);}};
    }
}

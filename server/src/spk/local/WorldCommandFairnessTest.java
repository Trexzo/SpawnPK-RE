package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldCommandFairnessTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(600L);WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();w.registerPlayer(a,"fair-a");w.registerPlayer(b,"fair-b");
        AtomicInteger arun=new AtomicInteger(),brun=new AtomicInteger();
        for(int i=0;i<40;i++)w.submit(a,arun::incrementAndGet);
        CompletableFuture<Void> b1=w.submit(b,brun::incrementAndGet),b2=w.submit(b,brun::incrementAndGet);
        w.pulse().pulseOnce(System.currentTimeMillis());
        b1.get(100,TimeUnit.MILLISECONDS);b2.get(100,TimeUnit.MILLISECONDS);
        if(arun.get()!=32||brun.get()!=2)throw new AssertionError("fair drain a="+arun+" b="+brun);
        // Drain remainder before testing per-player queue capacity.
        w.pulse().pulseOnce(System.currentTimeMillis());
        if(arun.get()!=40)throw new AssertionError("remainder");
        CompletableFuture<Void> rejected=null;
        for(int i=0;i<65;i++){CompletableFuture<Void> f=w.submit(a,()->{});if(i==64)rejected=f;}
        boolean limited=false;try{rejected.get(100,TimeUnit.MILLISECONDS);}catch(ExecutionException e){limited=e.getCause() instanceof RejectedExecutionException;}catch(CancellationException e){limited=true;}
        if(!limited||w.commands().queuedFor(a.id())!=64)throw new AssertionError("per-player bound");
        w.unregisterPlayer(a);w.unregisterPlayer(b);w.close();
        System.out.println("V512_WORLD_COMMAND_FAIRNESS_PASS maxPerTick=32 secondPlayerNotStarved=true perPlayerQueueLimit=64");
    }
}

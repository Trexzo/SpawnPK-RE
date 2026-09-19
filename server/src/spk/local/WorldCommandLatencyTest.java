package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Regression: World ownership must not add a full 600 ms tick of input latency. */
public final class WorldCommandLatencyTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(600L);
        WorldPlayer p=new WorldPlayer();w.registerPlayer(p,"latency");w.start();
        Thread.sleep(40L); // remain well before the first logical tick
        AtomicInteger ran=new AtomicInteger();
        long t0=System.nanoTime();
        w.submit(p,ran::incrementAndGet).get(250,TimeUnit.MILLISECONDS);
        long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-t0);
        long tickAtCompletion=w.clock().tick();
        String metrics=w.metrics();
        w.unregisterPlayer(p);w.close();
        if(ran.get()!=1)throw new AssertionError("command not run");
        if(ms>=200L)throw new AssertionError("artificial input latency ms="+ms);
        if(tickAtCompletion!=0L)throw new AssertionError("command waited for 600ms simulation tick tick="+tickAtCompletion);
        if(!metrics.contains("fastCommandsProcessed=1"))throw new AssertionError("fast path metric "+metrics);
        System.out.println("V5121_LOW_LATENCY_WORLD_COMMAND_PASS elapsedMs="+ms+" completedBeforeFirst600msTick=true metrics="+metrics);
    }
}

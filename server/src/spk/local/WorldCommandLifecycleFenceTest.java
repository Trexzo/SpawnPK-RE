package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldCommandLifecycleFenceTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(50L);
        WorldPlayer p=new WorldPlayer();w.registerPlayer(p,"fence");
        AtomicInteger mutations=new AtomicInteger();
        CompletableFuture<Void> pending=w.submit(p,mutations::incrementAndGet);
        if(!w.unregisterPlayer(p))throw new AssertionError("unregister");
        boolean cancelled=false;try{pending.get(100,TimeUnit.MILLISECONDS);}catch(CancellationException|ExecutionException ok){cancelled=true;}
        if(!cancelled||mutations.get()!=0)throw new AssertionError("queued command mutated after logout");
        CompletableFuture<Void> after=w.submit(p,mutations::incrementAndGet);
        try{after.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("post-logout submit accepted");}catch(CancellationException|ExecutionException ok){}
        w.close();
        System.out.println("V512_WORLD_COMMAND_FENCE_PASS queuedMutationAfterLogout=false postLogoutSubmit=false");
    }
}

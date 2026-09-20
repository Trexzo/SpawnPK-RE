package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldCommandShutdownFenceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"shutdown-fence");

        AtomicInteger mutations=new AtomicInteger();
        CompletableFuture<Void> first=world.submit(player,mutations::incrementAndGet);
        CompletableFuture<Void> second=world.submit(player,mutations::incrementAndGet);

        if(first.isDone()||second.isDone())
            throw new AssertionError("commands unexpectedly completed before shutdown");
        if(world.commands().size()!=2||world.commands().queuedFor(player.id())!=2)
            throw new AssertionError("pre-close accounting");

        world.close();

        expectCancelled(first,"first queued future");
        expectCancelled(second,"second queued future");

        if(mutations.get()!=0)
            throw new AssertionError("queued command executed during shutdown");
        if(world.commands().size()!=0||world.commands().queuedFor(player.id())!=0)
            throw new AssertionError("post-close accounting");
        if(!world.commands().closed())
            throw new AssertionError("inbox not closed");

        CompletableFuture<Void> after=world.submit(player,mutations::incrementAndGet);
        boolean rejected=false;
        try{
            after.get(100,TimeUnit.MILLISECONDS);
        }catch(ExecutionException e){
            rejected=e.getCause() instanceof RejectedExecutionException;
        }
        if(!rejected)
            throw new AssertionError("post-close submit not rejected");
        if(world.commands().size()!=0||world.commands().queuedFor(player.id())!=0)
            throw new AssertionError("post-close submit mutated queue");
        if(mutations.get()!=0)
            throw new AssertionError("post-close command executed");

        world.unregisterPlayer(player);

        System.out.println(
            "WORLD_COMMAND_SHUTDOWN_FENCE_PASS queuedFuturesSettled=true postCloseRejected=true accountingCleared=true mutations=0");
    }

    private static void expectCancelled(
        CompletableFuture<Void> future,
        String label
    )throws Exception{
        try{
            future.get(100,TimeUnit.MILLISECONDS);
            throw new AssertionError(label+" completed normally");
        }catch(CancellationException expected){
            return;
        }catch(ExecutionException e){
            if(e.getCause() instanceof CancellationException)return;
            throw new AssertionError(label+" wrong failure="+e.getCause(),e);
        }
    }
}

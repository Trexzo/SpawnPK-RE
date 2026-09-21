package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldCommandGenerationOwnershipFenceTest {
    public static void main(String[] args)
        throws Exception {

        World world=
            World.isolatedForTest(60_000L);
        WorldPlayer player=
            new WorldPlayer();
        AtomicInteger runs=
            new AtomicInteger();

        try{
            long firstGeneration=
                world.registerPlayer(
                    player,
                    "generation-owner"
                );

            CompletableFuture<Void> first=
                world.submit(
                    player,
                    firstGeneration,
                    runs::incrementAndGet
                );

            require(
                world.commands().size()==1,
                "first command not queued"
            );
            require(
                world.commands().drain(8,8)==1,
                "first command not drained"
            );

            first.get(
                1,
                TimeUnit.SECONDS
            );

            require(
                runs.get()==1,
                "first owner command runs="+
                runs.get()
            );

            require(
                world.unregisterPlayer(
                    player,
                    firstGeneration
                ),
                "first owner unregister failed"
            );

            long replacementGeneration=
                world.registerPlayer(
                    player,
                    "generation-owner"
                );

            require(
                replacementGeneration>
                    firstGeneration,
                "replacement generation did not advance"
            );

            CompletableFuture<Void> stale=
                world.submit(
                    player,
                    firstGeneration,
                    runs::incrementAndGet
                );

            assertCancelled(
                stale,
                "stale explicit submit"
            );

            require(
                world.commands().size()==0,
                "stale submit grew queue"
            );
            require(
                runs.get()==1,
                "stale submit executed action"
            );

            boolean staleWaitRejected=false;

            try{
                world.submitAndWait(
                    player,
                    firstGeneration,
                    runs::incrementAndGet,
                    250L
                );
            }catch(
                CancellationException expected
            ){
                staleWaitRejected=true;
            }

            require(
                staleWaitRejected,
                "stale submitAndWait accepted"
            );
            require(
                world.commands().size()==0,
                "stale submitAndWait grew queue"
            );
            require(
                runs.get()==1,
                "stale submitAndWait executed action"
            );

            CompletableFuture<Void> replacement=
                world.submit(
                    player,
                    replacementGeneration,
                    runs::incrementAndGet
                );

            require(
                world.commands().size()==1,
                "replacement command not queued"
            );
            require(
                world.commands().drain(8,8)==1,
                "replacement command not drained"
            );

            replacement.get(
                1,
                TimeUnit.SECONDS
            );

            require(
                runs.get()==2,
                "replacement owner command runs="+
                runs.get()
            );

            require(
                world.unregisterPlayer(
                    player,
                    replacementGeneration
                ),
                "replacement owner unregister failed"
            );

            System.out.println(
                "WORLD_COMMAND_GENERATION_OWNERSHIP_FENCE_PASS "+
                "firstOwnerExecuted=true "+
                "staleSubmitRejected=true "+
                "staleSubmitAndWaitRejected=true "+
                "queueUnchangedOnStale=true "+
                "replacementOwnerExecuted=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private static void assertCancelled(
        CompletableFuture<Void> future,
        String label
    )throws Exception{
        boolean cancelled=false;

        try{
            future.get(
                1,
                TimeUnit.SECONDS
            );
        }catch(CancellationException expected){
            cancelled=true;
        }catch(ExecutionException expected){
            cancelled=
                expected.getCause()
                    instanceof CancellationException;
        }

        require(
            cancelled,
            label+" was not cancelled"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private WorldCommandGenerationOwnershipFenceTest(){}
}

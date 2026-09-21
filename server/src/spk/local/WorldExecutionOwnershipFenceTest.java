package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldExecutionOwnershipFenceTest {
    public static void main(String[] args)
        throws Exception {

        World first=
            World.isolatedForTest(60_000L);
        World second=
            World.isolatedForTest(60_000L);

        WorldPlayer player=
            new WorldPlayer();

        AtomicInteger commandRuns=
            new AtomicInteger();
        AtomicInteger realtimeRuns=
            new AtomicInteger();

        try{
            long firstGeneration=
                first.registerPlayer(
                    player,
                    "execution-owner"
                );

            CompletableFuture<Void> foreignCommand=
                second.commands().submit(
                    player,
                    commandRuns::incrementAndGet
                );

            assertCancelled(
                foreignCommand,
                "foreign command"
            );

            if(second.commands().size()!=0)
                throw new AssertionError(
                    "foreign command grew second World queue"
                );

            boolean foreignRealtimeRejected=false;

            try{
                second.realtime().schedule(
                    0L,
                    player,
                    realtimeRuns::incrementAndGet
                );
            }catch(IllegalStateException expected){
                foreignRealtimeRejected=
                    expected.getMessage().contains(
                        "not owned by world"
                    );
            }

            if(!foreignRealtimeRejected)
                throw new AssertionError(
                    "foreign realtime task accepted"
                );

            if(second.realtime().size()!=0)
                throw new AssertionError(
                    "foreign realtime task grew second World queue"
                );

            CompletableFuture<Void> ownCommand=
                first.commands().submit(
                    player,
                    commandRuns::incrementAndGet
                );

            first.realtime().schedule(
                0L,
                player,
                realtimeRuns::incrementAndGet
            );

            if(first.commands().drain(8,8)!=1)
                throw new AssertionError(
                    "own command did not drain"
                );

            ownCommand.get(
                1,
                TimeUnit.SECONDS
            );

            if(first.realtime().runDue(
                    Long.MAX_VALUE)!=1)
                throw new AssertionError(
                    "own realtime task did not drain"
                );

            if(commandRuns.get()!=1||
               realtimeRuns.get()!=1)
                throw new AssertionError(
                    "own execution mismatch commands="+
                    commandRuns.get()+
                    " realtime="+
                    realtimeRuns.get()
                );

            if(!first.unregisterPlayer(
                    player,
                    firstGeneration
                ))
                throw new AssertionError(
                    "first owner unregister failed"
                );

            long secondGeneration=
                second.registerPlayer(
                    player,
                    "execution-owner"
                );

            if(secondGeneration<=firstGeneration)
                throw new AssertionError(
                    "ownership transfer generation did not advance"
                );

            CompletableFuture<Void> staleWorldCommand=
                first.commands().submit(
                    player,
                    commandRuns::incrementAndGet
                );

            assertCancelled(
                staleWorldCommand,
                "stale World command"
            );

            boolean staleWorldRealtimeRejected=false;

            try{
                first.realtime().schedule(
                    0L,
                    player,
                    realtimeRuns::incrementAndGet
                );
            }catch(IllegalStateException expected){
                staleWorldRealtimeRejected=
                    expected.getMessage().contains(
                        "not owned by world"
                    );
            }

            if(!staleWorldRealtimeRejected)
                throw new AssertionError(
                    "stale World realtime task accepted"
                );

            CompletableFuture<Void> transferredCommand=
                second.commands().submit(
                    player,
                    commandRuns::incrementAndGet
                );

            second.realtime().schedule(
                0L,
                player,
                realtimeRuns::incrementAndGet
            );

            if(second.commands().drain(8,8)!=1)
                throw new AssertionError(
                    "transferred command did not drain"
                );

            transferredCommand.get(
                1,
                TimeUnit.SECONDS
            );

            if(second.realtime().runDue(
                    Long.MAX_VALUE)!=1)
                throw new AssertionError(
                    "transferred realtime task did not drain"
                );

            if(commandRuns.get()!=2||
               realtimeRuns.get()!=2)
                throw new AssertionError(
                    "transferred execution mismatch commands="+
                    commandRuns.get()+
                    " realtime="+
                    realtimeRuns.get()
                );

            System.out.println(
                "WORLD_EXECUTION_OWNERSHIP_FENCE_PASS "+
                "foreignCommandRejected=true "+
                "foreignRealtimeRejected=true "+
                "ownerExecution=true "+
                "transferOldWorldRejected=true "+
                "transferNewWorldAccepted=true"
            );
        }finally{
            if(player.registered()){
                long generation=
                    player.generation();

                second.unregisterPlayer(
                    player,
                    generation
                );

                first.unregisterPlayer(
                    player,
                    generation
                );
            }

            second.close();
            first.close();
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

        if(!cancelled)
            throw new AssertionError(
                label+" was not rejected"
            );
    }

    private WorldExecutionOwnershipFenceTest(){}
}

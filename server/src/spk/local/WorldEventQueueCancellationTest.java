package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class WorldEventQueueCancellationTest {
    public static void main(String[] args)throws Exception{
        testImmediateRemovalAndIdempotence();
        testEqualTickOrderingWithCancellation();
        testExecutedHandleDetaches();

        System.out.println(
            "WORLD_EVENT_QUEUE_CANCELLATION_PASS "+
            "immediateRemoval=true "+
            "idempotent=true "+
            "suppressed=true "+
            "equalTickOrder=true "+
            "handleDetached=true"
        );
    }

    private static void testImmediateRemovalAndIdempotence()
        throws Exception{
        WorldEventQueue queue=
            new WorldEventQueue();

        int[] runs={0};

        WorldEventQueue.Handle handle=
            queue.schedule(
                1_000_000L,
                ()->runs[0]++
            );

        if(queue.size()!=1)
            throw new AssertionError(
                "scheduled event missing"
            );

        if(handle.cancelled())
            throw new AssertionError(
                "fresh handle already cancelled"
            );

        if(!attached(handle))
            throw new AssertionError(
                "fresh handle not attached"
            );

        handle.cancel();

        if(!handle.cancelled())
            throw new AssertionError(
                "cancel flag not set"
            );

        if(queue.size()!=0)
            throw new AssertionError(
                "cancelled event retained in queue"
            );

        if(attached(handle))
            throw new AssertionError(
                "cancelled handle retained queue owner"
            );

        handle.cancel();

        if(queue.size()!=0)
            throw new AssertionError(
                "repeated cancellation changed queue"
            );

        int executed=
            queue.runDue(
                Long.MAX_VALUE
            );

        if(executed!=0||
           runs[0]!=0)
            throw new AssertionError(
                "cancelled event executed"
            );
    }

    private static void testEqualTickOrderingWithCancellation(){
        WorldEventQueue queue=
            new WorldEventQueue();

        ArrayList<String> order=
            new ArrayList<>();

        queue.schedule(
            50L,
            ()->order.add("A")
        );

        WorldEventQueue.Handle cancelled=
            queue.schedule(
                50L,
                ()->order.add("B")
            );

        queue.schedule(
            50L,
            ()->order.add("C")
        );

        if(queue.size()!=3)
            throw new AssertionError(
                "equal-tick fixtures missing"
            );

        cancelled.cancel();

        if(queue.size()!=2)
            throw new AssertionError(
                "cancelled equal-tick event not removed"
            );

        int executed=
            queue.runDue(50L);

        if(executed!=2)
            throw new AssertionError(
                "unexpected executed count "+
                executed
            );

        if(!order.equals(
                Arrays.asList(
                    "A",
                    "C"
                )))
            throw new AssertionError(
                "equal-tick insertion order changed "+
                order
            );

        if(queue.size()!=0)
            throw new AssertionError(
                "due queue not drained"
            );
    }

    private static void testExecutedHandleDetaches()
        throws Exception{
        WorldEventQueue queue=
            new WorldEventQueue();

        int[] runs={0};

        WorldEventQueue.Handle handle=
            queue.schedule(
                5L,
                ()->runs[0]++
            );

        if(queue.runDue(5L)!=1||
           runs[0]!=1)
            throw new AssertionError(
                "live event did not execute"
            );

        if(attached(handle))
            throw new AssertionError(
                "executed handle retained queue owner"
            );

        handle.cancel();

        if(!handle.cancelled()||
           queue.size()!=0||
           runs[0]!=1)
            throw new AssertionError(
                "post-execution cancellation was not harmless"
            );
    }

    private static boolean attached(
        WorldEventQueue.Handle handle
    )throws Exception{
        Field owner=
            WorldEventQueue.Handle.class
                .getDeclaredField(
                    "owner"
                );

        owner.setAccessible(true);

        return owner.get(handle)!=null;
    }

    private WorldEventQueueCancellationTest(){}
}

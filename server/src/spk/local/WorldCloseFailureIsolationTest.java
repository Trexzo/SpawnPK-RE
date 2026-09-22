package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WorldCloseFailureIsolationTest {
    public static void main(String[] args){
        List<String> order=
            new ArrayList<>();

        RuntimeException firstFailure=
            new RuntimeException(
                "pulse-failed"
            );

        AssertionError laterFailure=
            new AssertionError(
                "domain-events-failed"
            );

        Throwable result=
            WorldCloseSequence.run(
                ()->{
                    order.add("pulse");
                    throw firstFailure;
                },
                ()->order.add(
                    "npc-presentation"
                ),
                ()->{
                    order.add(
                        "domain-events"
                    );
                    throw laterFailure;
                },
                ()->order.add(
                    "commands"
                ),
                ()->order.add(
                    "realtime"
                ),
                ()->order.add(
                    "events"
                ),
                ()->order.add(
                    "persistence"
                )
            );

        List<String> expected=
            Arrays.asList(
                "pulse",
                "npc-presentation",
                "domain-events",
                "commands",
                "realtime",
                "events",
                "persistence"
            );

        if(!order.equals(expected))
            throw new AssertionError(
                "close order/continuation mismatch expected="+
                expected+
                " actual="+
                order
            );

        if(result!=firstFailure)
            throw new AssertionError(
                "first close failure was not retained"
            );

        Throwable[] suppressed=
            result.getSuppressed();

        if(suppressed.length!=1||
           suppressed[0]!=laterFailure)
            throw new AssertionError(
                "later close failure not preserved as suppressed"
            );

        boolean rethrewSame=false;

        try{
            WorldCloseSequence.rethrow(
                result
            );
        }catch(RuntimeException error){
            rethrewSame=
                error==firstFailure;
        }

        if(!rethrewSame)
            throw new AssertionError(
                "World close primary failure identity was not preserved"
            );

        System.out.println(
            "WORLD_CLOSE_FAILURE_ISOLATION_PASS "+
            "allStepsAttempted=true "+
            "orderPreserved=true "+
            "primaryPreserved=true "+
            "laterFailureSuppressed=true "+
            "rethrowIdentityPreserved=true"
        );
    }

    private WorldCloseFailureIsolationTest(){}
}

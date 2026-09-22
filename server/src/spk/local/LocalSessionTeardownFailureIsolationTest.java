package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LocalSessionTeardownFailureIsolationTest {
    public static void main(String[] args){
        List<String> attempted=
            new ArrayList<>();

        boolean one=
            LocalSessionTeardown.run(
                "[teardown-test] ",
                "ONE",
                ()->{
                    attempted.add("ONE");
                    throw new IllegalStateException(
                        "one-failed"
                    );
                }
            );

        boolean two=
            LocalSessionTeardown.run(
                "[teardown-test] ",
                "TWO",
                ()->attempted.add("TWO")
            );

        boolean three=
            LocalSessionTeardown.run(
                "[teardown-test] ",
                "THREE",
                ()->{
                    attempted.add("THREE");
                    throw new AssertionError(
                        "three-failed"
                    );
                }
            );

        boolean four=
            LocalSessionTeardown.run(
                "[teardown-test] ",
                "FOUR",
                ()->attempted.add("FOUR")
            );

        boolean five=
            LocalSessionTeardown.run(
                "[teardown-test] ",
                "WORLD_UNREGISTER",
                ()->attempted.add(
                    "WORLD_UNREGISTER"
                )
            );

        List<String> expected=
            Arrays.asList(
                "ONE",
                "TWO",
                "THREE",
                "FOUR",
                "WORLD_UNREGISTER"
            );

        if(!attempted.equals(expected))
            throw new AssertionError(
                "teardown order/continuation mismatch expected="+
                expected+
                " actual="+
                attempted
            );

        if(one)
            throw new AssertionError(
                "RuntimeException step reported success"
            );

        if(!two)
            throw new AssertionError(
                "successful step TWO reported failure"
            );

        if(three)
            throw new AssertionError(
                "Error step reported success"
            );

        if(!four||!five)
            throw new AssertionError(
                "later successful steps were not preserved"
            );

        System.out.println(
            "LOCAL_SESSION_TEARDOWN_FAILURE_ISOLATION_PASS "+
            "allStepsAttempted=true "+
            "orderPreserved=true "+
            "runtimeFailureContained=true "+
            "errorContained=true "+
            "worldUnregisterReached=true"
        );
    }

    private LocalSessionTeardownFailureIsolationTest(){}
}

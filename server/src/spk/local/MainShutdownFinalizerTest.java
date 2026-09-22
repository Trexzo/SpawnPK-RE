package spk.local;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MainShutdownFinalizerTest {
    public static void main(String[] args){
        RuntimeException shutdownFailure=
            new RuntimeException(
                "shutdown-failed"
            );
        RuntimeException removalFailure=
            new RuntimeException(
                "remove-hook-failed"
            );

        AtomicBoolean removalRan=
            new AtomicBoolean();

        Throwable observed=null;

        try{
            MainShutdownFinalizer.run(
                ()->{
                    throw shutdownFailure;
                },
                ()->{
                    removalRan.set(true);
                    throw removalFailure;
                }
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(!removalRan.get())
            throw new AssertionError(
                "hook retirement was skipped after shutdown failure"
            );

        if(observed!=shutdownFailure)
            throw new AssertionError(
                "shutdown failure did not remain primary"
            );

        Throwable[] suppressed=
            observed.getSuppressed();

        if(suppressed.length!=1||
           suppressed[0]!=removalFailure)
            throw new AssertionError(
                "hook-removal failure was not suppressed behind shutdown failure"
            );

        MainShutdownFinalizer.run(
            ()->{},
            ()->{
                throw new IllegalStateException(
                    "JVM shutdown in progress"
                );
            }
        );

        RuntimeException removalOnly=
            new RuntimeException(
                "removal-only-failure"
            );

        Throwable removalObserved=null;

        try{
            MainShutdownFinalizer.run(
                ()->{},
                ()->{
                    throw removalOnly;
                }
            );
        }catch(Throwable failure){
            removalObserved=failure;
        }

        if(removalObserved!=removalOnly)
            throw new AssertionError(
                "unexpected hook-removal failure was not propagated"
            );

        MainShutdownFinalizer.run(
            ()->{},
            ()->{}
        );

        System.out.println(
            "MAIN_SHUTDOWN_FINALIZER_PASS "+
            "retirementAfterFailure=true "+
            "shutdownFailurePrimary=true "+
            "removalFailureSuppressed=true "+
            "jvmShutdownIllegalStateIgnored=true "+
            "removalOnlyFailurePropagated=true "+
            "cleanCompletionSilent=true"
        );
    }

    private MainShutdownFinalizerTest(){}
}

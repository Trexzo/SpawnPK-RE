package spk.local;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainShutdownFinalizerTest {
    public static void main(String[] args)
        throws Exception{
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

        IOException servingFailure=
            new IOException(
                "serving-loop-failed"
            );
        RuntimeException servingShutdownFailure=
            new RuntimeException(
                "serving-shutdown-failed"
            );
        RuntimeException servingRemovalFailure=
            new RuntimeException(
                "serving-remove-hook-failed"
            );
        AtomicBoolean servingShutdownRan=
            new AtomicBoolean();
        AtomicBoolean servingRemovalRan=
            new AtomicBoolean();
        Throwable servingObserved=null;

        try{
            MainShutdownFinalizer.runPreserving(
                servingFailure,
                ()->{
                    servingShutdownRan.set(
                        true
                    );
                    throw servingShutdownFailure;
                },
                ()->{
                    servingRemovalRan.set(
                        true
                    );
                    throw servingRemovalFailure;
                }
            );
        }catch(Throwable failure){
            servingObserved=failure;
        }

        if(servingObserved!=
                servingFailure)
            throw new AssertionError(
                "serving-loop failure did not remain exact primary",
                servingObserved
            );

        if(!servingShutdownRan.get()||
           !servingRemovalRan.get())
            throw new AssertionError(
                "serving-loop failure skipped terminal cleanup"
            );

        Throwable[] servingSuppressed=
            servingObserved.getSuppressed();

        if(servingSuppressed.length!=2||
           servingSuppressed[0]!=
                servingShutdownFailure||
           servingSuppressed[1]!=
                servingRemovalFailure)
            throw new AssertionError(
                "terminal cleanup failures were not suppressed behind serving failure in order"
            );

        IOException servingOnly=
            new IOException(
                "serving-only-failed"
            );
        Throwable servingOnlyObserved=null;

        try{
            MainShutdownFinalizer.runPreserving(
                servingOnly,
                ()->{},
                ()->{}
            );
        }catch(Throwable failure){
            servingOnlyObserved=failure;
        }

        if(servingOnlyObserved!=
                servingOnly)
            throw new AssertionError(
                "checked serving-only failure identity changed",
                servingOnlyObserved
            );

        System.out.println(
            "MAIN_SHUTDOWN_FINALIZER_PASS "+
            "retirementAfterFailure=true "+
            "shutdownFailurePrimary=true "+
            "removalFailureSuppressed=true "+
            "jvmShutdownIllegalStateIgnored=true "+
            "removalOnlyFailurePropagated=true "+
            "cleanCompletionSilent=true "+
            "servingFailurePrimary=true "+
            "servingCleanupSuppressed=true "+
            "checkedServingIdentity=true"
        );
    }

    private MainShutdownFinalizerTest(){}
}

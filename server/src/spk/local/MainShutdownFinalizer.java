package spk.local;

final class MainShutdownFinalizer {
    private MainShutdownFinalizer(){}

    static void run(
        Runnable shutdown,
        Runnable removeHook
    ){
        WorldCloseSequence.rethrow(
            collect(
                null,
                shutdown,
                removeHook
            )
        );
    }

    static void runPreserving(
        Throwable primary,
        Runnable shutdown,
        Runnable removeHook
    )throws Exception{
        rethrowPreserving(
            collect(
                primary,
                shutdown,
                removeHook
            )
        );
    }

    private static Throwable collect(
        Throwable primary,
        Runnable shutdown,
        Runnable removeHook
    ){
        if(shutdown==null||
           removeHook==null)
            throw new NullPointerException();

        Throwable failure=primary;

        try{
            shutdown.run();
        }catch(Throwable shutdownFailure){
            failure=
                appendFailure(
                    failure,
                    shutdownFailure
                );
        }

        try{
            removeHook.run();
        }catch(IllegalStateException jvmShutdownOwnsHook){
            // Runtime.removeShutdownHook documents this state while
            // JVM shutdown is already in progress. The registered hook
            // owns terminal cleanup in that case.
        }catch(Throwable hookFailure){
            failure=
                appendFailure(
                    failure,
                    hookFailure
                );
        }

        return failure;
    }

    private static Throwable appendFailure(
        Throwable primary,
        Throwable next
    ){
        if(next==null)
            return primary;

        if(primary==null)
            return next;

        if(next!=primary)
            primary.addSuppressed(
                next
            );

        return primary;
    }

    private static void rethrowPreserving(
        Throwable failure
    )throws Exception{
        if(failure==null)
            return;

        if(failure instanceof Exception)
            throw (Exception)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
    }
}

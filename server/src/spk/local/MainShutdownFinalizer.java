package spk.local;

final class MainShutdownFinalizer {
    private MainShutdownFinalizer(){}

    static void run(
        Runnable shutdown,
        Runnable removeHook
    ){
        if(shutdown==null||
           removeHook==null)
            throw new NullPointerException();

        Throwable failure=null;

        try{
            shutdown.run();
        }catch(Throwable shutdownFailure){
            failure=shutdownFailure;
        }

        try{
            removeHook.run();
        }catch(IllegalStateException jvmShutdownOwnsHook){
            // Runtime.removeShutdownHook documents this state while
            // JVM shutdown is already in progress. The registered hook
            // owns terminal cleanup in that case.
        }catch(Throwable hookFailure){
            if(failure==null)
                failure=hookFailure;
            else if(hookFailure!=failure)
                failure.addSuppressed(
                    hookFailure
                );
        }

        WorldCloseSequence.rethrow(
            failure
        );
    }
}

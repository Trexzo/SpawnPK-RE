package spk.local;

final class WorldPlayerUnregisterCleanup {
    private WorldPlayerUnregisterCleanup(){}

    static void run(
        Runnable persistenceCleanup,
        Runnable commandCleanup,
        Runnable realtimeCleanup,
        Runnable petCleanup
    ){
        run(
            persistenceCleanup,
            commandCleanup,
            realtimeCleanup,
            ()->{},
            petCleanup
        );
    }

    static void run(
        Runnable persistenceCleanup,
        Runnable commandCleanup,
        Runnable realtimeCleanup,
        Runnable duelCleanup,
        Runnable petCleanup
    ){
        runStep(
            "PERSISTENCE_RELEASE",
            persistenceCleanup
        );
        runStep(
            "COMMAND_CANCEL",
            commandCleanup
        );
        runStep(
            "REALTIME_CANCEL",
            realtimeCleanup
        );
        runStep(
            "DUEL_CANCEL",
            duelCleanup
        );
        runStep(
            "PET_REMOVE",
            petCleanup
        );
    }

    private static void runStep(
        String step,
        Runnable action
    ){
        if(action==null)
            throw new NullPointerException(
                "action"
            );

        try{
            action.run();
        }catch(Throwable failure){
            System.err.println(
                "WORLD_UNREGISTER_CLEANUP_FAILED step="+
                step+
                " error="+
                failure
            );
        }
    }
}

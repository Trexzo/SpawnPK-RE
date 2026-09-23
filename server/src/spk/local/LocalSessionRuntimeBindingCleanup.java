package spk.local;

final class LocalSessionRuntimeBindingCleanup {
    private LocalSessionRuntimeBindingCleanup(){}

    static void run(
        String tag,
        Runnable clearLocal,
        Runnable tradeCleanup,
        Runnable relayCleanup,
        Runnable player81Cleanup
    ){
        if(clearLocal==null||
           tradeCleanup==null||
           relayCleanup==null||
           player81Cleanup==null)
            throw new NullPointerException();

        clearLocal.run();

        LocalSessionTeardown.run(
            tag,
            "RUNTIME_TRADE_UNREGISTER",
            tradeCleanup
        );

        LocalSessionTeardown.run(
            tag,
            "RUNTIME_RELAY_UNREGISTER",
            relayCleanup
        );

        LocalSessionTeardown.run(
            tag,
            "RUNTIME_PLAYER81_UNREGISTER",
            player81Cleanup
        );
    }
}

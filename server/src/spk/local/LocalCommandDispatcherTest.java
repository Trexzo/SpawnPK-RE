package spk.local;

public final class LocalCommandDispatcherTest {
    public static void main(String[] args)throws Exception{
        String clean=
            LocalCommandDispatcher.clean(
                "  ::dev panel  "
            );
        if(!"dev panel".equals(clean))
            throw new AssertionError(
                "command normalization changed: "+
                clean
            );

        String[] devPanel=
            LocalCommandDispatcher.tokens(
                clean
            );
        if(devPanel.length!=2||
           !"dev".equals(devPanel[0])||
           !"panel".equals(devPanel[1]))
            throw new AssertionError(
                "dev panel tokenization changed"
            );

        if(!LocalCommandDispatcher
                .isDevPanelRoute(
                    devPanel))
            throw new AssertionError(
                "composite dev panel route not retained"
            );

        for(String alias:
                new String[]{
                    "devpanel",
                    "devui",
                    "lab"
                })
            if(LocalCommandDispatcher
                    .isDevPanelRoute(
                        new String[]{alias}))
                throw new AssertionError(
                    "content-owned alias still captured by core helper: "+
                    alias
                );

        if(LocalCommandDispatcher
                .isDevPanelRoute(
                    new String[]{
                        "dev",
                        "info"
                    }))
            throw new AssertionError(
                "dev info must not be captured by panel route"
            );

        if(LocalCommandDispatcher
                .isDevPanelRoute(
                    new String[]{
                        "regionload",
                        "12850"
                    }))
            throw new AssertionError(
                "unrelated command captured by panel route"
            );

        String untouched=
            LocalCommandDispatcher.clean(
                "authority"
            );
        if(!"authority".equals(
                untouched))
            throw new AssertionError(
                "non-prefixed command changed"
            );

        System.out.println(
            "LOCAL_COMMAND_DISPATCHER_PASS "+
            "normalization=true "+
            "compositeDevPanel=true "+
            "aliasesCoreOwned=false "+
            "unrelatedRejected=true"
        );
    }
}

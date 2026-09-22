package spk.local;

final class WorldCloseSequence {
    private WorldCloseSequence(){}

    static Throwable run(
        Runnable... steps
    ){
        if(steps==null)
            throw new NullPointerException(
                "steps"
            );

        Throwable primary=null;

        for(Runnable step:steps){
            if(step==null){
                NullPointerException failure=
                    new NullPointerException(
                        "close step"
                    );

                if(primary==null)
                    primary=failure;
                else
                    primary.addSuppressed(
                        failure
                    );

                continue;
            }

            try{
                step.run();
            }catch(Throwable failure){
                if(primary==null)
                    primary=failure;
                else if(failure!=primary)
                    primary.addSuppressed(
                        failure
                    );
            }
        }

        return primary;
    }

    static void rethrow(
        Throwable failure
    ){
        if(failure==null)
            return;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            "World close failed",
            failure
        );
    }
}

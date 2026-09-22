package spk.local;

final class LocalSessionTeardown {
    private LocalSessionTeardown(){}

    static boolean run(
        String tag,
        String step,
        Runnable action
    ){
        if(step==null||action==null)
            throw new NullPointerException();

        try{
            action.run();
            return true;
        }catch(Throwable failure){
            System.err.println(
                (tag==null?"":tag)+
                "SESSION_TEARDOWN_STEP_FAILED step="+
                step+
                " error="+
                failure
            );
            return false;
        }
    }
}

package spk.local;

final class GameClock {
    interface ExpectedTickAction {
        void run() throws Exception;
    }

    static final long TICK_MILLIS=600L;

    private long tick;
    private Thread expectedTickOwner;
    private long expectedTickValue=-1L;
    private int expectedTickDepth;

    synchronized long tick(){
        return tick;
    }

    synchronized long advance(){
        if(expectedTickDepth>0&&
           expectedTickOwner==Thread.currentThread())
            throw new IllegalStateException(
                "logical clock advance rejected while exact tick is owned tick="+
                expectedTickValue
            );

        return ++tick;
    }

    synchronized void withExpectedTick(
        long expectedTick,
        ExpectedTickAction action
    )throws Exception{
        if(expectedTick<0L)
            throw new IllegalArgumentException(
                "expectedTick="+expectedTick
            );
        if(action==null)
            throw new NullPointerException(
                "action"
            );
        if(tick!=expectedTick)
            throw new IllegalStateException(
                "shared world tick changed expected="+
                expectedTick+
                " actual="+
                tick
            );

        Thread current=
            Thread.currentThread();

        if(expectedTickDepth>0){
            if(expectedTickOwner!=current||
               expectedTickValue!=expectedTick)
                throw new IllegalStateException(
                    "incompatible expected-tick ownership current="+
                    expectedTickValue+
                    " requested="+
                    expectedTick
                );
        }else{
            expectedTickOwner=current;
            expectedTickValue=expectedTick;
        }

        expectedTickDepth++;

        try{
            action.run();

            if(tick!=expectedTick)
                throw new IllegalStateException(
                    "shared world tick changed inside owned action expected="+
                    expectedTick+
                    " actual="+
                    tick
                );
        }finally{
            expectedTickDepth--;

            if(expectedTickDepth==0){
                expectedTickOwner=null;
                expectedTickValue=-1L;
            }
        }
    }

    long millisForTicks(long ticks){
        return Math.multiplyExact(
            ticks,
            TICK_MILLIS
        );
    }
}

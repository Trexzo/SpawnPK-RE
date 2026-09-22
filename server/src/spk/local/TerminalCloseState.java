package spk.local;

import java.util.concurrent.CountDownLatch;

final class TerminalCloseState {
    private final CountDownLatch completed=
        new CountDownLatch(1);

    private volatile Throwable failure;

    void complete(
        Throwable terminalFailure
    ){
        failure=terminalFailure;
        completed.countDown();
    }

    void await(){
        boolean interrupted=false;

        for(;;){
            try{
                completed.await();
                break;
            }catch(InterruptedException error){
                interrupted=true;
            }
        }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    Throwable failure(){
        return failure;
    }

    void awaitAndRethrow(){
        await();
        WorldCloseSequence.rethrow(
            failure
        );
    }
}

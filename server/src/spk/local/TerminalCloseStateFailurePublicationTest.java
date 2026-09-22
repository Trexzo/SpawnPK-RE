package spk.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class TerminalCloseStateFailurePublicationTest {
    public static void main(String[] args)throws Exception{
        TerminalCloseState failed=
            new TerminalCloseState();

        RuntimeException terminalFailure=
            new RuntimeException(
                "terminal-world-close-failed"
            );

        CountDownLatch observerStarted=
            new CountDownLatch(1);
        AtomicReference<Throwable> observed=
            new AtomicReference<>();

        Thread observer=
            new Thread(
                ()->{
                    observerStarted.countDown();

                    try{
                        failed.awaitAndRethrow();
                    }catch(Throwable error){
                        observed.set(error);
                    }
                },
                "terminal-close-observer"
            );

        observer.start();

        if(!observerStarted.await(
                2L,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "observer did not start"
            );

        if(!observer.isAlive())
            throw new AssertionError(
                "observer did not wait for terminal completion"
            );

        failed.complete(
            terminalFailure
        );

        observer.join(2_000L);

        if(observer.isAlive())
            throw new AssertionError(
                "observer did not wake after terminal completion"
            );

        if(observed.get()!=terminalFailure)
            throw new AssertionError(
                "observer did not receive exact terminal failure identity"
            );

        if(failed.failure()!=terminalFailure)
            throw new AssertionError(
                "terminal failure was not published before completion"
            );

        TerminalCloseState clean=
            new TerminalCloseState();

        clean.complete(null);
        clean.awaitAndRethrow();

        if(clean.failure()!=null)
            throw new AssertionError(
                "clean terminal state published a failure"
            );

        System.out.println(
            "TERMINAL_CLOSE_STATE_FAILURE_PUBLICATION_PASS "+
            "observerWaited=true "+
            "sameFailureIdentity=true "+
            "cleanCompletionSilent=true"
        );
    }

    private TerminalCloseStateFailurePublicationTest(){}
}

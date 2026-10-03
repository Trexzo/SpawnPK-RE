package spk.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorldPulseStartFailureAtomicTest {
    public static void main(
        String[] args
    ){
        assertPreCreateRollbackAndClose();
        assertStarterRollbackRetryAndIdempotence();

        System.out.println(
            "WORLD_PULSE_START_FAILURE_ATOMIC_PASS "+
            "preCreateRollback=true "+
            "startRollback=true "+
            "failurePrimary=true "+
            "starterObservedRunning=true "+
            "retrySafe=true "+
            "singlePulse=true "+
            "closeAfterFailureSafe=true"
        );
    }

    private static void assertPreCreateRollbackAndClose(){
        World world=
            World.isolatedForTest(
                25L
            );

        RuntimeException expected=
            new RuntimeException(
                "fixture-pulse-thread-create-failure"
            );

        WorldPulse pulse=
            new WorldPulse(
                world,
                10_000L,
                (target,name)->{
                    throw expected;
                },
                Thread::start
            );

        Throwable observed=null;

        try{
            pulse.start();
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "pre-create failure did not remain primary"
            );

        assertRolledBack(
            pulse,
            "pre-create failure"
        );

        AtomicBoolean terminalRan=
            new AtomicBoolean();

        pulse.closeWithTerminal(
            ()->terminalRan.set(
                true
            )
        );

        if(!terminalRan.get())
            throw new AssertionError(
                "terminal callback did not run after failed start"
            );

        world.close();
    }

    private static void
        assertStarterRollbackRetryAndIdempotence(){
        World world=
            World.isolatedForTest(
                25L
            );

        AtomicInteger factoryCalls=
            new AtomicInteger();
        AtomicInteger starterCalls=
            new AtomicInteger();
        AtomicBoolean starterObservedRunning=
            new AtomicBoolean();
        List<Thread> candidates=
            new ArrayList<>();
        SecurityException expected=
            new SecurityException(
                "fixture-pulse-thread-start-failure"
            );

        final WorldPulse[] holder=
            new WorldPulse[1];

        WorldPulse pulse=
            new WorldPulse(
                world,
                10_000L,
                (target,name)->{
                    factoryCalls.incrementAndGet();

                    Thread candidate=
                        new Thread(
                            target,
                            name+
                                "-fixture-"+
                                factoryCalls.get()
                        );
                    candidates.add(
                        candidate
                    );
                    return candidate;
                },
                candidate->{
                    int call=
                        starterCalls.incrementAndGet();

                    if(holder[0]!=null&&
                       holder[0].running())
                        starterObservedRunning.set(
                            true
                        );

                    if(call==1)
                        throw expected;

                    candidate.start();
                }
            );
        holder[0]=pulse;

        Throwable observed=null;

        try{
            pulse.start();
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "starter failure did not remain primary"
            );

        if(!starterObservedRunning.get())
            throw new AssertionError(
                "starter did not observe running publication"
            );

        assertRolledBack(
            pulse,
            "starter failure"
        );

        if(candidates.size()!=1||
           candidates.get(0).isAlive())
            throw new AssertionError(
                "failed starter left a live pulse candidate"
            );

        pulse.start();

        if(!pulse.running()||
           pulse.thread()==null)
            throw new AssertionError(
                "retry did not publish a live pulse"
            );

        if(factoryCalls.get()!=2||
           starterCalls.get()!=2)
            throw new AssertionError(
                "retry did not create/start exactly one replacement pulse"
            );

        Thread retryThread=
            pulse.thread();

        pulse.start();

        if(factoryCalls.get()!=2||
           starterCalls.get()!=2||
           pulse.thread()!=retryThread)
            throw new AssertionError(
                "successful second start was not idempotent"
            );

        AtomicInteger terminalCalls=
            new AtomicInteger();

        pulse.closeWithTerminal(
            terminalCalls::incrementAndGet
        );

        if(terminalCalls.get()!=1)
            throw new AssertionError(
                "terminal callback count mismatch after retry: "+
                terminalCalls.get()
            );

        if(pulse.running())
            throw new AssertionError(
                "pulse remained running after terminal close"
            );

        world.close();
    }

    private static void assertRolledBack(
        WorldPulse pulse,
        String phase
    ){
        if(pulse.running())
            throw new AssertionError(
                phase+
                " left running=true"
            );

        if(pulse.thread()!=null)
            throw new AssertionError(
                phase+
                " retained failed thread reference"
            );
    }

    private WorldPulseStartFailureAtomicTest(){}
}

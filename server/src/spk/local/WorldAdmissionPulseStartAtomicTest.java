package spk.local;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class WorldAdmissionPulseStartAtomicTest {
    public static void main(
        String[] args
    ){
        assertStarterFailureRollsBackAndRetries();
        assertFailedOnlyAdmissionClosesSafely();

        System.out.println(
            "WORLD_ADMISSION_PULSE_START_ATOMIC_PASS "+
            "registeredBeforeStart=true "+
            "failurePrimary=true "+
            "membershipRolledBack=true "+
            "playerUnregistered=true "+
            "failedGenerationRejected=true "+
            "pulseRolledBack=true "+
            "retrySafe=true "+
            "freshGeneration=true "+
            "singlePulse=true "+
            "closeSafe=true"
        );
    }

    private static void
        assertStarterFailureRollsBackAndRetries(){
        RuntimeException expected=
            new RuntimeException(
                "fixture-world-admission-pulse-start-failure"
            );
        AtomicInteger factoryCalls=
            new AtomicInteger();
        AtomicInteger starterCalls=
            new AtomicInteger();
        AtomicBoolean registeredBeforeStart=
            new AtomicBoolean();
        AtomicLong failedGeneration=
            new AtomicLong(
                -1L
            );
        final World[] holder=
            new World[1];
        final WorldPlayer[] playerHolder=
            new WorldPlayer[1];

        World world=
            World.isolatedForTest(
                25L,
                (target,name)->{
                    factoryCalls.incrementAndGet();

                    return new Thread(
                        target,
                        name+
                            "-admission-fixture-"+
                            factoryCalls.get()
                    );
                },
                candidate->{
                    int call=
                        starterCalls.incrementAndGet();

                    World current=
                        holder[0];
                    WorldPlayer player=
                        playerHolder[0];

                    if(current!=null&&
                       player!=null&&
                       current.players().size()==1&&
                       player.registered()){
                        registeredBeforeStart.set(
                            true
                        );
                        failedGeneration.set(
                            player.generation()
                        );
                    }

                    if(call==1)
                        throw expected;

                    candidate.start();
                }
            );
        holder[0]=world;

        WorldPlayer player=
            new WorldPlayer();
        playerHolder[0]=player;

        Throwable observed=null;

        try{
            world.registerPlayerAndStart(
                player,
                "admission-pulse-fixture"
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "pulse-start failure did not remain primary"
            );

        if(!registeredBeforeStart.get())
            throw new AssertionError(
                "fixture did not observe registration before pulse start"
            );

        long failed=
            failedGeneration.get();

        if(failed<=0L)
            throw new AssertionError(
                "failed admission generation was not captured"
            );

        if(world.players().size()!=0)
            throw new AssertionError(
                "failed pulse start retained World membership"
            );

        if(player.registered())
            throw new AssertionError(
                "failed pulse start left player registered"
            );

        if(world.players().owns(
                player,
                failed))
            throw new AssertionError(
                "failed admission generation remained owned"
            );

        if(world.pulse().running()||
           world.pulse().thread()!=null)
            throw new AssertionError(
                "failed admission left pulse startup state published"
            );

        long retryGeneration=
            world.registerPlayerAndStart(
                player,
                "admission-pulse-fixture"
            );

        if(retryGeneration<=failed)
            throw new AssertionError(
                "retry did not allocate a fresh player generation: failed="+
                failed+
                " retry="+
                retryGeneration
            );

        if(!player.registered()||
           !world.players().owns(
                player,
                retryGeneration))
            throw new AssertionError(
                "retry did not publish exact current membership"
            );

        if(!world.pulse().running()||
           world.pulse().thread()==null)
            throw new AssertionError(
                "retry did not start the World pulse"
            );

        if(factoryCalls.get()!=2||
           starterCalls.get()!=2)
            throw new AssertionError(
                "retry did not create exactly one replacement pulse"
            );

        Thread pulse=
            world.pulse().thread();

        world.start();

        if(factoryCalls.get()!=2||
           starterCalls.get()!=2||
           world.pulse().thread()!=pulse)
            throw new AssertionError(
                "successful World start was not idempotent"
            );

        world.close();
    }

    private static void
        assertFailedOnlyAdmissionClosesSafely(){
        SecurityException expected=
            new SecurityException(
                "fixture-world-admission-precreate-failure"
            );
        World world=
            World.isolatedForTest(
                25L,
                (target,name)->{
                    throw expected;
                },
                Thread::start
            );
        WorldPlayer player=
            new WorldPlayer();

        Throwable observed=null;

        try{
            world.registerPlayerAndStart(
                player,
                "admission-close-fixture"
            );
        }catch(Throwable failure){
            observed=failure;
        }

        if(observed!=expected)
            throw new AssertionError(
                "pre-create pulse failure did not remain primary"
            );

        if(world.players().size()!=0||
           player.registered()||
           world.pulse().running()||
           world.pulse().thread()!=null)
            throw new AssertionError(
                "failed-only admission did not roll back before close"
            );

        world.close();
        world.close();

        if(!world.closed())
            throw new AssertionError(
                "World did not close safely after failed-only admission"
            );
    }

    private WorldAdmissionPulseStartAtomicTest(){}
}

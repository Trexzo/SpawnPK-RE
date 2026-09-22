package spk.local;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldPulseTimedEffectWorldOwnershipTest {
    public static void main(String[] args)throws Exception{
        World first=
            World.isolatedForTest(600L);
        World second=
            World.isolatedForTest(600L);

        WorldPlayer blocker=
            new WorldPlayer();
        WorldPlayer transferred=
            new WorldPlayer();

        long blockerGeneration=
            first.registerPlayer(
                blocker,
                "pulse-owner-blocker"
            );
        long transferredGenerationA=
            first.registerPlayer(
                transferred,
                "pulse-owner-transfer"
            );

        String effectKey=
            firstFixedEffectKey();

        transferred.timedEffects()
            .applyFixed(
                effectKey,
                1L,
                0L,
                "TEST_AUTHORITY"
            );

        if(!transferred.timedEffects()
                .active(effectKey))
            throw new AssertionError(
                "effect setup failed"
            );

        AtomicReference<Throwable> pulseFailure=
            new AtomicReference<>();

        Thread worker=
            new Thread(
                ()->{
                    try{
                        first.pulse()
                            .pulseOnce(
                                System.currentTimeMillis()
                            );
                    }catch(Throwable failure){
                        pulseFailure.set(failure);
                    }
                },
                "world-a-pulse-ownership-test"
            );

        long transferredGenerationB;

        synchronized(blocker.mutationLock()){
            worker.start();

            awaitBlockedInPulse(
                worker,
                5_000L
            );

            if(!first.unregisterPlayer(
                    transferred,
                    transferredGenerationA
                ))
                throw new AssertionError(
                    "World A transfer unregister failed"
                );

            transferredGenerationB=
                second.registerPlayer(
                    transferred,
                    "pulse-owner-transfer"
                );

            if(transferredGenerationB==
                    transferredGenerationA)
                throw new AssertionError(
                    "transfer generation did not advance"
                );

            if(first.players().owns(
                    transferred,
                    transferredGenerationB
                ))
                throw new AssertionError(
                    "World A incorrectly owns transferred generation"
                );

            if(!second.players().owns(
                    transferred,
                    transferredGenerationB
                ))
                throw new AssertionError(
                    "World B did not acquire transferred generation"
                );
        }

        worker.join(5_000L);

        if(worker.isAlive())
            throw new AssertionError(
                "World A pulse did not finish"
            );

        if(pulseFailure.get()!=null)
            throw new AssertionError(
                "World A pulse failed",
                pulseFailure.get()
            );

        if(!transferred.timedEffects()
                .active(effectKey))
            throw new AssertionError(
                "World A ticked timed effect after ownership transferred to World B"
            );

        if(first.clock().tick()!=1L)
            throw new AssertionError(
                "World A tick expected 1 actual="+
                first.clock().tick()
            );

        second.pulse()
            .pulseOnce(
                System.currentTimeMillis()
            );

        if(transferred.timedEffects()
                .active(effectKey))
            throw new AssertionError(
                "World B did not tick its owned timed effect"
            );

        if(second.clock().tick()!=1L)
            throw new AssertionError(
                "World B tick expected 1 actual="+
                second.clock().tick()
            );

        if(!first.unregisterPlayer(
                blocker,
                blockerGeneration
            ))
            throw new AssertionError(
                "blocker unregister failed"
            );

        if(!second.unregisterPlayer(
                transferred,
                transferredGenerationB
            ))
            throw new AssertionError(
                "transferred player final unregister failed"
            );

        first.close();
        second.close();

        System.out.println(
            "WORLD_PULSE_TIMED_EFFECT_WORLD_OWNERSHIP_PASS "+
            "snapshotTransfer=true "+
            "oldWorldSkipped=true "+
            "newWorldTicked=true "+
            "generationAdvanced=true"
        );
    }

    private static void awaitBlockedInPulse(
        Thread worker,
        long timeoutMillis
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(System.nanoTime()<deadline){
            if(worker.getState()==
                    Thread.State.BLOCKED){
                boolean inPulse=false;

                for(StackTraceElement element:
                        worker.getStackTrace()){
                    if(WorldPulse.class
                            .getName()
                            .equals(
                                element.getClassName()
                            )&&
                       "pulseOnce".equals(
                           element.getMethodName()
                       )){
                        inPulse=true;
                        break;
                    }
                }

                if(inPulse)
                    return;
            }

            if(!worker.isAlive())
                throw new AssertionError(
                    "pulse worker exited before blocking"
                );

            Thread.sleep(1L);
        }

        throw new AssertionError(
            "pulse worker did not block on first player mutation lock"
        );
    }

    private static String firstFixedEffectKey(){
        for(TimedEffectCatalog.Definition definition:
                TimedEffectCatalog.all()){
            if(!"DYNAMIC".equals(
                    definition.key))
                return definition.key;
        }

        throw new AssertionError(
            "no fixed timed-effect definition available"
        );
    }

    private WorldPulseTimedEffectWorldOwnershipTest(){}
}

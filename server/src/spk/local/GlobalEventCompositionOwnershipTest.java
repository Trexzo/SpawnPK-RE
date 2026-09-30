package spk.local;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic ownership regression for Issue #1139. */
public final class GlobalEventCompositionOwnershipTest {
    public static void main(String[] args)throws Exception{
        blockingAndReentrantAccess();
        terminalHoldBlocksTerminalization();
        actionFailureReleasesOwnership();

        System.out.println(
            "GLOBAL_EVENT_COMPOSITION_OWNERSHIP_PASS "+
            "eventBlocked=true "+
            "reentrantAccess=true "+
            "terminalHold=true "+
            "terminalHoldDeadlineBlock=true "+
            "capabilityIdentity=true "+
            "foreignCapabilityRejected=true "+
            "keyReleaseApiAbsent=true "+
            "actionFailureSafe=true "+
            "protocolIndependent=true"
        );
    }

    private static void blockingAndReentrantAccess()
        throws Exception{
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition definition=
            definition(
                "custom:owned-event",
                10L,
                20L
            );

        service.register(definition);

        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch owned=
            new CountDownLatch(1);
        CountDownLatch release=
            new CountDownLatch(1);
        AtomicReference<Thread> competitorThread=
            new AtomicReference<>();

        try{
            Future<?> owner=
                workers.submit(
                    ()->{
                        service.withEventCompositionOwnership(
                            definition.id,
                            ()->{
                                service.tick(10L);

                                check(
                                    service.get(
                                        definition.id
                                    ).lifecycle==
                                        GlobalEventService
                                            .Lifecycle.ACTIVE,
                                    "reentrant active lifecycle"
                                );

                                owned.countDown();

                                if(!release.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "ownership release timeout"
                                    );
                            }
                        );

                        return null;
                    }
                );

            check(
                owned.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "composition owner did not enter"
            );

            Future<?> competitor=
                workers.submit(
                    ()->{
                        competitorThread.set(
                            Thread.currentThread()
                        );

                        service.complete(
                            definition.id,
                            12L
                        );

                        return null;
                    }
                );

            awaitBlocked(
                competitorThread,
                "GlobalEvent completion crossed composition ownership"
            );

            // Do not call back into the synchronized service here:
            // the owner intentionally retains the same monitor until this
            // thread releases the latch. The owner already proved ACTIVE
            // reentrantly before publishing owned, and awaitBlocked proves
            // the competing lifecycle mutation cannot cross that boundary.
            release.countDown();

            owner.get(
                5L,
                TimeUnit.SECONDS
            );
            competitor.get(
                5L,
                TimeUnit.SECONDS
            );

            check(
                service.get(
                    definition.id
                ).lifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED,
                "competitor did not complete after ownership release"
            );
        }finally{
            release.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
        }
    }

    private static void terminalHoldBlocksTerminalization(){
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition definition=
            definition(
                "custom:terminal-held",
                10L,
                20L
            );

        service.register(definition);
        service.tick(10L);

        GlobalEventService.TerminalHold first=
            service.acquireTerminalHold(
                definition.id
            );

        check(
            service.terminalHoldCount(
                definition.id
            )==1,
            "terminal hold count"
        );

        expect(
            IllegalStateException.class,
            ()->service.complete(
                definition.id,
                12L
            ),
            "held explicit complete"
        );
        expect(
            IllegalStateException.class,
            ()->service.cancel(
                definition.id,
                12L
            ),
            "held explicit cancel"
        );

        service.tick(20L);

        check(
            service.get(
                definition.id
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE,
            "end deadline crossed terminal hold"
        );

        GlobalEventService.TerminalHold second=
            service.acquireTerminalHold(
                definition.id
            );

        check(
            first!=second&&
            service.terminalHoldCount(
                definition.id
            )==2,
            "terminal capabilities are not independent identities"
        );

        service.releaseTerminalHold(
            first
        );

        check(
            service.terminalHoldCount(
                definition.id
            )==1,
            "independent terminal hold count"
        );

        service.tick(20L);

        check(
            service.get(
                definition.id
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE,
            "remaining hold did not block deadline"
        );

        GlobalEventService foreign=
            new GlobalEventService();
        foreign.register(
            definition(
                "custom:foreign-terminal-held",
                10L,
                30L
            )
        );
        WorldEventId foreignId=
            WorldEventId.of(
                "custom:foreign-terminal-held"
            );
        foreign.tick(10L);
        GlobalEventService.TerminalHold foreignHold=
            foreign.acquireTerminalHold(
                foreignId
            );

        expect(
            IllegalArgumentException.class,
            ()->service.releaseTerminalHold(
                foreignHold
            ),
            "foreign terminal capability release"
        );

        service.releaseTerminalHold(
            second
        );

        check(
            service.terminalHoldCount(
                definition.id
            )==0,
            "terminal holds not released"
        );

        service.tick(20L);

        check(
            service.get(
                definition.id
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED,
            "deadline did not complete after hold release"
        );

        expect(
            IllegalStateException.class,
            ()->service.releaseTerminalHold(
                second
            ),
            "double terminal capability release"
        );

        boolean oldKeyReleaseApi=false;

        for(java.lang.reflect.Method method:
                GlobalEventService.class
                    .getDeclaredMethods())
            if(method.getName().equals(
                    "releaseTerminalHold")&&
               Arrays.equals(
                    method.getParameterTypes(),
                    new Class<?>[]{
                        WorldEventId.class,
                        String.class
                    }
               ))
                oldKeyReleaseApi=true;

        check(
            !oldKeyReleaseApi,
            "caller-forgeable key release API remains"
        );

        foreign.releaseTerminalHold(
            foreignHold
        );
    }


    private static void actionFailureReleasesOwnership()
        throws Exception{
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition definition=
            definition(
                "custom:owned-failure",
                30L,
                40L
            );

        service.register(definition);

        expect(
            IllegalStateException.class,
            ()->service.withEventCompositionOwnership(
                definition.id,
                ()->{
                    check(
                        service.get(
                            definition.id
                        ).lifecycle==
                            GlobalEventService
                                .Lifecycle.SCHEDULED,
                        "reentrant snapshot during failing action"
                    );

                    throw new IllegalStateException(
                        "caller boom"
                    );
                }
            ),
            "composition action failure"
        );

        check(
            service.get(
                definition.id
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.SCHEDULED,
            "caller failure mutated event"
        );

        service.cancel(
            definition.id,
            25L
        );

        check(
            service.get(
                definition.id
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.CANCELLED,
            "ownership not released after caller failure"
        );
    }

    private static WorldEventDefinition definition(
        String id,
        long start,
        long end
    ){
        return new WorldEventDefinition(
            WorldEventId.of(id),
            start,
            end,
            Collections.emptyList(),
            "CUSTOM_LOCALLAB"
        );
    }

    private static void awaitBlocked(
        AtomicReference<Thread> thread,
        String label
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(5L);

        while(System.nanoTime()<deadline){
            Thread current=thread.get();

            if(current!=null&&
               current.getState()==
                    Thread.State.BLOCKED)
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(label);
    }

    private static void expect(
        Class<? extends Throwable> type,
        Throwing action,
        String label
    ){
        try{
            action.run();
            throw new AssertionError(
                label+
                " did not throw "+
                type.getSimpleName()
            );
        }catch(Throwable failure){
            if(!type.isInstance(failure))
                throw new AssertionError(
                    label+
                    " threw "+
                    failure,
                    failure
                );
        }
    }

    private static void check(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}

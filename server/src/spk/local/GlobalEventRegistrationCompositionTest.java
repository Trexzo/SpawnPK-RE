package spk.local;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic registration-composition regression for Issue #1168. */
public final class GlobalEventRegistrationCompositionTest {
    public static void main(String[] args)throws Exception{
        atomicPublicationBlocksObservationAndMutation();
        failureRollbackAndDuplicateFence();

        System.out.println(
            "GLOBAL_EVENT_REGISTRATION_COMPOSITION_PASS "+
            "atomicPublication=true "+
            "observerBlocked=true "+
            "mutationBlocked=true "+
            "failureRollback=true "+
            "duplicateActionSuppressed=true "+
            "reentrantRead=true "+
            "scheduledPostimage=true "+
            "protocolIndependent=true"
        );
    }

    private static void atomicPublicationBlocksObservationAndMutation()
        throws Exception{
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition definition=
            definition(
                "custom:registration-owned",
                10L,
                20L
            );

        ExecutorService workers=
            Executors.newFixedThreadPool(3);
        CountDownLatch actionEntered=
            new CountDownLatch(1);
        CountDownLatch release=
            new CountDownLatch(1);
        AtomicReference<Thread> observerThread=
            new AtomicReference<>();
        AtomicReference<Thread> mutatorThread=
            new AtomicReference<>();

        try{
            Future<?> owner=
                workers.submit(
                    ()->{
                        GlobalEventService.Snapshot created=
                            service.registerWithCompositionOwnership(
                                definition,
                                ()->{
                                    GlobalEventService.Snapshot inside=
                                        service.get(
                                            definition.id
                                        );

                                    check(
                                        inside!=null&&
                                        inside.lifecycle==
                                            GlobalEventService
                                                .Lifecycle.SCHEDULED,
                                        "reentrant registration snapshot"
                                    );

                                    actionEntered.countDown();

                                    if(!release.await(
                                            5L,
                                            TimeUnit.SECONDS))
                                        throw new AssertionError(
                                            "registration release timeout"
                                        );
                                }
                            );

                        check(
                            created.lifecycle==
                                GlobalEventService
                                    .Lifecycle.SCHEDULED,
                            "registration postimage not scheduled"
                        );

                        return null;
                    }
                );

            check(
                actionEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "registration owner did not enter"
            );

            Future<GlobalEventService.Snapshot> observer=
                workers.submit(
                    ()->{
                        observerThread.set(
                            Thread.currentThread()
                        );
                        return service.get(
                            definition.id
                        );
                    }
                );

            Future<?> mutator=
                workers.submit(
                    ()->{
                        mutatorThread.set(
                            Thread.currentThread()
                        );
                        service.cancel(
                            definition.id,
                            5L
                        );
                        return null;
                    }
                );

            awaitBlocked(
                observerThread,
                "GlobalEvent observer crossed registration publication"
            );
            awaitBlocked(
                mutatorThread,
                "GlobalEvent mutation crossed registration publication"
            );

            release.countDown();

            owner.get(
                5L,
                TimeUnit.SECONDS
            );

            GlobalEventService.Snapshot observed=
                observer.get(
                    5L,
                    TimeUnit.SECONDS
                );
            mutator.get(
                5L,
                TimeUnit.SECONDS
            );

            check(
                observed!=null&&
                observed.lifecycle==
                    GlobalEventService
                        .Lifecycle.SCHEDULED,
                "observer did not see scheduled publication"
            );
            check(
                service.get(
                    definition.id
                ).lifecycle==
                    GlobalEventService
                        .Lifecycle.CANCELLED,
                "mutator did not run after registration release"
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

    private static void failureRollbackAndDuplicateFence()
        throws Exception{
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition failing=
            definition(
                "custom:registration-failure",
                30L,
                40L
            );

        expect(
            IllegalStateException.class,
            ()->service.registerWithCompositionOwnership(
                failing,
                ()->{
                    check(
                        service.get(
                            failing.id
                        ).lifecycle==
                            GlobalEventService
                                .Lifecycle.SCHEDULED,
                        "failing action cannot read scheduled event"
                    );

                    throw new IllegalStateException(
                        "parent publication boom"
                    );
                }
            ),
            "registration action failure"
        );

        check(
            service.get(
                failing.id
            )==null&&
            service.size()==0,
            "failing registration left orphan event"
        );

        WorldEventDefinition existing=
            definition(
                "custom:registration-duplicate",
                50L,
                60L
            );

        service.register(
            existing
        );

        AtomicInteger actionCalls=
            new AtomicInteger();

        expect(
            IllegalStateException.class,
            ()->service.registerWithCompositionOwnership(
                existing,
                ()->actionCalls.incrementAndGet()
            ),
            "duplicate registration"
        );

        check(
            actionCalls.get()==0&&
            service.size()==1&&
            service.get(
                existing.id
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.SCHEDULED,
            "duplicate registration invoked action or mutated existing event"
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
            Thread current=
                thread.get();

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

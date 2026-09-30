package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Deterministic registration-composition regression for Issue #1168. */
public final class GlobalEventRegistrationCompositionTest {
    public static void main(String[] args)throws Exception{
        atomicPublicationBlocksObservationAndMutation();
        failureRollbackAndDuplicateFence();
        consumerSourceShape();

        System.out.println(
            "GLOBAL_EVENT_REGISTRATION_COMPOSITION_PASS "+
            "atomicPublication=true "+
            "observerBlocked=true "+
            "mutationBlocked=true "+
            "failureRollback=true "+
            "duplicateActionSuppressed=true "+
            "reentrantRead=true "+
            "scheduledPostimage=true "+
            "consumerSourceShape=true "+
            "parentSnapshotInsideOwnership=true "+
            "parentRollbackSymmetric=true "+
            "protocolIndependent=true"
        );
    }

    private static void atomicPublicationBlocksObservationAndMutation()
        throws Exception{
        observerBlocksUntilPublication();
        mutationBlocksUntilPublication();
    }

    private static void observerBlocksUntilPublication()
        throws Exception{
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition definition=
            definition(
                "custom:registration-observer",
                10L,
                20L
            );

        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch actionEntered=
            new CountDownLatch(1);
        CountDownLatch release=
            new CountDownLatch(1);
        AtomicReference<Thread> observerThread=
            new AtomicReference<>();

        try{
            Future<?> owner=
                workers.submit(
                    ()->{
                        GlobalEventService.Snapshot created=
                            service.registerWithCompositionOwnership(
                                definition,
                                ()->{
                                    check(
                                        service.get(
                                            definition.id
                                        ).lifecycle==
                                            GlobalEventService
                                                .Lifecycle.SCHEDULED,
                                        "reentrant observer registration snapshot"
                                    );

                                    actionEntered.countDown();

                                    if(!release.await(
                                            5L,
                                            TimeUnit.SECONDS))
                                        throw new AssertionError(
                                            "observer registration release timeout"
                                        );
                                }
                            );

                        check(
                            created.lifecycle==
                                GlobalEventService
                                    .Lifecycle.SCHEDULED,
                            "observer registration postimage not scheduled"
                        );

                        return null;
                    }
                );

            check(
                actionEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "observer registration owner did not enter"
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

            awaitBlocked(
                observerThread,
                "GlobalEvent observer crossed registration publication"
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

            check(
                observed!=null&&
                observed.lifecycle==
                    GlobalEventService
                        .Lifecycle.SCHEDULED,
                "observer did not see scheduled publication"
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

    private static void mutationBlocksUntilPublication()
        throws Exception{
        GlobalEventService service=
            new GlobalEventService();
        WorldEventDefinition definition=
            definition(
                "custom:registration-mutator",
                10L,
                20L
            );

        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch actionEntered=
            new CountDownLatch(1);
        CountDownLatch release=
            new CountDownLatch(1);
        AtomicReference<Thread> mutatorThread=
            new AtomicReference<>();

        try{
            Future<?> owner=
                workers.submit(
                    ()->{
                        service.registerWithCompositionOwnership(
                            definition,
                            ()->{
                                check(
                                    service.get(
                                        definition.id
                                    ).lifecycle==
                                        GlobalEventService
                                            .Lifecycle.SCHEDULED,
                                    "reentrant mutator registration snapshot"
                                );

                                actionEntered.countDown();

                                if(!release.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "mutator registration release timeout"
                                    );
                            }
                        );

                        return null;
                    }
                );

            check(
                actionEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "mutator registration owner did not enter"
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
                mutatorThread,
                "GlobalEvent mutation crossed registration publication"
            );

            release.countDown();

            owner.get(
                5L,
                TimeUnit.SECONDS
            );
            mutator.get(
                5L,
                TimeUnit.SECONDS
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

    private static void consumerSourceShape()
        throws Exception{
        assertConsumerRegistration(
            read(
                "server/src/spk/local/TournamentService.java"
            ),
            "TournamentService",
            "synchronized Snapshot registerTournament(",
            "tournaments.put(",
            "tournaments.remove("
        );

        assertConsumerRegistration(
            read(
                "server/src/spk/local/PvpHotspotService.java"
            ),
            "PvpHotspotService",
            "synchronized Snapshot registerHotspot(",
            "hotspots.put(",
            "hotspots.remove("
        );
    }

    private static void assertConsumerRegistration(
        String source,
        String label,
        String signature,
        String parentPut,
        String parentRemove
    ){
        String registration=
            method(
                source,
                signature
            );

        int owned=
            registration.indexOf(
                "registerWithCompositionOwnership("
            );
        int snapshot=
            registration.indexOf(
                "Snapshot created=",
                owned
            );
        int put=
            registration.indexOf(
                parentPut,
                owned
            );
        int resultPublish=
            registration.indexOf(
                "result[0]=created;",
                put
            );

        check(
            owned>=0,
            label+
            " registration does not use GlobalEvent registration ownership"
        );
        check(
            !registration.contains(
                "events.register("
            ),
            label+
            " registration reverted to split GlobalEvent publication"
        );
        check(
            snapshot>owned&&
            put>snapshot&&
            resultPublish>put,
            label+
            " registration ordering is not owned snapshot -> parent put -> result publication"
        );
        check(
            count(
                registration,
                parentRemove
            )>=3,
            label+
            " registration failure paths do not symmetrically remove exact parent entry"
        );
    }

    private static String read(
        String relative
    )throws Exception{
        return new String(
            Files.readAllBytes(
                Paths.get(relative)
            ),
            StandardCharsets.UTF_8
        );
    }

    private static String method(
        String source,
        String signature
    ){
        int start=source.indexOf(signature);

        check(
            start>=0,
            "missing source signature "+
            signature
        );

        int open=source.indexOf('{',start);
        check(
            open>=0,
            "missing method body "+
            signature
        );

        int depth=0;

        for(int i=open;i<source.length();i++){
            char value=source.charAt(i);

            if(value=='{')
                depth++;
            else if(value=='}'){
                depth--;

                if(depth==0)
                    return source.substring(
                        start,
                        i+1
                    );
            }
        }

        throw new AssertionError(
            "unterminated method "+
            signature
        );
    }

    private static int count(
        String source,
        String token
    ){
        int count=0;
        int from=0;

        while(true){
            int found=source.indexOf(
                token,
                from
            );

            if(found<0)
                return count;

            count++;
            from=found+token.length();
        }
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

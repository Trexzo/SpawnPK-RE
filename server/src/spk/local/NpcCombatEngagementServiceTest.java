package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class NpcCombatEngagementServiceTest {
    public static void main(String[] args)throws Exception{
        waitingAttackAndDelegation();
        oneEngagementAndCancel();
        staleNpcAndTargetCancel();
        cadenceAndExecutorFailureNoAdvance();
        overflowAtomic();
        revisionOverflowAtomic();
        ownershipLinearized();
        concurrentTickSingleOwner();
        tickOwnershipLinearized();
        cadenceReentrantCancelPreventsExecutor();
        executorReentrantCancelReportsAttackTruthfully();
        authorityAndBoundary();

        System.out.println(
            "NPC_COMBAT_ENGAGEMENT_PASS "+
            "explicitTarget=true "+
            "exactTargetGeneration=true "+
            "oneEngagementPerNpc=true "+
            "firstAttackTick=true "+
            "waiting=true "+
            "cadenceCallerOwned=true "+
            "cadenceOverflowAtomic=true "+
            "revisionOverflowAtomic=true "+
            "beginOwnershipLinearized=true "+
            "cancelOwnershipLinearized=true "+
            "tickSingleOwner=true "+
            "tickOwnershipLinearized=true "+
            "cadenceReentrantCancelPreventsExecutor=true "+
            "executorReentrantCancelTruthful=true "+
            "executorDelegated=true "+
            "executorFailureNoAdvance=true "+
            "executorFailureReleasesReservation=true "+
            "staleNpcCancels=true "+
            "staleTargetCancels=true "+
            "cancelIdempotent=true "+
            "revisioned=true "+
            "aggroOwned=false "+
            "pathingOwned=false "+
            "damageOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void waitingAttackAndDelegation()throws Exception{
        Fixture f=new Fixture("engage-main");
        try{
            NpcPlayerCombatResolutionService damage=
                new NpcPlayerCombatResolutionService(
                    fixedDamage(5)
                );

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(3),
                    (npc,target,generation,tick)->
                        damage.resolveImmediateOwned(
                            f.world,
                            npc,
                            target,
                            generation,
                            tick
                        )
                );

            NpcCombatEngagementService.Snapshot begun=
                service.begin(
                    f.npc,
                    f.player,
                    f.generation,
                    10L
                );

            require(
                begun.nextAttackTick==10L&&
                begun.revision==0L,
                "begin schedule"
            );

            NpcCombatEngagementService.TickResult wait=
                service.tick(f.npc.id,9L);

            require(
                wait.status==
                    NpcCombatEngagementService.TickStatus.WAITING&&
                hp(f.player)==99,
                "waiting tick"
            );

            NpcCombatEngagementService.TickResult hit=
                service.tick(f.npc.id,10L);

            require(
                hit.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                hp(f.player)==94&&
                hit.snapshot.nextAttackTick==13L&&
                hit.snapshot.revision==1L,
                "delegated attack"
            );
        }finally{
            f.close();
        }
    }

    private static void oneEngagementAndCancel()throws Exception{
        Fixture f=new Fixture("engage-cancel");
        try{
            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(4),
                    (a,t,g,w)->{}
                );

            service.begin(f.npc,f.player,f.generation,0L);

            expect(
                IllegalStateException.class,
                ()->service.begin(
                    f.npc,
                    f.player,
                    f.generation,
                    1L
                ),
                "duplicate engagement"
            );

            require(
                service.cancel(f.npc)&&
                !service.cancel(f.npc)&&
                service.size()==0,
                "cancel idempotency"
            );
        }finally{
            f.close();
        }
    }

    private static void staleNpcAndTargetCancel()throws Exception{
        Fixture a=new Fixture("engage-stale-npc");
        try{
            int[] cadenceCalls={0};
            int[] attackCalls={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    a.world,
                    countingCadence(cadenceCalls,2),
                    (n,t,g,w)->attackCalls[0]++
                );

            service.begin(a.npc,a.player,a.generation,0L);
            require(
                a.world.npcs().remove(a.npc.id),
                "remove stale npc fixture"
            );

            NpcCombatEngagementService.TickResult result=
                service.tick(a.npc.id,0L);

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.STALE_ATTACKER&&
                service.size()==0&&
                cadenceCalls[0]==0&&
                attackCalls[0]==0,
                "stale npc did not cancel"
            );
        }finally{
            a.close();
        }

        Fixture b=new Fixture("engage-stale-target");
        try{
            int[] cadenceCalls={0};
            int[] attackCalls={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    b.world,
                    countingCadence(cadenceCalls,2),
                    (n,t,g,w)->attackCalls[0]++
                );

            service.begin(b.npc,b.player,b.generation,0L);

            require(
                b.world.unregisterPlayer(
                    b.player,
                    b.generation
                ),
                "remove stale target fixture"
            );
            b.generation=
                b.world.registerPlayer(
                    b.player,
                    "engage-stale-target"
                );

            NpcCombatEngagementService.TickResult result=
                service.tick(b.npc.id,0L);

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.STALE_TARGET&&
                service.size()==0&&
                cadenceCalls[0]==0&&
                attackCalls[0]==0,
                "stale target did not cancel"
            );
        }finally{
            b.close();
        }
    }

    private static void cadenceAndExecutorFailureNoAdvance()
        throws Exception{
        Fixture f=new Fixture("engage-failure");
        try{
            NpcCombatEngagementService cadenceFail=
                new NpcCombatEngagementService(
                    f.world,
                    new NpcCombatEngagementService
                        .CadenceResolver(){
                        public int nextDelayTicks(
                            NpcCombatEngagementService.Context c
                        ){
                            throw new IllegalStateException(
                                "cadence boom"
                            );
                        }
                        public String authority(){
                            return "CUSTOM_LOCALLAB_CADENCE";
                        }
                        public String policy(){
                            return "TEST";
                        }
                    },
                    (n,t,g,w)->{}
                );

            cadenceFail.begin(f.npc,f.player,f.generation,5L);

            expect(
                IllegalStateException.class,
                ()->cadenceFail.tick(f.npc.id,5L),
                "cadence failure"
            );

            require(
                cadenceFail.get(f.npc.id).nextAttackTick==5L&&
                cadenceFail.get(f.npc.id).revision==0L,
                "cadence failure advanced schedule"
            );

            cadenceFail.cancel(f.npc);

            AtomicInteger executorCalls=
                new AtomicInteger();

            NpcCombatEngagementService executorFail=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->{
                        if(executorCalls.incrementAndGet()==1)
                            throw new IllegalStateException(
                                "executor boom"
                            );
                    }
                );

            executorFail.begin(f.npc,f.player,f.generation,7L);

            expect(
                IllegalStateException.class,
                ()->executorFail.tick(f.npc.id,7L),
                "executor failure"
            );

            require(
                executorFail.get(f.npc.id).nextAttackTick==7L&&
                executorFail.get(f.npc.id).revision==0L,
                "executor failure advanced schedule"
            );

            NpcCombatEngagementService.TickResult retry=
                executorFail.tick(
                    f.npc.id,
                    7L
                );

            require(
                retry.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                retry.snapshot.nextAttackTick==9L&&
                retry.snapshot.revision==1L&&
                executorCalls.get()==2,
                "executor failure reservation was not released"
            );
        }finally{
            f.close();
        }
    }

    private static void overflowAtomic()throws Exception{
        Fixture f=new Fixture("engage-overflow");
        try{
            int[] attacks={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->attacks[0]++
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                Long.MAX_VALUE-1L
            );

            expect(
                IllegalStateException.class,
                ()->service.tick(
                    f.npc.id,
                    Long.MAX_VALUE-1L
                ),
                "cadence overflow"
            );

            require(
                attacks[0]==0&&
                service.get(f.npc.id).revision==0L,
                "overflow invoked executor/advanced"
            );
        }finally{
            f.close();
        }
    }

    private static void revisionOverflowAtomic()throws Exception{
        Fixture f=new Fixture(
            "engage-revision-overflow"
        );
        try{
            int[] attacks={0};

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->attacks[0]++
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                7L
            );

            setEngagementRevision(
                service,
                f.npc.id,
                Long.MAX_VALUE
            );

            expect(
                IllegalStateException.class,
                ()->service.tick(
                    f.npc.id,
                    7L
                ),
                "revision overflow"
            );

            NpcCombatEngagementService.Snapshot
                after=
                    service.get(
                        f.npc.id
                    );

            require(
                attacks[0]==0&&
                after.nextAttackTick==7L&&
                after.revision==
                    Long.MAX_VALUE,
                "revision overflow invoked executor/advanced schedule"
            );
        }finally{
            f.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void ownershipLinearized()throws Exception{
        beginOwnershipLinearized();
        cancelOwnershipLinearized();
    }

    @SuppressWarnings("unchecked")
    private static void beginOwnershipLinearized()throws Exception{
        Fixture f=new Fixture("engage-owned-begin");
        ExecutorService workers=
            Executors.newFixedThreadPool(3);

        try{
            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->{}
                );

            Map<EntityId,Object> engagements=
                engagementMap(service);

            AtomicReference<Thread> beginThread=
                new AtomicReference<>();
            CountDownLatch beginStarted=
                new CountDownLatch(1);

            Future<NpcCombatEngagementService.Snapshot> begin;

            synchronized(engagements){
                begin=
                    workers.submit(
                        ()->{
                            beginThread.set(
                                Thread.currentThread()
                            );
                            beginStarted.countDown();

                            return service.begin(
                                f.npc,
                                f.player,
                                f.generation,
                                0L
                            );
                        }
                    );

                require(
                    beginStarted.await(
                        5L,
                        TimeUnit.SECONDS
                    ),
                    "begin worker start"
                );

                awaitBlocked(
                    beginThread,
                    "begin publication did not reach engagement lock"
                );

                CountDownLatch removeStarted=
                    new CountDownLatch(1);
                CountDownLatch unregisterStarted=
                    new CountDownLatch(1);

                Future<Boolean> remove=
                    workers.submit(
                        ()->{
                            removeStarted.countDown();
                            return f.world.npcs()
                                .remove(
                                    f.npc.id
                                );
                        }
                    );

                Future<Boolean> unregister=
                    workers.submit(
                        ()->{
                            unregisterStarted.countDown();
                            return f.world
                                .unregisterPlayer(
                                    f.player,
                                    f.generation
                                );
                        }
                    );

                require(
                    removeStarted.await(
                        5L,
                        TimeUnit.SECONDS
                    )&&
                    unregisterStarted.await(
                        5L,
                        TimeUnit.SECONDS
                    ),
                    "begin competitors start"
                );

                requireBlocked(
                    remove,
                    "NPC removal crossed begin ownership"
                );
                requireBlocked(
                    unregister,
                    "target unregister crossed begin ownership"
                );
            }

            NpcCombatEngagementService.Snapshot snapshot=
                begin.get(
                    5L,
                    TimeUnit.SECONDS
                );

            require(
                snapshot!=null&&
                snapshot.attackerId.equals(
                    f.npc.id
                ),
                "begin result"
            );

            /*
             * Competitors are now allowed to proceed only after the owned
             * publication boundary released its NPC/player ownership.
             */
            workers.shutdown();
            require(
                workers.awaitTermination(
                    5L,
                    TimeUnit.SECONDS
                ),
                "begin worker completion"
            );
        }finally{
            workers.shutdownNow();
            f.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void cancelOwnershipLinearized()throws Exception{
        Fixture f=new Fixture("engage-owned-cancel");
        ExecutorService workers=
            Executors.newFixedThreadPool(3);

        try{
            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->{}
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            Map<EntityId,Object> engagements=
                engagementMap(service);

            CountDownLatch blockerEntered=
                new CountDownLatch(1);
            CountDownLatch releaseBlocker=
                new CountDownLatch(1);

            Future<Boolean> blocker=
                workers.submit(
                    ()->
                        f.world.npcs()
                            .withCurrentMutationOwnershipIfCurrent(
                                f.npc,
                                ()->{
                                    blockerEntered.countDown();

                                    require(
                                        releaseBlocker.await(
                                            5L,
                                            TimeUnit.SECONDS
                                        ),
                                        "cancel ownership blocker release"
                                    );
                                }
                            )
                );

            require(
                blockerEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "cancel ownership blocker entry"
            );

            AtomicReference<Thread> cancelThread=
                new AtomicReference<>();
            CountDownLatch cancelStarted=
                new CountDownLatch(1);

            Future<Boolean> cancel=
                workers.submit(
                    ()->{
                        cancelThread.set(
                            Thread.currentThread()
                        );
                        cancelStarted.countDown();

                        return service.cancel(
                            f.npc
                        );
                    }
                );

            require(
                cancelStarted.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "cancel worker start"
            );

            /*
             * The start latch fires immediately before service.cancel(...).
             * First prove cancel has crossed its initial engagement-map
             * idempotency precheck and is actually blocked on the NPC registry
             * lock still owned by blocker. Otherwise the main thread can grab
             * engagements first and mistake the initial precheck wait for the
             * later final-removal wait.
             */
            awaitBlocked(
                cancelThread,
                "cancel did not reach NPC ownership"
            );

            /*
             * Cancel is now queued on NPC mutation ownership held by blocker.
             * Taking the engagement monitor lets us release that blocker and
             * force cancel to acquire NPC ownership before it blocks at the
             * final removal.
             */
            synchronized(engagements){
                releaseBlocker.countDown();

                require(
                    blocker.get(
                        5L,
                        TimeUnit.SECONDS
                    ),
                    "cancel ownership blocker rejected"
                );

                awaitBlocked(
                    cancelThread,
                    "cancel removal did not reach engagement lock"
                );

                CountDownLatch removeStarted=
                    new CountDownLatch(1);

                Future<Boolean> remove=
                    workers.submit(
                        ()->{
                            removeStarted.countDown();
                            return f.world.npcs()
                                .remove(
                                    f.npc.id
                                );
                        }
                    );

                require(
                    removeStarted.await(
                        5L,
                        TimeUnit.SECONDS
                    ),
                    "cancel competitor start"
                );

                requireBlocked(
                    remove,
                    "NPC removal crossed cancel ownership"
                );
            }

            require(
                cancel.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "cancel result"
            );

            workers.shutdown();
            require(
                workers.awaitTermination(
                    5L,
                    TimeUnit.SECONDS
                ),
                "cancel worker completion"
            );

            require(
                service.size()==0,
                "cancel left engagement"
            );
        }finally{
            workers.shutdownNow();
            f.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<EntityId,Object> engagementMap(
        NpcCombatEngagementService service
    )throws Exception{
        Field field=
            NpcCombatEngagementService.class
                .getDeclaredField(
                    "engagements"
                );
        field.setAccessible(true);

        return (Map<EntityId,Object>)
            field.get(service);
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
               current.getState()==Thread.State.BLOCKED)
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(label);
    }

    private static void requireBlocked(
        Future<?> future,
        String label
    )throws Exception{
        try{
            future.get(
                150L,
                TimeUnit.MILLISECONDS
            );
            throw new AssertionError(label);
        }catch(TimeoutException expected){
            // Expected while canonical ownership is retained.
        }
    }


    @SuppressWarnings("unchecked")
    private static void setEngagementRevision(
        NpcCombatEngagementService service,
        EntityId attackerId,
        long revision
    )throws Exception{
        Field engagementsField=
            NpcCombatEngagementService.class
                .getDeclaredField(
                    "engagements"
                );
        engagementsField.setAccessible(true);

        java.util.Map<EntityId,Object>
            engagements=
                (java.util.Map<EntityId,Object>)
                    engagementsField.get(
                        service
                    );

        Object engagement=
            java.util.Objects.requireNonNull(
                engagements.get(
                    attackerId
                ),
                "engagement"
            );

        Field revisionField=
            engagement.getClass()
                .getDeclaredField(
                    "revision"
                );
        revisionField.setAccessible(true);
        revisionField.setLong(
            engagement,
            revision
        );
    }


    private static void concurrentTickSingleOwner()
        throws Exception{
        Fixture f=new Fixture(
            "engage-concurrent-tick"
        );
        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch releaseAttack=
            new CountDownLatch(1);

        try{
            AtomicInteger attacks=
                new AtomicInteger();
            CountDownLatch attackEntered=
                new CountDownLatch(1);

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->{
                        attacks.incrementAndGet();
                        attackEntered.countDown();

                        if(!releaseAttack.await(
                                5L,
                                TimeUnit.SECONDS))
                            throw new AssertionError(
                                "attack release timeout"
                            );
                    }
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            Future<NpcCombatEngagementService.TickResult>
                first=
                    workers.submit(
                        ()->service.tick(
                            f.npc.id,
                            0L
                        )
                    );

            require(
                attackEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "first attack did not enter executor"
            );

            Future<NpcCombatEngagementService.TickResult>
                second=
                    workers.submit(
                        ()->service.tick(
                            f.npc.id,
                            0L
                        )
                    );

            NpcCombatEngagementService.TickResult
                secondResult=
                    second.get(
                        2L,
                        TimeUnit.SECONDS
                    );

            require(
                secondResult.status==
                    NpcCombatEngagementService.TickStatus.WAITING&&
                attacks.get()==1,
                "concurrent tick executed duplicate attack"
            );

            releaseAttack.countDown();

            NpcCombatEngagementService.TickResult
                firstResult=
                    first.get(
                        5L,
                        TimeUnit.SECONDS
                    );

            NpcCombatEngagementService.Snapshot
                after=
                    service.get(
                        f.npc.id
                    );

            require(
                firstResult.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                attacks.get()==1&&
                after.nextAttackTick==2L&&
                after.revision==1L,
                "single-owner tick publication"
            );
        }finally{
            releaseAttack.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void tickOwnershipLinearized()
        throws Exception{
        Fixture f=new Fixture(
            "engage-owned-tick"
        );
        ExecutorService workers=
            Executors.newFixedThreadPool(3);
        CountDownLatch releaseAttack=
            new CountDownLatch(1);

        try{
            AtomicInteger attacks=
                new AtomicInteger();
            CountDownLatch attackEntered=
                new CountDownLatch(1);

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    cadence(2),
                    (n,t,g,w)->{
                        attacks.incrementAndGet();
                        attackEntered.countDown();

                        if(!releaseAttack.await(
                                5L,
                                TimeUnit.SECONDS))
                            throw new AssertionError(
                                "owned attack release timeout"
                            );
                    }
                );

            service.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            Future<NpcCombatEngagementService.TickResult>
                tick=
                    workers.submit(
                        ()->service.tick(
                            f.npc.id,
                            0L
                        )
                    );

            require(
                attackEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "owned tick did not enter executor"
            );

            Future<Boolean> remove=
                workers.submit(
                    ()->f.world.npcs()
                        .remove(
                            f.npc.id
                        )
                );

            Future<Boolean> unregister=
                workers.submit(
                    ()->f.world.unregisterPlayer(
                        f.player,
                        f.generation
                    )
                );

            requireBlocked(
                remove,
                "NPC removal crossed owned tick executor"
            );
            requireBlocked(
                unregister,
                "target unregister crossed owned tick executor"
            );

            releaseAttack.countDown();

            NpcCombatEngagementService.TickResult
                tickResult=
                    tick.get(
                        5L,
                        TimeUnit.SECONDS
                    );

            require(
                tickResult.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                tickResult.snapshot.revision==1L&&
                attacks.get()==1,
                "owned tick result"
            );

            require(
                remove.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "NPC removal after tick ownership release"
            );
            require(
                unregister.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "target unregister after tick ownership release"
            );
        }finally{
            releaseAttack.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void cadenceReentrantCancelPreventsExecutor()
        throws Exception{
        Fixture f=new Fixture(
            "engage-cadence-reentrant-cancel"
        );

        try{
            AtomicReference<NpcCombatEngagementService>
                serviceRef=
                    new AtomicReference<>();
            AtomicInteger attacks=
                new AtomicInteger();

            NpcCombatEngagementService.CadenceResolver
                reentrantCadence=
                    new NpcCombatEngagementService.CadenceResolver(){
                        public int nextDelayTicks(
                            NpcCombatEngagementService.Context c
                        ){
                            NpcCombatEngagementService service=
                                serviceRef.get();

                            require(
                                service!=null&&
                                service.cancel(f.npc),
                                "cadence reentrant cancel did not win"
                            );

                            return 2;
                        }

                        public String authority(){
                            return "CUSTOM_LOCALLAB_CADENCE";
                        }

                        public String policy(){
                            return "TEST_REENTRANT_CANCEL";
                        }
                    };

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    f.world,
                    reentrantCadence,
                    (n,t,g,w)->attacks.incrementAndGet()
                );

            serviceRef.set(service);

            service.begin(
                f.npc,
                f.player,
                f.generation,
                0L
            );

            NpcCombatEngagementService.TickResult result=
                service.tick(
                    f.npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.NONE&&
                attacks.get()==0&&
                service.get(f.npc.id)==null,
                "cadence reentrant cancel crossed executor boundary"
            );
        }finally{
            f.close();
        }
    }


    private static void executorReentrantCancelReportsAttackTruthfully()
        throws Exception{
        Fixture cancelFixture=
            new Fixture(
                "engage-executor-reentrant-cancel"
            );

        try{
            AtomicReference<NpcCombatEngagementService>
                serviceRef=
                    new AtomicReference<>();
            AtomicInteger attacks=
                new AtomicInteger();

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    cancelFixture.world,
                    cadence(2),
                    (npc,target,generation,tick)->{
                        attacks.incrementAndGet();

                        NpcCombatEngagementService current=
                            serviceRef.get();

                        require(
                            current!=null&&
                            current.cancel(cancelFixture.npc),
                            "executor reentrant cancel did not win"
                        );
                    }
                );

            serviceRef.set(service);

            service.begin(
                cancelFixture.npc,
                cancelFixture.player,
                cancelFixture.generation,
                0L
            );

            NpcCombatEngagementService.TickResult result=
                service.tick(
                    cancelFixture.npc.id,
                    0L
                );

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                result.snapshot==null&&
                attacks.get()==1&&
                service.get(
                    cancelFixture.npc.id
                )==null,
                "executor reentrant cancel attack truth"
            );
        }finally{
            cancelFixture.close();
        }

        Fixture replacementFixture=
            new Fixture(
                "engage-executor-reentrant-replace"
            );

        try{
            AtomicReference<NpcCombatEngagementService>
                serviceRef=
                    new AtomicReference<>();
            AtomicInteger attacks=
                new AtomicInteger();

            NpcCombatEngagementService service=
                new NpcCombatEngagementService(
                    replacementFixture.world,
                    cadence(3),
                    (npc,target,generation,tick)->{
                        attacks.incrementAndGet();

                        NpcCombatEngagementService current=
                            serviceRef.get();

                        require(
                            current!=null&&
                            current.cancel(
                                replacementFixture.npc
                            ),
                            "executor replacement cancel did not win"
                        );

                        current.begin(
                            replacementFixture.npc,
                            replacementFixture.player,
                            replacementFixture.generation,
                            50L
                        );
                    }
                );

            serviceRef.set(service);

            service.begin(
                replacementFixture.npc,
                replacementFixture.player,
                replacementFixture.generation,
                0L
            );

            NpcCombatEngagementService.TickResult result=
                service.tick(
                    replacementFixture.npc.id,
                    0L
                );

            NpcCombatEngagementService.Snapshot replacement=
                service.get(
                    replacementFixture.npc.id
                );

            require(
                result.status==
                    NpcCombatEngagementService.TickStatus.ATTACKED&&
                result.snapshot==null&&
                attacks.get()==1&&
                replacement!=null&&
                replacement.nextAttackTick==50L&&
                replacement.revision==0L,
                "executor reentrant replacement schedule isolation"
            );
        }finally{
            replacementFixture.close();
        }
    }


    private static void authorityAndBoundary(){
        Fixture f=new Fixture("engage-boundary");
        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcCombatEngagementService(
                    f.world,
                    cadenceWithAuthority(
                        2,
                        "EXACT_CURRENT_CLIENT"
                    ),
                    (n,t,g,w)->{}
                ),
                "client cadence authority"
            );

            for(Field field:
                    NpcCombatEngagementService.class
                        .getDeclaredFields()){
                String name=field.getName()
                    .toLowerCase(Locale.ROOT);

                for(String forbidden:new String[]{
                        "packet","opcode","widget","sceneindex",
                        "aggro","path","damage","animation",
                        "gfx","projectile","reward","drop"
                })
                    require(
                        !name.contains(forbidden),
                        "unowned policy leaked through field "+
                        field.getName()
                    );
            }
        }finally{
            f.close();
        }
    }

    private static NpcCombatEngagementService.CadenceResolver
        cadence(int delay){
        return cadenceWithAuthority(
            delay,
            "CUSTOM_LOCALLAB_CADENCE"
        );
    }

    private static NpcCombatEngagementService.CadenceResolver
        countingCadence(
            int[] calls,
            int delay
        ){
        return new NpcCombatEngagementService.CadenceResolver(){
            public int nextDelayTicks(
                NpcCombatEngagementService.Context c
            ){
                calls[0]++;
                return delay;
            }
            public String authority(){
                return "CUSTOM_LOCALLAB_CADENCE";
            }
            public String policy(){
                return "TEST_FIXED_DELAY";
            }
        };
    }

    private static NpcCombatEngagementService.CadenceResolver
        cadenceWithAuthority(
            int delay,
            String authority
        ){
        return new NpcCombatEngagementService.CadenceResolver(){
            public int nextDelayTicks(
                NpcCombatEngagementService.Context c
            ){
                return delay;
            }
            public String authority(){
                return authority;
            }
            public String policy(){
                return "TEST_FIXED_DELAY";
            }
        };
    }

    private static NpcPlayerCombatResolutionService.DamageResolver
        fixedDamage(int damage){
        return new NpcPlayerCombatResolutionService.DamageResolver(){
            public int resolve(
                NpcPlayerCombatResolutionService.DamageContext c
            ){
                return damage;
            }
            public String authority(){
                return "CUSTOM_LOCALLAB_NPC_DAMAGE";
            }
            public String formula(){
                return "TEST_FIXED_DAMAGE";
            }
        };
    }

    private static int hp(WorldPlayer player){
        return player.playerState()
            .currentLevel(PlayerState.HITPOINTS);
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer player=new WorldPlayer();
        long generation;
        final WorldNpc npc;

        Fixture(String username){
            generation=world.registerPlayer(
                player,
                username
            );
            npc=world.npcs().spawn(
                1488,
                3200,
                3200,
                0
            );
        }

        void close(){
            for(WorldPlayer current:
                    world.players().snapshot())
                world.unregisterPlayer(current);
            world.close();
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;
            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }
        throw new AssertionError(
            label+" did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private NpcCombatEngagementServiceTest(){}
}

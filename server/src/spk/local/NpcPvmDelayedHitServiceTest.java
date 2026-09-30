package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class NpcPvmDelayedHitServiceTest {
    public static void main(String[] args)throws Exception{
        noEarlyDamageAndDueOnce();
        sameTickInsertionOrder();
        cancellationPreventsDamage();
        cancellationDuringDueDeliveryNoDamage();
        deliveryFailureTerminalNoRetry();
        staleAttackerNoDamage();
        staleTargetNoDamage();
        alreadyDeadDoesNotDuplicateDeath();
        zeroDamageDelivered();
        scheduleOwnershipAndLockOrder();
        scheduleTickQueueAtomic();
        clockAdvanceBlockedDuringPublication();
        overflowAtomic();
        terminalRetirement();
        authorityAndBoundary();

        System.out.println(
            "NPC_PVM_DELAYED_HIT_PASS "+
            "worldEventQueue=true "+
            "exactAttackerGeneration=true "+
            "exactTargetObject=true "+
            "lockOrderPlayerThenNpc=true "+
            "scheduleTickQueueAtomic=true "+
            "clockQueuePublicationLinearized=true "+
            "positiveDelay=true "+
            "overflowAtomic=true "+
            "noEarlyDamage=true "+
            "dueDamageOnce=true "+
            "sameTickOrder=true "+
            "cancellation=true "+
            "staleAttackerNoDamage=true "+
            "staleTargetNoDamage=true "+
            "duplicateDeath=false "+
            "zeroDamage=true "+
            "terminalRetirement=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void noEarlyDamageAndDueOnce()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-due",
                40
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot
                scheduled=
                    service.schedule(
                        f.player,
                        f.generation,
                        f.npc,
                        7,
                        2,
                        "TEST_DAMAGE",
                        "FIXED_7"
                    );

            require(
                scheduled.state==
                    NpcPvmDelayedHitService.State.SCHEDULED&&
                scheduled.scheduledFromTick==0L&&
                scheduled.dueTick==2L&&
                f.world.events().size()==1&&
                f.hp()==40,
                "schedule facts"
            );

            long tick1=
                f.world.clock().advance();

            require(
                tick1==1L&&
                f.world.events().runDue(tick1)==0&&
                f.hp()==40&&
                service.get(
                    scheduled.hitId
                ).state==
                    NpcPvmDelayedHitService.State.SCHEDULED,
                "early delivery"
            );

            long tick2=
                f.world.clock().advance();

            require(
                tick2==2L&&
                f.world.events().runDue(tick2)==1,
                "due event count"
            );

            NpcPvmDelayedHitService.Snapshot
                delivered=
                    service.get(
                        scheduled.hitId
                    );

            require(
                delivered.state==
                    NpcPvmDelayedHitService.State.DELIVERED&&
                delivered.appliedDamage==7&&
                delivered.hitpointsBefore==40&&
                delivered.hitpointsAfter==33&&
                !delivered.newlyDied&&
                !delivered.ignoredDead&&
                f.hp()==33,
                "due delivery"
            );

            require(
                f.world.events().runDue(tick2)==0&&
                f.hp()==33,
                "duplicate due delivery"
            );
        }finally{
            f.close();
        }
    }

    private static void sameTickInsertionOrder()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-order",
                30
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot first=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    7,
                    1,
                    "TEST_DAMAGE",
                    "FIRST"
                );

            NpcPvmDelayedHitService.Snapshot second=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    9,
                    1,
                    "TEST_DAMAGE",
                    "SECOND"
                );

            require(
                f.world.events().size()==2,
                "same-tick events missing"
            );

            long tick=
                f.world.clock().advance();

            require(
                f.world.events().runDue(tick)==2,
                "same-tick event count"
            );

            NpcPvmDelayedHitService.Snapshot a=
                service.get(first.hitId);
            NpcPvmDelayedHitService.Snapshot b=
                service.get(second.hitId);

            require(
                a.state==
                    NpcPvmDelayedHitService.State.DELIVERED&&
                b.state==
                    NpcPvmDelayedHitService.State.DELIVERED&&
                a.hitpointsBefore==30&&
                a.hitpointsAfter==23&&
                b.hitpointsBefore==23&&
                b.hitpointsAfter==14&&
                f.hp()==14,
                "same-tick insertion order"
            );
        }finally{
            f.close();
        }
    }

    private static void cancellationPreventsDamage()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-cancel",
                25
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    11,
                    1,
                    "TEST_DAMAGE",
                    "CANCELLED"
                );

            require(
                service.cancel(hit.hitId),
                "first cancel"
            );
            require(
                !service.cancel(hit.hitId)&&
                service.get(hit.hitId).state==
                    NpcPvmDelayedHitService.State.CANCELLED&&
                f.world.events().size()==0,
                "cancel idempotency"
            );

            long tick=
                f.world.clock().advance();

            require(
                f.world.events().runDue(tick)==0&&
                f.hp()==25,
                "cancelled hit applied"
            );
        }finally{
            f.close();
        }
    }

    private static void cancellationDuringDueDeliveryNoDamage()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-cancel-due",
                25
            );
        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch npcLockHeld=
            new CountDownLatch(1);
        CountDownLatch releaseNpcLock=
            new CountDownLatch(1);
        AtomicReference<Thread> deliveryThread=
            new AtomicReference<>();

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    11,
                    1,
                    "TEST_DAMAGE",
                    "CANCEL_DURING_DUE"
                );

            Future<Boolean> blocker=
                workers.submit(
                    ()->f.world.npcs()
                        .withCurrentMutationOwnershipIfCurrent(
                            f.npc,
                            ()->{
                                npcLockHeld.countDown();

                                if(!releaseNpcLock.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "due cancel NPC lock timeout"
                                    );
                            }
                        )
                );

            require(
                npcLockHeld.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "due cancel blocker start"
            );

            long tick=
                f.world.clock().advance();

            Future<Integer> delivery=
                workers.submit(
                    ()->{
                        deliveryThread.set(
                            Thread.currentThread()
                        );
                        return f.world.events()
                            .runDue(tick);
                    }
                );

            awaitBlocked(
                deliveryThread,
                "due delivery did not wait on NPC ownership"
            );

            require(
                service.cancel(
                    hit.hitId
                ),
                "cancel during due delivery"
            );

            releaseNpcLock.countDown();

            require(
                blocker.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "due cancel blocker ownership"
            );

            require(
                delivery.get(
                    5L,
                    TimeUnit.SECONDS
                )==1,
                "due cancel event count"
            );

            NpcPvmDelayedHitService.Snapshot result=
                service.get(
                    hit.hitId
                );

            require(
                result.state==
                    NpcPvmDelayedHitService.State.CANCELLED&&
                f.hp()==25,
                "cancel during due delivery applied damage"
            );
        }finally{
            releaseNpcLock.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void deliveryFailureTerminalNoRetry()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-failure",
                18
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    4,
                    1,
                    "TEST_DAMAGE",
                    "FAILURE"
                );

            require(
                f.lifecycle.unregister(
                    f.npc.id
                ),
                "remove lifecycle fixture"
            );

            long tick=
                f.world.clock().advance();

            require(
                f.world.events().runDue(
                    tick
                )==1,
                "failed delivery event count"
            );

            NpcPvmDelayedHitService.Snapshot failed=
                service.get(
                    hit.hitId
                );

            require(
                failed.state==
                    NpcPvmDelayedHitService.State.FAILED&&
                IllegalArgumentException.class
                    .getName()
                    .equals(
                        failed.failureType
                    )&&
                f.world.events().runDue(
                    tick
                )==0&&
                service.get(
                    hit.hitId
                ).state==
                    NpcPvmDelayedHitService.State.FAILED,
                "delivery failure was retried or not retained"
            );
        }finally{
            f.close();
        }
    }


    private static void staleAttackerNoDamage()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-stale-attacker",
                22
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    8,
                    1,
                    "TEST_DAMAGE",
                    "STALE_ATTACKER"
                );

            require(
                f.world.unregisterPlayer(
                    f.player,
                    f.generation
                ),
                "attacker unregister"
            );

            long tick=
                f.world.clock().advance();
            f.world.events().runDue(tick);

            require(
                service.get(hit.hitId).state==
                    NpcPvmDelayedHitService.State.STALE_ATTACKER&&
                f.hp()==22,
                "stale attacker mutated target"
            );
        }finally{
            f.close();
        }
    }

    private static void staleTargetNoDamage()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-stale-target",
                22
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    8,
                    1,
                    "TEST_DAMAGE",
                    "STALE_TARGET"
                );

            require(
                f.world.npcs().remove(
                    f.npc.id
                ),
                "target removal"
            );

            long tick=
                f.world.clock().advance();
            f.world.events().runDue(tick);

            require(
                service.get(hit.hitId).state==
                    NpcPvmDelayedHitService.State.STALE_TARGET&&
                f.lifecycle.get(
                    f.npc.id
                ).hitpoints==22,
                "stale target lifecycle mutated"
            );
        }finally{
            f.close();
        }
    }

    private static void alreadyDeadDoesNotDuplicateDeath()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-dead",
                10
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    5,
                    2,
                    "TEST_DAMAGE",
                    "DEAD_BEFORE_DUE"
                );

            long lethalTick=
                f.world.clock().advance();

            NpcLifecycleService.DamageResult lethal=
                f.lifecycle.applyDamage(
                    f.npc.id,
                    99,
                    lethalTick
                );

            require(
                lethal.newlyDied&&
                f.hp()==0,
                "pre-due lethal fixture"
            );

            long due=
                f.world.clock().advance();
            f.world.events().runDue(due);

            NpcPvmDelayedHitService.Snapshot result=
                service.get(hit.hitId);

            require(
                result.state==
                    NpcPvmDelayedHitService.State.DELIVERED&&
                result.appliedDamage==0&&
                result.ignoredDead&&
                !result.newlyDied&&
                f.lifecycle.get(
                    f.npc.id
                ).deathTick==lethalTick,
                "delayed hit duplicated death"
            );
        }finally{
            f.close();
        }
    }

    private static void zeroDamageDelivered()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-zero",
                15
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    0,
                    1,
                    "TEST_DAMAGE",
                    "ZERO"
                );

            long tick=
                f.world.clock().advance();
            f.world.events().runDue(tick);

            NpcPvmDelayedHitService.Snapshot result=
                service.get(hit.hitId);

            require(
                result.state==
                    NpcPvmDelayedHitService.State.DELIVERED&&
                result.appliedDamage==0&&
                result.hitpointsBefore==15&&
                result.hitpointsAfter==15&&
                !result.ignoredDead&&
                f.hp()==15,
                "zero damage delivery"
            );
        }finally{
            f.close();
        }
    }

    private static void scheduleOwnershipAndLockOrder()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-lock-order",
                20
            );
        ExecutorService workers=
            Executors.newFixedThreadPool(3);
        CountDownLatch npcLockHeld=
            new CountDownLatch(1);
        CountDownLatch releaseNpcLock=
            new CountDownLatch(1);
        AtomicReference<Thread> scheduleThread=
            new AtomicReference<>();

        try{
            Future<Boolean> blocker=
                workers.submit(
                    ()->f.world.npcs()
                        .withCurrentMutationOwnershipIfCurrent(
                            f.npc,
                            ()->{
                                npcLockHeld.countDown();

                                if(!releaseNpcLock.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "NPC lock release timeout"
                                    );
                            }
                        )
                );

            require(
                npcLockHeld.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "NPC ownership blocker start"
            );

            NpcPvmDelayedHitService service=
                f.service();

            Future<NpcPvmDelayedHitService.Snapshot>
                schedule=
                    workers.submit(
                        ()->{
                            scheduleThread.set(
                                Thread.currentThread()
                            );

                            return service.schedule(
                                f.player,
                                f.generation,
                                f.npc,
                                3,
                                1,
                                "TEST_DAMAGE",
                                "LOCK_ORDER"
                            );
                        }
                    );

            awaitBlocked(
                scheduleThread,
                "schedule did not block on NPC ownership"
            );

            Future<Boolean> unregister=
                workers.submit(
                    ()->f.world.unregisterPlayer(
                        f.player,
                        f.generation
                    )
                );

            requireBlocked(
                unregister,
                "unregister crossed player -> NPC schedule ownership"
            );

            releaseNpcLock.countDown();

            require(
                blocker.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "NPC blocker lost ownership"
            );

            NpcPvmDelayedHitService.Snapshot hit=
                schedule.get(
                    5L,
                    TimeUnit.SECONDS
                );

            require(
                hit.state==
                    NpcPvmDelayedHitService.State.SCHEDULED,
                "owned schedule result"
            );

            require(
                unregister.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "unregister after schedule"
            );

            long tick=
                f.world.clock().advance();
            f.world.events().runDue(tick);

            require(
                service.get(hit.hitId).state==
                    NpcPvmDelayedHitService.State.STALE_ATTACKER&&
                f.hp()==20,
                "post-schedule stale attacker"
            );
        }finally{
            releaseNpcLock.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void scheduleTickQueueAtomic()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-schedule-atomic",
                30
            );
        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch npcOwned=
            new CountDownLatch(1);
        CountDownLatch releaseNpc=
            new CountDownLatch(1);
        AtomicReference<Thread> scheduleThread=
            new AtomicReference<>();

        try{
            NpcPvmDelayedHitService service=
                f.service();

            Future<Boolean> blocker=
                workers.submit(
                    ()->f.world.npcs()
                        .withCurrentMutationOwnershipIfCurrent(
                            f.npc,
                            ()->{
                                npcOwned.countDown();

                                if(!releaseNpc.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "schedule atomic NPC release timeout"
                                    );
                            }
                        )
                );

            require(
                npcOwned.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "schedule atomic NPC owner did not enter"
            );

            Future<NpcPvmDelayedHitService.Snapshot>
                scheduled=
                    workers.submit(
                        ()->{
                            scheduleThread.set(
                                Thread.currentThread()
                            );

                            return service.schedule(
                                f.player,
                                f.generation,
                                f.npc,
                                6,
                                2,
                                "TEST_DAMAGE",
                                "ATOMIC_SCHEDULE"
                            );
                        }
                    );

            awaitBlocked(
                scheduleThread,
                "delayed schedule did not block behind NPC ownership"
            );

            require(
                f.world.clock().advance()==1L&&
                f.world.clock().advance()==2L&&
                f.world.clock().advance()==3L,
                "clock did not advance while schedule was pre-publication blocked"
            );

            releaseNpc.countDown();

            require(
                blocker.get(
                    5L,
                    TimeUnit.SECONDS
                ),
                "schedule atomic NPC blocker ownership"
            );

            NpcPvmDelayedHitService.Snapshot
                hit=
                    scheduled.get(
                        5L,
                        TimeUnit.SECONDS
                    );

            require(
                hit.scheduledFromTick==3L&&
                hit.dueTick==5L&&
                f.hp()==30,
                "schedule did not bind publication tick"
            );

            long tick4=
                f.world.clock().advance();

            require(
                tick4==4L&&
                f.world.events().runDue(
                    tick4
                )==0&&
                f.hp()==30,
                "schedule atomic hit delivered early"
            );

            long tick5=
                f.world.clock().advance();

            require(
                tick5==5L&&
                f.world.events().runDue(
                    tick5
                )==1&&
                f.hp()==24,
                "schedule atomic hit did not preserve positive delay"
            );
        }finally{
            releaseNpc.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            f.close();
        }
    }

    private static void clockAdvanceBlockedDuringPublication()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch publicationEntered=
            new CountDownLatch(1);
        CountDownLatch releasePublication=
            new CountDownLatch(1);
        AtomicReference<Thread> advanceThread=
            new AtomicReference<>();
        AtomicBoolean eventInserted=
            new AtomicBoolean();

        try{
            Future<?> publication=
                workers.submit(
                    ()->{
                        world.withClockEventPublicationOwnership(
                            authoritativeTick->{
                                require(
                                    authoritativeTick==0L,
                                    "unexpected publication tick"
                                );

                                publicationEntered.countDown();

                                if(!releasePublication.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "clock publication release timeout"
                                    );

                                world.events().schedule(
                                    Math.addExact(
                                        authoritativeTick,
                                        1L
                                    ),
                                    ()->{}
                                );
                                eventInserted.set(
                                    true
                                );
                            }
                        );

                        return null;
                    }
                );

            require(
                publicationEntered.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "clock publication did not enter"
            );

            Future<Long> advance=
                workers.submit(
                    ()->{
                        advanceThread.set(
                            Thread.currentThread()
                        );
                        return world.clock()
                            .advance();
                    }
                );

            awaitBlocked(
                advanceThread,
                "clock advance crossed publication ownership"
            );

            require(
                !publication.isDone()&&
                !advance.isDone()&&
                !eventInserted.get(),
                "publication/advance escaped before release"
            );

            releasePublication.countDown();

            publication.get(
                5L,
                TimeUnit.SECONDS
            );

            require(
                advance.get(
                    5L,
                    TimeUnit.SECONDS
                )==1L&&
                world.events().size()==1&&
                world.events().runDue(
                    1L
                )==1,
                "clock/queue publication did not linearize"
            );
        }finally{
            releasePublication.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
            world.close();
        }
    }


    private static void overflowAtomic()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-overflow",
                20
            );

        try{
            Field tickField=
                GameClock.class
                    .getDeclaredField(
                        "tick"
                    );
            tickField.setAccessible(true);
            tickField.setLong(
                f.world.clock(),
                Long.MAX_VALUE
            );

            NpcPvmDelayedHitService service=
                f.service();

            expect(
                IllegalStateException.class,
                ()->service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    3,
                    1,
                    "TEST_DAMAGE",
                    "OVERFLOW"
                ),
                "due tick overflow"
            );

            require(
                service.size()==0&&
                f.world.events().size()==0&&
                f.hp()==20,
                "overflow published state"
            );
        }finally{
            f.close();
        }
    }

    private static void terminalRetirement()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-retire",
                12
            );

        try{
            NpcPvmDelayedHitService service=
                f.service();

            NpcPvmDelayedHitService.Snapshot hit=
                service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    2,
                    1,
                    "TEST_DAMAGE",
                    "RETIRE"
                );

            require(
                !service.retireTerminal(
                    hit.hitId
                ),
                "scheduled hit retired"
            );

            long tick=
                f.world.clock().advance();
            f.world.events().runDue(tick);

            require(
                service.get(hit.hitId).terminal()&&
                service.retireTerminal(
                    hit.hitId
                )&&
                service.get(hit.hitId)==null&&
                service.size()==0&&
                !service.retireTerminal(
                    hit.hitId
                ),
                "terminal retirement"
            );
        }finally{
            f.close();
        }
    }

    private static void authorityAndBoundary()
        throws Exception{
        Fixture f=
            new Fixture(
                "delayed-boundary",
                10
            );

        try{
            expect(
                IllegalArgumentException.class,
                ()->new NpcPvmDelayedHitService(
                    f.world,
                    f.lifecycle,
                    "EXACT_CURRENT_CLIENT",
                    "INVALID"
                ),
                "client delivery authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcPvmDelayedHitService(
                    f.world,
                    f.lifecycle,
                    "UNKNOWN_SERVER_AUTHORITY",
                    "INVALID"
                ),
                "unknown delivery authority"
            );

            expect(
                IllegalArgumentException.class,
                ()->new NpcPvmDelayedHitService(
                    f.world,
                    f.lifecycle,
                    "CUSTOM_LOCALLAB_DELAYED_HIT",
                    "ALLOW_STALE_ATTACKER"
                ),
                "unsupported delivery policy"
            );

            NpcPvmDelayedHitService service=
                f.service();

            expect(
                IllegalArgumentException.class,
                ()->service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    1,
                    0,
                    "TEST_DAMAGE",
                    "ZERO_DELAY"
                ),
                "zero delay"
            );

            expect(
                IllegalArgumentException.class,
                ()->service.schedule(
                    f.player,
                    f.generation,
                    f.npc,
                    1,
                    -1,
                    "TEST_DAMAGE",
                    "NEGATIVE_DELAY"
                ),
                "negative delay"
            );

            for(Field field:
                    NpcPvmDelayedHitService.class
                        .getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(Locale.ROOT);

                for(String forbidden:
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "sceneindex",
                            "projectile",
                            "gfx",
                            "animation",
                            "reward",
                            "drop",
                            "xp"
                        })
                    require(
                        !name.contains(forbidden),
                        "presentation/reward identity leaked "+
                        field.getName()
                    );

                require(
                    !PriorityQueue.class
                        .isAssignableFrom(
                            field.getType()
                        ),
                    "service owns a second priority scheduler"
                );
            }

            require(
                "CUSTOM_LOCALLAB_DELAYED_HIT".equals(
                    service.deliveryAuthority()
                )&&
                "REQUIRE_CURRENT_ATTACKER_GENERATION".equals(
                    service.deliveryPolicy()
                ),
                "delivery provenance"
            );
        }finally{
            f.close();
        }
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
            // Expected while schedule owns the player mutation lock.
        }
    }

    private static final class Fixture {
        final World world=
            World.isolatedForTest(600L);
        final WorldPlayer player=
            new WorldPlayer();
        final long generation;
        final NpcLifecycleService lifecycle;
        final WorldNpc npc;

        Fixture(
            String username,
            int hitpoints
        ){
            generation=
                world.registerPlayer(
                    player,
                    username
                );

            lifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            npc=
                world.npcs().spawn(
                    1488,
                    3200,
                    3200,
                    0
                );

            lifecycle.register(
                npc,
                hitpoints,
                "CUSTOM_LOCALLAB"
            );
        }

        NpcPvmDelayedHitService service(){
            return new NpcPvmDelayedHitService(
                world,
                lifecycle,
                "CUSTOM_LOCALLAB_DELAYED_HIT",
                "REQUIRE_CURRENT_ATTACKER_GENERATION"
            );
        }

        int hp(){
            return lifecycle.get(
                npc.id
            ).hitpoints;
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
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
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

    private NpcPvmDelayedHitServiceTest(){}
}

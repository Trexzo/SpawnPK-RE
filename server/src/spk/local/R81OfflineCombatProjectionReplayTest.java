package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R81 OFFLINE TEST ONLY: actual R79 verified lethal PvP producer ->
 * real DomainEventBus (two independent subscriptions to the SAME group) ->
 * R80 owner-gated objective, match and Collection Log projections.
 *
 * A complete or partially completed fanout is claimed BEFORE its first
 * observer. The same immutable fact instance can never run it twice.
 * No rollback, retry, durable receipt or live session is implied.
 */
public final class R81OfflineCombatProjectionReplayTest {
    private enum Mode {
        NORMAL,
        CUT_AFTER_OBJECTIVE,
        CUT_AFTER_MATCH,
        COLLECTION_RESOLVER_FAILURE
    }

    private static final class GlobalNotice
        implements DomainEventBus.Event {}

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer victim=new WorldPlayer();
        final long attackerGeneration;
        final long victimGeneration;
        final R74OfflineCommandIngressFence terminal;
        final R79OfflinePairedPvpOwnershipGate combat;
        final R80OfflineCombatProjectionOwnershipFence ownerFence;
        final R81OfflineCombatProjectionReceiptLedger receipts;
        final ObjectiveProgressService objectives=
            new ObjectiveProgressService();
        final MatchSessionService matches=
            new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r81");
        final MatchTeamId attackerTeam=MatchTeamId.of("team:r81-a");
        final MatchTeamId victimTeam=MatchTeamId.of("team:r81-b");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId(
                "collection:r81-synthetic"
            );
        final CollectionLogService collections;
        final CombatOutcomeObserverGroup group;
        final AtomicReference<CombatOutcome> acceptedKill=
            new AtomicReference<>();
        final AtomicInteger domainDeliveries=new AtomicInteger();
        final AtomicInteger globalDeliveries=new AtomicInteger();
        final AtomicInteger countResolverCalls=new AtomicInteger();
        final AtomicInteger isolatedFailures=new AtomicInteger();
        final Mode mode;
        boolean interruptedOnce;

        Fixture(Mode mode,int capacity)throws Exception{
            this.mode=mode;
            attackerGeneration=
                world.registerPlayer(attacker,"attacker");
            victimGeneration=
                world.registerPlayer(victim,"victim");
            victim.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,10
            );
            terminal=new R74OfflineCommandIngressFence(world);
            terminal.installForTest();
            combat=new R79OfflinePairedPvpOwnershipGate(
                world,attacker,attackerGeneration,
                victim,victimGeneration,terminal,
                CombatDamageRules.localLabFallback()
            );
            ownerFence=new R80OfflineCombatProjectionOwnershipFence(
                world,attacker,attackerGeneration,
                victim,victimGeneration,terminal
            );
            receipts=new R81OfflineCombatProjectionReceiptLedger(
                capacity
            );

            objectives.define(new ObjectiveDefinition(
                "combat:r81-kills",10L,"CUSTOM_LOCALLAB"
            ));

            matches.create(
                matchId,new MatchRules(
                    MatchRules.TeamMode.TEAMS,
                    MatchRules.SpellPolicy.UNRESTRICTED,
                    MatchRules.PrayerPolicy.UNRESTRICTED,
                    MatchRules.RestrictionPolicy.ALLOWED,
                    MatchRules.RestrictionPolicy.ALLOWED,
                    MatchRules.WinConditionKind.CALLER_RESOLVED,
                    MatchRules.NO_SCORE_TARGET,
                    "r81_offline_receipt_fixture","CUSTOM_LOCALLAB"
                )
            );
            matches.addTeam(matchId,attackerTeam);
            matches.addTeam(matchId,victimTeam);
            matches.join(
                matchId,attackerTeam,attacker.id().toString()
            );
            matches.join(
                matchId,victimTeam,victim.id().toString()
            );
            matches.attachInstance(
                matchId,WorldInstanceId.of("instance:r81")
            );
            matches.markReady(matchId);
            matches.activate(matchId);

            collections=new CollectionLogService(
                Collections.singletonList(
                    new CollectionLogDefinition(
                        collectionId,
                        new CollectionLogDefinition.CategoryId(
                            "category:r81-test"
                        ),
                        "R81 synthetic PvP projection",
                        Collections.singleton(
                            new CollectionLogDefinition.EntryId(
                                "entry:r81-test"
                            )
                        ),
                        true,
                        CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                    )
                )
            );

            CombatOutcomeObjectiveObserver objective=
                new CombatOutcomeObjectiveObserver(
                    objectives,attacker.id().toString(),
                    Collections.singletonList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver
                                .SubjectRole.ATTACKER,
                            "combat:r81-kills",1L,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            objective.installOfflineMutationAdmissionForTest(
                ownerFence::requireAdmitted
            );

            CombatOutcomeMatchObserver match=
                new CombatOutcomeMatchObserver(
                    matches,attacker.id().toString(),Arrays.asList(
                        new CombatOutcomeMatchObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeMatchObserver
                                .SubjectRole.ATTACKER,
                            matchId,
                            CombatOutcomeMatchObserver
                                .ScoreScope.PARTICIPANT,
                            "kills",2L,"CUSTOM_LOCALLAB"
                        ),
                        new CombatOutcomeMatchObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeMatchObserver
                                .SubjectRole.ATTACKER,
                            matchId,
                            CombatOutcomeMatchObserver
                                .ScoreScope.SUBJECT_TEAM,
                            "team_kills",3L,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            match.installOfflineMutationAdmissionForTest(
                ownerFence::requireAdmitted
            );

            CombatOutcomeCollectionLogObserver collection=
                new CombatOutcomeCollectionLogObserver(
                    collections,attacker.id().toString(),
                    (subject,binding,fact,current)->{
                        int calls=countResolverCalls.incrementAndGet();
                        if(mode==Mode.COLLECTION_RESOLVER_FAILURE&&
                           calls==1)
                            throw new IllegalStateException(
                                "R81_INJECTED_AFTER_OTHER_PROJECTIONS"
                            );
                        return 7L; // caller-owned synthetic absolute count
                    },
                    Collections.singletonList(
                        new CombatOutcomeCollectionLogObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeCollectionLogObserver
                                .SubjectRole.ATTACKER,
                            collectionId,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            collection.installOfflineMutationAdmissionForTest(
                ownerFence::requireAdmitted
            );

            group=new CombatOutcomeObserverGroup(
                Arrays.asList(objective,match,collection),
                (index,observer,fact,failure)->
                    isolatedFailures.incrementAndGet()
            );
            group.installOfflineProjectionAdmissionForTest(
                (fact,index)->{
                    if(!ownerFence.permitsFanout(fact,index))
                        return false;

                    // Simulates an interrupted downstream projection
                    // after one or two successful durable-in-memory
                    // mutations. A later duplicate call is allowed
                    // past R80 but MUST be stopped by the receipt.
                    boolean stopObjective=
                        mode==Mode.CUT_AFTER_OBJECTIVE&&index==1;
                    boolean stopMatch=
                        mode==Mode.CUT_AFTER_MATCH&&index==2;
                    if(!interruptedOnce&&
                       fact.type()==CombatOutcomeType.PLAYER_KILL&&
                       (stopObjective||stopMatch)){
                        interruptedOnce=true;
                        return false;
                    }
                    return true;
                }
            );
            group.installOfflineFirstDeliveryAdmissionForTest(
                receipts
            );

            // Two independent REAL domain-event subscriptions point
            // to the same projection group. Without the R81 receipt,
            // the second subscriber double-counts every kill.
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.HIGH,
                fact->deliver(fact)
            );
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->deliver(fact)
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                event->globalDeliveries.incrementAndGet()
            );
        }

        void deliver(CombatOutcome fact){
            domainDeliveries.incrementAndGet();
            if(fact.type()==CombatOutcomeType.PLAYER_KILL)
                acceptedKill.compareAndSet(null,fact);
            group.onCombatOutcome(fact);
        }

        void lethal()throws Exception{
            AtomicReference<PlayerCombatResolutionService.Result> result=
                new AtomicReference<>();
            world.events().schedule(1L,()->{
                try{
                    result.set(combat.resolveForTest(
                        4151,null,1L
                    ));
                    world.domainEvents().publish(new GlobalNotice());
                }catch(Exception failure){
                    throw new IllegalStateException(
                        "R81 source-authenticated PvP failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R81 first actual WorldPulse mismatch");
            require(result.get()!=null&&result.get().lifecycle.died,
                "R81 actual lethal PvP resolver did not complete");
        }

        void directSameFactReplay()throws Exception{
            final CombatOutcome fact=acceptedKill.get();
            require(fact!=null,"R81 source kill fact not captured");
            world.events().schedule(2L,()->{
                group.onCombatOutcome(fact);
                group.onCombatOutcome(fact);
            });
            require(world.observePulse(System.currentTimeMillis())==2L,
                "R81 second WorldPulse mismatch");
        }

        void forgedSameStringsRejectedByR79()throws Exception{
            final CombatOutcome fact=acceptedKill.get();
            require(fact!=null,"R81 no real source identity");
            final AtomicInteger denied=new AtomicInteger();
            world.events().schedule(3L,()->{
                CombatOutcome copy=new CombatOutcome(
                    fact.attacker(),fact.victim(),fact.type(),
                    fact.context(),fact.worldTick(),
                    fact.sourceAuthority()
                );
                try{
                    world.domainEvents().publish(copy);
                    throw new AssertionError(
                        "R81 raw clone bypassed R79 source proof"
                    );
                }catch(IOException refusal){
                    require(refusal.getMessage().contains(
                        "R79_PVP_DENIED unverified combat fact"
                    ),"R81 forged clone refused for wrong reason");
                    denied.incrementAndGet();
                }catch(Exception failure){
                    throw new IllegalStateException(failure);
                }
            });
            require(world.observePulse(System.currentTimeMillis())==3L&&
                    denied.get()==1,
                "R81 immutable clone source bypass was not refused");
        }

        void assertProjections(
            long objective,long participant,long team,Long collection,
            int resolverCalls,int failureCount
        ){
            require(objectives.get(
                    "combat:r81-kills").progress==objective,
                "R81 objective duplicated");
            MatchSession session=matches.get(matchId);
            MatchSession.Participant p=session.participant(
                attacker.id().toString()
            );
            MatchSession.Team t=session.team(attackerTeam);
            require(p!=null&&t!=null&&
                    p.scores.getOrDefault("kills",0L)==participant&&
                    t.scores.getOrDefault("team_kills",0L)==team,
                "R81 match counters duplicated");
            CollectionLogService.ProgressSnapshot snap=
                collections.snapshot(collectionId);
            Long observed=snap.killCount().isPresent()
                ?Long.valueOf(snap.killCount().getAsLong())
                :null;
            require(java.util.Objects.equals(observed,collection),
                "R81 Collection Log absolute projection unexpected");
            require(countResolverCalls.get()==resolverCalls&&
                    isolatedFailures.get()==failureCount,
                "R81 resolver or isolated failure was replayed");
            require(!objectives.get("combat:r81-kills").claimed&&
                    session.state==MatchSession.State.ACTIVE,
                "R81 replay granted reward or completed match");
        }

        @Override public void close(){world.close();}
    }

    public static void main(String[] args)throws Exception{
        fullRealSourceDuplicateDelivery();
        partialAfterObjectiveReplayIsRefused();
        partialAfterMatchReplayIsRefused();
        failedCollectionResolverIsNotRetried();
        boundedLedgerNeverEvictsOldReceipt();
        synchronizedConcurrentClaimWinsOnce();

        System.out.println(
            "R81_OFFLINE_COMBAT_RECEIPT_REPLAY_PASS "+
            "realPvPDomainTwoSubscribers=true "+
            "duplicateSameInstanceDeniedBeforeWrite=true "+
            "sourceFactKillAndDeathDistinct=true "+
            "partialObjectiveNotReplayed=true "+
            "partialMatchNotReplayed=true "+
            "failedCollectionResolverNotReplayed=true "+
            "priorCommittedWritesNotRolledBack=true "+
            "forgedSameFieldsRejectedByR79=true "+
            "capacityExhaustionFailsClosedWithoutEviction=true "+
            "concurrentFirstDeliveryAtomic=true "+
            "globalDomainNoticeUnaffected=true "+
            "noDurableExactlyOnceOrLiveActivation=true"
        );
    }

    private static void fullRealSourceDuplicateDelivery()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL,16)){
            f.lethal();
            f.assertProjections(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.domainDeliveries.get()==4&&
                    f.receipts.receiptCount()==2&&
                    f.receipts.firstDeliveryCount()==2L&&
                    f.receipts.duplicateRefusalCount()==2L&&
                    f.receipts.capacityRefusalCount()==0L&&
                    f.globalDeliveries.get()==1,
                "R81 double domain subscriber did not deduplicate");

            f.directSameFactReplay();
            f.forgedSameStringsRejectedByR79();
            f.assertProjections(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.duplicateRefusalCount()==4L&&
                    f.globalDeliveries.get()==1,
                "R81 repeated direct same-instance delivery changed state");
        }
    }

    private static void partialAfterObjectiveReplayIsRefused()
            throws Exception{
        try(Fixture f=new Fixture(Mode.CUT_AFTER_OBJECTIVE,16)){
            f.lethal();
            f.assertProjections(1L,0L,0L,null,0,0);
            require(f.interruptedOnce&&
                    f.receipts.duplicateRefusalCount()==2L,
                "R81 partial objective replay was allowed");
            f.directSameFactReplay();
            f.assertProjections(1L,0L,0L,null,0,0);
            require(f.receipts.duplicateRefusalCount()==4L,
                "R81 late objective replay crossed claim");
        }
    }

    private static void partialAfterMatchReplayIsRefused()
            throws Exception{
        try(Fixture f=new Fixture(Mode.CUT_AFTER_MATCH,16)){
            f.lethal();
            f.assertProjections(1L,2L,3L,null,0,0);
            require(f.interruptedOnce&&
                    f.receipts.duplicateRefusalCount()==2L,
                "R81 match partial replay was allowed");
            f.directSameFactReplay();
            f.assertProjections(1L,2L,3L,null,0,0);
            require(f.receipts.duplicateRefusalCount()==4L,
                "R81 late match replay crossed claim");
        }
    }

    private static void failedCollectionResolverIsNotRetried()
            throws Exception{
        try(Fixture f=new Fixture(
                Mode.COLLECTION_RESOLVER_FAILURE,16)){
            f.lethal();
            f.assertProjections(1L,2L,3L,null,1,1);
            require(f.receipts.duplicateRefusalCount()==2L,
                "R81 collection resolver failure reopened receipt");
            f.directSameFactReplay();
            f.assertProjections(1L,2L,3L,null,1,1);
            require(f.receipts.duplicateRefusalCount()==4L,
                "R81 retry re-ran failing Collection Log resolver");
        }
    }

    private static void boundedLedgerNeverEvictsOldReceipt()
            throws Exception{
        // Real source emits one kill + one death fact. Capacity 1
        // admits kill, refuses death, and never evicts kill to accept
        // a later replay. No false claim of all-event delivery.
        try(Fixture f=new Fixture(Mode.NORMAL,1)){
            f.lethal();
            f.assertProjections(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.receiptCount()==1&&
                    f.receipts.firstDeliveryCount()==1L&&
                    f.receipts.duplicateRefusalCount()==1L&&
                    f.receipts.capacityRefusalCount()==2L,
                "R81 full receipt ledger did not fail closed");
            f.directSameFactReplay();
            f.assertProjections(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.receiptCount()==1&&
                    f.receipts.duplicateRefusalCount()==3L,
                "R81 bounded receipt eviction reopened old event");
        }
    }

    private static void synchronizedConcurrentClaimWinsOnce()
            throws Exception{
        R81OfflineCombatProjectionReceiptLedger ledger=
            new R81OfflineCombatProjectionReceiptLedger(2);
        CombatOutcome one=new CombatOutcome(
            "player:a","player:b",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"CUSTOM_LOCALLAB"
        );

        final int workers=8;
        final CountDownLatch ready=new CountDownLatch(workers);
        final CountDownLatch go=new CountDownLatch(1);
        final AtomicInteger admitted=new AtomicInteger();
        final java.util.List<Thread> threads=
            new java.util.ArrayList<>();
        for(int i=0;i<workers;i++){
            Thread t=new Thread(()->{
                ready.countDown();
                try{
                    if(!go.await(10L,TimeUnit.SECONDS))
                        throw new AssertionError("R81 worker start expired");
                    if(ledger.tryBegin(one))admitted.incrementAndGet();
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new AssertionError(
                        "R81 concurrent claim interrupted",interrupted
                    );
                }
            },"r81-receipt-racer-"+i);
            t.start();
            threads.add(t);
        }
        try{
            require(ready.await(10L,TimeUnit.SECONDS),
                "R81 concurrent competitors did not start");
        }finally{
            go.countDown();
        }
        for(Thread t:threads)t.join(10_000L);
        for(Thread t:threads)require(!t.isAlive(),
            "R81 concurrent worker did not exit");
        require(admitted.get()==1&&
                ledger.receiptCount()==1&&
                ledger.duplicateRefusalCount()==workers-1,
            "R81 concurrent identical instance double claimed");

        // A freshly reconstructed event is a *different object*.
        // This ledger cannot honestly claim logical cross-instance
        // idempotency without a trusted producer-issued event ID.
        CombatOutcome clone=new CombatOutcome(
            one.attacker(),one.victim(),one.type(),
            one.context(),one.worldTick(),one.sourceAuthority()
        );
        require(ledger.tryBegin(clone)&&
                ledger.receiptCount()==2,
            "R81 wrongly deduplicated equal-field independent objects");
        require(!ledger.tryBegin(one)&&
                ledger.duplicateRefusalCount()==workers,
            "R81 prior original receipt was evicted");
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private R81OfflineCombatProjectionReplayTest(){}
}

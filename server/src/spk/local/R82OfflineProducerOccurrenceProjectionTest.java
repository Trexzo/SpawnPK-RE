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
 * R82 OFFLINE ONLY. Real PvP resolver -> R79 source proof and R82
 * producer ordinal -> real DomainEventBus -> R80 generation gate ->
 * REAL objective, match and Collection Log observers.
 *
 * A trusted test reissue constructs a NEW fact object with the source's
 * recorded occurrence key. It never applies HP twice and it is not
 * synthetic production traffic or a durable cross-JVM event ID.
 */
public final class R82OfflineProducerOccurrenceProjectionTest {
    private enum Mode{
        NORMAL,PAUSE_BEFORE_MATCH,
        FAIL_COLLECTION,FAIL_SECOND_OBJECTIVE_BINDING
    }

    private static final class GlobalNotice
        implements DomainEventBus.Event {}

    private static final class Fixture implements AutoCloseable{
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer victim=new WorldPlayer();
        final long attackerGeneration;
        final long victimGeneration;
        final R74OfflineCommandIngressFence terminal;
        final R79OfflinePairedPvpOwnershipGate combat;
        final R82OfflineCombatOccurrenceKeys source;
        final R80OfflineCombatProjectionOwnershipFence ownerFence;
        final R82OfflinePerProjectionReceiptLedger receipts;
        final ObjectiveProgressService objectives=
            new ObjectiveProgressService();
        final MatchSessionService matches=
            new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r82");
        final MatchTeamId teamA=MatchTeamId.of("team:r82-a");
        final MatchTeamId teamB=MatchTeamId.of("team:r82-b");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId("collection:r82");
        final CollectionLogService collections;
        final CombatOutcomeObserverGroup group;
        final AtomicReference<CombatOutcome> firstKill=
            new AtomicReference<>();
        final AtomicReference<CombatOutcome> secondKill=
            new AtomicReference<>();
        final AtomicInteger killSeen=new AtomicInteger();
        final AtomicInteger domainDeliveries=new AtomicInteger();
        final AtomicInteger globalDeliveries=new AtomicInteger();
        final AtomicInteger resolverCalls=new AtomicInteger();
        final AtomicInteger observerFailures=new AtomicInteger();
        final AtomicInteger objectiveAdmissionCalls=new AtomicInteger();
        final Mode mode;
        boolean paused;

        Fixture(Mode mode,int maxReceipts,boolean duplicateSubscriber)
                throws Exception{
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
            source=new R82OfflineCombatOccurrenceKeys(
                attacker,attackerGeneration,
                victim,victimGeneration
            );
            combat.installOfflineR82OccurrenceKeysForTest(source);
            ownerFence=new R80OfflineCombatProjectionOwnershipFence(
                world,attacker,attackerGeneration,
                victim,victimGeneration,terminal
            );
            receipts=new R82OfflinePerProjectionReceiptLedger(
                maxReceipts,combat::currentR82SourceKeyForTest
            );

            objectives.define(new ObjectiveDefinition(
                "combat:r82-primary",10L,"CUSTOM_LOCALLAB"
            ));
            objectives.define(new ObjectiveDefinition(
                "combat:r82-secondary",10L,"CUSTOM_LOCALLAB"
            ));
            CombatOutcomeObjectiveObserver objective=
                new CombatOutcomeObjectiveObserver(
                    objectives,attacker.id().toString(),Arrays.asList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                            "combat:r82-primary",1L,"CUSTOM_LOCALLAB"
                        ),
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                            "combat:r82-secondary",1L,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            objective.installOfflineMutationAdmissionForTest(
                fact->{
                    if(mode==Mode.FAIL_SECOND_OBJECTIVE_BINDING&&
                       objectiveAdmissionCalls.incrementAndGet()==2)
                        throw new IllegalStateException(
                            "R82_INJECTED_SECOND_BINDING_FAILURE"
                        );
                    ownerFence.requireAdmitted(fact);
                }
            );

            matches.create(matchId,new MatchRules(
                MatchRules.TeamMode.TEAMS,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "r82_offline_projection","CUSTOM_LOCALLAB"
            ));
            matches.addTeam(matchId,teamA);
            matches.addTeam(matchId,teamB);
            matches.join(matchId,teamA,attacker.id().toString());
            matches.join(matchId,teamB,victim.id().toString());
            matches.attachInstance(
                matchId,WorldInstanceId.of("instance:r82")
            );
            matches.markReady(matchId);
            matches.activate(matchId);

            CombatOutcomeMatchObserver match=
                new CombatOutcomeMatchObserver(
                    matches,attacker.id().toString(),Arrays.asList(
                        new CombatOutcomeMatchObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeMatchObserver.SubjectRole.ATTACKER,
                            matchId,
                            CombatOutcomeMatchObserver.ScoreScope.PARTICIPANT,
                            "kills",2L,"CUSTOM_LOCALLAB"
                        ),
                        new CombatOutcomeMatchObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeMatchObserver.SubjectRole.ATTACKER,
                            matchId,
                            CombatOutcomeMatchObserver.ScoreScope.SUBJECT_TEAM,
                            "team_kills",3L,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            match.installOfflineMutationAdmissionForTest(
                ownerFence::requireAdmitted
            );

            collections=new CollectionLogService(
                Collections.singletonList(
                    new CollectionLogDefinition(
                        collectionId,
                        new CollectionLogDefinition.CategoryId(
                            "category:r82"
                        ),
                        "R82 synthetic combat count",
                        Collections.singleton(
                            new CollectionLogDefinition.EntryId(
                                "entry:r82"
                            )
                        ),
                        true,
                        CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                    )
                )
            );
            CombatOutcomeCollectionLogObserver collection=
                new CombatOutcomeCollectionLogObserver(
                    collections,attacker.id().toString(),
                    (subject,binding,fact,current)->{
                        int attempt=resolverCalls.incrementAndGet();
                        if(mode==Mode.FAIL_COLLECTION&&attempt==1)
                            throw new IllegalStateException(
                                "R82_INJECTED_COUNT_RESOLVER_FAILURE"
                            );
                        return 6L+attempt; // synthetic authoritative count
                    },
                    Collections.singletonList(
                        new CombatOutcomeCollectionLogObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeCollectionLogObserver.SubjectRole.ATTACKER,
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
                    observerFailures.incrementAndGet()
            );
            group.installOfflineProjectionAdmissionForTest(
                (fact,index)->{
                    if(!ownerFence.permitsFanout(fact,index))
                        return false;
                    if(mode==Mode.PAUSE_BEFORE_MATCH&&
                       !paused&&index==1&&
                       fact.type()==CombatOutcomeType.PLAYER_KILL){
                        paused=true;
                        return false;
                    }
                    return true;
                }
            );
            group.installOfflinePerObserverReceiptForTest(receipts);

            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.HIGH,
                fact->deliver(fact)
            );
            if(duplicateSubscriber){
                world.domainEvents().subscribe(
                    CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                    fact->deliver(fact)
                );
            }
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                fact->globalDeliveries.incrementAndGet()
            );
        }

        void deliver(CombatOutcome fact){
            domainDeliveries.incrementAndGet();
            if(fact.type()==CombatOutcomeType.PLAYER_KILL){
                if(killSeen.incrementAndGet()==1)
                    firstKill.set(fact);
                else
                    secondKill.compareAndSet(null,fact);
            }
            group.onCombatOutcome(fact);
        }

        void firstRealKill()throws Exception{
            AtomicReference<PlayerCombatResolutionService.Result> result=
                new AtomicReference<>();
            world.events().schedule(1L,()->{
                try{
                    result.set(combat.resolveForTest(4151,null,1L));
                    world.domainEvents().publish(new GlobalNotice());
                }catch(Exception failure){
                    throw new IllegalStateException(
                        "R82 real lethal producer failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R82 first World tick mismatch");
            require(result.get()!=null&&result.get().lifecycle.died,
                "R82 real PvP resolution did not kill victim");
            require(firstKill.get()!=null,
                "R82 real source fact not observed");
        }

        CombatOutcome reissueKillOnTick(long tick)throws Exception{
            AtomicReference<CombatOutcome> clone=
                new AtomicReference<>();
            world.events().schedule(tick,()->{
                clone.set(combat.reissueRecordedFactForTest(
                    firstKill.get()
                ));
            });
            require(world.observePulse(System.currentTimeMillis())==tick,
                "R82 reissue World tick mismatch");
            CombatOutcome original=firstKill.get();
            require(clone.get()!=null&&clone.get()!=original,
                "R82 reissue did not create distinct immutable object");
            require(source.recordedKey(clone.get()).equals(
                        source.recordedKey(original)),
                "R82 trusted reissue lost producer occurrence key");
            return clone.get();
        }

        void forgeDirectFactAtTick(long tick)throws Exception{
            world.events().schedule(tick,()->{
                CombatOutcome trusted=firstKill.get();
                CombatOutcome forged=new CombatOutcome(
                    trusted.attacker(),trusted.victim(),
                    trusted.type(),trusted.context(),
                    trusted.worldTick(),trusted.sourceAuthority()
                );
                group.onCombatOutcome(forged);
                try{
                    world.domainEvents().publish(forged);
                    throw new AssertionError(
                        "R82 forged direct fact bypassed R79 proof"
                    );
                }catch(IOException denied){
                    require(denied.getMessage().contains(
                        "R79_PVP_DENIED unverified combat fact"
                    ),"R82 wrong DomainEventBus fake refusal");
                }catch(Exception error){
                    throw new IllegalStateException(error);
                }
            });
            require(world.observePulse(System.currentTimeMillis())==tick,
                "R82 fake fact World tick mismatch");
        }

        void secondRealKillAtTick6()throws Exception{
            for(long tick=world.clock().tick()+1;tick<6L;tick++)
                require(world.observePulse(System.currentTimeMillis())==tick,
                    "R82 pre-respawn tick mismatch "+tick);
            AtomicReference<PlayerCombatResolutionService.Result> result=
                new AtomicReference<>();
            world.events().schedule(6L,()->{
                try{
                    require(new PlayerLifecycleService(victim)
                        .tick(6L,10)==
                        PlayerLifecycleService.TickResult.RESPAWNED,
                        "R82 actual lifecycle respawn failed");
                    result.set(combat.resolveForTest(4151,null,6L));
                }catch(Exception failure){
                    throw new IllegalStateException(
                        "R82 second real lethal source failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==6L,
                "R82 second kill World tick mismatch");
            require(result.get()!=null&&result.get().lifecycle.died,
                "R82 second source did not kill respawned victim");
        }

        long objective(String key){
            return objectives.get(key).progress;
        }
        long participantKills(){
            return matches.get(matchId)
                .participant(attacker.id().toString())
                .scores.getOrDefault("kills",0L);
        }
        long teamKills(){
            return matches.get(matchId).team(teamA)
                .scores.getOrDefault("team_kills",0L);
        }
        Long count(){
            CollectionLogService.ProgressSnapshot s=
                collections.snapshot(collectionId);
            return s.killCount().isPresent()
                ?Long.valueOf(s.killCount().getAsLong()):null;
        }
        void assertCounts(
            long primary,long secondary,long participant,long team,
            Long collection,int resolverCount,int failureCount
        ){
            require(objective("combat:r82-primary")==primary&&
                    objective("combat:r82-secondary")==secondary&&
                    participantKills()==participant&&teamKills()==team&&
                    java.util.Objects.equals(count(),collection)&&
                    resolverCalls.get()==resolverCount&&
                    observerFailures.get()==failureCount,
                "R82 projection receipt partial/double count detected "+
                "primary="+objective("combat:r82-primary")+
                " secondary="+objective("combat:r82-secondary")+
                " p="+participantKills()+" t="+teamKills()+
                " log="+count()+" resolve="+resolverCalls.get());
            require(!objectives.get("combat:r82-primary").claimed&&
                    matches.get(matchId).state==MatchSession.State.ACTIVE,
                "R82 fixture accidentally claimed reward or ended match");
        }
        @Override public void close(){world.close();}
    }

    public static void main(String[] args)throws Exception{
        trustedReissueOfDifferentJavaObjectDoesNotDoubleCount();
        pausedUnstartedProjectionsResumeWithoutReplay();
        failedProjectionRemainsConsumedOnTrustedReissue();
        failureWithinOneObserverDoesNotReplayPartialBindings();
        distinctRealOccurrencesHaveDistinctSourceKeys();
        boundedCapacityAndProofFailureAreClosed();
        duplicatePolicyInstallationRefused();
        concurrentObserverClaimIsAtomic();

        System.out.println(
            "R82_OFFLINE_SOURCE_OCCURRENCE_PROJECTION_PASS "+
            "realPvPSourceOccurrenceOrdinal=true "+
            "killAndDeathTypedKeysSeparate=true "+
            "trustedDifferentObjectReissueSameKey=true "+
            "independentObserverReceipts=true "+
            "duplicateDomainSubscriptionsNoDoubleScore=true "+
            "unfinishedObserverCanResumeWithoutReplayingEarlier=true "+
            "failedObserverNeverRetried=true "+
            "sameObserverPartialBindingNotRetried=true "+
            "twoRealDeathsDistinctOrdinals=true "+
            "untrustedCopiedStringsCannotAcquireReceipt=true "+
            "capacityFailClosedNoReceiptEviction=true "+
            "concurrentReceiptClaimSingleWinner=true "+
            "nonDurableNoLiveProtocolNoRollback=true"
        );
    }

    private static void trustedReissueOfDifferentJavaObjectDoesNotDoubleCount()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL,8,true)){
            f.firstRealKill();
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            require(f.domainDeliveries.get()==4&&
                    f.receipts.sourceCount()==2&&
                    f.receipts.claimCount()==6L&&
                    f.receipts.duplicateCount()==6L&&
                    f.globalDeliveries.get()==1,
                "R82 real duplicate domain subscribers were not fenced");

            f.reissueKillOnTick(2L);
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            require(f.receipts.duplicateCount()==12L&&
                    f.receipts.sourceCount()==2&&
                    f.receipts.claimCount()==6L,
                "R82 trusted new object replayed existing projection");
            f.forgeDirectFactAtTick(3L);
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            require(f.receipts.unverifiedCount()==3L,
                "R82 raw forged group input obtained receipt");
        }
    }

    private static void pausedUnstartedProjectionsResumeWithoutReplay()
            throws Exception{
        try(Fixture f=new Fixture(Mode.PAUSE_BEFORE_MATCH,8,false)){
            f.firstRealKill();
            f.assertCounts(1L,1L,0L,0L,null,0,0);
            require(f.paused&&f.receipts.claimCount()==4L,
                "R82 original partial projection did not pause");

            f.reissueKillOnTick(2L);
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            require(f.receipts.claimCount()==6L&&
                    f.receipts.duplicateCount()>=1L,
                "R82 trusted reissue did not resume only missing observers");
        }
    }

    private static void failedProjectionRemainsConsumedOnTrustedReissue()
            throws Exception{
        try(Fixture f=new Fixture(Mode.FAIL_COLLECTION,8,false)){
            f.firstRealKill();
            f.assertCounts(1L,1L,2L,3L,null,1,1);
            f.reissueKillOnTick(2L);
            f.assertCounts(1L,1L,2L,3L,null,1,1);
            require(f.receipts.duplicateCount()>=3L,
                "R82 failed Collection Log resolver unexpectedly retried");
        }
    }

    private static void failureWithinOneObserverDoesNotReplayPartialBindings()
            throws Exception{
        try(Fixture f=new Fixture(
                Mode.FAIL_SECOND_OBJECTIVE_BINDING,8,false)){
            f.firstRealKill();
            f.assertCounts(1L,0L,2L,3L,7L,1,1);
            f.reissueKillOnTick(2L);
            f.assertCounts(1L,0L,2L,3L,7L,1,1);
            require(f.receipts.duplicateCount()>=3L,
                "R82 partial within same observer wrongly replayed");
        }
    }

    private static void distinctRealOccurrencesHaveDistinctSourceKeys()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL,8,false)){
            f.firstRealKill();
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            f.secondRealKillAtTick6();
            f.assertCounts(2L,2L,4L,6L,8L,2,0);

            CombatOutcome second=f.secondKill.get();
            CombatOutcome first=f.firstKill.get();
            require(second!=null&&second!=first,
                "R82 second real source was not observed");
            R82OfflineCombatOccurrenceKeys.Key one=
                f.source.recordedKey(first);
            R82OfflineCombatOccurrenceKeys.Key two=
                f.source.recordedKey(second);
            require(one!=null&&two!=null&&
                    !one.equals(two)&&one.ordinal==1L&&
                    two.ordinal==2L&&
                    f.receipts.sourceCount()==4,
                "R82 distinct real PvP resolutions shared receipt");
        }
    }

    private static void boundedCapacityAndProofFailureAreClosed()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL,1,false)){
            f.firstRealKill();
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            require(f.receipts.sourceCount()==1&&
                    f.receipts.capacityRefusalCount()==3L,
                "R82 distinct death fact did not fail closed at cap");
            f.reissueKillOnTick(2L);
            f.assertCounts(1L,1L,2L,3L,7L,1,0);
            require(f.receipts.sourceCount()==1&&
                    f.receipts.duplicateCount()>=3L,
                "R82 full ledger reopened old source key");
        }
    }

    private static void duplicatePolicyInstallationRefused()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL,8,false)){
            try{
                f.combat.installOfflineR82OccurrenceKeysForTest(
                    new R82OfflineCombatOccurrenceKeys(
                        f.attacker,f.attackerGeneration,
                        f.victim,f.victimGeneration
                    )
                );
                throw new AssertionError(
                    "R82 duplicate producer scope accepted"
                );
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "R82_SOURCE_DENIED duplicate provenance installation"
                ),"R82 wrong duplicate source rejection");
            }
            try{
                f.group.installOfflinePerObserverReceiptForTest(
                    (fact,index)->true
                );
                throw new AssertionError(
                    "R82 duplicate receipt policy accepted"
                );
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "R82_RECEIPT_GATE_DENIED"
                ),"R82 wrong duplicate group rejection");
            }
        }
    }

    private static void concurrentObserverClaimIsAtomic()
            throws Exception{
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        R82OfflineCombatOccurrenceKeys.Key key=
            new R82OfflineCombatOccurrenceKeys.Key(
                java.util.UUID.randomUUID(),a,1L,b,1L,
                1L,CombatOutcomeType.PLAYER_KILL
            );
        CombatOutcome fact=new CombatOutcome(
            a.id().toString(),b.id().toString(),
            CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,
            PlayerLifecycleService.AUTHORITY
        );
        // Standalone ledger atomicity fixture: trusted key is injected
        // directly only here, not in a gameplay producer.
        R82OfflinePerProjectionReceiptLedger ledger=
            new R82OfflinePerProjectionReceiptLedger(2,f->key);
        CountDownLatch ready=new CountDownLatch(8);
        CountDownLatch start=new CountDownLatch(1);
        AtomicInteger winners=new AtomicInteger();
        java.util.List<Thread> threads=new java.util.ArrayList<>();
        for(int i=0;i<8;i++){
            Thread t=new Thread(()->{
                ready.countDown();
                try{
                    if(!start.await(10L,TimeUnit.SECONDS))
                        throw new AssertionError(
                            "R82 receipt competitors timed out"
                        );
                    if(ledger.tryBegin(fact,1))
                        winners.incrementAndGet();
                }catch(InterruptedException failure){
                    Thread.currentThread().interrupt();
                    throw new AssertionError(
                        "R82 receipt worker interrupted",failure
                    );
                }
            },"r82-receipt-race-"+i);
            t.start();
            threads.add(t);
        }
        try{
            require(ready.await(10L,TimeUnit.SECONDS),
                "R82 receipt threads did not start");
        }finally{
            start.countDown();
        }
        for(Thread t:threads)t.join(10_000L);
        for(Thread t:threads)
            require(!t.isAlive(),"R82 receipt worker leaked");
        require(winners.get()==1&&
                ledger.claimCount()==1L&&
                ledger.duplicateCount()==7L&&
                ledger.sourceCount()==1&&
                ledger.tryBegin(fact,2)&&
                ledger.claimCount()==2L,
            "R82 per-observer concurrent receipt not atomic");
    }

    private static void require(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }

    private R82OfflineProducerOccurrenceProjectionTest(){}
}

package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R82 OFFLINE ONLY: real PvP source -> R79 exact fact proof ->
 * two independently constructed CombatOutcomeObserverGroup instances ->
 * SAME real objective, match and Collection Log projection stores.
 *
 * The R82 ordinal is issued only at the real verified PvP producer's
 * callback. No trusted cross-JVM/combat event identifier exists in
 * production CombatOutcome.
 */
public final class R82OfflineSourceScopedCombatReceiptTest {
    private enum Mode {
        COMPLETE,
        PARTIAL_AFTER_OBJECTIVE,
        FAILED_COLLECTION_RESOLVER
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
        final R82OfflineSourceScopedCombatReceiptLedger receipts;
        final ObjectiveProgressService objectives=
            new ObjectiveProgressService();
        final MatchSessionService matches=
            new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r82");
        final MatchTeamId attackerTeam=MatchTeamId.of("team:r82-a");
        final MatchTeamId victimTeam=MatchTeamId.of("team:r82-b");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId(
                "collection:r82-test-only"
            );
        final CollectionLogService collection;
        final AtomicInteger firstGroupDelivery=new AtomicInteger();
        final AtomicInteger secondGroupDelivery=new AtomicInteger();
        final AtomicInteger globalDelivery=new AtomicInteger();
        final AtomicInteger resolverCalls=new AtomicInteger();
        final AtomicInteger isolatedFailures=new AtomicInteger();
        final AtomicReference<CombatOutcome> sourceKill=
            new AtomicReference<>();
        final AtomicReference<CombatOutcome> sourceDeath=
            new AtomicReference<>();
        final CombatOutcomeObserverGroup group1;
        final CombatOutcomeObserverGroup group2;
        final Mode mode;

        Fixture(Mode mode,int capacity)throws Exception{
            this.mode=mode;
            attackerGeneration=world.registerPlayer(attacker,"attacker");
            victimGeneration=world.registerPlayer(victim,"victim");
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
            receipts=new R82OfflineSourceScopedCombatReceiptLedger(
                combat,capacity
            );
            combat.installOfflineSourceEmissionIssuerForTest(receipts);

            objectives.define(
                new ObjectiveDefinition(
                    "combat:r82-kills",10L,"CUSTOM_LOCALLAB"
                )
            );
            matches.create(
                matchId,new MatchRules(
                    MatchRules.TeamMode.TEAMS,
                    MatchRules.SpellPolicy.UNRESTRICTED,
                    MatchRules.PrayerPolicy.UNRESTRICTED,
                    MatchRules.RestrictionPolicy.ALLOWED,
                    MatchRules.RestrictionPolicy.ALLOWED,
                    MatchRules.WinConditionKind.CALLER_RESOLVED,
                    MatchRules.NO_SCORE_TARGET,
                    "r82_offline_receipt","CUSTOM_LOCALLAB"
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
                matchId,WorldInstanceId.of("instance:r82")
            );
            matches.markReady(matchId);
            matches.activate(matchId);

            collection=new CollectionLogService(
                Collections.singletonList(
                    new CollectionLogDefinition(
                        collectionId,
                        new CollectionLogDefinition.CategoryId(
                            "category:r82-test"
                        ),
                        "R82 synthetic combat count",
                        Collections.singleton(
                            new CollectionLogDefinition.EntryId(
                                "entry:r82-test"
                            )
                        ),
                        true,
                        CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                    )
                )
            );

            group1=newGroup(true);
            group2=newGroup(false);

            // Deliberately different REAL DomainEventBus subscriptions
            // and separately constructed groups sharing the SAME
            // stateful projection services and source emission ledger.
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.HIGH,
                fact->{
                    firstGroupDelivery.incrementAndGet();
                    captureSourceFact(fact);
                    group1.onCombatOutcome(fact);
                }
            );
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->{
                    secondGroupDelivery.incrementAndGet();
                    captureSourceFact(fact);
                    group2.onCombatOutcome(fact);
                }
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                fact->globalDelivery.incrementAndGet()
            );
        }

        private void captureSourceFact(CombatOutcome fact){
            if(fact.type()==CombatOutcomeType.PLAYER_KILL)
                sourceKill.compareAndSet(null,fact);
            if(fact.type()==CombatOutcomeType.PLAYER_DEATH)
                sourceDeath.compareAndSet(null,fact);
        }

        private CombatOutcomeObserverGroup newGroup(
            boolean firstGroup
        ){
            CombatOutcomeObjectiveObserver objective=
                new CombatOutcomeObjectiveObserver(
                    objectives,attacker.id().toString(),
                    Collections.singletonList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver
                                .SubjectRole.ATTACKER,
                            "combat:r82-kills",1L,"CUSTOM_LOCALLAB"
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

            CombatOutcomeCollectionLogObserver log=
                new CombatOutcomeCollectionLogObserver(
                    collection,attacker.id().toString(),
                    (subject,binding,fact,current)->{
                        int n=resolverCalls.incrementAndGet();
                        if(mode==Mode.FAILED_COLLECTION_RESOLVER&&n==1)
                            throw new IllegalStateException(
                                "R82_INJECTED_RESOLVER_FAULT"
                            );
                        return 7L; // test-owned absolute count only
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
            log.installOfflineMutationAdmissionForTest(
                ownerFence::requireAdmitted
            );

            CombatOutcomeObserverGroup group=
                new CombatOutcomeObserverGroup(
                    Arrays.asList(objective,match,log),
                    (index,consumer,fact,error)->
                        isolatedFailures.incrementAndGet()
                );
            group.installOfflineProjectionAdmissionForTest(
                (fact,index)->{
                    if(!ownerFence.permitsFanout(fact,index))
                        return false;
                    // Simulate a partial first-group delivery after
                    // one objective advancement without quiescence.
                    // The second independent group MUST NOT "resume"
                    // by replaying any prior or skipped writes.
                    return !(firstGroup&&
                        mode==Mode.PARTIAL_AFTER_OBJECTIVE&&
                        fact.type()==CombatOutcomeType.PLAYER_KILL&&
                        index>0);
                }
            );
            group.installOfflineFirstDeliveryAdmissionForTest(
                receipts
            );
            return group;
        }

        void lethal()throws Exception{
            AtomicReference<PlayerCombatResolutionService.Result> result=
                new AtomicReference<>();
            world.events().schedule(1L,()->{
                try{
                    result.set(combat.resolveForTest(4151,null,1L));
                    world.domainEvents().publish(new GlobalNotice());
                }catch(Exception failure){
                    throw new IllegalStateException(
                        "R82 real PvP source unexpectedly failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R82 actual WorldPulse expected one tick");
            require(result.get()!=null&&result.get().lifecycle.died,
                "R82 genuine lethality was not confirmed");
        }

        void attemptUnprovenClones()throws Exception{
            CombatOutcome sourceFact=sourceKill.get();
            require(sourceFact!=null,"R82 missing verified source fact");
            AtomicInteger clonedBusDenied=new AtomicInteger();
            world.events().schedule(2L,()->{
                CombatOutcome forged=new CombatOutcome(
                    sourceFact.attacker(),sourceFact.victim(),
                    sourceFact.type(),sourceFact.context(),
                    sourceFact.worldTick(),sourceFact.sourceAuthority()
                );
                try{
                    world.domainEvents().publish(forged);
                    throw new AssertionError(
                        "R82 raw identical fields entered domain bus"
                    );
                }catch(IOException denied){
                    require(denied.getMessage().contains(
                        "R79_PVP_DENIED unverified combat fact"
                    ),"R82 incorrect forged domain refusal");
                    clonedBusDenied.incrementAndGet();
                }catch(Exception exception){
                    throw new IllegalStateException(exception);
                }
                // A direct same-looking object cannot ask the receipt
                // gate for a new ordinal; only R79 real producer can.
                group2.onCombatOutcome(forged);
                // An already-consumed object also cannot restart its
                // projections outside the original live source proof.
                group1.onCombatOutcome(sourceFact);
            });
            require(world.observePulse(System.currentTimeMillis())==2L&&
                    clonedBusDenied.get()==1,
                "R82 forged/replayed combat clone was not rejected");
        }

        void assertProjection(
            long progress,long score,long teamScore,Long collectionCount,
            int resolverRuns,int errors
        ){
            require(objectives.get(
                "combat:r82-kills").progress==progress,
                "R82 duplicate objective count");
            MatchSession session=matches.get(matchId);
            MatchSession.Participant p=session.participant(
                attacker.id().toString()
            );
            MatchSession.Team t=session.team(attackerTeam);
            require(p!=null&&t!=null&&
                    p.scores.getOrDefault("kills",0L)==score&&
                    t.scores.getOrDefault("team_kills",0L)==teamScore,
                "R82 duplicate participant/team score");
            CollectionLogService.ProgressSnapshot snapshot=
                collection.snapshot(collectionId);
            Long observed=snapshot.killCount().isPresent()
                ?Long.valueOf(snapshot.killCount().getAsLong())
                :null;
            require(java.util.Objects.equals(observed,collectionCount),
                "R82 Collection Log absolute count changed");
            require(resolverCalls.get()==resolverRuns&&
                    isolatedFailures.get()==errors,
                "R82 collection resolver reran after receipt");
            require(session.state==MatchSession.State.ACTIVE&&
                    !objectives.get(
                        "combat:r82-kills").claimed,
                "R82 receipt granted rewards or ended match");
        }

        @Override public void close(){world.close();}
    }

    public static void main(String[] args)throws Exception{
        fullDeliveryAcrossTwoSeparateGroups();
        refusePartialProjectionCrossGroupRetry();
        failedCollectionResolverCannotRetryInSecondGroup();
        boundedCapacityNeverReopensOriginalOrdinal();
        separateProducerScopesAreDistinct();

        System.out.println(
            "R82_OFFLINE_SOURCE_SCOPED_COMBAT_RECEIPTS_PASS "+
            "actualPvpSourceCallbackIssuesOrdinal=true "+
            "killAndDeathSeparateOrdinalsSameWorldTick=true "+
            "separateProjectionGroupsShareSingleReceiptAuthority=true "+
            "objectiveScoreCollectionDoubleWritesDenied=true "+
            "crossGroupPartialFanoutNotRetried=true "+
            "failedCollectionResolverNotRetriedAcrossGroups=true "+
            "sameFieldsClonedEventUnproven=true "+
            "boundedFullLedgerFailsClosed=true "+
            "noOldReceiptEviction=true "+
            "producerScopesDistinctAcrossFreshWorlds=true "+
            "globalDomainEventsUnaffected=true "+
            "noRestartStableIdOrDurabilityClaim=true"
        );
    }

    private static void fullDeliveryAcrossTwoSeparateGroups()
            throws Exception{
        try(Fixture f=new Fixture(Mode.COMPLETE,8)){
            f.lethal();
            f.assertProjection(
                1L,2L,3L,Long.valueOf(7L),1,0
            );
            require(f.firstGroupDelivery.get()==2&&
                    f.secondGroupDelivery.get()==2&&
                    f.receipts.issuedCount()==2&&
                    f.receipts.receiptCount()==2&&
                    f.receipts.acceptedCount()==2&&
                    f.receipts.duplicateCount()==2&&
                    f.globalDelivery.get()==1&&
                    f.sourceKill.get()!=f.sourceDeath.get()&&
                    f.sourceKill.get().worldTick()==
                        f.sourceDeath.get().worldTick(),
                "R82 source-ordinal identity failed");
            f.attemptUnprovenClones();
            f.assertProjection(
                1L,2L,3L,Long.valueOf(7L),1,0
            );
            require(f.receipts.unprovenCount()>=2&&
                    f.globalDelivery.get()==1,
                "R82 unverified new clone/direct stale fact mutated");
        }
    }

    private static void refusePartialProjectionCrossGroupRetry()
            throws Exception{
        try(Fixture f=new Fixture(
                Mode.PARTIAL_AFTER_OBJECTIVE,8)){
            f.lethal();
            f.assertProjection(1L,0L,0L,null,0,0);
            require(f.receipts.issuedCount()==2&&
                    f.receipts.receiptCount()==2&&
                    f.receipts.duplicateCount()==2&&
                    f.secondGroupDelivery.get()==2,
                "R82 second group resumed an incomplete fanout");
        }
    }

    private static void failedCollectionResolverCannotRetryInSecondGroup()
            throws Exception{
        try(Fixture f=new Fixture(
                Mode.FAILED_COLLECTION_RESOLVER,8)){
            f.lethal();
            f.assertProjection(1L,2L,3L,null,1,1);
            require(f.receipts.duplicateCount()==2&&
                    f.secondGroupDelivery.get()==2,
                "R82 failed collection observer retried on other group");
        }
    }

    private static void boundedCapacityNeverReopensOriginalOrdinal()
            throws Exception{
        try(Fixture f=new Fixture(Mode.COMPLETE,1)){
            f.lethal();
            f.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.issuedCount()==2&&
                    f.receipts.receiptCount()==1&&
                    f.receipts.acceptedCount()==1&&
                    f.receipts.duplicateCount()==1&&
                    f.receipts.capacityCount()==2,
                "R82 capacity did not reject second source emission");
            f.attemptUnprovenClones();
            f.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.receiptCount()==1,
                "R82 capacity evicted a previously consumed ordinal");
        }
    }

    private static void separateProducerScopesAreDistinct()
            throws Exception{
        try(Fixture a=new Fixture(Mode.COMPLETE,2);
            Fixture b=new Fixture(Mode.COMPLETE,2)){
            require(!a.receipts.scopeForAudit().equals(
                        b.receipts.scopeForAudit()),
                "R82 fresh source scopes unexpectedly identical");
            a.lethal();
            b.lethal();
            a.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            b.assertProjection(1L,2L,3L,Long.valueOf(7L),1,0);
            require(a.receipts.issuedCount()==2&&
                    b.receipts.issuedCount()==2,
                "R82 separate source ordinals mixed across worlds");
        }
    }

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private R82OfflineSourceScopedCombatReceiptTest(){}
}

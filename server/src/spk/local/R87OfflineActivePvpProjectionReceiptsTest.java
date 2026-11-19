package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import spk.event.DomainEventBus;

/**
 * R87 OFFLINE ONLY. ACTUAL active LocalPlayerInteractionHandler Attack
 * -> PlayerCombatResolutionService lethal outcome -> R86 exact-object
 * domain source proof -> two INDEPENDENT R80-owner-gated observer groups
 * -> real ObjectiveProgressService, MatchSessionService and
 * CollectionLogService. Both groups share ONE source-proven R81
 * first-delivery receipt ledger. No live LocalSession changes.
 */
public final class R87OfflineActivePvpProjectionReceiptsTest {
    enum Scenario {
        COMPLETE,
        AFTER_OBJECTIVE_TERMINAL,
        BEFORE_MATCH_TEAM_TERMINAL,
        COLLECTION_RESOLVER_FAILURE,
        COLLECTION_RESOLVER_TERMINAL
    }

    static final class Case implements AutoCloseable {
        final R86OfflineActivePvpDomainBridgeTest.Fixture source;
        final R80OfflineCombatProjectionOwnershipFence ownerFence;
        final R81OfflineCombatProjectionReceiptLedger receipts;
        final ObjectiveProgressService objectives=
            new ObjectiveProgressService();
        final MatchSessionService matches=
            new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r87");
        final MatchTeamId attackerTeam=MatchTeamId.of("team:r87-a");
        final MatchTeamId victimTeam=MatchTeamId.of("team:r87-v");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId(
                "collection:r87-test-only"
            );
        final CollectionLogService collection;
        final CombatOutcomeObserverGroup group1;
        final CombatOutcomeObserverGroup group2;
        final AtomicInteger firstGroupInvocations=new AtomicInteger();
        final AtomicInteger secondGroupInvocations=new AtomicInteger();
        final AtomicInteger objectiveWrites=new AtomicInteger();
        final AtomicInteger matchWrites=new AtomicInteger();
        final AtomicInteger resolverCalls=new AtomicInteger();
        final AtomicInteger isolatedFailures=new AtomicInteger();
        final AtomicInteger currentSourceProofs=new AtomicInteger();
        final Scenario scenario;

        Case(Scenario scenario,int receiptCapacity)throws Exception{
            this(scenario,receiptCapacity,null);
        }

        /** R88: preserve exact R87 active handler and add isolated disk IO. */
        Case(Scenario scenario,int receiptCapacity,
             PlayerRepository repository)throws Exception{
            this.scenario=scenario;
            // Reuse the actual R86 active handler and original packet
            // writers rather than a new hand-wired combat resolver.
            source=new R86OfflineActivePvpDomainBridgeTest.Fixture(
                R86OfflineActivePvpDomainBridgeTest.Mode.NORMAL,
                repository
            );
            ownerFence=new R80OfflineCombatProjectionOwnershipFence(
                source.world,
                source.attacker,source.attackerGeneration,
                source.victim,source.victimGeneration,source.terminal
            );
            receipts=new R81OfflineCombatProjectionReceiptLedger(
                receiptCapacity,
                source.bridge::isVerifiedFactInCurrentListenerForTest
            );

            objectives.define(new ObjectiveDefinition(
                "combat:r87-kills",10L,"CUSTOM_LOCALLAB"
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
                    "r87_offline_active_combat","CUSTOM_LOCALLAB"
                )
            );
            matches.addTeam(matchId,attackerTeam);
            matches.addTeam(matchId,victimTeam);
            matches.join(
                matchId,attackerTeam,source.attacker.id().toString()
            );
            matches.join(
                matchId,victimTeam,source.victim.id().toString()
            );
            matches.attachInstance(
                matchId,WorldInstanceId.of("instance:r87")
            );
            matches.markReady(matchId);
            matches.activate(matchId);

            collection=new CollectionLogService(
                Collections.singletonList(
                    new CollectionLogDefinition(
                        collectionId,
                        new CollectionLogDefinition.CategoryId(
                            "category:r87-test"
                        ),
                        "R87 isolated PvP count",
                        Collections.singleton(
                            new CollectionLogDefinition.EntryId(
                                "entry:r87-test"
                            )
                        ),
                        true,
                        CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                    )
                )
            );

            group1=makeGroup(true);
            group2=makeGroup(false);

            source.world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->{
                    firstGroupInvocations.incrementAndGet();
                    require(source.bridge
                            .isVerifiedFactInCurrentListenerForTest(fact),
                        "R87 unverified active event reached first group");
                    currentSourceProofs.incrementAndGet();
                    group1.onCombatOutcome(fact);
                }
            );
            source.world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.LOW,
                fact->{
                    secondGroupInvocations.incrementAndGet();
                    require(source.bridge
                            .isVerifiedFactInCurrentListenerForTest(fact),
                        "R87 unverified active event reached second group");
                    currentSourceProofs.incrementAndGet();
                    group2.onCombatOutcome(fact);
                }
            );
        }

        private CombatOutcomeObserverGroup makeGroup(boolean first){
            CombatOutcomeObjectiveObserver objective=
                new CombatOutcomeObjectiveObserver(
                    objectives,source.attacker.id().toString(),
                    Collections.singletonList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver
                                .SubjectRole.ATTACKER,
                            "combat:r87-kills",1L,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            objective.installOfflineMutationAdmissionForTest(
                fact->{
                    ownerFence.requireAdmitted(fact);
                    objectiveWrites.incrementAndGet();
                }
            );

            CombatOutcomeMatchObserver match=
                new CombatOutcomeMatchObserver(
                    matches,source.attacker.id().toString(),Arrays.asList(
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
                fact->{
                    int n=matchWrites.incrementAndGet();
                    if(first&&
                       scenario==Scenario.BEFORE_MATCH_TEAM_TERMINAL&&
                       n==2)
                        source.terminal.quiesceInsideWorldActor(
                            source.victim,source.victimGeneration
                        );
                    ownerFence.requireAdmitted(fact);
                }
            );

            CombatOutcomeCollectionLogObserver log=
                new CombatOutcomeCollectionLogObserver(
                    collection,source.attacker.id().toString(),
                    (subject,binding,fact,current)->{
                        int n=resolverCalls.incrementAndGet();
                        if(first&&
                           scenario==Scenario.COLLECTION_RESOLVER_FAILURE&&
                           n==1)
                            throw new IllegalStateException(
                                "R87_INJECTED_COLLECTION_RESOLVER_FAILURE"
                            );
                        if(first&&
                           scenario==Scenario.COLLECTION_RESOLVER_TERMINAL&&
                           n==1)
                            source.terminal.quiesceInsideWorldActor(
                                source.victim,source.victimGeneration
                            );
                        return 7L; // Synthetic caller-owned absolute count
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

            CombatOutcomeObserver firstObjective=fact->{
                objective.onCombatOutcome(fact);
                if(first&&
                   scenario==Scenario.AFTER_OBJECTIVE_TERMINAL&&
                   fact.type()==CombatOutcomeType.PLAYER_KILL)
                    source.terminal.quiesceInsideWorldActor(
                        source.victim,source.victimGeneration
                    );
            };
            CombatOutcomeObserverGroup group=
                new CombatOutcomeObserverGroup(
                    Arrays.asList(firstObjective,match,log),
                    (index,observer,fact,failure)->
                        isolatedFailures.incrementAndGet()
                );
            group.installOfflineProjectionAdmissionForTest(
                ownerFence::permitsFanout
            );
            group.installOfflineFirstDeliveryAdmissionForTest(
                receipts
            );
            return group;
        }

        void lethal()throws Exception{
            source.lethal();
            require(source.progress()==1L&&
                    source.globalEvents.get()==1,
                "R87 broke original R86 semantic/domain baseline");
        }

        void check(
            long objective,long participant,long team,
            Long count,int resolverRuns,int failures
        ){
            require(objectives.get(
                    "combat:r87-kills").progress==objective,
                "R87 objective count mismatched");
            MatchSession match=matches.get(matchId);
            MatchSession.Participant player=
                match.participant(source.attacker.id().toString());
            MatchSession.Team attackerSide=match.team(attackerTeam);
            require(player!=null&&attackerSide!=null&&
                    player.scores.getOrDefault(
                        "kills",0L)==participant&&
                    attackerSide.scores.getOrDefault(
                        "team_kills",0L)==team,
                "R87 match participant/team score mismatch");
            CollectionLogService.ProgressSnapshot state=
                collection.snapshot(collectionId);
            Long stored=state.killCount().isPresent()
                ?Long.valueOf(state.killCount().getAsLong()):null;
            require(Objects.equals(stored,count)&&
                    resolverCalls.get()==resolverRuns&&
                    isolatedFailures.get()==failures,
                "R87 Collection Log or failure isolation mismatch");
            require(!objectives.get("combat:r87-kills").claimed&&
                    match.state==MatchSession.State.ACTIVE,
                "R87 incorrectly completed match/claimed reward");
        }

        void testDirectCopiesCannotClaim()throws Exception{
            CombatOutcome actual=source.killFact.get();
            require(actual!=null,"R87 no genuine active kill fact");
            CombatOutcome copy=new CombatOutcome(
                actual.attacker(),actual.victim(),actual.type(),
                actual.context(),actual.worldTick(),
                actual.sourceAuthority()
            );
            int before=receipts.receiptCount();
            source.world.events().schedule(2L,()->{
                // A direct projection group call, without the real R86
                // listener proof, cannot claim a NEW source receipt.
                group1.onCombatOutcome(copy);
                // The exact old instance is already consumed; no
                // fresh receipt or replay after original source exit.
                group2.onCombatOutcome(actual);
            });
            require(source.world.observePulse(
                    System.currentTimeMillis())==2L,
                "R87 later WorldPulse mismatch");
            require(receipts.receiptCount()==before&&
                    receipts.unverifiedRefusalCount()>=1,
                "R87 copied exact-fields input claimed fake receipt");
        }

        @Override public void close(){source.close();}
    }

    public static void main(String[] args)throws Exception{
        completeActivePvpFanoutAndDuplicateRefusal();
        terminalBetweenObjectiveAndMatch();
        terminalWithinMatchBindings();
        failedCollectionResolverNeverReplayed();
        terminalInsideCountResolverBlocksCas();
        limitedReceiptsFailClosed();

        System.out.println(
            "R87_OFFLINE_ACTIVE_PVP_CONSUMER_RECEIPTS_PASS "+
            "realLocalSessionShapedAttackAndHP134=true "+
            "realSourceProvenKillAndDeathFacts=true "+
            "twoIndependentObjectiveMatchCollectionGroups=true "+
            "R80ActorGenerationAndTerminalChecks=true "+
            "R81SharedExactFactFirstDeliveryReceipts=true "+
            "additiveObjectiveAndMatchDoubleCountsDenied=true "+
            "absoluteCollectionResolverRunsOnce=true "+
            "forgedSameFieldsDirectGroupRefused=true "+
            "terminalBetweenObserversStopsLaterWrites=true "+
            "terminalBetweenMatchBindingsStopsTeamWrite=true "+
            "terminalInsideCollectionResolverStopsCas=true "+
            "failedCollectionResolverNeverRetried=true "+
            "partialWritesNeverRolledBack=true "+
            "boundedReceiptCapacityFailsClosed=true "+
            "noRewardClaimOrMatchCompletion=true "+
            "defaultLocalSessionStillNoOp=true "+
            "noDurableSourceIdOrRecoveryClaim=true"
        );
    }

    private static void completeActivePvpFanoutAndDuplicateRefusal()
            throws Exception{
        try(Case f=new Case(Scenario.COMPLETE,8)){
            f.lethal();
            f.check(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.firstGroupInvocations.get()==2&&
                    f.secondGroupInvocations.get()==2&&
                    f.receipts.receiptCount()==2&&
                    f.receipts.firstDeliveryCount()==2L&&
                    f.receipts.duplicateRefusalCount()==2L&&
                    f.currentSourceProofs.get()==4&&
                    f.source.bridge.admittedCountForTest()==2,
                "R87 original two-source fact fanout duplicated");
            f.testDirectCopiesCannotClaim();
            f.check(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.duplicateRefusalCount()==3L&&
                    f.receipts.unverifiedRefusalCount()==1L,
                "R87 copied/expired fact claimed original receipt");
        }
    }

    private static void terminalBetweenObjectiveAndMatch()
            throws Exception{
        try(Case f=new Case(Scenario.AFTER_OBJECTIVE_TERMINAL,8)){
            f.lethal();
            f.check(1L,0L,0L,null,0,0);
            require(f.firstGroupInvocations.get()==1&&
                    f.secondGroupInvocations.get()==0&&
                    f.receipts.receiptCount()==1&&
                    f.ownerFence.deniedFanoutCountForTest()>=1&&
                    f.source.bridge.issuedCountForTest()==1&&
                    f.source.bridge.admittedCountForTest()==0,
                "R87 terminal owner allowed stale match projection");
        }
    }

    private static void terminalWithinMatchBindings()
            throws Exception{
        try(Case f=new Case(Scenario.BEFORE_MATCH_TEAM_TERMINAL,8)){
            f.lethal();
            f.check(1L,2L,0L,null,0,1);
            require(f.matchWrites.get()==2&&
                    f.secondGroupInvocations.get()==0&&
                    f.receipts.receiptCount()==1,
                "R87 quiescence between match bindings missed");
        }
    }

    private static void failedCollectionResolverNeverReplayed()
            throws Exception{
        try(Case f=new Case(
                Scenario.COLLECTION_RESOLVER_FAILURE,8)){
            f.lethal();
            f.check(1L,2L,3L,null,1,1);
            require(f.secondGroupInvocations.get()==2&&
                    f.receipts.duplicateRefusalCount()==2L&&
                    f.source.bridge.admittedCountForTest()==2,
                "R87 failed collection resolver reran on duplicate");
            f.testDirectCopiesCannotClaim();
            f.check(1L,2L,3L,null,1,1);
        }
    }

    private static void terminalInsideCountResolverBlocksCas()
            throws Exception{
        try(Case f=new Case(
                Scenario.COLLECTION_RESOLVER_TERMINAL,8)){
            f.lethal();
            f.check(1L,2L,3L,null,1,1);
            require(f.receipts.receiptCount()==1&&
                    f.source.bridge.issuedCountForTest()==1&&
                    f.source.bridge.admittedCountForTest()==0&&
                    f.secondGroupInvocations.get()==0,
                "R87 stale collection CAS or second group accepted");
        }
    }

    private static void limitedReceiptsFailClosed()
            throws Exception{
        try(Case f=new Case(Scenario.COMPLETE,1)){
            f.lethal();
            f.check(1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.receipts.receiptCount()==1&&
                    f.receipts.firstDeliveryCount()==1L&&
                    f.receipts.duplicateRefusalCount()==1L&&
                    f.receipts.capacityRefusalCount()==2L&&
                    f.firstGroupInvocations.get()==2&&
                    f.secondGroupInvocations.get()==2,
                "R87 full receipt ledger evicted or accepted death");
        }
    }

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }

    private R87OfflineActivePvpProjectionReceiptsTest(){}
}

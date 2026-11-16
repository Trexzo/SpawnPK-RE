package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R80 OFFLINE ONLY. Actual lethal PvP producer (R79) -> real domain bus ->
 * real CombatOutcomeObserverGroup -> actual ObjectiveProgressService,
 * MatchSessionService and CollectionLogService projections.
 *
 * Fixtures deliberately quiesce the same actor BETWEEN observers,
 * BETWEEN bindings, and inside the collection absolute-count resolver.
 * Previously committed changes are not rolled back.
 */
public final class R80OfflineCombatProjectionFanoutTest {
    private enum Mode {
        NORMAL,
        AFTER_OBJECTIVE,
        SECOND_OBJECTIVE_BINDING,
        SECOND_MATCH_BINDING,
        COLLECTION_RESOLVER_BEFORE_COMMIT
    }

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer target=new WorldPlayer();
        final long attackerGeneration;
        final long targetGeneration;
        final R74OfflineCommandIngressFence terminal;
        final R79OfflinePairedPvpOwnershipGate combat;
        final R80OfflineCombatProjectionOwnershipFence fence;
        final ObjectiveProgressService objectives=
            new ObjectiveProgressService();
        final MatchSessionService matches=
            new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r80");
        final MatchTeamId attackerTeam=MatchTeamId.of("team:r80-attacker");
        final MatchTeamId targetTeam=MatchTeamId.of("team:r80-target");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId(
                "collection:r80-combat-projection-fixture"
            );
        final CollectionLogService collections;
        final CombatOutcomeObjectiveObserver objectiveObserver;
        final CombatOutcomeMatchObserver matchObserver;
        final CombatOutcomeCollectionLogObserver collectionObserver;
        final CombatOutcomeObserverGroup group;
        final AtomicInteger objectiveChecks=new AtomicInteger();
        final AtomicInteger matchChecks=new AtomicInteger();
        final AtomicInteger collectionChecks=new AtomicInteger();
        final AtomicInteger resolverCalls=new AtomicInteger();
        final AtomicInteger isolatedFailures=new AtomicInteger();
        final AtomicInteger deliveredCombatEvents=new AtomicInteger();
        final AtomicInteger globalEvents=new AtomicInteger();
        final Mode mode;

        Fixture(Mode mode)throws Exception{
            this.mode=mode;
            attackerGeneration=world.registerPlayer(attacker,"attacker");
            targetGeneration=world.registerPlayer(target,"target");
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,10
            );
            terminal=new R74OfflineCommandIngressFence(world);
            terminal.installForTest();
            combat=new R79OfflinePairedPvpOwnershipGate(
                world,attacker,attackerGeneration,
                target,targetGeneration,terminal,
                CombatDamageRules.localLabFallback()
            );
            fence=new R80OfflineCombatProjectionOwnershipFence(
                world,attacker,attackerGeneration,
                target,targetGeneration,terminal
            );

            objectives.define(
                new ObjectiveDefinition(
                    "combat:r80-primary",10L,"CUSTOM_LOCALLAB"
                )
            );
            objectives.define(
                new ObjectiveDefinition(
                    "combat:r80-secondary",10L,"CUSTOM_LOCALLAB"
                )
            );
            objectiveObserver=new CombatOutcomeObjectiveObserver(
                objectives,attacker.id().toString(),Arrays.asList(
                    new CombatOutcomeObjectiveObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeObjectiveObserver
                            .SubjectRole.ATTACKER,
                        "combat:r80-primary",1L,"CUSTOM_LOCALLAB"
                    ),
                    new CombatOutcomeObjectiveObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeObjectiveObserver
                            .SubjectRole.ATTACKER,
                        "combat:r80-secondary",1L,"CUSTOM_LOCALLAB"
                    )
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
                    "r80_projection_test","CUSTOM_LOCALLAB"
                )
            );
            matches.addTeam(matchId,attackerTeam);
            matches.addTeam(matchId,targetTeam);
            matches.join(
                matchId,attackerTeam,attacker.id().toString()
            );
            matches.join(
                matchId,targetTeam,target.id().toString()
            );
            matches.attachInstance(
                matchId,WorldInstanceId.of("instance:r80")
            );
            matches.markReady(matchId);
            matches.activate(matchId);
            matchObserver=new CombatOutcomeMatchObserver(
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

            collections=new CollectionLogService(
                Collections.singletonList(
                    new CollectionLogDefinition(
                        collectionId,
                        new CollectionLogDefinition.CategoryId(
                            "category:r80-fixture"
                        ),
                        "R80 offline combat projection",
                        Collections.singleton(
                            new CollectionLogDefinition.EntryId(
                                "entry:r80-fixture"
                            )
                        ),
                        true,
                        CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                    )
                )
            );
            collectionObserver=new CombatOutcomeCollectionLogObserver(
                collections,attacker.id().toString(),
                (subject,binding,outcome,current)->{
                    resolverCalls.incrementAndGet();
                    if(mode==Mode.COLLECTION_RESOLVER_BEFORE_COMMIT)
                        terminal.quiesceInsideWorldActor(
                            attacker,attackerGeneration
                        );
                    return 7L; // synthetic caller-owned absolute count
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

            // Every mutation hook is opt-in. The normal observer
            // implementation keeps its exact direct path otherwise.
            objectiveObserver.installOfflineMutationAdmissionForTest(
                fact->{
                    if(mode==Mode.SECOND_OBJECTIVE_BINDING &&
                       objectiveChecks.incrementAndGet()==2)
                        terminal.quiesceInsideWorldActor(
                            attacker,attackerGeneration
                        );
                    else if(mode!=Mode.SECOND_OBJECTIVE_BINDING)
                        objectiveChecks.incrementAndGet();
                    fence.requireAdmitted(fact);
                }
            );
            matchObserver.installOfflineMutationAdmissionForTest(
                fact->{
                    if(mode==Mode.SECOND_MATCH_BINDING &&
                       matchChecks.incrementAndGet()==2)
                        terminal.quiesceInsideWorldActor(
                            target,targetGeneration
                        );
                    else if(mode!=Mode.SECOND_MATCH_BINDING)
                        matchChecks.incrementAndGet();
                    fence.requireAdmitted(fact);
                }
            );
            collectionObserver.installOfflineMutationAdmissionForTest(
                fact->{
                    collectionChecks.incrementAndGet();
                    fence.requireAdmitted(fact);
                }
            );

            group=new CombatOutcomeObserverGroup(
                Arrays.asList(
                    fact->{
                        objectiveObserver.onCombatOutcome(fact);
                        if(mode==Mode.AFTER_OBJECTIVE &&
                           fact.type()==CombatOutcomeType.PLAYER_KILL)
                            terminal.quiesceInsideWorldActor(
                                attacker,attackerGeneration
                            );
                    },
                    matchObserver,
                    collectionObserver
                ),
                (index,observer,fact,failure)->
                    isolatedFailures.incrementAndGet()
            );
            group.installOfflineProjectionAdmissionForTest(
                fence::permitsFanout
            );
            world.domainEvents().subscribe(
                CombatOutcome.class,
                DomainEventBus.Priority.NORMAL,
                fact->{
                    deliveredCombatEvents.incrementAndGet();
                    group.onCombatOutcome(fact);
                }
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,
                DomainEventBus.Priority.NORMAL,
                fact->globalEvents.incrementAndGet()
            );
        }

        PlayerCombatResolutionService.Result lethal()throws Exception{
            AtomicReference<PlayerCombatResolutionService.Result> value=
                new AtomicReference<>();
            world.events().schedule(1L,()->{
                try{
                    value.set(combat.resolveForTest(4151,null,1L));
                    world.domainEvents().publish(new GlobalNotice());
                }catch(Exception failure){
                    throw new IllegalStateException(
                        "R80 real PvP producer failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R80 actual WorldPulse tick mismatch");
            require(value.get()!=null && value.get().lifecycle.died,
                "R80 real PlayerCombatResolutionService lethal failed");
            return value.get();
        }

        long objective(String key){
            return objectives.get(key).progress;
        }

        long participantKills(){
            MatchSession.Participant p=matches.get(matchId)
                .participant(attacker.id().toString());
            require(p!=null,"R80 missing match participant");
            return p.scores.getOrDefault("kills",0L);
        }

        long teamKills(){
            MatchSession.Team team=matches.get(matchId)
                .team(attackerTeam);
            require(team!=null,"R80 missing match team");
            return team.scores.getOrDefault("team_kills",0L);
        }

        Long collectionCount(){
            CollectionLogService.ProgressSnapshot snap=
                collections.snapshot(collectionId);
            return snap.killCount().isPresent()
                ?Long.valueOf(snap.killCount().getAsLong())
                :null;
        }

        void assertState(
            long objectivePrimary,long objectiveSecondary,
            long participant,long team,Long collection,
            int resolverInvocations,int observerFailures
        ){
            require(objective("combat:r80-primary")==objectivePrimary &&
                    objective("combat:r80-secondary")==objectiveSecondary,
                "R80 objective partial projection incorrect");
            require(participantKills()==participant&&
                    teamKills()==team,
                "R80 match participant/team score incorrect");
            require(java.util.Objects.equals(
                    collectionCount(),collection),
                "R80 collection absolute projection incorrect");
            require(resolverCalls.get()==resolverInvocations &&
                    isolatedFailures.get()==observerFailures,
                "R80 resolver/failure isolation count incorrect");
            require(!objectives.get(
                    "combat:r80-primary").claimed,
                "R80 projection incorrectly claimed a reward");
            require(matches.get(matchId).state==
                    MatchSession.State.ACTIVE,
                "R80 projection unexpectedly ended the match");
        }

        @Override public void close(){
            world.close();
        }
    }

    private static final class GlobalNotice
        implements DomainEventBus.Event {}

    public static void main(String[] args)throws Exception{
        acceptedCompleteProjection();
        stoppedBeforeNextObserver();
        stoppedWithinObjectiveBindings();
        stoppedWithinMatchBindings();
        collectionResolverCannotCommitAfterQuiescence();
        directStaleFanoutCannotMutate();
        System.out.println(
            "R80_OFFLINE_COMBAT_PROJECTION_FANOUT_PASS "+
            "realPvpProducerDomainFanout=true "+
            "actualObjectiveMatchCollectionServices=true "+
            "normalThreeProjectionBaseline=true "+
            "actorQuiescenceStopsLaterObservers=true "+
            "quiescenceBetweenObjectiveBindingsDenied=true "+
            "quiescenceBetweenMatchBindingsDenied=true "+
            "collectionResolverQuiescenceDeniedBeforeAtomicCas=true "+
            "priorCommittedProjectionNotRolledBack=true "+
            "observerFailureIsolationStillWorks=true "+
            "staleDirectFanoutDenied=true "+
            "globalEventStillDelivered=true "+
            "noRewardsClaimsOrLiveRuntimeChanges=true"
        );
    }

    private static void acceptedCompleteProjection()throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL)){
            f.lethal();
            f.assertState(1L,1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.globalEvents.get()==1&&
                    f.deliveredCombatEvents.get()==2&&
                    f.fence.deniedFanoutCountForTest()==0,
                "R80 complete baseline unexpectedly gated real PvP");
        }
    }

    private static void stoppedBeforeNextObserver()throws Exception{
        try(Fixture f=new Fixture(Mode.AFTER_OBJECTIVE)){
            f.lethal();
            f.assertState(1L,1L,0L,0L,null,0,0);
            require(f.terminal.isQuiesced(
                        f.attacker,f.attackerGeneration)&&
                    f.fence.deniedFanoutCountForTest()>=1&&
                    f.deliveredCombatEvents.get()==1&&
                    f.globalEvents.get()==1,
                "R80 group ran another observer after terminal flip");
        }
    }

    private static void stoppedWithinObjectiveBindings()
            throws Exception{
        try(Fixture f=new Fixture(Mode.SECOND_OBJECTIVE_BINDING)){
            f.lethal();
            f.assertState(1L,0L,0L,0L,null,0,1);
            require(f.objectiveChecks.get()==2&&
                    f.fence.deniedFanoutCountForTest()>=1&&
                    f.globalEvents.get()==1,
                "R80 objective second binding used stale authorization");
        }
    }

    private static void stoppedWithinMatchBindings()
            throws Exception{
        try(Fixture f=new Fixture(Mode.SECOND_MATCH_BINDING)){
            f.lethal();
            f.assertState(1L,1L,2L,0L,null,0,1);
            require(f.matchChecks.get()==2&&
                    f.fence.deniedFanoutCountForTest()>=1&&
                    f.globalEvents.get()==1,
                "R80 match team binding used stale authorization");
        }
    }

    private static void collectionResolverCannotCommitAfterQuiescence()
            throws Exception{
        try(Fixture f=new Fixture(
                Mode.COLLECTION_RESOLVER_BEFORE_COMMIT)){
            f.lethal();
            f.assertState(1L,1L,2L,3L,null,1,1);
            require(f.collectionChecks.get()==2 &&
                    f.terminal.isQuiesced(
                        f.attacker,f.attackerGeneration)&&
                    f.globalEvents.get()==1,
                "R80 collection resolver committed after actor quiescence");
            // Earlier objective/match scores persist by design.
            require(f.participantKills()==2L,
                "R80 earlier committed score was falsely rolled back");
        }
    }

    private static void directStaleFanoutCannotMutate()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL)){
            f.lethal();
            f.assertState(1L,1L,2L,3L,Long.valueOf(7L),1,0);

            CombatOutcome copied=new CombatOutcome(
                f.attacker.id().toString(),
                f.target.id().toString(),
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,2L,
                PlayerLifecycleService.AUTHORITY
            );
            // This direct invocation lacks upstream R79 source proof,
            // but R80 still refuses it when run off-world or stale.
            f.group.onCombatOutcome(copied);
            f.assertState(1L,1L,2L,3L,Long.valueOf(7L),1,0);

            require(f.world.unregisterPlayer(
                    f.attacker,f.attackerGeneration),
                "R80 attacker unregister failed");
            f.world.events().schedule(2L,()->
                f.group.onCombatOutcome(copied)
            );
            require(f.world.observePulse(
                    System.currentTimeMillis())==2L,
                "R80 stale group tick mismatch");
            f.assertState(1L,1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.fence.deniedFanoutCountForTest()>=2,
                "R80 stale direct listener gate did not refuse");
        }
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private R80OfflineCombatProjectionFanoutTest(){}
}

package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R81 OFFLINE ONLY. R79-attested real lethal PvP fact -> DomainEventBus ->
 * R80 owner-fenced group -> real objective, match and Collection Log services.
 * The same fact reference is deliberately delivered a second time while still
 * inside the real R79-authenticated listener callback.
 */
public final class R81OfflineCombatOutcomeReplayTest {
    private enum Mode {
        COMPLETE, OBJECTIVE_INTERRUPTED, MATCH_INTERRUPTED,
        COLLECTION_INTERRUPTED, QUIESCE_AFTER_OBJECTIVE, REENTRANT
    }

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer target=new WorldPlayer();
        final long attackerGeneration;
        final long targetGeneration;
        final R74OfflineCommandIngressFence terminal;
        final R79OfflinePairedPvpOwnershipGate combat;
        final R80OfflineCombatProjectionOwnershipFence ownership;
        final R81OfflineCombatOutcomeReplayFence replay=
            new R81OfflineCombatOutcomeReplayFence();
        final ObjectiveProgressService objectives=new ObjectiveProgressService();
        final MatchSessionService matches=new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r81");
        final MatchTeamId attackerTeam=MatchTeamId.of("team:r81-attacker");
        final MatchTeamId targetTeam=MatchTeamId.of("team:r81-target");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId("collection:r81-fixture");
        final CollectionLogService collections;
        final CombatOutcomeObserverGroup group;
        final AtomicInteger objectiveAdmissions=new AtomicInteger();
        final AtomicInteger matchAdmissions=new AtomicInteger();
        final AtomicInteger resolverCalls=new AtomicInteger();
        final AtomicInteger failures=new AtomicInteger();
        final AtomicInteger busDeliveries=new AtomicInteger();
        final AtomicInteger globals=new AtomicInteger();
        final AtomicInteger recursiveCalls=new AtomicInteger();

        Fixture(Mode mode,boolean installReplay)throws Exception{
            attackerGeneration=world.registerPlayer(attacker,"attacker");
            targetGeneration=world.registerPlayer(target,"target");
            target.playerState().setCurrentLevel(PlayerState.HITPOINTS,10);
            terminal=new R74OfflineCommandIngressFence(world);
            terminal.installForTest();
            combat=new R79OfflinePairedPvpOwnershipGate(
                world,attacker,attackerGeneration,target,targetGeneration,
                terminal,CombatDamageRules.localLabFallback()
            );
            ownership=new R80OfflineCombatProjectionOwnershipFence(
                world,attacker,attackerGeneration,target,targetGeneration,
                terminal
            );

            objectives.define(new ObjectiveDefinition(
                "combat:r81-primary",10L,"CUSTOM_LOCALLAB"
            ));
            objectives.define(new ObjectiveDefinition(
                "combat:r81-secondary",10L,"CUSTOM_LOCALLAB"
            ));
            CombatOutcomeObjectiveObserver objective=
                new CombatOutcomeObjectiveObserver(
                    objectives,attacker.id().toString(),Arrays.asList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                            "combat:r81-primary",1L,"CUSTOM_LOCALLAB"
                        ),
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                            "combat:r81-secondary",1L,"CUSTOM_LOCALLAB"
                        )
                    )
                );

            matches.create(matchId,new MatchRules(
                MatchRules.TeamMode.TEAMS,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "r81_replay_test","CUSTOM_LOCALLAB"
            ));
            matches.addTeam(matchId,attackerTeam);
            matches.addTeam(matchId,targetTeam);
            matches.join(matchId,attackerTeam,attacker.id().toString());
            matches.join(matchId,targetTeam,target.id().toString());
            matches.attachInstance(matchId,WorldInstanceId.of("instance:r81"));
            matches.markReady(matchId);
            matches.activate(matchId);
            CombatOutcomeMatchObserver match=new CombatOutcomeMatchObserver(
                matches,attacker.id().toString(),Arrays.asList(
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver.SubjectRole.ATTACKER,
                        matchId,CombatOutcomeMatchObserver.ScoreScope.PARTICIPANT,
                        "kills",2L,"CUSTOM_LOCALLAB"
                    ),
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver.SubjectRole.ATTACKER,
                        matchId,CombatOutcomeMatchObserver.ScoreScope.SUBJECT_TEAM,
                        "team_kills",3L,"CUSTOM_LOCALLAB"
                    )
                )
            );

            collections=new CollectionLogService(
                Collections.singletonList(new CollectionLogDefinition(
                    collectionId,new CollectionLogDefinition.CategoryId(
                        "category:r81-fixture"
                    ),
                    "R81 offline combat replay",
                    Collections.singleton(
                        new CollectionLogDefinition.EntryId("entry:r81-fixture")
                    ),true,CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                ))
            );
            CombatOutcomeCollectionLogObserver collection=
                new CombatOutcomeCollectionLogObserver(
                    collections,attacker.id().toString(),
                    (subject,binding,fact,current)->{
                        resolverCalls.incrementAndGet();
                        if(mode==Mode.COLLECTION_INTERRUPTED)
                            throw new IllegalStateException(
                                "R81_TEST_RESOLVER_INTERRUPTED"
                            );
                        return 7L; // synthetic fixture count, not production PvP
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

            objective.installOfflineMutationAdmissionForTest(fact->{
                ownership.requireAdmitted(fact);
                if(mode==Mode.OBJECTIVE_INTERRUPTED &&
                   objectiveAdmissions.incrementAndGet()==2)
                    throw new IllegalStateException(
                        "R81_TEST_OBJECTIVE_INTERRUPTED"
                    );
                if(mode!=Mode.OBJECTIVE_INTERRUPTED)
                    objectiveAdmissions.incrementAndGet();
            });
            match.installOfflineMutationAdmissionForTest(fact->{
                ownership.requireAdmitted(fact);
                if(mode==Mode.MATCH_INTERRUPTED &&
                   matchAdmissions.incrementAndGet()==2)
                    throw new IllegalStateException(
                        "R81_TEST_MATCH_INTERRUPTED"
                    );
                if(mode!=Mode.MATCH_INTERRUPTED)
                    matchAdmissions.incrementAndGet();
            });
            collection.installOfflineMutationAdmissionForTest(
                ownership::requireAdmitted
            );

            AtomicReference<CombatOutcomeObserverGroup> groupRef=
                new AtomicReference<>();
            group=new CombatOutcomeObserverGroup(
                Arrays.asList(
                    fact->{
                        objective.onCombatOutcome(fact);
                        if(mode==Mode.REENTRANT &&
                           fact.type()==CombatOutcomeType.PLAYER_KILL){
                            recursiveCalls.incrementAndGet();
                            groupRef.get().onCombatOutcome(fact);
                        }
                        if(mode==Mode.QUIESCE_AFTER_OBJECTIVE &&
                           fact.type()==CombatOutcomeType.PLAYER_KILL)
                            terminal.quiesceInsideWorldActor(
                                attacker,attackerGeneration
                            );
                    },
                    match,collection
                ),
                (index,observer,fact,error)->failures.incrementAndGet()
            );
            groupRef.set(group);
            group.installOfflineProjectionAdmissionForTest(
                ownership::permitsFanout
            );
            if(installReplay){
                group.installOfflineReplayAdmissionForTest(
                    replay::beginFirstDelivery
                );
            }
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->{
                    busDeliveries.incrementAndGet();
                    // A repeated callback with the SAME R79-proven object.
                    // Never bypass the producer's bus proof to fabricate it.
                    group.onCombatOutcome(fact);
                    group.onCombatOutcome(fact);
                }
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                fact->globals.incrementAndGet()
            );
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
                        "R81 real PvP producer failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R81 WorldPulse tick mismatch");
            require(result.get()!=null&&result.get().lifecycle.died,
                "R81 real lethal resolution failed");
        }

        void state(long primary,long secondary,long participant,long team,
                   Long count,int resolver,int failureCount){
            require(objectives.get("combat:r81-primary").progress==primary&&
                    objectives.get("combat:r81-secondary").progress==secondary,
                "R81 objective replay/partial progress mismatch");
            MatchSession match=matches.get(matchId);
            require(match.participant(attacker.id().toString())
                    .scores.getOrDefault("kills",0L)==participant,
                "R81 participant score replay mismatch");
            require(match.team(attackerTeam).scores
                    .getOrDefault("team_kills",0L)==team,
                "R81 team score replay mismatch");
            CollectionLogService.ProgressSnapshot snapshot=
                collections.snapshot(collectionId);
            Long actual=snapshot.killCount().isPresent()
                ?Long.valueOf(snapshot.killCount().getAsLong()):null;
            require(java.util.Objects.equals(actual,count),
                "R81 collection count replay mismatch");
            require(resolverCalls.get()==resolver&&failures.get()==failureCount,
                "R81 resolver/failure count mismatch");
            require(!objectives.get("combat:r81-primary").claimed&&
                    match.state==MatchSession.State.ACTIVE,
                "R81 projection unexpectedly claimed/closed a match");
        }

        @Override public void close(){world.close();}
    }

    private static final class GlobalNotice implements DomainEventBus.Event {}

    public static void main(String[] args)throws Exception{
        fullReplayNoDoubleCount();
        objectiveInterruptedReplayNoDoubleCount();
        matchInterruptedReplayNoDoubleCount();
        collectionInterruptedReplayNoRetry();
        terminalReplayNoMutation();
        recursiveReplayRefused();
        distinctFactsCannotShareKey();
        ownerDenialDoesNotConsumeReplay();
        duplicateReplayInstallationDenied();
        unchangedWithoutReplayHook();
        System.out.println(
            "R81_OFFLINE_COMBAT_OUTCOME_REPLAY_PASS "+
            "realR79ProducerAndR80WorldOwnerGate=true "+
            "duplicateExactFactOnceAcrossThreeProjections=true "+
            "objectiveAndMatchPartialReplaysNotRetried=true "+
            "collectionResolverFailureNotRetried=true "+
            "terminalReplayDoesNotMutate=true "+
            "nestedFanoutSameFactRefused=true "+
            "distinctSameFieldFactsNotCollapsed=true "+
            "ownerPreflightBeforeReplayConsumption=true "+
            "duplicateHookInstallationDenied=true "+
            "uninstalledLegacyBehaviorUnchanged=true "+
            "offlineAtMostOnceNotDurableExactlyOnce=true"
        );
    }

    private static void fullReplayNoDoubleCount()throws Exception{
        try(Fixture f=new Fixture(Mode.COMPLETE,true)){
            f.lethal();
            f.state(1L,1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.busDeliveries.get()==2&&f.globals.get()==1&&
                    f.replay.acceptedForTest()==2&&f.replay.deniedForTest()==2,
                "R81 same-instance double callback was not suppressed");
        }
    }

    private static void objectiveInterruptedReplayNoDoubleCount()
            throws Exception{
        try(Fixture f=new Fixture(Mode.OBJECTIVE_INTERRUPTED,true)){
            f.lethal();
            f.state(1L,0L,2L,3L,Long.valueOf(7L),1,1);
            require(f.objectiveAdmissions.get()==2&&
                    f.replay.deniedForTest()==2,
                "R81 objective partial replay applied twice");
        }
    }

    private static void matchInterruptedReplayNoDoubleCount()
            throws Exception{
        try(Fixture f=new Fixture(Mode.MATCH_INTERRUPTED,true)){
            f.lethal();
            f.state(1L,1L,2L,0L,Long.valueOf(7L),1,1);
            require(f.matchAdmissions.get()==2&&
                    f.replay.deniedForTest()==2,
                "R81 match partial replay applied twice");
        }
    }

    private static void collectionInterruptedReplayNoRetry()
            throws Exception{
        try(Fixture f=new Fixture(Mode.COLLECTION_INTERRUPTED,true)){
            f.lethal();
            f.state(1L,1L,2L,3L,null,1,1);
            require(f.replay.deniedForTest()==2,
                "R81 resolver was retried after partial projection");
        }
    }

    private static void terminalReplayNoMutation()throws Exception{
        try(Fixture f=new Fixture(Mode.QUIESCE_AFTER_OBJECTIVE,true)){
            f.lethal();
            f.state(1L,1L,0L,0L,null,0,0);
            require(f.busDeliveries.get()==1&&
                    f.terminal.isQuiesced(f.attacker,f.attackerGeneration)&&
                    f.ownership.deniedFanoutCountForTest()>=1&&
                    f.replay.acceptedForTest()==1&&
                    f.globals.get()==1,
                "R81 terminal event replay mutated downstream state");
        }
    }

    private static void recursiveReplayRefused()throws Exception{
        try(Fixture f=new Fixture(Mode.REENTRANT,true)){
            f.lethal();
            f.state(1L,1L,2L,3L,Long.valueOf(7L),1,0);
            require(f.recursiveCalls.get()==1&&
                    f.replay.deniedForTest()==3,
                "R81 nested same-object fanout was accepted");
        }
    }

    private static void distinctFactsCannotShareKey(){
        R81OfflineCombatOutcomeReplayFence ledger=
            new R81OfflineCombatOutcomeReplayFence();
        CombatOutcome a=new CombatOutcome(
            "attacker","victim",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"TEST"
        );
        CombatOutcome b=new CombatOutcome(
            "attacker","victim",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"TEST"
        );
        require(a!=b&&ledger.beginFirstDelivery(a)&&
                ledger.beginFirstDelivery(b)&&
                !ledger.beginFirstDelivery(a)&&
                ledger.acceptedForTest()==2&&ledger.deniedForTest()==1,
            "R81 falsely coalesced distinct equal-field outcomes");
    }

    private static void ownerDenialDoesNotConsumeReplay(){
        R81OfflineCombatOutcomeReplayFence ledger=
            new R81OfflineCombatOutcomeReplayFence();
        CombatOutcome fact=new CombatOutcome(
            "a","v",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"TEST"
        );
        AtomicInteger permitted=new AtomicInteger();
        AtomicInteger called=new AtomicInteger();
        CombatOutcomeObserverGroup group=new CombatOutcomeObserverGroup(
            Collections.singletonList(ignored->called.incrementAndGet()),
            (index,observer,outcome,error)->{
                throw new AssertionError("R81 unexpected local failure");
            }
        );
        group.installOfflineProjectionAdmissionForTest(
            (ignored,index)->permitted.get()==1
        );
        group.installOfflineReplayAdmissionForTest(
            ledger::beginFirstDelivery
        );
        group.onCombatOutcome(fact);
        require(ledger.acceptedForTest()==0&&called.get()==0,
            "R81 consumed replay token before R80 owner preflight");
        permitted.set(1);
        group.onCombatOutcome(fact);
        group.onCombatOutcome(fact);
        require(ledger.acceptedForTest()==1&&ledger.deniedForTest()==1&&
                called.get()==1,
            "R81 incorrect owner preflight/first-delivery order");
    }

    private static void duplicateReplayInstallationDenied()throws Exception{
        try(Fixture f=new Fixture(Mode.COMPLETE,true)){
            try{
                f.group.installOfflineReplayAdmissionForTest(
                    fact->true
                );
                throw new AssertionError(
                    "R81 replay policy replacement was permitted"
                );
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "R81_REPLAY_GATE_DENIED already installed"
                ),"R81 wrong policy replacement denial");
            }
        }
        CombatOutcomeObserverGroup noOwner=new CombatOutcomeObserverGroup(
            Collections.singletonList(fact->{}),
            (index,observer,fact,error)->{}
        );
        try{
            noOwner.installOfflineReplayAdmissionForTest(fact->true);
            throw new AssertionError("R81 replay gate without R80 accepted");
        }catch(IllegalStateException expected){
            require(expected.getMessage().contains(
                "R81_REPLAY_GATE_DENIED R80 owner gate required"
            ),"R81 missing R80 gate not rejected");
        }
    }

    private static void unchangedWithoutReplayHook()throws Exception{
        try(Fixture f=new Fixture(Mode.COMPLETE,false)){
            f.lethal();
            f.state(2L,2L,4L,6L,Long.valueOf(7L),2,0);
            require(f.replay.acceptedForTest()==0&&
                    f.replay.deniedForTest()==0&&f.globals.get()==1,
                "R81 unintentionally enabled replay protection in legacy path");
        }
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private R81OfflineCombatOutcomeReplayTest(){}
}

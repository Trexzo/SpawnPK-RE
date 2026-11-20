package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R83 OFFLINE ONLY. Genuine R79-attested PvP -> DomainEventBus -> R81
 * once-only delivery -> R80 owner-gated group -> real objective, match,
 * Collection Log projections, with independent R83 callback observations.
 *
 * No checkpoint persistence, recovery, replay or live reward/action path.
 */
public final class R83OfflineCombatCheckpointAuditTest {
    private enum Mode {
        NORMAL,
        OBJECTIVE_SECOND_BINDING_THROWS,
        MATCH_AFTER_WRITE_THROWS,
        COLLECTION_AFTER_WRITE_THROWS,
        TERMINAL_AFTER_OBJECTIVE
    }

    private static final class GlobalNotice
        implements DomainEventBus.Event {}

    private static final class Fixture implements AutoCloseable {
        final Mode mode;
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer target=new WorldPlayer();
        final long attackerGeneration;
        final long targetGeneration;
        final R74OfflineCommandIngressFence terminal;
        final R79OfflinePairedPvpOwnershipGate source;
        final R80OfflineCombatProjectionOwnershipFence owner;
        final R81OfflineCombatOutcomeReplayFence replay=
            new R81OfflineCombatOutcomeReplayFence();
        final R83OfflineCombatCheckpointAudit audit=
            new R83OfflineCombatCheckpointAudit();
        final ObjectiveProgressService objectives=new ObjectiveProgressService();
        final MatchSessionService matches=new MatchSessionService();
        final MatchId matchId=MatchId.of("match:r83");
        final MatchTeamId attackerTeam=MatchTeamId.of("team:r83-attacker");
        final MatchTeamId victimTeam=MatchTeamId.of("team:r83-victim");
        final CollectionLogDefinition.CollectionId collectionId=
            new CollectionLogDefinition.CollectionId("collection:r83-fixture");
        final CollectionLogService collections;
        final AtomicInteger objectiveChecks=new AtomicInteger();
        final AtomicInteger failures=new AtomicInteger();
        final AtomicInteger resolverCalls=new AtomicInteger();
        final AtomicInteger deliveredEvents=new AtomicInteger();
        final AtomicInteger globalEvents=new AtomicInteger();
        final AtomicReference<CombatOutcome> killFact=new AtomicReference<>();
        final CombatOutcome delivery;

        Fixture(Mode mode)throws Exception {
            this.mode=mode;
            attackerGeneration=world.registerPlayer(attacker,"attacker");
            targetGeneration=world.registerPlayer(target,"target");
            target.playerState().setCurrentLevel(PlayerState.HITPOINTS,10);
            terminal=new R74OfflineCommandIngressFence(world);
            terminal.installForTest();
            source=new R79OfflinePairedPvpOwnershipGate(
                world,attacker,attackerGeneration,target,targetGeneration,
                terminal,CombatDamageRules.localLabFallback()
            );
            owner=new R80OfflineCombatProjectionOwnershipFence(
                world,attacker,attackerGeneration,target,targetGeneration,
                terminal
            );

            objectives.define(new ObjectiveDefinition(
                "combat:r83-primary",10L,"CUSTOM_LOCALLAB"
            ));
            objectives.define(new ObjectiveDefinition(
                "combat:r83-secondary",10L,"CUSTOM_LOCALLAB"
            ));
            CombatOutcomeObjectiveObserver objective=
                new CombatOutcomeObjectiveObserver(
                    objectives,attacker.id().toString(),Arrays.asList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                            "combat:r83-primary",1L,"CUSTOM_LOCALLAB"
                        ),
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver.SubjectRole.ATTACKER,
                            "combat:r83-secondary",1L,"CUSTOM_LOCALLAB"
                        )
                    )
                );
            objective.installOfflineMutationAdmissionForTest(fact->{
                owner.requireAdmitted(fact);
                if(mode==Mode.OBJECTIVE_SECOND_BINDING_THROWS &&
                   objectiveChecks.incrementAndGet()==2)
                    throw new IllegalStateException(
                        "R83_TEST_SECOND_OBJECTIVE_INTERRUPTED"
                    );
                if(mode!=Mode.OBJECTIVE_SECOND_BINDING_THROWS)
                    objectiveChecks.incrementAndGet();
            });

            matches.create(matchId,new MatchRules(
                MatchRules.TeamMode.TEAMS,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "r83_checkpoint_test","CUSTOM_LOCALLAB"
            ));
            matches.addTeam(matchId,attackerTeam);
            matches.addTeam(matchId,victimTeam);
            matches.join(matchId,attackerTeam,attacker.id().toString());
            matches.join(matchId,victimTeam,target.id().toString());
            matches.attachInstance(matchId,WorldInstanceId.of("instance:r83"));
            matches.markReady(matchId);
            matches.activate(matchId);
            CombatOutcomeMatchObserver match=new CombatOutcomeMatchObserver(
                matches,attacker.id().toString(),
                Collections.singletonList(
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver.SubjectRole.ATTACKER,
                        matchId,
                        CombatOutcomeMatchObserver.ScoreScope.PARTICIPANT,
                        "kills",2L,"CUSTOM_LOCALLAB"
                    )
                )
            );
            match.installOfflineMutationAdmissionForTest(
                owner::requireAdmitted
            );

            collections=new CollectionLogService(
                Collections.singletonList(new CollectionLogDefinition(
                    collectionId,
                    new CollectionLogDefinition.CategoryId("category:r83"),
                    "R83 offline count",
                    Collections.singleton(
                        new CollectionLogDefinition.EntryId("entry:r83")
                    ),
                    true,CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                ))
            );
            CombatOutcomeCollectionLogObserver collection=
                new CombatOutcomeCollectionLogObserver(
                    collections,attacker.id().toString(),
                    (subject,binding,fact,current)->{
                        resolverCalls.incrementAndGet();
                        return 7L; // synthetic absolute count ONLY
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
                owner::requireAdmitted
            );

            CombatOutcomeObserver objectiveWithFault=fact->{
                objective.onCombatOutcome(fact);
                if(mode==Mode.TERMINAL_AFTER_OBJECTIVE &&
                   fact.type()==CombatOutcomeType.PLAYER_KILL)
                    terminal.quiesceInsideWorldActor(
                        attacker,attackerGeneration
                    );
            };
            CombatOutcomeObserver matchWithFault=fact->{
                match.onCombatOutcome(fact);
                if(mode==Mode.MATCH_AFTER_WRITE_THROWS &&
                   fact.type()==CombatOutcomeType.PLAYER_KILL)
                    throw new IllegalStateException(
                        "R83_TEST_MATCH_POST_WRITE_EXCEPTION"
                    );
            };
            CombatOutcomeObserver collectionWithFault=fact->{
                collection.onCombatOutcome(fact);
                if(mode==Mode.COLLECTION_AFTER_WRITE_THROWS &&
                   fact.type()==CombatOutcomeType.PLAYER_KILL)
                    throw new IllegalStateException(
                        "R83_TEST_COLLECTION_POST_WRITE_EXCEPTION"
                    );
            };

            CombatOutcomeObserverGroup group=
                new CombatOutcomeObserverGroup(
                    Arrays.asList(
                        new R83OfflineCombatCheckpointObserver(
                            owner,audit,
                            R83OfflineCombatCheckpointAudit.Consumer.OBJECTIVE,
                            objectiveWithFault
                        ),
                        new R83OfflineCombatCheckpointObserver(
                            owner,audit,
                            R83OfflineCombatCheckpointAudit.Consumer.MATCH,
                            matchWithFault
                        ),
                        new R83OfflineCombatCheckpointObserver(
                            owner,audit,
                            R83OfflineCombatCheckpointAudit.Consumer.COLLECTION,
                            collectionWithFault
                        )
                    ),
                    (index,observer,fact,error)->
                        failures.incrementAndGet()
                );
            group.installOfflineProjectionAdmissionForTest(
                owner::permitsFanout
            );
            CombatOutcomeObserver guarded=
                new R81OfflineCombatOutcomeDeliveryGuard(owner,replay,group);
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->{
                    deliveredEvents.incrementAndGet();
                    if(fact.type()==CombatOutcomeType.PLAYER_KILL)
                        killFact.set(fact);
                    guarded.onCombatOutcome(fact);
                    guarded.onCombatOutcome(fact);
                }
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                fact->globalEvents.incrementAndGet()
            );
            // Keep a typed handle only for documentation/test diagnostics.
            delivery=null;
        }

        void lethal()throws Exception {
            AtomicReference<PlayerCombatResolutionService.Result> result=
                new AtomicReference<>();
            world.events().schedule(1L,()->{
                try {
                    result.set(source.resolveForTest(4151,null,1L));
                    world.domainEvents().publish(new GlobalNotice());
                } catch(Exception failure) {
                    throw new IllegalStateException(
                        "R83 legitimate PvP producer failed",failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R83 WorldPulse mismatch");
            require(result.get()!=null&&result.get().lifecycle.died,
                "R83 lethal resolution not reached");
            require(killFact.get()!=null,"R83 missing attested real kill fact");
        }

        long score() {
            return matches.get(matchId)
                .participant(attacker.id().toString())
                .scores.getOrDefault("kills",0L);
        }

        Long count() {
            CollectionLogService.ProgressSnapshot s=
                collections.snapshot(collectionId);
            return s.killCount().isPresent()
                ?Long.valueOf(s.killCount().getAsLong()):null;
        }

        void assertState(
            long primary,long secondary,long matchScore,Long logCount,
            int failureCount,int countResolverCalls
        ) {
            require(objectives.get("combat:r83-primary").progress==primary&&
                    objectives.get("combat:r83-secondary").progress==secondary,
                "R83 objective projection mismatch");
            require(score()==matchScore,"R83 match score mismatch");
            require(java.util.Objects.equals(count(),logCount),
                "R83 Collection Log projection mismatch");
            require(failures.get()==failureCount &&
                    resolverCalls.get()==countResolverCalls,
                "R83 callback failure or resolver count mismatch");
            require(!objectives.get("combat:r83-primary").claimed &&
                    matches.get(matchId).state==MatchSession.State.ACTIVE,
                "R83 audit unexpectedly claimed reward or finished match");
        }

        void phases(
            R83OfflineCombatCheckpointAudit.Phase objective,
            R83OfflineCombatCheckpointAudit.Phase match,
            R83OfflineCombatCheckpointAudit.Phase collection
        ){
            CombatOutcome fact=killFact.get();
            require(audit.phase(fact,
                    R83OfflineCombatCheckpointAudit.Consumer.OBJECTIVE)==
                    objective &&
                    audit.phase(fact,
                    R83OfflineCombatCheckpointAudit.Consumer.MATCH)==
                    match &&
                    audit.phase(fact,
                    R83OfflineCombatCheckpointAudit.Consumer.COLLECTION)==
                    collection,
                "R83 per-consumer phase mismatch");
            require(audit.recoveryDecision(fact)==
                    R83OfflineCombatCheckpointAudit
                        .RecoveryDecision.DENY_AUTOMATIC_REPLAY,
                "R83 checkpoint status authorized unsafe replay");
        }

        @Override public void close(){world.close();}
    }

    public static void main(String[] args)throws Exception {
        normalFactsHaveNoDurableRecoveryKey();
        interruptedObjectiveBindingIsUncertain();
        successfulMatchWriteCanStillBeUncertain();
        successfulCollectionWriteCanStillBeUncertain();
        terminalStopsLaterConsumers();
        identityTupleCollidesAndSyntheticSequenceIsLocal();
        consumerPhaseTransitionsFailClosed();
        unverifiedCopyDeniedBeforeCheckpoint();
        System.out.println(
            "R83_OFFLINE_COMBAT_CHECKPOINT_AUDIT_PASS "+
            "realR79SourceWorldBusAndR80R81Guards=true "+
            "actualObjectiveMatchCollectionObservers=true "+
            "perConsumerReturnedUncertainAbsent=true "+
            "noAutomaticReplayOfPartialWrites=true "+
            "ambiguousPostWriteFailurePreserved=true "+
            "quiescenceStopsLaterCheckpointEntries=true "+
            "equalFieldEventIdentityUnsafe=true "+
            "localDiagnosticSequenceNotDurable=true "+
            "forgedBusOutcomeDenied=true "+
            "noProductionWiringOrAccountChanges=true"
        );
    }

    private static void normalFactsHaveNoDurableRecoveryKey()
            throws Exception {
        try(Fixture f=new Fixture(Mode.NORMAL)) {
            f.lethal();
            f.assertState(1L,1L,2L,Long.valueOf(7L),0,1);
            f.phases(
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.RETURNED
            );
            require(f.audit.factCountForTest()==1 &&
                    f.audit.diagnosticSequence(f.killFact.get())==1L &&
                    f.replay.acceptedForTest()==2 &&
                    f.replay.deniedForTest()==2 &&
                    f.globalEvents.get()==1,
                "R83 complete attested baseline or duplicate denial failed");
        }
    }

    private static void interruptedObjectiveBindingIsUncertain()
            throws Exception {
        try(Fixture f=new Fixture(Mode.OBJECTIVE_SECOND_BINDING_THROWS)) {
            f.lethal();
            f.assertState(1L,0L,2L,Long.valueOf(7L),1,1);
            f.phases(
                R83OfflineCombatCheckpointAudit.Phase.UNCERTAIN,
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.RETURNED
            );
            require(f.objectiveChecks.get()==2 &&
                    f.replay.deniedForTest()==2,
                "R83 restarted interrupted objective despite R81");
        }
    }

    private static void successfulMatchWriteCanStillBeUncertain()
            throws Exception {
        try(Fixture f=new Fixture(Mode.MATCH_AFTER_WRITE_THROWS)) {
            f.lethal();
            f.assertState(1L,1L,2L,Long.valueOf(7L),1,1);
            f.phases(
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.UNCERTAIN,
                R83OfflineCombatCheckpointAudit.Phase.RETURNED
            );
            require(f.score()==2L&&f.replay.deniedForTest()==2,
                "R83 repeated already committed match mutation");
        }
    }

    private static void successfulCollectionWriteCanStillBeUncertain()
            throws Exception {
        try(Fixture f=new Fixture(Mode.COLLECTION_AFTER_WRITE_THROWS)) {
            f.lethal();
            f.assertState(1L,1L,2L,Long.valueOf(7L),1,1);
            f.phases(
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.UNCERTAIN
            );
            require(f.replay.deniedForTest()==2,
                "R83 retried a completed Collection Log CAS");
        }
    }

    private static void terminalStopsLaterConsumers()throws Exception {
        try(Fixture f=new Fixture(Mode.TERMINAL_AFTER_OBJECTIVE)) {
            f.lethal();
            f.assertState(1L,1L,0L,null,0,0);
            f.phases(
                R83OfflineCombatCheckpointAudit.Phase.RETURNED,
                R83OfflineCombatCheckpointAudit.Phase.ABSENT,
                R83OfflineCombatCheckpointAudit.Phase.ABSENT
            );
            require(f.terminal.isQuiesced(
                        f.attacker,f.attackerGeneration)&&
                    f.owner.deniedFanoutCountForTest()>=1 &&
                    f.globalEvents.get()==1,
                "R83 post-terminal projections were not refused");
        }
    }

    private static void identityTupleCollidesAndSyntheticSequenceIsLocal(){
        CombatOutcome a=new CombatOutcome(
            "attacker","victim",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"TEST_SOURCE"
        );
        CombatOutcome b=new CombatOutcome(
            "attacker","victim",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"TEST_SOURCE"
        );
        R83OfflineCombatCheckpointAudit audit=
            new R83OfflineCombatCheckpointAudit();
        require(a!=b&&R83OfflineCombatCheckpointAudit.candidateTuple(a)
                .equals(R83OfflineCombatCheckpointAudit.candidateTuple(b)),
            "R83 equal-field tuple collision reproduction failed");
        long first=audit.begin(
            a,R83OfflineCombatCheckpointAudit.Consumer.OBJECTIVE
        );
        long second=audit.begin(
            b,R83OfflineCombatCheckpointAudit.Consumer.OBJECTIVE
        );
        require(first==1L&&second==2L&&
                audit.factCountForTest()==2&&
                audit.recoveryDecision(a)==
                    R83OfflineCombatCheckpointAudit
                        .RecoveryDecision.DENY_AUTOMATIC_REPLAY,
            "R83 local sequence falsely coalesced events or enabled replay");
        R83OfflineCombatCheckpointAudit recreated=
            new R83OfflineCombatCheckpointAudit();
        require(recreated.diagnosticSequence(a)==0L &&
                recreated.begin(
                    a,R83OfflineCombatCheckpointAudit.Consumer.OBJECTIVE
                )==1L,
            "R83 diagnostic sequence incorrectly survived new audit");
    }

    private static void consumerPhaseTransitionsFailClosed(){
        CombatOutcome fact=new CombatOutcome(
            "attacker","victim",CombatOutcomeType.PLAYER_KILL,
            CombatOutcomeContext.PLAYER_PVP,1L,"TEST"
        );
        R83OfflineCombatCheckpointAudit audit=
            new R83OfflineCombatCheckpointAudit();
        R83OfflineCombatCheckpointAudit.Consumer consumer=
            R83OfflineCombatCheckpointAudit.Consumer.MATCH;
        require(audit.phase(fact,consumer)==
                R83OfflineCombatCheckpointAudit.Phase.ABSENT,
            "R83 unobserved fact had checkpoint");
        require(audit.begin(fact,consumer)==1L,
            "R83 first begin failed");
        expectDenied(()->audit.begin(fact,consumer),"repeated consumer entry");
        audit.uncertain(fact,consumer);
        expectDenied(()->audit.returned(fact,consumer),
            "invalid phase transition");
        expectDenied(()->audit.begin(fact,consumer),"repeated consumer entry");
        require(audit.phase(fact,consumer)==
                R83OfflineCombatCheckpointAudit.Phase.UNCERTAIN &&
                audit.deniedRepeatCountForTest()==2,
            "R83 failed to keep consumer state terminally uncertain");
    }

    private static void unverifiedCopyDeniedBeforeCheckpoint()
            throws Exception {
        try(Fixture f=new Fixture(Mode.NORMAL)) {
            AtomicInteger denied=new AtomicInteger();
            f.world.events().schedule(1L,()->{
                CombatOutcome forged=new CombatOutcome(
                    f.attacker.id().toString(),f.target.id().toString(),
                    CombatOutcomeType.PLAYER_KILL,
                    CombatOutcomeContext.PLAYER_PVP,1L,
                    PlayerLifecycleService.AUTHORITY
                );
                try {
                    f.world.domainEvents().publish(forged);
                    throw new AssertionError(
                        "R83 forged matching-string event was admitted"
                    );
                } catch(java.io.IOException expected) {
                    require(expected.getMessage().contains(
                        "R79_PVP_DENIED unverified combat fact"
                    ),"R83 unexpected forged event rejection");
                    denied.incrementAndGet();
                } catch(Exception error) {
                    throw new IllegalStateException(error);
                }
            });
            require(f.world.observePulse(System.currentTimeMillis())==1L&&
                    denied.get()==1&&f.audit.factCountForTest()==0,
                "R83 unverified source consumed a checkpoint");
        }
    }

    private static void expectDenied(Runnable test,String message){
        try {
            test.run();
            throw new AssertionError("R83 expected refusal: "+message);
        } catch(IllegalStateException refusal) {
            require(refusal.getMessage().contains(message),
                "R83 unexpected refusal "+refusal);
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private R83OfflineCombatCheckpointAuditTest(){}
}

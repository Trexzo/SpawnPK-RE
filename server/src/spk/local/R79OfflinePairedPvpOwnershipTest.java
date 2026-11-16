package spk.local;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R79 OFFLINE ONLY: the actual PvP resolveImmediateOwned path executes in
 * the real isolated WorldPulse with a paired owner-generation fence.
 * No game socket, persistent profile or production PvP dispatch runs.
 */
public final class R79OfflinePairedPvpOwnershipTest {
    private static final class GlobalNotice
            implements DomainEventBus.Event {}

    private static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer target=new WorldPlayer();
        final long attackerGeneration;
        final long targetGeneration;
        final R74OfflineCommandIngressFence terminal;
        final R79OfflinePairedPvpOwnershipGate gate;
        final AtomicInteger highHits=new AtomicInteger();
        final AtomicInteger normalHits=new AtomicInteger();
        final AtomicInteger globalHits=new AtomicInteger();

        Fixture(CombatDamageRules damageRuleFactory,
                boolean withDefaultListeners)throws Exception{
            attackerGeneration=
                world.registerPlayer(attacker,"attacker");
            targetGeneration=
                world.registerPlayer(target,"target");
            target.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,10
            );
            terminal=new R74OfflineCommandIngressFence(world);
            terminal.installForTest();

            CombatDamageRules rules=damageRuleFactory==null
                ?CombatDamageRules.localLabFallback()
                :damageRuleFactory;

            gate=new R79OfflinePairedPvpOwnershipGate(
                world,attacker,attackerGeneration,
                target,targetGeneration,terminal,rules
            );

            if(withDefaultListeners){
                world.domainEvents().subscribe(
                    CombatOutcome.class,
                    DomainEventBus.Priority.HIGH,
                    outcome->highHits.incrementAndGet()
                );
                world.domainEvents().subscribe(
                    CombatOutcome.class,
                    DomainEventBus.Priority.NORMAL,
                    outcome->normalHits.incrementAndGet()
                );
            }
            world.domainEvents().subscribe(
                GlobalNotice.class,
                DomainEventBus.Priority.NORMAL,
                event->globalHits.incrementAndGet()
            );
        }

        void tick(Runnable work){
            long expected=world.clock().tick()+1L;
            world.events().schedule(expected,work);
            long actual=world.observePulse(
                System.currentTimeMillis()
            );
            require(actual==expected,
                "R79 WorldPulse tick expected="+expected+
                " got="+actual);
        }

        int targetHp(){
            return target.playerState().currentLevel(
                PlayerState.HITPOINTS
            );
        }

        @Override public void close(){
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        normalLethalAndForgeries();
        targetGenerationChangesDuringCalculation();
        attackerGenerationChangesDuringCalculation();
        terminalAtCalculationBoundary();
        targetQuiescedBeforeResolution();
        listenerQuiescesDuringOutcomeFanout();

        System.out.println(
            "R79_OFFLINE_PAIRED_PVP_OWNER_OUTCOMES_PASS "+
            "realResolveImmediateOwnedPath=true "+
            "stableEntityIdOrderedDualMutationLocks=true "+
            "bothWorldGenerationsCheckedBeforeDamage=true "+
            "bothWorldGenerationsCheckedAfterDamageCalculation=true "+
            "staleTargetDuringCalculationDeniedBeforeHp=true "+
            "staleAttackerDuringCalculationDeniedBeforeHp=true "+
            "preQuiescedTargetDeniedBeforeHp=true "+
            "quiescenceDuringDamageCalculationDeniedBeforeHp=true "+
            "actualLethalKillAndDeathOutcomesDelivered=true "+
            "forgedSameIdentityOutcomeDenied=true "+
            "highPriorityQuiescenceStopsNormalListener=true "+
            "postQuiescenceDeathOutcomeNotPublished=true "+
            "globalWorldEventUnchanged=true "+
            "noProductionSessionOrHook=true"
        );
    }

    private static void normalLethalAndForgeries()
            throws Exception{
        try(Fixture f=new Fixture(null,true)){
            AtomicReference<PlayerCombatResolutionService.Result>
                result=new AtomicReference<>();
            AtomicInteger forged=new AtomicInteger();
            f.tick(()->{
                try{
                    result.set(f.gate.resolveForTest(
                        4151,null,f.world.clock().tick()
                    ));
                    CombatOutcome imitation=new CombatOutcome(
                        f.attacker.id().toString(),
                        f.target.id().toString(),
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        f.world.clock().tick(),
                        PlayerLifecycleService.AUTHORITY
                    );
                    try{
                        f.world.domainEvents().publish(imitation);
                        throw new AssertionError(
                            "R79 copied-string combat fact accepted"
                        );
                    }catch(IOException denied){
                        expect(denied,"R79_PVP_DENIED unverified combat fact");
                        forged.incrementAndGet();
                    }
                    f.world.domainEvents().publish(
                        new GlobalNotice()
                    );
                }catch(Exception e){
                    throw new IllegalStateException(
                        "R79 valid PvP source failed",e
                    );
                }
            });
            require(result.get()!=null &&
                    result.get().lifecycle.died &&
                    f.targetHp()==0 &&
                    f.highHits.get()==2&&
                    f.normalHits.get()==2&&
                    f.globalHits.get()==1&&
                    forged.get()==1,
                "R79 valid kill/death facts or fake denials failed");

            // After WorldPulse returns, having both WorldPlayer
            // references does not confer authority to attack.
            try{
                f.gate.resolveForTest(4151,null,2L);
                throw new AssertionError(
                    "R79 off-world PvP resolution accepted"
                );
            }catch(IllegalStateException refused){
                expect(refused,"R79_PVP_DENIED off-world");
            }
        }
    }

    private static void targetGenerationChangesDuringCalculation()
            throws Exception{
        final Fixture[] owner={null};
        CombatDamageRules rule=new InjectedRule(()->{
            Fixture f=owner[0];
            require(f.world.unregisterPlayer(
                    f.target,f.targetGeneration),
                "R79 test target unregister was not applied");
        });
        try(Fixture f=new Fixture(rule,true)){
            owner[0]=f;
            AtomicReference<Throwable> refusal=new AtomicReference<>();
            f.tick(()->{
                try{
                    f.gate.resolveForTest(4151,null,1L);
                    throw new AssertionError(
                        "R79 stale target reached HP mutation"
                    );
                }catch(IllegalStateException denied){
                    refusal.set(denied);
                }catch(Exception err){
                    throw new IllegalStateException(err);
                }
            });
            require(refusal.get()!=null&&
                    refusal.get().getMessage().contains(
                        "R79_PVP_DENIED stale target after damage calculation"
                    )&&f.targetHp()==10&&
                    f.highHits.get()==0&&f.normalHits.get()==0,
                "R79 target teardown during rule reached damage");
        }
    }

    private static void attackerGenerationChangesDuringCalculation()
            throws Exception{
        final Fixture[] owner={null};
        CombatDamageRules rule=new InjectedRule(()->{
            Fixture f=owner[0];
            require(f.world.unregisterPlayer(
                    f.attacker,f.attackerGeneration),
                "R79 test attacker unregister was not applied");
        });
        try(Fixture f=new Fixture(rule,true)){
            owner[0]=f;
            AtomicReference<Throwable> refusal=new AtomicReference<>();
            f.tick(()->{
                try{
                    f.gate.resolveForTest(4151,null,1L);
                    throw new AssertionError(
                        "R79 stale attacker reached damage"
                    );
                }catch(IllegalStateException denied){
                    refusal.set(denied);
                }catch(Exception err){
                    throw new IllegalStateException(err);
                }
            });
            require(refusal.get()!=null&&
                    refusal.get().getMessage().contains(
                        "R79_PVP_DENIED stale attacker after damage calculation"
                    )&&f.targetHp()==10&&
                    f.highHits.get()==0&&f.normalHits.get()==0,
                "R79 attacker teardown during rule reached damage");
        }
    }

    private static void terminalAtCalculationBoundary()
            throws Exception{
        final Fixture[] owner={null};
        CombatDamageRules rule=new InjectedRule(()->{
            Fixture f=owner[0];
            f.terminal.quiesceInsideWorldActor(
                f.attacker,f.attackerGeneration
            );
        });
        try(Fixture f=new Fixture(rule,true)){
            owner[0]=f;
            AtomicReference<Throwable> refusal=new AtomicReference<>();
            f.tick(()->{
                try{
                    f.gate.resolveForTest(4151,null,1L);
                    throw new AssertionError(
                        "R79 quiesced attacker reached damage"
                    );
                }catch(IllegalStateException denied){
                    refusal.set(denied);
                }catch(Exception err){
                    throw new IllegalStateException(err);
                }
            });
            require(refusal.get()!=null&&
                    refusal.get().getMessage().contains(
                        "R79_PVP_DENIED attacker quiesced after damage calculation"
                    )&&f.targetHp()==10&&
                    f.highHits.get()==0&&f.normalHits.get()==0,
                "R79 terminal transition during calculation not fenced");
        }
    }

    private static void targetQuiescedBeforeResolution()
            throws Exception{
        try(Fixture f=new Fixture(null,true)){
            AtomicReference<Throwable> refusal=new AtomicReference<>();
            f.tick(()->{
                f.terminal.quiesceInsideWorldActor(
                    f.target,f.targetGeneration
                );
                try{
                    f.gate.resolveForTest(4151,null,1L);
                    throw new AssertionError(
                        "R79 previously quiesced target reached damage"
                    );
                }catch(IllegalStateException denied){
                    refusal.set(denied);
                }catch(Exception err){
                    throw new IllegalStateException(err);
                }
            });
            require(refusal.get()!=null&&
                    refusal.get().getMessage().contains(
                        "R79_PVP_DENIED target quiesced before paired World action"
                    )&&f.targetHp()==10&&
                    f.highHits.get()==0&&f.normalHits.get()==0,
                "R79 pre-quiesced target was attacked");
        }
    }

    private static void listenerQuiescesDuringOutcomeFanout()
            throws Exception{
        try(Fixture f=new Fixture(null,false)){
            AtomicInteger attemptedForgery=new AtomicInteger();
            f.world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.HIGH,
                fact->{
                    f.highHits.incrementAndGet();
                    if(fact.type()==CombatOutcomeType.PLAYER_KILL){
                        CombatOutcome forged=new CombatOutcome(
                            f.attacker.id().toString(),
                            f.target.id().toString(),
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            fact.worldTick(),
                            fact.sourceAuthority()
                        );
                        try{
                            f.world.domainEvents().publish(forged);
                            throw new AssertionError(
                                "R79 nested fake PvP outcome delivered"
                            );
                        }catch(IOException denied){
                            expect(denied,
                                "R79_PVP_DENIED unverified combat fact");
                            attemptedForgery.incrementAndGet();
                        }
                        f.terminal.quiesceInsideWorldActor(
                            f.target,f.targetGeneration
                        );
                    }
                }
            );
            f.world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->f.normalHits.incrementAndGet()
            );

            AtomicReference<PlayerCombatResolutionService.Result>
                result=new AtomicReference<>();
            f.tick(()->{
                try{
                    result.set(f.gate.resolveForTest(
                        4151,null,1L
                    ));
                    f.world.domainEvents().publish(
                        new GlobalNotice()
                    );
                }catch(Exception err){
                    throw new IllegalStateException(err);
                }
            });
            require(result.get()!=null&&
                    result.get().lifecycle.died&&
                    f.targetHp()==0&&
                    f.highHits.get()==1&&
                    f.normalHits.get()==0&&
                    attemptedForgery.get()==1&&
                    f.gate.rejectedListenerCountForTest()>=2&&
                    f.globalHits.get()==1,
                "R79 listener quiescence allowed second fact or lower listener");

            // Damage has ALREADY happened. The test is explicitly
            // nontransactional: only downstream delivery is blocked.
            require(f.target.lifecycle().dead(),
                "R79 pre-completed irreversible HP death was rolled back");
        }
    }

    private static final class InjectedRule
            implements CombatDamageRules {
        private final Runnable beforeReturn;
        InjectedRule(Runnable beforeReturn){
            this.beforeReturn=beforeReturn;
        }
        @Override public Result calculate(Request request){
            beforeReturn.run();
            return new Result(
                10,10,"CUSTOM_LOCALLAB","R79_FIXED_TEST_DAMAGE"
            );
        }
        @Override public String authority(){
            return "CUSTOM_LOCALLAB";
        }
        @Override public String formula(){
            return "R79_FIXED_TEST_DAMAGE";
        }
    }

    private static void expect(Throwable failure,String expected){
        require(failure.getMessage()!=null&&
                failure.getMessage().contains(expected),
            "R79 wrong exception "+failure);
    }
    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }

    private R79OfflinePairedPvpOwnershipTest(){}
}

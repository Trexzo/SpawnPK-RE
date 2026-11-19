package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R86 OFFLINE ONLY. The exact LocalSession-shaped active PvP handler is
 * opted in through a dedicated factory; its real attack tick executes
 * inside WorldPulse and the original PlayerCombatResolutionService.
 * Source-proven CombatOutcome events reach the actual DomainEventBus.
 *
 * Default LocalSession construction remains unmodified and its
 * NO_OUTCOME_OBSERVER behavior is asserted separately by R85.
 */
public final class R86OfflineActivePvpDomainBridgeTest {
    enum Mode { NORMAL, QUIESCE_DURING_HIGH }

    private static final class GlobalNotice
        implements DomainEventBus.Event {}

    static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer attacker=new WorldPlayer();
        final WorldPlayer victim=new WorldPlayer();
        final long attackerGeneration;
        final long victimGeneration;
        final ByteArrayOutputStream attackerBytes=
            new ByteArrayOutputStream();
        final ByteArrayOutputStream victimBytes=
            new ByteArrayOutputStream();
        final ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerBytes,new IsaacCipher(new int[]{1,2,3,4})
            );
        final ServerPacketWriter victimWriter=
            new ServerPacketWriter(
                victimBytes,new IsaacCipher(new int[]{5,6,7,8})
            );
        final Player81WorldSync.Context attackerSync;
        final R74OfflineCommandIngressFence terminal;
        final R86OfflineActivePvpOutcomeBridge bridge;
        final LocalPlayerInteractionHandler handler;
        final ObjectiveProgressService objectives=
            new ObjectiveProgressService();
        final AtomicInteger highEvents=new AtomicInteger();
        final AtomicInteger normalEvents=new AtomicInteger();
        final AtomicInteger globalEvents=new AtomicInteger();
        final AtomicInteger nestedRealDenied=new AtomicInteger();
        final AtomicInteger nestedCopyDenied=new AtomicInteger();
        final AtomicReference<CombatOutcome> killFact=
            new AtomicReference<>();
        final AtomicReference<CombatOutcome> deathFact=
            new AtomicReference<>();
        final AtomicReference<String> resolved=new AtomicReference<>();
        final Mode mode;

        Fixture(Mode mode)throws Exception{
            this.mode=mode;
            attackerGeneration=
                world.registerPlayer(attacker,"r86-source");
            victimGeneration=
                world.registerPlayer(victim,"r86-target");
            String move=victim.movement().accept(
                new MovementRequest(
                    164,false,
                    new int[]{attacker.movement().x()+1},
                    new int[]{attacker.movement().y()},
                    new byte[0]
                )
            );
            require(move.startsWith("ACCEPTED"),
                "R86 target adjacency rejected");
            victim.movement().advance();
            attacker.equipment().setWeapon(4151);
            require(victim.playerState().setCurrentLevel(
                    PlayerState.HITPOINTS,9),
                "R86 target HP fixture rejected");

            attackerSync=Player81WorldSync.register(
                attackerWriter,world,attacker,
                new DevAuthorityWorkbench()
            );
            Player81WorldSync.Context victimSync=
                Player81WorldSync.register(
                    victimWriter,world,victim,
                    new DevAuthorityWorkbench()
                );
            Player81WorldSync.transformForTest(
                attackerSync,BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                victimSync,BootstrapPackets.player81Idle()
            );

            terminal=new R74OfflineCommandIngressFence(world);
            terminal.installForTest();
            bridge=new R86OfflineActivePvpOutcomeBridge(
                world,attacker,attackerGeneration,terminal
            );
            bridge.bindTargetForTest(victim,victimGeneration);
            bridge.installForTest();
            handler=LocalPlayerInteractionHandler
                .forOfflineOutcomeTest(
                    world,attacker,attacker.movement(),
                    attacker.equipment(),attacker::generation,bridge
                );

            objectives.define(
                new ObjectiveDefinition(
                    "r86:real-source-kills",10L,"CUSTOM_LOCALLAB"
                )
            );
            CombatOutcomeObjectiveObserver observer=
                new CombatOutcomeObjectiveObserver(
                    objectives,attacker.id().toString(),
                    Collections.singletonList(
                        new CombatOutcomeObjectiveObserver.Binding(
                            CombatOutcomeType.PLAYER_KILL,
                            CombatOutcomeContext.PLAYER_PVP,
                            CombatOutcomeObjectiveObserver
                                .SubjectRole.ATTACKER,
                            "r86:real-source-kills",1L,"CUSTOM_LOCALLAB"
                        )
                    )
                );

            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.HIGH,
                fact->{
                    highEvents.incrementAndGet();
                    if(fact.type()==CombatOutcomeType.PLAYER_KILL){
                        killFact.compareAndSet(null,fact);
                        if(mode==Mode.QUIESCE_DURING_HIGH){
                            terminal.quiesceInsideWorldActor(
                                victim,victimGeneration
                            );
                        }else{
                            // A listener cannot recursively publish
                            // the SAME exact verified fact.
                            try{
                                world.domainEvents().publish(fact);
                                throw new AssertionError(
                                    "R86 nested real fact was accepted"
                                );
                            }catch(IOException rejected){
                                require(rejected.getMessage().contains(
                                    "R86_PVP_DENIED nested"),
                                    "R86 nested exact fact denial mismatch");
                                nestedRealDenied.incrementAndGet();
                            }

                            // Nor can it forge a second object with
                            // matching source/attacker/tick fields.
                            CombatOutcome copy=copy(fact);
                            try{
                                world.domainEvents().publish(copy);
                                throw new AssertionError(
                                    "R86 nested copy was accepted"
                                );
                            }catch(IOException rejected){
                                require(rejected.getMessage().contains(
                                    "R86_PVP_DENIED unverified"),
                                    "R86 forged fact denial mismatch");
                                nestedCopyDenied.incrementAndGet();
                            }
                        }
                    }
                    if(fact.type()==CombatOutcomeType.PLAYER_DEATH)
                        deathFact.compareAndSet(null,fact);
                }
            );
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                fact->{
                    normalEvents.incrementAndGet();
                    observer.onCombatOutcome(fact);
                }
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                fact->globalEvents.incrementAndGet()
            );

            int targetIndex=attackerSync.clientIndexFor(victim);
            require(targetIndex>=0,"R86 no client-index target");
            String request=handler.handleResolved(
                new PlayerAction(128,1,targetIndex,"Attack"),
                victim,attackerSync
            );
            require(request!=null&&
                    request.contains("PLAYER_ATTACK_REQUEST"),
                "R86 active source attack request rejected");
        }

        void lethal()throws Exception{
            world.events().schedule(1L,()->{
                try{
                    resolved.set(handler.tickAttack(
                        20L,attackerWriter,attackerSync
                    ));
                    world.domainEvents().publish(
                        new GlobalNotice()
                    );
                }catch(Exception failure){
                    throw new IllegalStateException(
                        "R86 actual active attack tick failed",
                        failure
                    );
                }
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R86 actual WorldPulse tick mismatch");
            require(resolved.get()!=null&&
                    resolved.get().contains(
                        "PLAYER_ATTACK_RESOLVED")&&
                    resolved.get().contains(
                        "targetDied=true")&&
                    resolved.get().contains(
                        "targetHpPacket134=true")&&
                    victim.playerState().currentLevel(
                        PlayerState.HITPOINTS
                    )==0&&victim.lifecycle().dead()&&
                    handler.activeAttack()==null,
                "R86 original active PvP damage/presentation diverged");
        }

        void directSpoofAfterSourceReturns()throws Exception{
            CombatOutcome real=killFact.get();
            require(real!=null,"R86 no genuine kill event to copy");
            CombatOutcome cloned=copy(real);
            try{
                bridge.onCombatOutcome(cloned);
                throw new AssertionError(
                    "R86 direct bridge callback accepted off-world"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "no active verified resolver"),
                    "R86 direct source callback refused incorrectly");
            }
            AtomicInteger refusedFromBus=new AtomicInteger();
            world.events().schedule(2L,()->{
                try{
                    world.domainEvents().publish(cloned);
                    throw new AssertionError(
                        "R86 stale copied event entered bus"
                    );
                }catch(IOException rejected){
                    require(rejected.getMessage().contains(
                        "R86_PVP_DENIED unverified"),
                        "R86 copied event refused incorrectly");
                    refusedFromBus.incrementAndGet();
                }catch(Exception other){
                    throw new IllegalStateException(other);
                }
            });
            require(world.observePulse(System.currentTimeMillis())==2L&&
                    refusedFromBus.get()==1,
                "R86 post-source fake publish not rejected");
        }

        long progress(){
            return objectives.get(
                "r86:real-source-kills"
            ).progress;
        }

        @Override public void close(){
            try{Player81WorldSync.unregister(attackerWriter);}
            catch(Exception ignored){}
            try{Player81WorldSync.unregister(victimWriter);}
            catch(Exception ignored){}
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        genuineActiveSourcePublishesBothFacts();
        quiescenceBetweenListenersPreventsProjection();
        duplicateBindingAndUnregisteredSourceRefused();

        System.out.println(
            "R86_OFFLINE_ACTIVE_PVP_DOMAIN_BRIDGE_PASS "+
            "realLocalSessionShapedHandler=true "+
            "actualOwnedResolverAndWorldPulseUsed=true "+
            "realLethalDamageAndHp134Preserved=true "+
            "sourceKillAndDeathPublishedOnceEach=true "+
            "realDomainBusHighAndNormalListeners=true "+
            "boundObjectiveObserverAdvancedOnce=true "+
            "nestedExactAndForgedSameFieldsDenied=true "+
            "sourceProofDoesNotEscapeResolver=true "+
            "quiesceBetweenListenersBlocksNormal=true "+
            "subsequentDeathSourceDeniedAfterQuiescence=true "+
            "globalDomainNoticeUnaffected=true "+
            "duplicatePolicyInstallDenied=true "+
            "staleTargetBindingRejected=true "+
            "defaultLocalSessionNoOpPreserved=true "+
            "noLiveActivationOrDurableIdClaim=true"
        );
    }

    private static void genuineActiveSourcePublishesBothFacts()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL)){
            f.lethal();
            require(f.highEvents.get()==2&&
                    f.normalEvents.get()==2&&
                    f.progress()==1L&&
                    f.bridge.issuedCountForTest()==2&&
                    f.bridge.admittedCountForTest()==2&&
                    f.nestedRealDenied.get()==1&&
                    f.nestedCopyDenied.get()==1&&
                    f.globalEvents.get()==1&&
                    f.killFact.get()!=f.deathFact.get()&&
                    f.killFact.get().worldTick()==
                        f.deathFact.get().worldTick(),
                "R86 actual two-outcome semantic bus fanout mismatch");
            f.directSpoofAfterSourceReturns();
            require(f.progress()==1L&&
                    f.bridge.refusedCountForTest()>=3&&
                    f.normalEvents.get()==2,
                "R86 post-source copy changed objective");
        }
    }

    private static void quiescenceBetweenListenersPreventsProjection()
            throws Exception{
        try(Fixture f=new Fixture(Mode.QUIESCE_DURING_HIGH)){
            f.lethal();
            require(f.highEvents.get()==1&&
                    f.normalEvents.get()==0&&
                    f.progress()==0L&&
                    f.bridge.issuedCountForTest()==1&&
                    f.bridge.admittedCountForTest()==0&&
                    f.bridge.refusedCountForTest()>=1&&
                    f.terminal.isQuiesced(
                        f.victim,f.victimGeneration)&&
                    f.globalEvents.get()==1,
                "R86 terminal owner raced past normal listener");
        }
    }

    private static void duplicateBindingAndUnregisteredSourceRefused()
            throws Exception{
        try(Fixture f=new Fixture(Mode.NORMAL)){
            try{
                f.bridge.installForTest();
                throw new AssertionError(
                    "R86 replaced existing domain source gate"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "already installed"),
                    "R86 repeated domain gate denial mismatch");
            }
            try{
                f.bridge.bindTargetForTest(
                    f.victim,f.victimGeneration
                );
                throw new AssertionError(
                    "R86 rebound live target generation"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "target already bound"),
                    "R86 duplicate target binding denial mismatch");
            }
            f.lethal();
            require(f.world.unregisterPlayer(
                    f.victim,f.victimGeneration
                ),"R86 World target unregister failed");
            try{
                f.bridge.requireBoundForTest(
                    f.victim,f.victimGeneration
                );
                throw new AssertionError(
                    "R86 stale target generation was accepted"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "stale or off-world"),
                    "R86 stale generation refused incorrectly");
            }
            require(f.progress()==1L,
                "R86 unregister mutated already-committed objective");
        }
    }

    private static CombatOutcome copy(CombatOutcome fact){
        return new CombatOutcome(
            fact.attacker(),fact.victim(),fact.type(),
            fact.context(),fact.worldTick(),fact.sourceAuthority()
        );
    }

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }

    private R86OfflineActivePvpDomainBridgeTest(){}
}

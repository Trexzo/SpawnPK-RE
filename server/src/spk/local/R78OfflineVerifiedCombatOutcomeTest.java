package spk.local;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R78 real LocalLab PvM-kill producer and actual WorldPulse/DomainEventBus
 * listener acceptance. No live LocalSession, socket, game account or
 * production hook is touched. Every scheduled fixture runs synchronously
 * on an isolated World actor's compatibility WorldPulse.
 */
public final class R78OfflineVerifiedCombatOutcomeTest {
    private static final class GlobalNotice
            implements DomainEventBus.Event {}

    public static void main(String[] args)throws Exception{
        String prior=System.getProperty("spk.local.r6MortalFixture");
        System.setProperty("spk.local.r6MortalFixture","true");
        try{
            runIsolated();
        }finally{
            if(prior==null)
                System.clearProperty("spk.local.r6MortalFixture");
            else
                System.setProperty("spk.local.r6MortalFixture",prior);
        }
    }

    private static void runIsolated()throws Exception{
        try(World world=World.isolatedForTest(60_000L)){
            WorldPlayer owner=new WorldPlayer();
            long generation=world.registerPlayer(owner,"opensrc");
            WorldPlayer other=new WorldPlayer();
            long otherGeneration=world.registerPlayer(other,"other");

            R74OfflineCommandIngressFence commands=
                new R74OfflineCommandIngressFence(world);
            commands.installForTest();
            R78OfflineVerifiedCombatFactBridge bridge=
                new R78OfflineVerifiedCombatFactBridge(
                    world,owner,generation,commands
                );
            LocalLabPvmKillOutcomeProducer producer=
                bridge.installAndCreateProducerForTest();
            try{
                bridge.installAndCreateProducerForTest();
                throw new AssertionError(
                    "R78 accepted second verified producer installation"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "duplicate bridge installation"
                ),"R78 duplicate bridge refusal unexpected");
            }

            AtomicInteger high=new AtomicInteger();
            AtomicInteger normal=new AtomicInteger();
            AtomicInteger global=new AtomicInteger();
            AtomicInteger rawDenied=new AtomicInteger();
            AtomicInteger nestedDenied=new AtomicInteger();
            AtomicInteger lateDenied=new AtomicInteger();
            AtomicInteger releasedDenied=new AtomicInteger();
            AtomicReference<String> firstResult=new AtomicReference<>();
            AtomicReference<String> midResult=new AtomicReference<>();

            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.HIGH,
                outcome->{
                    high.incrementAndGet();
                    if(outcome.victim().contains("scene=101")){
                        try{
                            world.domainEvents().publish(
                                new CombatOutcome(
                                    owner.id().toString(),
                                    outcome.victim(),
                                    CombatOutcomeType.NPC_KILL,
                                    CombatOutcomeContext.NPC_PVM,
                                    outcome.worldTick(),
                                    LocalLabPvmKillOutcomeProducer.AUTHORITY
                                )
                            );
                            throw new AssertionError(
                                "R78 spoofed nested combat fact accepted"
                            );
                        }catch(IOException denied){
                            require(denied.getMessage().contains(
                                "R78_COMBAT_DENIED unverified combat fact"
                            ),"R78 wrong nested denial "+denied);
                            nestedDenied.incrementAndGet();
                        }
                        commands.quiesceInsideWorldActor(
                            owner,generation
                        );
                    }
                }
            );
            world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.NORMAL,
                outcome->normal.incrementAndGet()
            );
            world.domainEvents().subscribe(
                GlobalNotice.class,DomainEventBus.Priority.NORMAL,
                event->global.incrementAndGet()
            );

            // Tick 1 runs the actual PvM death producer, then the real
            // DomainEventBus priority listener chain. A separately
            // constructed raw fact with copied attacker/source fields
            // must not gain proof just from matching string identity.
            world.events().schedule(1L,()->{
                firstResult.set(producer.publishCompletedDeath(
                    dead(100),true,owner,1L
                ));
                try{
                    world.domainEvents().publish(
                        new CombatOutcome(
                            owner.id().toString(),"locallab:npc:def=1:scene=999",
                            CombatOutcomeType.NPC_KILL,
                            CombatOutcomeContext.NPC_PVM,1L,
                            LocalLabPvmKillOutcomeProducer.AUTHORITY
                        )
                    );
                    throw new AssertionError(
                        "R78 direct unverified combat fact accepted"
                    );
                }catch(IOException denied){
                    require(denied.getMessage().contains(
                        "R78_COMBAT_DENIED unverified combat fact"
                    ),"R78 wrong raw-fact rejection "+denied);
                    rawDenied.incrementAndGet();
                }catch(Exception failure){
                    throw new IllegalStateException(failure);
                }
                publishGlobal(world);
            });
            require(world.observePulse(System.currentTimeMillis())==1L,
                "R78 first World tick mismatch");
            require(firstResult.get()!=null &&
                    firstResult.get().contains(
                        "delivery=WORLD_DOMAIN_BUS") &&
                    high.get()==1&&normal.get()==1&&global.get()==1&&
                    rawDenied.get()==1&&
                    producer.publishedSceneCountForTest()==1,
                "R78 verified first PvM source and listener baseline failed");

            // Tick 2: the real producer first verifies the owning
            // World generation, emits one validated fact and runs the
            // HIGH listener. That listener makes the player terminal
            // *and* attempts nested spoofed publication. NORMAL must
            // be refused before it mutates anything.
            world.events().schedule(2L,()->{
                midResult.set(producer.publishCompletedDeath(
                    dead(101),true,owner,2L
                ));
                publishGlobal(world);
            });

            // This is a legacy untagged source scheduled *before* the
            // owner quiesces. Tick 3's producer preflight must still
            // reject it without consuming its death-scene ordinal.
            world.events().schedule(3L,()->{
                try{
                    producer.publishCompletedDeath(
                        dead(102),true,owner,3L
                    );
                    throw new AssertionError(
                        "R78 post-quiescence PvM death emitted"
                    );
                }catch(IllegalStateException denied){
                    require(denied.getMessage().contains(
                        "R78_COMBAT_DENIED owner quiesced before death"
                    ),"R78 wrong post-final refusal "+denied);
                    lateDenied.incrementAndGet();
                }
                publishGlobal(world);
            });
            require(world.observePulse(System.currentTimeMillis())==2L,
                "R78 second World tick mismatch");
            require(midResult.get()!=null&&
                    midResult.get().contains(
                        "OBSERVER_FAILED_ISOLATED")&&
                    high.get()==2&&normal.get()==1&&
                    nestedDenied.get()==1&&
                    commands.isQuiesced(owner,generation)&&
                    producer.publishedSceneCountForTest()==2&&
                    global.get()==2,
                "R78 high listener quiescence leaked to normal listener");

            require(world.observePulse(System.currentTimeMillis())==3L,
                "R78 third World tick mismatch");
            require(lateDenied.get()==1&&
                    high.get()==2&&normal.get()==1&&
                    producer.publishedSceneCountForTest()==2&&
                    global.get()==3,
                "R78 stale PvM producer consumed scene after quiescence");

            // Off-world invocation is explicitly refused even for the
            // same World actor; merely holding an attacker reference
            // cannot bypass the actual World execution context.
            try{
                producer.publishCompletedDeath(
                    dead(105),true,owner,3L
                );
                throw new AssertionError(
                    "R78 off-world attack fact accepted"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "R78_COMBAT_DENIED unbound or off-world attacker"
                ),"R78 wrong off-world refusal "+denied);
            }
            require(producer.publishedSceneCountForTest()==2,
                "R78 off-world death consumed scene");

            require(world.unregisterPlayer(owner,generation),
                "R78 owner unregister failed");
            world.events().schedule(4L,()->{
                try{
                    producer.publishCompletedDeath(
                        dead(103),true,owner,4L
                    );
                    throw new AssertionError(
                        "R78 unregistered owner published kill outcome"
                    );
                }catch(IllegalStateException denied){
                    require(denied.getMessage().contains(
                        "R78_COMBAT_DENIED stale attacker generation"
                    ),"R78 wrong stale-generation refusal "+denied);
                    releasedDenied.incrementAndGet();
                }
                publishGlobal(world);
            });
            require(world.observePulse(System.currentTimeMillis())==4L,
                "R78 fourth World tick mismatch");
            require(releasedDenied.get()==1&&
                    producer.publishedSceneCountForTest()==2&&
                    global.get()==4&&normal.get()==1&&
                    world.players().owns(other,otherGeneration),
                "R78 released source mutated facts or unrelated actor");

            System.out.println(
                "R78_VERIFIED_PVM_COMBAT_OUTCOME_PASS "+
                "realRemovedDeathProducerUsed=true "+
                "exactWorldGenerationCheckedBeforeDedup=true "+
                "actualDomainBusHighAndNormalListeners=true "+
                "verifiedCombatFactAccepted=true "+
                "forgedSameAttackerFactDenied=true "+
                "nestedSpoofedPublicationDenied=true "+
                "quiescenceDuringHighListenerStopsNormal=true "+
                "lateLegacyScheduledKillDeniedBeforeDedup=true "+
                "offWorldProducerDenied=true "+
                "unregisteredAttackerGenerationDenied=true "+
                "globalWorldEventStillDelivered=true "+
                "otherWorldActorUnaffected=true "+
                "defaultLiveCombatRoutingUnchanged=true"
            );
        }
    }

    private static NpcEntity dead(int scene){
        NpcEntity npc=new NpcEntity(scene,1,3088,3495);
        npc.enableLocalLabMortalFixture(10);
        require(npc.applyLocalLabMortalDamage(10)==0,
            "R78 test death was not lethal");
        return npc;
    }

    private static void publishGlobal(World world){
        try{
            world.domainEvents().publish(new GlobalNotice());
        }catch(Exception failure){
            throw new IllegalStateException(
                "R78 global notice wrongly blocked",failure
            );
        }
    }

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }

    private R78OfflineVerifiedCombatOutcomeTest(){}
}

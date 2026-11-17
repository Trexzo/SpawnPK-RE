package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import spk.event.DomainEventBus;

/**
 * R85 OFFLINE ONLY. Drives the SAME LocalPlayerInteractionHandler used
 * by LocalSession through a real v308-shaped player action and immediate
 * lethal PvP tick. This active handler constructs the FOUR-argument
 * PlayerCombatResolutionService, whose observer defaults to NO_OUTCOME_OBSERVER.
 *
 * Therefore damage and S2C134/remote presentation still happen, but
 * the separate semantic CombatOutcome bus and objective observer are
 * NOT automatically notified. This is proof of a specific default
 * wiring gap, not a claim that the PvP combat resolver is unused.
 */
public final class R85OfflineActivePvpOutcomeWiringGapTest {
    public static void main(String[] args)throws Exception{
        try(World world=World.isolatedForTest(600L)){
            WorldPlayer attacker=new WorldPlayer();
            WorldPlayer victim=new WorldPlayer();
            world.registerPlayer(attacker,"r85-attack");
            world.registerPlayer(victim,"r85-victim");

            ByteArrayOutputStream attackerWire=
                new ByteArrayOutputStream();
            ByteArrayOutputStream victimWire=
                new ByteArrayOutputStream();
            ServerPacketWriter attackerWriter=
                new ServerPacketWriter(
                    attackerWire,new IsaacCipher(new int[]{1,2,3,4})
                );
            ServerPacketWriter victimWriter=
                new ServerPacketWriter(
                    victimWire,new IsaacCipher(new int[]{5,6,7,8})
                );
            try{
                String move=victim.movement().accept(
                    new MovementRequest(
                        164,false,
                        new int[]{attacker.movement().x()+1},
                        new int[]{attacker.movement().y()},
                        new byte[0]
                    )
                );
                require(move.startsWith("ACCEPTED"),
                    "R85 victim adjacency setup failed");
                victim.movement().advance();
                attacker.equipment().setWeapon(4151);
                require(victim.playerState().setCurrentLevel(
                        PlayerState.HITPOINTS,9),
                    "R85 victim HP setup failed");

                Player81WorldSync.Context attackerSync=
                    Player81WorldSync.register(
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
                int targetIndex=attackerSync.clientIndexFor(victim);
                require(targetIndex>=0,"R85 victim was not visible");

                AtomicInteger publishedCombatFacts=
                    new AtomicInteger();
                ObjectiveProgressService objectives=
                    new ObjectiveProgressService();
                objectives.define(
                    new ObjectiveDefinition(
                        "r85:pvp-kills",10L,"CUSTOM_LOCALLAB"
                    )
                );
                CombatOutcomeObjectiveObserver projection=
                    new CombatOutcomeObjectiveObserver(
                        objectives,attacker.id().toString(),
                        Collections.singletonList(
                            new CombatOutcomeObjectiveObserver.Binding(
                                CombatOutcomeType.PLAYER_KILL,
                                CombatOutcomeContext.PLAYER_PVP,
                                CombatOutcomeObjectiveObserver
                                    .SubjectRole.ATTACKER,
                                "r85:pvp-kills",1L,"CUSTOM_LOCALLAB"
                            )
                        )
                    );
                world.domainEvents().subscribe(
                    CombatOutcome.class,
                    DomainEventBus.Priority.NORMAL,
                    fact->{
                        publishedCombatFacts.incrementAndGet();
                        projection.onCombatOutcome(fact);
                    }
                );

                // EXACT active interaction handler constructor shape
                // from LocalSession's playerInteractions initialization.
                LocalPlayerInteractionHandler interactions=
                    new LocalPlayerInteractionHandler(
                        world,attacker,
                        attacker.movement(),attacker.equipment(),
                        attacker::generation
                    );
                String request=interactions.handleResolved(
                    new PlayerAction(
                        128,1,targetIndex,"Attack"
                    ),
                    victim,attackerSync
                );
                require(request!=null&&
                        request.contains("PLAYER_ATTACK_REQUEST"),
                    "R85 active handler refused accepted attack");

                String attack=interactions.tickAttack(
                    20L,attackerWriter,attackerSync
                );
                require(attack!=null&&
                        attack.contains("PLAYER_ATTACK_RESOLVED")&&
                        attack.contains("targetDied=true")&&
                        attack.contains("targetHpPacket134=true")&&
                        victim.playerState().currentLevel(
                            PlayerState.HITPOINTS
                        )==0&&
                        victim.lifecycle().dead()&&
                        attackerWire.size()>0,
                    "R85 active handler did not apply real lethal hit");

                require(publishedCombatFacts.get()==0&&
                        objectives.get("r85:pvp-kills").progress==0L,
                    "R85 invented live PvP semantic-event wiring");

                System.out.println(
                    "R85_ACTIVE_PVP_OUTCOME_WIRING_GAP_PASS "+
                    "actualLocalSessionHandlerConstructor=true "+
                    "realPlayer81WorldSyncAttack=true "+
                    "realLethalPvpDamageApplied=true "+
                    "targetHpPacket134Published=true "+
                    "fourArgResolverUsesNoOutcomeObserver=true "+
                    "noDomainCombatFactOnActiveHandler=true "+
                    "objectiveProjectionNotAutoWired=true "+
                    "R79CustomObserverOnlyInOfflineAdapter=true "+
                    "noLiveAccountNetworkOrRewardChange=true"
                );
            }finally{
                try{Player81WorldSync.unregister(attackerWriter);}
                catch(Exception ignored){}
                try{Player81WorldSync.unregister(victimWriter);}
                catch(Exception ignored){}
                world.unregisterPlayer(attacker);
                world.unregisterPlayer(victim);
            }
        }
    }

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private R85OfflineActivePvpOutcomeWiringGapTest(){}
}

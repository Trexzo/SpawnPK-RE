package spk.local;

import java.util.ArrayList;
import java.util.List;

public final class PlayerPvpLifecycleIntegrationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        world.registerPlayer(attacker,"attacker");
        world.registerPlayer(target,"target");

        OutboundPacketQueue attackerWire=
            new OutboundPacketQueue(1<<20);
        OutboundPacketQueue targetWire=
            new OutboundPacketQueue(1<<20);

        ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerWire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        ServerPacketWriter targetWriter=
            new ServerPacketWriter(
                targetWire,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        try{
            String move=
                target.movement().accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{
                            attacker.movement().x()+1
                        },
                        new int[]{
                            attacker.movement().y()
                        },
                        new byte[0]
                    )
                );

            if(!move.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "target adjacency setup rejected "+
                    move
                );

            target.movement().advance();

            attacker.equipment().setWeapon(4151);

            if(!target.playerState().setCurrentLevel(
                    PlayerState.HITPOINTS,
                    9))
                throw new AssertionError(
                    "target HP fixture did not change"
                );

            Player81WorldSync.Context attackerSync=
                Player81WorldSync.register(
                    attackerWriter,
                    world,
                    attacker,
                    new DevAuthorityWorkbench()
                );

            Player81WorldSync.Context targetSync=
                Player81WorldSync.register(
                    targetWriter,
                    world,
                    target,
                    new DevAuthorityWorkbench()
                );

            Player81WorldSync.transformForTest(
                attackerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                targetSync,
                BootstrapPackets.player81Idle()
            );

            int targetIndex=
                attackerSync.clientIndexFor(target);

            if(targetIndex<0)
                throw new AssertionError(
                    "target not visible to attacker"
                );

            List<CombatOutcome> outcomes=
                new ArrayList<>();

            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    attacker,
                    attacker.movement(),
                    attacker.equipment(),
                    attacker.combatStyles(),
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules.recoveredCompatibility(),
                    CombatSystemHooks.forPlayer(attacker),
                    attacker::generation,
                    outcomes::add
                );

            String request=
                interactions.handleResolved(
                    new PlayerAction(
                        128,
                        1,
                        targetIndex,
                        "Attack"
                    ),
                    target,
                    attackerSync
                );

            if(request==null||
               !request.contains(
                    "PLAYER_ATTACK_REQUEST")||
               !request.contains(
                    "damageAuthority=CUSTOM_LOCALLAB"))
                throw new AssertionError(
                    "PvP request authority missing "+
                    request
                );

            int targetBytesBefore=
                targetWire.queuedBytes();

            long targetConsumedBefore=
                Player81WorldSync.consumedEventSequence(
                    targetWriter,
                    attacker.id()
                );

            String attack=
                interactions.tickAttack(
                    20L,
                    attackerWriter,
                    attackerSync
                );

            if(attack==null||
               !attack.contains(
                    "PLAYER_ATTACK_RESOLVED")||
               !attack.contains(
                    "damageAuthority=CUSTOM_LOCALLAB")||
               !attack.contains(
                    "damageFormula=CUSTOM_LOCALLAB_FLAT_10_V1")||
               !attack.contains(
                    "targetDied=true")||
               !attack.contains(
                    "targetHpPacket134=true"))
                throw new AssertionError(
                    "PvP attack did not resolve canonically "+
                    attack
                );

            if(target.playerState().currentLevel(
                    PlayerState.HITPOINTS)!=0||
               !target.lifecycle().dead())
                throw new AssertionError(
                    "lethal PvP did not enter canonical death "+
                    target.lifecycle()
                );

            if(target.lifecycle().deathTick()!=20L||
               target.lifecycle().respawnTick()!=25L)
                throw new AssertionError(
                    "PvP death timing mismatch "+
                    target.lifecycle()
                );

            if(interactions.activeAttack()!=null)
                throw new AssertionError(
                    "lethal PvP attack remained active"
                );

            PlayerDeathAttributionRegistry.Attribution attribution=
                world.playerDeathAttributions().get(
                    target.id(),
                    target.lifecycle().deathSequence()
                );

            if(attribution==null||
               !attribution.attackerId.equals(
                    attacker.id())||
               attribution.attackerGeneration!=
                    attacker.generation()||
               !attribution.victimId.equals(
                    target.id())||
               attribution.victimGeneration!=
                    target.generation()||
               attribution.deathTick!=20L||
               attribution.deathSequence!=
                    target.lifecycle().deathSequence()||
               !PlayerDeathAttributionRegistry.AUTHORITY.equals(
                    attribution.authority))
                throw new AssertionError(
                    "typed PvP death attribution mismatch"
                );

            if(outcomes.size()!=2||
               outcomes.get(0).type()!=
                    CombatOutcomeType.PLAYER_KILL||
               outcomes.get(1).type()!=
                    CombatOutcomeType.PLAYER_DEATH||
               outcomes.get(0).context()!=
                    CombatOutcomeContext.PLAYER_PVP||
               outcomes.get(1).context()!=
                    CombatOutcomeContext.PLAYER_PVP||
               !outcomes.get(0).attacker().equals(
                    attacker.id().toString())||
               !outcomes.get(0).victim().equals(
                    target.id().toString())||
               !outcomes.get(1).attacker().equals(
                    attacker.id().toString())||
               !outcomes.get(1).victim().equals(
                    target.id().toString()))
                throw new AssertionError(
                    "live PvP outcome observer mismatch "+
                    outcomes
                );

            if(targetWire.queuedBytes()<=targetBytesBefore)
                throw new AssertionError(
                    "target received no HP skill packet"
                );

            long published=
                Player81WorldSync.latestPublishedEventSequence(
                    attackerWriter
                );

            if(published<=0L)
                throw new AssertionError(
                    "attacker presentation event was not published"
                );

            targetWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            long targetConsumedAfter=
                Player81WorldSync.consumedEventSequence(
                    targetWriter,
                    attacker.id()
                );

            if(targetConsumedAfter<=targetConsumedBefore||
               targetConsumedAfter<published)
                throw new AssertionError(
                    "target viewer did not consume attacker presentation"+
                    " before="+targetConsumedBefore+
                    " after="+targetConsumedAfter+
                    " published="+published
                );

            System.out.println(
                "PLAYER_PVP_LIFECYCLE_INTEGRATION_PASS "+
                "damage=9 hp=9->0 "+
                "deathTick=20 respawnTick=25 "+
                "hpPacket134=true "+
                "remoteAttackPresentation=true "+
                "combatOutcomes=PLAYER_KILL,PLAYER_DEATH "+
                "typedDeathAttribution=true "+
                "authority=CUSTOM_LOCALLAB"
            );

            Player81WorldSync.unregister(attackerWriter);
            Player81WorldSync.unregister(targetWriter);
        }finally{
            try{Player81WorldSync.unregister(attackerWriter);}
            catch(Exception ignored){}
            try{Player81WorldSync.unregister(targetWriter);}
            catch(Exception ignored){}
            world.unregisterPlayer(attacker);
            world.unregisterPlayer(target);
            world.close();
        }
    }
}

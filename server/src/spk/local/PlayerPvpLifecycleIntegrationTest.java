package spk.local;

import java.io.*;

public final class PlayerPvpLifecycleIntegrationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        world.registerPlayer(attacker,"attacker");
        world.registerPlayer(target,"target");

        ByteArrayOutputStream attackerWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream targetWire=
            new ByteArrayOutputStream();

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
                    world.pvpDeathLedger()
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
                targetWire.size();

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

            PlayerPvpDeathLedger.Entry ledgerEntry=
                world.pvpDeathLedger().get(
                    target.id(),
                    target.lifecycle().deathSequence()
                );

            if(ledgerEntry==null||
               !ledgerEntry.attackerId.equals(attacker.id())||
               !ledgerEntry.victimId.equals(target.id())||
               ledgerEntry.attackerGeneration!=attacker.generation()||
               ledgerEntry.victimGeneration!=target.generation()||
               !"attacker".equals(ledgerEntry.attackerUsername)||
               !"target".equals(ledgerEntry.victimUsername)||
               ledgerEntry.deathSequence!=target.lifecycle().deathSequence()||
               ledgerEntry.deathTick!=20L)
                throw new AssertionError(
                    "live PvP killer attribution missing/mismatched"
                );

            if(interactions.activeAttack()!=null)
                throw new AssertionError(
                    "lethal PvP attack remained active"
                );

            if(targetWire.size()<=targetBytesBefore)
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
                "deathLedger=true "+
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

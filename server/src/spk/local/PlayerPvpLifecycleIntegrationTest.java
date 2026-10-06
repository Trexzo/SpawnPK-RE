package spk.local;

public final class PlayerPvpLifecycleIntegrationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(attacker,"attacker");
        long targetGeneration=
            world.registerPlayer(target,"target");

        OutboundPacketQueue attackerQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue targetQueue=
            new OutboundPacketQueue();

        ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerQueue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        ServerPacketWriter targetWriter=
            new ServerPacketWriter(
                targetQueue,
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
                    CombatSystemHooks.forPlayer(attacker)
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
                targetQueue.queuedBytes();

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

            PlayerLifecycleState.DeathAttribution attribution=
                target.lifecycle().deathAttribution();

            if(attribution==null||
               attribution.deathSequence!=
                    target.lifecycle().deathSequence()||
               !attacker.id().equals(
                    attribution.attackerId
                )||
               attribution.attackerGeneration!=
                    attackerGeneration||
               !"attacker".equals(
                    attribution.attackerUsername)||
               !"PLAYER_PVP".equals(
                    attribution.context))
                throw new AssertionError(
                    "PvP lethal attacker attribution mismatch "+
                    attribution
                );

            if(interactions.activeAttack()!=null)
                throw new AssertionError(
                    "lethal PvP attack remained active"
                );

            PvpRecordService.Record attackerRecord=
                world.pvpRecords().snapshot(
                    attacker
                );
            PvpRecordService.Record targetRecord=
                world.pvpRecords().snapshot(
                    target
                );

            if(attackerRecord.kills!=1L||
               attackerRecord.deaths!=0L||
               attackerRecord.currentStreak!=1L||
               attackerRecord.bestStreak!=1L)
                throw new AssertionError(
                    "attacker PvP record mismatch "+
                    attackerRecord
                );

            if(targetRecord.kills!=0L||
               targetRecord.deaths!=1L||
               targetRecord.currentStreak!=0L||
               targetRecord.bestStreak!=0L)
                throw new AssertionError(
                    "target PvP record mismatch "+
                    targetRecord
                );

            String duplicate=
                interactions.tickAttack(
                    21L,
                    attackerWriter,
                    attackerSync
                );

            if(duplicate!=null)
                throw new AssertionError(
                    "cleared lethal attack produced duplicate tick "+
                    duplicate
                );

            PvpRecordService.Record attackerAfterDuplicate=
                world.pvpRecords().snapshot(
                    attacker
                );
            PvpRecordService.Record targetAfterDuplicate=
                world.pvpRecords().snapshot(
                    target
                );

            if(attackerAfterDuplicate.kills!=1L||
               attackerAfterDuplicate.currentStreak!=1L||
               targetAfterDuplicate.deaths!=1L)
                throw new AssertionError(
                    "cleared attack duplicated PvP records attacker="+
                    attackerAfterDuplicate+
                    " target="+
                    targetAfterDuplicate
                );

            if(targetQueue.queuedBytes()<=targetBytesBefore)
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

            PlayerLifecycleService targetLifecycle=
                new PlayerLifecycleService(
                    target
                );
            PlayerLifecycleService.PreparedRespawn prepared=
                targetLifecycle.prepareRespawn(
                    25L
                );
            if(prepared==null)
                throw new AssertionError(
                    "PvP respawn preparation missing"
                );
            targetLifecycle.commitPreparedRespawn(
                prepared
            );

            if(!target.lifecycle().alive()||
               target.lifecycle().deathAttribution()!=null)
                throw new AssertionError(
                    "respawn retained lethal attacker attribution"
                );

            System.out.println(
                "PLAYER_PVP_LIFECYCLE_INTEGRATION_PASS "+
                "damage=9 hp=9->0 "+
                "deathTick=20 respawnTick=25 "+
                "lethalAttackerId=true "+
                "lethalAttackerGeneration=true "+
                "lethalAttackerUsername=true "+
                "attributionClearedOnRespawn=true "+
                "hpPacket134=true "+
                "remoteAttackPresentation=true "+
                "pvpRecordKill=1 pvpRecordDeath=1 "+
                "pvpRecordStreak=1 duplicateRecord=false "+
                "authority=CUSTOM_LOCALLAB"
            );

            Player81WorldSync.unregister(attackerWriter);
            Player81WorldSync.unregister(targetWriter);
        }finally{
            try{Player81WorldSync.unregister(attackerWriter);}
            catch(Exception ignored){}
            try{Player81WorldSync.unregister(targetWriter);}
            catch(Exception ignored){}
            world.unregisterPlayer(
                attacker,
                attackerGeneration
            );
            world.unregisterPlayer(
                target,
                targetGeneration
            );
            world.close();
        }
    }
}

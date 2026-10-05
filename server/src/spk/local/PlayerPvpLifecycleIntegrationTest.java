package spk.local;

import java.io.*;

public final class PlayerPvpLifecycleIntegrationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        world.registerPlayer(attacker,"attacker");
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

            BankState.Stack[] targetBank=
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] targetInventory=
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];
            targetInventory[0]=
                new BankState.Stack(
                    995,
                    100
                );
            targetInventory[1]=
                new BankState.Stack(
                    20466,
                    1
                );
            target.bank().restoreAccountState(
                targetBank,
                targetInventory,
                false
            );

            int[] targetEquipment=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] targetEquipmentQty=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            java.util.Arrays.fill(
                targetEquipment,
                -1
            );
            targetEquipment[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=4151;
            targetEquipmentQty[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=1;
            target.equipment().restoreAccountState(
                targetEquipment,
                targetEquipmentQty
            );

            Tile deathTile=
                new Tile(
                    target.movement().x(),
                    target.movement().y(),
                    target.movement().plane()
                );

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
                    "targetHpPacket134=true")||
               !attack.contains(
                    "deathLoot=SETTLED"))
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

            BankState.Stack kept=
                target.bank()
                    .inventoryAt(1);

            if(target.bank().inventoryAt(0)!=null||
               kept==null||
               kept.itemId!=20466||
               kept.qty!=1||
               target.equipment().itemAt(
                    EquipmentSlot.WEAPON
                )!=-1)
                throw new AssertionError(
                    "PvP death carried postimage mismatch"
                );

            GroundItem coinLoot=
                world.groundItems()
                    .findOwned(
                        995,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "attacker"
                    );
            GroundItem weaponLoot=
                world.groundItems()
                    .findOwned(
                        4151,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "attacker"
                    );
            GroundItem autoKeepLoot=
                world.groundItems()
                    .findOwned(
                        20466,
                        deathTile.x,
                        deathTile.y,
                        deathTile.plane,
                        "attacker"
                    );

            if(coinLoot==null||
               coinLoot.amount!=100||
               weaponLoot==null||
               weaponLoot.amount!=1||
               autoKeepLoot!=null)
                throw new AssertionError(
                    "PvP death loot settlement mismatch coins="+
                    coinLoot+
                    " weapon="+
                    weaponLoot+
                    " autoKeep="+
                    autoKeepLoot
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

            System.out.println(
                "PLAYER_PVP_LIFECYCLE_INTEGRATION_PASS "+
                "damage=9 hp=9->0 "+
                "deathTick=20 respawnTick=25 "+
                "hpPacket134=true "+
                "remoteAttackPresentation=true "+
                "deathLootSettled=true "+
                "standardLoss=true "+
                "exactAutoKeep=true "+
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

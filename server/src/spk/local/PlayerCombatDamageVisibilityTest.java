package spk.local;

import java.io.*;
import java.util.*;

public final class PlayerCombatDamageVisibilityTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer defender=new WorldPlayer();

        position(
            defender,
            attacker.movement().x()+1,
            attacker.movement().y()
        );

        world.registerPlayer(attacker,"attacker");
        world.registerPlayer(defender,"defender");

        ByteArrayOutputStream attackerOut=
            new ByteArrayOutputStream();
        ByteArrayOutputStream defenderOut=
            new ByteArrayOutputStream();

        ServerPacketWriter attackerWriter=
            new ServerPacketWriter(
                attackerOut,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        ServerPacketWriter defenderWriter=
            new ServerPacketWriter(
                defenderOut,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        Player81WorldSync.Context attackerSync=
            Player81WorldSync.register(
                attackerWriter,
                world,
                attacker,
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.Context defenderSync=
            Player81WorldSync.register(
                defenderWriter,
                world,
                defender,
                new DevAuthorityWorkbench()
            );

        try{
            Player81WorldSync.transformForTest(
                attackerSync,
                BootstrapPackets.player81Idle()
            );

            Player81WorldSync.transformForTest(
                defenderSync,
                BootstrapPackets.player81Idle()
            );

            if(attackerSync.clientIndexFor(defender)<0||
               defenderSync.clientIndexFor(attacker)<0)
                throw new AssertionError(
                    "players not mutually visible"
                );

            attacker.equipment().setWeapon(
                21566
            );

            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    attacker,
                    attacker.movement(),
                    attacker.equipment()
                );

            String request=
                interactions.handleResolved(
                    new PlayerAction(
                        128,
                        1,
                        attackerSync.clientIndexFor(
                            defender
                        ),
                        "Attack"
                    ),
                    defender,
                    attackerSync
                );

            if(request==null||
               !request.contains(
                    "PLAYER_ATTACK_REQUEST")||
               !request.contains(
                    "combatState=CANONICAL_WORLD_PLAYER"))
                throw new AssertionError(
                    "PvP request did not bind canonical combat state "+
                    request
                );

            if(!attacker.combatState().activePlayer()||
               !defender.id().equals(
                    attacker.combatState().targetPlayerId))
                throw new AssertionError(
                    "canonical PvP target missing"
                );

            int attackerBefore=
                attackerOut.size();
            int defenderBefore=
                defenderOut.size();

            String first=
                interactions.tickAttack(
                    10L,
                    attackerWriter,
                    attackerSync
                );

            if(first==null||
               !first.startsWith(
                    "V5131_PLAYER_ATTACK_APPLIED"))
                throw new AssertionError(
                    "first PvP attack not applied "+
                    first
                );

            if(!first.contains(
                    "damage=10")||
               !first.contains(
                    "damageAuthority=CUSTOM_LOCALLAB")||
               !first.contains(
                    "hp=99->89")||
               !first.contains(
                    "hitsplat=UNRESOLVED_CLIENT_PACKET_AUTHORITY"))
                throw new AssertionError(
                    "PvP authority/provenance mismatch "+
                    first
                );

            if(defender.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )!=89)
                throw new AssertionError(
                    "defender canonical HP did not change"
                );

            WorldPlayer canonical=
                world.players().byId(
                    defender.id()
                );

            if(canonical!=defender||
               canonical.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )!=89)
                throw new AssertionError(
                    "world registry does not expose canonical HP mutation"
                );

            if(attackerOut.size()<=attackerBefore)
                throw new AssertionError(
                    "attacker packet81 presentation missing"
                );

            if(defenderOut.size()<=defenderBefore)
                throw new AssertionError(
                    "defender packet134 HP publication missing"
                );

            long attackEvent=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        attackerWriter
                    );

            if(attackEvent<=0)
                throw new AssertionError(
                    "attacker player81 event not published"
                );

            Player81WorldSync.transformForTest(
                defenderSync,
                BootstrapPackets.player81Idle()
            );

            long consumed=
                Player81WorldSync
                    .consumedEventSequence(
                        defenderWriter,
                        attacker.id()
                    );

            if(consumed<attackEvent)
                throw new AssertionError(
                    "defender did not consume attacker presentation event published="+
                    attackEvent+
                    " consumed="+consumed
                );

            long tick=14L;
            String last=first;

            while(!defender.lifecycle().dead()&&
                  tick<100L){
                last=
                    interactions.tickAttack(
                        tick,
                        attackerWriter,
                        attackerSync
                    );
                tick+=4L;
            }

            if(!defender.lifecycle().dead())
                throw new AssertionError(
                    "repeated PvP damage did not produce death last="+
                    last
                );

            if(defender.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )!=0)
                throw new AssertionError(
                    "dead defender HP not zero"
                );

            if(attacker.combatState().activePlayer())
                throw new AssertionError(
                    "attacker retained dead PvP target"
                );

            long deathTick=
                defender.lifecycle().deathTick();

            if(deathTick<0)
                throw new AssertionError(
                    "death tick missing"
                );

            PlayerLifecycleService.TickResult beforeRespawn=
                new PlayerLifecycleService(
                    defender
                ).tick(
                    deathTick+
                    PlayerLifecycleService.LOCALLAB_RESPAWN_DELAY_TICKS-
                    1L
                );

            if(beforeRespawn!=
                    PlayerLifecycleService.TickResult.NONE)
                throw new AssertionError(
                    "respawn occurred early"
                );

            PlayerLifecycleService.TickResult respawn=
                new PlayerLifecycleService(
                    defender
                ).tick(
                    deathTick+
                    PlayerLifecycleService.LOCALLAB_RESPAWN_DELAY_TICKS
                );

            if(respawn!=
                    PlayerLifecycleService.TickResult.RESPAWNED||
               defender.lifecycle().dead()||
               defender.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )!=99)
                throw new AssertionError(
                    "deterministic respawn failed result="+
                    respawn
                );

            System.out.println(
                "PLAYER_COMBAT_DAMAGE_VISIBILITY_PASS "+
                "canonicalPvPState=true "+
                "damageAuthority=CUSTOM_LOCALLAB "+
                "attacker81Relay=true "+
                "defenderHp134=true "+
                "hitsplat=UNRESOLVED_CLIENT_PACKET_AUTHORITY "+
                "death=true respawn=true"
            );
        }finally{
            Player81WorldSync.unregister(
                attackerWriter
            );
            Player81WorldSync.unregister(
                defenderWriter
            );
            world.unregisterPlayer(
                attacker
            );
            world.unregisterPlayer(
                defender
            );
            world.close();
        }
    }

    private static void position(
        WorldPlayer player,
        int x,
        int y
    ){
        Properties properties=
            new Properties();
        properties.setProperty(
            "movement.worldX",
            Integer.toString(x)
        );
        properties.setProperty(
            "movement.worldY",
            Integer.toString(y)
        );
        properties.setProperty(
            "movement.plane",
            "0"
        );

        player.movement()
            .loadAccountProperties(
                properties
            );

        if(player.movement().x()!=x||
           player.movement().y()!=y)
            throw new AssertionError(
                "player fixture position rejected "+
                x+","+y
            );
    }
}

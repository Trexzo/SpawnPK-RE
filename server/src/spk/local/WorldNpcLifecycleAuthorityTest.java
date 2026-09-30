package spk.local;

import java.util.Collections;
import java.util.List;

/**
 * Permanent authority regression for the one canonical NPC HP/death lifecycle
 * owned by each World.
 */
final class WorldNpcLifecycleAuthorityTest {
    public static void main(String[] args)
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        World other=
            World.isolatedForTest(
                600L
            );

        try{
            NpcLifecycleService lifecycle=
                world.npcLifecycle();

            require(
                lifecycle==world.npcLifecycle(),
                "World lifecycle identity drifted"
            );
            require(
                lifecycle!=other.npcLifecycle(),
                "isolated Worlds shared NPC lifecycle"
            );

            WorldNpc npc=
                world.npcs().spawn(
                    1501,
                    3087,
                    3495,
                    0
                );

            NpcLifecycleService.Snapshot registered=
                lifecycle.register(
                    npc,
                    40,
                    "CUSTOM_LOCALLAB"
                );

            require(
                registered.npcId.equals(
                    npc.id
                )&&
                registered.hitpoints==40&&
                lifecycle.get(
                    npc.id
                )!=null,
                "World lifecycle registration missing"
            );

            NpcLifecycleService privateLifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            require(
                privateLifecycle.get(
                    npc.id
                )==null,
                "private lifecycle observed World lifecycle entry"
            );

            WorldPlayer player=
                new WorldPlayer();

            NpcCombatResolutionService combat=
                new NpcCombatResolutionService(
                    player,
                    lifecycle,
                    CombatDamageRules
                        .localLabFallback(),
                    CombatAttackTimingRules
                        .recoveredCompatibility(),
                    CombatSystemHooks.none()
                );

            NpcPvmDelayedHitService delayed=
                new NpcPvmDelayedHitService(
                    world,
                    lifecycle,
                    "CUSTOM_LOCALLAB",
                    NpcPvmDelayedHitService
                        .REQUIRE_CURRENT_ATTACKER_GENERATION
                );

            NpcDropResolutionService drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService
                        .DropResolver(){
                        @Override public List<
                            NpcDropResolutionService.Drop
                        > resolve(
                            NpcDropResolutionService
                                .DeathContext context
                        ){
                            return Collections
                                .emptyList();
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB";
                        }
                    }
                );

            NpcRespawnService respawns=
                new NpcRespawnService(
                    world.npcs(),
                    lifecycle
                );

            require(
                combat.isBoundTo(
                    lifecycle
                )&&
                delayed.isBoundTo(
                    world,
                    lifecycle
                )&&
                drops.isBoundTo(
                    world.npcs(),
                    lifecycle
                )&&
                respawns.isBoundTo(
                    world.npcs(),
                    lifecycle
                ),
                "runtime services do not share World lifecycle"
            );

            require(
                !combat.isBoundTo(
                    privateLifecycle
                )&&
                !delayed.isBoundTo(
                    world,
                    privateLifecycle
                )&&
                !drops.isBoundTo(
                    world.npcs(),
                    privateLifecycle
                )&&
                !respawns.isBoundTo(
                    world.npcs(),
                    privateLifecycle
                ),
                "private lifecycle accepted as World authority"
            );

            require(
                world.npcs().remove(
                    npc.id
                ),
                "canonical NPC removal fixture"
            );

            boolean rejected=false;

            try{
                lifecycle.applyDamage(
                    npc.id,
                    1,
                    0L
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            require(
                rejected,
                "World lifecycle mutated removed canonical NPC"
            );

            System.out.println(
                "WORLD_NPC_LIFECYCLE_AUTHORITY_PASS "+
                "stableWorldIdentity=true "+
                "isolatedWorldsIndependent=true "+
                "sharedRuntimeDependency=true "+
                "privateLifecycleNotAuthority=true "+
                "canonicalRemovalFailClosed=true "+
                "presentationOwned=false "+
                "policyOwned=false"
            );
        }finally{
            world.close();
            other.close();
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private WorldNpcLifecycleAuthorityTest(){}
}

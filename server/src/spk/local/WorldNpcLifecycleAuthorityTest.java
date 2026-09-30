package spk.local;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Locale;

public final class WorldNpcLifecycleAuthorityTest {
    public static void main(String[] args)throws Exception{
        stableWorldIdentity();
        isolatedWorldsIndependent();
        sharedRuntimeDependency();
        privateLifecycleNotAuthority();
        canonicalRemovalFailClosed();
        domainBoundary();

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
    }

    private static void stableWorldIdentity(){
        World world=World.isolatedForTest(600L);

        try{
            require(
                world.npcLifecycle()!=null&&
                world.npcLifecycle()==
                    world.npcLifecycle(),
                "World NPC lifecycle identity is not stable"
            );
        }finally{
            world.close();
        }
    }

    private static void isolatedWorldsIndependent(){
        World a=World.isolatedForTest(600L);
        World b=World.isolatedForTest(600L);

        try{
            require(
                a.npcs()!=b.npcs()&&
                a.npcLifecycle()!=
                    b.npcLifecycle(),
                "isolated Worlds share NPC lifecycle authority"
            );

            WorldNpc npc=
                a.npcs().spawn(
                    1600,
                    3087,
                    3495,
                    0
                );

            a.npcLifecycle().register(
                npc,
                25,
                "CUSTOM_LOCALLAB_WORLD_A"
            );

            require(
                a.npcLifecycle()
                    .get(npc.id)!=null&&
                b.npcLifecycle()
                    .get(npc.id)==null,
                "NPC lifecycle leaked across Worlds"
            );
        }finally{
            a.close();
            b.close();
        }
    }

    private static void sharedRuntimeDependency()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "world-lifecycle-runtime"
            );

        try{
            NpcLifecycleService lifecycle=
                world.npcLifecycle();

            WorldNpc npc=
                world.npcs().spawn(
                    1601,
                    3087,
                    3495,
                    0
                );

            lifecycle.register(
                npc,
                30,
                "CUSTOM_LOCALLAB_WORLD"
            );

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
                    "CUSTOM_LOCALLAB_WORLD",
                    NpcPvmDelayedHitService
                        .REQUIRE_CURRENT_ATTACKER_GENERATION
                );

            NpcDropResolutionService drops=
                new NpcDropResolutionService(
                    world.npcs(),
                    lifecycle,
                    new NpcDropResolutionService
                        .DropResolver(){
                        @Override public java.util.List<
                            NpcDropResolutionService.Drop
                        > resolve(
                            NpcDropResolutionService
                                .DeathContext context
                        ){
                            return Collections.emptyList();
                        }

                        @Override public String authority(){
                            return "CUSTOM_LOCALLAB_WORLD";
                        }
                    }
                );

            NpcRespawnService respawn=
                new NpcRespawnService(
                    world.npcs(),
                    lifecycle
                );

            require(
                world.players().owns(
                    player,
                    generation
                )&&
                combat.isBoundToLifecycle(
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
                respawn.isBoundTo(
                    world.npcs(),
                    lifecycle
                )&&
                lifecycle.get(
                    npc.id
                )!=null,
                "runtime service did not retain exact World lifecycle dependency"
            );
        }finally{
            cleanup(world);
        }
    }

    private static void privateLifecycleNotAuthority(){
        World world=World.isolatedForTest(600L);

        try{
            WorldNpc npc=
                world.npcs().spawn(
                    1602,
                    3087,
                    3495,
                    0
                );

            world.npcLifecycle()
                .register(
                    npc,
                    40,
                    "CUSTOM_LOCALLAB_WORLD"
                );

            NpcLifecycleService privateLifecycle=
                new NpcLifecycleService(
                    world.npcs()
                );

            require(
                privateLifecycle!=
                    world.npcLifecycle()&&
                privateLifecycle.get(
                    npc.id
                )==null&&
                world.npcLifecycle()
                    .get(npc.id)!=null,
                "private lifecycle unexpectedly acts as World authority"
            );
        }finally{
            world.close();
        }
    }

    private static void canonicalRemovalFailClosed(){
        World world=World.isolatedForTest(600L);

        try{
            WorldNpc npc=
                world.npcs().spawn(
                    1603,
                    3087,
                    3495,
                    0
                );

            NpcLifecycleService lifecycle=
                world.npcLifecycle();

            lifecycle.register(
                npc,
                50,
                "CUSTOM_LOCALLAB_WORLD"
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
                    7,
                    1L
                );
            }catch(IllegalStateException expected){
                rejected=true;
            }

            NpcLifecycleService.Snapshot snapshot=
                lifecycle.get(
                    npc.id
                );

            require(
                rejected&&
                snapshot!=null&&
                snapshot.hitpoints==50,
                "removed canonical NPC lifecycle mutation did not fail closed"
            );
        }finally{
            world.close();
        }
    }

    private static void domainBoundary(){
        for(Class<?> type:new Class<?>[]{
                World.class,
                NpcLifecycleService.class
        }){
            for(Field field:type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("reward")||
                   name.contains("dropformula")||
                   name.contains("respawnpolicy"))
                    throw new AssertionError(
                        "unsupported policy/presentation leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void cleanup(World world){
        for(WorldPlayer player:
                world.players().snapshot())
            world.unregisterPlayer(
                player
            );

        world.close();
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private WorldNpcLifecycleAuthorityTest(){}
}

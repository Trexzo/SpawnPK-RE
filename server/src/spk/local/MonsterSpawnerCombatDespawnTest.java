package spk.local;

import java.util.*;

public final class MonsterSpawnerCombatDespawnTest {
    private static final String OWNER="monster-spawner-despawn";

    public static void main(String[] args)throws Exception{
        legacyDespawnCompatible();
        atomicUnbindDespawn();
        missingBindingNoRemoval();
        failedCommitRestoresRuntime();
        postCommitFailureTerminalizesRuntime();

        System.out.println(
            "MONSTER_SPAWNER_COMBAT_DESPAWN_PASS "+
            "legacyDespawnCompatible=true "+
            "atomicUnbindDespawn=true "+
            "bindingRemoved=true "+
            "pulseTargetRemoved=true "+
            "noLaterAi=true "+
            "unbindFailureNoRemoval=true "+
            "removalFailureRollback=true "+
            "postCommitFailureTerminal=true "+
            "relayTrackingRemoved=true "+
            "lifecycleCoherent=true "+
            "presentationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void legacyDespawnCompatible()throws Exception{
        World world=World.isolatedForTest(600L);
        MonsterSpawnerService spawner=configuredSpawner(world,1);
        try{
            MonsterSpawnerService.SpawnResult spawned=
                spawner.spawnSelected(OWNER,3087,3495,0);

            MonsterSpawnerService.SessionSnapshot after=
                spawner.despawnTracked(OWNER,spawned.npc.id);

            require(
                world.npcs().byId(spawned.npc.id)==null&&
                !after.tracks(spawned.npc.id)&&
                after.spawnedNpcIds.isEmpty(),
                "legacy despawn compatibility"
            );
        }finally{
            world.close();
        }
    }

    private static void atomicUnbindDespawn()throws Exception{
        Fixture f=new Fixture();
        try{
            NpcCombatRuntimeBinder binder=f.binder();
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,f.spawner,binder
                );

            MonsterSpawnerCombatBindingService.Result bound=
                service.spawnAndBind(OWNER,3087,3495,0);

            f.world.pulse().pulseOnce(1000L); // engage only
            int hpBefore=f.hp();

            MonsterSpawnerCombatBindingService.DespawnResult terminal=
                service.despawnAndUnbind(
                    OWNER,
                    bound.spawn.npc.id
                );

            require(
                terminal.releasedBinding.npcId.equals(
                    bound.spawn.npc.id
                )&&
                !terminal.session.tracks(bound.spawn.npc.id)&&
                f.world.npcs().byId(bound.spawn.npc.id)==null&&
                binder.get(bound.spawn.npc.id)==null&&
                binder.size()==0&&
                f.world.npcTickTargetCount()==0,
                "atomic terminal publication"
            );

            f.world.pulse().pulseOnce(1600L);

            require(
                f.hp()==hpBefore,
                "despawned runtime executed later AI"
            );
        }finally{
            f.close();
        }
    }

    private static void missingBindingNoRemoval()throws Exception{
        Fixture f=new Fixture();
        try{
            MonsterSpawnerService.SpawnResult spawned=
                f.spawner.spawnSelected(OWNER,3087,3495,0);

            NpcCombatRuntimeBinder binder=f.binder();
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,f.spawner,binder
                );

            expect(
                IllegalStateException.class,
                ()->service.despawnAndUnbind(
                    OWNER,
                    spawned.npc.id
                ),
                "missing runtime binding"
            );

            MonsterSpawnerService.SessionSnapshot session=
                f.spawner.getSession(OWNER);

            require(
                f.world.npcs().byId(spawned.npc.id)==spawned.npc&&
                session.tracks(spawned.npc.id)&&
                binder.size()==0,
                "missing binding removed canonical/tracked NPC"
            );
        }finally{
            f.close();
        }
    }

    private static void failedCommitRestoresRuntime()throws Exception{
        Fixture f=new Fixture();
        try{
            NpcCombatRuntimeBinder binder=f.binder();
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,f.spawner,binder
                );

            MonsterSpawnerCombatBindingService.Result bound=
                service.spawnAndBind(OWNER,3087,3495,0);
            EntityId id=bound.spawn.npc.id;
            final IllegalStateException primary=
                new IllegalStateException("TERMINAL_COMMIT_FAILURE");

            Throwable observed=
                capture(
                    ()->f.spawner.despawnTrackedComposed(
                        OWNER,
                        id,
                        (npc,commit)->{
                            binder.unbindComposed(
                                id,
                                ()->{ throw primary; }
                            );
                            throw new AssertionError(
                                "failed commit unexpectedly returned"
                            );
                        }
                    )
                );

            require(
                observed==primary&&
                f.world.npcs().byId(id)==bound.spawn.npc&&
                f.spawner.getSession(OWNER).tracks(id)&&
                binder.get(id)!=null&&
                binder.size()==1&&
                f.world.npcTickTargetCount()==1,
                "failed terminal commit did not restore runtime"
            );

            // Prove restored target remains executable.
            f.world.pulse().pulseOnce(1000L);
            f.world.pulse().pulseOnce(1600L);
            require(
                f.hp()==89,
                "restored runtime no longer executes"
            );
        }finally{
            f.close();
        }
    }

    private static void postCommitFailureTerminalizesRuntime()
        throws Exception{
        Fixture f=new Fixture();
        try{
            NpcCombatRuntimeBinder binder=f.binder();
            MonsterSpawnerCombatBindingService service=
                new MonsterSpawnerCombatBindingService(
                    f.world,f.spawner,binder
                );

            MonsterSpawnerCombatBindingService.Result bound=
                service.spawnAndBind(OWNER,3087,3495,0);
            EntityId id=bound.spawn.npc.id;
            int hpBefore=f.hp();

            SharedNpcWorldRelay.trackCanonicalNpc(
                f.world,
                bound.spawn.npc
            );

            final IllegalStateException primary=
                new IllegalStateException(
                    "POST_COMMIT_TERMINAL_FAILURE"
                );

            Throwable observed=
                capture(
                    ()->service.despawnAndUnbindComposed(
                        OWNER,
                        id,
                        (npc,commit)->{
                            MonsterSpawnerService.SessionSnapshot
                                committed=
                                    commit.commit();

                            require(
                                !committed.tracks(id),
                                "post-commit fixture did not remove tracked NPC"
                            );

                            throw primary;
                        }
                    )
                );

            require(
                observed==primary&&
                f.world.npcs().byId(id)==null&&
                !f.spawner.getSession(OWNER).tracks(id)&&
                binder.get(id)==null&&
                binder.size()==0&&
                f.world.npcTickTargetCount()==0&&
                !SharedNpcWorldRelay.untrackCanonicalNpc(
                    f.world,
                    id
                ),
                "post-commit failure retained terminal runtime/presentation state"
            );

            f.world.pulse().pulseOnce(1000L);
            f.world.pulse().pulseOnce(1600L);

            require(
                f.hp()==hpBefore,
                "post-commit terminal failure allowed later NPC AI"
            );
        }finally{
            f.close();
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer player=new WorldPlayer();
        final long generation=
            world.registerPlayer(player,"despawn-target");
        final MonsterSpawnerService spawner=
            configuredSpawner(world,1);

        Fixture(){
            player.movement().restoreAccountState(
                false,100,3088,3495,0
            );
        }

        NpcCombatRuntimeBinder binder(){
            return new NpcCombatRuntimeBinder(
                world,
                context->plan()
            );
        }

        int hp(){
            return player.playerState()
                .currentLevel(PlayerState.HITPOINTS);
        }

        void close(){
            world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static MonsterSpawnerService configuredSpawner(
        World world,
        int budget
    ){
        MonsterSpawnerService spawner=
            new MonsterSpawnerService(world.npcs());

        spawner.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,"despawn-test",1530
                )
            ),
            "CUSTOM_LOCALLAB_CATALOG"
        );
        spawner.openSession(
            OWNER,
            "CUSTOM_LOCALLAB_SPAWNER"
        );
        spawner.selectRow(OWNER,0);
        spawner.activate(OWNER,budget);
        return spawner;
    }

    private static NpcCombatRuntimeBinder.BehaviorPlan plan(){
        return new NpcCombatRuntimeBinder.BehaviorPlan(
            "despawn-melee",
            "CUSTOM_LOCALLAB_PROFILE",
            context->
                context.samePlane
                    ?NpcTargetAcquisitionService.Decision
                        .eligible(context.chebyshevDistance)
                    :NpcTargetAcquisitionService.Decision
                        .ineligible(),
            "CUSTOM_LOCALLAB_TARGET",
            "DESPAWN_DISTANCE",
            fixedApproach(),
            new Cadence(),
            new Damage(),
            context->context.worldTick+1L,
            "CUSTOM_LOCALLAB_CONTROLLER",
            "CUSTOM_LOCALLAB_AI"
        );
    }

    private static NpcCombatApproachService.ApproachPolicy
        fixedApproach(){
        return new NpcCombatApproachService.ApproachPolicy(){
            @Override public int stopRange(
                NpcCombatApproachService.Context context
            ){ return 1; }

            @Override public RouteRequest.Policy routePolicy(
                NpcCombatApproachService.Context context
            ){
                return RouteRequest.Policy.WORLD_STATIC_AUTHORITY;
            }

            @Override public String authority(){
                return "CUSTOM_LOCALLAB_NPC_APPROACH";
            }

            @Override public String policy(){
                return "FIXED_RANGE_1_WORLD_STATIC";
            }
        };
    }

    private static final class Cadence
        implements NpcCombatEngagementService.CadenceResolver {
        @Override public int nextDelayTicks(
            NpcCombatEngagementService.Context context
        ){ return 3; }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_NPC_CADENCE";
        }

        @Override public String policy(){ return "FIXED_3"; }
    }

    private static final class Damage
        implements NpcPlayerCombatResolutionService.DamageResolver {
        @Override public int resolve(
            NpcPlayerCombatResolutionService.DamageContext context
        ){ return 10; }

        @Override public String authority(){
            return "CUSTOM_LOCALLAB_NPC_DAMAGE";
        }

        @Override public String formula(){ return "FIXED_10"; }
    }

    private static Throwable capture(
        ThrowingRunnable action
    ){
        try{
            action.run();
        }catch(Throwable failure){
            return failure;
        }
        throw new AssertionError("expected failure");
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    ){
        Throwable failure=capture(action);
        if(!type.isInstance(failure))
            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)throw new AssertionError(label);
    }

    private MonsterSpawnerCombatDespawnTest(){}
}

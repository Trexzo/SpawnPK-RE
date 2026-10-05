package spk.local;

import java.util.Objects;

/**
 * Composes Monster Spawner canonical creation + combat runtime binding with
 * canonical NPC HP/death lifecycle registration.
 *
 * Max HP and lifecycle provenance remain caller-owned gameplay/content policy.
 */
final class MonsterSpawnerNpcLifecycleBindingService {
    interface LifecyclePlanResolver {
        LifecyclePlan resolve(Context context) throws Exception;
    }

    @FunctionalInterface
    interface RegistrationCommitAction {
        void run(
            WorldNpc npc,
            NpcLifecycleService.Snapshot lifecycle,
            String lifecyclePlanKey
        ) throws Exception;
    }

    static final class Context {
        final EntityId npcId;
        final int definitionId;
        final Tile tile;

        private Context(WorldNpc npc){
            this.npcId=npc.id;
            this.definitionId=npc.definitionId;
            this.tile=npc.tile();
        }
    }

    static final class LifecyclePlan {
        final String planKey;
        final int maxHitpoints;
        final String sourceAuthority;

        LifecyclePlan(
            String planKey,
            int maxHitpoints,
            String sourceAuthority
        ){
            this.planKey=requireText(planKey,"planKey");
            if(maxHitpoints<=0)
                throw new IllegalArgumentException(
                    "maxHitpoints="+maxHitpoints
                );
            this.maxHitpoints=maxHitpoints;
            this.sourceAuthority=
                requireGameplayAuthority(
                    sourceAuthority
                );
        }
    }

    static final class Result {
        final MonsterSpawnerCombatBindingService.Result combat;
        final NpcLifecycleService.Snapshot lifecycle;
        final String lifecyclePlanKey;

        private Result(
            MonsterSpawnerCombatBindingService.Result combat,
            NpcLifecycleService.Snapshot lifecycle,
            String lifecyclePlanKey
        ){
            this.combat=Objects.requireNonNull(combat,"combat");
            this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
            this.lifecyclePlanKey=
                requireText(
                    lifecyclePlanKey,
                    "lifecyclePlanKey"
                );
        }
    }

    private final World world;
    private final MonsterSpawnerCombatBindingService combat;
    private final NpcLifecycleService lifecycle;
    private final LifecyclePlanResolver planResolver;

    MonsterSpawnerNpcLifecycleBindingService(
        World world,
        MonsterSpawnerCombatBindingService combat,
        NpcLifecycleService lifecycle,
        LifecyclePlanResolver planResolver
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.combat=
            Objects.requireNonNull(
                combat,
                "combat"
            );
        this.lifecycle=
            Objects.requireNonNull(
                lifecycle,
                "lifecycle"
            );

        if(this.lifecycle!=this.world.npcLifecycle())
            throw new IllegalArgumentException(
                "lifecycle must be the exact World-owned canonical NPC lifecycle"
            );

        this.planResolver=
            Objects.requireNonNull(
                planResolver,
                "planResolver"
            );
    }

    boolean isBoundTo(
        World expectedWorld,
        MonsterSpawnerCombatBindingService expectedCombat
    ){
        return world==expectedWorld&&
            combat==expectedCombat&&
            lifecycle==expectedWorld.npcLifecycle();
    }

    MonsterSpawnerCombatBindingService combatAuthority(){
        return combat;
    }

    Result spawnBindAndRegister(
        String ownerRef,
        int x,
        int y,
        int plane
    )throws Exception{
        return spawnBindAndRegisterComposed(
            ownerRef,
            x,
            y,
            plane,
            (npc,lifecycle,planKey)->{}
        );
    }

    Result spawnBindAndRegisterComposed(
        String ownerRef,
        int x,
        int y,
        int plane,
        RegistrationCommitAction commitAction
    )throws Exception{
        return spawnBindAndRegisterExpected(
            ownerRef,
            null,
            x,
            y,
            plane,
            commitAction
        );
    }

    Result spawnBindAndRegisterIfCurrent(
        String ownerRef,
        MonsterSpawnerService.SessionSnapshot expected,
        int x,
        int y,
        int plane
    )throws Exception{
        return spawnBindAndRegisterComposedIfCurrent(
            ownerRef,
            expected,
            x,
            y,
            plane,
            (npc,lifecycle,planKey)->{}
        );
    }

    Result spawnBindAndRegisterComposedIfCurrent(
        String ownerRef,
        MonsterSpawnerService.SessionSnapshot expected,
        int x,
        int y,
        int plane,
        RegistrationCommitAction commitAction
    )throws Exception{
        return spawnBindAndRegisterExpected(
            ownerRef,
            Objects.requireNonNull(
                expected,
                "expected"
            ),
            x,
            y,
            plane,
            commitAction
        );
    }

    private Result spawnBindAndRegisterExpected(
        String ownerRef,
        MonsterSpawnerService.SessionSnapshot expected,
        int x,
        int y,
        int plane,
        RegistrationCommitAction commitAction
    )throws Exception{
        final NpcLifecycleService.Snapshot[]
            lifecycleResult={null};
        final String[] planKey={null};

        RegistrationCommitAction checkedCommit=
            Objects.requireNonNull(
                commitAction,
                "commitAction"
            );

        MonsterSpawnerCombatBindingService.SpawnBindingAction
            bindingAction=
                (npc,binding)->{
                    try{
                        LifecyclePlan plan=
                            Objects.requireNonNull(
                                planResolver.resolve(
                                    new Context(npc)
                                ),
                                "lifecycle plan"
                            );

                        lifecycleResult[0]=
                            lifecycle.register(
                                npc,
                                plan.maxHitpoints,
                                plan.sourceAuthority
                            );
                        SharedNpcWorldRelay
                            .trackCanonicalNpc(
                                world,
                                npc
                            );
                        planKey[0]=plan.planKey;

                        checkedCommit.run(
                            npc,
                            lifecycleResult[0],
                            planKey[0]
                        );
                    }catch(Throwable primary){
                        try{
                            SharedNpcWorldRelay
                                .untrackCanonicalNpc(
                                    world,
                                    npc.id
                                );
                        }catch(Throwable rollbackFailure){
                            if(rollbackFailure!=primary)
                                primary.addSuppressed(
                                    rollbackFailure
                                );
                        }

                        try{
                            lifecycle.unregisterExact(
                                npc
                            );
                        }catch(Throwable rollbackFailure){
                            if(rollbackFailure!=primary)
                                primary.addSuppressed(
                                    rollbackFailure
                                );
                        }

                        rethrow(
                            primary
                        );
                    }
                };

        MonsterSpawnerCombatBindingService.Result
            combatResult=
                expected==null
                    ?combat.spawnAndBindComposed(
                        ownerRef,
                        x,
                        y,
                        plane,
                        bindingAction
                    )
                    :combat.spawnAndBindComposedIfCurrent(
                        ownerRef,
                        expected,
                        x,
                        y,
                        plane,
                        bindingAction
                    );

        return new Result(
            combatResult,
            Objects.requireNonNull(
                lifecycleResult[0],
                "lifecycle result"
            ),
            Objects.requireNonNull(
                planKey[0],
                "lifecycle plan key"
            )
        );
    }

    private static void rethrow(
        Throwable failure
    )throws Exception{
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;
        if(failure instanceof Exception)
            throw (Exception)failure;

        throw new RuntimeException(
            failure
        );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "sourceAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC lifecycle actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(name);

        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(name);

        return clean;
    }
}

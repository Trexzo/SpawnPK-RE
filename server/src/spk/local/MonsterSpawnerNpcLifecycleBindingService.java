package spk.local;

import java.util.Objects;

/**
 * Composes Monster Spawner canonical creation + combat runtime binding with
 * the exact World-owned canonical NPC HP/death lifecycle authority.
 *
 * Max HP and lifecycle provenance remain caller-owned gameplay/content policy.
 */
final class MonsterSpawnerNpcLifecycleBindingService {
    interface LifecyclePlanResolver {
        LifecyclePlan resolve(Context context) throws Exception;
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
    private final LifecyclePlanResolver planResolver;

    MonsterSpawnerNpcLifecycleBindingService(
        World world,
        MonsterSpawnerCombatBindingService combat,
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
        this.planResolver=
            Objects.requireNonNull(
                planResolver,
                "planResolver"
            );
    }

    Result spawnBindAndRegister(
        String ownerRef,
        int x,
        int y,
        int plane
    )throws Exception{
        final NpcLifecycleService.Snapshot[]
            lifecycleResult={null};
        final String[] planKey={null};

        MonsterSpawnerCombatBindingService.Result
            combatResult=
                combat.spawnAndBindComposed(
                    ownerRef,
                    x,
                    y,
                    plane,
                    (npc,binding)->{
                        LifecyclePlan plan=
                            Objects.requireNonNull(
                                planResolver.resolve(
                                    new Context(npc)
                                ),
                                "lifecycle plan"
                            );

                        boolean lifecycleRegistered=false;
                        boolean projectionTracked=false;

                        try{
                            lifecycleResult[0]=
                                world.npcLifecycle()
                                    .register(
                                        npc,
                                        plan.maxHitpoints,
                                        plan.sourceAuthority
                                    );
                            lifecycleRegistered=true;

                            SharedNpcWorldRelay
                                .trackCanonicalNpc(
                                    world,
                                    npc
                                );
                            projectionTracked=true;
                            planKey[0]=plan.planKey;
                        }catch(Throwable failure){
                            if(projectionTracked){
                                try{
                                    SharedNpcWorldRelay
                                        .untrackCanonicalNpc(
                                            world,
                                            npc.id
                                        );
                                }catch(Throwable cleanup){
                                    if(cleanup!=failure)
                                        failure.addSuppressed(
                                            cleanup
                                        );
                                }
                            }

                            if(lifecycleRegistered){
                                try{
                                    if(!world.npcLifecycle()
                                            .unregister(
                                                npc.id
                                            ))
                                        throw new IllegalStateException(
                                            "Monster Spawner lifecycle rollback missing id="+
                                            npc.id
                                        );
                                }catch(Throwable cleanup){
                                    if(cleanup!=failure)
                                        failure.addSuppressed(
                                            cleanup
                                        );
                                }
                            }

                            rethrow(
                                failure
                            );
                        }
                    }
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

    NpcLifecycleService lifecycleAuthority(){
        return world.npcLifecycle();
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

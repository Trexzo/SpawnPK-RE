package spk.local;

import java.util.Objects;

/**
 * Atomic composition of Monster Spawner canonical creation with NPC combat
 * runtime binding.
 *
 * World lifecycle ownership is outermost so the composition follows:
 * World.lifecycle -> MonsterSpawnerService -> WorldNpcRegistry.
 */
final class MonsterSpawnerCombatBindingService {
    static final class Result {
        final MonsterSpawnerService.SpawnResult spawn;
        final NpcCombatRuntimeBinder.BindingSnapshot binding;

        private Result(
            MonsterSpawnerService.SpawnResult spawn,
            NpcCombatRuntimeBinder.BindingSnapshot binding
        ){
            this.spawn=
                Objects.requireNonNull(
                    spawn,
                    "spawn"
                );
            this.binding=
                Objects.requireNonNull(
                    binding,
                    "binding"
                );
        }
    }

    private final World world;
    private final MonsterSpawnerService spawner;
    private final NpcCombatRuntimeBinder binder;

    MonsterSpawnerCombatBindingService(
        World world,
        MonsterSpawnerService spawner,
        NpcCombatRuntimeBinder binder
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.spawner=
            Objects.requireNonNull(
                spawner,
                "spawner"
            );
        this.binder=
            Objects.requireNonNull(
                binder,
                "binder"
            );
    }

    Result spawnAndBind(
        String ownerRef,
        int x,
        int y,
        int plane
    )throws Exception{
        final MonsterSpawnerService.SpawnResult[]
            spawn={null};
        final NpcCombatRuntimeBinder.BindingSnapshot[]
            binding={null};
        final EntityId[] attemptedNpcId={null};

        boolean open=
            world.withOpenLifecycleOwnership(
                ()->{
                    try{
                        spawn[0]=
                            spawner.spawnSelectedComposed(
                                ownerRef,
                                x,
                                y,
                                plane,
                                npc->{
                                    attemptedNpcId[0]=
                                        npc.id;

                                    NpcCombatRuntimeBinder.BindResult
                                        bound=
                                            binder.bind(
                                                npc
                                            );

                                    if(bound.status!=
                                            NpcCombatRuntimeBinder
                                                .BindStatus.BOUND||
                                       bound.binding==null)
                                        throw new IllegalStateException(
                                            "Monster Spawner combat runtime binding rejected id="+
                                            npc.id+
                                            " status="+
                                            bound.status
                                        );

                                    binding[0]=
                                        bound.binding;
                                }
                            );
                    }catch(Throwable failure){
                        EntityId attempted=
                            attemptedNpcId[0];

                        if(attempted!=null)
                            world.pruneNpcTickTargetIfNpcMissing(
                                attempted
                            );

                        rethrow(
                            failure
                        );
                    }
                }
            );

        if(!open)
            throw new IllegalStateException(
                "world closed before Monster Spawner combat binding"
            );

        return new Result(
            Objects.requireNonNull(
                spawn[0],
                "spawn result"
            ),
            Objects.requireNonNull(
                binding[0],
                "binding result"
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
}

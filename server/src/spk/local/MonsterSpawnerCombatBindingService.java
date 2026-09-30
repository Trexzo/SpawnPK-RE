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
    interface SpawnBindingAction {
        void run(
            WorldNpc npc,
            NpcCombatRuntimeBinder.BindingSnapshot binding
        ) throws Exception;
    }

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

    static final class DespawnResult {
        final MonsterSpawnerService.SessionSnapshot session;
        final NpcCombatRuntimeBinder.BindingSnapshot releasedBinding;

        private DespawnResult(
            MonsterSpawnerService.SessionSnapshot session,
            NpcCombatRuntimeBinder.BindingSnapshot releasedBinding
        ){
            this.session=
                Objects.requireNonNull(
                    session,
                    "session"
                );
            this.releasedBinding=
                Objects.requireNonNull(
                    releasedBinding,
                    "releasedBinding"
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
        return spawnAndBindComposed(
            ownerRef,
            x,
            y,
            plane,
            (npc,binding)->{}
        );
    }

    Result spawnAndBindComposed(
        String ownerRef,
        int x,
        int y,
        int plane,
        SpawnBindingAction action
    )throws Exception{
        SpawnBindingAction checkedAction=
            Objects.requireNonNull(
                action,
                "action"
            );
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

                                    try{
                                        checkedAction.run(
                                            npc,
                                            bound.binding
                                        );
                                    }catch(Throwable primary){
                                        try{
                                            if(!binder.unbind(
                                                    npc.id))
                                                throw new IllegalStateException(
                                                    "Monster Spawner combat rollback binding missing id="+
                                                    npc.id
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

    DespawnResult despawnAndUnbind(
        String ownerRef,
        EntityId npcId
    )throws Exception{
        EntityId checkedId=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );
        final MonsterSpawnerService.SessionSnapshot[]
            session={null};
        final NpcCombatRuntimeBinder.BindingSnapshot[]
            released={null};

        boolean open=
            world.withOpenLifecycleOwnership(
                ()->{
                    session[0]=
                        spawner.despawnTrackedComposed(
                            ownerRef,
                            checkedId,
                            (npc,commit)->{
                                final MonsterSpawnerService.SessionSnapshot[]
                                    committed={null};

                                NpcCombatRuntimeBinder.BindingSnapshot
                                    binding=
                                        binder.unbindComposed(
                                            checkedId,
                                            ()->
                                                committed[0]=
                                                    commit.commit()
                                        );

                                if(binding==null)
                                    throw new IllegalStateException(
                                        "Monster Spawner combat runtime binding missing id="+
                                        checkedId
                                    );

                                released[0]=binding;

                                return Objects.requireNonNull(
                                    committed[0],
                                    "despawn commit result"
                                );
                            }
                        );
                }
            );

        if(!open)
            throw new IllegalStateException(
                "world closed before Monster Spawner combat despawn"
            );

        return new DespawnResult(
            Objects.requireNonNull(
                session[0],
                "despawn session"
            ),
            Objects.requireNonNull(
                released[0],
                "released binding"
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
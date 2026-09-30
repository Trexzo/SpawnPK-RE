package spk.local;

import java.util.Objects;

/**
 * Composes deterministic caller-owned NPC drop resolution with exact terminal
 * teardown of one canonical Monster Spawner NPC.
 *
 * This service owns no drop table, XP, Slayer, Collection Log, reward settlement
 * or respawn policy.
 */
final class MonsterSpawnerNpcDeathFinalizationService {
    static final class Result {
        final EntityId npcId;
        final int definitionId;
        final Tile deathTile;
        final long deathTick;
        final String recipientRef;
        final NpcDropResolutionService.Resolution drops;
        final MonsterSpawnerCombatBindingService.DespawnResult teardown;

        private Result(
            NpcDropResolutionService.Resolution drops,
            MonsterSpawnerCombatBindingService.DespawnResult teardown
        ){
            this.drops=
                Objects.requireNonNull(
                    drops,
                    "drops"
                );
            this.teardown=
                Objects.requireNonNull(
                    teardown,
                    "teardown"
                );

            this.npcId=
                drops.context.npcId;
            this.definitionId=
                drops.context.definitionId;
            this.deathTile=
                drops.context.deathTile;
            this.deathTick=
                drops.context.deathTick;
            this.recipientRef=
                drops.context.recipientRef;
        }
    }

    private final World world;
    private final MonsterSpawnerCombatBindingService combat;
    private final NpcLifecycleService lifecycle;
    private final NpcDropResolutionService drops;

    MonsterSpawnerNpcDeathFinalizationService(
        World world,
        MonsterSpawnerCombatBindingService combat,
        NpcLifecycleService lifecycle,
        NpcDropResolutionService drops
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
        this.drops=
            Objects.requireNonNull(
                drops,
                "drops"
            );

        if(this.lifecycle!=this.world.npcLifecycle())
            throw new IllegalArgumentException(
                "lifecycle must be the exact World-owned canonical NPC lifecycle"
            );

        if(!this.drops.isBoundTo(
                this.world.npcs(),
                this.lifecycle
            ))
            throw new IllegalArgumentException(
                "drop resolver must be bound to the exact World registry + lifecycle"
            );
    }

    Result finalizeDead(
        String ownerRef,
        WorldNpc npc,
        String recipientRef
    )throws Exception{
        WorldNpc checkedNpc=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        if(world.npcs().byId(
                checkedNpc.id
            )!=checkedNpc)
            throw new IllegalStateException(
                "Monster Spawner finalization target is not exact canonical NPC id="+
                checkedNpc.id
            );

        NpcLifecycleService.Snapshot before=
            lifecycle.get(
                checkedNpc.id
            );

        if(before==null)
            throw new IllegalStateException(
                "Monster Spawner finalization lifecycle missing id="+
                checkedNpc.id
            );

        if(!before.dead()||
           !before.hasDeathTick())
            throw new IllegalStateException(
                "Monster Spawner finalization target is not dead id="+
                checkedNpc.id
            );

        /*
         * Resolve/cache caller-owned drops before irreversible teardown.
         * Resolver failure therefore leaves canonical dead NPC, lifecycle,
         * combat binding, pulse target and spawner tracking untouched.
         */
        NpcDropResolutionService.Resolution dropResolution=
            drops.resolve(
                checkedNpc,
                recipientRef
            );

        if(!dropResolution.context.npcId.equals(
                checkedNpc.id
            )||
           dropResolution.context.deathTick!=
                before.deathTick)
            throw new IllegalStateException(
                "drop resolution death identity drifted id="+
                checkedNpc.id
            );

        MonsterSpawnerCombatBindingService.DespawnResult
            teardown=
                combat.despawnAndUnbindComposed(
                    ownerRef,
                    checkedNpc.id,
                    (tracked,commit)->{
                        if(tracked!=checkedNpc)
                            throw new IllegalStateException(
                                "Monster Spawner tracked NPC identity drifted id="+
                                checkedNpc.id
                            );

                        final MonsterSpawnerService.SessionSnapshot[]
                            committed={null};

                        boolean consumed=
                            lifecycle.consumeDeadCanonical(
                                checkedNpc,
                                dead->{
                                    if(dead.deathTick!=
                                            dropResolution.context.deathTick||
                                       dead.definitionId!=
                                            dropResolution.context.definitionId)
                                        throw new IllegalStateException(
                                            "dead lifecycle identity differs from resolved drops id="+
                                            checkedNpc.id
                                        );

                                    committed[0]=
                                        commit.commit();
                                }
                            );

                        if(!consumed)
                            throw new IllegalStateException(
                                "dead lifecycle terminal composition did not consume id="+
                                checkedNpc.id
                            );

                        return Objects.requireNonNull(
                            committed[0],
                            "Monster Spawner terminal commit"
                        );
                    }
                );

        if(world.npcs().byId(
                checkedNpc.id
            )!=null)
            throw new IllegalStateException(
                "Monster Spawner finalization retained canonical NPC id="+
                checkedNpc.id
            );

        if(lifecycle.get(
                checkedNpc.id
            )!=null)
            throw new IllegalStateException(
                "Monster Spawner finalization retained lifecycle id="+
                checkedNpc.id
            );

        return new Result(
            dropResolution,
            teardown
        );
    }
}

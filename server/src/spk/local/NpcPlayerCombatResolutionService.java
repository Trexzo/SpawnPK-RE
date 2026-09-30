package spk.local;

import java.util.Objects;

/**
 * Immediate protocol-independent NPC -> player damage resolution.
 *
 * Aggro, target selection, pathing, range, cadence, attack presentation and
 * original-server combat formulas remain external policy.
 */
final class NpcPlayerCombatResolutionService {
    interface DamageResolver {
        int resolve(DamageContext context);
        String authority();
        String formula();
    }

    static final class DamageContext {
        final EntityId attackerId;
        final int attackerDefinitionId;
        final Tile attackerTile;
        final EntityId targetId;
        final long targetGeneration;
        final int targetHitpoints;
        final long worldTick;

        private DamageContext(
            EntityId attackerId,
            int attackerDefinitionId,
            Tile attackerTile,
            EntityId targetId,
            long targetGeneration,
            int targetHitpoints,
            long worldTick
        ){
            this.attackerId=Objects.requireNonNull(attackerId,"attackerId");
            this.attackerDefinitionId=attackerDefinitionId;
            this.attackerTile=Objects.requireNonNull(attackerTile,"attackerTile");
            this.targetId=Objects.requireNonNull(targetId,"targetId");
            this.targetGeneration=targetGeneration;
            this.targetHitpoints=targetHitpoints;
            this.worldTick=worldTick;
        }
    }

    static final class Result {
        final EntityId attackerId;
        final int attackerDefinitionId;
        final EntityId targetId;
        final long targetGeneration;
        final int resolvedDamage;
        final PlayerLifecycleService.DamageResult lifecycle;
        final String damageAuthority;
        final String damageFormula;

        private Result(
            EntityId attackerId,
            int attackerDefinitionId,
            EntityId targetId,
            long targetGeneration,
            int resolvedDamage,
            PlayerLifecycleService.DamageResult lifecycle,
            String damageAuthority,
            String damageFormula
        ){
            this.attackerId=Objects.requireNonNull(attackerId,"attackerId");
            this.attackerDefinitionId=attackerDefinitionId;
            this.targetId=Objects.requireNonNull(targetId,"targetId");
            this.targetGeneration=targetGeneration;
            this.resolvedDamage=resolvedDamage;
            this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
            this.damageAuthority=Objects.requireNonNull(damageAuthority,"damageAuthority");
            this.damageFormula=Objects.requireNonNull(damageFormula,"damageFormula");
        }
    }

    private final DamageResolver damageResolver;
    private final String damageAuthority;
    private final String damageFormula;

    NpcPlayerCombatResolutionService(
        DamageResolver damageResolver
    ){
        this.damageResolver=Objects.requireNonNull(
            damageResolver,
            "damageResolver"
        );
        this.damageAuthority=requireGameplayAuthority(
            damageResolver.authority()
        );
        this.damageFormula=requireText(
            damageResolver.formula(),
            "damageFormula"
        );
    }

    Result resolveImmediateOwned(
        World world,
        WorldNpc attacker,
        WorldPlayer target,
        long expectedTargetGeneration,
        long worldTick
    )throws Exception{
        return resolveImmediateOwnedInternal(
            world,
            attacker,
            target,
            expectedTargetGeneration,
            worldTick,
            false
        );
    }

    Result resolveImmediateOwnedAtExpectedTick(
        World world,
        WorldNpc attacker,
        WorldPlayer target,
        long expectedTargetGeneration,
        long worldTick
    )throws Exception{
        return resolveImmediateOwnedInternal(
            world,
            attacker,
            target,
            expectedTargetGeneration,
            worldTick,
            true
        );
    }

    private Result resolveImmediateOwnedInternal(
        World world,
        WorldNpc attacker,
        WorldPlayer target,
        long expectedTargetGeneration,
        long worldTick,
        boolean bindExpectedWorldTick
    )throws Exception{
        World checkedWorld=
            Objects.requireNonNull(world,"world");
        WorldNpc checkedAttacker=
            Objects.requireNonNull(attacker,"attacker");
        WorldPlayer checkedTarget=
            Objects.requireNonNull(target,"target");

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        final Result[] result=
            new Result[1];

        boolean targetCurrent=
            checkedWorld
                .withOpenPlayerMutationOwnershipIfCurrent(
                    checkedTarget,
                    expectedTargetGeneration,
                    ()->{
                        final boolean[] npcCurrent=
                            new boolean[1];

                        npcCurrent[0]=
                            checkedWorld.npcs()
                                .withCurrentMutationOwnershipIfCurrent(
                                    checkedAttacker,
                                    ()->{
                                        DamageContext context=
                                            new DamageContext(
                                                checkedAttacker.id,
                                                checkedAttacker.definitionId,
                                                checkedAttacker.tile(),
                                                checkedTarget.id(),
                                                expectedTargetGeneration,
                                                checkedTarget.playerState()
                                                    .currentLevel(
                                                        PlayerState.HITPOINTS
                                                    ),
                                                worldTick
                                            );

                                        int resolvedDamage=
                                            damageResolver.resolve(
                                                context
                                            );

                                        if(checkedWorld.npcs()
                                                .byId(
                                                    checkedAttacker.id
                                                )!=checkedAttacker)
                                            throw new IllegalStateException(
                                                "NPC attacker ownership lost id="+
                                                checkedAttacker.id
                                            );

                                        if(!checkedWorld.players()
                                                .owns(
                                                    checkedTarget,
                                                    expectedTargetGeneration
                                                ))
                                            throw new IllegalStateException(
                                                "player target ownership lost id="+
                                                checkedTarget.id()+
                                                " expectedGeneration="+
                                                expectedTargetGeneration+
                                                " actualGeneration="+
                                                checkedTarget.generation()
                                            );

                                        if(resolvedDamage<0)
                                            throw new IllegalStateException(
                                                "NPC damage resolver returned negative damage="+
                                                resolvedDamage+
                                                " authority="+
                                                damageAuthority
                                            );

                                        PlayerLifecycleService.DamageResult lifecycle;

                                        if(bindExpectedWorldTick){
                                            synchronized(
                                                checkedWorld.clock()
                                            ){
                                                long authoritativeTick=
                                                    checkedWorld.clock()
                                                        .tick();

                                                if(authoritativeTick!=
                                                        worldTick)
                                                    throw new IllegalStateException(
                                                        "NPC -> player damage tick drift expected="+
                                                        worldTick+
                                                        " actual="+
                                                        authoritativeTick
                                                    );

                                                lifecycle=
                                                    new PlayerLifecycleService(
                                                        checkedTarget
                                                    ).applyDamage(
                                                        resolvedDamage,
                                                        worldTick,
                                                        "NPC_ATTACK attacker="+
                                                        checkedAttacker.id+
                                                        " definition="+
                                                        checkedAttacker.definitionId+
                                                        " damageAuthority="+
                                                        damageAuthority+
                                                        " formula="+
                                                        damageFormula
                                                    );
                                            }
                                        }else{
                                            lifecycle=
                                                new PlayerLifecycleService(
                                                    checkedTarget
                                                ).applyDamage(
                                                    resolvedDamage,
                                                    worldTick,
                                                    "NPC_ATTACK attacker="+
                                                    checkedAttacker.id+
                                                    " definition="+
                                                    checkedAttacker.definitionId+
                                                    " damageAuthority="+
                                                    damageAuthority+
                                                    " formula="+
                                                    damageFormula
                                                );
                                        }

                                        result[0]=
                                            new Result(
                                                checkedAttacker.id,
                                                checkedAttacker.definitionId,
                                                checkedTarget.id(),
                                                expectedTargetGeneration,
                                                resolvedDamage,
                                                lifecycle,
                                                damageAuthority,
                                                damageFormula
                                            );
                                    }
                                );

                        if(!npcCurrent[0])
                            throw new IllegalStateException(
                                "NPC attacker is not exact canonical registry owner id="+
                                checkedAttacker.id
                            );
                    }
                );

        if(!targetCurrent)
            throw new IllegalStateException(
                "player target is not exact current world generation id="+
                checkedTarget.id()+
                " expectedGeneration="+
                expectedTargetGeneration+
                " actualGeneration="+
                checkedTarget.generation()
            );

        return result[0];
    }

    String damageAuthority(){
        return damageAuthority;
    }

    String damageFormula(){
        return damageFormula;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=requireText(
            value,
            "damageAuthority"
        );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC damage actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}

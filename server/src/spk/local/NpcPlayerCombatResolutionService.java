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
            this.attackerId=attackerId;
            this.attackerDefinitionId=attackerDefinitionId;
            this.attackerTile=attackerTile;
            this.targetId=targetId;
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
            this.attackerId=attackerId;
            this.attackerDefinitionId=attackerDefinitionId;
            this.targetId=targetId;
            this.targetGeneration=targetGeneration;
            this.resolvedDamage=resolvedDamage;
            this.lifecycle=lifecycle;
            this.damageAuthority=damageAuthority;
            this.damageFormula=damageFormula;
        }
    }

    private final WorldNpcRegistry npcs;
    private final PlayerRegistry players;
    private final DamageResolver damageResolver;
    private final String damageAuthority;
    private final String damageFormula;

    NpcPlayerCombatResolutionService(
        WorldNpcRegistry npcs,
        PlayerRegistry players,
        DamageResolver damageResolver
    ){
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.players=Objects.requireNonNull(players,"players");
        this.damageResolver=Objects.requireNonNull(damageResolver,"damageResolver");
        this.damageAuthority=requireGameplayAuthority(
            damageResolver.authority()
        );
        this.damageFormula=requireText(
            damageResolver.formula(),
            "damageFormula"
        );
    }

    Result resolveImmediate(
        WorldNpc attacker,
        WorldPlayer target,
        long worldTick
    ){
        WorldNpc checkedAttacker=
            Objects.requireNonNull(attacker,"attacker");
        WorldPlayer checkedTarget=
            Objects.requireNonNull(target,"target");

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        requireCanonicalAttacker(
            checkedAttacker
        );

        long expectedGeneration=
            checkedTarget.generation();

        synchronized(checkedTarget.mutationLock()){
            requireTargetOwnership(
                checkedTarget,
                expectedGeneration
            );
            requireCanonicalAttacker(
                checkedAttacker
            );

            DamageContext context=
                new DamageContext(
                    checkedAttacker.id,
                    checkedAttacker.definitionId,
                    checkedAttacker.tile(),
                    checkedTarget.id(),
                    expectedGeneration,
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

            if(resolvedDamage<0)
                throw new IllegalStateException(
                    "NPC damage resolver returned negative damage="+
                    resolvedDamage+
                    " authority="+
                    damageAuthority
                );

            /*
             * Resolver code is caller-owned. Recheck both semantic identities
             * before the first canonical HP/lifecycle mutation.
             */
            requireCanonicalAttacker(
                checkedAttacker
            );
            requireTargetOwnership(
                checkedTarget,
                expectedGeneration
            );

            PlayerLifecycleService.DamageResult lifecycle=
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

            return new Result(
                checkedAttacker.id,
                checkedAttacker.definitionId,
                checkedTarget.id(),
                expectedGeneration,
                resolvedDamage,
                lifecycle,
                damageAuthority,
                damageFormula
            );
        }
    }

    String damageAuthority(){
        return damageAuthority;
    }

    String damageFormula(){
        return damageFormula;
    }

    private void requireCanonicalAttacker(
        WorldNpc attacker
    ){
        if(npcs.byId(attacker.id)!=attacker)
            throw new IllegalStateException(
                "NPC attacker is not exact canonical registry owner id="+
                attacker.id
            );
    }

    private void requireTargetOwnership(
        WorldPlayer target,
        long expectedGeneration
    ){
        if(players.byId(target.id())!=target||
           !target.accepts(expectedGeneration))
            throw new IllegalStateException(
                "player target ownership lost id="+
                target.id()+
                " expectedGeneration="+
                expectedGeneration+
                " actualGeneration="+
                target.generation()
            );
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

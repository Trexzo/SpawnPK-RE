package spk.local;

import java.util.*;

public final class PlayerPvpTargetWorldOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
        World worldA=World.isolatedForTest(600L);
        World worldB=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();
        WorldPlayer freshAttacker=new WorldPlayer();

        long attackerGeneration=
            worldA.registerPlayer(
                attacker,
                "attacker"
            );
        long targetGenerationA=
            worldA.registerPlayer(
                target,
                "target"
            );

        try{
            if(attackerGeneration<=0L||
               targetGenerationA<=0L)
                throw new AssertionError(
                    "initial generations missing"
                );

            if(!target.playerState().setCurrentLevel(
                    PlayerState.HITPOINTS,
                    40
                ))
                throw new AssertionError(
                    "target HP fixture did not change"
                );

            final long[] targetGenerationB=
                new long[]{-1L};
            final boolean[] transferred=
                new boolean[]{false};
            final ArrayList<CombatOutcome> outcomes=
                new ArrayList<>();

            CombatSystemHooks transferBeforeDamage=
                new CombatSystemHooks(){
                    @Override public Snapshot beforeDamage(
                        CombatContext context,
                        int weaponId,
                        long worldTick
                    ){
                        if(!transferred[0]){
                            if(!worldA.unregisterPlayer(
                                    target,
                                    targetGenerationA
                                ))
                                throw new AssertionError(
                                    "target transfer unregister failed"
                                );

                            targetGenerationB[0]=
                                worldB.registerPlayer(
                                    target,
                                    "target"
                                );
                            transferred[0]=true;
                        }

                        return CombatSystemHooks.none()
                            .beforeDamage(
                                context,
                                weaponId,
                                worldTick
                            );
                    }
                };

            PlayerCombatResolutionService staleService=
                new PlayerCombatResolutionService(
                    attacker,
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules
                        .recoveredCompatibility(),
                    transferBeforeDamage,
                    outcomes::add
                );

            int hpBefore=
                target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                );

            boolean staleRejected=false;

            try{
                staleService.resolveImmediateOwned(
                    worldA,
                    target,
                    targetGenerationA,
                    4151,
                    null,
                    20L
                );
            }catch(
                PlayerCombatResolutionService
                    .StaleTargetOwnershipException expected
            ){
                staleRejected=true;
            }

            if(!transferred[0])
                throw new AssertionError(
                    "target did not transfer before damage"
                );

            if(!staleRejected)
                throw new AssertionError(
                    "stale World A damage was not rejected"
                );

            if(targetGenerationB[0]<=
                    targetGenerationA)
                throw new AssertionError(
                    "target generation did not advance "+
                    targetGenerationA+
                    " -> "+
                    targetGenerationB[0]
                );

            if(!worldB.players().owns(
                    target,
                    targetGenerationB[0]
                ))
                throw new AssertionError(
                    "World B does not own transferred target"
                );

            if(target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )!=hpBefore)
                throw new AssertionError(
                    "stale World A mutated World B HP"
                );

            if(target.lifecycle().dead())
                throw new AssertionError(
                    "stale World A changed target lifecycle"
                );

            if(!outcomes.isEmpty())
                throw new AssertionError(
                    "stale World A published combat outcomes "+
                    outcomes.size()
                );

            long freshAttackerGeneration=
                worldB.registerPlayer(
                    freshAttacker,
                    "freshAttacker"
                );

            if(!worldB.players().owns(
                    freshAttacker,
                    freshAttackerGeneration
                ))
                throw new AssertionError(
                    "fresh World B attacker not owned"
                );

            PlayerCombatResolutionService freshService=
                new PlayerCombatResolutionService(
                    freshAttacker,
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules
                        .recoveredCompatibility(),
                    CombatSystemHooks.none(),
                    outcomes::add
                );

            PlayerCombatResolutionService.Result fresh=
                freshService.resolveImmediateOwned(
                    worldB,
                    target,
                    targetGenerationB[0],
                    4151,
                    null,
                    21L
                );

            if(fresh.lifecycle.applied!=10||
               target.playerState().currentLevel(
                    PlayerState.HITPOINTS
               )!=hpBefore-10)
                throw new AssertionError(
                    "fresh World B damage failed "+
                    fresh.lifecycle
                );

            System.out.println(
                "PVP_TARGET_WORLD_OWNERSHIP_FENCE_PASS "+
                "transferredBeforeDamage=true "+
                "staleDamageRejected=true "+
                "hpUnchanged=true "+
                "lifecycleUnchanged=true "+
                "staleOutcomesRejected=true "+
                "freshWorldDamageWorks=true"
            );
        }finally{
            for(WorldPlayer player:
                    worldA.players().snapshot())
                worldA.unregisterPlayer(player);

            for(WorldPlayer player:
                    worldB.players().snapshot())
                worldB.unregisterPlayer(player);

            worldA.close();
            worldB.close();
        }
    }
}

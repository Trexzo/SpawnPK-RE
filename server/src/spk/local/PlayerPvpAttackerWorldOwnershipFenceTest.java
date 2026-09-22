package spk.local;

import java.util.ArrayList;

public final class PlayerPvpAttackerWorldOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer attacker=
            new WorldPlayer();
        WorldPlayer target=
            new WorldPlayer();

        long attackerGenerationA=
            world.registerPlayer(
                attacker,
                "attacker"
            );
        long targetGeneration=
            world.registerPlayer(
                target,
                "target"
            );

        try{
            if(!target.playerState().setCurrentLevel(
                    PlayerState.HITPOINTS,
                    40
                ))
                throw new AssertionError(
                    "target HP fixture did not change"
                );

            final long[] attackerGenerationB=
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
                            if(!world.unregisterPlayer(
                                    attacker,
                                    attackerGenerationA
                                ))
                                throw new AssertionError(
                                    "attacker transfer unregister failed"
                                );

                            attackerGenerationB[0]=
                                world.registerPlayer(
                                    attacker,
                                    "attacker"
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
                    world,
                    attackerGenerationA,
                    target,
                    targetGeneration,
                    4151,
                    null,
                    20L
                );
            }catch(
                PlayerCombatResolutionService
                    .StaleAttackerOwnershipException expected
            ){
                staleRejected=true;
            }

            if(!transferred[0])
                throw new AssertionError(
                    "attacker did not transfer before damage"
                );

            if(!staleRejected)
                throw new AssertionError(
                    "stale attacker generation was not rejected"
                );

            if(attackerGenerationB[0]<=
                    attackerGenerationA)
                throw new AssertionError(
                    "attacker generation did not advance "+
                    attackerGenerationA+
                    " -> "+
                    attackerGenerationB[0]
                );

            if(!world.players().owns(
                    attacker,
                    attackerGenerationB[0]
                ))
                throw new AssertionError(
                    "replacement attacker generation not authoritative"
                );

            if(!world.players().owns(
                    target,
                    targetGeneration
                ))
                throw new AssertionError(
                    "target ownership changed unexpectedly"
                );

            if(target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )!=hpBefore)
                throw new AssertionError(
                    "stale attacker mutated target HP"
                );

            if(target.lifecycle().dead())
                throw new AssertionError(
                    "stale attacker changed target lifecycle"
                );

            if(!outcomes.isEmpty())
                throw new AssertionError(
                    "stale attacker published combat outcomes "+
                    outcomes.size()
                );

            PlayerCombatResolutionService freshService=
                new PlayerCombatResolutionService(
                    attacker,
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules
                        .recoveredCompatibility(),
                    CombatSystemHooks.none(),
                    outcomes::add
                );

            PlayerCombatResolutionService.Result fresh=
                freshService.resolveImmediateOwned(
                    world,
                    attackerGenerationB[0],
                    target,
                    targetGeneration,
                    4151,
                    null,
                    21L
                );

            if(fresh.lifecycle.applied!=10)
                throw new AssertionError(
                    "fresh attacker damage mismatch "+
                    fresh.lifecycle.applied
                );

            if(target.playerState().currentLevel(
                    PlayerState.HITPOINTS
                )!=hpBefore-10)
                throw new AssertionError(
                    "fresh attacker did not mutate target HP"
                );

            System.out.println(
                "PVP_ATTACKER_WORLD_OWNERSHIP_FENCE_PASS "+
                "transferredBeforeDamage=true "+
                "staleAttackerRejected=true "+
                "hpUnchanged=true "+
                "lifecycleUnchanged=true "+
                "staleOutcomesRejected=true "+
                "replacementAttackerDamageWorks=true"
            );
        }finally{
            for(WorldPlayer player:
                    world.players().snapshot())
                world.unregisterPlayer(player);

            world.close();
        }
    }

    private PlayerPvpAttackerWorldOwnershipFenceTest(){}
}

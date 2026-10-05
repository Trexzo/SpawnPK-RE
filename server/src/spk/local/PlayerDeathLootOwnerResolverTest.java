package spk.local;

import java.util.ArrayList;
import java.util.List;

public final class PlayerDeathLootOwnerResolverTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_ATTRIBUTION_TEST";

    public static void main(String[] args){
        exactCurrentGenerationWins();
        staleGenerationFallsBackPublic();
        missingAndUnsupportedAttributionFallBackPublic();
        crossVictimResolutionRejected();
        nonLethalDamageDoesNotAttribute();

        System.out.println(
            "PLAYER_DEATH_LOOT_OWNER_RESOLVER_PASS "+
            "currentKillerGeneration=true "+
            "staleGenerationPublic=true "+
            "missingAttributionPublic=true "+
            "unsupportedContextPublic=true "+
            "crossVictimRejected=true "+
            "nonLethalUnattributed=true "+
            "usernameNotAuthority=true"
        );
    }

    private static void exactCurrentGenerationWins(){
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();
        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "killer"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim"
            );

        try{
            kill(victim,10L);
            victim.lifecycle()
                .attributeCurrentDeath(
                    victim.lifecycle()
                        .deathSequence(),
                    attacker.id(),
                    attackerGeneration,
                    "PLAYER_PVP"
                );

            PlayerDeathItemResolutionService.Resolution resolution=
                resolution(victim);

            PlayerDeathLootOwnerResolver.Result owner=
                new PlayerDeathLootOwnerResolver()
                    .resolve(
                        world,
                        victim,
                        resolution
                    );

            require(
                owner.killerScoped()&&
                "killer".equals(
                    owner.lootOwner
                )&&
                attacker.id().equals(
                    owner.attackerId
                )&&
                owner.attackerGeneration==
                    attackerGeneration&&
                "KILLER_CURRENT_GENERATION".equals(
                    owner.reason
                ),
                "exact killer owner"
            );
        }finally{
            world.unregisterPlayer(
                attacker,
                attackerGeneration
            );
            world.unregisterPlayer(
                victim,
                victimGeneration
            );
            world.close();
        }
    }

    private static void staleGenerationFallsBackPublic(){
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();
        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "killer"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim"
            );

        try{
            kill(victim,20L);
            victim.lifecycle()
                .attributeCurrentDeath(
                    victim.lifecycle()
                        .deathSequence(),
                    attacker.id(),
                    attackerGeneration,
                    "PLAYER_PVP"
                );

            PlayerDeathItemResolutionService.Resolution resolution=
                resolution(victim);

            require(
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                ),
                "attacker unregister"
            );

            long replacementGeneration=
                world.registerPlayer(
                    attacker,
                    "killer-reconnected"
                );

            try{
                PlayerDeathLootOwnerResolver.Result owner=
                    new PlayerDeathLootOwnerResolver()
                        .resolve(
                            world,
                            victim,
                            resolution
                        );

                require(
                    !owner.killerScoped()&&
                    owner.lootOwner==null&&
                    attacker.id().equals(
                        owner.attackerId
                    )&&
                    owner.attackerGeneration==
                        attackerGeneration&&
                    replacementGeneration!=
                        attackerGeneration&&
                    "PUBLIC_STALE_ATTACKER_GENERATION".equals(
                        owner.reason
                    ),
                    "stale attacker generation"
                );
            }finally{
                world.unregisterPlayer(
                    attacker,
                    replacementGeneration
                );
            }
        }finally{
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker
                );
            world.unregisterPlayer(
                victim,
                victimGeneration
            );
            world.close();
        }
    }

    private static void missingAndUnsupportedAttributionFallBackPublic(){
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();
        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "killer"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "victim"
            );

        try{
            kill(victim,30L);
            PlayerDeathItemResolutionService.Resolution resolution=
                resolution(victim);

            PlayerDeathLootOwnerResolver resolver=
                new PlayerDeathLootOwnerResolver();

            PlayerDeathLootOwnerResolver.Result missing=
                resolver.resolve(
                    world,
                    victim,
                    resolution
                );

            require(
                !missing.killerScoped()&&
                "PUBLIC_NO_ATTRIBUTION".equals(
                    missing.reason
                ),
                "missing attribution"
            );

            victim.lifecycle()
                .attributeCurrentDeath(
                    victim.lifecycle()
                        .deathSequence(),
                    attacker.id(),
                    attackerGeneration,
                    "NON_PVP_TEST"
                );

            PlayerDeathLootOwnerResolver.Result unsupported=
                resolver.resolve(
                    world,
                    victim,
                    resolution
                );

            require(
                !unsupported.killerScoped()&&
                "PUBLIC_UNSUPPORTED_ATTRIBUTION_CONTEXT".equals(
                    unsupported.reason
                ),
                "unsupported attribution"
            );
        }finally{
            world.unregisterPlayer(
                attacker,
                attackerGeneration
            );
            world.unregisterPlayer(
                victim,
                victimGeneration
            );
            world.close();
        }
    }

    private static void crossVictimResolutionRejected(){
        World world=World.isolatedForTest(600L);
        WorldPlayer first=new WorldPlayer();
        WorldPlayer second=new WorldPlayer();

        try{
            kill(first,40L);
            PlayerDeathItemResolutionService.Resolution resolution=
                resolution(first);

            boolean rejected=false;
            try{
                new PlayerDeathLootOwnerResolver()
                    .resolve(
                        world,
                        second,
                        resolution
                    );
            }catch(IllegalArgumentException expected){
                rejected=true;
            }

            require(
                rejected,
                "cross-victim resolution"
            );
        }finally{
            world.close();
        }
    }

    private static void nonLethalDamageDoesNotAttribute(){
        WorldPlayer player=new WorldPlayer();
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            ).applyDamage(
                1,
                50L,
                "NON_LETHAL"
            );

        require(
            !result.died&&
            player.lifecycle()
                .deathAttribution()==null,
            "nonlethal attribution"
        );
    }

    private static PlayerDeathItemResolutionService.Resolution
        resolution(
            WorldPlayer player
        ){
        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview=
            service.previewCurrentDeath();

        List<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.quantity
                )
            );

        return service.resolveCurrentDeath(
            preview,
            decisions
        );
    }

    private static void kill(
        WorldPlayer player,
        long tick
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            ).applyDamage(
                500,
                tick,
                "ATTRIBUTION_TEST"
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PlayerDeathLootOwnerResolverTest(){}
}

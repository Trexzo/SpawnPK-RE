package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VerifiedKillerLootDisconnectTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G54_KILLER_LOOT_TEST";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer victim=new WorldPlayer();

        long attackerGeneration=
            world.registerPlayer(
                attacker,
                "verified-killer"
            );
        long victimGeneration=
            world.registerPlayer(
                victim,
                "disconnect-victim"
            );

        boolean currentOwner=false;
        boolean disconnectOwner=false;
        boolean staleRewardRejected=false;
        boolean groundOwnerPreserved=false;
        boolean identityMismatchPublic=false;
        boolean wrongContextPublic=false;
        boolean staleDeathSequencePublic=false;

        try{
            installCoins(
                victim,
                100
            );
            kill(
                victim,
                10L
            );

            long deathSequence=
                victim.lifecycle()
                    .deathSequence();

            victim.lifecycle()
                .attributeCurrentDeath(
                    deathSequence,
                    attacker.id(),
                    attackerGeneration,
                    attacker.username(),
                    "PLAYER_PVP"
                );

            PlayerDeathItemResolutionService.Resolution
                resolution=
                    loseAll(
                        victim
                    );

            PlayerDeathLootOwnerResolver resolver=
                new PlayerDeathLootOwnerResolver();

            PlayerDeathLootOwnerResolver.Result current=
                resolver.resolve(
                    world,
                    victim,
                    resolution
                );

            currentOwner=
                current.killerScoped()&&
                "verified-killer".equals(
                    current.lootOwner
                )&&
                "KILLER_CURRENT_CAPTURED_IDENTITY"
                    .equals(
                        current.reason
                    );

            require(
                currentOwner,
                "current exact killer identity not owned"
            );

            require(
                world.unregisterPlayer(
                    attacker,
                    attackerGeneration
                ),
                "attacker disconnect fixture"
            );

            PlayerDeathLootOwnerResolver.Result disconnected=
                resolver.resolve(
                    world,
                    victim,
                    resolution
                );

            disconnectOwner=
                disconnected.killerScoped()&&
                "verified-killer".equals(
                    disconnected.lootOwner
                )&&
                disconnected.attackerGeneration==
                    attackerGeneration&&
                "KILLER_CAPTURED_IDENTITY_AFTER_DISCONNECT"
                    .equals(
                        disconnected.reason
                    );

            require(
                disconnectOwner,
                "disconnect erased verified killer ownership"
            );

            PvpKillRewardService reward=
                new PvpKillRewardService(
                    victim
                );
            PvpKillRewardService.Receipt
                rejectedReward=
                    reward.settle(
                        world,
                        attacker,
                        attackerGeneration,
                        deathSequence
                    );

            staleRewardRejected=
                !rejectedReward.granted&&
                "STALE_ATTACKER_GENERATION".equals(
                    rejectedReward.reason
                )&&
                PvpKillRewardService
                    .counters(attacker)
                    .kills==0L;

            require(
                staleRewardRejected,
                "stale attacker received PvP reward"
            );

            PlayerDeathGroundSettlementService
                settlement=
                    new PlayerDeathGroundSettlementService(
                        world,
                        victim,
                        AUTHORITY
                    );

            PlayerDeathGroundSettlementService.Settlement
                settled=
                    settlement.settle(
                        resolution,
                        disconnected.lootOwner
                    );

            GroundItem coins=
                world.groundItems()
                    .findOwned(
                        995,
                        settled.deathTile.x,
                        settled.deathTile.y,
                        settled.deathTile.plane,
                        "verified-killer"
                    );

            groundOwnerPreserved=
                coins!=null&&
                coins.amount==100&&
                "verified-killer".equals(
                    settled.lootOwner
                );

            require(
                groundOwnerPreserved,
                "settlement lost captured killer owner"
            );

            // Resolver must fail closed if a still-current attacker identity
            // disagrees with the identity captured at lethal attribution.
            new PlayerLifecycleService(
                victim,
                AUTHORITY
            ).commitPreparedRespawn(
                new PlayerLifecycleService(
                    victim,
                    AUTHORITY
                ).prepareRespawn(
                    victim.lifecycle()
                        .respawnTick()
                )
            );

            long reconnectedGeneration=
                world.registerPlayer(
                    attacker,
                    "verified-killer"
                );

            try{
                installCoins(
                    victim,
                    1
                );
                kill(
                    victim,
                    20L
                );

                victim.lifecycle()
                    .attributeCurrentDeath(
                        victim.lifecycle()
                            .deathSequence(),
                        attacker.id(),
                        reconnectedGeneration,
                        "different-killer",
                        "PLAYER_PVP"
                    );

                PlayerDeathItemResolutionService.Resolution
                    mismatchResolution=
                        loseAll(
                            victim
                        );

                PlayerDeathLootOwnerResolver.Result mismatch=
                    resolver.resolve(
                        world,
                        victim,
                        mismatchResolution
                    );

                identityMismatchPublic=
                    !mismatch.killerScoped()&&
                    "PUBLIC_ATTACKER_IDENTITY_MISMATCH"
                        .equals(
                            mismatch.reason
                        );

                require(
                    identityMismatchPublic,
                    "current identity mismatch was not public"
                );

                victim.lifecycle()
                    .attributeCurrentDeath(
                        victim.lifecycle()
                            .deathSequence(),
                        attacker.id(),
                        reconnectedGeneration,
                        attacker.username(),
                        "NON_PVP_TEST"
                    );

                PlayerDeathLootOwnerResolver.Result wrongContext=
                    resolver.resolve(
                        world,
                        victim,
                        mismatchResolution
                    );

                wrongContextPublic=
                    !wrongContext.killerScoped()&&
                    "PUBLIC_UNSUPPORTED_ATTRIBUTION_CONTEXT"
                        .equals(
                            wrongContext.reason
                        );

                require(
                    wrongContextPublic,
                    "wrong attribution context was not public"
                );

                // A resolution from the prior death cannot consume a newer
                // death attribution.
                PlayerDeathLootOwnerResolver.Result staleResolution=
                    resolver.resolve(
                        world,
                        victim,
                        resolution
                    );

                staleDeathSequencePublic=
                    !staleResolution.killerScoped()&&
                    "PUBLIC_STALE_DEATH_ATTRIBUTION"
                        .equals(
                            staleResolution.reason
                        );

                require(
                    staleDeathSequencePublic,
                    "stale death sequence retained killer owner"
                );
            }finally{
                if(attacker.registered())
                    world.unregisterPlayer(
                        attacker,
                        attacker.generation()
                    );
            }

            System.out.println(
                "G5_KILLER_LOOT_DISCONNECT_PASS"+
                " currentOwner="+currentOwner+
                " disconnectOwner="+disconnectOwner+
                " staleRewardRejected="+staleRewardRejected+
                " groundOwnerPreserved="+groundOwnerPreserved+
                " identityMismatchPublic="+identityMismatchPublic+
                " wrongContextPublic="+wrongContextPublic+
                " staleDeathSequencePublic="+staleDeathSequencePublic+
                " originalSpawnpkLootPolicyClaim=false"
            );
        }finally{
            if(attacker.registered())
                world.unregisterPlayer(
                    attacker,
                    attacker.generation()
                );
            if(victim.registered())
                world.unregisterPlayer(
                    victim,
                    victimGeneration
                );
            world.close();
        }
    }

    private static PlayerDeathItemResolutionService.Resolution
        loseAll(
            WorldPlayer victim
        ){
        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                victim,
                AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview
            preview=
                service.previewCurrentDeath();

        List<PlayerDeathItemResolutionService.Decision>
            decisions=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried)
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    0
                )
            );

        return service.resolveCurrentDeath(
            preview,
            decisions
        );
    }

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(
            items,
            -1
        );
        items[0]=995;
        quantities[0]=quantity;

        player.bank()
            .replaceInventorySemantic(
                items,
                quantities
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
                "G5_KILLER_LOOT_TEST",
                5L
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

    private VerifiedKillerLootDisconnectTest(){}
}

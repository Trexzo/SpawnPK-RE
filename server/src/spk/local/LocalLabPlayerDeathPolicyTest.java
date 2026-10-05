package spk.local;

import java.util.Arrays;

public final class LocalLabPlayerDeathPolicyTest {
    public static void main(String[] args) {
        exactRepositoryClassification();
        crossPlayerRejected();

        System.out.println(
            "LOCALLAB_PLAYER_DEATH_POLICY_PASS " +
            "explicitAutoKeep=true " +
            "explicitAutoLoss=true " +
            "standardUnresolvedKept=true " +
            "victimOwnedReclaim=true " +
            "deathTileCaptured=true " +
            "valueOrderingInvented=false " +
            "keepCountInvented=false"
        );
    }

    private static void exactRepositoryClassification() {
        WorldPlayer player =
            new WorldPlayer();
        World world =
            World.isolatedForTest(
                600L
            );

        world.registerPlayer(
            player,
            "death-policy-victim"
        );

        try {
            synchronized(player.mutationLock()) {
                BankState.Stack[] bank =
                    new BankState.Stack[
                        BankState.BANK_CAPACITY
                    ];
                BankState.Stack[] inventory =
                    new BankState.Stack[
                        BankState.INVENTORY_CAPACITY
                    ];

                inventory[0] =
                    new BankState.Stack(
                        20466,
                        2
                    );
                inventory[1] =
                    new BankState.Stack(
                        24254,
                        3
                    );
                inventory[2] =
                    new BankState.Stack(
                        995,
                        100
                    );

                player.bank().restoreAccountState(
                    bank,
                    inventory,
                    false
                );

                int[] equipmentItems =
                    new int[
                        EquipmentState.EQUIPMENT_SLOTS
                    ];
                int[] equipmentQuantities =
                    new int[
                        EquipmentState.EQUIPMENT_SLOTS
                    ];

                Arrays.fill(
                    equipmentItems,
                    -1
                );

                player.equipment()
                    .restoreAccountState(
                        equipmentItems,
                        equipmentQuantities
                    );
            }

            player.movement().accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{3211},
                    new int[]{3207},
                    new byte[0]
                )
            );
            player.movement().advance();

            PlayerLifecycleService.DamageResult death =
                new PlayerLifecycleService(
                    player
                ).applyDamage(
                    500,
                    50L,
                    "POLICY_TEST",
                    5L
                );

            require(
                death.died,
                "death fixture"
            );

            PlayerDeathItemResolutionService service =
                new PlayerDeathItemResolutionService(
                    player,
                    LocalLabPlayerDeathPolicy.AUTHORITY
                );

            PlayerDeathItemResolutionService.DeathPreview preview =
                service.previewCurrentDeath();

            LocalLabPlayerDeathPolicy.Plan plan =
                new LocalLabPlayerDeathPolicy()
                    .plan(
                        player,
                        preview
                    );

            require(
                plan.explicitKeepLines == 1 &&
                plan.explicitLossLines == 1 &&
                plan.unresolvedKeptLines == 1,
                "classification counts"
            );

            require(
                plan.deathTile.x ==
                    player.movement().x() &&
                plan.deathTile.y ==
                    player.movement().y() &&
                plan.deathTile.plane ==
                    player.movement().plane(),
                "death tile capture"
            );

            require(
                "death-policy-victim".equals(
                    plan.recipientRef
                ) &&
                LocalLabPlayerDeathPolicy
                    .RECIPIENT_POLICY.equals(
                        plan.recipientPolicy
                    ),
                "victim recipient"
            );

            PlayerDeathItemResolutionService.Resolution resolution =
                service.resolveCurrentDeath(
                    preview,
                    plan.decisions
                );

            requireDisposition(
                resolution,
                20466,
                2,
                0
            );
            requireDisposition(
                resolution,
                24254,
                0,
                3
            );
            requireDisposition(
                resolution,
                995,
                100,
                0
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void crossPlayerRejected() {
        WorldPlayer first =
            new WorldPlayer();
        WorldPlayer second =
            new WorldPlayer();
        World world =
            World.isolatedForTest(
                600L
            );

        world.registerPlayer(
            first,
            "first-policy"
        );
        world.registerPlayer(
            second,
            "second-policy"
        );

        try {
            new PlayerLifecycleService(
                first
            ).applyDamage(
                500,
                60L,
                "FIRST",
                5L
            );

            PlayerDeathItemResolutionService.DeathPreview preview =
                new PlayerDeathItemResolutionService(
                    first,
                    LocalLabPlayerDeathPolicy.AUTHORITY
                ).previewCurrentDeath();

            boolean rejected=false;

            try{
                new LocalLabPlayerDeathPolicy()
                    .plan(
                        second,
                        preview
                    );
            }catch(IllegalArgumentException expected){
                rejected=true;
            }

            require(
                rejected,
                "cross-player preview accepted"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void requireDisposition(
        PlayerDeathItemResolutionService.Resolution resolution,
        int itemId,
        int kept,
        int lost
    ){
        for(PlayerDeathItemResolutionService.Disposition disposition:
                resolution.dispositions)
            if(disposition.line.itemId==itemId){
                require(
                    disposition.keptAmount==kept&&
                    disposition.lostAmount==lost,
                    "disposition item="+
                    itemId
                );
                return;
            }

        throw new AssertionError(
            "missing disposition item="+
            itemId
        );
    }

    private static void cleanup(
        World world
    ){
        for(WorldPlayer player:
                world.players().snapshot())
            world.unregisterPlayer(
                player
            );

        world.close();
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

    private LocalLabPlayerDeathPolicyTest(){}
}

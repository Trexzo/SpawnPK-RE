package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlayerDeathGroundSettlementServiceTest {
    private static final String POLICY_AUTHORITY =
        "CUSTOM_LOCALLAB_DEATH_POLICY_TEST";
    private static final String COMMIT_AUTHORITY =
        "CUSTOM_LOCALLAB_DEATH_COMMIT_TEST";
    private static final String SETTLEMENT_AUTHORITY =
        "CUSTOM_LOCALLAB_DEATH_GROUND_TEST";

    public static void main(String[] args) {
        exactSettlementAndReplay();
        conflictingReplayRejected();
        missingRecipientStillSettles();
        emptyLostBundleSettles();
        authorityGuards();

        System.out.println(
            "PLAYER_DEATH_GROUND_SETTLEMENT_PASS " +
            "canonicalGroundState=true " +
            "stackMerge=true " +
            "exactReplay=true " +
            "conflictingReplayRejected=true " +
            "liveOwnerPresentation=true " +
            "missingRecipientStillCanonical=true " +
            "emptyBundle=true " +
            "recipientPolicyCallerOwned=true"
        );
    }

    private static void exactSettlementAndReplay() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer victim =
            configuredPlayer();
        WorldPlayer recipient =
            new WorldPlayer();

        world.registerPlayer(
            victim,
            "death-victim"
        );
        long recipientGeneration =
            world.registerPlayer(
                recipient,
                "death-recipient"
            );

        try {
            PlayerDeathItemCommitService.CommitResult commit =
                commitLosses(
                    victim,
                    10L
                );

            Tile tile =
                new Tile(
                    3200,
                    3201,
                    0
                );

            PlayerDeathGroundSettlementService service =
                new PlayerDeathGroundSettlementService(
                    world,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            PlayerDeathGroundSettlementService.Receipt receipt =
                service.settle(
                    commit,
                    tile,
                    "death-recipient"
                );

            require(
                receipt.playerId.equals(
                    victim.id()
                ) &&
                receipt.deathTick == 10L &&
                receipt.deathSequence == 1L &&
                receipt.deathTile.equals(
                    tile
                ) &&
                "death-recipient".equals(
                    receipt.recipientRef
                ) &&
                SETTLEMENT_AUTHORITY.equals(
                    receipt.settlementAuthority
                ),
                "receipt identity"
            );

            require(
                receipt.groundItems.size() == 3,
                "ground mutation count"
            );

            GroundItem coins =
                world.groundItems().findOwned(
                    995,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "death-recipient"
                );
            GroundItem whip =
                world.groundItems().findOwned(
                    4151,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "death-recipient"
                );
            GroundItem arrows =
                world.groundItems().findOwned(
                    892,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "death-recipient"
                );

            require(
                coins != null &&
                coins.amount == 60 &&
                whip != null &&
                whip.amount == 1 &&
                arrows != null &&
                arrows.amount == 30,
                "canonical lost amounts"
            );

            List<WorldGroundItemPresentationEvents.Event> events =
                world.groundItemPresentationEvents()
                    .pendingFor(
                        recipient.id(),
                        recipientGeneration,
                        System.currentTimeMillis()
                    );

            require(
                events.size() == 3,
                "live owner presentation events"
            );

            PlayerDeathGroundSettlementService.Receipt replay =
                service.settle(
                    commit,
                    tile,
                    "death-recipient"
                );

            require(
                replay == receipt &&
                service.size() == 1 &&
                world.groundItems().findOwned(
                    995,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "death-recipient"
                ).amount == 60,
                "replay duplicated ground amount"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void conflictingReplayRejected() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer victim =
            configuredPlayer();

        world.registerPlayer(
            victim,
            "conflict-victim"
        );

        try {
            PlayerDeathItemCommitService.CommitResult commit =
                commitLosses(
                    victim,
                    20L
                );

            PlayerDeathGroundSettlementService service =
                new PlayerDeathGroundSettlementService(
                    world,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            Tile first =
                new Tile(
                    3210,
                    3210,
                    0
                );

            service.settle(
                commit,
                first,
                "owner-a"
            );

            expect(
                IllegalStateException.class,
                () -> service.settle(
                    commit,
                    new Tile(
                        3211,
                        3210,
                        0
                    ),
                    "owner-a"
                ),
                "conflicting tile"
            );

            expect(
                IllegalStateException.class,
                () -> service.settle(
                    commit,
                    first,
                    "owner-b"
                ),
                "conflicting recipient"
            );

            require(
                world.groundItems().size() == 3,
                "conflicting replay changed ground"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void missingRecipientStillSettles() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer victim =
            configuredPlayer();

        world.registerPlayer(
            victim,
            "offline-victim"
        );

        try {
            PlayerDeathItemCommitService.CommitResult commit =
                commitLosses(
                    victim,
                    30L
                );
            Tile tile =
                new Tile(
                    3220,
                    3220,
                    0
                );

            PlayerDeathGroundSettlementService service =
                new PlayerDeathGroundSettlementService(
                    world,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            service.settle(
                commit,
                tile,
                "offline-recipient"
            );

            require(
                world.groundItems().findOwned(
                    995,
                    tile.x,
                    tile.y,
                    tile.plane,
                    "offline-recipient"
                ) != null &&
                world.groundItemPresentationEvents()
                    .size() == 0,
                "offline recipient canonical settlement"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void emptyLostBundleSettles() {
        World world =
            World.isolatedForTest(
                600L
            );
        WorldPlayer victim =
            configuredPlayer();

        world.registerPlayer(
            victim,
            "keep-all-victim"
        );

        try {
            PlayerDeathItemCommitService.CommitResult commit =
                commitKeepAll(
                    victim,
                    40L
                );

            PlayerDeathGroundSettlementService service =
                new PlayerDeathGroundSettlementService(
                    world,
                    SETTLEMENT_AUTHORITY,
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                );

            PlayerDeathGroundSettlementService.Receipt receipt =
                service.settle(
                    commit,
                    new Tile(
                        3230,
                        3230,
                        0
                    ),
                    "keep-all-owner"
                );

            require(
                receipt.groundItems.isEmpty() &&
                world.groundItems().size() == 0 &&
                service.size() == 1,
                "empty lost bundle"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static void authorityGuards() {
        World world =
            World.isolatedForTest(
                600L
            );

        try {
            expect(
                IllegalArgumentException.class,
                () -> new PlayerDeathGroundSettlementService(
                    world,
                    "EXACT_CURRENT_CLIENT",
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                ),
                "client settlement authority"
            );

            expect(
                IllegalArgumentException.class,
                () -> new PlayerDeathGroundSettlementService(
                    world,
                    "UNKNOWN_SERVER_AUTHORITY",
                    PlayerDeathGroundSettlementService
                        .OWNER_SCOPED_DEATH_TILE
                ),
                "unknown settlement authority"
            );
        } finally {
            cleanup(
                world
            );
        }
    }

    private static PlayerDeathItemCommitService.CommitResult
        commitLosses(
            WorldPlayer player,
            long tick
        ) {
        PlayerDeathItemResolutionService.Resolution resolution =
            resolve(
                player,
                tick,
                false
            );

        return new PlayerDeathItemCommitService(
            player,
            COMMIT_AUTHORITY
        ).commit(
            resolution
        );
    }

    private static PlayerDeathItemCommitService.CommitResult
        commitKeepAll(
            WorldPlayer player,
            long tick
        ) {
        PlayerDeathItemResolutionService.Resolution resolution =
            resolve(
                player,
                tick,
                true
            );

        return new PlayerDeathItemCommitService(
            player,
            COMMIT_AUTHORITY
        ).commit(
            resolution
        );
    }

    private static PlayerDeathItemResolutionService.Resolution
        resolve(
            WorldPlayer player,
            long tick,
            boolean keepAll
        ) {
        PlayerLifecycleService.DamageResult death =
            new PlayerLifecycleService(
                player,
                POLICY_AUTHORITY
            ).applyDamage(
                500,
                tick,
                "GROUND_SETTLEMENT_TEST",
                5L
            );

        require(
            death.died,
            "death fixture"
        );

        PlayerDeathItemResolutionService service =
            new PlayerDeathItemResolutionService(
                player,
                POLICY_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview =
            service.previewCurrentDeath();

        ArrayList<PlayerDeathItemResolutionService.Decision> decisions =
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                preview.carried) {
            int keep;

            if(keepAll) {
                keep = line.quantity;
            } else if(line.itemId == 995) {
                keep = 40;
            } else if(line.itemId == 15272) {
                keep = line.quantity;
            } else if(line.itemId == 4151) {
                keep = 0;
            } else if(line.itemId == 892) {
                keep = 20;
            } else {
                throw new AssertionError(
                    "unexpected item " +
                    line.itemId
                );
            }

            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    keep
                )
            );
        }

        return service.resolveCurrentDeath(
            preview,
            decisions
        );
    }

    private static WorldPlayer configuredPlayer() {
        WorldPlayer player =
            new WorldPlayer();

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
                    995,
                    100
                );
            inventory[1] =
                new BankState.Stack(
                    15272,
                    2
                );

            player.bank().restoreAccountState(
                bank,
                inventory,
                false
            );

            int[] items =
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] quantities =
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];

            Arrays.fill(
                items,
                -1
            );

            items[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ] = 4151;
            quantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ] = 1;

            items[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ] = 892;
            quantities[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ] = 50;

            player.equipment()
                .restoreAccountState(
                    items,
                    quantities
                );
        }

        return player;
    }

    private static void cleanup(
        World world
    ) {
        for(WorldPlayer player:
                world.players().snapshot())
            world.unregisterPlayer(
                player
            );

        world.close();
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ) {
        boolean observed = false;

        try {
            action.run();
        } catch(Throwable failure) {
            if(!type.isInstance(
                    failure))
                throw new AssertionError(
                    label+
                    " wrong failure "+
                    failure,
                    failure
                );
            observed = true;
        }

        require(
            observed,
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PlayerDeathGroundSettlementServiceTest() {}
}

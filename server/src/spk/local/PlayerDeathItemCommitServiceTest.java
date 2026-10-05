package spk.local;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PlayerDeathItemCommitServiceTest {
    private static final String POLICY_AUTHORITY =
        "CUSTOM_LOCALLAB_DEATH_POLICY_TEST";
    private static final String COMMIT_AUTHORITY =
        "CUSTOM_LOCALLAB_DEATH_COMMIT_TEST";

    public static void main(String[] args) {
        exactAtomicCommitAndReplay();
        stateDriftFailsBeforeMutation();
        crossPlayerRejected();
        authorityGuards();
        domainBoundary();

        System.out.println(
            "PLAYER_DEATH_ITEM_COMMIT_PASS " +
            "inventoryPartialKeep=true " +
            "inventoryFullKeep=true " +
            "equipmentLoss=true " +
            "equipmentPartialKeep=true " +
            "lostLinesSemantic=true " +
            "exactDeathIdentity=true " +
            "replayIdempotent=true " +
            "stateDriftNoPartialMutation=true " +
            "crossPlayerRejected=true " +
            "groundMutation=false " +
            "lootRecipientOwned=false " +
            "protocolIndependent=true"
        );
    }

    private static void exactAtomicCommitAndReplay() {
        WorldPlayer player =
            configuredPlayer();

        kill(
            player,
            10L,
            "PVP_TEST"
        );

        PlayerDeathItemResolutionService resolver =
            new PlayerDeathItemResolutionService(
                player,
                POLICY_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview =
            resolver.previewCurrentDeath();

        List<PlayerDeathItemResolutionService.Decision> decisions =
            new ArrayList<>();

        for (PlayerDeathItemResolutionService.CarriedLine line :
                preview.carried) {
            int keep;

            if (line.itemId == 995) {
                keep = 40;
            } else if (line.itemId == 15272) {
                keep = line.quantity;
            } else if (line.itemId == 4151) {
                keep = 0;
            } else if (line.itemId == 892) {
                keep = 20;
            } else {
                throw new AssertionError(
                    "unexpected fixture item " +
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

        PlayerDeathItemResolutionService.Resolution resolution =
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        PlayerDeathItemCommitService commits =
            new PlayerDeathItemCommitService(
                player,
                COMMIT_AUTHORITY
            );

        PlayerDeathItemCommitService.CommitResult result =
            commits.commit(
                resolution
            );

        BankState.Stack coins =
            player.bank().inventoryAt(
                0
            );
        BankState.Stack food =
            player.bank().inventoryAt(
                1
            );

        require(
            coins != null &&
            coins.itemId == 995 &&
            coins.qty == 40,
            "coin partial keep"
        );
        require(
            food != null &&
            food.itemId == 15272 &&
            food.qty == 2,
            "food full keep"
        );
        require(
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
            ) == -1 &&
            player.equipment().quantityAt(
                EquipmentSlot.WEAPON
            ) == 0,
            "weapon loss"
        );
        require(
            player.equipment().itemAt(
                EquipmentSlot.AMMO
            ) == 892 &&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            ) == 20,
            "ammo partial keep"
        );

        require(
            result.playerId.equals(
                player.id()
            ) &&
            result.deathTick == 10L &&
            result.deathSequence == 1L &&
            "PVP_TEST".equals(
                result.deathCause
            ) &&
            result.keptTotalQuantity == 62 &&
            result.lostTotalQuantity == 91 &&
            POLICY_AUTHORITY.equals(
                result.policyAuthority
            ) &&
            COMMIT_AUTHORITY.equals(
                result.commitAuthority
            ),
            "commit metadata"
        );

        requireLost(
            result,
            PlayerDeathItemResolutionService
                .Source.INVENTORY,
            0,
            null,
            995,
            60
        );
        requireLost(
            result,
            PlayerDeathItemResolutionService
                .Source.EQUIPMENT,
            EquipmentSlot.WEAPON
                .equipmentIndex,
            EquipmentSlot.WEAPON,
            4151,
            1
        );
        requireLost(
            result,
            PlayerDeathItemResolutionService
                .Source.EQUIPMENT,
            EquipmentSlot.AMMO
                .equipmentIndex,
            EquipmentSlot.AMMO,
            892,
            30
        );

        require(
            result.lost.size() == 3,
            "lost line count"
        );

        PlayerDeathItemCommitService.CommitResult replay =
            commits.commit(
                resolution
            );

        require(
            replay == result &&
            commits.size() == 1 &&
            commits.get(
                resolution.deathSequence
            ) == result,
            "commit replay"
        );

        require(
            player.bank().inventoryAt(0).qty == 40 &&
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
            ) == -1 &&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            ) == 20,
            "replay mutated postimage"
        );
    }

    private static void stateDriftFailsBeforeMutation() {
        WorldPlayer player =
            configuredPlayer();

        kill(
            player,
            20L,
            "DRIFT_TEST"
        );

        PlayerDeathItemResolutionService resolver =
            new PlayerDeathItemResolutionService(
                player,
                POLICY_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview =
            resolver.previewCurrentDeath();

        List<PlayerDeathItemResolutionService.Decision> decisions =
            new ArrayList<>();

        for (PlayerDeathItemResolutionService.CarriedLine line :
                preview.carried) {
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    0
                )
            );
        }

        PlayerDeathItemResolutionService.Resolution resolution =
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        synchronized (player.mutationLock()) {
            int[] ids =
                new int[
                    BankState.INVENTORY_CAPACITY
                ];
            int[] qty =
                new int[
                    BankState.INVENTORY_CAPACITY
                ];

            Arrays.fill(
                ids,
                -1
            );
            ids[0] = 995;
            qty[0] = 99;
            ids[1] = 15272;
            qty[1] = 2;

            player.bank().replaceInventorySemantic(
                ids,
                qty
            );
        }

        int weaponBefore =
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
            );
        int ammoBefore =
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            );

        PlayerDeathItemCommitService commits =
            new PlayerDeathItemCommitService(
                player,
                COMMIT_AUTHORITY
            );

        expect(
            IllegalStateException.class,
            () -> commits.commit(
                resolution
            ),
            "drift commit"
        );

        require(
            player.bank().inventoryAt(0) != null &&
            player.bank().inventoryAt(0).qty == 99 &&
            player.bank().inventoryAt(1) != null &&
            player.bank().inventoryAt(1).qty == 2 &&
            player.equipment().itemAt(
                EquipmentSlot.WEAPON
            ) == weaponBefore &&
            player.equipment().quantityAt(
                EquipmentSlot.AMMO
            ) == ammoBefore &&
            commits.size() == 0,
            "drift failure partially mutated"
        );
    }

    private static void crossPlayerRejected() {
        WorldPlayer first =
            configuredPlayer();
        WorldPlayer second =
            configuredPlayer();

        kill(
            first,
            30L,
            "CROSS_PLAYER"
        );
        kill(
            second,
            30L,
            "CROSS_PLAYER"
        );

        PlayerDeathItemResolutionService resolver =
            new PlayerDeathItemResolutionService(
                first,
                POLICY_AUTHORITY
            );
        PlayerDeathItemResolutionService.DeathPreview preview =
            resolver.previewCurrentDeath();

        List<PlayerDeathItemResolutionService.Decision> decisions =
            new ArrayList<>();

        for (PlayerDeathItemResolutionService.CarriedLine line :
                preview.carried) {
            decisions.add(
                new PlayerDeathItemResolutionService.Decision(
                    line.lineId,
                    line.quantity
                )
            );
        }

        PlayerDeathItemResolutionService.Resolution resolution =
            resolver.resolveCurrentDeath(
                preview,
                decisions
            );

        PlayerDeathItemCommitService foreign =
            new PlayerDeathItemCommitService(
                second,
                COMMIT_AUTHORITY
            );

        expect(
            IllegalArgumentException.class,
            () -> foreign.commit(
                resolution
            ),
            "cross player commit"
        );

        require(
            second.bank().inventoryAt(0).qty == 100 &&
            second.equipment().itemAt(
                EquipmentSlot.WEAPON
            ) == 4151,
            "cross player mutated"
        );
    }

    private static void authorityGuards() {
        WorldPlayer player =
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            () -> new PlayerDeathItemCommitService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client commit authority"
        );

        expect(
            IllegalArgumentException.class,
            () -> new PlayerDeathItemCommitService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown commit authority"
        );
    }

    private static void domainBoundary() {
        for (Class<?> type :
                new Class<?>[]{
                    PlayerDeathItemCommitService.class,
                    PlayerDeathItemCommitService
                        .CommitResult.class,
                    PlayerDeathItemCommitService
                        .LostLine.class
                }) {
            for (Field field :
                    type.getDeclaredFields()) {
                String name =
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if (name.contains("packet") ||
                    name.contains("opcode") ||
                    name.contains("widget") ||
                    name.contains("clientindex") ||
                    name.contains("sceneindex") ||
                    name.contains("recipient") ||
                    name.contains("ground")) {
                    throw new AssertionError(
                        "protocol/loot policy leaked " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }
    }

    private static WorldPlayer configuredPlayer() {
        WorldPlayer player =
            new WorldPlayer();

        synchronized (player.mutationLock()) {
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

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause
    ) {
        PlayerLifecycleService.DamageResult result =
            new PlayerLifecycleService(
                player,
                POLICY_AUTHORITY
            ).applyDamage(
                500,
                tick,
                cause,
                5L
            );

        require(
            result.died &&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static void requireLost(
        PlayerDeathItemCommitService.CommitResult result,
        PlayerDeathItemResolutionService.Source source,
        int sourceIndex,
        EquipmentSlot equipmentSlot,
        int itemId,
        int quantity
    ) {
        PlayerDeathItemCommitService.LostLine found =
            null;

        for (PlayerDeathItemCommitService.LostLine line :
                result.lost) {
            if (line.itemId == itemId) {
                if (found != null) {
                    throw new AssertionError(
                        "duplicate lost item " +
                        itemId
                    );
                }
                found = line;
            }
        }

        require(
            found != null &&
            found.source == source &&
            found.sourceIndex == sourceIndex &&
            found.equipmentSlot == equipmentSlot &&
            found.quantity == quantity,
            "lost line item=" + itemId
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ) {
        boolean observed = false;

        try {
            action.run();
        } catch (Throwable failure) {
            if (!type.isInstance(
                    failure)) {
                throw new AssertionError(
                    label +
                    " wrong failure " +
                    failure,
                    failure
                );
            }
            observed = true;
        }

        require(
            observed,
            label + " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private PlayerDeathItemCommitServiceTest() {}
}

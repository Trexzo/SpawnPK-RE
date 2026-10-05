package spk.local;

import java.util.Arrays;
import java.util.Objects;

/**
 * Explicit LocalLab playability policy for a truly new persistent account.
 *
 * This is not reconstructed original SpawnPK economy authority. It exists so
 * the playable server no longer inherits milestone/test constructor residue
 * such as Bloodrend being pre-equipped.
 */
final class PlayabilityStarterAccountPolicy {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYABILITY";

    static final int STARTER_WEAPON=4151;
    static final int FOOD_ITEM=15272;
    static final int COINS_ITEM=995;
    static final int DEATH_RUNE_ITEM=560;
    static final int BLOOD_RUNE_ITEM=565;

    static final int INVENTORY_FOOD=12;
    static final int BANK_COINS=100_000;
    static final int BANK_DEATH_RUNES=2_500;
    static final int BANK_BLOOD_RUNES=1_500;
    static final int BANK_FOOD=100;

    static final class Result {
        final int weapon;
        final int inventorySlots;
        final int bankSlots;
        final int runEnergy;
        final boolean runEnabled;

        Result(
            int weapon,
            int inventorySlots,
            int bankSlots,
            int runEnergy,
            boolean runEnabled
        ){
            this.weapon=weapon;
            this.inventorySlots=inventorySlots;
            this.bankSlots=bankSlots;
            this.runEnergy=runEnergy;
            this.runEnabled=runEnabled;
        }

        @Override public String toString(){
            return "StarterAccount{"+
                "authority="+AUTHORITY+
                ",weapon="+weapon+
                ",inventorySlots="+inventorySlots+
                ",bankSlots="+bankSlots+
                ",runEnabled="+runEnabled+
                ",runEnergy="+runEnergy+
                "}";
        }
    }

    static Result apply(
        WorldPlayer player
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        requireItem(
            STARTER_WEAPON
        );
        requireItem(
            FOOD_ITEM
        );
        requireItem(
            COINS_ITEM
        );
        requireItem(
            DEATH_RUNE_ITEM
        );
        requireItem(
            BLOOD_RUNE_ITEM
        );

        synchronized(player.mutationLock()){
            BankState bank=
                player.bank();
            EquipmentState equipment=
                player.equipment();
            MovementState movement=
                player.movement();
            PlayerState state=
                player.playerState();

            BankState.Stack[] starterBank=
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] starterInventory=
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];

            starterBank[0]=
                new BankState.Stack(
                    COINS_ITEM,
                    BANK_COINS
                );
            starterBank[1]=
                new BankState.Stack(
                    DEATH_RUNE_ITEM,
                    BANK_DEATH_RUNES
                );
            starterBank[2]=
                new BankState.Stack(
                    BLOOD_RUNE_ITEM,
                    BANK_BLOOD_RUNES
                );
            starterBank[3]=
                new BankState.Stack(
                    FOOD_ITEM,
                    BANK_FOOD
                );

            for(int slot=0;
                slot<INVENTORY_FOOD;
                slot++)
                starterInventory[slot]=
                    new BankState.Stack(
                        FOOD_ITEM,
                        1
                    );

            bank.restoreAccountState(
                starterBank,
                starterInventory,
                false
            );

            int[] equipmentItems=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] equipmentQuantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];

            Arrays.fill(
                equipmentItems,
                -1
            );

            equipment.restoreAccountState(
                equipmentItems,
                equipmentQuantities
            );
            equipment.setWeapon(
                STARTER_WEAPON
            );

            movement.setPersistentRun(
                true
            );
            movement.setRunEnergy(
                100
            );

            for(int skill=0;
                skill<PlayerState.COMBAT_SKILL_COUNT;
                skill++){
                state.setCurrentLevel(
                    skill,
                    99
                );
                state.setXp(
                    skill,
                    PlayerState.XP_99
                );
            }

            state.setSpecialEnergy(
                100
            );
            state.setNegativeEffects(
                0,
                0,
                0
            );

            return new Result(
                equipment.weapon(),
                bank.inventorySlots(),
                bank.bankSlots(),
                movement.runEnergy(),
                movement.persistentRun()
            );
        }
    }

    private static void requireItem(
        int itemId
    ){
        if(!ItemCatalog.exists(itemId))
            throw new IllegalStateException(
                "starter item missing from current catalog item="+
                itemId
            );
    }

    private PlayabilityStarterAccountPolicy(){}
}

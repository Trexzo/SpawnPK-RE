package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Exactly-once settlement of an already-decided player-death disposition.
 *
 * Risk/zone classification, keep-count policy, killer attribution and ground
 * publicization remain outside this service. This layer only commits the
 * supplied semantic loss decisions into carried state + canonical owner-scoped
 * ground state.
 */
final class PlayerDeathGroundSettlementService {
    static final String SETTLEMENT_AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYER_DEATH_GROUND_SETTLEMENT";

    static final class Receipt {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final Tile deathTile;
        final String recipientRef;
        final int keptQuantity;
        final int lostQuantity;
        final List<GroundItemRegistry.BatchMutation> groundMutations;

        private Receipt(
            PlayerDeathItemResolutionService.Resolution resolution,
            Tile deathTile,
            String recipientRef,
            List<GroundItemRegistry.BatchMutation> groundMutations
        ){
            this.playerId=resolution.playerId;
            this.deathTick=resolution.deathTick;
            this.deathSequence=resolution.deathSequence;
            this.deathTile=deathTile;
            this.recipientRef=recipientRef;
            this.keptQuantity=resolution.keptTotalQuantity();
            this.lostQuantity=resolution.lostTotalQuantity();
            this.groundMutations=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        groundMutations
                    )
                );
        }
    }

    private final World world;
    private final WorldPlayer player;
    private final GroundItemRegistry groundItems;
    private final LinkedHashMap<Long,Receipt> receipts=
        new LinkedHashMap<>();

    PlayerDeathGroundSettlementService(
        World world,
        WorldPlayer player
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.groundItems=world.groundItems();
    }

    Receipt settle(
        PlayerDeathItemResolutionService.Resolution resolution,
        Tile deathTile,
        String recipientRef
    ){
        PlayerDeathItemResolutionService.Resolution checked=
            Objects.requireNonNull(
                resolution,
                "resolution"
            );
        Tile tile=
            Objects.requireNonNull(
                deathTile,
                "deathTile"
            );
        String owner=requireText(
            recipientRef,
            "recipientRef"
        );

        if(!player.id().equals(
                checked.playerId))
            throw new IllegalArgumentException(
                "death resolution belongs to another player"
            );

        synchronized(player.mutationLock()){
            synchronized(groundItems){
                requireCurrentDeath(
                    checked
                );

                Receipt existing=
                    receipts.get(
                        checked.deathSequence
                    );

                if(existing!=null){
                    requireReplayMatches(
                        existing,
                        checked,
                        tile,
                        owner
                    );
                    return existing;
                }

                int[] inventoryItems=
                    new int[
                        BankState.INVENTORY_CAPACITY
                    ];
                int[] inventoryQuantities=
                    new int[
                        BankState.INVENTORY_CAPACITY
                    ];
                int[] equipmentItems=
                    new int[
                        EquipmentState.EQUIPMENT_SLOTS
                    ];
                int[] equipmentQuantities=
                    new int[
                        EquipmentState.EQUIPMENT_SLOTS
                    ];

                Arrays.fill(
                    inventoryItems,
                    -1
                );
                Arrays.fill(
                    equipmentItems,
                    -1
                );

                for(PlayerDeathItemResolutionService.Disposition disposition:
                        checked.dispositions){
                    PlayerDeathItemResolutionService.CarriedLine line=
                        disposition.line;

                    if(line.source==
                            PlayerDeathItemResolutionService.Source.INVENTORY){
                        inventoryItems[line.sourceIndex]=
                            line.itemId;
                        inventoryQuantities[line.sourceIndex]=
                            line.quantity;
                    }else{
                        equipmentItems[line.sourceIndex]=
                            line.itemId;
                        equipmentQuantities[line.sourceIndex]=
                            line.quantity;
                    }
                }

                requireCurrentCarriedMatches(
                    inventoryItems,
                    inventoryQuantities,
                    equipmentItems,
                    equipmentQuantities
                );

                int[] nextInventoryItems=
                    inventoryItems.clone();
                int[] nextInventoryQuantities=
                    inventoryQuantities.clone();
                int[] nextEquipmentItems=
                    equipmentItems.clone();
                int[] nextEquipmentQuantities=
                    equipmentQuantities.clone();

                ArrayList<GroundItemRegistry.AddRequest> requests=
                    new ArrayList<>();

                for(PlayerDeathItemResolutionService.Disposition disposition:
                        checked.dispositions){
                    if(disposition.lostAmount==0)
                        continue;

                    PlayerDeathItemResolutionService.CarriedLine line=
                        disposition.line;

                    if(line.source==
                            PlayerDeathItemResolutionService.Source.INVENTORY){
                        nextInventoryQuantities[line.sourceIndex]=
                            disposition.keptAmount;
                        if(disposition.keptAmount==0)
                            nextInventoryItems[line.sourceIndex]=-1;
                    }else{
                        nextEquipmentQuantities[line.sourceIndex]=
                            disposition.keptAmount;
                        if(disposition.keptAmount==0)
                            nextEquipmentItems[line.sourceIndex]=-1;
                    }

                    requests.add(
                        new GroundItemRegistry.AddRequest(
                            line.itemId,
                            disposition.lostAmount,
                            tile,
                            owner,
                            checked.deathTick,
                            false
                        )
                    );
                }

                List<GroundItemRegistry.BatchMutation> groundMutations=
                    Collections.emptyList();

                try{
                    groundMutations=
                        groundItems.addBatchDetailed(
                            requests
                        );

                    player.bank()
                        .replaceInventorySemantic(
                            nextInventoryItems,
                            nextInventoryQuantities
                        );
                    player.equipment()
                        .restoreAccountState(
                            nextEquipmentItems,
                            nextEquipmentQuantities
                        );
                }catch(RuntimeException failure){
                    rollbackGround(
                        groundMutations,
                        failure
                    );
                    restoreCarried(
                        inventoryItems,
                        inventoryQuantities,
                        equipmentItems,
                        equipmentQuantities,
                        failure
                    );
                    throw failure;
                }catch(Error failure){
                    rollbackGround(
                        groundMutations,
                        failure
                    );
                    restoreCarried(
                        inventoryItems,
                        inventoryQuantities,
                        equipmentItems,
                        equipmentQuantities,
                        failure
                    );
                    throw failure;
                }

                Receipt receipt=
                    new Receipt(
                        checked,
                        tile,
                        owner,
                        groundMutations
                    );

                receipts.put(
                    checked.deathSequence,
                    receipt
                );

                return receipt;
            }
        }
    }

    Receipt get(long deathSequence){
        synchronized(player.mutationLock()){
            return receipts.get(
                deathSequence
            );
        }
    }

    int size(){
        synchronized(player.mutationLock()){
            return receipts.size();
        }
    }

    private void requireCurrentDeath(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        PlayerLifecycleState lifecycle=
            player.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathSequence()!=
                resolution.deathSequence||
           lifecycle.deathTick()!=
                resolution.deathTick)
            throw new IllegalStateException(
                "player death identity changed before settlement sequence="+
                resolution.deathSequence
            );
    }

    private void requireCurrentCarriedMatches(
        int[] inventoryItems,
        int[] inventoryQuantities,
        int[] equipmentItems,
        int[] equipmentQuantities
    ){
        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.InventorySlotSnapshot snapshot=
                player.bank()
                    .inventorySlotSnapshot(
                        slot
                    );

            int actualItem=
                snapshot.occupied
                    ?snapshot.itemId
                    :-1;
            int actualQuantity=
                snapshot.occupied
                    ?snapshot.quantity
                    :0;

            if(actualItem!=inventoryItems[slot]||
               actualQuantity!=inventoryQuantities[slot])
                throw new IllegalStateException(
                    "inventory death preimage changed slot="+
                    slot
                );
        }

        for(int slot=0;
            slot<EquipmentState.EQUIPMENT_SLOTS;
            slot++)
            if(player.equipment().itemAt(slot)!=
                    equipmentItems[slot]||
               player.equipment().quantityAt(slot)!=
                    equipmentQuantities[slot])
                throw new IllegalStateException(
                    "equipment death preimage changed slot="+
                    slot
                );
    }

    private static void requireReplayMatches(
        Receipt existing,
        PlayerDeathItemResolutionService.Resolution resolution,
        Tile deathTile,
        String recipientRef
    ){
        if(existing.deathTick!=
                resolution.deathTick||
           !existing.deathTile.equals(
                deathTile)||
           !existing.recipientRef.equals(
                recipientRef)||
           existing.keptQuantity!=
                resolution.keptTotalQuantity()||
           existing.lostQuantity!=
                resolution.lostTotalQuantity())
            throw new IllegalStateException(
                "conflicting player death settlement replay sequence="+
                resolution.deathSequence
            );
    }

    private void rollbackGround(
        List<GroundItemRegistry.BatchMutation> groundMutations,
        Throwable primary
    ){
        if(groundMutations==null||
           groundMutations.isEmpty())
            return;

        try{
            groundItems.rollbackBatchDetailed(
                groundMutations
            );
        }catch(Throwable rollbackFailure){
            primary.addSuppressed(
                rollbackFailure
            );
        }
    }

    private void restoreCarried(
        int[] inventoryItems,
        int[] inventoryQuantities,
        int[] equipmentItems,
        int[] equipmentQuantities,
        Throwable primary
    ){
        try{
            player.bank()
                .replaceInventorySemantic(
                    inventoryItems,
                    inventoryQuantities
                );
            player.equipment()
                .restoreAccountState(
                    equipmentItems,
                    equipmentQuantities
                );
        }catch(Throwable restoreFailure){
            primary.addSuppressed(
                restoreFailure
            );
        }
    }

    private static String requireText(
        String value,
        String label
    ){
        if(value==null)
            throw new NullPointerException(
                label
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                label+" blank"
            );

        return clean;
    }
}

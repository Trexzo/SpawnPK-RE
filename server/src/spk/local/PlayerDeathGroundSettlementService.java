package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Commits one already-resolved player-death item disposition into canonical
 * carried state + owner-scoped world ground-item state.
 *
 * This class owns no keep-count, item-value, Protect Item, skull, killer
 * selection, publicization, expiry or economy policy. Those decisions must be
 * resolved before settlement.
 */
final class PlayerDeathGroundSettlementService {
    static final String OWNER_SCOPED_DEATH_TILE =
        "OWNER_SCOPED_DEATH_TILE";

    static final class SettledGroundItem {
        final long groundItemId;
        final int itemId;
        final int settledAmount;
        final int stackAmountAfter;

        private SettledGroundItem(
            GroundItemRegistry.BatchMutation mutation
        ){
            GroundItemRegistry.BatchMutation checked =
                Objects.requireNonNull(
                    mutation,
                    "mutation"
                );

            this.groundItemId = checked.groundItemId;
            this.itemId = checked.itemId;
            this.settledAmount = checked.addedAmount;
            this.stackAmountAfter = checked.newAmount;
        }
    }

    static final class Receipt {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final Tile deathTile;
        final String recipientRef;
        final String settlementAuthority;
        final String settlementPolicy;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final List<SettledGroundItem> groundItems;

        private Receipt(
            PlayerDeathItemResolutionService.Resolution resolution,
            String recipientRef,
            String settlementAuthority,
            String settlementPolicy,
            List<SettledGroundItem> groundItems
        ){
            this.playerId = resolution.playerId;
            this.deathTick = resolution.deathTick;
            this.deathSequence = resolution.deathSequence;
            this.deathTile = resolution.deathTile;
            this.recipientRef = recipientRef;
            this.settlementAuthority = settlementAuthority;
            this.settlementPolicy = settlementPolicy;
            this.keptTotalQuantity =
                resolution.keptTotalQuantity();
            this.lostTotalQuantity =
                resolution.lostTotalQuantity();
            this.groundItems =
                Collections.unmodifiableList(
                    new ArrayList<>(
                        groundItems
                    )
                );
        }
    }

    private static final class SettlementIdentity {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final Tile deathTile;
        final String policyAuthority;
        final String recipientRef;
        final List<RowIdentity> rows;

        SettlementIdentity(
            PlayerDeathItemResolutionService.Resolution resolution,
            String recipientRef
        ){
            this.playerId = resolution.playerId;
            this.deathTick = resolution.deathTick;
            this.deathSequence = resolution.deathSequence;
            this.deathCause = resolution.deathCause;
            this.deathTile = resolution.deathTile;
            this.policyAuthority = resolution.policyAuthority;
            this.recipientRef = recipientRef;

            ArrayList<RowIdentity> copy =
                new ArrayList<>();

            for(PlayerDeathItemResolutionService.Disposition disposition:
                    resolution.dispositions)
                copy.add(
                    new RowIdentity(
                        disposition
                    )
                );

            this.rows =
                Collections.unmodifiableList(
                    copy
                );
        }

        boolean sameAs(
            PlayerDeathItemResolutionService.Resolution resolution,
            String recipient
        ){
            if(!playerId.equals(
                    resolution.playerId
                )||
               deathTick != resolution.deathTick||
               deathSequence != resolution.deathSequence||
               !deathCause.equals(
                    resolution.deathCause
                )||
               !deathTile.equals(
                    resolution.deathTile
                )||
               !policyAuthority.equals(
                    resolution.policyAuthority
                )||
               !recipientRef.equals(
                    recipient
                )||
               rows.size() !=
                    resolution.dispositions.size())
                return false;

            for(int i=0;i<rows.size();i++)
                if(!rows.get(i).sameAs(
                        resolution.dispositions.get(i)
                    ))
                    return false;

            return true;
        }
    }

    private static final class RowIdentity {
        final int lineId;
        final PlayerDeathItemResolutionService.Source source;
        final int sourceIndex;
        final EquipmentSlot equipmentSlot;
        final int itemId;
        final int quantity;
        final int keptAmount;
        final int lostAmount;

        RowIdentity(
            PlayerDeathItemResolutionService.Disposition disposition
        ){
            PlayerDeathItemResolutionService.Disposition checked =
                Objects.requireNonNull(
                    disposition,
                    "disposition"
                );

            PlayerDeathItemResolutionService.CarriedLine line =
                checked.line;

            this.lineId = line.lineId;
            this.source = line.source;
            this.sourceIndex = line.sourceIndex;
            this.equipmentSlot = line.equipmentSlot;
            this.itemId = line.itemId;
            this.quantity = line.quantity;
            this.keptAmount = checked.keptAmount;
            this.lostAmount = checked.lostAmount;
        }

        boolean sameAs(
            PlayerDeathItemResolutionService.Disposition disposition
        ){
            PlayerDeathItemResolutionService.CarriedLine line =
                disposition.line;

            return lineId == line.lineId&&
                source == line.source&&
                sourceIndex == line.sourceIndex&&
                equipmentSlot == line.equipmentSlot&&
                itemId == line.itemId&&
                quantity == line.quantity&&
                keptAmount == disposition.keptAmount&&
                lostAmount == disposition.lostAmount;
        }
    }

    private final World world;
    private final WorldPlayer player;
    private final GroundItemRegistry groundItems;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerLifecycleState lifecycle;
    private final String settlementAuthority;
    private final String settlementPolicy;

    private final LinkedHashMap<Long,Receipt> receipts =
        new LinkedHashMap<>();
    private final LinkedHashMap<Long,SettlementIdentity> identities =
        new LinkedHashMap<>();

    PlayerDeathGroundSettlementService(
        World world,
        WorldPlayer player,
        String settlementAuthority,
        String settlementPolicy
    ){
        this.world =
            Objects.requireNonNull(
                world,
                "world"
            );
        this.player =
            Objects.requireNonNull(
                player,
                "player"
            );
        this.groundItems =
            this.world.groundItems();
        this.bank =
            this.player.bank();
        this.equipment =
            this.player.equipment();
        this.lifecycle =
            this.player.lifecycle();
        this.settlementAuthority =
            requireGameplayAuthority(
                settlementAuthority
            );
        this.settlementPolicy =
            requireText(
                settlementPolicy,
                "settlementPolicy"
            );

        if(!OWNER_SCOPED_DEATH_TILE.equals(
                this.settlementPolicy
            ))
            throw new IllegalArgumentException(
                "unsupported player death settlement policy "+
                this.settlementPolicy
            );
    }

    boolean isBoundTo(
        World expectedWorld,
        WorldPlayer expectedPlayer
    ){
        return world == expectedWorld&&
            player == expectedPlayer&&
            groundItems ==
                expectedWorld.groundItems();
    }

    synchronized Receipt settle(
        PlayerDeathItemResolutionService.Resolution resolution,
        String recipientRef
    ){
        PlayerDeathItemResolutionService.Resolution checked =
            Objects.requireNonNull(
                resolution,
                "resolution"
            );
        String recipient =
            requireText(
                recipientRef,
                "recipientRef"
            );

        Receipt existing =
            receipts.get(
                checked.deathSequence
            );

        if(existing != null){
            SettlementIdentity identity =
                identities.get(
                    checked.deathSequence
                );

            if(identity == null||
               !identity.sameAs(
                    checked,
                    recipient
                ))
                throw new IllegalStateException(
                    "conflicting player death settlement player="+
                    checked.playerId+
                    " deathSequence="+
                    checked.deathSequence
                );

            return existing;
        }

        final List<GroundItemRegistry.BatchMutation> mutations;

        synchronized(player.mutationLock()){
            requireCurrentDeath(
                checked
            );

            BankState.Stack[] nextInventory =
                snapshotInventory();
            int[] nextEquipmentItems =
                equipment.containerItems();
            int[] nextEquipmentQuantities =
                equipment.containerQuantities();

            ArrayList<GroundItemRegistry.AddRequest> requests =
                new ArrayList<>();

            for(PlayerDeathItemResolutionService.Disposition disposition:
                    checked.dispositions){
                PlayerDeathItemResolutionService.Disposition row =
                    Objects.requireNonNull(
                        disposition,
                        "disposition"
                    );

                validateAndApplyPostimage(
                    row,
                    nextInventory,
                    nextEquipmentItems,
                    nextEquipmentQuantities
                );

                if(row.lostAmount > 0)
                    requests.add(
                        new GroundItemRegistry.AddRequest(
                            row.line.itemId,
                            row.lostAmount,
                            checked.deathTile,
                            recipient,
                            checked.deathTick,
                            false
                        )
                    );
            }

            /*
             * GroundItemRegistry batch admission is all-or-nothing. It is
             * deliberately committed before the deterministic carried-state
             * postimage so overflow/invalid ground admission cannot delete
             * victim items without producing their corresponding loot.
             */
            mutations =
                groundItems.addBatchDetailed(
                    requests
                );

            /*
             * Both postimages were fully constructed and validated before the
             * ground commit. These exact-length state commits do not execute
             * caller policy and cannot reject for capacity.
             */
            bank.restoreInventoryState(
                nextInventory
            );
            equipment.restoreAccountState(
                nextEquipmentItems,
                nextEquipmentQuantities
            );
        }

        ArrayList<SettledGroundItem> groundRows =
            new ArrayList<>();

        for(GroundItemRegistry.BatchMutation mutation:
                mutations)
            groundRows.add(
                new SettledGroundItem(
                    mutation
                )
            );

        SettlementIdentity identity =
            new SettlementIdentity(
                checked,
                recipient
            );
        Receipt receipt =
            new Receipt(
                checked,
                recipient,
                settlementAuthority,
                settlementPolicy,
                groundRows
            );

        identities.put(
            checked.deathSequence,
            identity
        );
        receipts.put(
            checked.deathSequence,
            receipt
        );

        publishLiveOwnerScene(
            recipient,
            mutations
        );

        return receipt;
    }

    synchronized Receipt get(
        long deathSequence
    ){
        return receipts.get(
            deathSequence
        );
    }

    synchronized int size(){
        return receipts.size();
    }

    String settlementAuthority(){
        return settlementAuthority;
    }

    String settlementPolicy(){
        return settlementPolicy;
    }

    private void requireCurrentDeath(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        if(!player.id().equals(
                resolution.playerId
            ))
            throw new IllegalArgumentException(
                "death resolution belongs to another player expected="+
                player.id()+
                " actual="+
                resolution.playerId
            );

        if(!lifecycle.dead()||
           lifecycle.deathTick()!=
                resolution.deathTick||
           lifecycle.deathSequence()!=
                resolution.deathSequence||
           !safeCause(
                lifecycle.cause()
            ).equals(
                resolution.deathCause
            ))
            throw new IllegalStateException(
                "player death identity changed before settlement id="+
                player.id()
            );
    }

    private void validateAndApplyPostimage(
        PlayerDeathItemResolutionService.Disposition disposition,
        BankState.Stack[] nextInventory,
        int[] nextEquipmentItems,
        int[] nextEquipmentQuantities
    ){
        PlayerDeathItemResolutionService.CarriedLine line =
            disposition.line;

        if(disposition.keptAmount < 0||
           disposition.lostAmount < 0||
           disposition.keptAmount+
                disposition.lostAmount !=
                    line.quantity)
            throw new IllegalStateException(
                "invalid death disposition lineId="+
                line.lineId
            );

        if(line.source ==
                PlayerDeathItemResolutionService.Source.INVENTORY){
            if(line.sourceIndex < 0||
               line.sourceIndex >=
                    BankState.INVENTORY_CAPACITY)
                throw new IllegalStateException(
                    "invalid inventory death source index="+
                    line.sourceIndex
                );

            BankState.Stack current =
                bank.inventoryAt(
                    line.sourceIndex
                );

            if(current == null||
               current.itemId != line.itemId||
               current.qty != line.quantity)
                throw new IllegalStateException(
                    "inventory death preimage changed lineId="+
                    line.lineId
                );

            nextInventory[line.sourceIndex] =
                disposition.keptAmount == 0
                    ? null
                    : new BankState.Stack(
                        line.itemId,
                        disposition.keptAmount,
                        current.tab
                    );
            return;
        }

        if(line.source !=
                PlayerDeathItemResolutionService.Source.EQUIPMENT||
           line.equipmentSlot == null||
           line.sourceIndex !=
                line.equipmentSlot.equipmentIndex)
            throw new IllegalStateException(
                "invalid equipment death source lineId="+
                line.lineId
            );

        if(equipment.itemAt(
                line.sourceIndex
            ) != line.itemId||
           equipment.quantityAt(
                line.sourceIndex
            ) != line.quantity)
            throw new IllegalStateException(
                "equipment death preimage changed lineId="+
                line.lineId
            );

        if(disposition.keptAmount == 0){
            nextEquipmentItems[line.sourceIndex] = -1;
            nextEquipmentQuantities[line.sourceIndex] = 0;
        }else{
            nextEquipmentItems[line.sourceIndex] =
                line.itemId;
            nextEquipmentQuantities[line.sourceIndex] =
                disposition.keptAmount;
        }
    }

    private BankState.Stack[] snapshotInventory(){
        BankState.Stack[] out =
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];

        for(int slot=0;slot<out.length;slot++){
            BankState.Stack current =
                bank.inventoryAt(
                    slot
                );

            if(current != null)
                out[slot] =
                    new BankState.Stack(
                        current.itemId,
                        current.qty,
                        current.tab
                    );
        }

        return out;
    }

    private void publishLiveOwnerScene(
        String recipientRef,
        List<GroundItemRegistry.BatchMutation> mutations
    ){
        WorldPlayer recipient =
            world.players().byName(
                recipientRef
            );

        if(recipient == null)
            return;

        long generation =
            recipient.generation();

        if(!world.players().owns(
                recipient,
                generation
            ))
            return;

        long now =
            System.currentTimeMillis();

        for(GroundItemRegistry.BatchMutation mutation:
                mutations)
            if(mutation.created())
                world.groundItemPresentationEvents()
                    .enqueueSpawn(
                        now,
                        mutation,
                        recipient,
                        generation
                    );
            else
                world.groundItemPresentationEvents()
                    .enqueueAmount(
                        now,
                        mutation,
                        recipient,
                        generation
                    );
    }

    private static String safeCause(
        String value
    ){
        return value == null||
               value.trim().isEmpty()
            ? "UNSPECIFIED"
            : value;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean =
            requireText(
                value,
                "settlementAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(
                clean
            )||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean
            ))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define player death settlement actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value == null)
            throw new NullPointerException(
                name
            );

        String clean =
            value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                name
            );

        return clean;
    }
}

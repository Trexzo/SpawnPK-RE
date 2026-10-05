package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Protocol-independent execution of one already-resolved player-death item
 * disposition.
 *
 * This service deliberately does not decide item value, keep count, skull,
 * Protect Item, loot recipient, visibility timing, or persistence policy.
 * Those decisions remain caller-owned. It only commits the exact semantic
 * disposition supplied by PlayerDeathItemResolutionService.
 */
final class PlayerDeathGroundSettlementService {
    static final class GroundDrop {
        final long groundItemId;
        final int itemId;
        final int amount;
        final Tile tile;
        final String owner;

        private GroundDrop(
            long groundItemId,
            int itemId,
            int amount,
            Tile tile,
            String owner
        ){
            this.groundItemId=groundItemId;
            this.itemId=itemId;
            this.amount=amount;
            this.tile=tile;
            this.owner=owner;
        }
    }

    static final class Settlement {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final Tile deathTile;
        final String lootOwner;
        final List<GroundDrop> drops;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final String settlementAuthority;

        private Settlement(
            PlayerDeathItemResolutionService.Resolution resolution,
            Tile deathTile,
            String lootOwner,
            List<GroundDrop> drops,
            String settlementAuthority
        ){
            this.playerId=resolution.playerId;
            this.deathTick=resolution.deathTick;
            this.deathSequence=resolution.deathSequence;
            this.deathTile=deathTile;
            this.lootOwner=lootOwner;
            this.drops=Collections.unmodifiableList(
                new ArrayList<>(drops)
            );
            this.keptTotalQuantity=
                resolution.keptTotalQuantity();
            this.lostTotalQuantity=
                resolution.lostTotalQuantity();
            this.settlementAuthority=settlementAuthority;
        }
    }

    private final World world;
    private final WorldPlayer player;
    private final GroundItemRegistry groundItems;
    private final String settlementAuthority;
    private final LinkedHashMap<Long,Settlement> settledByDeathSequence=
        new LinkedHashMap<>();

    PlayerDeathGroundSettlementService(
        World world,
        WorldPlayer player,
        String settlementAuthority
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.player=Objects.requireNonNull(
            player,
            "player"
        );
        this.groundItems=this.world.groundItems();
        this.settlementAuthority=
            requireGameplayAuthority(
                settlementAuthority
            );
    }

    Settlement settle(
        PlayerDeathItemResolutionService.Resolution resolution,
        String lootOwner
    ){
        PlayerDeathItemResolutionService.Resolution checked=
            Objects.requireNonNull(
                resolution,
                "resolution"
            );

        Settlement settlement;
        List<GroundItemRegistry.BatchMutation>
            groundMutations;

        synchronized(player.mutationLock()){
            requireExactCurrentDeath(checked);

            Settlement existing=
                settledByDeathSequence.get(
                    checked.deathSequence
                );
            if(existing!=null)
                return existing;

            int[] inventoryItems=
                new int[
                    BankState.INVENTORY_CAPACITY
                ];
            int[] inventoryQuantities=
                new int[
                    BankState.INVENTORY_CAPACITY
                ];

            int occupied=0;
            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.InventorySlotSnapshot snapshot=
                    player.bank()
                        .inventorySlotSnapshot(
                            slot
                        );

                if(snapshot.occupied){
                    inventoryItems[slot]=
                        snapshot.itemId;
                    inventoryQuantities[slot]=
                        snapshot.quantity;
                    occupied++;
                }else{
                    inventoryItems[slot]=-1;
                    inventoryQuantities[slot]=0;
                }
            }

            int[] equipmentItems=
                player.equipment()
                    .containerItems();
            int[] equipmentQuantities=
                player.equipment()
                    .containerQuantities();

            for(int item:equipmentItems)
                if(item>=0)
                    occupied++;

            if(occupied!=
                    checked.dispositions.size())
                throw new IllegalStateException(
                    "carried item line count changed before death settlement id="+
                    player.id()+
                    " expected="+
                    checked.dispositions.size()+
                    " actual="+
                    occupied
                );

            ArrayList<GroundItemRegistry.AddRequest>
                groundRequests=
                    new ArrayList<>();

            for(PlayerDeathItemResolutionService.Disposition disposition:
                    checked.dispositions){
                PlayerDeathItemResolutionService.CarriedLine line=
                    Objects.requireNonNull(
                        disposition.line,
                        "line"
                    );

                if(disposition.keptAmount<0||
                   disposition.lostAmount<0||
                   disposition.keptAmount+
                        disposition.lostAmount!=
                            line.quantity)
                    throw new IllegalStateException(
                        "invalid death disposition lineId="+
                        line.lineId
                    );

                if(line.source==
                        PlayerDeathItemResolutionService.Source.INVENTORY){
                    int slot=line.sourceIndex;
                    if(slot<0||
                       slot>=inventoryItems.length||
                       inventoryItems[slot]!=line.itemId||
                       inventoryQuantities[slot]!=line.quantity)
                        throw new IllegalStateException(
                            "inventory death preimage changed lineId="+
                            line.lineId+
                            " slot="+
                            slot
                        );

                    if(disposition.keptAmount==0){
                        inventoryItems[slot]=-1;
                        inventoryQuantities[slot]=0;
                    }else{
                        inventoryQuantities[slot]=
                            disposition.keptAmount;
                    }
                }else if(line.source==
                        PlayerDeathItemResolutionService.Source.EQUIPMENT){
                    int slot=line.sourceIndex;
                    if(slot<0||
                       slot>=equipmentItems.length||
                       equipmentItems[slot]!=line.itemId||
                       equipmentQuantities[slot]!=line.quantity)
                        throw new IllegalStateException(
                            "equipment death preimage changed lineId="+
                            line.lineId+
                            " slot="+
                            slot
                        );

                    if(disposition.keptAmount==0){
                        equipmentItems[slot]=-1;
                        equipmentQuantities[slot]=0;
                    }else{
                        equipmentQuantities[slot]=
                            disposition.keptAmount;
                    }
                }else{
                    throw new IllegalStateException(
                        "unsupported death source lineId="+
                        line.lineId
                    );
                }

                if(disposition.lostAmount>0)
                    groundRequests.add(
                        new GroundItemRegistry.AddRequest(
                            line.itemId,
                            disposition.lostAmount,
                            checked.deathTile,
                            lootOwner,
                            checked.deathTick,
                            false
                        )
                    );
            }

            /*
             * GroundItemRegistry validates the complete batch before applying
             * any mutation. Player postimages were fully validated above.
             * While player.mutationLock is held, no carried-state writer may
             * change the source slots between validation and commit.
             */
            groundMutations=
                groundItems.addBatchDetailed(
                    groundRequests
                );

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

            ArrayList<GroundDrop> drops=
                new ArrayList<>(
                    groundMutations.size()
                );
            for(GroundItemRegistry.BatchMutation mutation:
                    groundMutations)
                drops.add(
                    new GroundDrop(
                        mutation.groundItemId,
                        mutation.itemId,
                        mutation.addedAmount,
                        mutation.tile,
                        mutation.owner
                    )
                );

            settlement=
                new Settlement(
                    checked,
                    checked.deathTile,
                    lootOwner,
                    drops,
                    settlementAuthority
                );

            settledByDeathSequence.put(
                checked.deathSequence,
                settlement
            );
        }

        publishLiveScene(
            groundMutations,
            settlement.deathTile,
            settlement.lootOwner
        );
        return settlement;
    }

    Settlement get(long deathSequence){
        synchronized(player.mutationLock()){
            return settledByDeathSequence.get(
                deathSequence
            );
        }
    }

    int size(){
        synchronized(player.mutationLock()){
            return settledByDeathSequence.size();
        }
    }

    private void requireExactCurrentDeath(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        if(!player.id().equals(
                resolution.playerId))
            throw new IllegalArgumentException(
                "death resolution belongs to another player expected="+
                player.id()+
                " actual="+
                resolution.playerId
            );

        PlayerLifecycleState lifecycle=
            player.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathTick()!=
                resolution.deathTick||
           lifecycle.deathSequence()!=
                resolution.deathSequence||
           !safeCause(
                lifecycle.cause()
            ).equals(
                resolution.deathCause)||
           player.movement().x()!=
                resolution.deathTile.x||
           player.movement().y()!=
                resolution.deathTile.y||
           player.movement().plane()!=
                resolution.deathTile.plane)
            throw new IllegalStateException(
                "player death identity/tile changed before settlement id="+
                player.id()
            );
    }

    private void publishLiveScene(
        List<GroundItemRegistry.BatchMutation> mutations,
        Tile deathTile,
        String lootOwner
    ){
        if(mutations.isEmpty())
            return;

        long now=System.currentTimeMillis();

        if(lootOwner!=null){
            WorldPlayer recipient=
                world.players().byName(
                    lootOwner
                );
            if(recipient==null)
                return;

            publishForRecipient(
                mutations,
                deathTile,
                recipient,
                recipient.generation(),
                now
            );
            return;
        }

        for(WorldPlayer recipient:
                world.players().snapshot()){
            long generation=
                recipient.generation();

            if(!world.players().owns(
                    recipient,
                    generation))
                continue;

            publishForRecipient(
                mutations,
                deathTile,
                recipient,
                generation,
                now
            );
        }
    }

    private void publishForRecipient(
        List<GroundItemRegistry.BatchMutation> mutations,
        Tile deathTile,
        WorldPlayer recipient,
        long generation,
        long now
    ){
        MovementState movement=
            recipient.movement();

        if(movement.plane()!=deathTile.plane||
           !movement.insideCurrentLoadedRegion(
                deathTile.x,
                deathTile.y
            ))
            return;

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

    private static String safeCause(String value){
        return value==null||
               value.trim().isEmpty()
            ?"UNSPECIFIED"
            :value;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "settlementAuthority"
            );

        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "settlementAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define death settlement actual="+
                clean
            );

        return clean;
    }
}

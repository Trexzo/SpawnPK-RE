package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Exactly-once settlement of already-resolved player death losses into
 * canonical World ground-item state.
 *
 * The caller owns recipient choice and death-tile policy. This service only
 * materializes the immutable lost-item facts and publishes live owner-scoped
 * scene events when the selected recipient is currently world-owned.
 */
final class PlayerDeathGroundLootSettlementService {
    static final String OWNER_SCOPED_DEATH_TILE=
        "OWNER_SCOPED_DEATH_TILE";

    static final class SettledGroundItem {
        final long groundItemId;
        final int itemId;
        final int settledAmount;
        final int stackAmountAfter;

        private SettledGroundItem(
            GroundItemRegistry.BatchMutation mutation
        ){
            GroundItemRegistry.BatchMutation checked=
                Objects.requireNonNull(
                    mutation,
                    "mutation"
                );

            this.groundItemId=checked.groundItemId;
            this.itemId=checked.itemId;
            this.settledAmount=checked.addedAmount;
            this.stackAmountAfter=checked.newAmount;
        }
    }

    static final class Receipt {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final Tile deathTile;
        final String recipientRef;
        final String settlementAuthority;
        final String settlementPolicy;
        final List<SettledGroundItem> groundItems;

        private Receipt(
            PlayerDeathItemSettlementService.Settlement settlement,
            Tile deathTile,
            String recipientRef,
            String settlementAuthority,
            String settlementPolicy,
            List<SettledGroundItem> groundItems
        ){
            this.playerId=settlement.playerId;
            this.deathTick=settlement.deathTick;
            this.deathSequence=settlement.deathSequence;
            this.deathCause=settlement.deathCause;
            this.deathTile=deathTile;
            this.recipientRef=recipientRef;
            this.settlementAuthority=
                settlementAuthority;
            this.settlementPolicy=
                settlementPolicy;
            this.groundItems=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        groundItems
                    )
                );
        }
    }

    private static final class DeathKey {
        final EntityId playerId;
        final long deathSequence;

        DeathKey(
            EntityId playerId,
            long deathSequence
        ){
            this.playerId=
                Objects.requireNonNull(
                    playerId,
                    "playerId"
                );
            this.deathSequence=
                deathSequence;
        }

        @Override public boolean equals(
            Object other
        ){
            if(this==other)
                return true;
            if(!(other instanceof DeathKey))
                return false;

            DeathKey key=(DeathKey)other;

            return deathSequence==
                    key.deathSequence&&
                playerId.equals(
                    key.playerId
                );
        }

        @Override public int hashCode(){
            int result=playerId.hashCode();
            result=31*result+
                Long.valueOf(
                    deathSequence
                ).hashCode();
            return result;
        }
    }

    private static final class SettlementIdentity {
        final PlayerDeathItemSettlementService.Settlement settlement;
        final Tile deathTile;
        final String recipientRef;

        SettlementIdentity(
            PlayerDeathItemSettlementService.Settlement settlement,
            Tile deathTile,
            String recipientRef
        ){
            this.settlement=
                Objects.requireNonNull(
                    settlement,
                    "settlement"
                );
            this.deathTile=
                Objects.requireNonNull(
                    deathTile,
                    "deathTile"
                );
            this.recipientRef=
                requireText(
                    recipientRef,
                    "recipientRef"
                );
        }

        boolean sameAs(
            PlayerDeathItemSettlementService.Settlement otherSettlement,
            Tile otherTile,
            String otherRecipient
        ){
            if(!sameSettlement(
                    settlement,
                    otherSettlement))
                return false;

            return deathTile.equals(
                    otherTile
                )&&
                recipientRef.equals(
                    otherRecipient
                );
        }
    }

    private final World world;
    private final GroundItemRegistry groundItems;
    private final String settlementAuthority;
    private final String settlementPolicy;

    private final LinkedHashMap<DeathKey,Receipt>
        receipts=
            new LinkedHashMap<>();
    private final LinkedHashMap<DeathKey,SettlementIdentity>
        identities=
            new LinkedHashMap<>();

    PlayerDeathGroundLootSettlementService(
        World world,
        String settlementAuthority,
        String settlementPolicy
    ){
        World checkedWorld=
            Objects.requireNonNull(
                world,
                "world"
            );

        this.world=checkedWorld;
        this.groundItems=
            checkedWorld.groundItems();
        this.settlementAuthority=
            requireGameplayAuthority(
                settlementAuthority
            );
        this.settlementPolicy=
            requireText(
                settlementPolicy,
                "settlementPolicy"
            );

        if(!OWNER_SCOPED_DEATH_TILE.equals(
                this.settlementPolicy
            ))
            throw new IllegalArgumentException(
                "unsupported player death loot settlement policy "+
                this.settlementPolicy
            );
    }

    boolean isBoundTo(
        World expectedWorld
    ){
        return world==expectedWorld&&
            groundItems==
                expectedWorld.groundItems();
    }

    synchronized Receipt settle(
        PlayerDeathItemSettlementService.Settlement settlement,
        Tile deathTile,
        String recipientRef
    ){
        PlayerDeathItemSettlementService.Settlement
            checkedSettlement=
                Objects.requireNonNull(
                    settlement,
                    "settlement"
                );
        Tile checkedTile=
            Objects.requireNonNull(
                deathTile,
                "deathTile"
            );
        String checkedRecipient=
            requireText(
                recipientRef,
                "recipientRef"
            );

        if(checkedSettlement.deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+
                checkedSettlement.deathTick
            );
        if(checkedSettlement.deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                checkedSettlement.deathSequence
            );

        DeathKey key=
            new DeathKey(
                checkedSettlement.playerId,
                checkedSettlement.deathSequence
            );

        Receipt existing=
            receipts.get(key);

        if(existing!=null){
            SettlementIdentity identity=
                identities.get(key);

            if(identity==null||
               !identity.sameAs(
                    checkedSettlement,
                    checkedTile,
                    checkedRecipient))
                throw new IllegalStateException(
                    "conflicting player death loot settlement id="+
                    checkedSettlement.playerId+
                    " deathSequence="+
                    checkedSettlement.deathSequence
                );

            return existing;
        }

        ArrayList<GroundItemRegistry.AddRequest>
            requests=
                new ArrayList<>();

        for(PlayerDeathItemSettlementService.LostLine line:
                checkedSettlement.lost){
            PlayerDeathItemSettlementService.LostLine
                checkedLine=
                    Objects.requireNonNull(
                        line,
                        "lostLine"
                    );

            if(checkedLine.itemId<0||
               checkedLine.quantity<=0)
                throw new IllegalArgumentException(
                    "invalid player death lost line item="+
                    checkedLine.itemId+
                    " qty="+
                    checkedLine.quantity
                );

            requests.add(
                new GroundItemRegistry.AddRequest(
                    checkedLine.itemId,
                    checkedLine.quantity,
                    checkedTile,
                    checkedRecipient,
                    checkedSettlement.deathTick,
                    false
                )
            );
        }

        List<GroundItemRegistry.BatchMutation>
            mutations=
                groundItems.addBatchDetailed(
                    requests
                );

        ArrayList<SettledGroundItem> rows=
            new ArrayList<>();

        for(GroundItemRegistry.BatchMutation mutation:
                mutations)
            rows.add(
                new SettledGroundItem(
                    mutation
                )
            );

        SettlementIdentity identity=
            new SettlementIdentity(
                checkedSettlement,
                checkedTile,
                checkedRecipient
            );

        Receipt receipt=
            new Receipt(
                checkedSettlement,
                checkedTile,
                checkedRecipient,
                settlementAuthority,
                settlementPolicy,
                rows
            );

        identities.put(
            key,
            identity
        );
        receipts.put(
            key,
            receipt
        );

        publishLiveOwnerScene(
            checkedRecipient,
            mutations
        );

        return receipt;
    }

    synchronized Receipt get(
        EntityId playerId,
        long deathSequence
    ){
        return receipts.get(
            new DeathKey(
                playerId,
                deathSequence
            )
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

    private void publishLiveOwnerScene(
        String recipientRef,
        List<GroundItemRegistry.BatchMutation> mutations
    ){
        WorldPlayer recipient=
            world.players().byName(
                recipientRef
            );

        if(recipient==null)
            return;

        long generation=
            recipient.generation();

        if(!world.players().owns(
                recipient,
                generation
            ))
            return;

        long now=
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

    private static boolean sameSettlement(
        PlayerDeathItemSettlementService.Settlement left,
        PlayerDeathItemSettlementService.Settlement right
    ){
        if(left==right)
            return true;

        if(right==null||
           !left.playerId.equals(
                right.playerId)||
           left.deathTick!=right.deathTick||
           left.deathSequence!=
                right.deathSequence||
           !left.deathCause.equals(
                right.deathCause)||
           left.keptTotalQuantity!=
                right.keptTotalQuantity||
           left.lostTotalQuantity!=
                right.lostTotalQuantity||
           !left.resolutionAuthority.equals(
                right.resolutionAuthority)||
           !left.settlementAuthority.equals(
                right.settlementAuthority)||
           left.lost.size()!=
                right.lost.size())
            return false;

        for(int i=0;
            i<left.lost.size();
            i++){
            PlayerDeathItemSettlementService.LostLine
                a=left.lost.get(i);
            PlayerDeathItemSettlementService.LostLine
                b=right.lost.get(i);

            if(a.source!=b.source||
               a.sourceIndex!=b.sourceIndex||
               a.equipmentSlot!=
                    b.equipmentSlot||
               a.itemId!=b.itemId||
               a.quantity!=b.quantity)
                return false;
        }

        return true;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "settlementAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define player death loot settlement actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(name);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(name);

        return clean;
    }
}

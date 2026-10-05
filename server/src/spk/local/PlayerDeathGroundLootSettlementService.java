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
 * materializes immutable lost-item facts and publishes live owner-scoped scene
 * events when the selected recipient is currently world-owned.
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

    static final class CanonicalCommit {
        final Receipt receipt;
        final String recipientRef;
        private final List<GroundItemRegistry.BatchMutation>
            mutations;
        private final boolean publishNeeded;

        private CanonicalCommit(
            Receipt receipt,
            String recipientRef,
            List<GroundItemRegistry.BatchMutation> mutations,
            boolean publishNeeded
        ){
            this.receipt=
                Objects.requireNonNull(
                    receipt,
                    "receipt"
                );
            this.recipientRef=
                requireText(
                    recipientRef,
                    "recipientRef"
                );
            this.mutations=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        mutations
                    )
                );
            this.publishNeeded=
                publishNeeded;
        }
    }

    static final class PreparedLoot {
        private final PlayerDeathItemResolutionService.Resolution
            resolution;
        private final Tile deathTile;
        private final String recipientRef;
        private final GroundItemRegistry.PreparedBatchAdd
            groundBatch;

        private PreparedLoot(
            PlayerDeathItemResolutionService.Resolution resolution,
            Tile deathTile,
            String recipientRef,
            GroundItemRegistry.PreparedBatchAdd groundBatch
        ){
            this.resolution=
                Objects.requireNonNull(
                    resolution,
                    "resolution"
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
            this.groundBatch=
                Objects.requireNonNull(
                    groundBatch,
                    "groundBatch"
                );
        }

        int canonicalStackCount(){
            return groundBatch
                .canonicalStackCount();
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
            return sameSettlement(
                    settlement,
                    otherSettlement
                )&&
                deathTile.equals(
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
    private final Object receiptLock=
        new Object();

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

    /**
     * Preflight ground capacity from the semantic death resolution before any
     * carried-item mutation. Callers that need cross-owner atomicity should
     * keep GroundItemRegistry's monitor from this prepare through
     * commitPrepared(...).
     */
    PreparedLoot prepare(
        PlayerDeathItemResolutionService.Resolution resolution,
        Tile deathTile,
        String recipientRef
    ){
        PlayerDeathItemResolutionService.Resolution checked=
            Objects.requireNonNull(
                resolution,
                "resolution"
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

        validateResolutionIdentity(
            checked
        );

        List<GroundItemRegistry.AddRequest>
            requests=
                requestsFromResolution(
                    checked,
                    checkedTile,
                    checkedRecipient
                );

        synchronized(groundItems){
            return new PreparedLoot(
                checked,
                checkedTile,
                checkedRecipient,
                groundItems
                    .prepareAddBatchDetailed(
                        requests
                    )
            );
        }
    }

    CanonicalCommit commitPreparedCanonical(
        PreparedLoot prepared,
        PlayerDeathItemSettlementService.Settlement settlement
    ){
        PreparedLoot checkedPrepared=
            Objects.requireNonNull(
                prepared,
                "prepared"
            );
        PlayerDeathItemSettlementService.Settlement
            checkedSettlement=
                Objects.requireNonNull(
                    settlement,
                    "settlement"
                );

        validateSettlementMatchesResolution(
            checkedPrepared.resolution,
            checkedSettlement
        );

        synchronized(groundItems){
            Receipt existing=
                existingReceipt(
                    checkedSettlement,
                    checkedPrepared.deathTile,
                    checkedPrepared.recipientRef
                );

            if(existing!=null)
                return new CanonicalCommit(
                    existing,
                    checkedPrepared.recipientRef,
                    Collections.emptyList(),
                    false
                );

            List<GroundItemRegistry.BatchMutation>
                mutations=
                    groundItems
                        .commitPreparedAddBatch(
                            checkedPrepared.groundBatch
                        );

            Receipt receipt=
                recordReceipt(
                    checkedSettlement,
                    checkedPrepared.deathTile,
                    checkedPrepared.recipientRef,
                    mutations
                );

            return new CanonicalCommit(
                receipt,
                checkedPrepared.recipientRef,
                mutations,
                true
            );
        }
    }

    Receipt publishCommitted(
        CanonicalCommit committed
    ){
        CanonicalCommit checked=
            Objects.requireNonNull(
                committed,
                "committed"
            );

        if(checked.publishNeeded)
            publishLiveOwnerScene(
                checked.recipientRef,
                checked.mutations
            );

        return checked.receipt;
    }

    Receipt commitPrepared(
        PreparedLoot prepared,
        PlayerDeathItemSettlementService.Settlement settlement
    ){
        return publishCommitted(
            commitPreparedCanonical(
                prepared,
                settlement
            )
        );
    }

    /**
     * Convenience immediate settlement retained for non-cross-owner callers.
     * The atomic live PvP composition uses prepare(...)/commitPrepared(...).
     */
    Receipt settle(
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

        validateSettlementIdentity(
            checkedSettlement
        );

        List<GroundItemRegistry.BatchMutation>
            mutations;
        Receipt receipt;

        synchronized(groundItems){
            Receipt existing=
                existingReceipt(
                    checkedSettlement,
                    checkedTile,
                    checkedRecipient
                );

            if(existing!=null)
                return existing;

            mutations=
                groundItems.addBatchDetailed(
                    requestsFromSettlement(
                        checkedSettlement,
                        checkedTile,
                        checkedRecipient
                    )
                );

            receipt=
                recordReceipt(
                    checkedSettlement,
                    checkedTile,
                    checkedRecipient,
                    mutations
                );
        }

        publishLiveOwnerScene(
            checkedRecipient,
            mutations
        );

        return receipt;
    }

    Receipt get(
        EntityId playerId,
        long deathSequence
    ){
        synchronized(receiptLock){
            return receipts.get(
                new DeathKey(
                    playerId,
                    deathSequence
                )
            );
        }
    }

    int size(){
        synchronized(receiptLock){
            return receipts.size();
        }
    }

    String settlementAuthority(){
        return settlementAuthority;
    }

    String settlementPolicy(){
        return settlementPolicy;
    }

    private Receipt existingReceipt(
        PlayerDeathItemSettlementService.Settlement settlement,
        Tile deathTile,
        String recipientRef
    ){
        DeathKey key=
            new DeathKey(
                settlement.playerId,
                settlement.deathSequence
            );

        synchronized(receiptLock){
            Receipt existing=
                receipts.get(
                    key
                );

            if(existing==null)
                return null;

            SettlementIdentity identity=
                identities.get(
                    key
                );

            if(identity==null||
               !identity.sameAs(
                    settlement,
                    deathTile,
                    recipientRef))
                throw new IllegalStateException(
                    "conflicting player death loot settlement id="+
                    settlement.playerId+
                    " deathSequence="+
                    settlement.deathSequence
                );

            return existing;
        }
    }

    private Receipt recordReceipt(
        PlayerDeathItemSettlementService.Settlement settlement,
        Tile deathTile,
        String recipientRef,
        List<GroundItemRegistry.BatchMutation> mutations
    ){
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
                settlement,
                deathTile,
                recipientRef
            );

        Receipt receipt=
            new Receipt(
                settlement,
                deathTile,
                recipientRef,
                settlementAuthority,
                settlementPolicy,
                rows
            );

        DeathKey key=
            new DeathKey(
                settlement.playerId,
                settlement.deathSequence
            );

        synchronized(receiptLock){
            if(receipts.containsKey(
                    key))
                throw new IllegalStateException(
                    "player death loot receipt raced id="+
                    settlement.playerId+
                    " deathSequence="+
                    settlement.deathSequence
                );

            identities.put(
                key,
                identity
            );
            receipts.put(
                key,
                receipt
            );
        }

        return receipt;
    }

    private static List<GroundItemRegistry.AddRequest>
        requestsFromResolution(
            PlayerDeathItemResolutionService.Resolution resolution,
            Tile deathTile,
            String recipientRef
        )
    {
        ArrayList<GroundItemRegistry.AddRequest>
            requests=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.Disposition disposition:
                resolution.dispositions){
            PlayerDeathItemResolutionService.Disposition checked=
                Objects.requireNonNull(
                    disposition,
                    "disposition"
                );

            if(checked.lostAmount<=0)
                continue;

            requests.add(
                new GroundItemRegistry.AddRequest(
                    checked.line.itemId,
                    checked.lostAmount,
                    deathTile,
                    recipientRef,
                    resolution.deathTick,
                    false
                )
            );
        }

        return requests;
    }

    private static List<GroundItemRegistry.AddRequest>
        requestsFromSettlement(
            PlayerDeathItemSettlementService.Settlement settlement,
            Tile deathTile,
            String recipientRef
        )
    {
        ArrayList<GroundItemRegistry.AddRequest>
            requests=
                new ArrayList<>();

        for(PlayerDeathItemSettlementService.LostLine line:
                settlement.lost){
            PlayerDeathItemSettlementService.LostLine checked=
                Objects.requireNonNull(
                    line,
                    "lostLine"
                );

            if(checked.itemId<0||
               checked.quantity<=0)
                throw new IllegalArgumentException(
                    "invalid player death lost line item="+
                    checked.itemId+
                    " qty="+
                    checked.quantity
                );

            requests.add(
                new GroundItemRegistry.AddRequest(
                    checked.itemId,
                    checked.quantity,
                    deathTile,
                    recipientRef,
                    settlement.deathTick,
                    false
                )
            );
        }

        return requests;
    }

    private static void validateResolutionIdentity(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        if(resolution.deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+
                resolution.deathTick
            );
        if(resolution.deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                resolution.deathSequence
            );
    }

    private static void validateSettlementIdentity(
        PlayerDeathItemSettlementService.Settlement settlement
    ){
        if(settlement.deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+
                settlement.deathTick
            );
        if(settlement.deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                settlement.deathSequence
            );
    }

    private static void validateSettlementMatchesResolution(
        PlayerDeathItemResolutionService.Resolution resolution,
        PlayerDeathItemSettlementService.Settlement settlement
    ){
        validateResolutionIdentity(
            resolution
        );
        validateSettlementIdentity(
            settlement
        );

        if(!resolution.playerId.equals(
                settlement.playerId)||
           resolution.deathTick!=
                settlement.deathTick||
           resolution.deathSequence!=
                settlement.deathSequence||
           !resolution.deathCause.equals(
                settlement.deathCause)||
           !resolution.policyAuthority.equals(
                settlement.resolutionAuthority))
            throw new IllegalStateException(
                "player death item/ground resolution identity mismatch"
            );

        ArrayList<PlayerDeathItemResolutionService.Disposition>
            lost=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.Disposition disposition:
                resolution.dispositions)
            if(disposition.lostAmount>0)
                lost.add(
                    disposition
                );

        if(lost.size()!=
                settlement.lost.size())
            throw new IllegalStateException(
                "player death lost-line count changed before ground commit"
            );

        for(int i=0;i<lost.size();i++){
            PlayerDeathItemResolutionService.Disposition
                disposition=lost.get(i);
            PlayerDeathItemSettlementService.LostLine
                line=settlement.lost.get(i);

            if(disposition.line.source!=
                    line.source||
               disposition.line.sourceIndex!=
                    line.sourceIndex||
               disposition.line.equipmentSlot!=
                    line.equipmentSlot||
               disposition.line.itemId!=
                    line.itemId||
               disposition.lostAmount!=
                    line.quantity)
                throw new IllegalStateException(
                    "player death lost-line facts changed before ground commit index="+
                    i
                );
        }
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

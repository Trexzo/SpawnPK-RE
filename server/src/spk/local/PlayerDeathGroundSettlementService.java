package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Exactly-once settlement of one committed player-death lost-item bundle into
 * canonical World ground-item state.
 *
 * Death keep/loss policy and carried-state mutation stay outside this service.
 * The caller supplies the death tile and ground-owner/recipient reference.
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
        final List<SettledGroundItem> groundItems;

        private Receipt(
            PlayerDeathItemCommitService.CommitResult commit,
            Tile deathTile,
            String recipientRef,
            String settlementAuthority,
            String settlementPolicy,
            List<SettledGroundItem> groundItems
        ){
            this.playerId = commit.playerId;
            this.deathTick = commit.deathTick;
            this.deathSequence = commit.deathSequence;
            this.deathTile = deathTile;
            this.recipientRef = recipientRef;
            this.settlementAuthority = settlementAuthority;
            this.settlementPolicy = settlementPolicy;
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
        final Tile deathTile;
        final String recipientRef;
        final List<LostIdentity> lost;

        SettlementIdentity(
            PlayerDeathItemCommitService.CommitResult commit,
            Tile deathTile,
            String recipientRef
        ){
            this.playerId = commit.playerId;
            this.deathTick = commit.deathTick;
            this.deathSequence = commit.deathSequence;
            this.deathTile = deathTile;
            this.recipientRef = recipientRef;

            ArrayList<LostIdentity> rows =
                new ArrayList<>();

            for(PlayerDeathItemCommitService.LostLine line:
                    commit.lost)
                rows.add(
                    new LostIdentity(
                        line
                    )
                );

            this.lost =
                Collections.unmodifiableList(
                    rows
                );
        }

        boolean sameAs(
            PlayerDeathItemCommitService.CommitResult commit,
            Tile tile,
            String recipient
        ){
            if(!playerId.equals(
                    commit.playerId
                )||
               deathTick != commit.deathTick||
               deathSequence != commit.deathSequence||
               !deathTile.equals(tile)||
               !recipientRef.equals(recipient)||
               lost.size() != commit.lost.size())
                return false;

            for(int i=0;i<lost.size();i++)
                if(!lost.get(i).sameAs(
                        commit.lost.get(i)))
                    return false;

            return true;
        }
    }

    private static final class LostIdentity {
        final PlayerDeathItemResolutionService.Source source;
        final int sourceIndex;
        final EquipmentSlot equipmentSlot;
        final int itemId;
        final int quantity;

        LostIdentity(
            PlayerDeathItemCommitService.LostLine line
        ){
            this.source = line.source;
            this.sourceIndex = line.sourceIndex;
            this.equipmentSlot = line.equipmentSlot;
            this.itemId = line.itemId;
            this.quantity = line.quantity;
        }

        boolean sameAs(
            PlayerDeathItemCommitService.LostLine line
        ){
            return source == line.source&&
                sourceIndex == line.sourceIndex&&
                equipmentSlot == line.equipmentSlot&&
                itemId == line.itemId&&
                quantity == line.quantity;
        }
    }

    private final World world;
    private final GroundItemRegistry groundItems;
    private final String settlementAuthority;
    private final String settlementPolicy;
    private final LinkedHashMap<String,Receipt> receipts =
        new LinkedHashMap<>();
    private final LinkedHashMap<String,SettlementIdentity> identities =
        new LinkedHashMap<>();

    PlayerDeathGroundSettlementService(
        World world,
        String settlementAuthority,
        String settlementPolicy
    ){
        World checkedWorld =
            Objects.requireNonNull(
                world,
                "world"
            );

        this.world = checkedWorld;
        this.groundItems = checkedWorld.groundItems();
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
                this.settlementPolicy))
            throw new IllegalArgumentException(
                "unsupported player death settlement policy "+
                this.settlementPolicy
            );
    }

    boolean isBoundTo(
        World expectedWorld
    ){
        return world == expectedWorld&&
            groundItems == expectedWorld.groundItems();
    }

    synchronized Receipt settle(
        PlayerDeathItemCommitService.CommitResult commit,
        Tile deathTile,
        String recipientRef
    ){
        PlayerDeathItemCommitService.CommitResult checked =
            Objects.requireNonNull(
                commit,
                "commit"
            );
        Tile checkedTile =
            Objects.requireNonNull(
                deathTile,
                "deathTile"
            );
        String checkedRecipient =
            requireText(
                recipientRef,
                "recipientRef"
            );

        if(checked.deathTick < 0L)
            throw new IllegalArgumentException(
                "deathTick="+
                checked.deathTick
            );
        if(checked.deathSequence <= 0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                checked.deathSequence
            );

        String key =
            identityKey(
                checked.playerId,
                checked.deathSequence
            );

        Receipt existing =
            receipts.get(
                key
            );

        if(existing != null){
            SettlementIdentity identity =
                identities.get(
                    key
                );

            if(identity == null||
               !identity.sameAs(
                    checked,
                    checkedTile,
                    checkedRecipient))
                throw new IllegalStateException(
                    "conflicting player death ground settlement player="+
                    checked.playerId+
                    " deathSequence="+
                    checked.deathSequence
                );

            return existing;
        }

        ArrayList<GroundItemRegistry.AddRequest> requests =
            new ArrayList<>();

        for(PlayerDeathItemCommitService.LostLine line:
                checked.lost){
            PlayerDeathItemCommitService.LostLine row =
                Objects.requireNonNull(
                    line,
                    "lost line"
                );

            if(row.itemId < 0||
               row.quantity <= 0)
                throw new IllegalArgumentException(
                    "invalid lost item line item="+
                    row.itemId+
                    " quantity="+
                    row.quantity
                );

            requests.add(
                new GroundItemRegistry.AddRequest(
                    row.itemId,
                    row.quantity,
                    checkedTile,
                    checkedRecipient,
                    checked.deathTick,
                    false
                )
            );
        }

        List<GroundItemRegistry.BatchMutation> mutations =
            groundItems.addBatchDetailed(
                requests
            );

        ArrayList<SettledGroundItem> rows =
            new ArrayList<>();

        for(GroundItemRegistry.BatchMutation mutation:
                mutations)
            rows.add(
                new SettledGroundItem(
                    mutation
                )
            );

        SettlementIdentity identity =
            new SettlementIdentity(
                checked,
                checkedTile,
                checkedRecipient
            );

        Receipt receipt =
            new Receipt(
                checked,
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
            identityKey(
                Objects.requireNonNull(
                    playerId,
                    "playerId"
                ),
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
                generation))
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

    private static String identityKey(
        EntityId playerId,
        long deathSequence
    ){
        if(deathSequence <= 0L)
            throw new IllegalArgumentException(
                "deathSequence="+
                deathSequence
            );

        return playerId.toString()+
            ":"+
            Long.toUnsignedString(
                deathSequence
            );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean =
            requireText(
                value,
                "settlementAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
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

        String clean = value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                name
            );

        return clean;
    }
}

package spk.local;

import java.util.*;

/**
 * Exactly-once settlement of an immutable NPC drop resolution into canonical
 * World ground-item state.
 *
 * Drop calculation, pickup/publicization, expiry and reward policy remain
 * outside this service.
 */
final class NpcDropGroundSettlementService {
    static final String OWNER_SCOPED_DEATH_TILE=
        "OWNER_SCOPED_DEATH_TILE";

    static final class SettledGroundItem {
        final long groundItemId;
        final int itemId;
        final int settledAmount;
        final int stackAmountAfter;

        private SettledGroundItem(
            GroundItem groundItem,
            int settledAmount
        ){
            this.groundItemId=groundItem.id;
            this.itemId=groundItem.itemId;
            this.settledAmount=settledAmount;
            this.stackAmountAfter=groundItem.amount;
        }
    }

    static final class Receipt {
        final EntityId npcId;
        final int definitionId;
        final Tile deathTile;
        final long deathTick;
        final String recipientRef;
        final String settlementAuthority;
        final String settlementPolicy;
        final List<SettledGroundItem> groundItems;

        private Receipt(
            NpcDropResolutionService.Resolution resolution,
            String settlementAuthority,
            String settlementPolicy,
            List<SettledGroundItem> groundItems
        ){
            this.npcId=resolution.context.npcId;
            this.definitionId=
                resolution.context.definitionId;
            this.deathTile=
                resolution.context.deathTile;
            this.deathTick=
                resolution.context.deathTick;
            this.recipientRef=
                resolution.context.recipientRef;
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

    private static final class SettlementIdentity {
        final EntityId npcId;
        final int definitionId;
        final Tile deathTile;
        final long deathTick;
        final String recipientRef;
        final String dropAuthority;
        final List<NpcDropResolutionService.Drop> drops;

        SettlementIdentity(
            NpcDropResolutionService.Resolution resolution
        ){
            this.npcId=resolution.context.npcId;
            this.definitionId=
                resolution.context.definitionId;
            this.deathTile=
                resolution.context.deathTile;
            this.deathTick=
                resolution.context.deathTick;
            this.recipientRef=
                resolution.context.recipientRef;
            this.dropAuthority=
                resolution.dropAuthority;
            this.drops=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        resolution.drops
                    )
                );
        }

        boolean sameAs(
            NpcDropResolutionService.Resolution resolution
        ){
            if(!npcId.equals(
                    resolution.context.npcId
                )||
               definitionId!=
                    resolution.context.definitionId||
               !deathTile.equals(
                    resolution.context.deathTile
                )||
               deathTick!=
                    resolution.context.deathTick||
               !recipientRef.equals(
                    resolution.context.recipientRef
                )||
               !dropAuthority.equals(
                    resolution.dropAuthority
                )||
               drops.size()!=
                    resolution.drops.size())
                return false;

            for(int i=0;i<drops.size();i++){
                NpcDropResolutionService.Drop a=
                    drops.get(i);
                NpcDropResolutionService.Drop b=
                    resolution.drops.get(i);

                if(a.itemId!=b.itemId||
                   a.amount!=b.amount)
                    return false;
            }

            return true;
        }
    }

    private final World world;
    private final GroundItemRegistry groundItems;
    private final String settlementAuthority;
    private final String settlementPolicy;
    private final LinkedHashMap<EntityId,Receipt>
        receipts=
            new LinkedHashMap<>();
    private final LinkedHashMap<EntityId,SettlementIdentity>
        identities=
            new LinkedHashMap<>();

    NpcDropGroundSettlementService(
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
                "unsupported drop settlement policy "+
                this.settlementPolicy
            );
    }

    synchronized Receipt settle(
        NpcDropResolutionService.Resolution resolution
    ){
        NpcDropResolutionService.Resolution checked=
            Objects.requireNonNull(
                resolution,
                "resolution"
            );

        EntityId npcId=
            Objects.requireNonNull(
                checked.context.npcId,
                "npcId"
            );

        Receipt existing=
            receipts.get(npcId);

        if(existing!=null){
            SettlementIdentity identity=
                identities.get(npcId);

            if(identity==null||
               !identity.sameAs(checked))
                throw new IllegalStateException(
                    "conflicting NPC drop settlement id="+
                    npcId
                );

            return existing;
        }

        if(checked.context.deathTick<0L)
            throw new IllegalArgumentException(
                "deathTick="+
                checked.context.deathTick
            );

        ArrayList<GroundItemRegistry.AddRequest>
            requests=
                new ArrayList<>();
        ArrayList<Integer> oldAmounts=
            new ArrayList<>();

        for(NpcDropResolutionService.Drop drop:
                checked.drops){
            NpcDropResolutionService.Drop row=
                Objects.requireNonNull(
                    drop,
                    "drop"
                );

            GroundItem existingStack=
                groundItems.findOwned(
                    row.itemId,
                    checked.context.deathTile.x,
                    checked.context.deathTile.y,
                    checked.context.deathTile.plane,
                    checked.context.recipientRef
                );

            oldAmounts.add(
                existingStack==null
                    ?null
                    :Integer.valueOf(
                        existingStack.amount
                    )
            );

            requests.add(
                new GroundItemRegistry.AddRequest(
                    row.itemId,
                    row.amount,
                    checked.context.deathTile,
                    checked.context.recipientRef,
                    checked.context.deathTick,
                    false
                )
            );
        }

        List<GroundItem> materialized=
            groundItems.addBatch(
                requests
            );

        if(materialized.size()!=
                checked.drops.size()&&
           !checked.drops.isEmpty())
            throw new IllegalStateException(
                "drop bundle canonicalization changed unexpectedly npc="+
                npcId
            );

        ArrayList<SettledGroundItem>
            rows=
                new ArrayList<>();

        for(int i=0;i<materialized.size();i++)
            rows.add(
                new SettledGroundItem(
                    materialized.get(i),
                    checked.drops.get(i).amount
                )
            );

        SettlementIdentity identity=
            new SettlementIdentity(
                checked
            );

        Receipt receipt=
            new Receipt(
                checked,
                settlementAuthority,
                settlementPolicy,
                rows
            );

        identities.put(
            npcId,
            identity
        );
        receipts.put(
            npcId,
            receipt
        );

        publishLiveOwnerScene(
            checked,
            materialized,
            oldAmounts
        );

        return receipt;
    }

    private void publishLiveOwnerScene(
        NpcDropResolutionService.Resolution resolution,
        List<GroundItem> materialized,
        List<Integer> oldAmounts
    ){
        WorldPlayer recipient=
            world.players().byName(
                resolution.context.recipientRef
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

        for(int i=0;i<materialized.size();i++){
            GroundItem item=
                materialized.get(i);
            Integer oldAmount=
                oldAmounts.get(i);

            if(oldAmount==null)
                world.groundItemPresentationEvents()
                    .enqueueSpawn(
                        now,
                        item,
                        recipient,
                        generation
                    );
            else
                world.groundItemPresentationEvents()
                    .enqueueAmount(
                        now,
                        item,
                        oldAmount.intValue(),
                        recipient,
                        generation
                    );
        }
    }

    synchronized Receipt get(
        EntityId npcId
    ){
        return receipts.get(
            Objects.requireNonNull(
                npcId,
                "npcId"
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
                "client/unknown authority cannot define NPC drop settlement actual="+
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
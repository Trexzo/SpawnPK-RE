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

        /*
         * Canonicalize duplicate stack rows before mutating GroundItemRegistry.
         * The current settlement policy gives every row the same tile/owner/
         * devOwned identity, so itemId is the remaining stack-key component.
         * GroundItemRegistry.addBatch() also canonicalizes duplicates, but doing
         * it here first prevents discovering a shape mismatch after world
         * mutation and lets the receipt retain the exact settled amount.
         */
        LinkedHashMap<Integer,Integer>
            canonicalAmounts=
                new LinkedHashMap<>();

        for(NpcDropResolutionService.Drop drop:
                checked.drops){
            NpcDropResolutionService.Drop row=
                Objects.requireNonNull(
                    drop,
                    "drop"
                );

            int prior=
                canonicalAmounts.getOrDefault(
                    row.itemId,
                    0
                );

            final int combined;

            try{
                combined=
                    Math.addExact(
                        prior,
                        row.amount
                    );
            }catch(ArithmeticException overflow){
                throw new IllegalStateException(
                    "canonical drop amount overflow itemId="+
                    row.itemId,
                    overflow
                );
            }

            if(combined<=0)
                throw new IllegalStateException(
                    "canonical drop amount invalid itemId="+
                    row.itemId+
                    " amount="+
                    combined
                );

            canonicalAmounts.put(
                row.itemId,
                combined
            );
        }

        ArrayList<GroundItemRegistry.AddRequest>
            requests=
                new ArrayList<>(
                    canonicalAmounts.size()
                );

        for(Map.Entry<Integer,Integer> row:
                canonicalAmounts.entrySet())
            requests.add(
                new GroundItemRegistry.AddRequest(
                    row.getKey(),
                    row.getValue(),
                    checked.context.deathTile,
                    checked.context.recipientRef,
                    checked.context.deathTick,
                    false
                )
            );

        List<GroundItem> materialized=
            groundItems.addBatch(
                requests
            );

        if(materialized.size()!=
                requests.size())
            throw new IllegalStateException(
                "canonical ground settlement shape mismatch npc="+
                npcId
            );

        ArrayList<SettledGroundItem>
            rows=
                new ArrayList<>();

        int index=0;

        for(Integer settledAmount:
                canonicalAmounts.values())
            rows.add(
                new SettledGroundItem(
                    materialized.get(index++),
                    settledAmount
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

        return receipt;
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
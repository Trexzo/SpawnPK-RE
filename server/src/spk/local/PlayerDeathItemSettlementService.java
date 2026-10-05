package spk.local;

import java.util.*;

/**
 * Applies an already-resolved player-death item disposition to canonical
 * carried state and owner-scoped ground items.
 *
 * Policy is deliberately outside this service. The caller decides kept
 * amounts through PlayerDeathItemResolutionService; this type owns only
 * deterministic settlement mechanics.
 */
final class PlayerDeathItemSettlementService {
    static final String AUTHORITY="CUSTOM_LOCALLAB";

    static final class GroundLine {
        final long groundItemId;
        final int itemId;
        final int addedAmount;
        final int stackAmountAfter;

        GroundLine(
            GroundItemRegistry.BatchMutation mutation
        ){
            GroundItemRegistry.BatchMutation checked=
                Objects.requireNonNull(mutation,"mutation");
            groundItemId=checked.groundItemId;
            itemId=checked.itemId;
            addedAmount=checked.addedAmount;
            stackAmountAfter=checked.newAmount;
        }
    }

    static final class Receipt {
        final EntityId playerId;
        final long deathSequence;
        final long deathTick;
        final Tile deathTile;
        final String lootOwner;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final List<GroundLine> groundItems;
        private final List<GroundItemRegistry.BatchMutation> mutations;
        final String settlementAuthority;

        Receipt(
            PlayerDeathItemResolutionService.Resolution resolution,
            Tile deathTile,
            String lootOwner,
            List<GroundItemRegistry.BatchMutation> mutations
        ){
            this.playerId=resolution.playerId;
            this.deathSequence=resolution.deathSequence;
            this.deathTick=resolution.deathTick;
            this.deathTile=deathTile;
            this.lootOwner=lootOwner;
            this.keptTotalQuantity=
                resolution.keptTotalQuantity();
            this.lostTotalQuantity=
                resolution.lostTotalQuantity();

            ArrayList<GroundLine> lines=
                new ArrayList<>();
            for(GroundItemRegistry.BatchMutation mutation:
                    mutations)
                lines.add(new GroundLine(mutation));
            this.groundItems=
                Collections.unmodifiableList(lines);
            this.mutations=
                Collections.unmodifiableList(
                    new ArrayList<>(mutations)
                );
            this.settlementAuthority=AUTHORITY;
        }
    }

    private static final class SettlementIdentity {
        final PlayerDeathItemResolutionService.Resolution resolution;
        final String lootOwner;

        SettlementIdentity(
            PlayerDeathItemResolutionService.Resolution resolution,
            String lootOwner
        ){
            this.resolution=resolution;
            this.lootOwner=lootOwner;
        }

        boolean same(
            PlayerDeathItemResolutionService.Resolution other,
            String otherOwner
        ){
            return Objects.equals(lootOwner,otherOwner)&&
                sameResolution(resolution,other);
        }
    }

    private static final class CommitResult {
        Receipt receipt;
        boolean created;
    }

    private final World world;
    private final WorldPlayer player;
    private final GroundItemRegistry groundItems;
    private final LinkedHashMap<Long,SettlementIdentity>
        identities=new LinkedHashMap<>();
    private final LinkedHashMap<Long,Receipt>
        receipts=new LinkedHashMap<>();

    PlayerDeathItemSettlementService(
        World world,
        WorldPlayer player
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.groundItems=world.groundItems();
    }

    Receipt settle(
        PlayerDeathItemResolutionService.Resolution resolution,
        String lootOwner
    )throws Exception{
        PlayerDeathItemResolutionService.Resolution checked=
            Objects.requireNonNull(resolution,"resolution");
        String owner=requireOwner(lootOwner);

        synchronized(this){
            Receipt existing=
                receipts.get(checked.deathSequence);
            if(existing!=null){
                SettlementIdentity identity=
                    identities.get(checked.deathSequence);
                if(identity==null||
                   !identity.same(checked,owner))
                    throw new IllegalStateException(
                        "conflicting player death settlement replay sequence="+
                        checked.deathSequence
                    );
                return existing;
            }
        }

        if(!player.id().equals(checked.playerId))
            throw new IllegalArgumentException(
                "death resolution belongs to another player"
            );

        long generation=player.generation();
        CommitResult result=new CommitResult();

        boolean current=
            world.withOpenPlayerMutationOwnershipIfCurrent(
                player,
                generation,
                ()->{
                    synchronized(PlayerDeathItemSettlementService.this){
                        Receipt existing=
                            receipts.get(
                                checked.deathSequence
                            );
                        if(existing!=null){
                            SettlementIdentity identity=
                                identities.get(
                                    checked.deathSequence
                                );
                            if(identity==null||
                               !identity.same(
                                    checked,
                                    owner
                                ))
                                throw new IllegalStateException(
                                    "conflicting player death settlement replay sequence="+
                                    checked.deathSequence
                                );
                            result.receipt=existing;
                            return;
                        }

                        requireCurrentDeath(checked);

                        Tile deathTile=
                            new Tile(
                                player.movement().x(),
                                player.movement().y(),
                                player.movement().plane()
                            );

                        Postimage postimage=
                            buildPostimage(checked);

                        ArrayList<GroundItemRegistry.AddRequest>
                            requests=
                                new ArrayList<>();

                        for(PlayerDeathItemResolutionService.Disposition
                                disposition:
                                checked.dispositions)
                            if(disposition.lostAmount>0)
                                requests.add(
                                    new GroundItemRegistry.AddRequest(
                                        disposition.line.itemId,
                                        disposition.lostAmount,
                                        deathTile,
                                        owner,
                                        checked.deathTick,
                                        false
                                    )
                                );

                        List<GroundItemRegistry.BatchMutation>
                            mutations;

                        /*
                         * Keep the registry monitor from preflight through
                         * carried-state mutation and ground commit. With the
                         * victim mutation lock already held by World, neither
                         * side of the cross-domain preimage can drift.
                         */
                        synchronized(groundItems){
                            groundItems.validateBatchAdd(
                                requests
                            );

                            player.bank()
                                .replaceInventorySemantic(
                                    postimage.inventoryItems,
                                    postimage.inventoryQuantities
                                );
                            player.equipment()
                                .restoreAccountState(
                                    postimage.equipmentItems,
                                    postimage.equipmentQuantities
                                );
                            player.playerState()
                                .syncEquipmentPresentation(
                                    player.equipment()
                                );

                            mutations=
                                groundItems.addBatchDetailed(
                                    requests
                                );
                        }

                        Receipt receipt=
                            new Receipt(
                                checked,
                                deathTile,
                                owner,
                                mutations
                            );

                        identities.put(
                            checked.deathSequence,
                            new SettlementIdentity(
                                checked,
                                owner
                            )
                        );
                        receipts.put(
                            checked.deathSequence,
                            receipt
                        );

                        result.receipt=receipt;
                        result.created=true;
                    }
                }
            );

        if(!current||result.receipt==null)
            throw new IllegalStateException(
                "player death settlement lost world ownership sequence="+
                checked.deathSequence
            );

        if(result.created)
            publishGroundPresentation(
                checked,
                owner,
                result.receipt
            );

        return result.receipt;
    }

    synchronized Receipt get(
        long deathSequence
    ){
        return receipts.get(deathSequence);
    }

    synchronized int size(){
        return receipts.size();
    }

    private void publishGroundPresentation(
        PlayerDeathItemResolutionService.Resolution resolution,
        String owner,
        Receipt receipt
    ){
        WorldPlayer recipient=
            world.players().byName(owner);
        if(recipient==null)
            return;

        long generation=recipient.generation();
        if(!world.players().owns(
                recipient,
                generation
            ))
            return;

        /*
         * Re-read canonical mutations by receipt identity is unnecessary:
         * presentation only needs the immutable values already captured by
         * BatchMutation. The settlement path enqueues immediately after commit.
         */
        long now=System.currentTimeMillis();
        for(GroundItemRegistry.BatchMutation mutation:
                receipt.mutations){
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
    }

    private void requireCurrentDeath(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        PlayerLifecycleState lifecycle=
            player.lifecycle();

        if(!lifecycle.dead()||
           lifecycle.deathTick()!=resolution.deathTick||
           lifecycle.deathSequence()!=
                resolution.deathSequence||
           !safeCause(lifecycle.cause()).equals(
                safeCause(resolution.deathCause)))
            throw new IllegalStateException(
                "player death identity changed before settlement id="+
                player.id()
            );
    }

    private Postimage buildPostimage(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        int[] inventoryItems=
            new int[BankState.INVENTORY_CAPACITY];
        int[] inventoryQuantities=
            new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(inventoryItems,-1);

        int currentCarried=0;
        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.InventorySlotSnapshot snapshot=
                player.bank().inventorySlotSnapshot(slot);
            if(!snapshot.occupied)
                continue;
            inventoryItems[slot]=snapshot.itemId;
            inventoryQuantities[slot]=snapshot.quantity;
            currentCarried++;
        }

        int[] equipmentItems=
            player.equipment().containerItems();
        int[] equipmentQuantities=
            player.equipment().containerQuantities();
        for(int itemId:equipmentItems)
            if(itemId>=0)
                currentCarried++;

        HashSet<String> seen=
            new HashSet<>();

        for(PlayerDeathItemResolutionService.Disposition
                disposition:
                resolution.dispositions){
            PlayerDeathItemResolutionService.CarriedLine line=
                Objects.requireNonNull(
                    disposition.line,
                    "disposition.line"
                );

            if(disposition.keptAmount<0||
               disposition.lostAmount<0||
               disposition.keptAmount+
                    disposition.lostAmount!=
                    line.quantity)
                throw new IllegalArgumentException(
                    "invalid death disposition lineId="+
                    line.lineId
                );

            String sourceKey=
                line.source+":"+
                line.sourceIndex;

            if(!seen.add(sourceKey))
                throw new IllegalArgumentException(
                    "duplicate death source line "+
                    sourceKey
                );

            if(line.source==
                    PlayerDeathItemResolutionService
                        .Source.INVENTORY){
                if(line.sourceIndex<0||
                   line.sourceIndex>=
                        BankState.INVENTORY_CAPACITY)
                    throw new IllegalArgumentException(
                        "invalid inventory source index="+
                        line.sourceIndex
                    );

                BankState.InventorySlotSnapshot current=
                    player.bank()
                        .inventorySlotSnapshot(
                            line.sourceIndex
                        );

                if(!current.occupied||
                   current.itemId!=line.itemId||
                   current.quantity!=line.quantity)
                    throw new IllegalStateException(
                        "inventory death preimage changed lineId="+
                        line.lineId
                    );

                if(disposition.keptAmount==0){
                    inventoryItems[line.sourceIndex]=-1;
                    inventoryQuantities[line.sourceIndex]=0;
                }else{
                    inventoryItems[line.sourceIndex]=
                        line.itemId;
                    inventoryQuantities[line.sourceIndex]=
                        disposition.keptAmount;
                }
            }else if(line.source==
                    PlayerDeathItemResolutionService
                        .Source.EQUIPMENT){
                if(line.sourceIndex<0||
                   line.sourceIndex>=
                        EquipmentState.EQUIPMENT_SLOTS)
                    throw new IllegalArgumentException(
                        "invalid equipment source index="+
                        line.sourceIndex
                    );

                if(equipmentItems[line.sourceIndex]!=
                        line.itemId||
                   equipmentQuantities[line.sourceIndex]!=
                        line.quantity)
                    throw new IllegalStateException(
                        "equipment death preimage changed lineId="+
                        line.lineId
                    );

                if(disposition.keptAmount==0){
                    equipmentItems[line.sourceIndex]=-1;
                    equipmentQuantities[line.sourceIndex]=0;
                }else{
                    equipmentItems[line.sourceIndex]=
                        line.itemId;
                    equipmentQuantities[line.sourceIndex]=
                        disposition.keptAmount;
                }
            }else{
                throw new IllegalArgumentException(
                    "unsupported death source lineId="+
                    line.lineId
                );
            }
        }

        if(seen.size()!=currentCarried||
           seen.size()!=resolution.dispositions.size())
            throw new IllegalStateException(
                "carried item set changed before settlement expected="+
                resolution.dispositions.size()+
                " actual="+currentCarried
            );

        return new Postimage(
            inventoryItems,
            inventoryQuantities,
            equipmentItems,
            equipmentQuantities
        );
    }

    private static boolean sameResolution(
        PlayerDeathItemResolutionService.Resolution left,
        PlayerDeathItemResolutionService.Resolution right
    ){
        if(left==right)
            return true;
        if(left==null||right==null||
           !left.playerId.equals(right.playerId)||
           left.deathTick!=right.deathTick||
           left.deathSequence!=right.deathSequence||
           !safeCause(left.deathCause).equals(
                safeCause(right.deathCause))||
           !Objects.equals(
                left.policyAuthority,
                right.policyAuthority)||
           left.dispositions.size()!=
                right.dispositions.size())
            return false;

        for(int i=0;
            i<left.dispositions.size();
            i++){
            PlayerDeathItemResolutionService.Disposition a=
                left.dispositions.get(i);
            PlayerDeathItemResolutionService.Disposition b=
                right.dispositions.get(i);
            PlayerDeathItemResolutionService.CarriedLine al=a.line;
            PlayerDeathItemResolutionService.CarriedLine bl=b.line;

            if(al.lineId!=bl.lineId||
               al.source!=bl.source||
               al.sourceIndex!=bl.sourceIndex||
               al.equipmentSlot!=bl.equipmentSlot||
               al.itemId!=bl.itemId||
               al.quantity!=bl.quantity||
               a.keptAmount!=b.keptAmount||
               a.lostAmount!=b.lostAmount)
                return false;
        }

        return true;
    }

    private static String requireOwner(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "lootOwner"
            );
        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "lootOwner blank"
            );
        return clean;
    }

    private static String safeCause(
        String value
    ){
        return value==null||
               value.trim().isEmpty()
            ?"UNSPECIFIED"
            :value;
    }

    private static final class Postimage {
        final int[] inventoryItems;
        final int[] inventoryQuantities;
        final int[] equipmentItems;
        final int[] equipmentQuantities;

        Postimage(
            int[] inventoryItems,
            int[] inventoryQuantities,
            int[] equipmentItems,
            int[] equipmentQuantities
        ){
            this.inventoryItems=inventoryItems;
            this.inventoryQuantities=
                inventoryQuantities;
            this.equipmentItems=equipmentItems;
            this.equipmentQuantities=
                equipmentQuantities;
        }
    }
}

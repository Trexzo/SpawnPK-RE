package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Applies one already-resolved carried-item death disposition to the canonical
 * inventory/equipment state.
 *
 * This service owns settlement only. It does not decide item value ordering,
 * skull/protect-item rules, loot recipient, ground visibility, packet
 * publication, or persistence policy.
 */
final class PlayerDeathItemSettlementService {
    static final class LostLine {
        final PlayerDeathItemResolutionService.Source source;
        final int sourceIndex;
        final EquipmentSlot equipmentSlot;
        final int itemId;
        final int quantity;

        private LostLine(
            PlayerDeathItemResolutionService.CarriedLine line,
            int quantity
        ){
            if(quantity<=0)
                throw new IllegalArgumentException(
                    "lost quantity="+quantity
                );

            this.source=line.source;
            this.sourceIndex=line.sourceIndex;
            this.equipmentSlot=line.equipmentSlot;
            this.itemId=line.itemId;
            this.quantity=quantity;
        }
    }

    static final class Settlement {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final List<LostLine> lost;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final String resolutionAuthority;
        final String settlementAuthority;

        private final PlayerDeathItemResolutionService.Resolution resolution;

        private Settlement(
            PlayerDeathItemResolutionService.Resolution resolution,
            List<LostLine> lost,
            String settlementAuthority
        ){
            this.resolution=
                Objects.requireNonNull(
                    resolution,
                    "resolution"
                );
            this.playerId=resolution.playerId;
            this.deathTick=resolution.deathTick;
            this.deathSequence=resolution.deathSequence;
            this.deathCause=resolution.deathCause;
            this.lost=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        lost
                    )
                );
            this.keptTotalQuantity=
                resolution.keptTotalQuantity();
            this.lostTotalQuantity=
                resolution.lostTotalQuantity();
            this.resolutionAuthority=
                resolution.policyAuthority;
            this.settlementAuthority=
                settlementAuthority;
        }
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerLifecycleState lifecycle;
    private final Object mutationLock;
    private final String settlementAuthority;

    private final LinkedHashMap<Long,Settlement>
        settledByDeathSequence=
            new LinkedHashMap<>();

    PlayerDeathItemSettlementService(
        WorldPlayer player,
        String settlementAuthority
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=player.bank();
        this.equipment=player.equipment();
        this.lifecycle=player.lifecycle();
        this.mutationLock=player.mutationLock();
        this.settlementAuthority=
            requireGameplayAuthority(
                settlementAuthority
            );
    }

    Settlement settle(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        PlayerDeathItemResolutionService.Resolution
            expected=
                Objects.requireNonNull(
                    resolution,
                    "resolution"
                );

        synchronized(mutationLock){
            requirePlayerIdentity(
                expected
            );

            Settlement existing=
                settledByDeathSequence.get(
                    expected.deathSequence
                );

            if(existing!=null){
                if(!sameResolution(
                        existing.resolution,
                        expected))
                    throw new IllegalStateException(
                        "death settlement replay identity mismatch id="+
                        player.id()+
                        " deathSequence="+
                        expected.deathSequence
                    );

                return existing;
            }

            requireExactCurrentDeath(
                expected
            );

            int[] inventoryItems=
                new int[
                    BankState.INVENTORY_CAPACITY
                ];
            int[] inventoryQuantities=
                new int[
                    BankState.INVENTORY_CAPACITY
                ];
            boolean[] inventoryCovered=
                new boolean[
                    BankState.INVENTORY_CAPACITY
                ];

            for(int slot=0;
                slot<inventoryItems.length;
                slot++){
                BankState.InventorySlotSnapshot
                    snapshot=
                        bank.inventorySlotSnapshot(
                            slot
                        );

                inventoryItems[slot]=
                    snapshot.occupied
                        ?snapshot.itemId
                        :-1;
                inventoryQuantities[slot]=
                    snapshot.occupied
                        ?snapshot.quantity
                        :0;
            }

            int[] equipmentItems=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] equipmentQuantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            boolean[] equipmentCovered=
                new boolean[
                    EquipmentState.EQUIPMENT_SLOTS
                ];

            for(int index=0;
                index<equipmentItems.length;
                index++){
                int itemId=
                    equipment.itemAt(
                        index
                    );
                int quantity=
                    equipment.quantityAt(
                        index
                    );

                equipmentItems[index]=itemId;
                equipmentQuantities[index]=
                    itemId<0
                        ?0
                        :quantity;
            }

            ArrayList<LostLine> lost=
                new ArrayList<>();

            for(
                PlayerDeathItemResolutionService.Disposition
                    disposition:
                        expected.dispositions
            ){
                PlayerDeathItemResolutionService.CarriedLine
                    line=
                        Objects.requireNonNull(
                            disposition.line,
                            "disposition.line"
                        );

                if(line.quantity<=0||
                   disposition.keptAmount<0||
                   disposition.lostAmount<0||
                   disposition.keptAmount+
                        disposition.lostAmount!=
                            line.quantity)
                    throw new IllegalArgumentException(
                        "invalid death disposition lineId="+
                        line.lineId
                    );

                switch(line.source){
                    case INVENTORY:
                        requireInventoryPreimage(
                            line,
                            inventoryItems,
                            inventoryQuantities,
                            inventoryCovered
                        );

                        if(disposition.keptAmount==0){
                            inventoryItems[
                                line.sourceIndex
                            ]=-1;
                            inventoryQuantities[
                                line.sourceIndex
                            ]=0;
                        }else{
                            inventoryQuantities[
                                line.sourceIndex
                            ]=
                                disposition.keptAmount;
                        }
                        break;

                    case EQUIPMENT:
                        requireEquipmentPreimage(
                            line,
                            equipmentItems,
                            equipmentQuantities,
                            equipmentCovered
                        );

                        if(disposition.keptAmount==0){
                            equipmentItems[
                                line.sourceIndex
                            ]=-1;
                            equipmentQuantities[
                                line.sourceIndex
                            ]=0;
                        }else{
                            equipmentQuantities[
                                line.sourceIndex
                            ]=
                                disposition.keptAmount;
                        }
                        break;

                    default:
                        throw new IllegalStateException(
                            "unsupported death item source "+
                            line.source
                        );
                }

                if(disposition.lostAmount>0)
                    lost.add(
                        new LostLine(
                            line,
                            disposition.lostAmount
                        )
                    );
            }

            requireCompleteCoverage(
                inventoryItems,
                inventoryCovered,
                equipmentItems,
                equipmentCovered
            );

            /*
             * Both complete postimages were validated above under the one player
             * mutation lock. BankState performs its own full postimage validation
             * before touching a slot; EquipmentState receives fixed-size arrays
             * whose entries were already validated from canonical state.
             */
            bank.replaceInventorySemantic(
                inventoryItems,
                inventoryQuantities
            );
            equipment.restoreAccountState(
                equipmentItems,
                equipmentQuantities
            );

            Settlement settlement=
                new Settlement(
                    expected,
                    lost,
                    settlementAuthority
                );

            settledByDeathSequence.put(
                expected.deathSequence,
                settlement
            );

            return settlement;
        }
    }

    Settlement get(
        long deathSequence
    ){
        synchronized(mutationLock){
            return settledByDeathSequence.get(
                deathSequence
            );
        }
    }

    int size(){
        synchronized(mutationLock){
            return settledByDeathSequence.size();
        }
    }

    String settlementAuthority(){
        return settlementAuthority;
    }

    private void requirePlayerIdentity(
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
    }

    private void requireExactCurrentDeath(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        if(!lifecycle.dead()||
           lifecycle.deathTick()!=
                resolution.deathTick||
           lifecycle.deathSequence()!=
                resolution.deathSequence||
           !safeCause(
                lifecycle.cause()
            ).equals(
                resolution.deathCause))
            throw new IllegalStateException(
                "player death identity changed before item settlement id="+
                player.id()
            );
    }

    private static void requireInventoryPreimage(
        PlayerDeathItemResolutionService.CarriedLine line,
        int[] itemIds,
        int[] quantities,
        boolean[] covered
    ){
        int slot=line.sourceIndex;

        if(slot<0||
           slot>=itemIds.length)
            throw new IllegalStateException(
                "death inventory source slot invalid lineId="+
                line.lineId+
                " slot="+slot
            );

        if(covered[slot])
            throw new IllegalStateException(
                "duplicate death inventory source slot="+
                slot
            );

        if(line.equipmentSlot!=null||
           itemIds[slot]!=line.itemId||
           quantities[slot]!=line.quantity)
            throw new IllegalStateException(
                "death inventory preimage changed lineId="+
                line.lineId+
                " slot="+slot+
                " expected="+
                line.itemId+"x"+line.quantity+
                " actual="+
                itemIds[slot]+"x"+quantities[slot]
            );

        covered[slot]=true;
    }

    private static void requireEquipmentPreimage(
        PlayerDeathItemResolutionService.CarriedLine line,
        int[] itemIds,
        int[] quantities,
        boolean[] covered
    ){
        int index=line.sourceIndex;

        if(index<0||
           index>=itemIds.length)
            throw new IllegalStateException(
                "death equipment source index invalid lineId="+
                line.lineId+
                " index="+index
            );

        if(covered[index])
            throw new IllegalStateException(
                "duplicate death equipment source index="+
                index
            );

        EquipmentSlot expectedSlot=
            EquipmentSlot.fromEquipmentIndex(
                index
            );

        if(line.equipmentSlot!=expectedSlot||
           itemIds[index]!=line.itemId||
           quantities[index]!=line.quantity)
            throw new IllegalStateException(
                "death equipment preimage changed lineId="+
                line.lineId+
                " slot="+
                expectedSlot+
                " expected="+
                line.itemId+"x"+line.quantity+
                " actual="+
                itemIds[index]+"x"+quantities[index]
            );

        covered[index]=true;
    }

    private static void requireCompleteCoverage(
        int[] inventoryItems,
        boolean[] inventoryCovered,
        int[] equipmentItems,
        boolean[] equipmentCovered
    ){
        for(int slot=0;
            slot<inventoryItems.length;
            slot++)
            if(inventoryItems[slot]>=0&&
               !inventoryCovered[slot])
                throw new IllegalStateException(
                    "death resolution omitted occupied inventory slot="+
                    slot+
                    " item="+
                    inventoryItems[slot]
                );

        for(int index=0;
            index<equipmentItems.length;
            index++)
            if(equipmentItems[index]>=0&&
               !equipmentCovered[index])
                throw new IllegalStateException(
                    "death resolution omitted occupied equipment index="+
                    index+
                    " item="+
                    equipmentItems[index]
                );
    }

    private static boolean sameResolution(
        PlayerDeathItemResolutionService.Resolution left,
        PlayerDeathItemResolutionService.Resolution right
    ){
        if(left==right)
            return true;

        if(!left.playerId.equals(
                right.playerId)||
           left.deathTick!=right.deathTick||
           left.deathSequence!=right.deathSequence||
           !left.deathCause.equals(
                right.deathCause)||
           !left.policyAuthority.equals(
                right.policyAuthority)||
           left.dispositions.size()!=
                right.dispositions.size())
            return false;

        for(int i=0;
            i<left.dispositions.size();
            i++){
            PlayerDeathItemResolutionService.Disposition
                a=left.dispositions.get(i);
            PlayerDeathItemResolutionService.Disposition
                b=right.dispositions.get(i);

            if(a.line.lineId!=b.line.lineId||
               a.line.source!=b.line.source||
               a.line.sourceIndex!=
                    b.line.sourceIndex||
               a.line.equipmentSlot!=
                    b.line.equipmentSlot||
               a.line.itemId!=b.line.itemId||
               a.line.quantity!=b.line.quantity||
               a.keptAmount!=b.keptAmount||
               a.lostAmount!=b.lostAmount)
                return false;
        }

        return true;
    }

    private static String safeCause(
        String value
    ){
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

        if("EXACT_CURRENT_CLIENT".equals(
                clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define death settlement policy actual="+
                clean
            );

        return clean;
    }
}

package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Applies one already-resolved player-death carried-item disposition to the
 * canonical inventory/equipment state.
 *
 * This service does not decide which items are kept, who receives lost items,
 * where ground items appear, or any wilderness/skull/protection policy.
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

    private static final class LineIdentity {
        final int lineId;
        final PlayerDeathItemResolutionService.Source source;
        final int sourceIndex;
        final EquipmentSlot equipmentSlot;
        final int itemId;
        final int carriedQuantity;
        final int keptAmount;
        final int lostAmount;

        LineIdentity(
            PlayerDeathItemResolutionService.Disposition disposition
        ){
            PlayerDeathItemResolutionService.Disposition checked=
                Objects.requireNonNull(
                    disposition,
                    "disposition"
                );
            PlayerDeathItemResolutionService.CarriedLine line=
                Objects.requireNonNull(
                    checked.line,
                    "carried line"
                );

            this.lineId=line.lineId;
            this.source=line.source;
            this.sourceIndex=line.sourceIndex;
            this.equipmentSlot=line.equipmentSlot;
            this.itemId=line.itemId;
            this.carriedQuantity=line.quantity;
            this.keptAmount=checked.keptAmount;
            this.lostAmount=checked.lostAmount;
        }

        boolean matches(
            PlayerDeathItemResolutionService.Disposition disposition
        ){
            if(disposition==null||
               disposition.line==null)
                return false;

            PlayerDeathItemResolutionService.CarriedLine line=
                disposition.line;

            return lineId==line.lineId&&
                source==line.source&&
                sourceIndex==line.sourceIndex&&
                equipmentSlot==line.equipmentSlot&&
                itemId==line.itemId&&
                carriedQuantity==line.quantity&&
                keptAmount==disposition.keptAmount&&
                lostAmount==disposition.lostAmount;
        }
    }

    static final class Receipt {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final List<LostLine> lostLines;
        final String policyAuthority;

        private final List<LineIdentity>
            sourceLines;

        private Receipt(
            PlayerDeathItemResolutionService.Resolution resolution,
            List<LostLine> lostLines,
            String policyAuthority
        ){
            this.playerId=resolution.playerId;
            this.deathTick=resolution.deathTick;
            this.deathSequence=resolution.deathSequence;
            this.deathCause=resolution.deathCause;
            this.keptTotalQuantity=
                resolution.keptTotalQuantity();
            this.lostTotalQuantity=
                resolution.lostTotalQuantity();
            this.lostLines=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        lostLines
                    )
                );
            this.policyAuthority=policyAuthority;

            ArrayList<LineIdentity> identities=
                new ArrayList<>();

            for(PlayerDeathItemResolutionService.Disposition
                    disposition:
                    resolution.dispositions)
                identities.add(
                    new LineIdentity(
                        disposition
                    )
                );

            this.sourceLines=
                Collections.unmodifiableList(
                    identities
                );
        }

        private boolean matches(
            PlayerDeathItemResolutionService.Resolution resolution
        ){
            if(resolution==null||
               !playerId.equals(
                    resolution.playerId)||
               deathTick!=resolution.deathTick||
               deathSequence!=
                    resolution.deathSequence||
               !deathCause.equals(
                    resolution.deathCause)||
               keptTotalQuantity!=
                    resolution.keptTotalQuantity()||
               lostTotalQuantity!=
                    resolution.lostTotalQuantity()||
               sourceLines.size()!=
                    resolution.dispositions.size())
                return false;

            for(int i=0;
                i<sourceLines.size();
                i++)
                if(!sourceLines.get(i)
                        .matches(
                            resolution.dispositions
                                .get(i)
                        ))
                    return false;

            return true;
        }
    }

    private static final class PreparedSettlement {
        final int[] expectedInventoryItems;
        final int[] expectedInventoryQuantities;
        final int[] expectedEquipmentItems;
        final int[] expectedEquipmentQuantities;
        final int[] nextInventoryItems;
        final int[] nextInventoryQuantities;
        final int[] nextEquipmentItems;
        final int[] nextEquipmentQuantities;
        final List<LostLine> lostLines;

        PreparedSettlement(
            int[] expectedInventoryItems,
            int[] expectedInventoryQuantities,
            int[] expectedEquipmentItems,
            int[] expectedEquipmentQuantities,
            int[] nextInventoryItems,
            int[] nextInventoryQuantities,
            int[] nextEquipmentItems,
            int[] nextEquipmentQuantities,
            List<LostLine> lostLines
        ){
            this.expectedInventoryItems=
                expectedInventoryItems;
            this.expectedInventoryQuantities=
                expectedInventoryQuantities;
            this.expectedEquipmentItems=
                expectedEquipmentItems;
            this.expectedEquipmentQuantities=
                expectedEquipmentQuantities;
            this.nextInventoryItems=
                nextInventoryItems;
            this.nextInventoryQuantities=
                nextInventoryQuantities;
            this.nextEquipmentItems=
                nextEquipmentItems;
            this.nextEquipmentQuantities=
                nextEquipmentQuantities;
            this.lostLines=lostLines;
        }
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerLifecycleState lifecycle;
    private final Object mutationLock;
    private final String policyAuthority;

    private final LinkedHashMap<Long,Receipt>
        receiptsByDeathSequence=
            new LinkedHashMap<>();

    PlayerDeathItemSettlementService(
        WorldPlayer player,
        String policyAuthority
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=player.bank();
        this.equipment=player.equipment();
        this.lifecycle=player.lifecycle();
        this.mutationLock=
            player.mutationLock();
        this.policyAuthority=
            requireGameplayAuthority(
                policyAuthority
            );
    }

    Receipt settle(
        PlayerDeathItemResolutionService.Resolution
            resolution
    ){
        PlayerDeathItemResolutionService.Resolution
            checked=
                Objects.requireNonNull(
                    resolution,
                    "resolution"
                );

        if(!player.id().equals(
                checked.playerId))
            throw new IllegalArgumentException(
                "death resolution belongs to another player expected="+
                player.id()+
                " actual="+
                checked.playerId
            );

        PreparedSettlement prepared=
            prepare(
                checked
            );

        synchronized(mutationLock){
            requireExactCurrentDeath(
                checked
            );

            Receipt existing=
                receiptsByDeathSequence.get(
                    checked.deathSequence
                );

            if(existing!=null){
                if(!existing.matches(
                        checked))
                    throw new IllegalStateException(
                        "conflicting replay for settled death sequence="+
                        checked.deathSequence
                    );

                return existing;
            }

            requireCurrentPreimage(
                prepared
            );

            try{
                bank.replaceInventorySemantic(
                    prepared.nextInventoryItems,
                    prepared.nextInventoryQuantities
                );
                equipment.restoreAccountState(
                    prepared.nextEquipmentItems,
                    prepared.nextEquipmentQuantities
                );

                requireCurrentPostimage(
                    prepared
                );
            }catch(RuntimeException failure){
                rollback(
                    prepared,
                    failure
                );
                throw failure;
            }catch(Error failure){
                rollback(
                    prepared,
                    failure
                );
                throw failure;
            }

            Receipt receipt=
                new Receipt(
                    checked,
                    prepared.lostLines,
                    policyAuthority
                );

            receiptsByDeathSequence.put(
                checked.deathSequence,
                receipt
            );

            return receipt;
        }
    }

    Receipt get(long deathSequence){
        synchronized(mutationLock){
            return receiptsByDeathSequence.get(
                deathSequence
            );
        }
    }

    int size(){
        synchronized(mutationLock){
            return receiptsByDeathSequence.size();
        }
    }

    String policyAuthority(){
        return policyAuthority;
    }

    private PreparedSettlement prepare(
        PlayerDeathItemResolutionService.Resolution
            resolution
    ){
        int[] expectedInventoryItems=
            emptyItems(
                BankState.INVENTORY_CAPACITY
            );
        int[] expectedInventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] expectedEquipmentItems=
            emptyItems(
                EquipmentState.EQUIPMENT_SLOTS
            );
        int[] expectedEquipmentQuantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        int[] nextInventoryItems=
            emptyItems(
                BankState.INVENTORY_CAPACITY
            );
        int[] nextInventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] nextEquipmentItems=
            emptyItems(
                EquipmentState.EQUIPMENT_SLOTS
            );
        int[] nextEquipmentQuantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        boolean[] inventorySeen=
            new boolean[
                BankState.INVENTORY_CAPACITY
            ];
        boolean[] equipmentSeen=
            new boolean[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        ArrayList<LostLine> lost=
            new ArrayList<>();

        for(PlayerDeathItemResolutionService.Disposition
                disposition:
                resolution.dispositions){
            PlayerDeathItemResolutionService.Disposition
                checked=
                    Objects.requireNonNull(
                        disposition,
                        "disposition"
                    );
            PlayerDeathItemResolutionService.CarriedLine
                line=
                    Objects.requireNonNull(
                        checked.line,
                        "carried line"
                    );

            if(line.itemId<0||
               line.quantity<=0||
               checked.keptAmount<0||
               checked.lostAmount<0||
               checked.keptAmount+
                    checked.lostAmount!=
                    line.quantity)
                throw new IllegalArgumentException(
                    "invalid death disposition lineId="+
                    line.lineId
                );

            if(checked.lostAmount>0)
                lost.add(
                    new LostLine(
                        line,
                        checked.lostAmount
                    )
                );

            if(line.source==
                    PlayerDeathItemResolutionService
                        .Source.INVENTORY){
                int slot=line.sourceIndex;

                if(slot<0||
                   slot>=
                        BankState
                            .INVENTORY_CAPACITY||
                   line.equipmentSlot!=null)
                    throw new IllegalArgumentException(
                        "invalid inventory death line lineId="+
                        line.lineId
                    );

                if(inventorySeen[slot])
                    throw new IllegalArgumentException(
                        "duplicate inventory death source slot="+
                        slot
                    );

                inventorySeen[slot]=true;
                expectedInventoryItems[slot]=
                    line.itemId;
                expectedInventoryQuantities[slot]=
                    line.quantity;

                if(checked.keptAmount>0){
                    nextInventoryItems[slot]=
                        line.itemId;
                    nextInventoryQuantities[slot]=
                        checked.keptAmount;
                }

                continue;
            }

            if(line.source==
                    PlayerDeathItemResolutionService
                        .Source.EQUIPMENT){
                EquipmentSlot equipmentSlot=
                    line.equipmentSlot;
                int slot=line.sourceIndex;

                if(equipmentSlot==null||
                   slot!=
                        equipmentSlot
                            .equipmentIndex||
                   slot<0||
                   slot>=
                        EquipmentState
                            .EQUIPMENT_SLOTS)
                    throw new IllegalArgumentException(
                        "invalid equipment death line lineId="+
                        line.lineId
                    );

                if(equipmentSeen[slot])
                    throw new IllegalArgumentException(
                        "duplicate equipment death source slot="+
                        slot
                    );

                equipmentSeen[slot]=true;
                expectedEquipmentItems[slot]=
                    line.itemId;
                expectedEquipmentQuantities[slot]=
                    line.quantity;

                if(checked.keptAmount>0){
                    nextEquipmentItems[slot]=
                        line.itemId;
                    nextEquipmentQuantities[slot]=
                        checked.keptAmount;
                }

                continue;
            }

            throw new IllegalArgumentException(
                "unsupported death source lineId="+
                line.lineId
            );
        }

        return new PreparedSettlement(
            expectedInventoryItems,
            expectedInventoryQuantities,
            expectedEquipmentItems,
            expectedEquipmentQuantities,
            nextInventoryItems,
            nextInventoryQuantities,
            nextEquipmentItems,
            nextEquipmentQuantities,
            Collections.unmodifiableList(
                lost
            )
        );
    }

    private void requireExactCurrentDeath(
        PlayerDeathItemResolutionService.Resolution
            resolution
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
                "player death identity changed before settlement id="+
                player.id()
            );
    }

    private void requireCurrentPreimage(
        PreparedSettlement prepared
    ){
        if(!Arrays.equals(
                currentInventoryItems(),
                prepared.expectedInventoryItems)||
           !Arrays.equals(
                currentInventoryQuantities(),
                prepared.expectedInventoryQuantities)||
           !Arrays.equals(
                equipment.containerItems(),
                prepared.expectedEquipmentItems)||
           !Arrays.equals(
                equipment.containerQuantities(),
                prepared.expectedEquipmentQuantities))
            throw new IllegalStateException(
                "carried state changed before death settlement id="+
                player.id()
            );
    }

    private void requireCurrentPostimage(
        PreparedSettlement prepared
    ){
        if(!Arrays.equals(
                currentInventoryItems(),
                prepared.nextInventoryItems)||
           !Arrays.equals(
                currentInventoryQuantities(),
                prepared.nextInventoryQuantities)||
           !Arrays.equals(
                equipment.containerItems(),
                prepared.nextEquipmentItems)||
           !Arrays.equals(
                equipment.containerQuantities(),
                prepared.nextEquipmentQuantities))
            throw new IllegalStateException(
                "death settlement postimage mismatch id="+
                player.id()
            );
    }

    private int[] currentInventoryItems(){
        int[] out=
            emptyItems(
                BankState.INVENTORY_CAPACITY
            );

        for(int slot=0;
            slot<
                BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);

            if(stack!=null)
                out[slot]=stack.itemId;
        }

        return out;
    }

    private int[] currentInventoryQuantities(){
        int[] out=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        for(int slot=0;
            slot<
                BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);

            if(stack!=null)
                out[slot]=stack.qty;
        }

        return out;
    }

    private void rollback(
        PreparedSettlement prepared,
        Throwable primary
    ){
        try{
            bank.replaceInventorySemantic(
                prepared.expectedInventoryItems,
                prepared.expectedInventoryQuantities
            );
            equipment.restoreAccountState(
                prepared.expectedEquipmentItems,
                prepared.expectedEquipmentQuantities
            );
        }catch(Throwable rollbackFailure){
            primary.addSuppressed(
                rollbackFailure
            );
        }
    }

    private static int[] emptyItems(
        int length
    ){
        int[] out=new int[length];
        Arrays.fill(out,-1);
        return out;
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
                "policyAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policyAuthority blank"
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

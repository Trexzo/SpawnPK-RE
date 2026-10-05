package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Atomic semantic commit for one already-resolved player death disposition.
 *
 * Policy selection remains in PlayerDeathItemResolutionService. This service
 * only revalidates the exact death/carried-state identity, applies kept/lost
 * quantities to canonical inventory/equipment and returns immutable semantic
 * lost-item lines. Ground placement and loot-recipient policy stay outside.
 */
final class PlayerDeathItemCommitService {
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
            this.source=line.source;
            this.sourceIndex=line.sourceIndex;
            this.equipmentSlot=line.equipmentSlot;
            this.itemId=line.itemId;
            this.quantity=quantity;
        }
    }

    static final class CommitResult {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final List<LostLine> lost;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final String policyAuthority;
        final String commitAuthority;

        private CommitResult(
            PlayerDeathItemResolutionService.Resolution resolution,
            List<LostLine> lost,
            String commitAuthority
        ){
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
            this.policyAuthority=
                resolution.policyAuthority;
            this.commitAuthority=
                commitAuthority;
        }
    }

    private final WorldPlayer player;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerLifecycleState lifecycle;
    private final String commitAuthority;
    private final LinkedHashMap<Long,CommitResult>
        committedByDeathSequence=
            new LinkedHashMap<>();

    PlayerDeathItemCommitService(
        WorldPlayer player,
        String commitAuthority
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.bank=player.bank();
        this.equipment=player.equipment();
        this.lifecycle=player.lifecycle();
        this.commitAuthority=
            requireGameplayAuthority(
                commitAuthority
            );
    }

    CommitResult commit(
        PlayerDeathItemResolutionService.Resolution resolution
    ){
        PlayerDeathItemResolutionService.Resolution checked=
            Objects.requireNonNull(
                resolution,
                "resolution"
            );

        synchronized(player.mutationLock()){
            requireCurrentDeath(
                checked
            );

            CommitResult existing=
                committedByDeathSequence.get(
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
            int[] equipmentItems=
                equipment.containerItems();
            int[] equipmentQuantities=
                equipment.containerQuantities();

            int currentCarried=0;

            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.Stack stack=
                    bank.inventoryAt(
                        slot
                    );

                if(stack==null){
                    inventoryItems[slot]=-1;
                    inventoryQuantities[slot]=0;
                    continue;
                }

                if(stack.itemId<0||
                   stack.qty<=0)
                    throw new IllegalStateException(
                        "invalid canonical inventory state slot="+
                        slot+
                        " item="+
                        stack.itemId+
                        " quantity="+
                        stack.qty
                    );

                inventoryItems[slot]=
                    stack.itemId;
                inventoryQuantities[slot]=
                    stack.qty;
                currentCarried++;
            }

            for(int index=0;
                index<EquipmentState.EQUIPMENT_SLOTS;
                index++)
                if(equipmentItems[index]>=0){
                    if(equipmentQuantities[index]<=0)
                        throw new IllegalStateException(
                            "invalid canonical equipment state index="+
                            index+
                            " item="+
                            equipmentItems[index]+
                            " quantity="+
                            equipmentQuantities[index]
                        );
                    currentCarried++;
                }

            if(currentCarried!=
                    checked.dispositions.size())
                throw new IllegalStateException(
                    "carried item state changed after death resolution id="+
                    player.id()+
                    " expectedLines="+
                    checked.dispositions.size()+
                    " currentLines="+
                    currentCarried
                );

            int[] originalInventoryItems=
                inventoryItems.clone();
            int[] originalInventoryQuantities=
                inventoryQuantities.clone();
            int[] originalEquipmentItems=
                equipmentItems.clone();
            int[] originalEquipmentQuantities=
                equipmentQuantities.clone();

            boolean[] seenInventory=
                new boolean[
                    BankState.INVENTORY_CAPACITY
                ];
            boolean[] seenEquipment=
                new boolean[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            ArrayList<LostLine> lost=
                new ArrayList<>();

            for(PlayerDeathItemResolutionService.Disposition
                    disposition:
                    checked.dispositions){
                PlayerDeathItemResolutionService.CarriedLine
                    line=
                        Objects.requireNonNull(
                            disposition.line,
                            "disposition.line"
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
                        PlayerDeathItemResolutionService
                            .Source.INVENTORY){
                    int slot=line.sourceIndex;

                    if(slot<0||
                       slot>=BankState.INVENTORY_CAPACITY||
                       seenInventory[slot])
                        throw new IllegalStateException(
                            "invalid/duplicate inventory death source slot="+
                            slot
                        );

                    seenInventory[slot]=true;

                    if(originalInventoryItems[slot]!=
                            line.itemId||
                       originalInventoryQuantities[slot]!=
                            line.quantity)
                        throw new IllegalStateException(
                            "inventory death source drift slot="+
                            slot+
                            " expected="+
                            line.itemId+"x"+
                            line.quantity+
                            " actual="+
                            originalInventoryItems[slot]+"x"+
                            originalInventoryQuantities[slot]
                        );

                    if(disposition.keptAmount==0){
                        inventoryItems[slot]=-1;
                        inventoryQuantities[slot]=0;
                    }else{
                        inventoryQuantities[slot]=
                            disposition.keptAmount;
                    }
                }else if(line.source==
                            PlayerDeathItemResolutionService
                                .Source.EQUIPMENT){
                    int index=line.sourceIndex;
                    EquipmentSlot expectedSlot=
                        EquipmentSlot
                            .fromEquipmentIndex(
                                index
                            );

                    if(index<0||
                       index>=EquipmentState.EQUIPMENT_SLOTS||
                       expectedSlot==null||
                       expectedSlot!=line.equipmentSlot||
                       seenEquipment[index])
                        throw new IllegalStateException(
                            "invalid/duplicate equipment death source index="+
                            index
                        );

                    seenEquipment[index]=true;

                    if(originalEquipmentItems[index]!=
                            line.itemId||
                       originalEquipmentQuantities[index]!=
                            line.quantity)
                        throw new IllegalStateException(
                            "equipment death source drift index="+
                            index+
                            " expected="+
                            line.itemId+"x"+
                            line.quantity+
                            " actual="+
                            originalEquipmentItems[index]+"x"+
                            originalEquipmentQuantities[index]
                        );

                    if(disposition.keptAmount==0){
                        equipmentItems[index]=-1;
                        equipmentQuantities[index]=0;
                    }else{
                        equipmentQuantities[index]=
                            disposition.keptAmount;
                    }
                }else{
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

            try{
                bank.replaceInventorySemantic(
                    inventoryItems,
                    inventoryQuantities
                );
                equipment.restoreAccountState(
                    equipmentItems,
                    equipmentQuantities
                );
            }catch(Throwable failure){
                Throwable rollbackFailure=null;

                try{
                    bank.replaceInventorySemantic(
                        originalInventoryItems,
                        originalInventoryQuantities
                    );
                    equipment.restoreAccountState(
                        originalEquipmentItems,
                        originalEquipmentQuantities
                    );
                }catch(Throwable rollback){
                    rollbackFailure=rollback;
                }

                if(rollbackFailure!=null){
                    failure.addSuppressed(
                        rollbackFailure
                    );
                    throw new IllegalStateException(
                        "death item commit failed and rollback was incomplete id="+
                        player.id()+
                        " deathSequence="+
                        checked.deathSequence,
                        failure
                    );
                }

                rethrowUnchecked(
                    failure
                );
            }

            CommitResult result=
                new CommitResult(
                    checked,
                    lost,
                    commitAuthority
                );

            committedByDeathSequence.put(
                checked.deathSequence,
                result
            );

            return result;
        }
    }

    CommitResult get(
        long deathSequence
    ){
        synchronized(player.mutationLock()){
            return committedByDeathSequence.get(
                deathSequence
            );
        }
    }

    int size(){
        synchronized(player.mutationLock()){
            return committedByDeathSequence.size();
        }
    }

    String commitAuthority(){
        return commitAuthority;
    }

    private void requireCurrentDeath(
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
                "player death identity changed after resolution id="+
                player.id()
            );
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
                "commitAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "commitAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(
                clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define death item commit actual="+
                clean
            );

        return clean;
    }

    private static void rethrowUnchecked(
        Throwable failure
    ){
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new IllegalStateException(
            failure
        );
    }
}

package spk.local;

import java.util.*;

/**
 * Conservative live death settlement over the recovered death-policy table.
 *
 * LOCAL_LAB policy:
 * - exact AUTO_KEEP rows are preserved;
 * - exact AUTO_LOSS + destroy=true rows are destroyed;
 * - AUTO_LOSS rows whose destination is unresolved are preserved/deferred;
 * - standard keep/loss ordering remains unresolved and is preserved.
 *
 * This intentionally does not invent ground-drop destinations or value ordering.
 */
final class PlayerDeathSettlementService {
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_DEATH_CONSERVATIVE_V1";

    static final class Receipt {
        final long deathSequence;
        final int destroyedLines;
        final int destroyedQuantity;
        final int explicitKeeps;
        final int deferredAutoLoss;
        final int standardUnresolved;
        final String policyAuthority;

        Receipt(
            long deathSequence,
            int destroyedLines,
            int destroyedQuantity,
            int explicitKeeps,
            int deferredAutoLoss,
            int standardUnresolved
        ){
            this.deathSequence=deathSequence;
            this.destroyedLines=destroyedLines;
            this.destroyedQuantity=destroyedQuantity;
            this.explicitKeeps=explicitKeeps;
            this.deferredAutoLoss=deferredAutoLoss;
            this.standardUnresolved=standardUnresolved;
            this.policyAuthority=POLICY_AUTHORITY;
        }
    }

    private final WorldPlayer player;
    private final PlayerDeathItemResolutionService semantic;
    private final InventoryMutationService inventory;
    private final EquipmentMutationService equipment;
    private final LinkedHashMap<Long,Receipt> receipts=
        new LinkedHashMap<>();

    PlayerDeathSettlementService(WorldPlayer player){
        this.player=Objects.requireNonNull(player,"player");
        this.semantic=
            new PlayerDeathItemResolutionService(
                player,
                POLICY_AUTHORITY
            );
        this.inventory=
            new InventoryMutationService(
                player,
                POLICY_AUTHORITY
            );
        this.equipment=
            new EquipmentMutationService(
                player,
                POLICY_AUTHORITY
            );
    }

    Receipt settleCurrentDeath(){
        synchronized(player.mutationLock()){
            long currentSequence=
                player.lifecycle().deathSequence();

            Receipt prior=receipts.get(currentSequence);
            if(prior!=null)return prior;

            PlayerDeathItemResolutionService.DeathPreview preview=
                semantic.previewCurrentDeath();

            ArrayList<PlayerDeathItemResolutionService.Decision>
                decisions=new ArrayList<>();
            ArrayList<PlayerDeathItemResolutionService.CarriedLine>
                destroy=new ArrayList<>();

            int explicitKeeps=0;
            int deferredAutoLoss=0;
            int standardUnresolved=0;
            long destroyedQuantity=0L;

            for(PlayerDeathItemResolutionService.CarriedLine line:
                    preview.carried){
                DeathPolicyRepository.Policy policy=
                    DeathPolicyRepository.get(line.itemId);

                int kept=line.quantity;

                if(policy.kind==
                        DeathPolicyRepository.Kind.AUTO_KEEP_EXPLICIT){
                    explicitKeeps++;
                }else if(policy.kind==
                        DeathPolicyRepository.Kind.AUTO_LOSS_EXPLICIT){
                    if(policy.destroyExplicit){
                        kept=0;
                        destroy.add(line);
                        destroyedQuantity=
                            Math.addExact(
                                destroyedQuantity,
                                (long)line.quantity
                            );
                    }else{
                        /*
                         * Exact client/cache evidence proves this item is not
                         * auto-kept, but does not prove destroy vs ground/drop
                         * destination. Preserve until that server policy is
                         * separately recovered or intentionally designed.
                         */
                        deferredAutoLoss++;
                    }
                }else{
                    standardUnresolved++;
                }

                decisions.add(
                    new PlayerDeathItemResolutionService.Decision(
                        line.lineId,
                        kept
                    )
                );
            }

            if(destroyedQuantity>Integer.MAX_VALUE)
                throw new IllegalStateException(
                    "death destroyed quantity overflow"
                );

            /*
             * Validate every concrete destroy preimage before either semantic
             * resolution or mutation. All subsequent carried-state operations
             * remain under the same player mutation lock.
             */
            for(PlayerDeathItemResolutionService.CarriedLine line:
                    destroy)
                requireLineCurrent(line);

            PlayerDeathItemResolutionService.Resolution resolution=
                semantic.resolveCurrentDeath(
                    preview,
                    decisions
                );

            BankState.Stack[] inventoryBefore=
                player.bank().inventoryContainerSnapshot();
            int[] equipmentItemsBefore=
                player.equipment().containerItems();
            int[] equipmentQuantitiesBefore=
                player.equipment().containerQuantities();

            try{
                for(PlayerDeathItemResolutionService.CarriedLine line:
                        destroy){
                    if(line.source==
                            PlayerDeathItemResolutionService.Source.INVENTORY){
                        InventoryMutationService.ConsumeResult consumed=
                            inventory.consume(
                                line.sourceIndex,
                                line.itemId,
                                line.quantity
                            );

                        if(!consumed.cleared)
                            throw new IllegalStateException(
                                "death destroy did not clear inventory line="+
                                line.lineId
                            );
                    }else{
                        EquipmentMutationService.ReplaceResult removed=
                            equipment.remove(
                                line.equipmentSlot,
                                line.itemId,
                                line.quantity
                            );

                        if(!removed.cleared())
                            throw new IllegalStateException(
                                "death destroy did not clear equipment line="+
                                line.lineId
                            );
                    }
                }
            }catch(Throwable failure){
                player.bank().restoreInventoryContainerSnapshot(
                    inventoryBefore
                );
                player.equipment().restoreAccountState(
                    equipmentItemsBefore,
                    equipmentQuantitiesBefore
                );

                if(failure instanceof RuntimeException)
                    throw (RuntimeException)failure;
                if(failure instanceof Error)
                    throw (Error)failure;
                throw new RuntimeException(failure);
            }

            if(resolution.deathSequence!=preview.deathSequence)
                throw new IllegalStateException(
                    "death resolution sequence drift"
                );

            Receipt receipt=
                new Receipt(
                    preview.deathSequence,
                    destroy.size(),
                    (int)destroyedQuantity,
                    explicitKeeps,
                    deferredAutoLoss,
                    standardUnresolved
                );

            receipts.put(
                preview.deathSequence,
                receipt
            );

            return receipt;
        }
    }

    Receipt receipt(long deathSequence){
        synchronized(player.mutationLock()){
            return receipts.get(deathSequence);
        }
    }

    private void requireLineCurrent(
        PlayerDeathItemResolutionService.CarriedLine line
    ){
        if(line.source==
                PlayerDeathItemResolutionService.Source.INVENTORY){
            InventoryMutationService.SlotSnapshot current=
                inventory.inspect(line.sourceIndex);

            if(!current.occupied||
               current.itemId!=line.itemId||
               current.quantity!=line.quantity)
                throw new IllegalStateException(
                    "death inventory preimage drift line="+
                    line.lineId
                );
            return;
        }

        EquipmentMutationService.SlotSnapshot current=
            equipment.inspect(line.equipmentSlot);

        if(!current.occupied||
           current.itemId!=line.itemId||
           current.quantity!=line.quantity)
            throw new IllegalStateException(
                "death equipment preimage drift line="+
                line.lineId
            );
    }
}

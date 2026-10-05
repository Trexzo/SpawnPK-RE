package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Exactly-once carried-state settlement for one canonical player death.
 *
 * This service deliberately stops before ground-item publication. Lost rows
 * are returned as immutable settlement output so a separate world/ground
 * transaction can prove its own atomicity before live composition.
 */
final class PlayerDeathCarriedSettlementService {
    static final class LostLine {
        final int itemId;
        final int amount;
        final PlayerDeathItemResolutionService.Source source;
        final int sourceIndex;
        final EquipmentSlot equipmentSlot;

        private LostLine(
            PlayerDeathItemResolutionService.Disposition disposition
        ){
            this.itemId=disposition.line.itemId;
            this.amount=disposition.lostAmount;
            this.source=disposition.line.source;
            this.sourceIndex=disposition.line.sourceIndex;
            this.equipmentSlot=disposition.line.equipmentSlot;
        }
    }

    static final class Receipt {
        final EntityId playerId;
        final long deathTick;
        final long deathSequence;
        final String deathCause;
        final String policyAuthority;
        final int keptTotalQuantity;
        final int lostTotalQuantity;
        final List<LostLine> lost;

        private Receipt(
            PlayerDeathItemResolutionService.Resolution resolution
        ){
            this.playerId=resolution.playerId;
            this.deathTick=resolution.deathTick;
            this.deathSequence=resolution.deathSequence;
            this.deathCause=resolution.deathCause;
            this.policyAuthority=resolution.policyAuthority;
            this.keptTotalQuantity=resolution.keptTotalQuantity();
            this.lostTotalQuantity=resolution.lostTotalQuantity();

            ArrayList<LostLine> rows=new ArrayList<>();
            for(PlayerDeathItemResolutionService.Disposition disposition:
                    resolution.dispositions)
                if(disposition.lostAmount>0)
                    rows.add(new LostLine(disposition));

            this.lost=Collections.unmodifiableList(rows);
        }
    }

    private final WorldPlayer player;
    private final PlayerDeathDispositionPolicy policy;
    private final PlayerDeathItemResolutionService resolution;
    private final LinkedHashMap<Long,Receipt> settled=
        new LinkedHashMap<>();

    PlayerDeathCarriedSettlementService(
        WorldPlayer player,
        PlayerDeathDispositionPolicy policy
    ){
        this.player=Objects.requireNonNull(player,"player");
        this.policy=Objects.requireNonNull(policy,"policy");
        this.resolution=
            new PlayerDeathItemResolutionService(
                player,
                requireAuthority(policy.authority())
            );
    }

    synchronized Receipt settleCurrentDeath(){
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolution.previewCurrentDeath();

        Receipt existing=settled.get(preview.deathSequence);
        if(existing!=null)
            return existing;

        Collection<PlayerDeathItemResolutionService.Decision> decisions=
            Objects.requireNonNull(
                policy.decide(preview),
                "death disposition decisions"
            );

        PlayerDeathItemResolutionService.Resolution resolved=
            resolution.resolveCurrentDeath(
                preview,
                decisions
            );

        synchronized(player.mutationLock()){
            requireCurrentDeath(resolved);

            Receipt replay=settled.get(resolved.deathSequence);
            if(replay!=null)
                return replay;

            BankState bank=player.bank();
            EquipmentState equipment=player.equipment();

            BankState.Stack[] nextBank=bank.bankSnapshot();
            BankState.Stack[] nextInventory=bank.inventorySnapshot();
            int[] nextEquipmentItems=equipment.containerItems();
            int[] nextEquipmentQuantities=
                equipment.containerQuantities();

            for(PlayerDeathItemResolutionService.Disposition disposition:
                    resolved.dispositions)
                applyDisposition(
                    disposition,
                    nextInventory,
                    nextEquipmentItems,
                    nextEquipmentQuantities
                );

            /*
             * Both carried containers are replaced while the single player
             * mutation lock is held. No policy callback or ground/world lock
             * is entered inside this critical section.
             */
            bank.restoreAccountState(
                nextBank,
                nextInventory,
                bank.isOpen()
            );
            equipment.restoreAccountState(
                nextEquipmentItems,
                nextEquipmentQuantities
            );

            Receipt receipt=new Receipt(resolved);
            settled.put(resolved.deathSequence,receipt);
            return receipt;
        }
    }

    synchronized Receipt get(long deathSequence){
        return settled.get(deathSequence);
    }

    synchronized int size(){
        return settled.size();
    }

    private void requireCurrentDeath(
        PlayerDeathItemResolutionService.Resolution resolved
    ){
        PlayerLifecycleState lifecycle=player.lifecycle();

        if(!player.id().equals(resolved.playerId)||
           !lifecycle.dead()||
           lifecycle.deathTick()!=resolved.deathTick||
           lifecycle.deathSequence()!=resolved.deathSequence||
           !safeCause(lifecycle.cause()).equals(resolved.deathCause))
            throw new IllegalStateException(
                "death identity changed before carried settlement player="+
                player.id()
            );
    }

    private static void applyDisposition(
        PlayerDeathItemResolutionService.Disposition disposition,
        BankState.Stack[] inventory,
        int[] equipmentItems,
        int[] equipmentQuantities
    ){
        PlayerDeathItemResolutionService.CarriedLine line=
            disposition.line;

        if(line.source==
                PlayerDeathItemResolutionService.Source.INVENTORY){
            if(line.sourceIndex<0||
               line.sourceIndex>=inventory.length)
                throw new IllegalStateException(
                    "death inventory source index invalid "+
                    line.sourceIndex
                );

            BankState.Stack current=inventory[line.sourceIndex];

            if(current==null||
               current.itemId!=line.itemId||
               current.qty!=line.quantity)
                throw new IllegalStateException(
                    "death inventory preimage changed slot="+
                    line.sourceIndex
                );

            inventory[line.sourceIndex]=
                disposition.keptAmount==0
                    ?null
                    :new BankState.Stack(
                        line.itemId,
                        disposition.keptAmount
                    );
            return;
        }

        if(line.equipmentSlot==null)
            throw new IllegalStateException(
                "death equipment line missing slot"
            );

        int index=line.equipmentSlot.equipmentIndex;

        if(index<0||
           index>=equipmentItems.length||
           equipmentItems[index]!=line.itemId||
           equipmentQuantities[index]!=line.quantity)
            throw new IllegalStateException(
                "death equipment preimage changed slot="+
                line.equipmentSlot
            );

        if(disposition.keptAmount==0){
            equipmentItems[index]=-1;
            equipmentQuantities[index]=0;
        }else{
            equipmentItems[index]=line.itemId;
            equipmentQuantities[index]=disposition.keptAmount;
        }
    }

    private static String safeCause(String value){
        return value==null||value.trim().isEmpty()
            ?"UNSPECIFIED"
            :value;
    }

    private static String requireAuthority(String value){
        if(value==null)
            throw new NullPointerException("policy authority");

        String clean=value.trim();
        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policy authority blank"
            );

        return clean;
    }
}

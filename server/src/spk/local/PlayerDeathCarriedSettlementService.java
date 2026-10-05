package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Exactly-once carried-state settlement for one canonical player death.
 *
 * Policy evaluation and postimage construction happen during prepare. Commit
 * revalidates the exact death plus full bank/inventory/equipment preimage under
 * WorldPlayer.mutationLock(), then applies one deterministic carried postimage.
 *
 * Ground/world publication is deliberately separate. Prepared.lost is the
 * immutable handoff for that transaction.
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

    static final class Prepared {
        final PlayerDeathItemResolutionService.Resolution resolution;
        final Receipt receipt;
        final boolean replay;

        final BankState.Stack[] expectedBank;
        final BankState.Stack[] expectedInventory;
        final boolean expectedBankOpen;
        final int[] expectedEquipmentItems;
        final int[] expectedEquipmentQuantities;

        final BankState.Stack[] postBank;
        final BankState.Stack[] postInventory;
        final int[] postEquipmentItems;
        final int[] postEquipmentQuantities;

        private Prepared(Receipt replayReceipt){
            this.resolution=null;
            this.receipt=replayReceipt;
            this.replay=true;
            this.expectedBank=null;
            this.expectedInventory=null;
            this.expectedBankOpen=false;
            this.expectedEquipmentItems=null;
            this.expectedEquipmentQuantities=null;
            this.postBank=null;
            this.postInventory=null;
            this.postEquipmentItems=null;
            this.postEquipmentQuantities=null;
        }

        private Prepared(
            PlayerDeathItemResolutionService.Resolution resolution,
            BankState.Stack[] expectedBank,
            BankState.Stack[] expectedInventory,
            boolean expectedBankOpen,
            int[] expectedEquipmentItems,
            int[] expectedEquipmentQuantities,
            BankState.Stack[] postBank,
            BankState.Stack[] postInventory,
            int[] postEquipmentItems,
            int[] postEquipmentQuantities
        ){
            this.resolution=resolution;
            this.receipt=new Receipt(resolution);
            this.replay=false;
            this.expectedBank=expectedBank;
            this.expectedInventory=expectedInventory;
            this.expectedBankOpen=expectedBankOpen;
            this.expectedEquipmentItems=expectedEquipmentItems;
            this.expectedEquipmentQuantities=
                expectedEquipmentQuantities;
            this.postBank=postBank;
            this.postInventory=postInventory;
            this.postEquipmentItems=postEquipmentItems;
            this.postEquipmentQuantities=
                postEquipmentQuantities;
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

    synchronized Prepared prepareCurrentDeath(){
        PlayerDeathItemResolutionService.DeathPreview preview=
            resolution.previewCurrentDeath();

        Receipt existing=settled.get(preview.deathSequence);
        if(existing!=null)
            return new Prepared(existing);

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
                return new Prepared(replay);

            BankState bank=player.bank();
            EquipmentState equipment=player.equipment();

            BankState.Stack[] expectedBank=bank.bankSnapshot();
            BankState.Stack[] expectedInventory=
                bank.inventorySnapshot();
            boolean expectedBankOpen=bank.isOpen();
            int[] expectedEquipmentItems=
                equipment.containerItems();
            int[] expectedEquipmentQuantities=
                equipment.containerQuantities();

            BankState.Stack[] postBank=
                copyStacks(expectedBank);
            BankState.Stack[] postInventory=
                copyStacks(expectedInventory);
            int[] postEquipmentItems=
                expectedEquipmentItems.clone();
            int[] postEquipmentQuantities=
                expectedEquipmentQuantities.clone();

            for(PlayerDeathItemResolutionService.Disposition disposition:
                    resolved.dispositions)
                applyDisposition(
                    disposition,
                    postInventory,
                    postEquipmentItems,
                    postEquipmentQuantities
                );

            return new Prepared(
                resolved,
                expectedBank,
                expectedInventory,
                expectedBankOpen,
                expectedEquipmentItems,
                expectedEquipmentQuantities,
                postBank,
                postInventory,
                postEquipmentItems,
                postEquipmentQuantities
            );
        }
    }

    synchronized Receipt commitPrepared(
        Prepared prepared
    ){
        Prepared checked=
            Objects.requireNonNull(
                prepared,
                "prepared"
            );

        if(checked.replay)
            return checked.receipt;

        synchronized(player.mutationLock()){
            requirePreparedCurrentLocked(
                checked
            );

            Receipt replay=
                settled.get(
                    checked.resolution.deathSequence
                );

            if(replay!=null)
                return replay;

            BankState bank=player.bank();
            EquipmentState equipment=player.equipment();

            bank.restoreAccountState(
                copyStacks(checked.postBank),
                copyStacks(checked.postInventory),
                checked.expectedBankOpen
            );
            equipment.restoreAccountState(
                checked.postEquipmentItems.clone(),
                checked.postEquipmentQuantities.clone()
            );

            settled.put(
                checked.resolution.deathSequence,
                checked.receipt
            );
            return checked.receipt;
        }
    }

    synchronized Receipt settleCurrentDeath(){
        return commitPrepared(
            prepareCurrentDeath()
        );
    }

    synchronized void requirePreparedCurrent(
        Prepared prepared
    ){
        Prepared checked=
            Objects.requireNonNull(
                prepared,
                "prepared"
            );

        if(checked.replay)
            return;

        synchronized(player.mutationLock()){
            requirePreparedCurrentLocked(
                checked
            );
        }
    }

    synchronized Receipt get(long deathSequence){
        return settled.get(deathSequence);
    }

    synchronized int size(){
        return settled.size();
    }

    private void requirePreparedCurrentLocked(
        Prepared prepared
    ){
        requireCurrentDeath(
            prepared.resolution
        );

        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();

        if(bank.isOpen()!=prepared.expectedBankOpen||
           !sameStacks(
                bank.bankSnapshot(),
                prepared.expectedBank)||
           !sameStacks(
                bank.inventorySnapshot(),
                prepared.expectedInventory)||
           !Arrays.equals(
                equipment.containerItems(),
                prepared.expectedEquipmentItems)||
           !Arrays.equals(
                equipment.containerQuantities(),
                prepared.expectedEquipmentQuantities))
            throw new IllegalStateException(
                "carried settlement preimage changed player="+
                player.id()+
                " deathSequence="+
                prepared.resolution.deathSequence
            );
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

    private static BankState.Stack[] copyStacks(
        BankState.Stack[] source
    ){
        BankState.Stack[] copy=
            new BankState.Stack[source.length];

        for(int i=0;i<source.length;i++){
            BankState.Stack stack=source[i];
            if(stack!=null)
                copy[i]=
                    new BankState.Stack(
                        stack.itemId,
                        stack.qty
                    );
        }

        return copy;
    }

    private static boolean sameStacks(
        BankState.Stack[] left,
        BankState.Stack[] right
    ){
        if(left.length!=right.length)
            return false;

        for(int i=0;i<left.length;i++){
            BankState.Stack a=left[i];
            BankState.Stack b=right[i];

            if(a==null||b==null){
                if(a!=b)
                    return false;
                continue;
            }

            if(a.itemId!=b.itemId||
               a.qty!=b.qty)
                return false;
        }

        return true;
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

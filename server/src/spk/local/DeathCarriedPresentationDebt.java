package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Session-owned retryable presentation debt for the exact carried postimage
 * produced by one settled player death.
 *
 * Gameplay mutation remains outside this class. It only captures immutable
 * presentation state after settlement and clears it after exact packet commit
 * or when same-session respawn presentation supersedes it.
 */
final class DeathCarriedPresentationDebt {
    @FunctionalInterface
    interface AppearancePublisher {
        void publish(
            int[] appearanceItems,
            ServerPacketWriter writer
        )throws IOException;
    }

    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G5_DEATH_CARRIED_PRESENTATION_V1";

    static final class Snapshot {
        final EntityId playerId;
        final long playerGeneration;
        final long deathSequence;
        final int[] inventoryItems;
        final int[] inventoryQuantities;
        final int[] equipmentItems;
        final int[] equipmentQuantities;
        final int[] appearanceItems;

        private Snapshot(
            WorldPlayer player
        ){
            this.playerId=player.id();
            this.playerGeneration=player.generation();
            this.deathSequence=
                player.lifecycle()
                    .deathSequence();

            if(!player.lifecycle().dead()||
               deathSequence<=0L)
                throw new IllegalStateException(
                    "death carried presentation requires current dead lifecycle"
                );

            this.inventoryItems=
                new int[
                    BankState.INVENTORY_CAPACITY
                ];
            this.inventoryQuantities=
                new int[
                    BankState.INVENTORY_CAPACITY
                ];
            Arrays.fill(
                inventoryItems,
                -1
            );

            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.InventorySlotSnapshot item=
                    player.bank()
                        .inventorySlotSnapshot(slot);

                if(!item.occupied)
                    continue;

                inventoryItems[slot]=item.itemId;
                inventoryQuantities[slot]=
                    item.quantity;
            }

            this.equipmentItems=
                player.equipment()
                    .containerItems();
            this.equipmentQuantities=
                player.equipment()
                    .containerQuantities();
            this.appearanceItems=
                appearanceFromEquipment(
                    equipmentItems
                );
        }
    }

    private Snapshot pending;

    synchronized void captureAfterSettlement(
        WorldPlayer player
    ){
        WorldPlayer checked=
            Objects.requireNonNull(
                player,
                "player"
            );

        Snapshot next=
            new Snapshot(
                checked
            );

        if(pending!=null){
            if(pending.playerId.equals(
                    next.playerId)&&
               pending.playerGeneration==
                    next.playerGeneration&&
               pending.deathSequence==
                    next.deathSequence)
                return;

            throw new IllegalStateException(
                "conflicting death carried presentation debt player="+
                checked.id()+
                " pendingDeath="+
                pending.deathSequence+
                " nextDeath="+
                next.deathSequence
            );
        }

        pending=next;
    }

    synchronized boolean pending(){
        return pending!=null;
    }

    synchronized long pendingDeathSequence(){
        return pending==null
            ?0L
            :pending.deathSequence;
    }

    boolean publishIfPending(
        World world,
        WorldPlayer player,
        ServerPacketWriter writer,
        AppearancePublisher appearance,
        boolean respawnSupersedes,
        String tag
    ){
        Objects.requireNonNull(
            world,
            "world"
        );
        Objects.requireNonNull(
            player,
            "player"
        );
        Objects.requireNonNull(
            writer,
            "writer"
        );
        Objects.requireNonNull(
            appearance,
            "appearance"
        );

        final Snapshot debt;

        synchronized(this){
            debt=pending;
        }

        if(debt==null)
            return false;

        if(!debt.playerId.equals(
                player.id())||
           !world.players().owns(
                player,
                debt.playerGeneration)){
            clearIfSame(debt);
            return false;
        }

        if(respawnSupersedes){
            clearIfSame(debt);
            return false;
        }

        if(!player.lifecycle().dead()||
           player.lifecycle().deathSequence()!=
                debt.deathSequence){
            clearIfSame(debt);
            return false;
        }

        boolean batchActive=false;
        boolean committed=false;

        try{
            writer.beginBatch();
            batchActive=true;

            writer.varShort(
                53,
                BootstrapPackets.itemContainer53(
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    debt.inventoryItems,
                    debt.inventoryQuantities
                )
            );
            writer.varShort(
                53,
                BootstrapPackets.equipmentContainer53(
                    debt.equipmentItems,
                    debt.equipmentQuantities
                )
            );
            appearance.publish(
                debt.appearanceItems.clone(),
                writer
            );

            writer.endBatch();
            batchActive=false;
            committed=true;
        }catch(IOException failure){
            abortQuietly(
                writer,
                batchActive
            );

            System.out.println(
                cleanTag(tag)+
                "G5_DEATH_CARRIED_PRESENTATION_RETRY"+
                " deathSequence="+
                debt.deathSequence+
                " error="+
                failure.getClass()
                    .getSimpleName()
            );
            return false;
        }catch(RuntimeException failure){
            abortQuietly(
                writer,
                batchActive
            );
            throw failure;
        }catch(Error failure){
            abortQuietly(
                writer,
                batchActive
            );
            throw failure;
        }

        if(!committed)
            return false;

        clearIfSame(debt);

        System.out.println(
            cleanTag(tag)+
            "G5_DEATH_CARRIED_PRESENTATION_COMMITTED"+
            " deathSequence="+
            debt.deathSequence+
            " generation="+
            debt.playerGeneration+
            " authority="+
            AUTHORITY
        );
        return true;
    }

    private synchronized void clearIfSame(
        Snapshot expected
    ){
        if(pending==expected)
            pending=null;
    }

    private static void abortQuietly(
        ServerPacketWriter writer,
        boolean batchActive
    ){
        if(!batchActive)
            return;

        try{
            writer.abortBatch();
        }catch(Throwable ignored){}
    }

    private static int[] appearanceFromEquipment(
        int[] equipmentItems
    ){
        int[] appearance=
            new int[
                EquipmentState.APPEARANCE_SLOTS
            ];
        Arrays.fill(
            appearance,
            -1
        );

        for(EquipmentSlot slot:
                EquipmentSlot.values())
            if(slot.appearanceIndex>=0)
                appearance[
                    slot.appearanceIndex
                ]=
                    equipmentItems[
                        slot.equipmentIndex
                    ];

        return appearance;
    }

    private static String cleanTag(
        String value
    ){
        return value==null
            ?""
            :value;
    }
}

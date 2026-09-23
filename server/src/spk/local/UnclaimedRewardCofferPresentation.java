package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Coffer of Unclaimed Rewards & Prizes client adapter.
 *
 * Exact client authority covers the 70-slot container, four per-item Remove
 * options, and two explicit bulk destination buttons. Durable reward delivery,
 * expiry, fallback, capacity overflow policy and actual inventory/bank mutation
 * remain semantic/server authority.
 */
final class UnclaimedRewardCofferPresentation {
    static final int ROOT=42100;
    static final int CONTAINER_WIDGET=42101;
    static final int BULK_INVENTORY_WIDGET=42104;
    static final int BULK_INVENTORY_HOVER=42105;
    static final int BULK_BANK_WIDGET=42108;
    static final int BULK_BANK_HOVER=42109;
    static final int CAPACITY=70;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum AmountMode {
        ONE,
        FIVE,
        TEN,
        ALL
    }

    static final class ProjectedSlot {
        final int clientSlot;
        final UnclaimedRewardCofferService.SlotId slotId;
        final int itemId;
        final int quantity;
        final long available;

        ProjectedSlot(
            int clientSlot,
            UnclaimedRewardCofferService.SlotSnapshot slot
        ){
            this.clientSlot=clientSlot;
            this.slotId=
                Objects.requireNonNull(
                    slot.slotId,
                    "slotId"
                );
            this.itemId=checkedItemId(slot.itemId);
            this.quantity=checkedQuantity(slot.amount);
            this.available=slot.available;

            if(available<0L||
               available>slot.amount)
                throw new IllegalStateException(
                    "reward coffer availability drift slot="+
                    slot.slotId+
                    " amount="+slot.amount+
                    " available="+available
                );
        }
    }

    static final class Projection {
        final String ownerRef;
        final List<ProjectedSlot> slots;

        Projection(
            String ownerRef,
            List<ProjectedSlot> slots
        ){
            this.ownerRef=
                Objects.requireNonNull(
                    ownerRef,
                    "ownerRef"
                );
            this.slots=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        slots
                    )
                );
        }

        ProjectedSlot clientSlot(
            int clientSlot
        ){
            if(clientSlot<0||
               clientSlot>=CAPACITY)
                return null;

            return clientSlot<slots.size()
                ?slots.get(clientSlot)
                :null;
        }

        byte[] containerBody()
            throws IOException
        {
            int[] itemIds=new int[CAPACITY];
            int[] quantities=new int[CAPACITY];

            java.util.Arrays.fill(
                itemIds,
                -1
            );

            for(ProjectedSlot slot:slots){
                itemIds[slot.clientSlot]=
                    slot.itemId;
                quantities[slot.clientSlot]=
                    slot.quantity;
            }

            return BootstrapPackets
                .itemContainer53(
                    CONTAINER_WIDGET,
                    itemIds,
                    quantities
                );
        }
    }

    /**
     * Exact per-item Remove intent. The exact client does not establish whether
     * one row removal settles to inventory or bank, so destination is purposely
     * absent here.
     */
    static final class RemoveIntent {
        final UnclaimedRewardCofferService.SlotId slotId;
        final int clientSlot;
        final int itemId;
        final AmountMode amountMode;

        RemoveIntent(
            ProjectedSlot slot,
            AmountMode amountMode
        ){
            this.slotId=slot.slotId;
            this.clientSlot=slot.clientSlot;
            this.itemId=slot.itemId;
            this.amountMode=
                Objects.requireNonNull(
                    amountMode,
                    "amountMode"
                );
        }
    }

    static final class BulkIntent {
        final UnclaimedRewardCofferService.Destination destination;

        BulkIntent(
            UnclaimedRewardCofferService.Destination destination
        ){
            this.destination=
                Objects.requireNonNull(
                    destination,
                    "destination"
                );
        }
    }

    static Projection project(
        UnclaimedRewardCofferService.Snapshot snapshot
    ){
        UnclaimedRewardCofferService.Snapshot checked=
            Objects.requireNonNull(
                snapshot,
                "snapshot"
            );

        if(checked.capacity!=CAPACITY)
            throw new IllegalStateException(
                "reward coffer capacity drift expected="+
                CAPACITY+
                " actual="+checked.capacity
            );

        if(checked.slots.size()>CAPACITY)
            throw new IllegalArgumentException(
                "reward coffer slots="+
                checked.slots.size()+
                " max="+CAPACITY
            );

        ArrayList<ProjectedSlot> slots=
            new ArrayList<>();

        /*
         * LOCAL_LAB adapter projection. The semantic service owns stable SlotIds,
         * while exact client evidence does not recover original server physical
         * slot allocation. Project current semantic order compactly and require
         * slot + item identity parity on every per-item input.
         */
        for(int i=0;
            i<checked.slots.size();
            i++)
            slots.add(
                new ProjectedSlot(
                    i,
                    checked.slots.get(i)
                )
            );

        return new Projection(
            checked.ownerRef,
            slots
        );
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    ROOT
                )
            );
    }

    static void publishContainer(
        ServerPacketWriter packets,
        Projection projection
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .varShort(
                53,
                Objects.requireNonNull(
                    projection,
                    "projection"
                ).containerBody()
            );
    }

    static RemoveIntent resolveItemAction(
        ItemContainerAction action,
        Projection projection
    ){
        ItemContainerAction checked=
            Objects.requireNonNull(
                action,
                "action"
            );
        Projection projected=
            Objects.requireNonNull(
                projection,
                "projection"
            );

        if(checked.widgetId!=
                CONTAINER_WIDGET)
            return null;

        AmountMode amountMode=
            amountMode(checked.opcode);

        if(amountMode==null)
            throw new IllegalArgumentException(
                "unsupported reward coffer item opcode "+
                checked.opcode
            );

        if(checked.extra!=0)
            throw new IllegalArgumentException(
                "unexpected reward coffer item extra="+
                checked.extra
            );

        ProjectedSlot slot=
            projected.clientSlot(
                checked.slot
            );

        if(slot==null)
            throw new IllegalStateException(
                "stale/empty reward coffer client slot "+
                checked.slot
            );

        if(checked.itemId!=slot.itemId)
            throw new IllegalStateException(
                "stale reward coffer item identity clientSlot="+
                checked.slot+
                " actionItem="+checked.itemId+
                " projectedItem="+slot.itemId
            );

        return new RemoveIntent(
            slot,
            amountMode
        );
    }

    static BulkIntent resolveWidget(
        int widgetId
    ){
        if(widgetId<0||
           widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        switch(widgetId){
            case BULK_INVENTORY_WIDGET:
                return new BulkIntent(
                    UnclaimedRewardCofferService
                        .Destination
                        .INVENTORY
                );
            case BULK_BANK_WIDGET:
                return new BulkIntent(
                    UnclaimedRewardCofferService
                        .Destination
                        .BANK
                );
            default:
                return null;
        }
    }

    private static AmountMode amountMode(
        int opcode
    ){
        switch(opcode){
            case 145:
                return AmountMode.ONE;
            case 117:
                return AmountMode.FIVE;
            case 43:
                return AmountMode.TEN;
            case 129:
                return AmountMode.ALL;
            default:
                return null;
        }
    }

    private static int checkedItemId(
        int itemId
    ){
        if(itemId<0||
           itemId>=0xffff)
            throw new IllegalArgumentException(
                "reward coffer itemId="+
                itemId
            );

        return itemId;
    }

    private static int checkedQuantity(
        long quantity
    ){
        if(quantity<=0L||
           quantity>Integer.MAX_VALUE)
            throw new IllegalArgumentException(
                "reward coffer quantity outside S2C53 i32 range "+
                quantity
            );

        return (int)quantity;
    }

    private UnclaimedRewardCofferPresentation(){}
}

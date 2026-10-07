package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Looting Bag client adapter over semantic LootingBagService state.
 *
 * Exact client authority covers root/container shape and the four per-item
 * Deposit 1/5/10/All option transports. Original server admission, Wilderness,
 * death, persistence, bank eligibility and bulk-widget transport remain outside
 * this adapter.
 */
final class LootingBagPresentation {
    static final int ROOT=26700;
    static final int CONTAINER_WIDGET=26706;
    static final int EMPTY_TEXT_WIDGET=26707;
    static final int BULK_DEPOSIT_WIDGET=26708;
    static final int CAPACITY=28;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum AmountMode {
        ONE,
        FIVE,
        TEN,
        ALL
    }

    static final class ProjectedSlot {
        final int clientSlot;
        final LootingBagService.SlotId slotId;
        final int itemId;
        final int quantity;
        final long available;

        ProjectedSlot(
            int clientSlot,
            LootingBagService.SlotSnapshot slot
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
                    "Looting Bag availability drift slot="+
                    slot.slotId+
                    " amount="+slot.amount+
                    " available="+available
                );
        }

        ProjectedSlot(
            int clientSlot,
            LootingBagService.SlotId slotId,
            int itemId,
            long amount,
            long available
        ){
            this.clientSlot=clientSlot;
            this.slotId=
                Objects.requireNonNull(
                    slotId,
                    "slotId"
                );
            this.itemId=checkedItemId(itemId);
            this.quantity=checkedQuantity(amount);
            this.available=available;

            if(available<0L||
               available>amount)
                throw new IllegalStateException(
                    "Looting Bag predicted availability drift slot="+
                    slotId+
                    " amount="+amount+
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

    static final class DepositIntent {
        final LootingBagService.SlotId slotId;
        final int clientSlot;
        final int itemId;
        final AmountMode amountMode;

        DepositIntent(
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

    static Projection project(
        LootingBagService.Snapshot snapshot
    ){
        LootingBagService.Snapshot checked=
            Objects.requireNonNull(
                snapshot,
                "snapshot"
            );

        if(checked.capacity!=CAPACITY)
            throw new IllegalStateException(
                "Looting Bag capacity drift expected="+
                CAPACITY+
                " actual="+checked.capacity
            );

        if(checked.slots.size()>CAPACITY)
            throw new IllegalArgumentException(
                "Looting Bag slots="+
                checked.slots.size()+
                " max="+CAPACITY
            );

        ArrayList<ProjectedSlot> slots=
            new ArrayList<>();

        /*
         * LOCAL_LAB adapter projection: the semantic service owns stable
         * SlotIds but exact client evidence does not recover original server
         * physical-slot allocation. Project current semantic order compactly
         * into client slots and require item-identity revalidation on input.
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

    static Projection projectAfterBankDeposit(
        LootingBagService.Snapshot snapshot,
        LootingBagService.SettlementSnapshot settlement
    ){
        LootingBagService.Snapshot checked=
            Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        LootingBagService.SettlementSnapshot reserved=
            Objects.requireNonNull(
                settlement,
                "settlement"
            );

        if(reserved.state!=
                LootingBagService.SettlementState.RESERVED)
            throw new IllegalArgumentException(
                "reserved Looting Bag settlement required"
            );

        if(!checked.ownerRef.equals(
                reserved.ownerRef))
            throw new IllegalArgumentException(
                "Looting Bag settlement owner mismatch"
            );

        LinkedHashMap<LootingBagService.SlotId,Long>
            amounts=
                new LinkedHashMap<>();

        for(LootingBagService.SettlementLineSnapshot line:
                reserved.lines){
            if(line.amount<=0L||
               amounts.put(
                   Objects.requireNonNull(
                       line.slotId,
                       "settlement slotId"
                   ),
                   line.amount
               )!=null)
                throw new IllegalArgumentException(
                    "invalid duplicate/amount Looting Bag settlement line"
                );
        }

        ArrayList<ProjectedSlot> projected=
            new ArrayList<>();

        for(LootingBagService.SlotSnapshot slot:
                checked.slots){
            Long debit=amounts.remove(slot.slotId);
            long line=
                debit==null
                    ?0L
                    :debit.longValue();

            if(line<0L||
               line>slot.reserved||
               line>slot.amount)
                throw new IllegalStateException(
                    "Looting Bag settlement exceeds reserved slot="+
                    slot.slotId
                );

            long nextAmount=
                slot.amount-line;
            long nextReserved=
                slot.reserved-line;

            if(nextAmount==0L){
                if(nextReserved!=0L)
                    throw new IllegalStateException(
                        "Looting Bag zero postimage retains reservation slot="+
                        slot.slotId
                    );
                continue;
            }

            projected.add(
                new ProjectedSlot(
                    projected.size(),
                    slot.slotId,
                    slot.itemId,
                    nextAmount,
                    nextAmount-nextReserved
                )
            );
        }

        if(!amounts.isEmpty())
            throw new IllegalStateException(
                "Looting Bag settlement references missing slots "+
                amounts.keySet()
            );

        return new Projection(
            checked.ownerRef,
            projected
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

    static DepositIntent resolveItemAction(
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
                "unsupported Looting Bag item opcode "+
                checked.opcode
            );

        if(checked.extra!=0)
            throw new IllegalArgumentException(
                "unexpected Looting Bag item extra="+
                checked.extra
            );

        ProjectedSlot slot=
            projected.clientSlot(
                checked.slot
            );

        if(slot==null)
            throw new IllegalStateException(
                "stale/empty Looting Bag client slot "+
                checked.slot
            );

        if(checked.itemId!=slot.itemId)
            throw new IllegalStateException(
                "stale Looting Bag item identity clientSlot="+
                checked.slot+
                " actionItem="+checked.itemId+
                " projectedItem="+slot.itemId
            );

        return new DepositIntent(
            slot,
            amountMode
        );
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
                "Looting Bag itemId="+
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
                "Looting Bag quantity outside S2C53 i32 range "+
                quantity
            );

        return (int)quantity;
    }

    private LootingBagPresentation(){}
}

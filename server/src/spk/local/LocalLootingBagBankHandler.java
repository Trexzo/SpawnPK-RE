package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Live LocalLab composition for exact-v308 Looting Bag per-item Deposit actions.
 *
 * Bag intake, Wilderness/death rules, accepted items, persistence and original
 * SpawnPK bank policy remain explicitly outside this handler.
 */
final class LocalLootingBagBankHandler {
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_G8_LOOTING_BAG_BANK_V1";

    enum Status {
        OPENED,
        CLOSED_UI_NOOP,
        NO_AVAILABLE_QUANTITY,
        BANK_REJECTED,
        DEPOSITED
    }

    static final class Result {
        final Status status;
        final int itemId;
        final int amount;
        final String detail;
        final String saveReason;

        Result(
            Status status,
            int itemId,
            int amount,
            String detail,
            String saveReason
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.itemId=itemId;
            this.amount=amount;
            this.detail=detail;
            this.saveReason=saveReason;
        }

        boolean mutated(){
            return status==Status.DEPOSITED;
        }
    }

    private final Supplier<String> ownerRef;
    private final LootingBagService service;
    private final BankState bank;
    private boolean open;

    LocalLootingBagBankHandler(
        Supplier<String> ownerRef,
        LootingBagService service,
        BankState bank
    ){
        this.ownerRef=Objects.requireNonNull(
            ownerRef,
            "ownerRef"
        );
        this.service=Objects.requireNonNull(
            service,
            "service"
        );
        this.bank=Objects.requireNonNull(
            bank,
            "bank"
        );
    }

    Result open(
        ServerPacketWriter writer
    )throws IOException{
        ServerPacketWriter packets=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        String owner=owner();
        LootingBagService.Snapshot snapshot=
            service.get(owner);
        LootingBagPresentation.Projection projection=
            snapshot==null
                ?new LootingBagPresentation.Projection(
                    owner,
                    Collections.emptyList()
                )
                :LootingBagPresentation.project(
                    snapshot
                );

        packets.beginBatch();
        boolean ended=false;

        try{
            LootingBagPresentation.open(
                packets
            );
            LootingBagPresentation.publishContainer(
                packets,
                projection
            );
            packets.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                abortQuietly(packets);
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                abortQuietly(packets);
            throw failure;
        }catch(Error failure){
            if(!ended)
                abortQuietly(packets);
            throw failure;
        }

        open=true;

        return new Result(
            Status.OPENED,
            -1,
            0,
            "root="+LootingBagPresentation.ROOT+
            " container="+
            LootingBagPresentation.CONTAINER_WIDGET,
            null
        );
    }

    boolean close(){
        boolean wasOpen=open;
        open=false;
        return wasOpen;
    }

    boolean isOpen(){
        return open;
    }

    boolean owns(
        ItemContainerAction action
    ){
        return action!=null&&
            action.widgetId==
                LootingBagPresentation
                    .CONTAINER_WIDGET;
    }

    Result handle(
        ItemContainerAction action,
        ServerPacketWriter writer
    )throws IOException{
        if(!owns(action))
            return null;

        if(!open)
            return new Result(
                Status.CLOSED_UI_NOOP,
                action.itemId,
                0,
                "rootOpen=false",
                null
            );

        LootingBagService.Snapshot before=
            service.get(owner());

        if(before==null)
            return new Result(
                Status.BANK_REJECTED,
                action.itemId,
                0,
                "EMPTY_BAG",
                null
            );

        LootingBagPresentation.Projection projection=
            LootingBagPresentation.project(
                before
            );

        final LootingBagPresentation.DepositIntent
            intent;

        try{
            intent=
                LootingBagPresentation
                    .resolveItemAction(
                        action,
                        projection
                    );
        }catch(IllegalArgumentException failure){
            return new Result(
                Status.BANK_REJECTED,
                action.itemId,
                0,
                "INPUT_REJECTED "+
                    failure.getMessage(),
                null
            );
        }catch(IllegalStateException failure){
            return new Result(
                Status.BANK_REJECTED,
                action.itemId,
                0,
                "INPUT_REJECTED "+
                    failure.getMessage(),
                null
            );
        }

        LootingBagService.SlotSnapshot slot=
            before.slot(
                intent.slotId
            );

        if(slot==null)
            return new Result(
                Status.BANK_REJECTED,
                intent.itemId,
                0,
                "SEMANTIC_SLOT_MISSING",
                null
            );

        int amount=
            resolvedAmount(
                intent.amountMode,
                slot.available
            );

        if(amount<=0)
            return new Result(
                Status.NO_AVAILABLE_QUANTITY,
                intent.itemId,
                0,
                "available="+slot.available,
                null
            );

        LootingBagService.SettlementSnapshot
            reservation=
                service.beginBankDeposit(
                    owner(),
                    intent.slotId,
                    amount
                );

        BankState.PreparedExternalBankCredit
            bankCredit=
                bank.prepareExternalBankCredit(
                    intent.itemId,
                    amount
                );

        if(!bankCredit.accepted()){
            requireCancelled(
                reservation
            );

            return new Result(
                Status.BANK_REJECTED,
                intent.itemId,
                0,
                bankCredit.rejection,
                null
            );
        }

        LootingBagPresentation.Projection postimage=
            LootingBagPresentation
                .projectAfterBankDeposit(
                    service.get(owner()),
                    reservation
                );

        ServerPacketWriter packets=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        packets.beginBatch();
        boolean ended=false;

        try{
            LootingBagPresentation.publishContainer(
                packets,
                postimage
            );
            packets.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                abortQuietly(packets);
            requireCancelled(reservation);
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                abortQuietly(packets);
            requireCancelled(reservation);
            throw failure;
        }catch(Error failure){
            if(!ended)
                abortQuietly(packets);
            requireCancelled(reservation);
            throw failure;
        }

        /*
         * World execution serializes the session mutation path. Both commits
         * below are prevalidated, non-I/O state commits after the client
         * postimage has been accepted by ServerPacketWriter.
         */
        bank.commitPreparedExternalBankCredit(
            bankCredit
        );

        if(!service.confirmBankDeposit(
                reservation.settlementId))
            throw new IllegalStateException(
                "Looting Bag reserved settlement was not committed"
            );

        return new Result(
            Status.DEPOSITED,
            intent.itemId,
            amount,
            "slot="+intent.slotId+
            " mode="+intent.amountMode+
            " bankSlot="+
            bankCredit.destinationSlot,
            "LOOTING_BAG_BANK_DEPOSIT"
        );
    }

    private String owner(){
        String value=ownerRef.get();

        if(value==null||
           value.trim().isEmpty())
            throw new IllegalStateException(
                "Looting Bag owner unavailable"
            );

        return value;
    }

    private int resolvedAmount(
        LootingBagPresentation.AmountMode mode,
        long available
    ){
        if(available<=0L)
            return 0;

        long requested;

        switch(
            Objects.requireNonNull(
                mode,
                "mode"
            )
        ){
            case ONE:
                requested=1L;
                break;
            case FIVE:
                requested=5L;
                break;
            case TEN:
                requested=10L;
                break;
            case ALL:
                requested=available;
                break;
            default:
                throw new IllegalStateException(
                    "unknown Looting Bag amount mode "+
                    mode
                );
        }

        long amount=
            Math.min(
                requested,
                available
            );

        if(amount>Integer.MAX_VALUE)
            throw new IllegalStateException(
                "Looting Bag bank amount outside i32 "+
                amount
            );

        return (int)amount;
    }

    private void requireCancelled(
        LootingBagService.SettlementSnapshot reservation
    ){
        if(!service.cancelBankDeposit(
                reservation.settlementId))
            throw new IllegalStateException(
                "Looting Bag reservation cancellation failed "+
                reservation.settlementId
            );
    }

    private static void abortQuietly(
        ServerPacketWriter writer
    ){
        try{
            writer.abortBatch();
        }catch(Throwable ignored){}
    }
}

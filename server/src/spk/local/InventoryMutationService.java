package spk.local;

import java.util.Objects;

/**
 * Semantic protocol-independent inventory mutation boundary over BankState's
 * canonical 28-slot inventory.
 */
final class InventoryMutationService {
    static final class SlotSnapshot {
        final int slot;
        final boolean occupied;
        final int itemId;
        final int quantity;
        final String policyAuthority;

        private SlotSnapshot(
            int slot,
            boolean occupied,
            int itemId,
            int quantity,
            String policyAuthority
        ){
            this.slot=slot;
            this.occupied=occupied;
            this.itemId=itemId;
            this.quantity=quantity;
            this.policyAuthority=policyAuthority;
        }
    }

    static final class ConsumeResult {
        final int slot;
        final int itemId;
        final int requested;
        final int beforeQuantity;
        final int afterQuantity;
        final boolean cleared;
        final String policyAuthority;

        private ConsumeResult(
            BankState.InventoryConsumeResult result,
            String policyAuthority
        ){
            this.slot=result.slot;
            this.itemId=result.itemId;
            this.requested=result.requested;
            this.beforeQuantity=result.beforeQuantity;
            this.afterQuantity=result.afterQuantity;
            this.cleared=result.cleared;
            this.policyAuthority=policyAuthority;
        }
    }

    private final BankState inventory;
    private final Object mutationLock;
    private final String policyAuthority;

    InventoryMutationService(
        WorldPlayer player,
        String policyAuthority
    ){
        WorldPlayer owner=
            Objects.requireNonNull(
                player,
                "player"
            );

        this.inventory=owner.bank();
        this.mutationLock=owner.mutationLock();
        this.policyAuthority=
            requireGameplayAuthority(
                policyAuthority
            );
    }

    SlotSnapshot inspect(int slot){
        synchronized(mutationLock){
            BankState.InventorySlotSnapshot raw=
                inventory.inventorySlotSnapshot(
                    slot
                );

            return new SlotSnapshot(
                raw.slot,
                raw.occupied,
                raw.itemId,
                raw.quantity,
                policyAuthority
            );
        }
    }

    ConsumeResult consume(
        int slot,
        int expectedItemId,
        int amount
    ){
        synchronized(mutationLock){
            BankState.InventoryConsumeResult raw=
                inventory.consumeInventoryAmountSemantic(
                    slot,
                    expectedItemId,
                    amount
                );

            return new ConsumeResult(
                raw,
                policyAuthority
            );
        }
    }

    String policyAuthority(){
        return policyAuthority;
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

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define inventory mutation policy actual="+
                clean
            );

        return clean;
    }
}

package spk.local;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * G21.21 read-only claim simulation over the canonical 28-slot inventory.
 *
 * This does not grant, acknowledge, reserve, persist, or publish any item.
 * Current account persistence captures asynchronously; there is not yet a
 * proven crash-durable, atomic inventory-plus-Mailbox claim commit. Native
 * widget 32181 therefore remains explicitly non-mutating.
 *
 * For item definitions with unknown stackability, fail closed instead of
 * treating the ItemCatalog fallback as server reward authority.
 */
final class MailboxInventoryClaimPreflight {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2121_CLAIM_PREFLIGHT_ONLY";

    static final class Preview {
        final boolean eligible;
        final String reason;
        final int[] itemIds;
        final int[] quantities;
        final int attachmentCount;
        final String authority;

        private Preview(
            boolean eligible,String reason,
            int[] itemIds,int[] quantities,
            int attachmentCount
        ){
            this.eligible=eligible;
            this.reason=Objects.requireNonNull(reason,"reason");
            this.itemIds=itemIds==null?null:itemIds.clone();
            this.quantities=quantities==null?null:quantities.clone();
            this.attachmentCount=attachmentCount;
            this.authority=AUTHORITY;
        }

        static Preview reject(String reason){
            return new Preview(false,reason,null,null,0);
        }

        static Preview eligible(
            int[] ids,int[] quantities,int attachmentCount
        ){
            return new Preview(
                true,"PREFLIGHT_ONLY_NOT_SETTLED",
                ids,quantities,attachmentCount
            );
        }
    }

    static Preview inspect(
        WorldPlayer owner,
        MailboxRewardDeliveryService.Snapshot selected
    ){
        WorldPlayer player=Objects.requireNonNull(owner,"owner");
        MailboxRewardDeliveryService.Snapshot row=
            Objects.requireNonNull(selected,"selected");

        synchronized(player.mutationLock()){
            MailboxRewardDeliveryService.Snapshot current=
                player.mailbox().get(row.message.messageId);
            if(current==null||current.message!=row.message)
                throw new IllegalStateException(
                    "stale Mailbox claim selection"
                );
            if(current.claimState!=
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
                return Preview.reject("NOT_UNCLAIMED");

            List<RewardDeliveryMessage.Attachment> items=
                current.message.attachments;
            if(items==null||items.isEmpty())
                return Preview.reject("NO_ATTACHMENTS");

            BankState inventory=player.bank();
            int slots=inventory.inventoryCapacity();
            if(slots!=28||slots!=BankState.INVENTORY_CAPACITY)
                return Preview.reject("UNSUPPORTED_INVENTORY_CAPACITY");

            int[] ids=new int[slots];
            int[] qty=new int[slots];
            Arrays.fill(ids,-1);
            for(int i=0;i<slots;i++){
                BankState.InventorySlotSnapshot existing=
                    inventory.inventorySlotSnapshot(i);
                if(!existing.occupied)
                    continue;
                if(existing.itemId<0||existing.quantity<=0)
                    return Preview.reject("INVALID_INVENTORY_PREIMAGE");
                ids[i]=existing.itemId;
                qty[i]=existing.quantity;
            }

            for(RewardDeliveryMessage.Attachment item:items){
                if(item==null)
                    return Preview.reject("NULL_ATTACHMENT");

                int id=item.itemId;
                long amount=item.amount;
                if(id<0||id>=65535||!ItemDefinitionRepository.exists(id))
                    return Preview.reject("UNSUPPORTED_ITEM_ID");
                if(amount<=0||amount>Integer.MAX_VALUE)
                    return Preview.reject("INVALID_ATTACHMENT_QUANTITY");

                String evidence=
                    ItemDefinitionRepository.stackabilityEvidence(id);
                if(evidence==null||
                   "UNKNOWN_DEFAULT_NONSTACKABLE".equals(evidence))
                    return Preview.reject("STACKABILITY_NOT_VERIFIED");

                boolean stackable=
                    ItemDefinitionRepository.isStackable(id);
                if(stackable){
                    int dst=-1;
                    for(int i=0;i<slots;i++){
                        if(ids[i]==id){dst=i;break;}
                    }
                    if(dst<0)
                        dst=firstFree(ids);
                    if(dst<0)
                        return Preview.reject("INVENTORY_FULL");

                    long merged=(long)qty[dst]+amount;
                    if(merged>Integer.MAX_VALUE)
                        return Preview.reject("STACK_OVERFLOW");
                    ids[dst]=id;
                    qty[dst]=(int)merged;
                }else{
                    int needed=(int)amount;
                    int free=0;
                    for(int idAt:ids)
                        if(idAt<0)free++;
                    if(needed>free)
                        return Preview.reject("INVENTORY_FULL");

                    for(int n=0;n<needed;n++){
                        int dst=firstFree(ids);
                        ids[dst]=id;
                        qty[dst]=1;
                    }
                }
            }

            return Preview.eligible(ids,qty,items.size());
        }
    }

    private static int firstFree(int[] ids){
        for(int i=0;i<ids.length;i++)
            if(ids[i]<0)return i;
        return -1;
    }

    private MailboxInventoryClaimPreflight(){}
}

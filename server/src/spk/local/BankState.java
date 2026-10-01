package spk.local;

import java.io.*;
import java.util.*;

/** Clean-room local bank state for the v4 protocol milestone. */
final class BankState {
    static final int COSMETIC_WIDGET = 27701; // exact client equipment-tab cosmetic item widget

    static final int BANK_ROOT = 5292;
    static final int BANK_WRAPPER_ROOT = 23000; // exact current SpawnPK wrapper; activation remains runtime-gated
    static final int BANK_CONTAINER = 5382;
    static final int BANK_INVENTORY_ROOT = 5063;
    static final int BANK_INVENTORY_CONTAINER = 5064;
    static final int NORMAL_INVENTORY_ROOT = 3213;
    static final int NORMAL_INVENTORY_CONTAINER = 3214;
    static final int DEPOSIT_INVENTORY_WIDGET = 26012;
    static final int TOGGLE_PLACEHOLDERS_WIDGET = 39971;
    static final int BANK_OBJECT_ID = 26972;

    // Exact client widget dimensions: bank 8x44, bank-side inventory 4x7.
    static final int BANK_CAPACITY = 352;
    static final int INVENTORY_CAPACITY = 28;

    static final class Stack {
        final int itemId;
        int qty;
        int tab;
        Stack(int itemId, int qty) { this(itemId, qty, 0); }
        Stack(int itemId, int qty, int tab) { this.itemId=itemId; this.qty=qty; this.tab=tab; }
        @Override public String toString(){return "Stack{"+itemId+" x"+qty+",tab="+tab+"}";}
    }


    /** Protocol-independent immutable view of one canonical inventory slot. */
    static final class InventorySlotSnapshot {
        final int slot;
        final boolean occupied;
        final int itemId;
        final int quantity;

        InventorySlotSnapshot(int slot,boolean occupied,int itemId,int quantity){
            this.slot=slot;
            this.occupied=occupied;
            this.itemId=itemId;
            this.quantity=quantity;
        }
    }

    /** Protocol-independent immutable result of one exact-slot inventory consume. */
    static final class InventoryConsumeResult {
        final int slot;
        final int itemId;
        final int requested;
        final int beforeQuantity;
        final int afterQuantity;
        final boolean cleared;

        InventoryConsumeResult(
            int slot,
            int itemId,
            int requested,
            int beforeQuantity,
            int afterQuantity
        ){
            this.slot=slot;
            this.itemId=itemId;
            this.requested=requested;
            this.beforeQuantity=beforeQuantity;
            this.afterQuantity=afterQuantity;
            this.cleared=afterQuantity==0;
        }
    }

    private final Stack[] bank = new Stack[BANK_CAPACITY];
    private final Stack[] inventory = new Stack[INVENTORY_CAPACITY];
    private boolean open;
    private boolean placeholdersEnabled;
    private ItemContainerAction pendingX;

    BankState() {
        // Synthetic LOCAL bank state only. Inventory intentionally starts empty.
        bank[0] = new Stack(995, 100_000);   // coins
        bank[1] = new Stack(560, 2_500);     // death runes
        bank[2] = new Stack(565, 1_500);     // blood runes
        bank[3] = new Stack(15272, 20);      // food
        bank[4] = new Stack(4708, 1);        // equipment example
        bank[5] = new Stack(4151, 1);        // abyssal whip: v4 equipment swap test
        bank[6] = new Stack(EquipmentState.BLOODREND_ID, 1); // Bloodrend spare for inventory/Wield test
    }

    boolean isOpen() { return open; }
    boolean placeholdersEnabled() { return placeholdersEnabled; }
    int bankSlots() { return occupied(bank); }
    int inventorySlots() { return occupied(inventory); }
    int bankCapacity() { return BANK_CAPACITY; }
    int inventoryCapacity() { return INVENTORY_CAPACITY; }
    Stack bankAt(int slot) { return slot>=0 && slot<bank.length ? bank[slot] : null; }
    Stack inventoryAt(int slot) { return slot>=0 && slot<inventory.length ? inventory[slot] : null; }
    int inventoryCount(int itemId) {
        long total=0;
        for(Stack st:inventory) if(st!=null && st.itemId==itemId) total+=st.qty;
        return total>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)total;
    }

    void open(ServerPacketWriter w) throws IOException {
        byte[] root=
            BootstrapPackets.interfaceOverlay248(
                BANK_ROOT,
                BANK_INVENTORY_ROOT
            );
        byte[] bankPayload=
            containerPayload(
                BANK_CONTAINER,
                bank
            );
        byte[] inventoryPayload=
            containerPayload(
                BANK_INVENTORY_CONTAINER,
                inventory
            );

        w.beginBatch();
        boolean ended=false;

        try{
            w.fixed(
                248,
                root
            );
            w.varShort(
                53,
                bankPayload
            );
            w.varShort(
                53,
                inventoryPayload
            );
            w.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                try{
                    w.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{
                    w.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{
                    w.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }

        open = true;
        pendingX = null;
    }

    void close(ServerPacketWriter w) throws IOException {
        if (!open) return;

        byte[] normalInventory=
            containerPayload(
                NORMAL_INVENTORY_CONTAINER,
                inventory
            );

        w.beginBatch();
        boolean ended=false;

        try{
            w.fixed(
                219,
                new byte[0]
            );
            // The bank overlay uses widget 5064 while normal inventory uses 3214.
            // Re-send 3214 on close so withdrawn items remain visible after leaving bank.
            w.varShort(
                53,
                normalInventory
            );
            w.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                try{
                    w.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{
                    w.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{
                    w.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }

        open = false;
        pendingX = null;
    }

    boolean clientClosed() {
        boolean wasOpen = open;
        open = false;
        pendingX = null;
        return wasOpen;
    }

    void sendContainers(ServerPacketWriter w) throws IOException {
        w.varShort(53, containerPayload(BANK_CONTAINER, bank));
        w.varShort(53, containerPayload(BANK_INVENTORY_CONTAINER, inventory));
    }

    String togglePlaceholders(ServerPacketWriter w) throws IOException {
        if (!open) return "IGNORED_BANK_CLOSED";
        placeholdersEnabled = !placeholdersEnabled;
        if (!placeholdersEnabled) {
            for (int i=0;i<bank.length;i++) if (bank[i] != null && bank[i].qty == 0) bank[i]=null;
        }
        sendContainers(w);
        return "PLACEHOLDERS_"+(placeholdersEnabled?"ENABLED":"DISABLED")+" occupied="+bankSlots();
    }

    String depositInventory(ServerPacketWriter w) throws IOException {
        if (!open) return "IGNORED_BANK_CLOSED";

        Stack[] nextBank=
            copyStacks(bank);
        Stack[] nextInventory=
            copyStacks(inventory);

        int moved=0;
        boolean partial=false;

        for (int i=0;i<nextInventory.length;i++) {
            Stack s=nextInventory[i];
            if (s==null || s.qty<=0) continue;

            int dst=findItem(
                nextBank,
                s.itemId
            );
            if (dst<0)
                dst=firstEmpty(nextBank);

            if (dst<0) {
                partial=true;
                break;
            }

            if (nextBank[dst]==null)
                nextBank[dst]=
                    new Stack(
                        s.itemId,
                        0
                    );

            nextBank[dst].qty+=s.qty;
            moved+=s.qty;
            nextInventory[i]=null;
        }

        if(moved==0&&partial)
            return "PARTIAL_BANK_FULL movedQty=0";

        publishContainerPostimage(
            w,
            nextBank,
            nextInventory
        );
        replaceStacks(
            bank,
            nextBank
        );
        replaceStacks(
            inventory,
            nextInventory
        );

        if(partial)
            return "PARTIAL_BANK_FULL movedQty="+moved;

        return "DEPOSITED_ALL movedQty="+moved+
            " bankOccupied="+occupied(bank)+
            " inventoryOccupied="+occupied(inventory);
    }

    void sendNormalInventory(ServerPacketWriter w) throws IOException {
        w.varShort(53, containerPayload(NORMAL_INVENTORY_CONTAINER, inventory));
    }

    /**
     * Local dev-only visual preview. Publishes a temporary client container snapshot
     * without mutating the authoritative inventory or persistence state.
     */
    String sendDevInventorySpritePreview(int slot,int itemId,ServerPacketWriter w) throws IOException {
        if(slot<0||slot>=INVENTORY_CAPACITY) return "REJECTED_SLOT_RANGE expected=0..27";
        if(!ItemCatalog.exists(itemId)) return "REJECTED_UNKNOWN_ITEM id="+itemId;
        Stack[] preview=new Stack[INVENTORY_CAPACITY];
        for(int i=0;i<inventory.length;i++){
            Stack st=inventory[i];
            if(st!=null) preview[i]=new Stack(st.itemId,st.qty,st.tab);
        }
        preview[slot]=new Stack(itemId,1);
        w.varShort(53,containerPayload(NORMAL_INVENTORY_CONTAINER,preview));
        return "DEV_INVENTORY_SPRITE_PREVIEW slot="+slot+" item="+itemId+" name="+ItemCatalog.name(itemId)+" serverStateMutated=false persisted=false";
    }

    String sendDevInventorySpriteGallery(int startSlot,int[] itemIds,ServerPacketWriter w) throws IOException {
        if(itemIds==null||itemIds.length==0) return "REJECTED_NO_ITEMS";
        if(startSlot<0||startSlot+itemIds.length>INVENTORY_CAPACITY) return "REJECTED_SLOT_RANGE";
        Stack[] preview=new Stack[INVENTORY_CAPACITY];
        for(int i=0;i<inventory.length;i++){
            Stack st=inventory[i]; if(st!=null) preview[i]=new Stack(st.itemId,st.qty,st.tab);
        }
        StringBuilder ids=new StringBuilder();
        for(int i=0;i<itemIds.length;i++){
            int id=itemIds[i]; if(!ItemCatalog.exists(id)) return "REJECTED_UNKNOWN_ITEM id="+id;
            preview[startSlot+i]=new Stack(id,1);
            if(i>0)ids.append(','); ids.append(id).append('@').append(startSlot+i);
        }
        w.varShort(53,containerPayload(NORMAL_INVENTORY_CONTAINER,preview));
        return "DEV_INVENTORY_SPRITE_GALLERY "+ids+" serverStateMutated=false persisted=false";
    }

    String sendDevInventoryVariantPreview(java.util.Map<Integer,Integer> itemToPreviewItem,ServerPacketWriter w) throws IOException {
        if(itemToPreviewItem==null||itemToPreviewItem.isEmpty()) return "REJECTED_NO_VARIANT_SPRITE_BINDINGS";
        Stack[] preview=new Stack[INVENTORY_CAPACITY];
        int changed=0; StringBuilder rows=new StringBuilder();
        for(int i=0;i<inventory.length;i++){
            Stack st=inventory[i];
            if(st==null) continue;
            int visual=st.itemId;
            Integer mapped=itemToPreviewItem.get(st.itemId);
            if(mapped!=null){
                if(!ItemCatalog.exists(mapped)) return "REJECTED_UNKNOWN_PREVIEW_ITEM source="+st.itemId+" preview="+mapped;
                visual=mapped; changed++;
                if(rows.length()>0)rows.append(','); rows.append(st.itemId).append("->").append(mapped).append('@').append(i);
            }
            preview[i]=new Stack(visual,st.qty,st.tab);
        }
        w.varShort(53,containerPayload(NORMAL_INVENTORY_CONTAINER,preview));
        return "DEV_INVENTORY_VARIANT_SPRITES changed="+changed+" bindings=["+rows+"] serverStateMutated=false persisted=false doNotClickPreviewItems=true";
    }

    String restoreDevInventoryPreview(ServerPacketWriter w) throws IOException {
        sendNormalInventory(w);
        if(open) sendContainers(w);
        return "DEV_INVENTORY_PREVIEW_RESTORED authoritative=true";
    }

    void sendEquipment(ServerPacketWriter w, EquipmentState equipment) throws IOException {
        w.varShort(53, BootstrapPackets.equipmentContainer53(equipment.containerItems(),equipment.containerQuantities()));
    }

    /** Exact client equipment-tab bottom-center cosmetic slot (widget 27701). */
    void sendCosmetic(ServerPacketWriter w, CosmeticState cosmetic) throws IOException {
        int item=(cosmetic!=null&&cosmetic.active())?cosmetic.itemId():-1;
        int qty=item>=0?1:0;
        w.varShort(53, BootstrapPackets.itemContainer53(COSMETIC_WIDGET,new int[]{item},new int[]{qty}));
    }


    /**
     * Protocol-independent exact-slot inventory snapshot for gameplay/domain
     * services. No widget/container/presentation identity escapes this seam.
     */
    InventorySlotSnapshot inventorySlotSnapshot(int slot){
        if(!validSlot(inventory,slot))
            throw new IllegalArgumentException("inventory slot 0..27");

        Stack st=inventory[slot];
        return st==null
            ?new InventorySlotSnapshot(slot,false,-1,0)
            :new InventorySlotSnapshot(slot,true,st.itemId,st.qty);
    }

    /**
     * Commit one prevalidated complete inventory postimage without publishing.
     *
     * Two-session transactions use this only after their presentation admission
     * has committed. Unchanged item ids retain their existing Stack object/tab;
     * replaced slots receive ordinary inventory stacks.
     */
    void replaceInventorySemantic(
        int[] itemIds,
        int[] quantities
    ){
        if(itemIds==null||
           quantities==null||
           itemIds.length!=inventory.length||
           quantities.length!=inventory.length)
            throw new IllegalArgumentException(
                "inventory postimage length"
            );

        for(int slot=0;
            slot<inventory.length;
            slot++){
            int itemId=itemIds[slot];
            int quantity=quantities[slot];

            if(itemId<0){
                if(quantity!=0)
                    throw new IllegalArgumentException(
                        "empty inventory postimage quantity slot="+
                        slot
                    );
                continue;
            }

            if(quantity<=0)
                throw new IllegalArgumentException(
                    "inventory postimage quantity slot="+
                    slot+
                    " item="+itemId+
                    " qty="+quantity
                );
        }

        for(int slot=0;
            slot<inventory.length;
            slot++){
            int itemId=itemIds[slot];
            int quantity=quantities[slot];

            if(itemId<0){
                inventory[slot]=null;
                continue;
            }

            Stack existing=
                inventory[slot];

            if(existing!=null&&
               existing.itemId==itemId){
                existing.qty=quantity;
                continue;
            }

            inventory[slot]=
                new Stack(
                    itemId,
                    quantity
                );
        }
    }

    /**
     * Protocol-independent exact-slot consume primitive.
     *
     * Validates the complete mutation before changing the canonical inventory.
     * It publishes no packet and performs no compaction.
     */
    InventoryConsumeResult consumeInventoryAmountSemantic(
        int slot,
        int expectedItemId,
        int amount
    ){
        if(!validSlot(inventory,slot))
            throw new IllegalArgumentException("inventory slot 0..27");
        if(expectedItemId<0)
            throw new IllegalArgumentException("expectedItemId");
        if(amount<=0)
            throw new IllegalArgumentException("amount");

        Stack st=inventory[slot];
        if(st==null)
            throw new IllegalStateException("inventory slot empty slot="+slot);
        if(st.itemId!=expectedItemId)
            throw new IllegalStateException(
                "inventory item mismatch slot="+slot+
                " expected="+expectedItemId+
                " actual="+st.itemId
            );
        if(st.qty<amount)
            throw new IllegalStateException(
                "inventory quantity insufficient slot="+slot+
                " item="+expectedItemId+
                " have="+st.qty+
                " requested="+amount
            );

        int before=st.qty;
        int after=before-amount;

        if(after==0)
            inventory[slot]=null;
        else
            st.qty=after;

        return new InventoryConsumeResult(
            slot,
            expectedItemId,
            amount,
            before,
            after
        );
    }

    /** Remove exactly one item from a concrete inventory slot (pet Drop path). */
    String consumeInventoryOne(int slot, int itemId, ServerPacketWriter w) throws IOException {
        if (!validSlot(inventory,slot) || inventory[slot]==null) return "REJECTED_INVENTORY_SLOT";
        Stack st=inventory[slot];
        if (st.itemId!=itemId) return "REJECTED_INVENTORY_ITEM_MISMATCH expected="+st.itemId;
        if (st.qty<=0) return "REJECTED_INVENTORY_QTY";
        st.qty--;
        if (st.qty==0) inventory[slot]=null;
        sendNormalInventory(w);
        if (open) sendContainers(w);
        return "INVENTORY_CONSUME_OK item="+itemId+" slot="+slot+" remaining="+(inventory[slot]==null?0:inventory[slot].qty);
    }

    boolean canAddInventoryOne(int itemId) {
        if (isStackable(itemId) && findItem(inventory,itemId)>=0) return true;
        return firstEmpty(inventory)>=0;
    }

    /** Restore exactly one item to inventory (pet Pick-up path). Returns destination slot. */
    int addInventoryOne(int itemId, ServerPacketWriter w) throws IOException {
        int dst = isStackable(itemId) ? findItem(inventory,itemId) : -1;
        if (dst<0) dst=firstEmpty(inventory);
        if (dst<0) return -1;
        if (inventory[dst]==null) inventory[dst]=new Stack(itemId,0);
        if (inventory[dst].qty==Integer.MAX_VALUE) return -1;
        inventory[dst].qty++;
        sendNormalInventory(w);
        if (open) sendContainers(w);
        return dst;
    }

    /**
     * Restore one item to a preferred concrete slot when possible.  Pet replacement
     * uses the just-vacated slot of the newly dropped pet, matching production's
     * swap-like inventory behavior instead of compacting to the first empty slot.
     */
    int addInventoryOnePreferred(int itemId,int preferredSlot,ServerPacketWriter w) throws IOException {
        int dst=-1;
        if(validSlot(inventory,preferredSlot)){
            Stack at=inventory[preferredSlot];
            if(at==null) dst=preferredSlot;
            else if(isStackable(itemId) && at.itemId==itemId && at.qty<Integer.MAX_VALUE) dst=preferredSlot;
        }
        if(dst<0 && isStackable(itemId)) dst=findItem(inventory,itemId);
        if(dst<0) dst=firstEmpty(inventory);
        if(dst<0) return -1;
        if(inventory[dst]==null) inventory[dst]=new Stack(itemId,0);
        if(inventory[dst].itemId!=itemId || inventory[dst].qty==Integer.MAX_VALUE) return -1;
        inventory[dst].qty++;
        sendNormalInventory(w);
        if(open) sendContainers(w);
        return dst;
    }

    /** Consume the entire concrete inventory stack. Used by ordinary ground Drop. */
    int consumeInventoryAll(int slot,int itemId,ServerPacketWriter w) throws IOException {
        if(!validSlot(inventory,slot)||inventory[slot]==null)return -1;
        Stack st=inventory[slot]; if(st.itemId!=itemId||st.qty<=0)return -1;
        int qty=st.qty; inventory[slot]=null; sendNormalInventory(w); if(open)sendContainers(w); return qty;
    }

    boolean canAddInventoryAmount(int itemId,int amount){
        if(amount<=0)return false;
        if(isStackable(itemId)){
            int at=findItem(inventory,itemId);
            if(at>=0)return (long)inventory[at].qty+amount<=Integer.MAX_VALUE;
            return firstEmpty(inventory)>=0;
        }
        int free=0; for(Stack st:inventory)if(st==null)free++;
        return free>=amount;
    }

    /** Add a positive amount atomically after canAddInventoryAmount preflight. Returns first destination slot or -1. */
    int addInventoryAmount(int itemId,int amount,ServerPacketWriter w)throws IOException{
        if(!canAddInventoryAmount(itemId,amount))return -1;
        int first=-1;
        if(isStackable(itemId)){
            int dst=findItem(inventory,itemId); if(dst<0)dst=firstEmpty(inventory); first=dst;
            if(inventory[dst]==null)inventory[dst]=new Stack(itemId,0);
            inventory[dst].qty+=amount;
        }else{
            for(int n=0;n<amount;n++){int dst=firstEmpty(inventory);if(first<0)first=dst;inventory[dst]=new Stack(itemId,1);}
        }
        sendNormalInventory(w); if(open)sendContainers(w); return first;
    }

    /** Equip a native icon into the dedicated COSMETIC channel, never AMMO. */
    String equipCosmeticFromInventory(int slot,int itemId,CosmeticState cosmetic,ServerPacketWriter w)throws IOException{
        if(cosmetic==null)return "REJECTED_NO_COSMETIC_STATE";
        if(!ItemCatalog.isNativePlayerIcon(itemId))return "REJECTED_NOT_NATIVE_COSMETIC item="+itemId;
        if(!validSlot(inventory,slot)||inventory[slot]==null||inventory[slot].itemId!=itemId||inventory[slot].qty<=0)return "REJECTED_INVENTORY_MISMATCH";
        int old=cosmetic.itemId();
        // Consuming one from the selected slot always creates capacity for a previous non-stackable cosmetic.
        Stack st=inventory[slot]; st.qty--; if(st.qty==0)inventory[slot]=null;
        if(old>=0){
            int dst=(inventory[slot]==null)?slot:(isStackable(old)?findItem(inventory,old):-1);
            if(dst<0)dst=firstEmpty(inventory);
            if(dst<0){ // rollback
                if(inventory[slot]==null)inventory[slot]=new Stack(itemId,1); else inventory[slot].qty++;
                return "REJECTED_INVENTORY_FULL_ROLLBACK";
            }
            if(inventory[dst]==null)inventory[dst]=new Stack(old,0); inventory[dst].qty++;
        }
        cosmetic.set(itemId); sendNormalInventory(w); if(open)sendContainers(w);
        return "COSMETIC_EQUIP_OK item="+itemId+" old="+old+" slot="+slot+" channel=DEDICATED_BS ammoIndependent=true";
    }

    String unequipCosmeticToInventory(CosmeticState cosmetic,ServerPacketWriter w)throws IOException{
        if(cosmetic==null||!cosmetic.active())return "COSMETIC_NONE_ACTIVE";
        int old=cosmetic.itemId(); if(!canAddInventoryOne(old))return "REJECTED_INVENTORY_FULL";
        int dst=addInventoryOne(old,w); if(dst<0)return "REJECTED_INVENTORY_FULL"; cosmetic.clear();
        return "COSMETIC_UNEQUIP_OK item="+old+" inventorySlot="+dst;
    }

    /** Exact in-slot non-stackable transform used by native Switch-colors actions. */
    String transformInventoryOne(int slot,int expectedItemId,int replacementItemId,ServerPacketWriter w) throws IOException {
        if(!validSlot(inventory,slot) || inventory[slot]==null) return "REJECTED_INVENTORY_SLOT";
        Stack st=inventory[slot];
        if(st.itemId!=expectedItemId) return "REJECTED_INVENTORY_ITEM_MISMATCH expected="+st.itemId;
        if(st.qty!=1) return "REJECTED_TRANSFORM_QTY qty="+st.qty;
        if(ItemDefinitionRepository.get(replacementItemId)==null) return "REJECTED_UNKNOWN_REPLACEMENT item="+replacementItemId;
        inventory[slot]=new Stack(replacementItemId,1);
        sendNormalInventory(w);
        if(open) sendContainers(w);
        return "INVENTORY_TRANSFORM_OK slot="+slot+" item="+expectedItemId+"->"+replacementItemId;
    }


    void restoreAccountState(
        Stack[] nextBank,
        Stack[] nextInventory,
        boolean placeholders
    ){
        if(nextBank==null||
           nextBank.length!=BANK_CAPACITY)
            throw new IllegalArgumentException(
                "bank snapshot length"
            );
        if(nextInventory==null||
           nextInventory.length!=INVENTORY_CAPACITY)
            throw new IllegalArgumentException(
                "inventory snapshot length"
            );

        System.arraycopy(
            nextBank,
            0,
            bank,
            0,
            bank.length
        );
        System.arraycopy(
            nextInventory,
            0,
            inventory,
            0,
            inventory.length
        );

        placeholdersEnabled=placeholders;
        open=false;
        pendingX=null;
    }


    /**
     * Generic localhost item spawn foundation. Every id present in the embedded
     * current items catalogue can be placed in inventory without item-specific
     * server code; the client/cache owns its icon/model/name rendering.
     *
     * Stackability follows exact current custom metadata when present. Unknown
     * stackability defaults conservatively to non-stackable rather than inventing
     * an invalid giant stack.
     */
    String spawnItem(int itemId, int requested, ServerPacketWriter w) throws IOException {
        ItemCatalog.Meta meta=ItemDefinitionRepository.get(itemId);
        if (meta==null) return "REJECTED_UNKNOWN_ITEM id="+itemId;
        int amount=Math.max(1,Math.min(requested,1_000_000_000));
        boolean stackable=ItemDefinitionRepository.isStackable(itemId);
        int moved=0;
        if (stackable) {
            int dst=findItem(inventory,itemId);
            if (dst<0) dst=firstEmpty(inventory);
            if (dst<0) return "REJECTED_INVENTORY_FULL";
            if (inventory[dst]==null) inventory[dst]=new Stack(itemId,0);
            long next=(long)inventory[dst].qty+amount;
            inventory[dst].qty=(int)Math.min(Integer.MAX_VALUE,next);
            moved=amount;
        } else {
            for (int i=0;i<amount;i++) {
                int dst=firstEmpty(inventory);
                if (dst<0) break;
                inventory[dst]=new Stack(itemId,1);
                moved++;
            }
            if (moved==0) return "REJECTED_INVENTORY_FULL";
        }
        sendNormalInventory(w);
        if (open) sendContainers(w);
        return "ITEM_SPAWN_OK id="+itemId+" name="+safe(meta.name)+" requested="+amount+" moved="+moved
             +" stackable="+stackable+" stackabilityEvidence="+ItemDefinitionRepository.stackabilityEvidence(itemId)
             +" inventoryOccupied="+inventorySlots()+"/"+inventoryCapacity();
    }

    /**
     * Equip one inventory item using authoritative slot metadata. The source
     * inventory slot is preserved for the displaced item; no inventory compaction
     * occurs. A Wield/Wear action by itself is deliberately insufficient.
     */
    String equipFromInventory(int slot, int itemId, EquipmentState equipment, ServerPacketWriter w) throws IOException {
        if (!validSlot(inventory, slot) || inventory[slot] == null) return "REJECTED_INVENTORY_SLOT";
        Stack clicked = inventory[slot];
        if (clicked.itemId != itemId) return "REJECTED_INVENTORY_ITEM_MISMATCH expected="+clicked.itemId;

        EquipmentMetadataRepository.Meta meta = ItemDefinitionRepository.equipmentMetaForClientAction(itemId);
        if (meta == null) {
            return "REJECTED_EQUIPMENT_SLOT_UNRESOLVED item="+itemId
                 + " catalogEquipAction="+ItemDefinitionRepository.equipActionEvidence(itemId)
                 + " evidence="+EquipmentMetadataRepository.resolutionEvidence(itemId);
        }

        final boolean stackEquip = meta.slot==EquipmentSlot.AMMO && isStackable(itemId);
        if (!stackEquip && clicked.qty != 1) return "REJECTED_EQUIP_STACK_QTY qty="+clicked.qty;

        // Stackable ammunition moves as one authoritative stack and merges with
        // identical ammo already worn. This keeps inventory/bank/equipment packet-53
        // quantities coherent instead of turning each arrow/bolt into a new slot.
        if(stackEquip){
            int amount=clicked.qty;
            int previous=equipment.itemAt(meta.slot);
            int previousQty=equipment.quantityAt(meta.slot);
            if(previous==itemId){
                long merged=(long)previousQty+amount;
                if(merged>Integer.MAX_VALUE) return "REJECTED_EQUIPMENT_QTY_OVERFLOW";
                equipment.setStack(meta.slot,itemId,(int)merged);
                inventory[slot]=null;
                sendNormalInventory(w); sendEquipment(w,equipment); if(open)sendContainers(w);
                return "EQUIP_STACK_MERGE_OK item="+itemId+" slot="+meta.slot+" moved="+amount+" equippedQty="+merged+" inventorySlot="+slot+" evidence="+meta.evidence;
            }
            // The clicked slot becomes free, so a displaced ammo stack always has
            // a deterministic same-slot destination.
            equipment.setStack(meta.slot,itemId,amount);
            inventory[slot]=null;
            if(previous>=0) inventory[slot]=new Stack(previous,Math.max(1,previousQty));
            sendNormalInventory(w); sendEquipment(w,equipment); if(open)sendContainers(w);
            return "EQUIP_STACK_OK item="+itemId+" slot="+meta.slot+" moved="+amount+" equippedQty="+amount+" displaced="+(previous<0?"[]":"["+previous+" x"+previousQty+"]")+" inventorySlot="+slot+" evidence="+meta.evidence;
        }

        ArrayList<Integer> displaced = new ArrayList<>();
        int previous = equipment.itemAt(meta.slot);
        if (previous >= 0) displaced.add(previous);

        boolean clearShield = false;
        boolean clearWeapon = false;
        if (meta.slot == EquipmentSlot.WEAPON && meta.twoHanded) {
            int shield = equipment.itemAt(EquipmentSlot.SHIELD);
            if (shield >= 0) { displaced.add(shield); clearShield = true; }
        } else if (meta.slot == EquipmentSlot.SHIELD) {
            int weapon = equipment.itemAt(EquipmentSlot.WEAPON);
            EquipmentMetadataRepository.Meta weaponMeta = EquipmentMetadataRepository.resolveKnownSlot(weapon, EquipmentSlot.WEAPON);
            if (weapon >= 0 && weaponMeta != null && weaponMeta.twoHanded) {
                displaced.add(weapon); clearWeapon = true;
            }
        }

        int emptyElsewhere = 0;
        for (int i=0;i<inventory.length;i++) if (i != slot && inventory[i] == null) emptyElsewhere++;
        if (displaced.size() > 1 + emptyElsewhere)
            return "REJECTED_INVENTORY_FULL_FOR_DISPLACED count="+displaced.size();

        equipment.set(meta.slot, itemId);
        if (clearShield) equipment.unequip(EquipmentSlot.SHIELD);
        if (clearWeapon) equipment.unequip(EquipmentSlot.WEAPON);

        inventory[slot] = null;
        for (int i=0;i<displaced.size();i++) {
            int dst = i == 0 ? slot : firstEmpty(inventory);
            if (dst < 0) throw new IllegalStateException("preflight inventory-space mismatch");
            inventory[dst] = new Stack(displaced.get(i),1);
        }

        sendNormalInventory(w);
        sendEquipment(w,equipment);
        if (open) sendContainers(w);
        return "EQUIP_OK item="+itemId+" slot="+meta.slot+" equipmentIndex="+meta.slot.equipmentIndex
             + " appearanceSlot="+meta.appearanceSlot+" twoHanded="+meta.twoHanded
             + " pose="+meta.pose.name+" poseSpecificMask=0x"+Integer.toHexString(meta.pose.weaponSpecificMask)
             + " displaced="+displaced+" inventorySlot="+slot+" evidence="+meta.evidence;
    }

    String wieldFromInventory(int slot, int itemId, EquipmentState equipment, ServerPacketWriter w) throws IOException {
        return equipFromInventory(slot, itemId, equipment, w);
    }

    String unequipToInventory(int equipmentIndex, int itemId, EquipmentState equipment, ServerPacketWriter w) throws IOException {
        EquipmentSlot slot = EquipmentSlot.fromEquipmentIndex(equipmentIndex);
        if (slot == null) return "REJECTED_EQUIPMENT_SLOT index="+equipmentIndex;
        int current = equipment.itemAt(slot);
        if (current < 0) return "REJECTED_EQUIPMENT_EMPTY slot="+slot;
        if (current != itemId) return "REJECTED_EQUIPMENT_ITEM_MISMATCH expected="+current;
        int amount=Math.max(1,equipment.quantityAt(slot));
        int dst=(isStackable(itemId)?findItem(inventory,itemId):-1);
        if(dst<0) dst=firstEmpty(inventory);
        if (dst < 0) return "REJECTED_INVENTORY_FULL";
        if(inventory[dst]==null) inventory[dst]=new Stack(itemId,0);
        long merged=(long)inventory[dst].qty+amount;
        if(merged>Integer.MAX_VALUE)return "REJECTED_INVENTORY_QTY_OVERFLOW";
        equipment.unequip(slot);
        inventory[dst].qty=(int)merged;
        sendNormalInventory(w);
        sendEquipment(w,equipment);
        if (open) sendContainers(w);
        return "UNEQUIP_OK item="+itemId+" slot="+slot+" equipmentIndex="+equipmentIndex+" amount="+amount+" inventorySlot="+dst;
    }

    /** Dyed Doppel -> regular Doppel in-place plus a returned dye in another slot. */
    String splitInventoryOne(int slot,int expectedItemId,int primaryReplacement,int extraItem,ServerPacketWriter w) throws IOException {
        if(!validSlot(inventory,slot)||inventory[slot]==null)return "REJECTED_INVENTORY_SLOT";
        Stack st=inventory[slot];
        if(st.itemId!=expectedItemId)return "REJECTED_INVENTORY_ITEM_MISMATCH expected="+st.itemId;
        if(st.qty!=1)return "REJECTED_TRANSFORM_QTY qty="+st.qty;
        if(ItemDefinitionRepository.get(primaryReplacement)==null||ItemDefinitionRepository.get(extraItem)==null)return "REJECTED_UNKNOWN_REPLACEMENT";
        int extraDst=isStackable(extraItem)?findItem(inventory,extraItem):-1;
        if(extraDst<0) extraDst=firstEmptyExcept(inventory,slot);
        if(extraDst<0)return "REJECTED_INVENTORY_FULL_FOR_SPLIT";
        inventory[slot]=new Stack(primaryReplacement,1);
        if(inventory[extraDst]==null)inventory[extraDst]=new Stack(extraItem,0);
        if(inventory[extraDst].itemId!=extraItem||inventory[extraDst].qty==Integer.MAX_VALUE)throw new IllegalStateException("split preflight mismatch");
        inventory[extraDst].qty++;
        sendNormalInventory(w); if(open)sendContainers(w);
        return "INVENTORY_SPLIT_OK slot="+slot+" item="+expectedItemId+"->"+primaryReplacement+" extra="+extraItem+" extraSlot="+extraDst;
    }

    /** Consume one dye and one regular pet and place the dyed pet in the regular-pet slot. */
    String combineInventoryOne(int slotA,int itemA,int slotB,int itemB,int resultItem,ServerPacketWriter w)throws IOException{
        if(slotA==slotB)return "REJECTED_COMBINE_SAME_SLOT";
        if(!validSlot(inventory,slotA)||!validSlot(inventory,slotB)||inventory[slotA]==null||inventory[slotB]==null)return "REJECTED_INVENTORY_SLOT";
        if(inventory[slotA].itemId!=itemA||inventory[slotB].itemId!=itemB)return "REJECTED_COMBINE_ITEM_MISMATCH";
        if(ItemDefinitionRepository.get(resultItem)==null)return "REJECTED_UNKNOWN_RESULT";
        int regularSlot=itemA==3241?slotA:itemB==3241?slotB:slotB;
        int other=regularSlot==slotA?slotB:slotA;
        Stack rs=inventory[regularSlot], os=inventory[other];
        if(rs.qty<=0||os.qty<=0)return "REJECTED_COMBINE_QTY";
        rs.qty--; os.qty--;
        if(rs.qty==0)inventory[regularSlot]=null;
        if(os.qty==0)inventory[other]=null;
        // Exact mechanic here uses one regular pet + one dye -> one dyed pet.
        if(inventory[regularSlot]!=null)return "REJECTED_COMBINE_REGULAR_STACK_REMAINS";
        inventory[regularSlot]=new Stack(resultItem,1);
        sendNormalInventory(w); if(open)sendContainers(w);
        return "INVENTORY_COMBINE_OK regularSlot="+regularSlot+" consumed="+itemA+"+"+itemB+" result="+resultItem;
    }

    String apply(ItemContainerAction a, ServerPacketWriter w) throws IOException {
        if (!open) return "IGNORED_BANK_CLOSED";
        if (a.widgetId == BANK_CONTAINER) return withdraw(a, w);
        if (a.widgetId == BANK_INVENTORY_CONTAINER) return deposit(a, w);
        return "OBSERVED_NON_BANK_WIDGET widget="+a.widgetId;
    }

    String applyAmount(int amount, ServerPacketWriter w) throws IOException {
        if (!open) {
            pendingX=null;
            return "IGNORED_BANK_CLOSED";
        }

        ItemContainerAction a=pendingX;
        if (a==null)
            return "IGNORED_NO_PENDING_X amount="+amount;

        if (amount<=0) {
            pendingX=null;
            return "X_NO_ITEMS_MOVED amount="+amount;
        }

        String result;

        if (a.widgetId==BANK_CONTAINER)
            result=
                withdrawAmount(
                    a,
                    amount,
                    w,
                    "WITHDRAW_X_OK"
                );
        else if (a.widgetId==
                    BANK_INVENTORY_CONTAINER)
            result=
                depositAmount(
                    a,
                    amount,
                    w,
                    "STORE_X_OK"
                );
        else {
            pendingX=null;
            return "X_PENDING_WIDGET_UNSUPPORTED widget="+
                a.widgetId;
        }

        pendingX=null;
        return result;
    }

    String applyDrag(ContainerDrag d, ServerPacketWriter w) throws IOException {
        // Opcode 214 is shared by the ordinary inventory (3214) and bank containers.
        // The old handler incorrectly required the bank to be open before even looking
        // at the widget id. That made a normal inventory drag look correct client-side
        // while leaving the authoritative server slot unchanged; a later opcode-41
        // Equip from the moved slot then failed as REJECTED_INVENTORY_SLOT.
        final boolean normalInventory = d.widgetId==NORMAL_INVENTORY_CONTAINER;
        Stack[] xs;
        if (d.widgetId==BANK_CONTAINER) {
            if (!open) return "IGNORED_BANK_CLOSED";
            xs=bank;
        } else if (d.widgetId==BANK_INVENTORY_CONTAINER) {
            if (!open) return "IGNORED_BANK_CLOSED";
            xs=inventory;
        } else if (normalInventory) {
            xs=inventory;
        } else {
            return "OBSERVED_NON_BANK_DRAG widget="+d.widgetId;
        }
        if (!validSlot(xs,d.sourceSlot) || !validSlot(xs,d.destinationSlot)) return "REJECTED_DRAG_SLOT";
        if (d.sourceSlot==d.destinationSlot) return normalInventory ? "INVENTORY_DRAG_NOOP" : "DRAG_NOOP";
        if (d.mode==1) insertMove(xs,d.sourceSlot,d.destinationSlot);
        else swap(xs,d.sourceSlot,d.destinationSlot);

        if (normalInventory) {
            sendNormalInventory(w);
            // If a bank overlay is open, keep its mirror of the same inventory state in sync.
            if (open) sendContainers(w);
            return "INVENTORY_DRAG_OK mode="+d.mode+" source="+d.sourceSlot+" destination="+d.destinationSlot;
        }
        sendContainers(w);
        return "DRAG_OK mode="+d.mode+" source="+d.sourceSlot+" destination="+d.destinationSlot;
    }

    /** Handle exact SpawnPK bank-tab command strings emitted through opcode 103. */
    String applyCommand(String command, ServerPacketWriter w) throws IOException {
        if (command==null) return "IGNORED_NULL_COMMAND";
        String s=command.trim();
        if (s.startsWith("::")) s=s.substring(2);
        String[] p=s.split("\\s+");
        if (p.length<1) return "IGNORED_EMPTY_COMMAND";
        if (p[0].equalsIgnoreCase("banksearch")) {
            if (!open) return "IGNORED_BANK_CLOSED";
            String query=s.length()>"banksearch".length()?s.substring("banksearch".length()).trim():"";
            if (query.equalsIgnoreCase("~clear") || query.isEmpty())
                return "BANKSEARCH_REQUEST_ACCEPTED query=CLEAR transport=C2S103 filtering=SERVER_AUTHORITY_DEFERRED";
            return "BANKSEARCH_REQUEST_ACCEPTED query="+safe(query)+" transport=C2S103 liveOnEdit=true filtering=SERVER_AUTHORITY_DEFERRED";
        }
        if (p[0].equalsIgnoreCase("setbanktab")) {
            if (!open) return "IGNORED_BANK_CLOSED";
            if (p.length<3) return "REJECTED_SETBANKTAB_ARGS";
            int slot=parseInt(p[1],-1), tab=parseInt(p[2],-1);
            if (!validSlot(bank,slot) || bank[slot]==null || tab<0 || tab>8) return "REJECTED_SETBANKTAB_RANGE";
            bank[slot].tab=tab;
            // Server-side tab ownership is now authoritative. Current full-bank view keeps
            // physical packet-53 slot order unchanged until exact tab-selection control is certified.
            sendContainers(w);
            return "SETBANKTAB_OK slot="+slot+" tab="+tab+" presentation=FULL_BANK_VIEW";
        }
        if (p[0].equalsIgnoreCase("swapbanktab")) {
            if (!open) return "IGNORED_BANK_CLOSED";
            if (p.length<3) return "REJECTED_SWAPBANKTAB_ARGS";
            int target=parseInt(p[2],-1);
            int sourceTab=p.length>=4?parseInt(p[3],-1):-1;
            if (target<0 || target>8 || sourceTab<0 || sourceTab>8) return "SWAPBANKTAB_OBSERVED_UNCERTIFIED target="+target+" sourceTab="+sourceTab;
            for (Stack st:bank) if(st!=null){ if(st.tab==sourceTab)st.tab=-1; else if(st.tab==target)st.tab=sourceTab; }
            for (Stack st:bank) if(st!=null && st.tab==-1)st.tab=target;
            sendContainers(w);
            return "SWAPBANKTAB_OK sourceTab="+sourceTab+" targetTab="+target+" presentation=FULL_BANK_VIEW";
        }
        return "IGNORED_NON_BANK_COMMAND";
    }

    private String withdraw(ItemContainerAction a, ServerPacketWriter w) throws IOException {
        if (!validSlot(bank,a.slot) || bank[a.slot]==null) return "REJECTED_BANK_SLOT";
        Stack s=bank[a.slot];
        if (s.itemId != a.itemId) return "REJECTED_BANK_ITEM_MISMATCH expected="+s.itemId;
        switch (a.opcode) {
            case 145: return withdrawAmount(a,1,w,"WITHDRAW_OK");
            case 117: return withdrawAmount(a,5,w,"WITHDRAW_OK");
            case 43:  return withdrawAmount(a,10,w,"WITHDRAW_OK");
            case 129: return withdrawAmount(a,s.qty,w,"WITHDRAW_OK");
            case 140:
                // Exact client reuses opcode140 for two visible actions. For coins (995)
                // it means Bag-exchange; the resulting server mutation is not recoverable
                // from client bytecode, so fail closed instead of incorrectly withdrawing all-but-one.
                if (s.itemId==995) return "BAG_EXCHANGE_SERVER_AUTHORITY_DEFERRED item=995 mutation=NONE";
                return withdrawAmount(a,Math.max(0,s.qty-1),w,"WITHDRAW_ALL_BUT_ONE_OK");
            case 141:
                // R18 exact-current authority: the final i32 is Client.ih, a live configurable
                // withdraw amount. It is not a hardcoded 14.
                if (a.extra<=0) return "REJECTED_CONFIGURED_WITHDRAW_AMOUNT amount="+a.extra;
                return withdrawAmount(a,a.extra,w,"WITHDRAW_CONFIGURED_OK");
            case 135:
                w.fixed(27,new byte[0]);
                pendingX=a;
                return "WITHDRAW_X_PROMPT_SENT opcode=27 pendingSlot="+a.slot+" itemId="+a.itemId;
            default: return "UNSUPPORTED_BANK_WITHDRAW_OPCODE";
        }
    }

    private String deposit(ItemContainerAction a, ServerPacketWriter w) throws IOException {
        if (!validSlot(inventory,a.slot) || inventory[a.slot]==null) return "REJECTED_INVENTORY_SLOT";
        Stack s=inventory[a.slot];
        if (s.itemId != a.itemId) return "REJECTED_INVENTORY_ITEM_MISMATCH expected="+s.itemId;
        switch (a.opcode) {
            case 145: return depositAmount(a,1,w,"STORE_OK");
            case 117: return depositAmount(a,5,w,"STORE_OK");
            case 43:  return depositAmount(a,10,w,"STORE_OK");
            case 129: return depositAmount(a,s.qty,w,"STORE_OK");
            case 135:
                w.fixed(27,new byte[0]);
                pendingX=a;
                return "STORE_X_PROMPT_SENT opcode=27 pendingSlot="+a.slot+" itemId="+a.itemId;
            default: return "UNSUPPORTED_BANK_STORE_OPCODE";
        }
    }

    private String withdrawAmount(ItemContainerAction a,int requested,ServerPacketWriter w,String label) throws IOException {
        if (!validSlot(bank,a.slot) || bank[a.slot]==null) return "REJECTED_BANK_SLOT";

        Stack source=bank[a.slot];
        if (source.itemId!=a.itemId)
            return "REJECTED_BANK_ITEM_MISMATCH expected="+source.itemId;

        int requestedClamped=
            Math.min(
                Math.max(0,requested),
                source.qty
            );
        if(requestedClamped<=0)
            return "NO_ITEMS_MOVED";

        Stack[] nextBank=
            copyStacks(bank);
        Stack[] nextInventory=
            copyStacks(inventory);
        Stack nextSource=
            nextBank[a.slot];
        int itemId=nextSource.itemId;
        int sourceTab=nextSource.tab;
        int moved=0;

        if (isStackable(itemId)) {
            int dst=findItem(
                nextInventory,
                itemId
            );
            if(dst<0)
                dst=firstEmpty(nextInventory);
            if(dst<0)
                return "REJECTED_INVENTORY_FULL";

            if(nextInventory[dst]==null)
                nextInventory[dst]=
                    new Stack(
                        itemId,
                        0,
                        sourceTab
                    );

            nextInventory[dst].qty+=
                requestedClamped;
            moved=requestedClamped;
        } else {
            for (int n=0;n<requestedClamped;n++) {
                int dst=firstEmpty(
                    nextInventory
                );
                if(dst<0)
                    break;

                nextInventory[dst]=
                    new Stack(
                        itemId,
                        1,
                        sourceTab
                    );
                moved++;
            }

            if(moved==0)
                return "REJECTED_INVENTORY_FULL";
        }

        nextSource.qty-=moved;
        if(nextSource.qty<=0){
            nextSource.qty=0;
            if(!placeholdersEnabled)
                nextBank[a.slot]=null;
        }

        publishContainerPostimage(
            w,
            nextBank,
            nextInventory
        );
        replaceStacks(
            bank,
            nextBank
        );
        replaceStacks(
            inventory,
            nextInventory
        );

        return label+" amount="+moved+
            " requested="+requestedClamped+
            " stackable="+isStackable(itemId)+
            " bankOccupied="+occupied(bank)+
            " inventoryOccupied="+occupied(inventory);
    }

    private String depositAmount(ItemContainerAction a,int requested,ServerPacketWriter w,String label) throws IOException {
        if (!validSlot(inventory,a.slot) || inventory[a.slot]==null) return "REJECTED_INVENTORY_SLOT";

        Stack source=inventory[a.slot];
        if(source.itemId!=a.itemId)
            return "REJECTED_INVENTORY_ITEM_MISMATCH expected="+source.itemId;

        int amount=
            Math.min(
                Math.max(0,requested),
                source.qty
            );
        if(amount<=0)
            return "NO_ITEMS_MOVED";

        Stack[] nextBank=
            copyStacks(bank);
        Stack[] nextInventory=
            copyStacks(inventory);
        Stack nextSource=
            nextInventory[a.slot];
        int itemId=nextSource.itemId;
        int sourceTab=nextSource.tab;

        int dst=findItem(
            nextBank,
            itemId
        );
        if(dst<0)
            dst=firstEmpty(nextBank);
        if(dst<0)
            return "REJECTED_BANK_FULL";

        if(nextBank[dst]==null)
            nextBank[dst]=
                new Stack(
                    itemId,
                    0,
                    sourceTab
                );

        nextBank[dst].qty+=amount;
        nextSource.qty-=amount;

        if(nextSource.qty<=0)
            nextInventory[a.slot]=null;

        publishContainerPostimage(
            w,
            nextBank,
            nextInventory
        );
        replaceStacks(
            bank,
            nextBank
        );
        replaceStacks(
            inventory,
            nextInventory
        );

        return label+" amount="+amount+
            " stackable="+isStackable(itemId)+
            " inventorySlotPreserved=true bankOccupied="+
            occupied(bank)+
            " inventoryOccupied="+
            occupied(inventory);
    }

    private static Stack[] copyStacks(
        Stack[] source
    ){
        Stack[] copy=
            new Stack[source.length];

        for(int i=0;i<source.length;i++){
            Stack stack=source[i];
            if(stack!=null)
                copy[i]=
                    new Stack(
                        stack.itemId,
                        stack.qty,
                        stack.tab
                    );
        }

        return copy;
    }

    private static void replaceStacks(
        Stack[] target,
        Stack[] source
    ){
        if(target.length!=source.length)
            throw new IllegalArgumentException(
                "stack postimage length"
            );

        for(int i=0;i<target.length;i++){
            Stack stack=source[i];
            target[i]=
                stack==null
                    ?null
                    :new Stack(
                        stack.itemId,
                        stack.qty,
                        stack.tab
                    );
        }
    }

    private static void publishContainerPostimage(
        ServerPacketWriter writer,
        Stack[] bankPostimage,
        Stack[] inventoryPostimage
    )throws IOException{
        byte[] bankPayload=
            containerPayload(
                BANK_CONTAINER,
                bankPostimage
            );
        byte[] inventoryPayload=
            containerPayload(
                BANK_INVENTORY_CONTAINER,
                inventoryPostimage
            );

        writer.beginBatch();
        boolean ended=false;

        try{
            writer.varShort(
                53,
                bankPayload
            );
            writer.varShort(
                53,
                inventoryPayload
            );
            writer.endBatch();
            ended=true;
        }finally{
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
        }
    }

    /** Current item-catalog metadata is authoritative when explicit; unknowns default non-stackable. */
    static boolean isStackable(int itemId) {
        return ItemDefinitionRepository.isStackable(itemId);
    }

    private static String safe(String s) {
        if (s==null) return "";
        return s.replace(' ','_').replace('\t','_').replace('\n','_').replace('\r','_');
    }

    private static int occupied(Stack[] xs){int n=0;for(Stack s:xs)if(s!=null)n++;return n;}
    private static boolean validSlot(Stack[] xs,int i){return i>=0&&i<xs.length;}
    private static int findItem(Stack[] xs,int id){for(int i=0;i<xs.length;i++)if(xs[i]!=null&&xs[i].itemId==id)return i;return -1;}
    private static int firstEmpty(Stack[] xs){for(int i=0;i<xs.length;i++)if(xs[i]==null)return i;return -1;}
    private static int firstEmptyExcept(Stack[] xs,int except){for(int i=0;i<xs.length;i++)if(i!=except&&xs[i]==null)return i;return -1;}
    private static void swap(Stack[] xs,int a,int b){Stack t=xs[a];xs[a]=xs[b];xs[b]=t;}
    private static void insertMove(Stack[] xs,int from,int to){
        Stack moving=xs[from];
        if(from<to)System.arraycopy(xs,from+1,xs,from,to-from);
        else System.arraycopy(xs,to,xs,to+1,from-to);
        xs[to]=moving;
    }
    private static int parseInt(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}

    private static byte[] containerPayload(int widgetId, Stack[] xs) throws IOException {
        int[] ids=new int[xs.length], qty=new int[xs.length];
        Arrays.fill(ids,-1);
        for(int i=0;i<xs.length;i++)if(xs[i]!=null){ids[i]=xs[i].itemId;qty[i]=xs[i].qty;}
        return BootstrapPackets.itemContainer53(widgetId,ids,qty);
    }
}

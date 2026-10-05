package spk.local;

import java.util.*;

/**
 * Converts one immutable semantic PlayerLoadout into the exact canonical
 * inventory/equipment postimage used by the live player.
 *
 * This is LocalLab playability policy, not original SpawnPK loadout policy.
 */
final class PlayabilityLoadoutMaterializer {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_PLAYABILITY";

    static final class Postimage {
        final int[] inventoryItemIds;
        final int[] inventoryQuantities;
        final int[] equipmentItemIds;
        final int[] equipmentQuantities;

        Postimage(
            int[] inventoryItemIds,
            int[] inventoryQuantities,
            int[] equipmentItemIds,
            int[] equipmentQuantities
        ){
            this.inventoryItemIds=
                inventoryItemIds.clone();
            this.inventoryQuantities=
                inventoryQuantities.clone();
            this.equipmentItemIds=
                equipmentItemIds.clone();
            this.equipmentQuantities=
                equipmentQuantities.clone();
        }

        int inventorySlots(){
            int count=0;
            for(int itemId:inventoryItemIds)
                if(itemId>=0)
                    count++;
            return count;
        }

        int equipmentSlots(){
            int count=0;
            for(int itemId:equipmentItemIds)
                if(itemId>=0)
                    count++;
            return count;
        }
    }

    static LoadoutService.ValidationResult validate(
        PlayerLoadout loadout
    ){
        try{
            materialize(loadout);
            return LoadoutService
                .ValidationResult
                .valid();
        }catch(RuntimeException invalid){
            return LoadoutService
                .ValidationResult
                .invalid(
                    AUTHORITY+
                    " "+
                    sanitize(
                        invalid.getMessage()
                    )
                );
        }
    }

    static Postimage materialize(
        PlayerLoadout loadout
    ){
        PlayerLoadout checked=
            Objects.requireNonNull(
                loadout,
                "loadout"
            );

        if(checked.hasSkillProfile())
            throw new IllegalArgumentException(
                "skill profile apply adapter unavailable"
            );

        if(checked.hasPetSelection())
            throw new IllegalArgumentException(
                "pet selection apply adapter unavailable"
            );

        int[] inventoryItems=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] inventoryQuantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] equipmentItems=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] equipmentQuantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        Arrays.fill(
            inventoryItems,
            -1
        );
        Arrays.fill(
            equipmentItems,
            -1
        );

        int inventorySlot=0;

        for(PlayerLoadout.InventoryEntry entry:
                checked.inventory){
            int itemId=
                itemId(
                    entry.itemKey
                );

            requireCatalogItem(
                itemId
            );

            if(ItemCatalog.isStackable(itemId)){
                if(inventorySlot>=
                        BankState.INVENTORY_CAPACITY)
                    throw new IllegalArgumentException(
                        "inventory exceeds 28 materialized slots"
                    );

                inventoryItems[inventorySlot]=
                    itemId;
                inventoryQuantities[inventorySlot]=
                    positiveInt(
                        entry.quantity,
                        "inventory quantity item="+
                        itemId
                    );
                inventorySlot++;
                continue;
            }

            if(entry.quantity>
                    BankState.INVENTORY_CAPACITY-
                    inventorySlot)
                throw new IllegalArgumentException(
                    "non-stackable inventory exceeds 28 materialized slots item="+
                    itemId+
                    " qty="+
                    entry.quantity
                );

            for(long copy=0L;
                copy<entry.quantity;
                copy++){
                inventoryItems[inventorySlot]=
                    itemId;
                inventoryQuantities[inventorySlot]=
                    1;
                inventorySlot++;
            }
        }

        EquipmentMetadataRepository.Meta
            weaponMeta=null;
        boolean shieldPresent=false;

        for(PlayerLoadout.EquipmentEntry entry:
                checked.equipment){
            EquipmentSlot slot=
                equipmentSlot(
                    entry.slotKey
                );
            int itemId=
                itemId(
                    entry.itemKey
                );

            requireCatalogItem(
                itemId
            );

            EquipmentMetadataRepository.Meta meta=
                EquipmentMetadataRepository
                    .resolve(itemId);

            if(meta==null)
                throw new IllegalArgumentException(
                    "equipment metadata unresolved item="+
                    itemId+
                    " slot="+
                    slot
                );

            if(meta.slot!=slot)
                throw new IllegalArgumentException(
                    "equipment slot mismatch item="+
                    itemId+
                    " requested="+
                    slot+
                    " resolved="+
                    meta.slot
                );

            int quantity=
                positiveInt(
                    entry.quantity,
                    "equipment quantity item="+
                    itemId
                );

            if(slot!=EquipmentSlot.AMMO&&
               quantity!=1)
                throw new IllegalArgumentException(
                    "non-ammo equipment quantity item="+
                    itemId+
                    " slot="+
                    slot+
                    " qty="+
                    quantity
                );

            if(slot==EquipmentSlot.AMMO&&
               quantity>1&&
               !ItemCatalog.isStackable(itemId))
                throw new IllegalArgumentException(
                    "non-stackable ammo quantity item="+
                    itemId+
                    " qty="+
                    quantity
                );

            equipmentItems[
                slot.equipmentIndex
            ]=itemId;
            equipmentQuantities[
                slot.equipmentIndex
            ]=quantity;

            if(slot==EquipmentSlot.WEAPON)
                weaponMeta=meta;
            else if(slot==EquipmentSlot.SHIELD)
                shieldPresent=true;
        }

        if(weaponMeta!=null&&
           weaponMeta.twoHanded&&
           shieldPresent)
            throw new IllegalArgumentException(
                "two-handed weapon conflicts with shield item="+
                weaponMeta.itemId
            );

        return new Postimage(
            inventoryItems,
            inventoryQuantities,
            equipmentItems,
            equipmentQuantities
        );
    }

    static String itemKey(int itemId){
        if(itemId<0)
            throw new IllegalArgumentException(
                "itemId="+itemId
            );
        return "item:"+itemId;
    }

    static String slotKey(
        EquipmentSlot slot
    ){
        return "slot:"+
            Objects.requireNonNull(
                slot,
                "slot"
            ).name()
             .toLowerCase(
                 Locale.ROOT
             );
    }

    private static int itemId(
        String itemKey
    ){
        String normalized=
            PlayerLoadout.normalizeKey(
                itemKey,
                "itemKey"
            );

        if(!normalized.startsWith(
                "item:"))
            throw new IllegalArgumentException(
                "unsupported item key="+
                normalized
            );

        String numeric=
            normalized.substring(
                "item:".length()
            );

        if(numeric.isEmpty())
            throw new IllegalArgumentException(
                "empty numeric item key"
            );

        try{
            int itemId=
                Integer.parseInt(
                    numeric
                );

            if(itemId<0)
                throw new NumberFormatException(
                    "negative"
                );

            return itemId;
        }catch(NumberFormatException invalid){
            throw new IllegalArgumentException(
                "invalid numeric item key="+
                normalized,
                invalid
            );
        }
    }

    private static EquipmentSlot equipmentSlot(
        String slotKey
    ){
        String normalized=
            PlayerLoadout.normalizeKey(
                slotKey,
                "slotKey"
            );

        if(!normalized.startsWith(
                "slot:"))
            throw new IllegalArgumentException(
                "unsupported equipment slot key="+
                normalized
            );

        String name=
            normalized.substring(
                "slot:".length()
            );

        try{
            return EquipmentSlot.valueOf(
                name.toUpperCase(
                    Locale.ROOT
                )
            );
        }catch(IllegalArgumentException invalid){
            throw new IllegalArgumentException(
                "unknown equipment slot key="+
                normalized,
                invalid
            );
        }
    }

    private static void requireCatalogItem(
        int itemId
    ){
        if(!ItemCatalog.exists(itemId))
            throw new IllegalArgumentException(
                "unknown current item="+
                itemId
            );
    }

    private static int positiveInt(
        long value,
        String label
    ){
        if(value<=0L||
           value>Integer.MAX_VALUE)
            throw new IllegalArgumentException(
                label+
                " value="+
                value
            );
        return (int)value;
    }

    private static String sanitize(
        String message
    ){
        if(message==null||
           message.trim().isEmpty())
            return "invalid loadout";

        return message
            .trim()
            .replace(
                '\n',
                ' '
            )
            .replace(
                '\r',
                ' '
            );
    }

    private PlayabilityLoadoutMaterializer(){}
}

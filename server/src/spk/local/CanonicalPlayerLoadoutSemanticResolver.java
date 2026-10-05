package spk.local;

import java.util.Locale;

/**
 * Minimal canonical LocalLab loadout resolver.
 *
 * Item keys use server/cache item identity: item:id:<decimal>.
 * Equipment slot keys use semantic names: slot:weapon, slot:head, ...
 */
final class CanonicalPlayerLoadoutSemanticResolver
    implements PlayerLoadoutSemanticResolver {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_CANONICAL_LOADOUT_KEYS_V1";

    @Override public Item resolveItem(
        String itemKey
    ){
        String key=
            PlayerLoadout.normalizeKey(
                itemKey,
                "itemKey"
            );
        String prefix="item:id:";

        if(!key.startsWith(prefix))
            throw new IllegalArgumentException(
                "unsupported loadout item key="+
                itemKey+
                " expected="+
                prefix+"<decimal>"
            );

        String decimal=
            key.substring(
                prefix.length()
            );

        if(decimal.isEmpty())
            throw new IllegalArgumentException(
                "missing loadout item id key="+
                itemKey
            );

        final int itemId;

        try{
            itemId=
                Integer.parseInt(
                    decimal
                );
        }catch(NumberFormatException failure){
            throw new IllegalArgumentException(
                "invalid loadout item id key="+
                itemKey,
                failure
            );
        }

        if(itemId<0||
           !ItemDefinitionRepository.exists(
                itemId))
            throw new IllegalArgumentException(
                "unknown loadout item id="+
                itemId
            );

        return new Item(
            itemId,
            ItemDefinitionRepository
                .isStackable(
                    itemId
                )
        );
    }

    @Override public EquipmentSlot
        resolveEquipmentSlot(
            String slotKey
        )
    {
        String key=
            PlayerLoadout.normalizeKey(
                slotKey,
                "slotKey"
            );
        String prefix="slot:";

        if(!key.startsWith(prefix))
            throw new IllegalArgumentException(
                "unsupported loadout equipment slot key="+
                slotKey
            );

        String semantic=
            key.substring(
                prefix.length()
            );

        try{
            return EquipmentSlot.valueOf(
                semantic.toUpperCase(
                    Locale.ROOT
                )
            );
        }catch(IllegalArgumentException failure){
            throw new IllegalArgumentException(
                "unknown loadout equipment slot="+
                slotKey,
                failure
            );
        }
    }

    @Override public String authority(){
        return AUTHORITY;
    }
}

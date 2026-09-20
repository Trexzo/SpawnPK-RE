package spk.local;

import java.util.*;

/**
 * Immutable semantic desired-state loadout definition.
 *
 * This object does not mutate player inventory, equipment, skills or pets.
 * Semantic keys are caller-owned domain identity, never client slot/widget ids.
 */
final class PlayerLoadout {
    static final class InventoryEntry {
        final String itemKey;
        final long quantity;

        InventoryEntry(String itemKey,long quantity){
            this.itemKey=normalizeKey(itemKey,"itemKey");
            if(quantity<=0)
                throw new IllegalArgumentException(
                    "inventory quantity="+quantity
                );
            this.quantity=quantity;
        }
    }

    static final class EquipmentEntry {
        final String slotKey;
        final String itemKey;
        final long quantity;

        EquipmentEntry(
            String slotKey,
            String itemKey,
            long quantity
        ){
            this.slotKey=normalizeKey(slotKey,"slotKey");
            this.itemKey=normalizeKey(itemKey,"itemKey");
            if(quantity<=0)
                throw new IllegalArgumentException(
                    "equipment quantity="+quantity
                );
            this.quantity=quantity;
        }
    }

    static final class SkillValue {
        final String skillKey;
        final long desiredValue;

        SkillValue(String skillKey,long desiredValue){
            this.skillKey=normalizeKey(
                skillKey,
                "skillKey"
            );
            if(desiredValue<0)
                throw new IllegalArgumentException(
                    "desired skill value="+desiredValue
                );
            this.desiredValue=desiredValue;
        }
    }

    static final class SkillProfile {
        final List<SkillValue> values;

        SkillProfile(List<SkillValue> values){
            if(values==null)
                throw new NullPointerException("values");

            ArrayList<SkillValue> copy=new ArrayList<>();
            HashSet<String> keys=new HashSet<>();

            for(SkillValue value:values){
                SkillValue checked=
                    Objects.requireNonNull(
                        value,
                        "skill value"
                    );

                if(!keys.add(checked.skillKey))
                    throw new IllegalArgumentException(
                        "duplicate skill key="+
                        checked.skillKey
                    );

                copy.add(checked);
            }

            this.values=Collections.unmodifiableList(copy);
        }
    }

    static final class PetSelection {
        final String petKey;

        PetSelection(String petKey){
            this.petKey=normalizeKey(
                petKey,
                "petKey"
            );
        }
    }

    final PlayerLoadoutId id;
    final PlayerLoadoutVersion version;
    final String ownerRef;
    final List<InventoryEntry> inventory;
    final List<EquipmentEntry> equipment;
    final SkillProfile skillProfile;
    final PetSelection petSelection;
    final String sourceAuthority;

    PlayerLoadout(
        PlayerLoadoutId id,
        PlayerLoadoutVersion version,
        String ownerRef,
        List<InventoryEntry> inventory,
        List<EquipmentEntry> equipment,
        SkillProfile skillProfile,
        PetSelection petSelection,
        String sourceAuthority
    ){
        this.id=Objects.requireNonNull(id,"id");
        this.version=Objects.requireNonNull(version,"version");
        this.ownerRef=requireText(ownerRef,"ownerRef");
        this.inventory=immutableInventory(inventory);
        this.equipment=immutableEquipment(equipment);
        this.skillProfile=skillProfile;
        this.petSelection=petSelection;
        this.sourceAuthority=
            requireText(
                sourceAuthority,
                "sourceAuthority"
            );
    }

    boolean hasSkillProfile(){
        return skillProfile!=null;
    }

    boolean hasPetSelection(){
        return petSelection!=null;
    }

    private static List<InventoryEntry> immutableInventory(
        List<InventoryEntry> values
    ){
        if(values==null)
            throw new NullPointerException("inventory");

        ArrayList<InventoryEntry> copy=new ArrayList<>();
        HashSet<String> keys=new HashSet<>();

        for(InventoryEntry value:values){
            InventoryEntry checked=
                Objects.requireNonNull(
                    value,
                    "inventory entry"
                );

            if(!keys.add(checked.itemKey))
                throw new IllegalArgumentException(
                    "duplicate inventory item key="+
                    checked.itemKey
                );

            copy.add(checked);
        }

        return Collections.unmodifiableList(copy);
    }

    private static List<EquipmentEntry> immutableEquipment(
        List<EquipmentEntry> values
    ){
        if(values==null)
            throw new NullPointerException("equipment");

        ArrayList<EquipmentEntry> copy=new ArrayList<>();
        HashSet<String> slots=new HashSet<>();

        for(EquipmentEntry value:values){
            EquipmentEntry checked=
                Objects.requireNonNull(
                    value,
                    "equipment entry"
                );

            if(!slots.add(checked.slotKey))
                throw new IllegalArgumentException(
                    "duplicate equipment slot key="+
                    checked.slotKey
                );

            copy.add(checked);
        }

        return Collections.unmodifiableList(copy);
    }

    static String normalizeKey(String value,String field){
        if(value==null)
            throw new NullPointerException(field);

        String normalized=
            value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        if(normalized.length()>160)
            throw new IllegalArgumentException(
                field+" too long"
            );

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid "+field+"="+value
                );
        }

        return normalized;
    }

    static String requireText(String value,String field){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}

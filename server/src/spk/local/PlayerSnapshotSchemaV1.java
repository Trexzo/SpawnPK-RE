package spk.local;

import java.util.*;

/**
 * Schema-v1 gameplay-state codec.
 *
 * This class is the only authority that knows the established account property
 * keys. Gameplay state classes expose typed state only; repositories decide how
 * an immutable PlayerSnapshot is stored.
 */
final class PlayerSnapshotSchemaV1 {
    private static final String PET_ACCESSORY_KEY="pet.accessoryItem";

    static SortedMap<String,String> capture(
        WorldPlayer player,
        int petAccessoryItem
    ){
        Objects.requireNonNull(player,"player");
        return capture(
            player.bank(),
            player.equipment(),
            player.movement(),
            player.petState(),
            player.playerState(),
            petAccessoryItem
        );
    }

    static SortedMap<String,String> capture(
        BankState bank,
        EquipmentState equipment,
        MovementState movement,
        PetState pet,
        PlayerState player,
        int petAccessoryItem
    ){
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(equipment,"equipment");
        Objects.requireNonNull(movement,"movement");

        TreeMap<String,String> values=
            new TreeMap<>();

        encodeBank(values,bank);
        encodeEquipment(values,equipment);
        encodeMovement(values,movement);
        if(pet!=null)encodePet(values,pet);
        if(player!=null)encodePlayer(values,player);
        encodePetAccessory(values,petAccessoryItem);

        return values;
    }

    static void apply(
        PlayerSnapshot snapshot,
        WorldPlayer player
    ){
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(player,"player");

        apply(
            snapshot.values(),
            player.bank(),
            player.equipment(),
            player.movement(),
            player.petState(),
            player.playerState()
        );
    }

    static void apply(
        Map<String,String> values,
        BankState bank,
        EquipmentState equipment,
        MovementState movement,
        PetState pet,
        PlayerState player
    ){
        Objects.requireNonNull(values,"values");
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(equipment,"equipment");
        Objects.requireNonNull(movement,"movement");

        decodeBank(values,bank);
        decodeEquipment(values,equipment);
        decodeMovement(values,movement);
        if(pet!=null)decodePet(values,pet);
        if(player!=null)decodePlayer(values,player,equipment);
    }

    static int petAccessoryItem(
        PlayerSnapshot snapshot
    ){
        Objects.requireNonNull(snapshot,"snapshot");
        return petAccessoryItem(snapshot.values());
    }

    static int petAccessoryItem(
        Map<String,String> values
    ){
        String raw=value(
            values,
            PET_ACCESSORY_KEY,
            "0"
        ).trim();

        try{
            return Math.max(
                0,
                Integer.parseInt(raw)
            );
        }catch(NumberFormatException e){
            return 0;
        }
    }

    private static void encodeBank(
        Map<String,String> values,
        BankState bank
    ){
        put(
            values,
            "bank.placeholders",
            Boolean.toString(
                bank.placeholdersEnabled()
            )
        );

        for(int i=0;
            i<BankState.BANK_CAPACITY;
            i++)
            put(
                values,
                "bank."+i,
                encodeStack(
                    bank.bankAt(i)
                )
            );

        for(int i=0;
            i<BankState.INVENTORY_CAPACITY;
            i++)
            put(
                values,
                "inventory."+i,
                encodeStack(
                    bank.inventoryAt(i)
                )
            );
    }

    private static void decodeBank(
        Map<String,String> values,
        BankState bank
    ){
        BankState.Stack[] nextBank=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];
        BankState.Stack[] nextInventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];

        for(int i=0;
            i<nextBank.length;
            i++)
            nextBank[i]=decodeStack(
                value(
                    values,
                    "bank."+i,
                    ""
                ),
                "bank."+i
            );

        for(int i=0;
            i<nextInventory.length;
            i++)
            nextInventory[i]=decodeStack(
                value(
                    values,
                    "inventory."+i,
                    ""
                ),
                "inventory."+i
            );

        bank.restoreAccountState(
            nextBank,
            nextInventory,
            Boolean.parseBoolean(
                value(
                    values,
                    "bank.placeholders",
                    "false"
                )
            )
        );
    }

    private static String encodeStack(
        BankState.Stack stack
    ){
        return stack==null
            ?""
            :stack.itemId+
                ","+stack.qty+
                ","+stack.tab;
    }

    private static BankState.Stack decodeStack(
        String text,
        String key
    ){
        if(text==null||text.isEmpty())
            return null;

        String[] parts=
            text.split(",",-1);

        if(parts.length!=3)
            throw new IllegalArgumentException(
                "bad "+key+"="+text
            );

        try{
            int itemId=
                Integer.parseInt(
                    parts[0]
                );
            int quantity=
                Integer.parseInt(
                    parts[1]
                );
            int tab=
                Integer.parseInt(
                    parts[2]
                );

            if(itemId<0||quantity<0)
                throw new IllegalArgumentException(
                    "bad "+key+"="+text
                );

            return new BankState.Stack(
                itemId,
                quantity,
                tab
            );
        }catch(NumberFormatException e){
            throw new IllegalArgumentException(
                "bad "+key+"="+text,
                e
            );
        }
    }

    private static void encodeEquipment(
        Map<String,String> values,
        EquipmentState equipment
    ){
        for(int i=0;
            i<EquipmentState.EQUIPMENT_SLOTS;
            i++){
            put(
                values,
                "equipment."+i,
                Integer.toString(
                    equipment.itemAt(i)
                )
            );
            put(
                values,
                "equipmentQty."+i,
                Integer.toString(
                    equipment.quantityAt(i)
                )
            );
        }
    }

    private static void decodeEquipment(
        Map<String,String> values,
        EquipmentState equipment
    ){
        int[] next=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] nextQty=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        Arrays.fill(next,-1);

        for(int i=0;
            i<next.length;
            i++){
            String itemText=
                values.get(
                    "equipment."+i
                );

            if(itemText==null)
                continue;

            try{
                next[i]=
                    Integer.parseInt(
                        itemText
                    );
            }catch(Exception e){
                throw new IllegalArgumentException(
                    "bad equipment."+
                    i+"="+itemText
                );
            }

            if(next[i]>=0){
                String quantityText=
                    values.get(
                        "equipmentQty."+i
                    );

                if(quantityText==null||
                   quantityText.isEmpty())
                    nextQty[i]=1;
                else{
                    try{
                        nextQty[i]=
                            Integer.parseInt(
                                quantityText
                            );
                    }catch(Exception e){
                        throw new IllegalArgumentException(
                            "bad equipmentQty."+
                            i+"="+quantityText
                        );
                    }

                    if(nextQty[i]<=0)
                        nextQty[i]=1;
                }
            }
        }

        equipment.restoreAccountState(
            next,
            nextQty
        );
    }

    private static void encodeMovement(
        Map<String,String> values,
        MovementState movement
    ){
        put(
            values,
            "movement.runEnabled",
            Boolean.toString(
                movement.persistentRun()
            )
        );
        put(
            values,
            "movement.runEnergy",
            Integer.toString(
                movement.runEnergy()
            )
        );

        int worldX=
            movement.transientRegion()
                ?MovementState.INITIAL_X
                :movement.x();
        int worldY=
            movement.transientRegion()
                ?MovementState.INITIAL_Y
                :movement.y();
        int plane=
            movement.transientRegion()
                ?0
                :movement.plane();

        put(
            values,
            "movement.worldX",
            Integer.toString(worldX)
        );
        put(
            values,
            "movement.worldY",
            Integer.toString(worldY)
        );
        put(
            values,
            "movement.plane",
            Integer.toString(plane)
        );
    }

    private static void decodeMovement(
        Map<String,String> values,
        MovementState movement
    ){
        boolean runEnabled=
            Boolean.parseBoolean(
                value(
                    values,
                    "movement.runEnabled",
                    "false"
                )
            );

        int runEnergy=100;
        try{
            runEnergy=
                Integer.parseInt(
                    value(
                        values,
                        "movement.runEnergy",
                        "100"
                    )
                );
        }catch(Exception ignored){
            runEnergy=100;
        }

        int worldX=
            MovementState.INITIAL_X;
        int worldY=
            MovementState.INITIAL_Y;
        int plane=0;

        try{
            worldX=
                Integer.parseInt(
                    value(
                        values,
                        "movement.worldX",
                        Integer.toString(
                            MovementState.INITIAL_X
                        )
                    )
                );
            worldY=
                Integer.parseInt(
                    value(
                        values,
                        "movement.worldY",
                        Integer.toString(
                            MovementState.INITIAL_Y
                        )
                    )
                );
            plane=
                Integer.parseInt(
                    value(
                        values,
                        "movement.plane",
                        "0"
                    )
                );
        }catch(Exception ignored){
            worldX=
                MovementState.INITIAL_X;
            worldY=
                MovementState.INITIAL_Y;
            plane=0;
        }

        movement.restoreAccountState(
            runEnabled,
            runEnergy,
            worldX,
            worldY,
            plane
        );
    }

    private static void encodePet(
        Map<String,String> values,
        PetState pet
    ){
        put(
            values,
            "pet.activeItemId",
            Integer.toString(
                pet.itemId()
            )
        );
        put(
            values,
            "pet.activeNpcId",
            Integer.toString(
                pet.npcId()
            )
        );
        put(
            values,
            "pet.miniItemId",
            Integer.toString(
                pet.miniItemId()
            )
        );
    }

    private static void decodePet(
        Map<String,String> values,
        PetState pet
    ){
        int itemId=
            tolerantInt(
                values.get(
                    "pet.activeItemId"
                ),
                -1
            );
        int npcId=
            tolerantInt(
                values.get(
                    "pet.activeNpcId"
                ),
                -1
            );

        pet.clear();

        if((itemId<0)==(npcId<0)&&
           itemId>=0){
            PetDefinitionRepository.Def definition=
                PetDefinitionRepository.get(
                    itemId
                );

            if(definition!=null&&
               definition.npcId==npcId)
                pet.activate(
                    definition
                );
        }

        int miniItemId=
            tolerantInt(
                values.get(
                    "pet.miniItemId"
                ),
                -1
            );

        pet.clearMini();

        if(miniItemId>=0&&
           MiniPetDefinitionRepository.get(
                miniItemId
           )!=null)
            pet.configureMini(
                miniItemId
            );
    }

    private static void encodePlayer(
        Map<String,String> values,
        PlayerState player
    ){
        for(int i=0;
            i<PlayerState.COMBAT_SKILL_COUNT;
            i++){
            put(
                values,
                "skill."+i+".current",
                Integer.toString(
                    player.currentLevel(i)
                )
            );
            put(
                values,
                "skill."+i+".xp",
                Integer.toString(
                    player.xp(i)
                )
            );
        }

        int[] selectors=
            player.compSelectors();

        for(int i=0;
            i<selectors.length;
            i++)
            put(
                values,
                "comp.selector."+i,
                Integer.toString(
                    selectors[i]
                )
            );

        put(
            values,
            "combat.special.energy",
            Integer.toString(
                player.specialEnergy()
            )
        );
        put(
            values,
            "status.poison",
            Integer.toString(
                player.poison()
            )
        );
        put(
            values,
            "status.venom",
            Integer.toString(
                player.venom()
            )
        );
        put(
            values,
            "status.sicken",
            Integer.toString(
                player.sicken()
            )
        );
        put(
            values,
            "cosmetic.itemId",
            Integer.toString(
                player.cosmetic().itemId()
            )
        );
    }

    private static void decodePlayer(
        Map<String,String> values,
        PlayerState player,
        EquipmentState equipment
    ){
        for(int i=0;
            i<PlayerState.COMBAT_SKILL_COUNT;
            i++){
            player.setCurrentLevel(
                i,
                boundedInt(
                    values.get(
                        "skill."+i+".current"
                    ),
                    99,
                    1,
                    255
                )
            );
            player.setXp(
                i,
                boundedInt(
                    values.get(
                        "skill."+i+".xp"
                    ),
                    PlayerState.XP_99,
                    0,
                    Integer.MAX_VALUE
                )
            );
        }

        int[] selectors={
            13,9,7,9,7,5
        };

        for(int i=0;
            i<selectors.length;
            i++)
            selectors[i]=
                boundedInt(
                    values.get(
                        "comp.selector."+i
                    ),
                    selectors[i],
                    0,
                    19
                );

        if(!player.setCompSelectors(
                selectors))
            throw new IllegalStateException(
                "invalid comp selectors"
            );

        player.setSpecialEnergy(
            boundedInt(
                values.get(
                    "combat.special.energy"
                ),
                100,
                0,
                100
            )
        );
        player.setNegativeEffects(
            boundedInt(
                values.get(
                    "status.poison"
                ),
                0,
                0,
                Integer.MAX_VALUE
            ),
            boundedInt(
                values.get(
                    "status.venom"
                ),
                0,
                0,
                Integer.MAX_VALUE
            ),
            boundedInt(
                values.get(
                    "status.sicken"
                ),
                0,
                0,
                Integer.MAX_VALUE
            )
        );

        int cosmeticItem=
            boundedInt(
                values.get(
                    "cosmetic.itemId"
                ),
                -1,
                -1,
                65535
            );

        if(cosmeticItem>0&&
           ItemCatalog.isNativePlayerIcon(
               cosmeticItem))
            player.cosmetic().set(
                cosmeticItem
            );
        else
            player.cosmetic().clear();

        player.syncEquipmentPresentation(
            equipment
        );
    }

    private static void encodePetAccessory(
        Map<String,String> values,
        int petAccessoryItem
    ){
        if(petAccessoryItem>0)
            put(
                values,
                PET_ACCESSORY_KEY,
                Integer.toString(
                    petAccessoryItem
                )
            );
        else
            values.remove(
                PET_ACCESSORY_KEY
            );
    }

    private static String value(
        Map<String,String> values,
        String key,
        String fallback
    ){
        String result=
            values.get(key);
        return result==null
            ?fallback
            :result;
    }

    private static void put(
        Map<String,String> values,
        String key,
        String value
    ){
        values.put(
            key,
            value
        );
    }

    private static int tolerantInt(
        String value,
        int fallback
    ){
        try{
            return value==null
                ?fallback
                :Integer.parseInt(
                    value
                );
        }catch(Exception e){
            return fallback;
        }
    }

    private static int boundedInt(
        String value,
        int fallback,
        int min,
        int max
    ){
        try{
            int parsed=
                Integer.parseInt(
                    value
                );

            return parsed<min||
                   parsed>max
                ?fallback
                :parsed;
        }catch(Exception e){
            return fallback;
        }
    }

    private PlayerSnapshotSchemaV1(){}
}

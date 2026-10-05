package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * G1.3 LocalLab default-loadout regear composition.
 *
 * This is explicit SpawnPK-RE gameplay policy, not recovered original SpawnPK
 * starter/economy authority. Loadout identity/version/default selection remain
 * owned by the existing LoadoutService/DefaultLoadoutService foundations.
 */
final class G1DefaultLoadoutRegearService {
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_G1_STARTER_REGEAR_V1";
    static final PlayerLoadoutId STARTER_ID=
        PlayerLoadoutId.of("g1:starter");
    static final int STARTER_WEAPON=4151;
    static final int STARTER_FOOD=385;
    static final int STARTER_FOOD_COUNT=10;

    static final class Prepared {
        final String ownerRef;
        final long selectionRevision;
        final LoadoutService.LoadoutApplyPlan plan;
        final int[] inventoryItems;
        final int[] inventoryQuantities;
        final int[] equipmentItems;
        final int[] equipmentQuantities;

        private Prepared(
            String ownerRef,
            long selectionRevision,
            LoadoutService.LoadoutApplyPlan plan,
            int[] inventoryItems,
            int[] inventoryQuantities,
            int[] equipmentItems,
            int[] equipmentQuantities
        ){
            this.ownerRef=ownerRef;
            this.selectionRevision=selectionRevision;
            this.plan=plan;
            this.inventoryItems=inventoryItems;
            this.inventoryQuantities=inventoryQuantities;
            this.equipmentItems=equipmentItems;
            this.equipmentQuantities=equipmentQuantities;
        }
    }

    static final class Result {
        final boolean applied;
        final boolean firstApplyOfVersion;
        final String ownerRef;
        final PlayerLoadoutId loadoutId;
        final PlayerLoadoutVersion version;
        final long selectionRevision;
        final int inventorySlots;
        final int equipmentSlots;
        final String policyAuthority;

        private Result(
            boolean applied,
            boolean firstApplyOfVersion,
            Prepared prepared,
            int inventorySlots,
            int equipmentSlots
        ){
            this.applied=applied;
            this.firstApplyOfVersion=firstApplyOfVersion;
            this.ownerRef=prepared.ownerRef;
            this.loadoutId=prepared.plan.loadout.id;
            this.version=prepared.plan.loadout.version;
            this.selectionRevision=prepared.selectionRevision;
            this.inventorySlots=inventorySlots;
            this.equipmentSlots=equipmentSlots;
            this.policyAuthority=POLICY_AUTHORITY;
        }

        @Override public String toString(){
            return "Result{applied="+applied+
                ",firstApplyOfVersion="+firstApplyOfVersion+
                ",owner="+ownerRef+
                ",loadout="+loadoutId+
                ",version="+version+
                ",selectionRevision="+selectionRevision+
                ",inventorySlots="+inventorySlots+
                ",equipmentSlots="+equipmentSlots+
                ",authority="+policyAuthority+"}";
        }
    }

    private final World world;
    private final WorldPlayer player;
    private final LoadoutService loadouts;
    private final DefaultLoadoutService defaults;

    G1DefaultLoadoutRegearService(
        World world,
        WorldPlayer player
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.loadouts=world.loadouts();
        this.defaults=world.defaultLoadouts();
    }

    /**
     * Called while login initialization is serialized. It never overwrites an
     * existing default selection.
     */
    static DefaultLoadoutService.Snapshot ensureStarterDefault(
        World world,
        String ownerRef
    ){
        Objects.requireNonNull(world,"world");
        String owner=normalizeOwner(ownerRef);
        LoadoutService loadouts=world.loadouts();
        DefaultLoadoutService defaults=world.defaultLoadouts();

        if(loadouts.get(owner,STARTER_ID)==null){
            loadouts.create(starterLoadout(owner));
        }

        DefaultLoadoutService.Snapshot current=
            defaults.snapshot(owner);

        if(current.hasDefault())
            return current;

        return defaults.setDefault(
            owner,
            STARTER_ID
        );
    }

    Prepared prepareDefault(
        String ownerRef
    ){
        String owner=normalizeOwner(ownerRef);

        DefaultLoadoutService.Snapshot selected=
            defaults.snapshot(owner);

        if(!selected.hasDefault())
            return null;

        LoadoutService.LoadoutApplyPlan plan=
            loadouts.planApply(
                owner,
                selected.defaultLoadoutId,
                G1DefaultLoadoutRegearService::validateLoadout
            );

        Postimage postimage=
            buildPostimage(
                plan.loadout
            );

        return new Prepared(
            owner,
            selected.selectionRevision,
            plan,
            postimage.inventoryItems,
            postimage.inventoryQuantities,
            postimage.equipmentItems,
            postimage.equipmentQuantities
        );
    }

    void publishPrepared(
        Prepared prepared,
        ServerPacketWriter writer
    )throws IOException{
        if(prepared==null)
            return;

        Objects.requireNonNull(
            writer,
            "writer"
        );

        writer.varShort(
            53,
            BootstrapPackets.itemContainer53(
                BankState.NORMAL_INVENTORY_CONTAINER,
                prepared.inventoryItems,
                prepared.inventoryQuantities
            )
        );
        writer.varShort(
            53,
            BootstrapPackets.equipmentContainer53(
                prepared.equipmentItems,
                prepared.equipmentQuantities
            )
        );
    }

    Result commitPreparedAfterRespawn(
        Prepared prepared
    ){
        if(prepared==null)
            return null;

        if(!player.lifecycle().alive())
            throw new IllegalStateException(
                "G1 default regear requires alive player"
            );

        DefaultLoadoutService.Snapshot selected=
            defaults.snapshot(
                prepared.ownerRef
            );

        if(!selected.hasDefault()||
           !selected.defaultLoadoutId.equals(
                prepared.plan.loadout.id)||
           selected.selectionRevision!=
                prepared.selectionRevision)
            throw new IllegalStateException(
                "G1 default selection changed before regear commit"
            );

        LoadoutService.Snapshot current=
            loadouts.get(
                prepared.ownerRef,
                prepared.plan.loadout.id
            );

        if(current==null||
           !current.loadout.version.equals(
                prepared.plan.loadout.version))
            throw new IllegalStateException(
                "G1 loadout version changed before regear commit"
            );

        synchronized(player.mutationLock()){
            if(!player.lifecycle().alive())
                throw new IllegalStateException(
                    "G1 default regear player died before commit"
                );

            int[] inventoryBefore=
                new int[BankState.INVENTORY_CAPACITY];
            int[] inventoryQtyBefore=
                new int[BankState.INVENTORY_CAPACITY];

            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.InventorySlotSnapshot before=
                    player.bank().inventorySlotSnapshot(
                        slot
                    );
                inventoryBefore[slot]=
                    before.occupied
                        ?before.itemId
                        :-1;
                inventoryQtyBefore[slot]=
                    before.occupied
                        ?before.quantity
                        :0;
            }

            int[] equipmentBefore=
                player.equipment().containerItems();
            int[] equipmentQtyBefore=
                player.equipment().containerQuantities();

            boolean stateMutated=false;

            try{
                player.bank().replaceInventorySemantic(
                    prepared.inventoryItems,
                    prepared.inventoryQuantities
                );
                player.equipment().restoreAccountState(
                    prepared.equipmentItems,
                    prepared.equipmentQuantities
                );
                player.playerState()
                    .syncEquipmentPresentation(
                        player.equipment()
                    );
                stateMutated=true;

                boolean firstApply=
                    loadouts.acknowledgeApplied(
                        prepared.plan
                    );

                return new Result(
                    true,
                    firstApply,
                    prepared,
                    occupied(
                        prepared.inventoryItems
                    ),
                    occupied(
                        prepared.equipmentItems
                    )
                );
            }catch(RuntimeException|Error failure){
                if(stateMutated){
                    player.bank().replaceInventorySemantic(
                        inventoryBefore,
                        inventoryQtyBefore
                    );
                    player.equipment().restoreAccountState(
                        equipmentBefore,
                        equipmentQtyBefore
                    );
                    player.playerState()
                        .syncEquipmentPresentation(
                            player.equipment()
                        );
                }
                throw failure;
            }
        }
    }

    private static LoadoutService.ValidationResult validateLoadout(
        PlayerLoadout loadout
    ){
        try{
            buildPostimage(loadout);
            return LoadoutService.ValidationResult.valid();
        }catch(RuntimeException invalid){
            return LoadoutService.ValidationResult.invalid(
                invalid.getMessage()==null
                    ?invalid.getClass().getSimpleName()
                    :invalid.getMessage()
            );
        }
    }

    private static PlayerLoadout starterLoadout(
        String owner
    ){
        return new PlayerLoadout(
            STARTER_ID,
            PlayerLoadoutVersion.of(1L),
            owner,
            Arrays.asList(
                new PlayerLoadout.InventoryEntry(
                    "item:"+STARTER_FOOD,
                    STARTER_FOOD_COUNT
                )
            ),
            Arrays.asList(
                new PlayerLoadout.EquipmentEntry(
                    "slot:weapon",
                    "item:"+STARTER_WEAPON,
                    1L
                )
            ),
            null,
            null,
            POLICY_AUTHORITY
        );
    }

    private static final class Postimage {
        final int[] inventoryItems;
        final int[] inventoryQuantities;
        final int[] equipmentItems;
        final int[] equipmentQuantities;

        Postimage(
            int[] inventoryItems,
            int[] inventoryQuantities,
            int[] equipmentItems,
            int[] equipmentQuantities
        ){
            this.inventoryItems=inventoryItems;
            this.inventoryQuantities=inventoryQuantities;
            this.equipmentItems=equipmentItems;
            this.equipmentQuantities=equipmentQuantities;
        }
    }

    private static Postimage buildPostimage(
        PlayerLoadout loadout
    ){
        Objects.requireNonNull(
            loadout,
            "loadout"
        );

        if(loadout.hasSkillProfile())
            throw new IllegalArgumentException(
                "G1 regear does not own skill-profile mutation"
            );
        if(loadout.hasPetSelection())
            throw new IllegalArgumentException(
                "G1 regear does not own pet-selection mutation"
            );

        int[] inventoryItems=
            new int[BankState.INVENTORY_CAPACITY];
        int[] inventoryQuantities=
            new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(
            inventoryItems,
            -1
        );

        int nextSlot=0;

        for(PlayerLoadout.InventoryEntry entry:
            loadout.inventory){
            int itemId=
                parseItemKey(
                    entry.itemKey
                );
            requireCatalogItem(
                itemId
            );

            if(entry.quantity>
                    Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "inventory quantity exceeds int item="+
                    itemId
                );

            if(ItemCatalog.isStackable(itemId)){
                if(nextSlot>=
                        BankState.INVENTORY_CAPACITY)
                    throw new IllegalArgumentException(
                        "inventory exceeds 28 slots"
                    );

                inventoryItems[nextSlot]=
                    itemId;
                inventoryQuantities[nextSlot]=
                    (int)entry.quantity;
                nextSlot++;
                continue;
            }

            if(entry.quantity>
                    BankState.INVENTORY_CAPACITY-
                    nextSlot)
                throw new IllegalArgumentException(
                    "inventory exceeds 28 slots"
                );

            for(long n=0;
                n<entry.quantity;
                n++){
                inventoryItems[nextSlot]=
                    itemId;
                inventoryQuantities[nextSlot]=1;
                nextSlot++;
            }
        }

        int[] equipmentItems=
            new int[EquipmentState.EQUIPMENT_SLOTS];
        int[] equipmentQuantities=
            new int[EquipmentState.EQUIPMENT_SLOTS];
        Arrays.fill(
            equipmentItems,
            -1
        );

        EquipmentMetadataRepository.Meta weaponMeta=null;
        boolean shieldPresent=false;

        for(PlayerLoadout.EquipmentEntry entry:
            loadout.equipment){
            EquipmentSlot slot=
                parseSlotKey(
                    entry.slotKey
                );
            int itemId=
                parseItemKey(
                    entry.itemKey
                );
            requireCatalogItem(
                itemId
            );

            EquipmentMetadataRepository.Meta meta=
                EquipmentMetadataRepository.resolve(
                    itemId
                );
            if(meta==null)
                throw new IllegalArgumentException(
                    "equipment metadata unresolved item="+
                    itemId
                );
            if(meta.slot!=slot)
                throw new IllegalArgumentException(
                    "equipment slot mismatch item="+
                    itemId+
                    " requested="+slot+
                    " resolved="+meta.slot
                );

            if(entry.quantity>
                    Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                    "equipment quantity exceeds int item="+
                    itemId
                );

            if(slot!=EquipmentSlot.AMMO&&
               entry.quantity!=1L)
                throw new IllegalArgumentException(
                    "non-ammo equipment quantity must be 1 item="+
                    itemId
                );

            if(slot==EquipmentSlot.AMMO&&
               !ItemCatalog.isStackable(itemId))
                throw new IllegalArgumentException(
                    "ammo equipment must be stackable item="+
                    itemId
                );

            equipmentItems[
                slot.equipmentIndex
            ]=itemId;
            equipmentQuantities[
                slot.equipmentIndex
            ]=(int)entry.quantity;

            if(slot==EquipmentSlot.WEAPON)
                weaponMeta=meta;
            if(slot==EquipmentSlot.SHIELD)
                shieldPresent=true;
        }

        if(weaponMeta!=null&&
           weaponMeta.twoHanded&&
           shieldPresent)
            throw new IllegalArgumentException(
                "two-handed weapon conflicts with shield"
            );

        return new Postimage(
            inventoryItems,
            inventoryQuantities,
            equipmentItems,
            equipmentQuantities
        );
    }

    private static int parseItemKey(
        String key
    ){
        String normalized=
            PlayerLoadout.normalizeKey(
                key,
                "itemKey"
            );

        if(!normalized.startsWith(
                "item:"))
            throw new IllegalArgumentException(
                "G1 item key must be item:<id> actual="+
                normalized
            );

        String number=
            normalized.substring(
                "item:".length()
            );

        try{
            int itemId=
                Integer.parseInt(
                    number
                );
            if(itemId<0)
                throw new NumberFormatException(
                    "negative"
                );
            return itemId;
        }catch(NumberFormatException bad){
            throw new IllegalArgumentException(
                "invalid G1 item key="+
                normalized,
                bad
            );
        }
    }

    private static EquipmentSlot parseSlotKey(
        String key
    ){
        String normalized=
            PlayerLoadout.normalizeKey(
                key,
                "slotKey"
            );

        if(!normalized.startsWith(
                "slot:"))
            throw new IllegalArgumentException(
                "G1 slot key must be slot:<name> actual="+
                normalized
            );

        String name=
            normalized.substring(
                "slot:".length()
            ).toUpperCase(
                Locale.ROOT
            );

        try{
            return EquipmentSlot.valueOf(
                name
            );
        }catch(IllegalArgumentException bad){
            throw new IllegalArgumentException(
                "unknown G1 equipment slot="+
                normalized,
                bad
            );
        }
    }

    private static void requireCatalogItem(
        int itemId
    ){
        if(!ItemCatalog.exists(itemId))
            throw new IllegalArgumentException(
                "unknown G1 item="+
                itemId
            );
    }

    private static int occupied(
        int[] items
    ){
        int count=0;
        for(int item:items)
            if(item>=0)
                count++;
        return count;
    }

    private static String normalizeOwner(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "ownerRef"
            );

        String normalized=
            value.trim().toLowerCase(
                Locale.ROOT
            );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "ownerRef blank"
            );

        return normalized;
    }

}

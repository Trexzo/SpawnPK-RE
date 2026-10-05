package spk.local;

import java.util.Arrays;
import java.util.Collections;

public final class PlayerLoadoutApplyServiceTest {
    private static final String OWNER=
        "player:alice";
    private static final String RESOLVER_AUTHORITY=
        "CUSTOM_LOCALLAB_LOADOUT_RESOLVER_TEST";

    public static void main(String[] args){
        exactApplyAndReplay();
        stalePlanRejectsWithoutMutation();
        capacityRejectsWithoutMutation();
        canonicalResolverContract();

        System.out.println(
            "PLAYER_LOADOUT_APPLY_PASS "+
            "semanticPlan=true "+
            "inventoryMaterialized=true "+
            "nonStackablesExpanded=true "+
            "equipmentMaterialized=true "+
            "versionStable=true "+
            "ackAfterCommit=true "+
            "sameRevisionIdempotent=true "+
            "stalePlanAtomic=true "+
            "capacityAtomic=true "+
            "widgetIdentity=false "+
            "packetOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactApplyAndReplay(){
        WorldPlayer player=
            new WorldPlayer();
        LoadoutService loadouts=
            new LoadoutService();

        PlayerLoadout loadout=
            loadout(
                1L,
                995,
                100L,
                15272,
                2L
            );

        loadouts.create(
            loadout
        );

        LoadoutService.LoadoutApplyPlan plan=
            loadouts.planApply(
                OWNER,
                loadout.id,
                value->
                    LoadoutService
                        .ValidationResult
                        .valid()
            );

        PlayerLoadoutApplyService service=
            new PlayerLoadoutApplyService(
                player,
                loadouts,
                resolver()
            );

        PlayerLoadoutApplyService.Result first=
            service.apply(
                plan
            );

        BankState.InventorySlotSnapshot slot0=
            player.bank()
                .inventorySlotSnapshot(0);
        BankState.InventorySlotSnapshot slot1=
            player.bank()
                .inventorySlotSnapshot(1);
        BankState.InventorySlotSnapshot slot2=
            player.bank()
                .inventorySlotSnapshot(2);

        require(
            slot0.occupied&&
            slot0.itemId==995&&
            slot0.quantity==100&&
            slot1.occupied&&
            slot1.itemId==15272&&
            slot1.quantity==1&&
            slot2.occupied&&
            slot2.itemId==15272&&
            slot2.quantity==1,
            "inventory postimage"
        );

        require(
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                )==4151&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.WEAPON
                )==1,
            "equipment postimage"
        );

        require(
            first.firstAcknowledgement&&
            first.inventoryOccupiedSlots==3&&
            first.equipmentOccupiedSlots==1&&
            first.version.equals(
                PlayerLoadoutVersion.of(
                    1L
                )
            )&&
            RESOLVER_AUTHORITY.equals(
                first.resolverAuthority
            ),
            "apply result"
        );

        PlayerLoadoutApplyService.Result replay=
            service.apply(
                plan
            );

        require(
            !replay.firstAcknowledgement&&
            loadouts.get(
                OWNER,
                loadout.id
            ).lastAppliedVersion.equals(
                PlayerLoadoutVersion.of(
                    1L
                )
            ),
            "same revision acknowledgement"
        );
    }

    private static void stalePlanRejectsWithoutMutation(){
        WorldPlayer player=
            new WorldPlayer();
        LoadoutService loadouts=
            new LoadoutService();

        PlayerLoadout v1=
            loadout(
                1L,
                995,
                10L,
                15272,
                1L
            );
        loadouts.create(v1);

        LoadoutService.LoadoutApplyPlan stale=
            loadouts.planApply(
                OWNER,
                v1.id,
                value->
                    LoadoutService
                        .ValidationResult
                        .valid()
            );

        PlayerLoadout v2=
            loadout(
                2L,
                995,
                20L,
                15272,
                1L
            );
        loadouts.replace(
            v2,
            PlayerLoadoutVersion.of(1L)
        );

        BankState.InventorySlotSnapshot before=
            player.bank()
                .inventorySlotSnapshot(0);
        int weaponBefore=
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                );

        PlayerLoadoutApplyService service=
            new PlayerLoadoutApplyService(
                player,
                loadouts,
                resolver()
            );

        expect(
            IllegalStateException.class,
            ()->service.apply(stale),
            "stale apply plan"
        );

        BankState.InventorySlotSnapshot after=
            player.bank()
                .inventorySlotSnapshot(0);

        require(
            sameSlot(before,after)&&
            player.equipment()
                .itemAt(
                    EquipmentSlot.WEAPON
                )==weaponBefore&&
            loadouts.get(
                OWNER,
                v1.id
            ).lastAppliedVersion==null,
            "stale plan mutated state"
        );
    }

    private static void capacityRejectsWithoutMutation(){
        WorldPlayer player=
            new WorldPlayer();
        LoadoutService loadouts=
            new LoadoutService();

        PlayerLoadout tooLarge=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "loadout:too-large"
                ),
                PlayerLoadoutVersion.of(1L),
                OWNER,
                Collections.singletonList(
                    new PlayerLoadout.InventoryEntry(
                        "item:id:15272",
                        29L
                    )
                ),
                Collections.emptyList(),
                null,
                null,
                "CUSTOM_LOCALLAB_TEST"
            );

        loadouts.create(
            tooLarge
        );

        LoadoutService.LoadoutApplyPlan plan=
            loadouts.planApply(
                OWNER,
                tooLarge.id,
                value->
                    LoadoutService
                        .ValidationResult
                        .valid()
            );

        BankState.InventorySlotSnapshot before=
            player.bank()
                .inventorySlotSnapshot(0);

        expect(
            IllegalArgumentException.class,
            ()->new PlayerLoadoutApplyService(
                player,
                loadouts,
                resolver()
            ).apply(plan),
            "over-capacity loadout"
        );

        require(
            sameSlot(
                before,
                player.bank()
                    .inventorySlotSnapshot(0)
            )&&
            loadouts.get(
                OWNER,
                tooLarge.id
            ).lastAppliedVersion==null,
            "capacity rejection mutated state"
        );
    }

    private static void canonicalResolverContract(){
        CanonicalPlayerLoadoutSemanticResolver resolver=
            new CanonicalPlayerLoadoutSemanticResolver();

        PlayerLoadoutSemanticResolver.Item whip=
            resolver.resolveItem(
                "item:id:4151"
            );

        require(
            whip.itemId==4151&&
            resolver.resolveEquipmentSlot(
                "slot:weapon"
            )==EquipmentSlot.WEAPON&&
            CanonicalPlayerLoadoutSemanticResolver
                .AUTHORITY
                .equals(
                    resolver.authority()
                ),
            "canonical semantic resolver"
        );

        expect(
            IllegalArgumentException.class,
            ()->resolver.resolveItem(
                "item:fixture-whip"
            ),
            "noncanonical item key"
        );

        expect(
            IllegalArgumentException.class,
            ()->resolver.resolveEquipmentSlot(
                "slot:not-real"
            ),
            "unknown semantic equipment slot"
        );
    }

    private static PlayerLoadout loadout(
        long version,
        int stackableItem,
        long stackableQuantity,
        int foodItem,
        long foodQuantity
    ){
        return new PlayerLoadout(
            PlayerLoadoutId.of(
                "loadout:pvp-primary"
            ),
            PlayerLoadoutVersion.of(
                version
            ),
            OWNER,
            Arrays.asList(
                new PlayerLoadout.InventoryEntry(
                    "item:id:"+
                    stackableItem,
                    stackableQuantity
                ),
                new PlayerLoadout.InventoryEntry(
                    "item:id:"+
                    foodItem,
                    foodQuantity
                )
            ),
            Collections.singletonList(
                new PlayerLoadout.EquipmentEntry(
                    "slot:weapon",
                    "item:id:4151",
                    1L
                )
            ),
            null,
            null,
            "CUSTOM_LOCALLAB_TEST"
        );
    }

    private static PlayerLoadoutSemanticResolver
        resolver()
    {
        return new PlayerLoadoutSemanticResolver(){
            @Override public Item resolveItem(
                String itemKey
            ){
                String key=
                    PlayerLoadout.normalizeKey(
                        itemKey,
                        "itemKey"
                    );

                if("item:id:995".equals(key))
                    return new Item(
                        995,
                        true
                    );
                if("item:id:15272".equals(key))
                    return new Item(
                        15272,
                        false
                    );
                if("item:id:4151".equals(key))
                    return new Item(
                        4151,
                        false
                    );

                throw new IllegalArgumentException(
                    "unknown fixture key="+
                    key
                );
            }

            @Override public EquipmentSlot
                resolveEquipmentSlot(
                    String slotKey
                )
            {
                if("slot:weapon".equals(
                        PlayerLoadout.normalizeKey(
                            slotKey,
                            "slotKey"
                        )))
                    return EquipmentSlot.WEAPON;

                throw new IllegalArgumentException(
                    "unknown fixture slot="+
                    slotKey
                );
            }

            @Override public String authority(){
                return RESOLVER_AUTHORITY;
            }
        };
    }

    private static boolean sameSlot(
        BankState.InventorySlotSnapshot a,
        BankState.InventorySlotSnapshot b
    ){
        return a.occupied==b.occupied&&
            a.itemId==b.itemId&&
            a.quantity==b.quantity;
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PlayerLoadoutApplyServiceTest(){}
}

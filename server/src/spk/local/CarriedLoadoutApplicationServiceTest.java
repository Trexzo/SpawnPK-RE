package spk.local;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class CarriedLoadoutApplicationServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_GAMEPLAY_R1";

    public static void main(String[] args){
        appliesAtomicCarriedState();
        stalePreimageFailsClosed();
        planningFailuresDoNotMutate();
        unsupportedExtensionsFailClosed();
        authorityAndProtocolBoundary();

        System.out.println(
            "CARRIED_LOADOUT_APPLICATION_PASS "+
            "planningMutationFree=true "+
            "stackableMaterialization=true "+
            "nonStackableExpansion=true "+
            "exactPreimageCas=true "+
            "inventoryEquipmentAtomic=true "+
            "stalePlanRejected=true "+
            "unsupportedExtensionsRejected=true "+
            "callerOwnedResolution=true "+
            "protocolIndependent=true"
        );
    }

    private static void appliesAtomicCarriedState(){
        WorldPlayer player=
            new WorldPlayer();
        CarriedLoadoutApplicationService service=
            new CarriedLoadoutApplicationService(
                player,
                POLICY
            );

        PlayerLoadout loadout=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "gameplay:r1-primary"
                ),
                PlayerLoadoutVersion.of(1L),
                "player:alice",
                Arrays.asList(
                    new PlayerLoadout.InventoryEntry(
                        "item:food",
                        3L
                    ),
                    new PlayerLoadout.InventoryEntry(
                        "item:runes",
                        250L
                    )
                ),
                Arrays.asList(
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:whip",
                        1L
                    ),
                    new PlayerLoadout.EquipmentEntry(
                        "slot:ammo",
                        "item:ammo",
                        125L
                    )
                ),
                null,
                null,
                POLICY
            );

        CarriedLoadoutApplicationService.Plan plan=
            service.plan(
                loadout,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            );

        require(
            player.bank().inventorySlots()==0,
            "planning mutated inventory"
        );
        require(
            player.equipment().weapon()==
                EquipmentState.BLOODREND_ID,
            "planning mutated equipment"
        );
        require(
            plan.inventoryOccupiedSlots()==4,
            "inventory materialization count"
        );
        require(
            plan.equipmentOccupiedSlots()==2,
            "equipment materialization count"
        );

        CarriedLoadoutApplicationService
            .CommitResult result=
                service.commit(plan);

        require(
            result.loadoutId.equals(
                loadout.id)&&
            result.loadoutVersion.equals(
                loadout.version)&&
            result.inventoryOccupiedSlots==4&&
            result.equipmentOccupiedSlots==2&&
            POLICY.equals(
                result.policyAuthority),
            "commit result identity"
        );

        for(int slot=0;slot<3;slot++){
            BankState.Stack stack=
                player.bank()
                    .inventoryAt(slot);
            require(
                stack!=null&&
                stack.itemId==1001&&
                stack.qty==1,
                "non-stackable expansion slot="+
                slot
            );
        }

        BankState.Stack runes=
            player.bank()
                .inventoryAt(3);
        require(
            runes!=null&&
            runes.itemId==1002&&
            runes.qty==250,
            "stackable materialization"
        );

        for(int slot=4;
            slot<BankState.INVENTORY_CAPACITY;
            slot++)
            require(
                player.bank()
                    .inventoryAt(slot)==null,
                "inventory trailing slot not cleared "+slot
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
            "weapon applied"
        );

        require(
            player.equipment()
                .itemAt(
                    EquipmentSlot.AMMO
                )==892&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.AMMO
                )==125,
            "ammo applied"
        );

        require(
            player.equipment()
                .occupiedSlots()==2,
            "old equipment not replaced"
        );
    }

    private static void stalePreimageFailsClosed(){
        WorldPlayer player=
            new WorldPlayer();
        CarriedLoadoutApplicationService service=
            new CarriedLoadoutApplicationService(
                player,
                POLICY
            );

        PlayerLoadout loadout=
            basicLoadout();

        CarriedLoadoutApplicationService.Plan plan=
            service.plan(
                loadout,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            );

        player.bank().replaceInventorySemantic(
            inventoryItems(
                2001
            ),
            inventoryQuantities(
                1
            )
        );

        expect(
            IllegalStateException.class,
            ()->service.commit(plan),
            "stale plan"
        );

        BankState.Stack stack=
            player.bank().inventoryAt(0);

        require(
            stack!=null&&
            stack.itemId==2001&&
            stack.qty==1,
            "stale rejection overwrote newer inventory"
        );

        require(
            player.equipment().weapon()==
                EquipmentState.BLOODREND_ID,
            "stale rejection mutated equipment"
        );
    }

    private static void planningFailuresDoNotMutate(){
        WorldPlayer player=
            new WorldPlayer();
        CarriedLoadoutApplicationService service=
            new CarriedLoadoutApplicationService(
                player,
                POLICY
            );

        int weaponBefore=
            player.equipment().weapon();

        PlayerLoadout overflow=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "gameplay:overflow"
                ),
                PlayerLoadoutVersion.of(1L),
                "player:alice",
                Collections.singletonList(
                    new PlayerLoadout.InventoryEntry(
                        "item:food",
                        29L
                    )
                ),
                Collections.emptyList(),
                null,
                null,
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.plan(
                overflow,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            ),
            "capacity overflow"
        );

        PlayerLoadout duplicateResolvedSlot=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "gameplay:duplicate-resolved-slot"
                ),
                PlayerLoadoutVersion.of(1L),
                "player:alice",
                Collections.emptyList(),
                Arrays.asList(
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:whip",
                        1L
                    ),
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon-alias",
                        "item:ammo",
                        1L
                    )
                ),
                null,
                null,
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.plan(
                duplicateResolvedSlot,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            ),
            "duplicate resolved slot"
        );

        PlayerLoadout unresolved=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "gameplay:unresolved"
                ),
                PlayerLoadoutVersion.of(1L),
                "player:alice",
                Collections.singletonList(
                    new PlayerLoadout.InventoryEntry(
                        "item:missing",
                        1L
                    )
                ),
                Collections.emptyList(),
                null,
                null,
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.plan(
                unresolved,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            ),
            "unresolved item"
        );

        require(
            player.bank().inventorySlots()==0&&
            player.equipment().weapon()==
                weaponBefore,
            "failed planning mutated carried state"
        );
    }

    private static void unsupportedExtensionsFailClosed(){
        WorldPlayer player=
            new WorldPlayer();
        CarriedLoadoutApplicationService service=
            new CarriedLoadoutApplicationService(
                player,
                POLICY
            );

        PlayerLoadout skillLoadout=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "gameplay:skill-extension"
                ),
                PlayerLoadoutVersion.of(1L),
                "player:alice",
                Collections.emptyList(),
                Collections.emptyList(),
                new PlayerLoadout.SkillProfile(
                    Collections.singletonList(
                        new PlayerLoadout.SkillValue(
                            "skill:attack",
                            75L
                        )
                    )
                ),
                null,
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.plan(
                skillLoadout,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            ),
            "skill extension silently ignored"
        );

        PlayerLoadout petLoadout=
            new PlayerLoadout(
                PlayerLoadoutId.of(
                    "gameplay:pet-extension"
                ),
                PlayerLoadoutVersion.of(1L),
                "player:alice",
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                new PlayerLoadout.PetSelection(
                    "pet:test"
                ),
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.plan(
                petLoadout,
                CarriedLoadoutApplicationServiceTest
                    ::resolveItem,
                CarriedLoadoutApplicationServiceTest
                    ::resolveSlot
            ),
            "pet extension silently ignored"
        );
    }

    private static void authorityAndProtocolBoundary(){
        WorldPlayer player=
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new CarriedLoadoutApplicationService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client policy authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new CarriedLoadoutApplicationService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown policy authority"
        );

        for(Class<?> type:new Class<?>[]{
                CarriedLoadoutApplicationService.class,
                CarriedLoadoutApplicationService.Plan.class,
                CarriedLoadoutApplicationService.CommitResult.class,
                CarriedLoadoutApplicationService.ResolvedItem.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:
                        new String[]{
                            "widget",
                            "opcode",
                            "packet",
                            "socket",
                            "clientslot"
                        })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "protocol identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static PlayerLoadout basicLoadout(){
        return new PlayerLoadout(
            PlayerLoadoutId.of(
                "gameplay:basic"
            ),
            PlayerLoadoutVersion.of(1L),
            "player:alice",
            Collections.singletonList(
                new PlayerLoadout.InventoryEntry(
                    "item:runes",
                    10L
                )
            ),
            Collections.singletonList(
                new PlayerLoadout.EquipmentEntry(
                    "slot:weapon",
                    "item:whip",
                    1L
                )
            ),
            null,
            null,
            POLICY
        );
    }

    private static CarriedLoadoutApplicationService
        .ResolvedItem resolveItem(
            String key
        ){
        switch(key){
            case "item:food":
                return new CarriedLoadoutApplicationService
                    .ResolvedItem(
                        1001,
                        false
                    );
            case "item:runes":
                return new CarriedLoadoutApplicationService
                    .ResolvedItem(
                        1002,
                        true
                    );
            case "item:whip":
                return new CarriedLoadoutApplicationService
                    .ResolvedItem(
                        4151,
                        false
                    );
            case "item:ammo":
                return new CarriedLoadoutApplicationService
                    .ResolvedItem(
                        892,
                        true
                    );
            default:
                return null;
        }
    }

    private static EquipmentSlot resolveSlot(
        String key
    ){
        switch(key){
            case "slot:weapon":
            case "slot:weapon-alias":
                return EquipmentSlot.WEAPON;
            case "slot:ammo":
                return EquipmentSlot.AMMO;
            default:
                return null;
        }
    }

    private static int[] inventoryItems(
        int first
    ){
        int[] out=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        Arrays.fill(out,-1);
        out[0]=first;
        return out;
    }

    private static int[] inventoryQuantities(
        int first
    ){
        int[] out=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        out[0]=first;
        return out;
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
            throw new AssertionError(label);
    }

    private CarriedLoadoutApplicationServiceTest(){}
}

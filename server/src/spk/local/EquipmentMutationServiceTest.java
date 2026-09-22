package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class EquipmentMutationServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_EQUIPMENT_MUTATION_TEST";

    public static void main(String[] args){
        WorldPlayer player=
            new WorldPlayer();
        EquipmentState equipment=
            player.equipment();

        int[] items=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];
        int[] quantities=
            new int[
                EquipmentState.EQUIPMENT_SLOTS
            ];

        java.util.Arrays.fill(
            items,
            -1
        );

        items[EquipmentSlot.WEAPON.equipmentIndex]=4151;
        quantities[EquipmentSlot.WEAPON.equipmentIndex]=1;
        items[EquipmentSlot.AMMO.equipmentIndex]=892;
        quantities[EquipmentSlot.AMMO.equipmentIndex]=250;

        equipment.restoreAccountState(
            items,
            quantities
        );

        EquipmentMutationService service=
            new EquipmentMutationService(
                player,
                AUTHORITY
            );

        require(
            EquipmentSlot.values().length==11,
            "semantic slot count"
        );

        require(
            EquipmentSlot.fromEquipmentIndex(6)==null&&
            EquipmentSlot.fromEquipmentIndex(8)==null&&
            EquipmentSlot.fromEquipmentIndex(11)==null,
            "unsupported raw slots promoted"
        );

        EquipmentMutationService.SlotSnapshot weapon=
            service.inspect(
                EquipmentSlot.WEAPON
            );

        require(
            weapon.occupied&&
            weapon.itemId==4151&&
            weapon.quantity==1&&
            AUTHORITY.equals(
                weapon.policyAuthority
            ),
            "weapon inspect"
        );

        EquipmentMutationService.ReplaceResult replace=
            service.replace(
                EquipmentSlot.WEAPON,
                4151,
                1,
                28526,
                1
            );

        require(
            replace.previousItemId==4151&&
            replace.previousQuantity==1&&
            replace.nextItemId==28526&&
            replace.nextQuantity==1&&
            equipment.itemAt(
                EquipmentSlot.WEAPON
            )==28526,
            "weapon replace"
        );

        EquipmentMutationService.SlotSnapshot ammo=
            service.inspect(
                EquipmentSlot.AMMO
            );

        require(
            ammo.occupied&&
            ammo.itemId==892&&
            ammo.quantity==250,
            "ammo quantity snapshot"
        );

        EquipmentMutationService.ReplaceResult ammoReplace=
            service.replace(
                EquipmentSlot.AMMO,
                892,
                250,
                9144,
                500
            );

        require(
            ammoReplace.nextItemId==9144&&
            ammoReplace.nextQuantity==500&&
            equipment.quantityAt(
                EquipmentSlot.AMMO
            )==500,
            "ammo quantity replacement"
        );

        int beforeMismatchItem=
            equipment.itemAt(
                EquipmentSlot.WEAPON
            );
        int beforeMismatchQty=
            equipment.quantityAt(
                EquipmentSlot.WEAPON
            );

        expect(
            IllegalStateException.class,
            ()->service.replace(
                EquipmentSlot.WEAPON,
                4151,
                1,
                1333,
                1
            ),
            "compare-and-set mismatch"
        );

        require(
            equipment.itemAt(
                EquipmentSlot.WEAPON
            )==beforeMismatchItem&&
            equipment.quantityAt(
                EquipmentSlot.WEAPON
            )==beforeMismatchQty,
            "mismatch mutated equipment"
        );

        EquipmentMutationService.ReplaceResult removed=
            service.remove(
                EquipmentSlot.WEAPON,
                28526,
                1
            );

        require(
            removed.cleared()&&
            removed.nextItemId==-1&&
            removed.nextQuantity==0&&
            equipment.itemAt(
                EquipmentSlot.WEAPON
            )==-1&&
            equipment.quantityAt(
                EquipmentSlot.WEAPON
            )==0,
            "remove"
        );

        invalidInputsAtomic(
            service,
            equipment
        );
        authorityGuards(
            player
        );
        ownershipLockFence(
            player,
            service
        );
        boundaryGuard();

        System.out.println(
            "EQUIPMENT_MUTATION_SERVICE_PASS "+
            "canonicalEquipmentState=true "+
            "semanticSlots=11 "+
            "exactCompareAndSet=true "+
            "replace=true "+
            "remove=true "+
            "ammoQuantityPreserved=true "+
            "mismatchAtomic=true "+
            "unsupportedRawSlotsAbsent=true "+
            "immutableFacts=true "+
            "packetOwned=false "+
            "inventoryOwned=false "+
            "eligibilityOwned=false "+
            "persistenceOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void invalidInputsAtomic(
        EquipmentMutationService service,
        WorldPlayer player
    ){
        int beforeAmmo=
            equipment.itemAt(
                EquipmentSlot.AMMO
            );
        int beforeQty=
            equipment.quantityAt(
                EquipmentSlot.AMMO
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replace(
                EquipmentSlot.AMMO,
                beforeAmmo,
                beforeQty,
                -1,
                1
            ),
            "invalid empty replacement"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replace(
                EquipmentSlot.AMMO,
                beforeAmmo,
                beforeQty,
                100,
                0
            ),
            "invalid non-empty replacement"
        );

        expect(
            NullPointerException.class,
            ()->service.inspect(
                null
            ),
            "null slot"
        );

        require(
            equipment.itemAt(
                EquipmentSlot.AMMO
            )==beforeAmmo&&
            equipment.quantityAt(
                EquipmentSlot.AMMO
            )==beforeQty,
            "invalid input mutated equipment"
        );
    }

    private static void authorityGuards(
        WorldPlayer player
    ){
        expect(
            IllegalArgumentException.class,
            ()->new EquipmentMutationService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new EquipmentMutationService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority"
        );
    }

    private static void ownershipLockFence(
        WorldPlayer player,
        EquipmentMutationService service
    ){
        CountDownLatch started=
            new CountDownLatch(1);
        CountDownLatch completed=
            new CountDownLatch(1);

        Thread worker=
            new Thread(
                ()->{
                    started.countDown();
                    service.inspect(
                        EquipmentSlot.AMMO
                    );
                    completed.countDown();
                },
                "equipment-mutation-lock-fence"
            );

        try{
            synchronized(player.mutationLock()){
                worker.start();

                require(
                    started.await(
                        2L,
                        TimeUnit.SECONDS
                    ),
                    "equipment ownership worker did not start"
                );

                require(
                    !completed.await(
                        100L,
                        TimeUnit.MILLISECONDS
                    ),
                    "equipment mutation bypassed WorldPlayer mutation lock"
                );
            }

            require(
                completed.await(
                    2L,
                    TimeUnit.SECONDS
                ),
                "equipment mutation did not resume after ownership lock release"
            );
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new AssertionError(
                "equipment ownership lock test interrupted",
                interrupted
            );
        }
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                EquipmentMutationService.class,
                EquipmentMutationService.SlotSnapshot.class,
                EquipmentMutationService.ReplaceResult.class
        }){
            for(Field field:type.getDeclaredFields()){
                String haystack=
                    (field.getName()+" "+
                     field.getType().getName())
                        .toLowerCase(Locale.ROOT);

                for(String forbidden:new String[]{
                        "packet",
                        "widget",
                        "container",
                        "serverpacketwriter",
                        "inventory",
                        "appearance",
                        "eligibility"
                }){
                    require(
                        !haystack.contains(forbidden),
                        "unowned identity leaked through "+
                        type.getSimpleName()+"."+
                        field.getName()
                    );
                }
            }
        }

        for(Method method:
                EquipmentMutationService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            for(String forbidden:new String[]{
                    "packet",
                    "publish",
                    "persist",
                    "inventory",
                    "appearance",
                    "wield",
                    "eligibility"
            }){
                require(
                    !name.contains(forbidden),
                    "unowned behavior leaked through "+
                    method.getName()
                );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private EquipmentMutationServiceTest(){}
}

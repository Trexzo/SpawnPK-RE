package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class InventoryMutationServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_INVENTORY_MUTATION_TEST";

    public static void main(String[] args){
        WorldPlayer player=seededPlayer();
        BankState bank=player.bank();
        InventoryMutationService service=
            new InventoryMutationService(
                player,
                AUTHORITY
            );

        InventoryMutationService.SlotSnapshot initial=
            service.inspect(5);

        require(
            initial.occupied&&
            initial.slot==5&&
            initial.itemId==15272&&
            initial.quantity==3&&
            AUTHORITY.equals(initial.policyAuthority),
            "initial slot snapshot"
        );

        InventoryMutationService.ConsumeResult partial=
            service.consume(
                5,
                15272,
                2
            );

        require(
            partial.slot==5&&
            partial.itemId==15272&&
            partial.requested==2&&
            partial.beforeQuantity==3&&
            partial.afterQuantity==1&&
            !partial.cleared,
            "partial consume facts"
        );

        require(
            bank.inventoryAt(5)!=null&&
            bank.inventoryAt(5).itemId==15272&&
            bank.inventoryAt(5).qty==1,
            "partial canonical mutation"
        );

        require(
            bank.inventoryAt(7)!=null&&
            bank.inventoryAt(7).itemId==995&&
            bank.inventoryAt(7).qty==50,
            "unrelated slot moved during partial consume"
        );

        int beforeMismatch=
            bank.inventoryAt(5).qty;

        expect(
            IllegalStateException.class,
            ()->service.consume(
                5,
                4151,
                1
            ),
            "item mismatch"
        );

        require(
            bank.inventoryAt(5)!=null&&
            bank.inventoryAt(5).qty==
                beforeMismatch,
            "mismatch mutated inventory"
        );

        expect(
            IllegalStateException.class,
            ()->service.consume(
                5,
                15272,
                2
            ),
            "insufficient quantity"
        );

        require(
            bank.inventoryAt(5)!=null&&
            bank.inventoryAt(5).qty==1,
            "insufficient quantity mutated inventory"
        );

        InventoryMutationService.ConsumeResult full=
            service.consume(
                5,
                15272,
                1
            );

        require(
            full.beforeQuantity==1&&
            full.afterQuantity==0&&
            full.cleared&&
            bank.inventoryAt(5)==null,
            "full consume did not clear exact slot"
        );

        require(
            bank.inventoryAt(7)!=null&&
            bank.inventoryAt(7).itemId==995&&
            bank.inventoryAt(7).qty==50,
            "full consume compacted unrelated slot"
        );

        InventoryMutationService.SlotSnapshot empty=
            service.inspect(5);

        require(
            !empty.occupied&&
            empty.itemId==-1&&
            empty.quantity==0,
            "empty slot snapshot"
        );

        invalidInputsAtomic(
            service,
            bank
        );
        authorityGuards(player);
        ownershipLockFence(player, service);
        boundaryGuard();

        System.out.println(
            "INVENTORY_MUTATION_SERVICE_PASS "+
            "canonicalBankState=true "+
            "exactSlot=true "+
            "exactItemMatch=true "+
            "partialConsume=true "+
            "fullConsumeClearsSlot=true "+
            "invalidAmountAtomic=true "+
            "mismatchAtomic=true "+
            "noCompaction=true "+
            "immutableFacts=true "+
            "writerIndependent=true "+
            "packet53Owned=false "+
            "itemPolicyOwned=false "+
            "persistenceOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static WorldPlayer seededPlayer(){
        WorldPlayer player=
            new WorldPlayer();
        BankState bank=player.bank();

        BankState.Stack[] bankRows=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];
        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];

        inventory[5]=
            new BankState.Stack(
                15272,
                3
            );
        inventory[7]=
            new BankState.Stack(
                995,
                50
            );

        bank.restoreAccountState(
            bankRows,
            inventory,
            false
        );

        return player;
    }

    private static void invalidInputsAtomic(
        InventoryMutationService service,
        BankState bank
    ){
        BankState.Stack[] bankRows=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];
        BankState.Stack[] inventory=
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ];
        inventory[4]=
            new BankState.Stack(
                385,
                4
            );

        bank.restoreAccountState(
            bankRows,
            inventory,
            false
        );

        expect(
            IllegalArgumentException.class,
            ()->service.consume(
                4,
                385,
                0
            ),
            "zero amount"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.consume(
                -1,
                385,
                1
            ),
            "negative slot"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.consume(
                4,
                -1,
                1
            ),
            "negative item"
        );

        require(
            bank.inventoryAt(4)!=null&&
            bank.inventoryAt(4).itemId==385&&
            bank.inventoryAt(4).qty==4,
            "invalid consume mutated canonical inventory"
        );
    }

    private static void authorityGuards(
        WorldPlayer player
    ){
        expect(
            IllegalArgumentException.class,
            ()->new InventoryMutationService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new InventoryMutationService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority"
        );
    }

    private static void ownershipLockFence(
        WorldPlayer player,
        InventoryMutationService service
    ){
        CountDownLatch started=
            new CountDownLatch(1);
        CountDownLatch completed=
            new CountDownLatch(1);

        Thread worker=
            new Thread(
                ()->{
                    started.countDown();
                    service.inspect(7);
                    completed.countDown();
                },
                "inventory-mutation-lock-fence"
            );

        try{
            synchronized(player.mutationLock()){
                worker.start();

                require(
                    started.await(
                        2L,
                        TimeUnit.SECONDS
                    ),
                    "inventory ownership worker did not start"
                );

                require(
                    !completed.await(
                        100L,
                        TimeUnit.MILLISECONDS
                    ),
                    "inventory mutation bypassed WorldPlayer mutation lock"
                );
            }

            require(
                completed.await(
                    2L,
                    TimeUnit.SECONDS
                ),
                "inventory mutation did not resume after ownership lock release"
            );
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new AssertionError(
                "inventory ownership lock test interrupted",
                interrupted
            );
        }
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                InventoryMutationService.class,
                InventoryMutationService.SlotSnapshot.class,
                InventoryMutationService.ConsumeResult.class,
                BankState.InventorySlotSnapshot.class,
                BankState.InventoryConsumeResult.class
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
                        "food",
                        "heal",
                        "bankroot"
                }){
                    require(
                        !haystack.contains(forbidden),
                        "presentation/item-policy identity leaked through "+
                        type.getSimpleName()+"."+
                        field.getName()
                    );
                }
            }
        }

        for(Method method:
                InventoryMutationService.class
                    .getDeclaredMethods()){
            String haystack=
                (method.getName()+" "+
                 method.getReturnType().getName())
                    .toLowerCase(Locale.ROOT);

            for(String forbidden:new String[]{
                    "packet",
                    "publish",
                    "persist",
                    "food",
                    "heal",
                    "equip",
                    "bank"
            }){
                require(
                    !haystack.contains(forbidden),
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

    private InventoryMutationServiceTest(){}
}

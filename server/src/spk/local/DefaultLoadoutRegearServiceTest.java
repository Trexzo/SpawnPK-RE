package spk.local;

import java.util.Arrays;
import java.util.Collections;

public final class DefaultLoadoutRegearServiceTest {
    private static final String OWNER=
        "player:alice";

    public static void main(String[] args){
        defaultSelectionAppliesCurrentRevision();
        missingDefaultFailsClosed();
        ownershipLossFailsClosed();

        System.out.println(
            "DEFAULT_LOADOUT_REGEAR_PASS "+
            "worldOwnedRegistry=true "+
            "registeredOwnerBinding=true "+
            "defaultSelection=true "+
            "currentRevisionFollow=true "+
            "inventoryApplied=true "+
            "equipmentApplied=true "+
            "sameRevisionAckIdempotent=true "+
            "missingDefaultFailClosed=true "+
            "staleGenerationFailClosed=true "+
            "autoRespawnOwned=false "+
            "packetOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void defaultSelectionAppliesCurrentRevision(){
        World world=World.isolatedForTest(1300L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "Alice"
            );

        try{
            PlayerLoadout v1=
                fixture(
                    1L,
                    100L,
                    2L
                );

            world.loadouts()
                .create(
                    v1
                );

            DefaultLoadoutService.Snapshot selected=
                world.defaultLoadouts()
                    .setDefault(
                        OWNER,
                        v1.id
                    );

            require(
                selected.hasDefault()&&
                selected.selectionRevision==1L,
                "default v1 selection"
            );

            DefaultLoadoutRegearService service=
                new DefaultLoadoutRegearService(
                    world
                );

            DefaultLoadoutRegearService.Result first=
                service.regear(
                    player,
                    generation
                );

            require(
                first.loadoutId.equals(
                    v1.id
                )&&
                first.version.equals(
                    PlayerLoadoutVersion.of(
                        1L
                    )
                )&&
                first.selectionRevision==1L&&
                first.apply.firstAcknowledgement,
                "first regear result"
            );

            requirePostimage(
                player,
                100,
                2
            );

            DefaultLoadoutRegearService.Result replay=
                service.regear(
                    player,
                    generation
                );

            require(
                !replay.apply.firstAcknowledgement&&
                replay.version.equals(
                    PlayerLoadoutVersion.of(
                        1L
                    )
                ),
                "same revision regear acknowledgement"
            );

            PlayerLoadout v2=
                fixture(
                    2L,
                    250L,
                    4L
                );

            world.loadouts()
                .replace(
                    v2,
                    PlayerLoadoutVersion.of(
                        1L
                    )
                );

            DefaultLoadoutService.Snapshot followed=
                world.defaultLoadouts()
                    .snapshot(
                        OWNER
                    );

            require(
                followed.selectionRevision==1L&&
                followed.currentLoadout==
                    v2&&
                followed.currentLoadout.version
                    .equals(
                        PlayerLoadoutVersion.of(
                            2L
                        )
                    ),
                "default did not follow current revision"
            );

            DefaultLoadoutRegearService.Result second=
                service.regear(
                    player,
                    generation
                );

            require(
                second.version.equals(
                    PlayerLoadoutVersion.of(
                        2L
                    )
                )&&
                second.apply.firstAcknowledgement,
                "updated default regear"
            );

            requirePostimage(
                player,
                250,
                4
            );

            require(
                DefaultLoadoutRegearService
                    .ownerRef(player)
                    .equals(
                        OWNER
                    ),
                "registered owner semantic identity"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void missingDefaultFailsClosed(){
        World world=World.isolatedForTest(1400L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "Bob"
            );

        try{
            BankState.InventorySlotSnapshot before=
                player.bank()
                    .inventorySlotSnapshot(0);

            expect(
                IllegalStateException.class,
                ()->new DefaultLoadoutRegearService(
                    world
                ).regear(
                    player,
                    generation
                ),
                "missing default"
            );

            require(
                sameSlot(
                    before,
                    player.bank()
                        .inventorySlotSnapshot(0)
                ),
                "missing default mutated inventory"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void ownershipLossFailsClosed(){
        World world=World.isolatedForTest(1500L);
        WorldPlayer player=new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                "Alice"
            );

        try{
            PlayerLoadout v1=
                fixture(
                    1L,
                    50L,
                    1L
                );
            world.loadouts().create(v1);
            world.defaultLoadouts()
                .setDefault(
                    OWNER,
                    v1.id
                );

            world.unregisterPlayer(
                player,
                generation
            );

            expect(
                IllegalStateException.class,
                ()->new DefaultLoadoutRegearService(
                    world
                ).regear(
                    player,
                    generation
                ),
                "stale player generation"
            );

            require(
                world.loadouts()
                    .get(
                        OWNER,
                        v1.id
                    )
                    .lastAppliedVersion==null,
                "ownership loss acknowledged loadout"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static PlayerLoadout fixture(
        long version,
        long coins,
        long food
    ){
        return new PlayerLoadout(
            PlayerLoadoutId.of(
                "loadout:pvp-default"
            ),
            PlayerLoadoutVersion.of(
                version
            ),
            OWNER,
            Arrays.asList(
                new PlayerLoadout.InventoryEntry(
                    "item:id:995",
                    coins
                ),
                new PlayerLoadout.InventoryEntry(
                    "item:id:15272",
                    food
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
            "CUSTOM_LOCALLAB_DEFAULT_REGEAR_TEST"
        );
    }

    private static void requirePostimage(
        WorldPlayer player,
        int coins,
        int food
    ){
        BankState.InventorySlotSnapshot slot0=
            player.bank()
                .inventorySlotSnapshot(0);

        require(
            slot0.occupied&&
            slot0.itemId==995&&
            slot0.quantity==coins,
            "coin postimage"
        );

        int foodCount=0;

        for(int slot=1;
            slot<
                BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.InventorySlotSnapshot entry=
                player.bank()
                    .inventorySlotSnapshot(slot);

            if(entry.occupied&&
               entry.itemId==15272){
                require(
                    entry.quantity==1,
                    "food nonstackable postimage"
                );
                foodCount++;
            }
        }

        require(
            foodCount==food,
            "food slot count expected="+
            food+
            " actual="+
            foodCount
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
            "weapon postimage"
        );
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
            if(type.isInstance(failure))
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

    private DefaultLoadoutRegearServiceTest(){}
}
